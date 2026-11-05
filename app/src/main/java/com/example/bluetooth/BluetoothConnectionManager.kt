package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.db.BlueLinkDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.FileCategory
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.data.model.TransferEntity
import com.example.data.model.TransferStatus
import com.example.protocol.BluetoothPacket
import com.example.protocol.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BluetoothConnectionManager(
    private val context: Context,
    private val helper: BluetoothManagerHelper
) {
    private val db = BlueLinkDatabase.getDatabase(context)
    private val chatDao = db.chatDao()
    private val transferDao = db.transferDao()
    private val deviceDao = db.deviceDao()

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDevice = MutableStateFlow<ConnectedDeviceInfo?>(null)
    val connectedDevice: StateFlow<ConnectedDeviceInfo?> = _connectedDevice.asStateFlow()

    private val _incomingFileOffer = MutableStateFlow<TransferEntity?>(null)
    val incomingFileOffer: StateFlow<TransferEntity?> = _incomingFileOffer.asStateFlow()

    private var activeSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var server: BluetoothServer? = null
    private var client: BluetoothClient? = null

    private var durationTimerJob: Job? = null
    private var connectionStartTime: Long = 0

    // Outbound transfers state cache
    private val outboundFileUris = ConcurrentHashMap<String, Uri>()
    private val activeReceivingStreams = ConcurrentHashMap<String, FileOutputStream>()

    init {
        startListeningServer()
    }

    fun startListeningServer() {
        if (server == null) {
            server = BluetoothServer(helper.adapter) { acceptedSocket ->
                onSocketConnected(acceptedSocket, isServer = true)
            }
        }
        server?.start()
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: ScannedDevice) {
        if (_connectionStatus.value == ConnectionStatus.CONNECTING ||
            _connectionStatus.value == ConnectionStatus.CONNECTED
        ) return

        _connectionStatus.value = ConnectionStatus.CONNECTING
        val targetNativeDev = device.nativeDevice
            ?: helper.adapter?.getRemoteDevice(device.address)

        if (targetNativeDev == null) {
            _connectionStatus.value = ConnectionStatus.FAILED
            return
        }

        val deviceName = try {
            targetNativeDev.name ?: device.name
        } catch (e: SecurityException) {
            device.name
        }

        _connectedDevice.value = ConnectedDeviceInfo(
            address = device.address,
            name = deviceName,
            status = ConnectionStatus.CONNECTING
        )

        client = BluetoothClient(helper.adapter)
        client?.connect(
            device = targetNativeDev,
            onConnected = { socket ->
                onSocketConnected(socket, isServer = false)
            },
            onFailed = { errorMsg ->
                Log.e("ConnectionManager", "Connect failed: $errorMsg")
                _connectionStatus.value = ConnectionStatus.FAILED
                _connectedDevice.value = null
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun onSocketConnected(socket: BluetoothSocket, isServer: Boolean) {
        disconnectActiveSocket(updateStatus = false)

        activeSocket = socket
        try {
            inputStream = socket.inputStream
            outputStream = socket.outputStream
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Error getting streams from socket", e)
            _connectionStatus.value = ConnectionStatus.FAILED
            return
        }

        val remoteDev = socket.remoteDevice
        val address = remoteDev?.address ?: "UNKNOWN"
        val name = try {
            remoteDev?.name ?: if (isServer) "Nearby Device" else "Connected Device"
        } catch (e: SecurityException) {
            "Connected Device"
        }

        connectionStartTime = System.currentTimeMillis()
        val connectedInfo = ConnectedDeviceInfo(
            address = address,
            name = name,
            connectedDurationMs = 0,
            status = ConnectionStatus.CONNECTED
        )

        _connectedDevice.value = connectedInfo
        _connectionStatus.value = ConnectionStatus.CONNECTED

        startDurationTimer()
        startReaderLoop()

        // Send PING keepalive
        scope.launch {
            sendPacket(BluetoothPacket(type = PacketType.PING))
        }
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = scope.launch {
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                val duration = System.currentTimeMillis() - connectionStartTime
                _connectedDevice.value = _connectedDevice.value?.copy(connectedDurationMs = duration)
                delay(1000)
            }
        }
    }

    private fun startReaderLoop() {
        scope.launch(Dispatchers.IO) {
            val inStream = inputStream ?: return@launch
            while (isActive && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                try {
                    val packet = BluetoothPacket.readFromStream(inStream)
                    if (packet == null) {
                        Log.d("ConnectionManager", "Stream EOF reached or disconnected")
                        handleDisconnect()
                        break
                    }
                    handleIncomingPacket(packet)
                } catch (e: Exception) {
                    Log.e("ConnectionManager", "Error reading packet", e)
                    handleDisconnect()
                    break
                }
            }
        }
    }

    private suspend fun handleIncomingPacket(packet: BluetoothPacket) {
        val currentDev = _connectedDevice.value ?: return

        when (packet.type) {
            PacketType.TEXT -> {
                val text = String(packet.payload, StandardCharsets.UTF_8)
                val message = ChatMessageEntity(
                    messageId = UUID.randomUUID().toString(),
                    senderAddress = currentDev.address,
                    senderName = currentDev.name,
                    receiverAddress = "MY_DEVICE",
                    isFromMe = false,
                    textContent = text,
                    type = MessageType.TEXT,
                    status = MessageStatus.SENT,
                    timestamp = System.currentTimeMillis()
                )
                chatDao.insertMessage(message)
            }

            PacketType.FILE_REQ -> {
                // Header format in payload: CategoryName|FileName|FileSize
                val meta = String(packet.payload, StandardCharsets.UTF_8).split("|")
                if (meta.size >= 3) {
                    val category = try { FileCategory.valueOf(meta[0]) } catch (e: Exception) { FileCategory.OTHER }
                    val fileName = meta[1]
                    val fileSize = meta[2].toLongOrNull() ?: 0L

                    val transfer = TransferEntity(
                        fileId = packet.fileId,
                        fileName = fileName,
                        fileCategory = category,
                        totalBytes = fileSize,
                        bytesTransferred = 0,
                        isIncoming = true,
                        remoteDeviceAddress = currentDev.address,
                        remoteDeviceName = currentDev.name,
                        status = TransferStatus.ACCEPT_REQUIRED,
                        timestamp = System.currentTimeMillis()
                    )
                    transferDao.insertTransfer(transfer)
                    _incomingFileOffer.value = transfer
                }
            }

            PacketType.FILE_ACCEPT -> {
                val fileId = packet.fileId
                val uri = outboundFileUris[fileId]
                if (uri != null) {
                    val transfer = transferDao.getTransferById(fileId)
                    if (transfer != null) {
                        transferDao.updateTransfer(transfer.copy(status = TransferStatus.TRANSFERRING))
                        streamOutboundFile(fileId, uri)
                    }
                }
            }

            PacketType.FILE_DECLINE -> {
                val fileId = packet.fileId
                val transfer = transferDao.getTransferById(fileId)
                if (transfer != null) {
                    transferDao.updateTransfer(transfer.copy(status = TransferStatus.DECLINED))
                }
            }

            PacketType.FILE_CHUNK -> {
                val fileId = packet.fileId
                val outStream = activeReceivingStreams[fileId]
                val transfer = transferDao.getTransferById(fileId)

                if (outStream != null && transfer != null) {
                    outStream.write(packet.payload)
                    val newBytes = transfer.bytesTransferred + packet.payload.size
                    val speed = 256 * 1024L // estimated bandwidth sample
                    transferDao.updateTransfer(
                        transfer.copy(
                            bytesTransferred = newBytes,
                            status = TransferStatus.TRANSFERRING,
                            speedBps = speed
                        )
                    )
                }
            }

            PacketType.FILE_END -> {
                val fileId = packet.fileId
                val outStream = activeReceivingStreams.remove(fileId)
                try {
                    outStream?.flush()
                    outStream?.close()
                } catch (e: Exception) {
                    Log.e("ConnectionManager", "Error closing file output stream", e)
                }

                val transfer = transferDao.getTransferById(fileId)
                if (transfer != null) {
                    val completedTransfer = transfer.copy(
                        status = TransferStatus.COMPLETED,
                        bytesTransferred = transfer.totalBytes,
                        speedBps = 0
                    )
                    transferDao.updateTransfer(completedTransfer)

                    // Also add a chat bubble for received media files
                    val msgType = when (transfer.fileCategory) {
                        FileCategory.PHOTO -> MessageType.PHOTO
                        FileCategory.VIDEO -> MessageType.VIDEO
                        else -> MessageType.FILE
                    }
                    val message = ChatMessageEntity(
                        messageId = UUID.randomUUID().toString(),
                        senderAddress = currentDev.address,
                        senderName = currentDev.name,
                        receiverAddress = "MY_DEVICE",
                        isFromMe = false,
                        textContent = "Received file: ${transfer.fileName}",
                        mediaUri = transfer.localUri,
                        fileName = transfer.fileName,
                        fileSize = transfer.totalBytes,
                        type = msgType,
                        status = MessageStatus.SENT,
                        timestamp = System.currentTimeMillis()
                    )
                    chatDao.insertMessage(message)
                }
            }

            PacketType.CANCEL -> {
                val fileId = packet.fileId
                activeReceivingStreams.remove(fileId)?.close()
                val transfer = transferDao.getTransferById(fileId)
                if (transfer != null) {
                    transferDao.updateTransfer(transfer.copy(status = TransferStatus.CANCELLED))
                }
            }

            PacketType.PING -> {
                sendPacket(BluetoothPacket(type = PacketType.PONG))
            }

            PacketType.PONG -> {
                // Heartbeat OK
            }
        }
    }

    fun sendTextMessage(text: String) {
        val currentDev = _connectedDevice.value ?: return
        val message = ChatMessageEntity(
            messageId = UUID.randomUUID().toString(),
            senderAddress = "MY_DEVICE",
            senderName = "Me",
            receiverAddress = currentDev.address,
            isFromMe = true,
            textContent = text,
            type = MessageType.TEXT,
            status = MessageStatus.SENDING,
            timestamp = System.currentTimeMillis()
        )

        scope.launch {
            val id = chatDao.insertMessage(message)
            val packet = BluetoothPacket(type = PacketType.TEXT, payload = text.toByteArray(StandardCharsets.UTF_8))
            val success = sendPacket(packet)

            val updatedStatus = if (success) MessageStatus.SENT else MessageStatus.FAILED
            chatDao.updateMessage(message.copy(id = id, status = updatedStatus))
        }
    }

    fun offerFileTransfer(
        fileName: String,
        fileCategory: FileCategory,
        fileSize: Long,
        uri: Uri
    ): String {
        val currentDev = _connectedDevice.value ?: return ""
        val fileId = UUID.randomUUID().toString()
        outboundFileUris[fileId] = uri

        val transfer = TransferEntity(
            fileId = fileId,
            fileName = fileName,
            fileCategory = fileCategory,
            totalBytes = fileSize,
            bytesTransferred = 0,
            isIncoming = false,
            remoteDeviceAddress = currentDev.address,
            remoteDeviceName = currentDev.name,
            status = TransferStatus.PENDING,
            localUri = uri.toString(),
            timestamp = System.currentTimeMillis()
        )

        scope.launch {
            transferDao.insertTransfer(transfer)
            val header = "${fileCategory.name}|$fileName|$fileSize"
            val packet = BluetoothPacket(
                type = PacketType.FILE_REQ,
                fileId = fileId,
                payload = header.toByteArray(StandardCharsets.UTF_8)
            )
            sendPacket(packet)
        }

        return fileId
    }

    fun acceptIncomingFile(fileId: String) {
        val offer = _incomingFileOffer.value
        _incomingFileOffer.value = null

        scope.launch(Dispatchers.IO) {
            val transfer = transferDao.getTransferById(fileId) ?: offer ?: return@launch

            // Save file in app external files directory under BlueLink/Received
            val receivedDir = File(context.getExternalFilesDir(null), "BlueLink/Received")
            if (!receivedDir.exists()) receivedDir.mkdirs()

            val saveFile = File(receivedDir, transfer.fileName)
            val fileOut = FileOutputStream(saveFile)
            activeReceivingStreams[fileId] = fileOut

            val updated = transfer.copy(
                status = TransferStatus.TRANSFERRING,
                localUri = Uri.fromFile(saveFile).toString()
            )
            transferDao.updateTransfer(updated)

            // Send ACCEPT packet back
            sendPacket(BluetoothPacket(type = PacketType.FILE_ACCEPT, fileId = fileId))
        }
    }

    fun declineIncomingFile(fileId: String) {
        _incomingFileOffer.value = null
        scope.launch {
            val transfer = transferDao.getTransferById(fileId)
            if (transfer != null) {
                transferDao.updateTransfer(transfer.copy(status = TransferStatus.DECLINED))
            }
            sendPacket(BluetoothPacket(type = PacketType.FILE_DECLINE, fileId = fileId))
        }
    }

    private suspend fun streamOutboundFile(fileId: String, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(uri) ?: return@withContext
                val transfer = transferDao.getTransferById(fileId) ?: return@withContext

                val buffer = ByteArray(32 * 1024) // 32KB chunks
                var bytesRead: Int
                var totalSent = 0L
                val startTime = System.currentTimeMillis()

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (_connectionStatus.value != ConnectionStatus.CONNECTED) break

                    val chunk = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                    val packet = BluetoothPacket(
                        type = PacketType.FILE_CHUNK,
                        fileId = fileId,
                        payload = chunk
                    )
                    val ok = sendPacket(packet)
                    if (!ok) break

                    totalSent += bytesRead
                    val elapsedSec = maxOf((System.currentTimeMillis() - startTime) / 1000.0, 0.1)
                    val speed = (totalSent / elapsedSec).toLong()

                    transferDao.updateTransfer(
                        transfer.copy(
                            bytesTransferred = totalSent,
                            status = TransferStatus.TRANSFERRING,
                            speedBps = speed
                        )
                    )
                }

                input.close()

                // Send FILE_END packet
                sendPacket(BluetoothPacket(type = PacketType.FILE_END, fileId = fileId))

                transferDao.updateTransfer(
                    transfer.copy(
                        status = TransferStatus.COMPLETED,
                        bytesTransferred = transfer.totalBytes,
                        speedBps = 0
                    )
                )

                // Add to chat history as sent attachment
                val currentDev = _connectedDevice.value
                if (currentDev != null) {
                    val msgType = when (transfer.fileCategory) {
                        FileCategory.PHOTO -> MessageType.PHOTO
                        FileCategory.VIDEO -> MessageType.VIDEO
                        else -> MessageType.FILE
                    }
                    val message = ChatMessageEntity(
                        messageId = UUID.randomUUID().toString(),
                        senderAddress = "MY_DEVICE",
                        senderName = "Me",
                        receiverAddress = currentDev.address,
                        isFromMe = true,
                        textContent = "Sent file: ${transfer.fileName}",
                        mediaUri = transfer.localUri,
                        fileName = transfer.fileName,
                        fileSize = transfer.totalBytes,
                        type = msgType,
                        status = MessageStatus.SENT,
                        timestamp = System.currentTimeMillis()
                    )
                    chatDao.insertMessage(message)
                }

            } catch (e: Exception) {
                Log.e("ConnectionManager", "Error streaming outbound file", e)
                val transfer = transferDao.getTransferById(fileId)
                if (transfer != null) {
                    transferDao.updateTransfer(transfer.copy(status = TransferStatus.FAILED))
                }
            } finally {
                outboundFileUris.remove(fileId)
            }
        }
    }

    @Synchronized
    private fun sendPacket(packet: BluetoothPacket): Boolean {
        val out = outputStream ?: return false
        return try {
            BluetoothPacket.writeToStream(out, packet)
            true
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Error writing packet to socket stream", e)
            false
        }
    }

    private fun handleDisconnect() {
        if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
            _connectionStatus.value = ConnectionStatus.CONNECTION_LOST
        } else {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
        disconnectActiveSocket(updateStatus = true)
    }

    fun disconnectActiveSocket(updateStatus: Boolean = true) {
        durationTimerJob?.cancel()
        try {
            inputStream?.close()
            outputStream?.close()
            activeSocket?.close()
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Error closing socket streams", e)
        }
        inputStream = null
        outputStream = null
        activeSocket = null

        if (updateStatus) {
            _connectedDevice.value = null
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
        }
    }
}

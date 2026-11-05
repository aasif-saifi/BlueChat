package com.example.data

import android.content.Context
import android.net.Uri
import com.example.bluetooth.BluetoothConnectionManager
import com.example.bluetooth.BluetoothManagerHelper
import com.example.bluetooth.ConnectedDeviceInfo
import com.example.bluetooth.ConnectionStatus
import com.example.bluetooth.ScannedDevice
import com.example.data.db.BlueLinkDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.FileCategory
import com.example.data.model.TransferEntity
import com.example.data.pref.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class BlueLinkRepository(val context: Context) {

    private val db = BlueLinkDatabase.getDatabase(context)
    val chatDao = db.chatDao()
    val transferDao = db.transferDao()
    val deviceDao = db.deviceDao()

    val settings = SettingsDataStore(context)
    val bluetoothHelper = BluetoothManagerHelper(context)
    val connectionManager = BluetoothConnectionManager(context, bluetoothHelper)

    val isBluetoothEnabled: StateFlow<Boolean> = bluetoothHelper.isBluetoothEnabled
    val isScanning: StateFlow<Boolean> = bluetoothHelper.isScanning
    val scannedDevices: StateFlow<List<ScannedDevice>> = bluetoothHelper.scannedDevices

    val connectionStatus: StateFlow<ConnectionStatus> = connectionManager.connectionStatus
    val connectedDevice: StateFlow<ConnectedDeviceInfo?> = connectionManager.connectedDevice
    val incomingFileOffer: StateFlow<TransferEntity?> = connectionManager.incomingFileOffer

    val allMessages: Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()
    val allTransfers: Flow<List<TransferEntity>> = transferDao.getAllTransfers()
    val receivedFiles: Flow<List<TransferEntity>> = transferDao.getReceivedFiles()

    fun getMessagesForDevice(deviceAddress: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForDevice(deviceAddress)

    fun startScanning() = bluetoothHelper.startDiscovery()
    fun stopScanning() = bluetoothHelper.stopDiscovery()

    fun connectToDevice(device: ScannedDevice) = connectionManager.connectToDevice(device)
    fun disconnect() = connectionManager.disconnectActiveSocket()

    fun sendTextMessage(text: String) = connectionManager.sendTextMessage(text)

    fun offerFileTransfer(
        fileName: String,
        fileCategory: FileCategory,
        fileSize: Long,
        uri: Uri
    ): String = connectionManager.offerFileTransfer(fileName, fileCategory, fileSize, uri)

    fun acceptIncomingFile(fileId: String) = connectionManager.acceptIncomingFile(fileId)
    fun declineIncomingFile(fileId: String) = connectionManager.declineIncomingFile(fileId)

    suspend fun clearChatHistory() = chatDao.clearAllMessages()
    suspend fun clearTransferHistory() = transferDao.clearAllTransfers()
}

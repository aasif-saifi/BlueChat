package com.example.protocol

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

object PacketType {
    const val TEXT: Byte = 0x01
    const val FILE_REQ: Byte = 0x02
    const val FILE_ACCEPT: Byte = 0x03
    const val FILE_DECLINE: Byte = 0x04
    const val FILE_CHUNK: Byte = 0x05
    const val FILE_END: Byte = 0x06
    const val ACK: Byte = 0x07
    const val CANCEL: Byte = 0x08
    const val PING: Byte = 0x09
    const val PONG: Byte = 0x0A
}

data class BluetoothPacket(
    val type: Byte,
    val fileId: String = "",
    val payload: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BluetoothPacket
        if (type != other.type) return false
        if (fileId != other.fileId) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.toInt()
        result = 31 * result + fileId.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        private val MAGIC = byteArrayOf('B'.code.toByte(), 'L'.code.toByte())
        private const val HEADER_SIZE = 2 + 1 + 36 + 4 + 8 // 51 bytes header

        fun writeToStream(output: OutputStream, packet: BluetoothPacket) {
            val fileIdBytes = ByteArray(36)
            val rawFileId = packet.fileId.toByteArray(StandardCharsets.UTF_8)
            System.arraycopy(rawFileId, 0, fileIdBytes, 0, minOf(rawFileId.size, 36))

            val crc = CRC32()
            crc.update(packet.payload)
            val checksum = crc.value

            val buffer = ByteBuffer.allocate(HEADER_SIZE + packet.payload.size)
            buffer.put(MAGIC)
            buffer.put(packet.type)
            buffer.put(fileIdBytes)
            buffer.putInt(packet.payload.size)
            buffer.putLong(checksum)
            buffer.put(packet.payload)

            output.write(buffer.array())
            output.flush()
        }

        fun readFromStream(input: InputStream): BluetoothPacket? {
            val headerBuffer = ByteArray(HEADER_SIZE)
            var bytesRead = 0
            while (bytesRead < HEADER_SIZE) {
                val read = input.read(headerBuffer, bytesRead, HEADER_SIZE - bytesRead)
                if (read == -1) return null // Stream closed
                bytesRead += read
            }

            val buffer = ByteBuffer.wrap(headerBuffer)
            val m1 = buffer.get()
            val m2 = buffer.get()
            if (m1 != 'B'.code.toByte() || m2 != 'L'.code.toByte()) {
                // Invalid magic, unsynced stream
                return null
            }

            val type = buffer.get()
            val fileIdBytes = ByteArray(36)
            buffer.get(fileIdBytes)
            val fileId = String(fileIdBytes, StandardCharsets.UTF_8).trim('\u0000', ' ')
            val payloadLen = buffer.getInt()
            val checksum = buffer.getLong()

            if (payloadLen < 0 || payloadLen > 10 * 1024 * 1024) { // Max 10MB chunk sanity check
                return null
            }

            val payload = ByteArray(payloadLen)
            var payloadRead = 0
            while (payloadRead < payloadLen) {
                val read = input.read(payload, payloadRead, payloadLen - payloadRead)
                if (read == -1) return null
                payloadRead += read
            }

            // Verify checksum
            val crc = CRC32()
            crc.update(payload)
            if (crc.value != checksum) {
                // Checksum mismatch
                return null
            }

            return BluetoothPacket(type = type, fileId = fileId, payload = payload)
        }
    }
}

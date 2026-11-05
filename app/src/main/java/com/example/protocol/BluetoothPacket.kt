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
        private const val MAGIC_BYTE_1 = 'B'.code.toByte()
        private const val MAGIC_BYTE_2 = 'L'.code.toByte()
        // Header after magic: 1 (type) + 36 (fileId) + 4 (length) + 8 (checksum) = 49 bytes
        private const val HEADER_BODY_SIZE = 1 + 36 + 4 + 8

        fun writeToStream(output: OutputStream, packet: BluetoothPacket) {
            val fileIdBytes = ByteArray(36)
            val rawFileId = packet.fileId.toByteArray(StandardCharsets.UTF_8)
            System.arraycopy(rawFileId, 0, fileIdBytes, 0, minOf(rawFileId.size, 36))

            val crc = CRC32()
            crc.update(packet.payload)
            val checksum = crc.value

            val totalSize = 2 + HEADER_BODY_SIZE + packet.payload.size
            val buffer = ByteBuffer.allocate(totalSize)
            buffer.put(MAGIC_BYTE_1)
            buffer.put(MAGIC_BYTE_2)
            buffer.put(packet.type)
            buffer.put(fileIdBytes)
            buffer.putInt(packet.payload.size)
            buffer.putLong(checksum)
            buffer.put(packet.payload)

            output.write(buffer.array())
            output.flush()
        }

        fun readFromStream(input: InputStream): BluetoothPacket? {
            // Find and synchronize on MAGIC bytes 'B', 'L'
            while (true) {
                val b = input.read()
                if (b == -1) return null
                if (b.toByte() == MAGIC_BYTE_1) {
                    val next = input.read()
                    if (next == -1) return null
                    if (next.toByte() == MAGIC_BYTE_2) {
                        break // Synchronized on MAGIC!
                    }
                }
            }

            val headerBuffer = ByteArray(HEADER_BODY_SIZE)
            var bytesRead = 0
            while (bytesRead < HEADER_BODY_SIZE) {
                val read = input.read(headerBuffer, bytesRead, HEADER_BODY_SIZE - bytesRead)
                if (read == -1) return null
                bytesRead += read
            }

            val buffer = ByteBuffer.wrap(headerBuffer)
            val type = buffer.get()
            val fileIdBytes = ByteArray(36)
            buffer.get(fileIdBytes)
            val fileId = String(fileIdBytes, StandardCharsets.UTF_8).trim('\u0000', ' ')
            val payloadLen = buffer.getInt()
            val checksum = buffer.getLong()

            if (payloadLen < 0 || payloadLen > 10 * 1024 * 1024) {
                return null
            }

            val payload = ByteArray(payloadLen)
            var payloadRead = 0
            while (payloadRead < payloadLen) {
                val read = input.read(payload, payloadRead, payloadLen - payloadRead)
                if (read == -1) return null
                payloadRead += read
            }

            val crc = CRC32()
            crc.update(payload)
            if (crc.value != checksum) {
                return null
            }

            return BluetoothPacket(type = type, fileId = fileId, payload = payload)
        }
    }
}

package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FileCategory {
    PHOTO, VIDEO, DOCUMENT, AUDIO, OTHER
}

enum class TransferStatus {
    PENDING, ACCEPT_REQUIRED, TRANSFERRING, COMPLETED, FAILED, CANCELLED, DECLINED
}

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey val fileId: String,
    val fileName: String,
    val fileCategory: FileCategory,
    val totalBytes: Long,
    val bytesTransferred: Long = 0,
    val isIncoming: Boolean,
    val remoteDeviceAddress: String,
    val remoteDeviceName: String,
    val status: TransferStatus,
    val localUri: String? = null,
    val speedBps: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val address: String,
    val name: String,
    val deviceType: String = "Smartphone",
    val isPaired: Boolean = false,
    val isConnected: Boolean = false,
    val lastConnectedTime: Long = 0,
    val rssi: Int = 0
)

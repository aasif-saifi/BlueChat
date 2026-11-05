package com.example.bluetooth

import android.bluetooth.BluetoothDevice

data class ScannedDevice(
    val address: String,
    val name: String,
    val isPaired: Boolean,
    val deviceType: String = "Mobile Device",
    val rssi: Int = 0,
    val nativeDevice: BluetoothDevice? = null
)

enum class ConnectionStatus {
    DISCONNECTED,
    SEARCHING,
    PAIRING,
    CONNECTING,
    CONNECTED,
    CONNECTION_LOST,
    FAILED
}

data class ConnectedDeviceInfo(
    val address: String,
    val name: String,
    val connectedDurationMs: Long = 0,
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED
)

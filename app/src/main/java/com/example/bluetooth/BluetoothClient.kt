package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

class BluetoothClient(
    private val adapter: BluetoothAdapter?,
    private val serviceUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
) {
    private var clientSocket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun connect(
        device: BluetoothDevice,
        onConnected: (BluetoothSocket) -> Unit,
        onFailed: (String) -> Unit
    ) {
        Thread {
            try {
                if (adapter?.isDiscovering == true) {
                    adapter.cancelDiscovery()
                }

                clientSocket = device.createRfcommSocketToServiceRecord(serviceUuid)
                Log.d("BluetoothClient", "Connecting to ${device.address}...")
                clientSocket?.connect()

                val socket = clientSocket
                if (socket != null && socket.isConnected) {
                    Log.d("BluetoothClient", "Connected successfully to ${device.address}")
                    onConnected(socket)
                } else {
                    onFailed("Connection failed or socket not connected")
                }
            } catch (e: SecurityException) {
                Log.e("BluetoothClient", "SecurityException connecting to device", e)
                onFailed("Missing Bluetooth permissions")
            } catch (e: IOException) {
                Log.e("BluetoothClient", "IOException connecting to device", e)
                // Fallback attempt using reflection if standard connection fails
                try {
                    val fallbackMethod = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    val fallbackSocket = fallbackMethod.invoke(device, 1) as? BluetoothSocket
                    fallbackSocket?.connect()
                    if (fallbackSocket != null && fallbackSocket.isConnected) {
                        clientSocket = fallbackSocket
                        onConnected(fallbackSocket)
                        return@Thread
                    }
                } catch (fallbackEx: Exception) {
                    Log.e("BluetoothClient", "Fallback RFCOMM connection failed", fallbackEx)
                }
                onFailed("Could not connect to ${device.name ?: device.address}: ${e.localizedMessage}")
            }
        }.start()
    }

    fun cancel() {
        try {
            clientSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error closing client socket", e)
        }
        clientSocket = null
    }
}

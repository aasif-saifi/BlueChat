package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.util.UUID

class BluetoothServer(
    private val adapter: BluetoothAdapter?,
    private val serviceUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"),
    private val onSocketAccepted: (BluetoothSocket) -> Unit
) {
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var isRunning = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (adapter == null || !adapter.isEnabled || isRunning) return
        isRunning = true

        Thread {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("BlueLinkServer", serviceUuid)
                Log.d("BluetoothServer", "RFCOMM Server listening on UUID: $serviceUuid")

                while (isRunning) {
                    val socket: BluetoothSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: IOException) {
                        Log.d("BluetoothServer", "Server accept closed or interrupted: ${e.message}")
                        break
                    }

                    if (socket != null) {
                        Log.d("BluetoothServer", "Incoming socket accepted from: ${socket.remoteDevice?.address}")
                        onSocketAccepted(socket)
                    }
                }
            } catch (e: SecurityException) {
                Log.e("BluetoothServer", "Permission missing for RFCOMM server", e)
            } catch (e: IOException) {
                Log.e("BluetoothServer", "IOException starting server socket", e)
            } finally {
                stop()
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothServer", "Error closing server socket", e)
        }
        serverSocket = null
    }
}

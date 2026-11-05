package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothManagerHelper(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.TELECOM_SERVICE) as? BluetoothManager
            ?: context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val adapter: BluetoothAdapter? = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private val _isBluetoothEnabled = MutableStateFlow(adapter?.isEnabled == true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private var isReceiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }

                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()

                    device?.let { dev ->
                        val name = try {
                            dev.name ?: "Unknown Device"
                        } catch (e: SecurityException) {
                            "Unknown Device"
                        }
                        val address = dev.address
                        val isBonded = try {
                            dev.bondState == BluetoothDevice.BOND_BONDED
                        } catch (e: SecurityException) {
                            false
                        }

                        val scannedDev = ScannedDevice(
                            address = address,
                            name = name,
                            isPaired = isBonded,
                            rssi = rssi,
                            nativeDevice = dev
                        )

                        val currentList = _scannedDevices.value.toMutableList()
                        val existingIndex = currentList.indexOfFirst { it.address == address }
                        if (existingIndex >= 0) {
                            currentList[existingIndex] = scannedDev
                        } else {
                            currentList.add(scannedDev)
                        }
                        _scannedDevices.value = currentList
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    fun registerReceivers() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            context.registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
        }
        updateBluetoothState()
    }

    fun unregisterReceivers() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(bluetoothReceiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
            isReceiverRegistered = false
        }
    }

    fun updateBluetoothState() {
        _isBluetoothEnabled.value = adapter?.isEnabled == true
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<ScannedDevice> {
        if (adapter == null || !adapter.isEnabled) return emptyList()
        return try {
            adapter.bondedDevices.map { dev ->
                ScannedDevice(
                    address = dev.address,
                    name = dev.name ?: "Paired Device",
                    isPaired = true,
                    nativeDevice = dev
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        if (adapter == null || !adapter.isEnabled) return false
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            _scannedDevices.value = getPairedDevices()
            _isScanning.value = adapter.startDiscovery()
            return _isScanning.value
        } catch (e: SecurityException) {
            _isScanning.value = false
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (adapter?.isDiscovering == true) {
            try {
                adapter.cancelDiscovery()
            } catch (e: SecurityException) {
                // Permission handled
            }
        }
        _isScanning.value = false
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(device: BluetoothDevice): Boolean {
        return try {
            device.createBond()
        } catch (e: SecurityException) {
            false
        }
    }
}

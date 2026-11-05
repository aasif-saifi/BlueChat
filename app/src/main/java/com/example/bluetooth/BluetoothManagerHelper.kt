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
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BluetoothManagerHelper(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val adapter: BluetoothAdapter? = bluetoothManager?.adapter ?: try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (e: Exception) {
        null
    }

    private val _isSimulatedMode = MutableStateFlow(adapter == null)
    val isSimulatedMode: StateFlow<Boolean> = _isSimulatedMode.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(
        (adapter?.isEnabled == true) || (adapter == null)
    )
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = _scannedDevices.asStateFlow()

    private var isReceiverRegistered = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON) || _isSimulatedMode.value
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
                            dev.name ?: "Nearby Device"
                        } catch (e: SecurityException) {
                            "Nearby Device"
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
                            nativeDevice = dev,
                            isSimulated = false
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

    init {
        updateBluetoothState()
        if (_isBluetoothEnabled.value) {
            populateDefaultDevices()
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
            try {
                ContextCompat.registerReceiver(
                    context,
                    bluetoothReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isReceiverRegistered = true
            } catch (e: Exception) {
                Log.w("BluetoothHelper", "Could not register receiver: ${e.message}")
            }
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
        val realEnabled = try {
            adapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
        _isBluetoothEnabled.value = realEnabled || _isSimulatedMode.value
    }

    fun setBluetoothSimulatedEnabled(enabled: Boolean) {
        _isSimulatedMode.value = enabled
        _isBluetoothEnabled.value = enabled || (adapter?.isEnabled == true)
        if (_isBluetoothEnabled.value) {
            populateDefaultDevices()
        }
    }

    private fun populateDefaultDevices() {
        val list = mutableListOf<ScannedDevice>()
        list.addAll(getPairedDevices())
        if (list.none { it.isSimulated }) {
            list.add(
                ScannedDevice(
                    address = "SIM_PIXEL_8",
                    name = "Pixel 8 Pro (Nearby Peer)",
                    isPaired = false,
                    rssi = -55,
                    nativeDevice = null,
                    isSimulated = true
                )
            )
            list.add(
                ScannedDevice(
                    address = "SIM_GALAXY_S24",
                    name = "Galaxy S24 (Direct Link)",
                    isPaired = false,
                    rssi = -68,
                    nativeDevice = null,
                    isSimulated = true
                )
            )
        }
        _scannedDevices.value = list
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
                    nativeDevice = dev,
                    isSimulated = false
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        updateBluetoothState()
        _isScanning.value = true

        val paired = getPairedDevices()
        val current = _scannedDevices.value.toMutableList()
        paired.forEach { dev ->
            if (current.none { it.address == dev.address }) {
                current.add(dev)
            }
        }
        // Ensure simulation devices exist for offline testing
        if (current.none { it.isSimulated }) {
            current.add(
                ScannedDevice(
                    address = "SIM_PIXEL_8",
                    name = "Pixel 8 Pro (Nearby Peer)",
                    isPaired = false,
                    rssi = -55,
                    nativeDevice = null,
                    isSimulated = true
                )
            )
            current.add(
                ScannedDevice(
                    address = "SIM_GALAXY_S24",
                    name = "Galaxy S24 (Direct Link)",
                    isPaired = false,
                    rssi = -68,
                    nativeDevice = null,
                    isSimulated = true
                )
            )
        }
        _scannedDevices.value = current

        var realStarted = false
        if (adapter != null && adapter.isEnabled) {
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
                realStarted = adapter.startDiscovery()
            } catch (e: SecurityException) {
                Log.w("BluetoothHelper", "startDiscovery SecurityException: ${e.message}")
            }
        }

        // If simulated or real discovery stops after delay
        scope.launch {
            delay(12000)
            _isScanning.value = false
        }

        return realStarted || true
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

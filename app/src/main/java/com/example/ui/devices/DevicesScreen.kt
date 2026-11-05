package com.example.ui.devices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetooth.ConnectionStatus
import com.example.bluetooth.ScannedDevice
import com.example.ui.MainViewModel

@SuppressLint("MissingPermission")
@Composable
fun DevicesScreen(
    viewModel: MainViewModel,
    onNavigateToChat: () -> Unit
) {
    val context = LocalContext.current
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val devices by viewModel.scannedDevices.collectAsStateWithLifecycle()
    val connStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val connectedDev by viewModel.connectedDevice.collectAsStateWithLifecycle()

    val pairedDevices = devices.filter { it.isPaired }
    val availableDevices = devices.filter { !it.isPaired }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Scanning Header Card with Animated Ripple
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Radar / Scanning Animation
                val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "radar_rotate"
                )
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_scale"
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(if (isScanning) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothSearching,
                        contentDescription = "Scanning",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(40.dp)
                            .rotate(if (isScanning) rotation else 0f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isScanning) "Scanning for nearby devices..." else "Device Discovery",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isScanning) viewModel.stopScan() else viewModel.startScan()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isScanning) "Stop Scan" else "Start Scan")
                    }

                    OutlinedButton(
                        onClick = {
                            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                            }
                            context.startActivity(discoverableIntent)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("make_discoverable_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Discoverable", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discoverable")
                    }
                }
            }
        }

        // List of Devices
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (connectedDev != null) {
                item {
                    Text(
                        text = "Currently Connected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                item {
                    DeviceItemCard(
                        name = connectedDev?.name ?: "Connected Device",
                        address = connectedDev?.address ?: "",
                        isPaired = true,
                        isConnected = true,
                        onConnectClick = { onNavigateToChat() },
                        onDisconnectClick = { viewModel.disconnect() }
                    )
                }
            }

            item {
                Text(
                    text = "Paired Devices (${pairedDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (pairedDevices.isEmpty()) {
                item {
                    Text(
                        text = "No paired devices found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(pairedDevices) { device ->
                    val isThisConnected = connectedDev?.address == device.address
                    DeviceItemCard(
                        name = device.name,
                        address = device.address,
                        isPaired = true,
                        isConnected = isThisConnected,
                        onConnectClick = {
                            if (isThisConnected) onNavigateToChat()
                            else viewModel.connectToDevice(device)
                        },
                        onDisconnectClick = { viewModel.disconnect() }
                    )
                }
            }

            item {
                Text(
                    text = "Available Nearby Devices (${availableDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            if (availableDevices.isEmpty()) {
                item {
                    Text(
                        text = if (isScanning) "Searching..." else "Tap 'Start Scan' to discover nearby phones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(availableDevices) { device ->
                    DeviceItemCard(
                        name = device.name,
                        address = device.address,
                        isPaired = false,
                        isConnected = false,
                        onConnectClick = { viewModel.connectToDevice(device) },
                        onDisconnectClick = { }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    name: String,
    address: String,
    isPaired: Boolean,
    isConnected: Boolean,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.PhoneAndroid,
                        contentDescription = "Device",
                        tint = if (isConnected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConnected) "Connected" else if (isPaired) "Paired" else "Available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isConnected) {
                Button(
                    onClick = onConnectClick,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Chat")
                }
            } else {
                Button(
                    onClick = onConnectClick,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Connect")
                }
            }
        }
    }
}

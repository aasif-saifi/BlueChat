package com.example.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import android.bluetooth.BluetoothAdapter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetooth.ConnectionStatus
import com.example.ui.MainViewModel
import com.example.ui.permissions.checkBluetoothPermissions
import com.example.ui.permissions.getBluetoothPermissionsArray
import com.example.ui.theme.*
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToDevices: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToSendFiles: () -> Unit,
    onNavigateToPhotoCapture: () -> Unit,
    onNavigateToVideoCapture: () -> Unit,
    onNavigateToReceivedFiles: () -> Unit
) {
    val context = LocalContext.current
    val isBtEnabled by viewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val connStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val connDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            try {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                context.startActivity(intent)
            } catch (e: Exception) {
                viewModel.setBluetoothSimulatedEnabled(true)
            }
            viewModel.updateBluetoothState()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header Banner
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "BlueLink",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Connect. Chat. Share. Offline.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = "Offline Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "100% Offline",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bluetooth Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bluetooth_status_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isBtEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isBtEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                            contentDescription = "Bluetooth Status",
                            tint = if (isBtEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Bluetooth",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isBtEnabled) Color(0xFF3DDC84) else Color.Red
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBtEnabled) "Enabled" else "Disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!isBtEnabled) {
                    Button(
                        onClick = {
                            if (!checkBluetoothPermissions(context)) {
                                permissionLauncher.launch(getBluetoothPermissionsArray())
                            } else {
                                try {
                                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    viewModel.setBluetoothSimulatedEnabled(true)
                                    Toast.makeText(context, "Bluetooth enabled (Testing Mode)", Toast.LENGTH_SHORT).show()
                                }
                                viewModel.updateBluetoothState()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("turn_on_bluetooth_btn")
                    ) {
                        Text("Turn On")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            viewModel.setBluetoothSimulatedEnabled(false)
                            Toast.makeText(context, "Bluetooth disabled", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("turn_off_bluetooth_btn")
                    ) {
                        Text("Turn Off")
                    }
                }
            }
        }

        // Connection Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connection_status_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (connStatus == ConnectionStatus.CONNECTED)
                    GeoPrimaryLight
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Subtle Geo Pattern Accent in Background for Connected state
                if (connStatus == ConnectionStatus.CONNECTED) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (connStatus == ConnectionStatus.CONNECTED)
                                Color.White.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "CONNECTED DEVICE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = if (connStatus == ConnectionStatus.CONNECTED) Color.White.copy(alpha = 0.9f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (connStatus == ConnectionStatus.CONNECTED)
                                Color(0xFFD3E4FF).copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (connStatus == ConnectionStatus.CONNECTED) Color(0xFF3DDC84) else Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (connStatus == ConnectionStatus.CONNECTED) "P2P Active" else "Idle",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (connStatus == ConnectionStatus.CONNECTED) Color.White
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (connStatus == ConnectionStatus.CONNECTED && connDevice != null) {
                        Text(
                            text = connDevice?.name ?: "Connected Device",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        val durationSec = (connDevice?.connectedDurationMs ?: 0L) / 1000
                        val minutes = durationSec / 60
                        val seconds = durationSec % 60
                        Text(
                            text = String.format(Locale.US, "Connected for %02d:%02d • Strong Signal", minutes, seconds),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = GeoPrimaryLight
                                )
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Phone Call", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Phone Call", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.disconnect() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Disconnect", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = "Connect to a nearby device to start offline messaging and file transfers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onNavigateToDevices,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("find_devices_btn")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Find Devices")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Find Nearby Devices", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Main Actions Section Title
        Text(
            text = "Main Actions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Action Cards Grid
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MainActionCard(
                    title = "Find Devices",
                    subtitle = "Scan & Pair",
                    icon = Icons.Default.Search,
                    cardBgColor = GeoPrimaryContainerLight,
                    iconBgColor = GeoPrimaryLight,
                    iconTint = Color.White,
                    textColor = GeoOnPrimaryContainerLight,
                    modifier = Modifier.weight(1f),
                    testTag = "action_find_devices",
                    onClick = onNavigateToDevices
                )
                MainActionCard(
                    title = "Messages",
                    subtitle = "Offline Chat",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    cardBgColor = MaterialTheme.colorScheme.surface,
                    iconBgColor = GeoSecondaryContainerLight,
                    iconTint = GeoOnSecondaryContainerLight,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    testTag = "action_messages",
                    onClick = onNavigateToMessages
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MainActionCard(
                    title = "Send Files",
                    subtitle = "Pick & Transfer",
                    icon = Icons.Default.FolderOpen,
                    cardBgColor = MaterialTheme.colorScheme.surface,
                    iconBgColor = GeoMintContainerLight,
                    iconTint = GeoOnMintContainerLight,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    testTag = "action_send_files",
                    onClick = onNavigateToSendFiles
                )
                MainActionCard(
                    title = "Send Photo",
                    subtitle = "Camera Snap",
                    icon = Icons.Default.CameraAlt,
                    cardBgColor = MaterialTheme.colorScheme.surface,
                    iconBgColor = GeoTertiaryContainerLight,
                    iconTint = GeoOnTertiaryContainerLight,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    testTag = "action_send_photo",
                    onClick = onNavigateToPhotoCapture
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MainActionCard(
                    title = "Send Video",
                    subtitle = "Camera Record",
                    icon = Icons.Default.Videocam,
                    cardBgColor = MaterialTheme.colorScheme.surface,
                    iconBgColor = GeoErrorContainerLight,
                    iconTint = GeoOnErrorContainerLight,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    testTag = "action_send_video",
                    onClick = onNavigateToVideoCapture
                )
                MainActionCard(
                    title = "Received Files",
                    subtitle = "History & Gallery",
                    icon = Icons.Default.Download,
                    cardBgColor = MaterialTheme.colorScheme.surface,
                    iconBgColor = GeoPrimaryContainerLight,
                    iconTint = GeoOnPrimaryContainerLight,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    testTag = "action_received_files",
                    onClick = onNavigateToReceivedFiles
                )
            }
        }
    }
}

@Composable
fun MainActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    cardBgColor: Color,
    iconBgColor: Color,
    iconTint: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = "",
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(112.dp)
            .testTag(testTag)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

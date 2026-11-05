package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraCaptureScreen
import com.example.ui.devices.DevicesScreen
import com.example.ui.home.HomeScreen
import com.example.ui.messages.ChatScreen
import com.example.ui.permissions.PermissionHandler
import com.example.ui.settings.SettingsScreen
import androidx.compose.material3.NavigationBarItemDefaults
import com.example.ui.theme.BlueLinkTheme
import com.example.ui.theme.GeoOnPrimaryContainerLight
import com.example.ui.theme.GeoPrimaryContainerLight
import com.example.ui.transfers.ReceivedFilesScreen
import com.example.ui.transfers.SendFilesScreen
import com.example.ui.transfers.TransfersScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeSetting by viewModel.themeSetting.collectAsStateWithLifecycle()
            val dynamicColorSetting by viewModel.dynamicColorSetting.collectAsStateWithLifecycle()

            val isDarkTheme = when (themeSetting) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            BlueLinkTheme(
                darkTheme = isDarkTheme,
                dynamicColor = dynamicColorSetting
            ) {
                BlueLinkApp(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.registerReceivers()
    }

    override fun onStop() {
        super.onStop()
        viewModel.unregisterReceivers()
    }
}

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    object Messages : BottomNavItem("chat", "Messages", Icons.AutoMirrored.Filled.Chat)
    object Transfers : BottomNavItem("transfers", "Transfers", Icons.Default.SwapHoriz)
    object Devices : BottomNavItem("devices", "Devices", Icons.Default.Search)
    object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun BlueLinkApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var showPermissionDialog by remember { mutableStateOf(false) }

    PermissionHandler(
        showDialog = showPermissionDialog,
        onDismiss = { showPermissionDialog = false },
        onPermissionsGranted = { }
    )

    val bottomNavItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Messages,
        BottomNavItem.Transfers,
        BottomNavItem.Devices,
        BottomNavItem.Settings
    )

    val isTopLevelRoute = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevelRoute) {
                NavigationBar(
                    containerColor = com.example.ui.theme.GeoLightSurfaceVariant,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("bottom_navigation_bar")
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = GeoPrimaryContainerLight,
                                selectedIconColor = GeoOnPrimaryContainerLight,
                                selectedTextColor = GeoOnPrimaryContainerLight
                            ),
                            modifier = Modifier.testTag("nav_item_${item.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToDevices = { navController.navigate(BottomNavItem.Devices.route) },
                    onNavigateToMessages = { navController.navigate(BottomNavItem.Messages.route) },
                    onNavigateToSendFiles = { navController.navigate("send_files") },
                    onNavigateToPhotoCapture = { navController.navigate("camera_photo") },
                    onNavigateToVideoCapture = { navController.navigate("camera_video") },
                    onNavigateToReceivedFiles = { navController.navigate("received_files") }
                )
            }

            composable(BottomNavItem.Messages.route) {
                ChatScreen(
                    viewModel = viewModel,
                    onNavigateToPhotoCapture = { navController.navigate("camera_photo") }
                )
            }

            composable(BottomNavItem.Transfers.route) {
                TransfersScreen(viewModel = viewModel)
            }

            composable(BottomNavItem.Devices.route) {
                DevicesScreen(
                    viewModel = viewModel,
                    onNavigateToChat = { navController.navigate(BottomNavItem.Messages.route) }
                )
            }

            composable(BottomNavItem.Settings.route) {
                SettingsScreen(
                    viewModel = viewModel,
                    onRequestPermissions = { showPermissionDialog = true }
                )
            }

            composable("send_files") {
                SendFilesScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("received_files") {
                ReceivedFilesScreen(viewModel = viewModel)
            }

            composable("camera_photo") {
                CameraCaptureScreen(
                    viewModel = viewModel,
                    initialIsVideo = false,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("camera_video") {
                CameraCaptureScreen(
                    viewModel = viewModel,
                    initialIsVideo = true,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

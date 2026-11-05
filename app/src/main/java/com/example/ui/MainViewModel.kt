package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.ConnectedDeviceInfo
import com.example.bluetooth.ConnectionStatus
import com.example.bluetooth.ScannedDevice
import com.example.data.BlueLinkRepository
import com.example.data.model.ChatMessageEntity
import com.example.data.model.FileCategory
import com.example.data.model.TransferEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = BlueLinkRepository(application)

    val isBluetoothEnabled: StateFlow<Boolean> = repository.isBluetoothEnabled
    val isScanning: StateFlow<Boolean> = repository.isScanning
    val scannedDevices: StateFlow<List<ScannedDevice>> = repository.scannedDevices

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
    val connectedDevice: StateFlow<ConnectedDeviceInfo?> = repository.connectedDevice
    val incomingFileOffer: StateFlow<TransferEntity?> = repository.incomingFileOffer

    val messages: StateFlow<List<ChatMessageEntity>> = repository.allMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val transfers: StateFlow<List<TransferEntity>> = repository.allTransfers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val receivedFiles: StateFlow<List<TransferEntity>> = repository.receivedFiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val themeSetting: StateFlow<String> = repository.settings.theme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "SYSTEM"
    )

    val dynamicColorSetting: StateFlow<Boolean> = repository.settings.dynamicColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    fun registerReceivers() {
        repository.bluetoothHelper.registerReceivers()
    }

    fun unregisterReceivers() {
        repository.bluetoothHelper.unregisterReceivers()
    }

    fun startScan() {
        repository.startScanning()
    }

    fun stopScan() {
        repository.stopScanning()
    }

    fun connectToDevice(device: ScannedDevice) {
        repository.connectToDevice(device)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun sendTextMessage(text: String) {
        if (text.isNotBlank()) {
            repository.sendTextMessage(text.trim())
        }
    }

    fun offerFileTransfer(fileName: String, fileCategory: FileCategory, fileSize: Long, uri: Uri) {
        repository.offerFileTransfer(fileName, fileCategory, fileSize, uri)
    }

    fun acceptFile(fileId: String) {
        repository.acceptIncomingFile(fileId)
    }

    fun declineFile(fileId: String) {
        repository.declineIncomingFile(fileId)
    }

    fun setTheme(theme: String) {
        viewModelScope.launch { repository.settings.setTheme(theme) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.settings.setDynamicColor(enabled) }
    }

    fun clearChat() {
        viewModelScope.launch { repository.clearChatHistory() }
    }

    fun clearTransfers() {
        viewModelScope.launch { repository.clearTransferHistory() }
    }
}

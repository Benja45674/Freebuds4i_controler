package com.example.audifonos.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.audifonos.data.protocol.AncMode
import com.example.audifonos.data.protocol.AncToggleLoop
import com.example.audifonos.data.protocol.DoubleTapAction
import com.example.audifonos.data.protocol.HoldAction
import com.example.audifonos.data.repository.HeadphonesRepository
import com.example.audifonos.domain.model.HeadphonesUiState
import kotlinx.coroutines.flow.StateFlow

class HeadphonesViewModel(
    private val repository: HeadphonesRepository,
    context: Context
) : ViewModel() {
    val uiState: StateFlow<HeadphonesUiState> = repository.uiState

    private val sharedPrefs = context.getSharedPreferences("headphones_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val DEFAULT_MAC_ADDRESS = "28:54:71:B4:7B:36"
    }

    @SuppressLint("MissingPermission")
    fun getLastMacAddress(): String {
        val saved = sharedPrefs.getString("mac_address", "")
        val bonded = getBondedDevices()

        if (!saved.isNullOrBlank() && bonded.any { it.address.equals(saved, ignoreCase = true) }) {
            return saved
        }

        val freebuds = bonded.firstOrNull { device ->
            val name = device.name?.lowercase() ?: ""
            name.contains("freebuds") || name.contains("huawei") || name.contains("honor")
        } ?: bonded.firstOrNull()

        if (freebuds != null) {
            val autoMac = freebuds.address
            sharedPrefs.edit().putString("mac_address", autoMac).apply()
            return autoMac
        }

        return if (!saved.isNullOrBlank()) saved else DEFAULT_MAC_ADDRESS
    }

    fun connect(macAddress: String) {
        sharedPrefs.edit().putString("mac_address", macAddress.uppercase()).apply()
        repository.connect(macAddress)
    }

    fun disconnect() {
        repository.disconnect()
    }

    fun setAncMode(mode: AncMode) {
        repository.setAncMode(mode)
    }

    fun setWearDetection(enabled: Boolean) {
        repository.setWearDetection(enabled)
    }

    fun setDoubleTapActions(left: DoubleTapAction, right: DoubleTapAction) {
        repository.setDoubleTapActions(left, right)
    }

    fun setHoldAction(action: HoldAction) {
        repository.setHoldAction(action)
    }

    fun setAncToggleLoop(loop: AncToggleLoop) {
        repository.setAncToggleLoop(loop)
    }

    fun getBondedDevices(): List<android.bluetooth.BluetoothDevice> {
        return repository.getBondedDevices()
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }

    class Factory(
        private val repository: HeadphonesRepository,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HeadphonesViewModel(repository, context) as T
        }
    }
}

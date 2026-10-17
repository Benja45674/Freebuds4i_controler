package com.example.audifonos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.audifonos.data.bluetooth.MbbBluetoothManager
import com.example.audifonos.data.repository.HeadphonesRepository
import com.example.audifonos.ui.screens.MainScreen
import com.example.audifonos.ui.screens.HeadphonesViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: HeadphonesViewModel by viewModels {
        val bluetoothManager = MbbBluetoothManager(applicationContext)
        val repository = HeadphonesRepository(bluetoothManager)
        HeadphonesViewModel.Factory(repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainScreen(viewModel = viewModel)
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            viewModel.disconnect()
        }
        super.onDestroy()
    }
}

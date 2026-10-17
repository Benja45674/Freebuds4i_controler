package com.example.audifonos.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

@Suppress("DEPRECATION")
class MbbBluetoothManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter? = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        ?: BluetoothAdapter.getDefaultAdapter()
) {
    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private const val MIN_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 30000L
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _rawBytesReceived = MutableSharedFlow<ByteArray>(replay = 0)
    val rawBytesReceived: SharedFlow<ByteArray> = _rawBytesReceived.asSharedFlow()

    private var connectionJob: Job? = null
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isExplicitDisconnect = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun connect(macAddress: String) {
        disconnect()
        isExplicitDisconnect = false

        connectionJob = scope.launch {
            var backoffMs = MIN_BACKOFF_MS

            while (isActive) {
                _connectionState.value = ConnectionState.Connecting

                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    _connectionState.value = ConnectionState.Error("Bluetooth desactivado")
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }

                val device: BluetoothDevice
                try {
                    device = bluetoothAdapter.getRemoteDevice(macAddress)
                } catch (e: IllegalArgumentException) {
                    _connectionState.value = ConnectionState.Error("Dispositivo no encontrado")
                    break
                }

                var currentSocket: BluetoothSocket? = null
                var wasConnected = false
                try {
                    currentSocket = connectSocketWithFallbacks(device, bluetoothAdapter)
                    socket = currentSocket

                    val inputStream = currentSocket.inputStream
                    outputStream = currentSocket.outputStream

                    wasConnected = true
                    _connectionState.value = ConnectionState.Connected
                    backoffMs = MIN_BACKOFF_MS

                    readLoop(inputStream)

                } catch (e: SecurityException) {
                    if (!isExplicitDisconnect) {
                        _connectionState.value = ConnectionState.Error("Permiso Bluetooth requerido")
                    }
                    break
                } catch (e: IOException) {
                    if (!isExplicitDisconnect) {
                        if (wasConnected) {
                            _connectionState.value = ConnectionState.Error("Conexión interrumpida")
                            delay(backoffMs)
                            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                            continue
                        } else {
                            _connectionState.value = ConnectionState.Error("Dispositivo no encontrado. Verifica que esté encendido y vinculado.")
                            break
                        }
                    } else {
                        break
                    }
                } finally {
                    cleanupSocket(currentSocket)
                    socket = null
                    outputStream = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectSocketWithFallbacks(device: BluetoothDevice, adapter: BluetoothAdapter): BluetoothSocket {
        val attempts = mutableListOf<() -> BluetoothSocket>()

        attempts.add {
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            m.invoke(device, 1) as BluetoothSocket
        }

        attempts.add {
            val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
            m.invoke(device, 1) as BluetoothSocket
        }

        val deviceUuids = try { device.uuids } catch (_: Exception) { null }
        if (deviceUuids != null) {
            for (pUuid in deviceUuids) {
                val u = pUuid.uuid
                if (u != null) {
                    attempts.add { device.createRfcommSocketToServiceRecord(u) }
                    attempts.add { device.createInsecureRfcommSocketToServiceRecord(u) }
                }
            }
        }

        attempts.add { device.createRfcommSocketToServiceRecord(SPP_UUID) }
        attempts.add { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) }

        attempts.add {
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            m.invoke(device, 2) as BluetoothSocket
        }

        var lastException: IOException? = null
        for (factory in attempts) {
            var s: BluetoothSocket? = null
            try {
                s = factory()
                adapter.cancelDiscovery()
                s.connect()
                return s
            } catch (e: IOException) {
                lastException = e
                try { s?.close() } catch (_: Exception) {}
            } catch (e: Exception) {
                try { s?.close() } catch (_: Exception) {}
            }
        }
        throw (lastException ?: IOException("No se pudo conectar a los auriculares"))
    }

    private suspend fun readLoop(inputStream: InputStream) {
        val buffer = ByteArray(1024)
        while (coroutineContext.isActive) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead > 0) {
                val readData = buffer.copyOfRange(0, bytesRead)
                _rawBytesReceived.emit(readData)
            } else if (bytesRead == -1) {

                throw IOException("Socket closed (EOF)")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun write(bytes: ByteArray): Boolean {
        val currentOutput = outputStream
        if (_connectionState.value != ConnectionState.Connected || currentOutput == null) {
            return false
        }
        return try {
            currentOutput.write(bytes)
            currentOutput.flush()
            true
        } catch (e: IOException) {
            false
        }
    }

    fun disconnect() {
        isExplicitDisconnect = true
        connectionJob?.cancel()
        connectionJob = null
        cleanupSocket(socket)
        socket = null
        outputStream = null
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun cleanupSocket(s: BluetoothSocket?) {
        try {
            s?.close()
        } catch (e: IOException) {

        }
    }
}

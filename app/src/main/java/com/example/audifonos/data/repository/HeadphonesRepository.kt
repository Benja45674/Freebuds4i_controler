package com.example.audifonos.data.repository

import com.example.audifonos.data.bluetooth.ConnectionState
import com.example.audifonos.data.bluetooth.MbbBluetoothManager
import com.example.audifonos.data.protocol.*
import com.example.audifonos.domain.model.HeadphonesUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HeadphonesRepository(
    private val bluetoothManager: MbbBluetoothManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val parser = MbbParser()

    private val _uiState = MutableStateFlow(HeadphonesUiState())
    val uiState: StateFlow<HeadphonesUiState> = _uiState.asStateFlow()

    private var queryJob: kotlinx.coroutines.Job? = null

    init {

        scope.launch {
            bluetoothManager.connectionState.collect { connState ->
                val stateStr = when (connState) {
                    is ConnectionState.Disconnected -> "Desconectado"
                    is ConnectionState.Connecting -> "Conectando"
                    is ConnectionState.Connected -> "Conectado"
                    is ConnectionState.Error -> "No encontrado"
                }
                val errMsg = if (connState is ConnectionState.Error) connState.message else null

                _uiState.update {
                    it.copy(
                        connectionState = stateStr,
                        connectionErrorMessage = errMsg
                    )
                }

                if (connState is ConnectionState.Connected) {
                    queryJob?.cancel()
                    queryJob = scope.launch {
                        sendInitialQueries()
                    }
                } else if (connState is ConnectionState.Disconnected || connState is ConnectionState.Error) {
                    queryJob?.cancel()
                    queryJob = null
                    parser.reset()
                }
            }
        }

        scope.launch {
            bluetoothManager.rawBytesReceived.collect { bytes ->
                val frames = parser.onBytesReceived(bytes)
                for (frame in frames) {
                    processIncomingFrame(frame)
                }
            }
        }
    }

    fun connect(macAddress: String) {
        bluetoothManager.connect(macAddress)
    }

    fun disconnect() {
        queryJob?.cancel()
        queryJob = null
        bluetoothManager.disconnect()
    }

    fun getBondedDevices(): List<android.bluetooth.BluetoothDevice> {
        return bluetoothManager.getBondedDevices()
    }

    fun setAncMode(mode: AncMode) {
        val frame = MbbCommand.createSetAnc(mode)
        bluetoothManager.write(frame.toByteArray())

        _uiState.update { it.copy(ancMode = mode) }
    }

    fun setWearDetection(enabled: Boolean) {
        val frame = MbbCommand.createWriteWearDetection(enabled)
        bluetoothManager.write(frame.toByteArray())

        _uiState.update { it.copy(wearDetectionEnabled = enabled) }
    }

    fun setDoubleTapActions(left: DoubleTapAction, right: DoubleTapAction) {
        val frame = MbbCommand.createWriteDoubleTap(left, right)
        bluetoothManager.write(frame.toByteArray())

        _uiState.update { it.copy(doubleTapLeft = left, doubleTapRight = right) }
    }

    fun setHoldAction(action: HoldAction) {
        val frame = MbbCommand.createWriteHold(action)
        bluetoothManager.write(frame.toByteArray())

        _uiState.update { it.copy(holdAction = action) }
    }

    fun setAncToggleLoop(loop: AncToggleLoop) {
        val frame = MbbCommand.createWriteAncToggleLoop(loop)
        bluetoothManager.write(frame.toByteArray())

        _uiState.update { it.copy(ancToggleLoop = loop) }
    }

    private suspend fun sendInitialQueries() {
        delay(300)
        sendFrame(MbbCommand.createQueryBattery())
        delay(100)
        sendFrame(MbbCommand.createGetAnc())
        delay(100)
        sendFrame(MbbCommand.createReadWearDetection())
        delay(100)
        sendFrame(MbbCommand.createReadDoubleTap())
        delay(100)
        sendFrame(MbbCommand.createReadHold())
        delay(100)
        sendFrame(MbbCommand.createReadAncToggleLoop())
    }

    private fun sendFrame(frame: MbbFrame) {
        bluetoothManager.write(frame.toByteArray())
    }

    private fun processIncomingFrame(frame: MbbFrame) {
        when {

            frame.serviceId == MbbCommand.SERVICE_BATTERY &&
            (frame.commandId == MbbCommand.CMD_QUERY_BATTERY || frame.commandId == MbbCommand.CMD_REPORT_BATTERY) -> {
                MbbCommand.parseBatteryState(frame)?.let { state ->
                    _uiState.update {
                        it.copy(
                            batteryLowest = state.lowestPercent,
                            batteryLeft = state.leftPercent,
                            batteryRight = state.rightPercent,
                            batteryCase = state.casePercent,
                            isLeftCharging = state.leftCharging,
                            isRightCharging = state.rightCharging,
                            isCaseCharging = state.caseCharging
                        )
                    }
                }
            }

            frame.serviceId == MbbCommand.SERVICE_ANC_WEAR && frame.commandId == MbbCommand.CMD_GET_ANC -> {
                MbbCommand.parseAncMode(frame)?.let { mode ->
                    _uiState.update { it.copy(ancMode = mode) }
                }
            }

            frame.serviceId == MbbCommand.SERVICE_ANC_WEAR && frame.commandId == MbbCommand.CMD_GET_WEAR -> {
                MbbCommand.parseWearDetection(frame)?.let { enabled ->
                    _uiState.update { it.copy(wearDetectionEnabled = enabled) }
                }
            }

            frame.serviceId == MbbCommand.SERVICE_ANC_WEAR && frame.commandId == MbbCommand.CMD_EVENT_WEAR -> {
                MbbCommand.parseWearEvent(frame)?.let { event ->
                    _uiState.update {
                        if (event.isLeft) {
                            it.copy(isLeftPlaced = event.isPlaced)
                        } else {
                            it.copy(isRightPlaced = event.isPlaced)
                        }
                    }
                }
            }

            frame.serviceId == MbbCommand.SERVICE_BATTERY && frame.commandId == MbbCommand.CMD_READ_DOUBLE_TAP -> {
                MbbCommand.parseDoubleTapActions(frame)?.let { actions ->
                    _uiState.update {
                        it.copy(
                            doubleTapLeft = actions.first,
                            doubleTapRight = actions.second
                        )
                    }
                }
            }

            frame.serviceId == MbbCommand.SERVICE_ANC_WEAR && frame.commandId == MbbCommand.CMD_READ_HOLD -> {
                MbbCommand.parseHoldAction(frame)?.let { action ->
                    _uiState.update { it.copy(holdAction = action) }
                }
            }

            frame.serviceId == MbbCommand.SERVICE_ANC_WEAR && frame.commandId == MbbCommand.CMD_READ_ANC_LOOP -> {
                MbbCommand.parseAncToggleLoop(frame)?.let { loop ->
                    _uiState.update { it.copy(ancToggleLoop = loop) }
                }
            }
        }
    }
}

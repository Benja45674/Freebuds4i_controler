package com.example.audifonos.domain.model

import com.example.audifonos.data.protocol.AncMode
import com.example.audifonos.data.protocol.AncToggleLoop
import com.example.audifonos.data.protocol.DoubleTapAction
import com.example.audifonos.data.protocol.HoldAction

data class HeadphonesUiState(
    val connectionState: String = "Desconectado",
    val connectionErrorMessage: String? = null,
    val batteryLowest: Int = 0,
    val batteryLeft: Int = 0,
    val batteryRight: Int = 0,
    val batteryCase: Int = 0,
    val isLeftCharging: Boolean = false,
    val isRightCharging: Boolean = false,
    val isCaseCharging: Boolean = false,
    val ancMode: AncMode = AncMode.OFF,
    val wearDetectionEnabled: Boolean = false,
    val isLeftPlaced: Boolean = false,
    val isRightPlaced: Boolean = false,
    val doubleTapLeft: DoubleTapAction = DoubleTapAction.NONE,
    val doubleTapRight: DoubleTapAction = DoubleTapAction.NONE,
    val holdAction: HoldAction = HoldAction.NONE,
    val ancToggleLoop: AncToggleLoop = AncToggleLoop.NOISE_CANCELLING_TRANSPARENCY_OFF
)

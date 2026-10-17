package com.example.audifonos.data.protocol

data class BatteryState(
    val lowestPercent: Int,
    val leftPercent: Int,
    val rightPercent: Int,
    val casePercent: Int,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean
)

enum class AncMode(val mbbCode: Int) {
    OFF(0),
    NOISE_CANCELLING(1),
    TRANSPARENCY(2);

    companion object {
        fun fromMbbCode(mbbCode: Int): AncMode =
            entries.firstOrNull { it.mbbCode == mbbCode } ?: OFF
    }
}

enum class DoubleTapAction(val mbbCode: Int) {
    NONE(255),
    VOICE_ASSISTANT(0),
    PLAY_PAUSE(1),
    NEXT_TRACK(2),
    PREVIOUS_TRACK(7);

    companion object {
        fun fromMbbCode(mbbCode: Int): DoubleTapAction =
            entries.firstOrNull { it.mbbCode == mbbCode } ?: NONE
    }
}

enum class HoldAction(val mbbCode: Int) {
    NONE(255),
    TOGGLE_ANC(10);

    companion object {
        fun fromMbbCode(mbbCode: Int): HoldAction =
            entries.firstOrNull { it.mbbCode == mbbCode } ?: NONE
    }
}

enum class AncToggleLoop(val mbbCode: Int) {
    OFF_NOISE_CANCELLING(1),
    NOISE_CANCELLING_TRANSPARENCY_OFF(2),
    NOISE_CANCELLING_TRANSPARENCY(3),
    OFF_TRANSPARENCY(4);

    companion object {
        fun fromMbbCode(mbbCode: Int): AncToggleLoop =
            entries.firstOrNull { it.mbbCode == mbbCode } ?: NOISE_CANCELLING_TRANSPARENCY_OFF
    }
}

data class WearEvent(
    val isLeft: Boolean,
    val isPlaced: Boolean
)

object MbbCommand {

    const val SERVICE_BATTERY: Byte = 1
    const val SERVICE_ANC_WEAR: Byte = 43

    const val CMD_QUERY_BATTERY: Byte = 8
    const val CMD_REPORT_BATTERY: Byte = 39

    const val CMD_SET_ANC: Byte = 4
    const val CMD_GET_ANC: Byte = 42

    const val CMD_SET_WEAR: Byte = 16
    const val CMD_GET_WEAR: Byte = 17
    const val CMD_EVENT_WEAR: Byte = 3

    const val CMD_WRITE_DOUBLE_TAP: Byte = 31
    const val CMD_READ_DOUBLE_TAP: Byte = 32

    const val CMD_WRITE_HOLD: Byte = 22
    const val CMD_READ_HOLD: Byte = 23

    const val CMD_WRITE_ANC_LOOP: Byte = 24
    const val CMD_READ_ANC_LOOP: Byte = 25

    fun createQueryBattery(): MbbFrame = MbbFrame(SERVICE_BATTERY, CMD_QUERY_BATTERY)

    fun createSetAnc(mode: AncMode): MbbFrame {
        val strength = if (mode == AncMode.OFF) 0.toByte() else 255.toByte()
        val tlvValue = byteArrayOf(mode.mbbCode.toByte(), strength)
        val tlv = MbbTlv(1.toByte(), tlvValue)
        return MbbFrame(SERVICE_ANC_WEAR, CMD_SET_ANC, tlv.toByteArray())
    }

    fun createGetAnc(): MbbFrame = MbbFrame(SERVICE_ANC_WEAR, CMD_GET_ANC)

    fun createReadWearDetection(): MbbFrame = MbbFrame(SERVICE_ANC_WEAR, CMD_GET_WEAR)

    fun createWriteWearDetection(enable: Boolean): MbbFrame {
        val value = byteArrayOf(if (enable) 1.toByte() else 0.toByte())
        val tlv = MbbTlv(1.toByte(), value)
        return MbbFrame(SERVICE_ANC_WEAR, CMD_SET_WEAR, tlv.toByteArray())
    }

    fun createReadDoubleTap(): MbbFrame = MbbFrame(SERVICE_BATTERY, CMD_READ_DOUBLE_TAP)

    fun createWriteDoubleTap(left: DoubleTapAction, right: DoubleTapAction): MbbFrame {
        val tlvLeft = MbbTlv(1.toByte(), byteArrayOf(left.mbbCode.toByte()))
        val tlvRight = MbbTlv(2.toByte(), byteArrayOf(right.mbbCode.toByte()))
        val payload = tlvLeft.toByteArray() + tlvRight.toByteArray()
        return MbbFrame(SERVICE_BATTERY, CMD_WRITE_DOUBLE_TAP, payload)
    }

    fun createReadHold(): MbbFrame = MbbFrame(SERVICE_ANC_WEAR, CMD_READ_HOLD)

    fun createWriteHold(action: HoldAction): MbbFrame {
        val tlv = MbbTlv(1.toByte(), byteArrayOf(action.mbbCode.toByte()))
        return MbbFrame(SERVICE_ANC_WEAR, CMD_WRITE_HOLD, tlv.toByteArray())
    }

    fun createReadAncToggleLoop(): MbbFrame = MbbFrame(SERVICE_ANC_WEAR, CMD_READ_ANC_LOOP)

    fun createWriteAncToggleLoop(loop: AncToggleLoop): MbbFrame {
        val tlv1 = MbbTlv(1.toByte(), byteArrayOf(loop.mbbCode.toByte()))
        val tlv2 = MbbTlv(2.toByte(), byteArrayOf(loop.mbbCode.toByte()))
        val payload = tlv1.toByteArray() + tlv2.toByteArray()
        return MbbFrame(SERVICE_ANC_WEAR, CMD_WRITE_ANC_LOOP, payload)
    }

    fun parseBatteryState(frame: MbbFrame): BatteryState? {
        if (frame.serviceId != SERVICE_BATTERY ||
            (frame.commandId != CMD_QUERY_BATTERY && frame.commandId != CMD_REPORT_BATTERY)) {
            return null
        }
        val tlvs = MbbTlv.parseTlvs(frame.payload)
        val arg1 = tlvs.firstOrNull { it.type == 1.toByte() } ?: return null
        val arg2 = tlvs.firstOrNull { it.type == 2.toByte() } ?: return null
        val arg3 = tlvs.firstOrNull { it.type == 3.toByte() } ?: return null

        if (arg1.value.size != 1 || arg2.value.size != 3 || arg3.value.size != 3) {
            return null
        }

        val lowest = arg1.value[0].toInt() and 0xFF
        val left = arg2.value[0].toInt() and 0xFF
        val right = arg2.value[1].toInt() and 0xFF
        val case = arg2.value[2].toInt() and 0xFF

        val leftCharging = arg3.value[0] == 1.toByte()
        val rightCharging = arg3.value[1] == 1.toByte()
        val caseCharging = arg3.value[2] == 1.toByte()

        return BatteryState(lowest, left, right, case, leftCharging, rightCharging, caseCharging)
    }

    fun parseAncMode(frame: MbbFrame): AncMode? {
        if (frame.serviceId != SERVICE_ANC_WEAR || frame.commandId != CMD_GET_ANC) {
            return null
        }
        val tlvs = MbbTlv.parseTlvs(frame.payload)
        val arg1 = tlvs.firstOrNull { it.type == 1.toByte() } ?: return null
        if (arg1.value.size != 2) return null

        val stateByte1 = arg1.value[0].toInt()
        val stateByte2 = arg1.value[1].toInt()

        return when {
            stateByte1 == 4 && stateByte2 == 1 -> AncMode.NOISE_CANCELLING
            stateByte1 == 0 && stateByte2 == 0 -> AncMode.OFF
            stateByte1 == 0 && stateByte2 == 2 -> AncMode.TRANSPARENCY
            else -> null
        }
    }

    fun parseWearDetection(frame: MbbFrame): Boolean? {
        if (frame.serviceId != SERVICE_ANC_WEAR || frame.commandId != CMD_GET_WEAR) {
            return null
        }
        val tlvs = MbbTlv.parseTlvs(frame.payload)
        val arg1 = tlvs.firstOrNull { it.type == 1.toByte() } ?: return null
        if (arg1.value.size != 1) return null
        return arg1.value[0] == 1.toByte()
    }

    fun parseWearEvent(frame: MbbFrame): WearEvent? {
        if (frame.serviceId != SERVICE_ANC_WEAR || frame.commandId != CMD_EVENT_WEAR) {
            return null
        }
        val tlvs = MbbTlv.parseTlvs(frame.payload)
        val wearTlv = tlvs.firstOrNull { it.type == 8.toByte() || it.type == 9.toByte() } ?: return null
        if (wearTlv.value.size != 1) return null

        val isLeft = wearTlv.type == 8.toByte()
        val isPlaced = wearTlv.value[0] == 1.toByte()

        return WearEvent(isLeft, isPlaced)
    }

    fun parseDoubleTapActions(frame: MbbFrame): Pair<DoubleTapAction, DoubleTapAction>? {
        if (frame.serviceId != SERVICE_BATTERY || frame.commandId != CMD_READ_DOUBLE_TAP) {
            return null
        }
        val tlvs = MbbTlv.parseTlvs(frame.payload)
        val arg1 = tlvs.firstOrNull { it.type == 1.toByte() } ?: return null
        val arg2 = tlvs.firstOrNull { it.type == 2.toByte() } ?: return null

        if (arg1.value.size != 1 || arg2.value.size != 1) return null

        val leftAction = DoubleTapAction.fromMbbCode(arg1.value[0].toInt() and 0xFF)
        val rightAction = DoubleTapAction.fromMbbCode(arg2.value[0].toInt() and 0xFF)

        return Pair(leftAction, rightAction)
    }

    fun parseHoldAction(frame: MbbFrame): HoldAction? {
        if (frame.serviceId != SERVICE_ANC_WEAR || frame.commandId != CMD_READ_HOLD) {
            return null
        }
        val tlvs = MbbTlv.parseTlvs(frame.payload)
        val arg1 = tlvs.firstOrNull { it.type == 1.toByte() } ?: return null
        if (arg1.value.size != 1) return null
        return HoldAction.fromMbbCode(arg1.value[0].toInt() and 0xFF)
    }

    fun parseAncToggleLoop(frame: MbbFrame): AncToggleLoop? {
        if (frame.serviceId != SERVICE_ANC_WEAR || frame.commandId != CMD_READ_ANC_LOOP) {
            return null
        }
        val tlvs = MbbTlv.parseTlvs(frame.payload)
        val arg1 = tlvs.firstOrNull { it.type == 1.toByte() } ?: return null
        if (arg1.value.size != 1) return null
        return AncToggleLoop.fromMbbCode(arg1.value[0].toInt() and 0xFF)
    }
}

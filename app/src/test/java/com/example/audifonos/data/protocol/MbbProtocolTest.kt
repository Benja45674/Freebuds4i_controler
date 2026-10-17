package com.example.audifonos.data.protocol

import org.junit.Assert.*
import org.junit.Test

class MbbProtocolTest {

    @Test
    fun testCrc16XModemStandardValue() {
        val testInput = "123456789".toByteArray(Charsets.US_ASCII)
        val expectedCrc = 0x31C3
        assertEquals(expectedCrc, Crc16XModem.calculate(testInput))
    }

    @Test
    fun testCrc16XModemZeroPaddingBugPrevention() {

        val crcWithValue5 = 5
        val bytes5 = Crc16XModem.toBytes(crcWithValue5)
        assertArrayEquals(byteArrayOf(0x00.toByte(), 0x05.toByte()), bytes5)

        val bytes10 = Crc16XModem.toBytes(10)
        assertArrayEquals(byteArrayOf(0x00.toByte(), 0x0A.toByte()), bytes10)

        val bytes31C3 = Crc16XModem.toBytes(0x31C3)
        assertArrayEquals(byteArrayOf(0x31.toByte(), 0xC3.toByte()), bytes31C3)
    }

    @Test
    fun testParserReassemblesFragmentedFrames() {
        val parser = MbbParser()
        val frame = MbbCommand.createQueryBattery()
        val fullBytes = frame.toByteArray()

        val frag1 = fullBytes.copyOfRange(0, 3)
        val frag2 = fullBytes.copyOfRange(3, 6)
        val frag3 = fullBytes.copyOfRange(6, fullBytes.size)

        var parsed = parser.onBytesReceived(frag1)
        assertTrue(parsed.isEmpty())
        assertEquals(3, parser.getAccumulatedSize())

        parsed = parser.onBytesReceived(frag2)
        assertTrue(parsed.isEmpty())
        assertEquals(6, parser.getAccumulatedSize())

        parsed = parser.onBytesReceived(frag3)
        assertEquals(1, parsed.size)
        assertEquals(frame, parsed[0])
        assertEquals(0, parser.getAccumulatedSize())
    }

    @Test
    fun testParserGarbageToleranceAndSelfHealing() {
        val parser = MbbParser()
        val frame = MbbCommand.createQueryBattery()
        val frameBytes = frame.toByteArray()

        val garbageBefore = byteArrayOf(0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x5A.toByte())
        val garbageAfter = byteArrayOf(0x77.toByte(), 0x88.toByte(), 0x99.toByte())
        val input = garbageBefore + frameBytes + garbageAfter

        val parsed = parser.onBytesReceived(input)

        assertEquals(1, parsed.size)
        assertEquals(frame, parsed[0])
    }

    @Test
    fun testParserMultipleFramesInSingleChunk() {
        val parser = MbbParser()
        val frame1 = MbbCommand.createQueryBattery()
        val frame2 = MbbCommand.createGetAnc()

        val chunk = frame1.toByteArray() + frame2.toByteArray()
        val parsed = parser.onBytesReceived(chunk)

        assertEquals(2, parsed.size)
        assertEquals(frame1, parsed[0])
        assertEquals(frame2, parsed[1])
        assertEquals(0, parser.getAccumulatedSize())
    }

    @Test
    fun testBatteryQueryAndResponse() {

        val queryFrame = MbbCommand.createQueryBattery()
        assertEquals(MbbCommand.SERVICE_BATTERY, queryFrame.serviceId)
        assertEquals(MbbCommand.CMD_QUERY_BATTERY, queryFrame.commandId)
        assertEquals(0, queryFrame.payload.size)

        val arg1 = MbbTlv(1.toByte(), byteArrayOf(50.toByte()))
        val arg2 = MbbTlv(2.toByte(), byteArrayOf(60.toByte(), 70.toByte(), 80.toByte()))
        val arg3 = MbbTlv(3.toByte(), byteArrayOf(1.toByte(), 0.toByte(), 1.toByte()))

        val payload = arg1.toByteArray() + arg2.toByteArray() + arg3.toByteArray()

        val responseFrame = MbbFrame(
            serviceId = MbbCommand.SERVICE_BATTERY,
            commandId = MbbCommand.CMD_REPORT_BATTERY,
            payload = payload
        )

        val state = MbbCommand.parseBatteryState(responseFrame)
        assertNotNull(state)
        assertEquals(50, state!!.lowestPercent)
        assertEquals(60, state.leftPercent)
        assertEquals(70, state.rightPercent)
        assertEquals(80, state.casePercent)
        assertTrue(state.leftCharging)
        assertFalse(state.rightCharging)
        assertTrue(state.caseCharging)
    }

    @Test
    fun testAncSetAndGet() {

        val setFrame = MbbCommand.createSetAnc(AncMode.NOISE_CANCELLING)
        assertEquals(MbbCommand.SERVICE_ANC_WEAR, setFrame.serviceId)
        assertEquals(MbbCommand.CMD_SET_ANC, setFrame.commandId)

        val setTlvs = MbbTlv.parseTlvs(setFrame.payload)
        assertEquals(1, setTlvs.size)
        assertEquals(1.toByte(), setTlvs[0].type)
        assertArrayEquals(byteArrayOf(1.toByte(), 255.toByte()), setTlvs[0].value)

        val getFrame = MbbCommand.createGetAnc()
        assertEquals(MbbCommand.SERVICE_ANC_WEAR, getFrame.serviceId)
        assertEquals(MbbCommand.CMD_GET_ANC, getFrame.commandId)
        assertEquals(0, getFrame.payload.size)

        val responseTlv = MbbTlv(1.toByte(), byteArrayOf(4.toByte(), 1.toByte()))
        val responseFrame = MbbFrame(
            serviceId = MbbCommand.SERVICE_ANC_WEAR,
            commandId = MbbCommand.CMD_GET_ANC,
            payload = responseTlv.toByteArray()
        )
        val mode = MbbCommand.parseAncMode(responseFrame)
        assertEquals(AncMode.NOISE_CANCELLING, mode)
    }

    @Test
    fun testWearDetectionReadWriteAndEvent() {

        val readFrame = MbbCommand.createReadWearDetection()
        assertEquals(MbbCommand.SERVICE_ANC_WEAR, readFrame.serviceId)
        assertEquals(MbbCommand.CMD_GET_WEAR, readFrame.commandId)

        val readResponseTlv = MbbTlv(1.toByte(), byteArrayOf(1.toByte()))
        val readResponseFrame = MbbFrame(
            serviceId = MbbCommand.SERVICE_ANC_WEAR,
            commandId = MbbCommand.CMD_GET_WEAR,
            payload = readResponseTlv.toByteArray()
        )
        assertTrue(MbbCommand.parseWearDetection(readResponseFrame) == true)

        val writeFrame = MbbCommand.createWriteWearDetection(false)
        assertEquals(MbbCommand.SERVICE_ANC_WEAR, writeFrame.serviceId)
        assertEquals(MbbCommand.CMD_SET_WEAR, writeFrame.commandId)
        val writeTlvs = MbbTlv.parseTlvs(writeFrame.payload)
        assertArrayEquals(byteArrayOf(0.toByte()), writeTlvs[0].value)

        val eventPayload = byteArrayOf(9.toByte(), 1.toByte(), 1.toByte())
        val eventFrame = MbbFrame(
            serviceId = MbbCommand.SERVICE_ANC_WEAR,
            commandId = MbbCommand.CMD_EVENT_WEAR,
            payload = eventPayload
        )
        val event = MbbCommand.parseWearEvent(eventFrame)
        assertNotNull(event)
        assertFalse(event!!.isLeft)
        assertTrue(event.isPlaced)
    }

    @Test
    fun testGesturesDoubleTap() {

        val writeFrame = MbbCommand.createWriteDoubleTap(DoubleTapAction.PLAY_PAUSE, DoubleTapAction.NEXT_TRACK)
        assertEquals(MbbCommand.SERVICE_BATTERY, writeFrame.serviceId)
        assertEquals(MbbCommand.CMD_WRITE_DOUBLE_TAP, writeFrame.commandId)

        val tlvs = MbbTlv.parseTlvs(writeFrame.payload)
        assertEquals(2, tlvs.size)
        assertEquals(1.toByte(), tlvs[0].type)
        assertEquals(1.toByte(), tlvs[0].value[0])
        assertEquals(2.toByte(), tlvs[1].type)
        assertEquals(2.toByte(), tlvs[1].value[0])

        val readResponsePayload = MbbTlv(1.toByte(), byteArrayOf(7.toByte())).toByteArray() +
                                  MbbTlv(2.toByte(), byteArrayOf(0.toByte())).toByteArray()
        val readResponseFrame = MbbFrame(
            serviceId = MbbCommand.SERVICE_BATTERY,
            commandId = MbbCommand.CMD_READ_DOUBLE_TAP,
            payload = readResponsePayload
        )
        val gestures = MbbCommand.parseDoubleTapActions(readResponseFrame)
        assertNotNull(gestures)
        assertEquals(DoubleTapAction.PREVIOUS_TRACK, gestures!!.first)
        assertEquals(DoubleTapAction.VOICE_ASSISTANT, gestures.second)
    }

    @Test
    fun testGesturesHoldAndAncLoop() {

        val writeHoldFrame = MbbCommand.createWriteHold(HoldAction.TOGGLE_ANC)
        assertEquals(MbbCommand.SERVICE_ANC_WEAR, writeHoldFrame.serviceId)
        assertEquals(MbbCommand.CMD_WRITE_HOLD, writeHoldFrame.commandId)

        val readHoldResponseTlv = MbbTlv(1.toByte(), byteArrayOf(10.toByte()))
        val readHoldResponseFrame = MbbFrame(
            serviceId = MbbCommand.SERVICE_ANC_WEAR,
            commandId = MbbCommand.CMD_READ_HOLD,
            payload = readHoldResponseTlv.toByteArray()
        )
        assertEquals(HoldAction.TOGGLE_ANC, MbbCommand.parseHoldAction(readHoldResponseFrame))

        val writeLoopFrame = MbbCommand.createWriteAncToggleLoop(AncToggleLoop.NOISE_CANCELLING_TRANSPARENCY)
        assertEquals(MbbCommand.SERVICE_ANC_WEAR, writeLoopFrame.serviceId)
        assertEquals(MbbCommand.CMD_WRITE_ANC_LOOP, writeLoopFrame.commandId)

        val readLoopResponsePayload = MbbTlv(1.toByte(), byteArrayOf(3.toByte())).toByteArray() +
                                      MbbTlv(2.toByte(), byteArrayOf(3.toByte())).toByteArray()
        val readLoopResponseFrame = MbbFrame(
            serviceId = MbbCommand.SERVICE_ANC_WEAR,
            commandId = MbbCommand.CMD_READ_ANC_LOOP,
            payload = readLoopResponsePayload
        )
        assertEquals(AncToggleLoop.NOISE_CANCELLING_TRANSPARENCY, MbbCommand.parseAncToggleLoop(readLoopResponseFrame))
    }
}

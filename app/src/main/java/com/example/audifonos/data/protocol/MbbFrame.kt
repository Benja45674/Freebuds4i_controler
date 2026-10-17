package com.example.audifonos.data.protocol

data class MbbFrame(
    val serviceId: Byte,
    val commandId: Byte,
    val payload: ByteArray = ByteArray(0)
) {

    fun toByteArray(): ByteArray {
        val payloadLen = payload.size
        val lengthByte = (payloadLen + 3).toByte()

        val frameWithoutCrc = ByteArray(6 + payloadLen)
        frameWithoutCrc[0] = 0x5A.toByte()
        frameWithoutCrc[1] = 0x00.toByte()
        frameWithoutCrc[2] = lengthByte
        frameWithoutCrc[3] = 0x00.toByte()
        frameWithoutCrc[4] = serviceId
        frameWithoutCrc[5] = commandId
        System.arraycopy(payload, 0, frameWithoutCrc, 6, payloadLen)

        val crc = Crc16XModem.calculate(frameWithoutCrc)
        val crcBytes = Crc16XModem.toBytes(crc)

        val fullFrame = ByteArray(frameWithoutCrc.size + 2)
        System.arraycopy(frameWithoutCrc, 0, fullFrame, 0, frameWithoutCrc.size)
        fullFrame[fullFrame.size - 2] = crcBytes[0]
        fullFrame[fullFrame.size - 1] = crcBytes[1]

        return fullFrame
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MbbFrame

        if (serviceId != other.serviceId) return false
        if (commandId != other.commandId) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = serviceId.toInt()
        result = 31 * result + commandId.toInt()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {

        fun fromByteArray(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): MbbFrame? {
            if (length < 8) return null
            if (bytes[offset] != 0x5A.toByte() || bytes[offset + 1] != 0x00.toByte()) return null

            val lenByte = bytes[offset + 2].toInt() and 0xFF
            val magic2 = bytes[offset + 3]
            if (magic2 != 0x00.toByte()) return null

            val totalPacketLen = lenByte + 5
            if (length < totalPacketLen) return null

            val serviceId = bytes[offset + 4]
            val commandId = bytes[offset + 5]
            val payloadLen = lenByte - 3
            if (payloadLen < 0) return null

            val payload = bytes.copyOfRange(offset + 6, offset + 6 + payloadLen)

            val calculatedCrc = Crc16XModem.calculate(bytes, offset, 6 + payloadLen)
            val expectedCrcBytes = Crc16XModem.toBytes(calculatedCrc)

            val actualCrcHigh = bytes[offset + 6 + payloadLen]
            val actualCrcLow = bytes[offset + 7 + payloadLen]

            if (actualCrcHigh != expectedCrcBytes[0] || actualCrcLow != expectedCrcBytes[1]) {

                return null
            }

            return MbbFrame(serviceId, commandId, payload)
        }
    }
}

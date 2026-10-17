package com.example.audifonos.data.protocol

object Crc16XModem {

    fun calculate(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Int {
        var crc = 0x0000
        for (i in offset until (offset + length)) {
            val b = bytes[i].toInt()
            crc = crc xor (b and 0xFF shl 8)
            for (j in 0 until 8) {
                crc = if (crc and 0x8000 != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }

    fun toBytes(crc: Int): ByteArray {
        val hex = (crc and 0xFFFF).toString(16).padStart(4, '0')
        val high = hex.substring(0, 2).toInt(16).toByte()
        val low = hex.substring(2, 4).toInt(16).toByte()
        return byteArrayOf(high, low)
    }
}

package com.example.audifonos.data.protocol

class MbbParser {
    private var accumulator = ByteArray(0)

    fun onBytesReceived(newBytes: ByteArray): List<MbbFrame> {

        val current = ByteArray(accumulator.size + newBytes.size)
        System.arraycopy(accumulator, 0, current, 0, accumulator.size)
        System.arraycopy(newBytes, 0, current, accumulator.size, newBytes.size)
        accumulator = current

        val frames = mutableListOf<MbbFrame>()
        var offset = 0

        while (offset < accumulator.size) {

            var magicIdx = -1
            for (i in offset until (accumulator.size - 1)) {
                if (accumulator[i] == 0x5A.toByte() && accumulator[i + 1] == 0x00.toByte()) {
                    magicIdx = i
                    break
                }
            }

            if (magicIdx == -1) {

                if (accumulator.isNotEmpty() && accumulator.last() == 0x5A.toByte()) {
                    accumulator = byteArrayOf(0x5A.toByte())
                } else {
                    accumulator = ByteArray(0)
                }
                break
            }

            if (magicIdx + 2 >= accumulator.size) {

                accumulator = accumulator.copyOfRange(magicIdx, accumulator.size)
                break
            }

            val lenByte = accumulator[magicIdx + 2].toInt() and 0xFF
            val totalPacketLen = lenByte + 5

            if (magicIdx + totalPacketLen > accumulator.size) {

                accumulator = accumulator.copyOfRange(magicIdx, accumulator.size)
                break
            }

            val frameBytes = accumulator.copyOfRange(magicIdx, magicIdx + totalPacketLen)
            val frame = MbbFrame.fromByteArray(frameBytes)
            if (frame != null) {
                frames.add(frame)

                accumulator = accumulator.copyOfRange(magicIdx + totalPacketLen, accumulator.size)
                offset = 0
            } else {

                accumulator = accumulator.copyOfRange(magicIdx + 1, accumulator.size)
                offset = 0
            }
        }

        return frames
    }

    fun reset() {
        accumulator = ByteArray(0)
    }

    fun getAccumulatedSize(): Int = accumulator.size
}

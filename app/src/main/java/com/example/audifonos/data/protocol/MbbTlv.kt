package com.example.audifonos.data.protocol

data class MbbTlv(
    val type: Byte,
    val value: ByteArray
) {
    val length: Byte
        get() = value.size.toByte()

    fun toByteArray(): ByteArray {
        val result = ByteArray(2 + value.size)
        result[0] = type
        result[1] = length
        System.arraycopy(value, 0, result, 2, value.size)
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MbbTlv

        if (type != other.type) return false
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.toInt()
        result = 31 * result + value.contentHashCode()
        return result
    }

    companion object {
        fun parseTlvs(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): List<MbbTlv> {
            val tlvs = mutableListOf<MbbTlv>()
            var i = offset
            val end = offset + length
            while (i + 2 <= end) {
                val type = bytes[i]
                val len = bytes[i + 1].toInt() and 0xFF
                if (i + 2 + len > end) {
                    break
                }
                val value = bytes.copyOfRange(i + 2, i + 2 + len)
                tlvs.add(MbbTlv(type, value))
                i += 2 + len
            }
            return tlvs
        }
    }
}

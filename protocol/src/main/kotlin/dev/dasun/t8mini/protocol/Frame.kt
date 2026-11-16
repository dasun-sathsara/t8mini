package dev.dasun.t8mini.protocol

/**
 * Wire format for the Tronsmart T8 Mini control channel.
 *
 * Request: 08 EE 00 00 00 01 <cmd> <len lo> <len hi> <payload...> <sum>
 * Reply:   09 FF 00 00 01 01 <cmd> <len lo> <len hi> <payload...> <sum>
 *
 * `len` counts the whole frame including header and checksum, little endian.
 * `sum` is the low byte of the sum of every byte before it.
 */
object Frame {

    const val HEADER_SIZE = 9
    const val TRAILER_SIZE = 1

    private const val REQUEST_0 = 0x08
    private const val REQUEST_1 = 0xEE
    private const val REPLY_0 = 0x09
    private const val REPLY_1 = 0xFF

    fun encode(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val total = HEADER_SIZE + payload.size + TRAILER_SIZE
        val out = ByteArray(total)
        out[0] = REQUEST_0.toByte()
        out[1] = REQUEST_1.toByte()
        out[2] = 0
        out[3] = 0
        out[4] = 0
        out[5] = 1
        out[6] = cmd.toByte()
        out[7] = (total and 0xFF).toByte()
        out[8] = ((total shr 8) and 0xFF).toByte()
        payload.copyInto(out, HEADER_SIZE)
        out[total - 1] = checksum(out, total - 1)
        return out
    }

    fun encode(cmd: Int, vararg payload: Int): ByteArray =
        encode(cmd, ByteArray(payload.size) { payload[it].toByte() })

    fun decode(bytes: ByteArray): Reply? {
        if (bytes.size < HEADER_SIZE + TRAILER_SIZE) return null
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val isReply = b0 == REPLY_0 && b1 == REPLY_1
        val isRequest = b0 == REQUEST_0 && b1 == REQUEST_1
        if (!isReply && !isRequest) return null
        val declared = (bytes[7].toInt() and 0xFF) or ((bytes[8].toInt() and 0xFF) shl 8)
        if (declared != bytes.size) return null
        val expected = checksum(bytes, bytes.size - 1)
        if (expected != bytes[bytes.size - 1]) return null
        val cmd = bytes[6].toInt() and 0xFF
        val payload = bytes.copyOfRange(HEADER_SIZE, bytes.size - TRAILER_SIZE)
        return Reply(cmd = cmd, payload = payload)
    }

    fun checksum(bytes: ByteArray, until: Int): Byte {
        var sum = 0
        for (i in 0 until until) sum += bytes[i].toInt() and 0xFF
        return (sum and 0xFF).toByte()
    }
}

data class Reply(val cmd: Int, val payload: ByteArray) {
    fun payloadInt(): Int = payload.firstOrNull()?.toInt()?.and(0xFF) ?: -1
    override fun equals(other: Any?): Boolean =
        other is Reply && other.cmd == cmd && other.payload.contentEquals(payload)
    override fun hashCode(): Int = 31 * cmd + payload.contentHashCode()
    override fun toString(): String = "Reply(0x%02x, %s)".format(cmd, payload.joinToString(" ") { "%02x".format(it) })
}

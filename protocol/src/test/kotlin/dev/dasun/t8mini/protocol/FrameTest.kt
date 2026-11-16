package dev.dasun.t8mini.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FrameTest {

    private fun hex(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }

    @Test
    fun `led on matches captured frame`() {
        val encoded = Cmds.led(true)
        assertEquals(
            "08 ee 00 00 00 01 98 0b 00 01 9b",
            encoded.joinToString(" ") { "%02x".format(it) },
        )
    }

    @Test
    fun `led off matches captured frame`() {
        val encoded = Cmds.led(false)
        assertEquals(
            "08 ee 00 00 00 01 98 0b 00 00 9a",
            encoded.joinToString(" ") { "%02x".format(it) },
        )
    }

    @Test
    fun `deep bass preset matches captured frame`() {
        val encoded = Cmds.eqPreset(EqPreset.DEEP_BASS)
        assertEquals(
            "08 ee 00 00 00 01 82 10 00 02 00 00 00 00 00 8b",
            encoded.joinToString(" ") { "%02x".format(it) },
        )
    }

    @Test
    fun `timer 15 matches captured frame`() {
        val encoded = Cmds.timer(15)
        assertEquals(
            "08 ee 00 00 00 01 96 0c 00 0f 00 a8",
            encoded.joinToString(" ") { "%02x".format(it) },
        )
    }

    @Test
    fun `decodes a real acknowledgement`() {
        val reply = Frame.decode(hex(0x09, 0xFF, 0, 0, 1, 1, 0x99, 0x0B, 0, 1, 0xAF))
        assertNotNull(reply)
        assertEquals(0x99, reply.cmd)
        assertEquals(1, reply.payloadInt())
    }

    @Test
    fun `rejects a bad checksum`() {
        assertNull(Frame.decode(hex(0x09, 0xFF, 0, 0, 1, 1, 0x99, 0x0B, 0, 1, 0xAE)))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(Frame.decode(hex(0x01, 0x02, 0x03)))
    }
}

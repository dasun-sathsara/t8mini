package dev.dasun.t8mini.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Every frame in here was captured from the speaker over BLE, so these tests
 * pin down behaviour that the vendor app never documented.
 */
class RepliesTest {

    private fun decode(hex: String): Reply {
        val bytes = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        return assertNotNull(Frame.decode(bytes), "frame should decode: $hex")
    }

    @Test
    fun `custom curve echoes the five bands`() {
        val reply = decode("09 ff 00 00 01 01 04 10 00 fe 80 a8 ff 80 80 43")
        assertEquals(
            ReplyEvent.EqBands(listOf(128, 168, 255, 128, 128)),
            Replies.parse(reply),
        )
    }

    @Test
    fun `preset echoes its index`() {
        val reply = decode("09 ff 00 00 01 01 04 10 00 02 00 00 00 00 00 20")
        assertEquals(ReplyEvent.EqPreset(2), Replies.parse(reply))
    }

    @Test
    fun `led on is acknowledged`() {
        val reply = decode("09 ff 00 00 01 01 99 0b 00 01 af")
        assertEquals(ReplyEvent.Led(on = true), Replies.parse(reply))
    }

    @Test
    fun `led mode notification carries only the animation`() {
        val reply = decode("09 ff 00 00 01 01 92 0b 00 02 a9")
        assertEquals(ReplyEvent.LedModeChanged(2), Replies.parse(reply))
    }

    @Test
    fun `timer reports remaining minutes and seconds`() {
        val reply = decode("09 ff 00 00 01 01 97 0f 00 1e 00 1d 00 38 23")
        assertEquals(
            ReplyEvent.Timer(setMinutes = 30, remainingSeconds = 29 * 60 + 56),
            Replies.parse(reply),
        )
    }

    @Test
    fun `disarmed timer reads as zero`() {
        val reply = decode("09 ff 00 00 01 01 97 0f 00 00 00 00 00 00 b0")
        assertEquals(ReplyEvent.Timer(0, 0), Replies.parse(reply))
    }

    @Test
    fun `voice and stereo acknowledgements`() {
        assertEquals(ReplyEvent.Voice(true), Replies.parse(decode("09 ff 00 00 01 01 9c 0b 00 01 b2")))
        assertEquals(ReplyEvent.Tws(false), Replies.parse(0x8C, listOf(0)))
    }
}

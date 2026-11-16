package dev.dasun.t8mini.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurvesTest {

    @Test
    fun `podcast curve fits the five band payload`() {
        val frame = Cmds.eqCustom(Curves.podcast)
        val reply = Frame.decode(frame)
        // The frame is a request, but decoding proves the payload round trips.
        assertEquals(6, reply?.payload?.size)
        assertEquals(0xFE.toByte(), reply?.payload?.get(0))
    }

    @Test
    fun `podcast curve keeps the low end down and the presence band up`() {
        val bands = Curves.podcast
        assertTrue(bands[0] < 128, "60 Hz should be trimmed")
        assertTrue(bands[3] > 128, "3.6 kHz should be lifted")
        assertEquals(128, bands[2], "900 Hz should remain neutral")
        assertEquals(128, bands[4], "14 kHz should remain neutral")
        assertTrue(bands.all { it in 0..255 })
    }

    @Test
    fun `matches recognises the curve it sent, allowing a little drift`() {
        assertTrue(Curves.matchesPodcast(Curves.podcast))
        assertTrue(Curves.matchesPodcast(Curves.podcast.map { it + 1 }))
        assertFalse(Curves.matchesPodcast(listOf(128, 128, 128, 128, 128)))
    }
}

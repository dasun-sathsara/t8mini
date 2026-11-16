package dev.dasun.t8mini.protocol

/**
 * Curves the app sends itself, because the speaker's five built-in presets do not
 * include one for spoken word.
 *
 * Band order matches the firmware: 60 Hz, 225 Hz, 900 Hz, 3.6 kHz, 14 kHz. Each
 * band is 0..255 with 128 flat; the firmware does not document its gain per step,
 * so the resulting gain is unknown and should be adjusted by ear.
 */
object Curves {

    /**
     * Speech clarity.
     *
     * Rumble below 100 Hz carries nothing but handling noise and proximity
     * boom, the 200-400 Hz octave is where voices turn muddy, 2-5 kHz is where
     * intelligibility lives, and sibilance sits above 5 kHz. So: trim the two
     * low bands, hold the mid, lift 3.6 kHz, and ease the top a little.
     */
    val podcast = listOf(96, 104, 128, 160, 104)

    fun matchesPodcast(bands: List<Int>): Boolean =
        bands.size == podcast.size && bands.zip(podcast).all { (a, b) -> kotlin.math.abs(a - b) <= 2 }
}

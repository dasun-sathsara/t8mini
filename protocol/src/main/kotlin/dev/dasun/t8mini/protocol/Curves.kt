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
     * A speech starting point: reduce low-frequency energy, preserve
     * the midrange and upper treble, and raise the presence band.
     * The speaker's filter widths and gain scale are unknown, so these raw
     * values are a listening starting point, not measured gain in decibels.
     */
    val podcast = listOf(80, 104, 128, 144, 128)

    fun matchesPodcast(bands: List<Int>): Boolean =
        bands.size == podcast.size && bands.zip(podcast).all { (a, b) -> kotlin.math.abs(a - b) <= 2 }
}

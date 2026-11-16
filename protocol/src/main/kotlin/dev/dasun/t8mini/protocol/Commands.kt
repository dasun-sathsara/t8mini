package dev.dasun.t8mini.protocol

enum class EqPreset(val label: String, val index: Int) {
    DEFAULT("Default", 0),
    SOUND_PULSE("SoundPulse", 1),
    DEEP_BASS("Deep Bass", 2),
    CLASSICAL("Classical", 3),
    ROCK("Rock", 4),
}

enum class LedMode(val label: String, val index: Int) {
    FASHION_PARTY("Fashion Party", 1),
    DEEP_BREATH("Deep Breath", 2),
    CAROUSEL("Carousel", 3),
}

enum class Input(val label: String, val index: Int) {
    BLUETOOTH("Bluetooth", 0),
    AUX("AUX", 1),
    TF_CARD("TF Card", 2),
}

/** Command bytes observed on the speaker, all verified by replay. */
object Cmd {
    const val STATUS_A = 0x02
    const val STATUS_C = 0x08
    const val STATUS_D = 0x09
    const val EQ = 0x82
    const val LED_MODE = 0x8A
    const val TWS = 0x8B
    const val STATUS_B = 0x8F
    const val INPUT = 0x90
    const val STATUS_E = 0x97
    const val TIMER = 0x96
    const val LED = 0x98
    const val VOICE = 0x9B

    /** Notification: current EQ state, `0x04 [preset,…]` or `0x04 [0xfe,bands…]`. */
    const val EQ_STATE = 0x04

    /** Notification: LED mode after a change, `0x92 [mode]`, 0 meaning off. */
    const val LED_STATE = 0x92

    /** Notification: timer state, `0x97 [set lo,set hi,rem lo,rem hi,seconds]`. */
    const val TIMER_STATE = 0x97

    val statusQueries = listOf(
        STATUS_A, STATUS_B, STATUS_C, STATUS_D, TIMER_STATE, LED_STATE, LED + 1,
    )
}

object Cmds {

    fun eqPreset(preset: EqPreset): ByteArray =
        Frame.encode(Cmd.EQ, preset.index, 0, 0, 0, 0, 0)

    /** Six byte payload: 0xFE marks a custom curve, then five band levels 0..255. */
    fun eqCustom(bands: List<Int>): ByteArray {
        require(bands.size == 5) { "expected five bands" }
        val payload = ByteArray(6)
        payload[0] = 0xFE.toByte()
        for (i in 0..4) payload[i + 1] = bands[i].coerceIn(0, 255).toByte()
        return Frame.encode(Cmd.EQ, payload)
    }

    fun led(on: Boolean): ByteArray = Frame.encode(Cmd.LED, if (on) 1 else 0)

    fun ledMode(mode: LedMode): ByteArray = Frame.encode(Cmd.LED_MODE, mode.index)

    fun timer(minutes: Int): ByteArray =
        Frame.encode(Cmd.TIMER, minutes and 0xFF, (minutes shr 8) and 0xFF)

    fun voice(on: Boolean): ByteArray = Frame.encode(Cmd.VOICE, if (on) 1 else 0)

    fun tws(on: Boolean): ByteArray = Frame.encode(Cmd.TWS, if (on) 1 else 0)

    fun input(input: Input): ByteArray = Frame.encode(Cmd.INPUT, input.index)

    fun status(cmd: Int): ByteArray = Frame.encode(cmd)

    fun statusAll(): List<ByteArray> = Cmd.statusQueries.map(::status)

    /** A set command is acknowledged by a reply whose command byte is request + 1. */
    fun ackFor(cmd: Int): Int = (cmd + 1) and 0xFF
}

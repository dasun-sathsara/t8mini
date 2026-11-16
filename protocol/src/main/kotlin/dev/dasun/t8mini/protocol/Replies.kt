package dev.dasun.t8mini.protocol

/**
 * What a notification from the speaker means. Every case here was observed on
 * the real device, not inferred from the vendor app.
 */
sealed interface ReplyEvent {
    /** `0x04 [preset,0,0,0,0,0]` after a preset is applied. */
    data class EqPreset(val index: Int) : ReplyEvent

    /** `0x04 [0xfe,b1..b5]` after a custom curve is applied. */
    data class EqBands(val bands: List<Int>) : ReplyEvent

    /** `0x99 [on]` acknowledges the light being switched on or off. */
    data class Led(val on: Boolean) : ReplyEvent

    /**
     * `0x92 [mode]` carries the *animation*, not the on/off state: the speaker
     * still answers `0x92 [02]` while the light is off.
     */
    data class LedModeChanged(val mode: Int) : ReplyEvent

    /** `0x97 [set lo,set hi,rem lo,rem hi,seconds]`, sent every few seconds while armed. */
    data class Timer(val setMinutes: Int, val remainingSeconds: Int) : ReplyEvent

    /** `0x9c [on]` */
    data class Voice(val on: Boolean) : ReplyEvent

    /** `0x8c [on]` */
    data class Tws(val on: Boolean) : ReplyEvent

    data class Unknown(val cmd: Int, val payload: List<Int>) : ReplyEvent
}

object Replies {

    fun parse(reply: Reply): ReplyEvent = parse(reply.cmd, reply.payload.map { it.toInt() and 0xFF })

    fun parse(cmd: Int, payload: List<Int>): ReplyEvent = when (cmd) {
        Cmd.EQ_STATE -> when (payload.firstOrNull()) {
            null -> ReplyEvent.Unknown(cmd, payload)
            0xFE -> ReplyEvent.EqBands(payload.drop(1).take(5))
            else -> ReplyEvent.EqPreset(payload[0])
        }

        Cmd.LED_STATE -> ReplyEvent.LedModeChanged(payload.firstOrNull() ?: 0)

        Cmd.LED + 1 -> ReplyEvent.Led(on = payload.firstOrNull() == 1)

        Cmd.TIMER_STATE -> {
            val set = u16(payload, 0)
            val remaining = u16(payload, 2)
            val seconds = payload.getOrNull(4) ?: 0
            ReplyEvent.Timer(setMinutes = set, remainingSeconds = remaining * 60 + seconds)
        }

        Cmd.VOICE + 1 -> ReplyEvent.Voice(on = payload.firstOrNull() == 1)

        Cmd.TWS + 1 -> ReplyEvent.Tws(on = payload.firstOrNull() == 1)

        else -> ReplyEvent.Unknown(cmd, payload)
    }

    private fun u16(payload: List<Int>, offset: Int): Int =
        (payload.getOrNull(offset) ?: 0) or ((payload.getOrNull(offset + 1) ?: 0) shl 8)
}

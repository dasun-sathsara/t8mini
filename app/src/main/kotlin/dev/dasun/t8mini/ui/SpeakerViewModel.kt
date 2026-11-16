package dev.dasun.t8mini.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dasun.t8mini.ble.GattLink
import dev.dasun.t8mini.protocol.Cmd
import dev.dasun.t8mini.protocol.Cmds
import dev.dasun.t8mini.protocol.Curves
import dev.dasun.t8mini.protocol.EqPreset
import dev.dasun.t8mini.protocol.Frame
import dev.dasun.t8mini.protocol.Input
import dev.dasun.t8mini.protocol.LedMode
import dev.dasun.t8mini.protocol.ReplyEvent
import dev.dasun.t8mini.protocol.Replies
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LinkState { Idle, Scanning, Connecting, Ready, Failed }

data class SpeakerState(
    val link: LinkState = LinkState.Idle,
    val error: String? = null,
    val eqPreset: EqPreset = EqPreset.DEFAULT,
    val customEq: Boolean = false,
    val bands: List<Int> = listOf(128, 128, 128, 128, 128),
    val ledOn: Boolean = false,
    val ledMode: LedMode = LedMode.FASHION_PARTY,
    val voicePrompt: Boolean = false,
    val tws: Boolean = false,
    val input: Input = Input.BLUETOOTH,
    val timerMinutes: Int = 0,
    val timerRemainingSeconds: Int = 0,
)

class SpeakerViewModel(app: Application) : AndroidViewModel(app) {

    private val link = GattLink(app)

    private val _state = MutableStateFlow(SpeakerState())
    val state: StateFlow<SpeakerState> = _state.asStateFlow()

    private var connectJob: Job? = null
    private var pollJob: Job? = null
    private var lastReplyAt: Long = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            link.replies.collect { raw ->
                Frame.decode(raw)?.let { reply ->
                    lastReplyAt = System.currentTimeMillis()
                    onReply(Replies.parse(reply))
                }
            }
        }
        viewModelScope.launch {
            link.connected.collect { connected ->
                if (connected) return@collect
                _state.update { current ->
                    if (current.link == LinkState.Ready) {
                        current.copy(
                            link = LinkState.Failed,
                            error = "Link lost. Turn the speaker on and tap Connect.",
                        )
                    } else {
                        current
                    }
                }
            }
        }
        connect()
    }

    /** Polls the speaker while the screen is visible, which also keeps the UI honest. */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                if (_state.value.link == LinkState.Ready) {
                    try {
                        Cmd.statusQueries.forEach { command -> link.write(Cmds.status(command)) }
                    } catch (error: Exception) {
                        android.util.Log.d("T8LINK", "poll failed: ${error.message}")
                        _state.update {
                            it.copy(
                                link = LinkState.Failed,
                                error = "Link lost. Turn the speaker on and tap Connect.",
                            )
                        }
                    }
                    val silentFor = System.currentTimeMillis() - lastReplyAt
                    if (_state.value.link == LinkState.Ready && silentFor > REPLY_TIMEOUT_MS) {
                        _state.update {
                            it.copy(
                                link = LinkState.Failed,
                                error = "The speaker stopped answering. Tap Connect.",
                            )
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun connect() {
        if (connectJob?.isActive == true) return
        if (_state.value.link == LinkState.Ready) return
        connectJob = viewModelScope.launch {
            _state.update { it.copy(link = LinkState.Scanning, error = null) }
            try {
                _state.update { it.copy(link = LinkState.Connecting) }
                link.connectToSpeaker()
                lastReplyAt = System.currentTimeMillis()
                _state.update { it.copy(link = LinkState.Ready) }
                Cmd.statusQueries.forEach { command -> link.write(Cmds.status(command)) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(link = LinkState.Failed, error = error.message ?: "connection failed")
                }
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        link.disconnect()
        _state.update { it.copy(link = LinkState.Idle, timerRemainingSeconds = 0) }
    }

    fun setPreset(preset: EqPreset) {
        if (!ready()) return
        _state.update { it.copy(eqPreset = preset, customEq = false) }
        send(Cmds.eqPreset(preset))
    }

    /** App-provided speech curve, sent as a custom EQ like any hand drawn one. */
    fun setPodcast() {
        if (!ready()) return
        _state.update { it.copy(bands = Curves.podcast, customEq = true) }
        send(Cmds.eqCustom(Curves.podcast))
    }

    fun updateBands(bands: List<Int>) {
        if (!ready()) return
        _state.update { it.copy(bands = bands, customEq = true) }
    }

    fun resetEq() {
        if (!ready()) return
        val flat = List(5) { 128 }
        _state.update { it.copy(bands = flat, customEq = true) }
        send(Cmds.eqCustom(flat))
    }

    fun commitBands() {
        if (!ready()) return
        send(Cmds.eqCustom(_state.value.bands))
    }

    fun setLed(on: Boolean) {
        if (!ready()) return
        _state.update { it.copy(ledOn = on) }
        send(Cmds.led(on))
    }

    fun setLedMode(mode: LedMode) {
        if (!ready()) return
        _state.update { it.copy(ledMode = mode, ledOn = true) }
        send(Cmds.ledMode(mode))
    }

    fun setVoice(on: Boolean) {
        if (!ready()) return
        _state.update { it.copy(voicePrompt = on) }
        send(Cmds.voice(on))
    }

    fun setTws(on: Boolean) {
        if (!ready()) return
        _state.update { it.copy(tws = on) }
        send(Cmds.tws(on))
    }

    fun setInput(input: Input) {
        if (!ready()) return
        _state.update { it.copy(input = input) }
        send(Cmds.input(input))
    }

    fun setTimer(minutes: Int) {
        if (!ready()) return
        _state.update {
            it.copy(timerMinutes = minutes, timerRemainingSeconds = minutes * 60)
        }
        send(Cmds.timer(minutes))
    }

    private fun ready(): Boolean = _state.value.link == LinkState.Ready

    /** The speaker reports its own state, so the UI follows the hardware. */
    private fun onReply(event: ReplyEvent) {
        when (event) {
            is ReplyEvent.EqPreset -> {
                val preset = EqPreset.entries.firstOrNull { it.index == event.index }
                if (preset != null) {
                    _state.update { it.copy(eqPreset = preset, customEq = false) }
                }
            }

            is ReplyEvent.EqBands -> _state.update {
                it.copy(bands = event.bands, customEq = true)
            }

            is ReplyEvent.Led -> _state.update { it.copy(ledOn = event.on) }

            is ReplyEvent.LedModeChanged -> LedMode.entries
                .firstOrNull { it.index == event.mode }
                ?.let { mode -> _state.update { it.copy(ledMode = mode) } }

            is ReplyEvent.Timer -> _state.update {
                it.copy(
                    timerMinutes = event.setMinutes,
                    timerRemainingSeconds = event.remainingSeconds,
                )
            }

            is ReplyEvent.Voice -> _state.update { it.copy(voicePrompt = event.on) }
            is ReplyEvent.Tws -> _state.update { it.copy(tws = event.on) }
            is ReplyEvent.Unknown -> Unit
        }
    }

    private fun send(bytes: ByteArray) {
        viewModelScope.launch {
            try {
                link.write(bytes)
            } catch (error: Exception) {
                android.util.Log.d("T8LINK", "send failed: ${error.message}")
                _state.update {
                    it.copy(
                        link = LinkState.Failed,
                        error = "Command failed. Tap Connect and try again.",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        link.disconnect()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val REPLY_TIMEOUT_MS = 20_000L
    }
}

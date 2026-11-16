package dev.dasun.t8mini.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dasun.t8mini.protocol.Curves
import dev.dasun.t8mini.R
import dev.dasun.t8mini.protocol.EqPreset
import dev.dasun.t8mini.protocol.Input
import dev.dasun.t8mini.protocol.LedMode
import dev.dasun.t8mini.ui.components.EqCurve
import dev.dasun.t8mini.ui.components.RingHero

private val TimerChoices = listOf(0, 15, 30, 60)

@Composable
fun SpeakerScreen(vm: SpeakerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accent by animateColorAsState(accentFor(state.ledMode, state.ledOn), tween(550), label = "accent")
    val haptics = LocalHapticFeedback.current
    // Not saveable: a restored offset used to reopen the app mid page.
    val scroll = remember { ScrollState(0) }
    val ready = state.link == LinkState.Ready

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.startPolling()
                Lifecycle.Event.ON_STOP -> vm.stopPolling()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(520.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            if (state.ledOn) accent.copy(alpha = 0.13f) else Color.Transparent,
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp),
        ) {
            Header(state = state, accent = accent, onConnect = vm::connect)

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(horizontal = 40.dp, vertical = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (!ready) return@clickable
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.setLed(!state.ledOn)
                    },
                contentAlignment = Alignment.Center,
            ) {
                RingHero(
                    ledOn = state.ledOn,
                    mode = state.ledMode,
                    connected = ready,
                    modifier = Modifier.size(142.dp),
                )
            }

            Text(
                text = if (ready) "Tap the ring to switch the light" else "Not connected",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            Column(Modifier.alpha(if (ready) 1f else 0.5f)) {
                SoundCard(state = state, accent = accent, vm = vm)

                Spacer(Modifier.height(12.dp))

                TimerCard(state = state, accent = accent, vm = vm)

                Spacer(Modifier.height(12.dp))

                LightCard(state = state, accent = accent, vm = vm)

                Spacer(Modifier.height(12.dp))

                SpeakerCard(state = state, accent = accent, vm = vm)
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SoundCard(state: SpeakerState, accent: Color, vm: SpeakerViewModel) {
    val podcastActive = state.customEq && Curves.matchesPodcast(state.bands)

    SectionCard(
        title = "Sound",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.customEq && !podcastActive) {
                    Text("CUSTOM", style = MaterialTheme.typography.labelSmall, color = accent)
                } else if (!state.customEq && state.eqPreset !in listOf(EqPreset.DEFAULT, EqPreset.SOUND_PULSE)) {
                    Text(state.eqPreset.label, style = MaterialTheme.typography.labelSmall, color = accent)
                }
                IconButton(onClick = vm::resetEq, enabled = state.link == LinkState.Ready) {
                    Icon(
                        painter = painterResource(R.drawable.ic_reset),
                        contentDescription = "Reset EQ",
                        tint = if (state.link == LinkState.Ready) accent else TextDim,
                    )
                }
            }
        },
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(
                text = "Default",
                selected = !state.customEq && state.eqPreset == EqPreset.DEFAULT,
                accent = accent,
                onClick = { vm.setPreset(EqPreset.DEFAULT) },
            )
            Pill(
                text = "Podcast",
                selected = podcastActive,
                accent = accent,
                onClick = vm::setPodcast,
            )
            Pill(
                text = "SoundPulse",
                selected = !state.customEq && state.eqPreset == EqPreset.SOUND_PULSE,
                accent = accent,
                onClick = { vm.setPreset(EqPreset.SOUND_PULSE) },
            )
        }

        Spacer(Modifier.height(14.dp))

        EqCurve(
            bands = state.bands,
            accent = accent,
            onChange = vm::updateBands,
            onCommit = vm::commitBands,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .height(120.dp),
        )

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("60", "225", "900", "3.6k", "14k").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = Geist,
                    color = TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(48.dp),
                )
            }
        }
    }
}

@Composable
private fun TimerCard(state: SpeakerState, accent: Color, vm: SpeakerViewModel) {
    val armed = state.timerRemainingSeconds > 0

    SectionCard(title = "Auto power-off") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (armed) clock(state.timerRemainingSeconds) else "Off",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp),
                    fontFamily = Geist,
                    color = if (armed) accent else TextDim,
                )
                Text(
                    text = if (armed) {
                        "remaining of ${state.timerMinutes} min"
                    } else {
                        "no auto power-off"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                )
            }

            StepButton("−", enabled = state.timerMinutes > 0) {
                vm.setTimer((state.timerMinutes - 5).coerceIn(0, 240))
            }
            Text(
                text = "${state.timerMinutes} min",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = Geist,
                modifier = Modifier.width(84.dp),
                textAlign = TextAlign.Center,
            )
            StepButton("+", enabled = state.timerMinutes < 240) {
                vm.setTimer((state.timerMinutes + 5).coerceIn(0, 240))
            }
        }

        Spacer(Modifier.height(14.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimerChoices.forEach { minutes ->
                Pill(
                    text = if (minutes == 0) "Off" else "$minutes min",
                    selected = state.timerMinutes == minutes,
                    accent = accent,
                    onClick = { vm.setTimer(minutes) },
                )
            }
        }
    }
}

@Composable
private fun LightCard(state: SpeakerState, accent: Color, vm: SpeakerViewModel) {
    SectionCard(
        title = "Light",
        trailing = {
            Switch(
                checked = state.ledOn,
                onCheckedChange = vm::setLed,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedTrackColor = Surface2,
                    uncheckedBorderColor = Line,
                ),
            )
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LedMode.entries.forEach { mode ->
                ModeTile(
                    mode = mode,
                    selected = state.ledOn && state.ledMode == mode,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(if (state.ledOn) 1f else 0.55f),
                    onClick = { vm.setLedMode(mode) },
                )
            }
        }
    }
}

@Composable
private fun SpeakerCard(state: SpeakerState, accent: Color, vm: SpeakerViewModel) {
    SectionCard(title = "Speaker") {
        ToggleRow(
            label = "Voice prompt",
            detail = "Beeps and spoken confirmations",
            checked = state.voicePrompt,
            accent = accent,
            onChange = vm::setVoice,
        )
        Divider()
        ToggleRow(
            label = "Stereo pair",
            detail = "Twin with a second T8 Mini",
            checked = state.tws,
            accent = accent,
            onChange = vm::setTws,
        )
        Divider()
        Column {
            Column {
                Text("Input", style = MaterialTheme.typography.bodyMedium, color = TextMain)
                Text(
                    "AUX and TF card are untested",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                )
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Input.entries.forEach { input ->
                    Pill(
                        text = input.label,
                        selected = state.input == input,
                        accent = accent,
                        onClick = { vm.setInput(input) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: SpeakerState, accent: Color, onConnect: () -> Unit) {
    val status = when (state.link) {
        LinkState.Ready -> "Connected"
        LinkState.Scanning -> "Looking for the speaker"
        LinkState.Connecting -> "Connecting"
        LinkState.Failed -> state.error ?: "Offline"
        LinkState.Idle -> "Offline"
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("T8 Mini", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim,
                    maxLines = 2,
                )
            }
            when (state.link) {
                LinkState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(7.dp))
                    Text("Live", style = MaterialTheme.typography.bodySmall, color = accent)
                }

                LinkState.Scanning, LinkState.Connecting -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = TextDim,
                )

                LinkState.Failed, LinkState.Idle -> Pill(
                    text = "Connect",
                    selected = true,
                    accent = accent,
                    onClick = onConnect,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Surface1)
            .border(1.dp, Line, RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextDim,
            )
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun Pill(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(), label = "pill")

    Box(
        Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.16f) else Surface2)
            .border(1.dp, if (selected) accent.copy(alpha = 0.75f) else Line, CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Color.White else TextDim,
            maxLines = 1,
        )
    }
}

@Composable
private fun ModeTile(
    mode: LedMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = accentFor(mode)
    val alt = accentAltFor(mode)
    val haptics = LocalHapticFeedback.current

    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Surface2)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent.copy(alpha = 0.85f) else Line,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(accent, alt))),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = mode.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Color.White else TextDim,
            maxLines = 2,
        )
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean = true, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Surface2)
            .border(1.dp, Line, CircleShape)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleMedium, color = TextMain)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    detail: String,
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMain)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TextDim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedTrackColor = Surface2,
                uncheckedBorderColor = Line,
            ),
        )
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = 0.7f)))
}

/** mm:ss for a remaining duration. */
private fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

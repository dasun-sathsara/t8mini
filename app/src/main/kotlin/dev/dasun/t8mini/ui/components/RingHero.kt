package dev.dasun.t8mini.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dasun.t8mini.protocol.LedMode
import dev.dasun.t8mini.ui.Feixen
import dev.dasun.t8mini.ui.TextDim
import dev.dasun.t8mini.ui.accentAltFor
import dev.dasun.t8mini.ui.accentFor

/**
 * The signature control: a ring that mirrors the physical LED. Tapping it turns
 * the light on or off.
 */
@Composable
fun RingHero(
    ledOn: Boolean,
    mode: LedMode,
    connected: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val accent = accentFor(mode)
    val alt = accentAltFor(mode)

    val lit = ledOn && connected

    val transition = rememberInfiniteTransition(label = "ring")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val radius = size.minDimension / 2f - 14.dp.toPx()
            val stroke = 11.dp.toPx()

            if (lit) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                        center = c,
                        radius = radius * 1.45f,
                    ),
                    radius = radius * 1.45f,
                    center = c,
                )
            }

            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = radius,
                center = c,
                style = Stroke(width = 2.dp.toPx()),
            )

            if (!lit) return@Canvas

            when (mode) {
                LedMode.FASHION_PARTY -> {
                    rotate(spin) {
                        listOf(accent, alt, Color(0xFF22D3EE)).forEachIndexed { index, color ->
                            drawArc(
                                color = color,
                                startAngle = index * 120f,
                                sweepAngle = 82f,
                                useCenter = false,
                                topLeft = Offset(c.x - radius, c.y - radius),
                                size = Size(radius * 2f, radius * 2f),
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                }

                LedMode.DEEP_BREATH -> {
                    drawCircle(
                        color = accent.copy(alpha = pulse),
                        radius = radius,
                        center = c,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawCircle(
                        color = accent.copy(alpha = pulse * 0.25f),
                        radius = radius * (1f + (1f - pulse) * 0.12f),
                        center = c,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }

                LedMode.CAROUSEL -> {
                    rotate(spin) {
                        repeat(6) { index ->
                            drawArc(
                                color = if (index % 2 == 0) accent else alt,
                                startAngle = index * 60f,
                                sweepAngle = 34f,
                                useCenter = false,
                                topLeft = Offset(c.x - radius, c.y - radius),
                                size = Size(radius * 2f, radius * 2f),
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                }
            }
        }

        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize(0.62f)) {
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF1E1E27), Color(0xFF101016)),
                    ),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            Text(
                text = when {
                    !connected -> "Not connected"
                    ledOn -> mode.label
                    else -> "Light off"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = Feixen,
                fontWeight = FontWeight.Bold,
                color = if (ledOn) Color.White else TextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

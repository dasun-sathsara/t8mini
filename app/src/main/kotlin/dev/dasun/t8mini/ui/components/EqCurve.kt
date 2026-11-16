package dev.dasun.t8mini.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Five band curve. Drag a node, release to send. The speaker only accepts whole
 * curves, so the write happens on gesture end.
 */
@Composable
fun EqCurve(
    bands: List<Int>,
    accent: Color,
    onChange: (List<Int>) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(bands)
    var dragging by remember { mutableIntStateOf(-1) }

    fun valueAt(y: Float, height: Float, inset: Float): Int =
        (255f * (1f - ((y - inset) / (height - 2 * inset)).coerceIn(0f, 1f))).roundToInt().coerceIn(0, 255)

    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val horizontalInset = 24.dp.toPx()
                val verticalInset = 10.dp.toPx()
                val nodes = current.mapIndexed { index, value ->
                    Offset(
                        x = horizontalInset + (size.width - 2 * horizontalInset) * index / 4f,
                        y = verticalInset + (1f - value / 255f) * (size.height - 2 * verticalInset),
                    )
                }
                // Only steal the gesture when it starts on a node; anywhere else
                // must scroll the page.
                val hit = nodes.indexOfFirst {
                    (it - down.position).getDistance() <= 30.dp.toPx()
                }
                if (hit < 0) return@awaitEachGesture

                down.consume()
                dragging = hit
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                var latest = current
                var moved = false

                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.pressed && change.position != change.previousPosition) {
                        moved = true
                        val next = latest.toMutableList()
                        next[hit] = valueAt(change.position.y, size.height.toFloat(), verticalInset)
                        latest = next
                        onChange(next)
                    }
                    change.consume()
                } while (change.pressed)

                dragging = -1
                if (moved) {
                    onCommit()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        },
    ) {
        val width = size.width
        val height = size.height
        val horizontalInset = 24.dp.toPx()
        val verticalInset = 10.dp.toPx()
        val points = current.mapIndexed { index, value ->
            Offset(
                x = horizontalInset + (width - 2 * horizontalInset) * index / 4f,
                y = verticalInset + (1f - value / 255f) * (height - 2 * verticalInset),
            )
        }

        // grid
        for (index in 0..4) {
            val x = horizontalInset + (width - 2 * horizontalInset) * index / 4f
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(x, verticalInset),
                end = Offset(x, height - verticalInset),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawLine(
            color = Color.White.copy(alpha = 0.07f),
            start = Offset(horizontalInset, height / 2f),
            end = Offset(width - horizontalInset, height / 2f),
            strokeWidth = 1.dp.toPx(),
        )

        val curve = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (index in 0 until points.lastIndex) {
                val from = points[index]
                val to = points[index + 1]
                val midX = (from.x + to.x) / 2f
                cubicTo(midX, from.y, midX, to.y, to.x, to.y)
            }
        }

        val fill = Path().apply {
            addPath(curve)
            lineTo(width - horizontalInset, height - verticalInset)
            lineTo(horizontalInset, height - verticalInset)
            close()
        }

        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.02f)),
            ),
        )
        drawPath(
            path = curve,
            color = accent,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )

        points.forEachIndexed { index, point ->
            val active = index == dragging
            drawCircle(color = Color(0xFF101016), radius = if (active) 11.dp.toPx() else 9.dp.toPx(), center = point)
            drawCircle(
                color = accent,
                radius = if (active) 11.dp.toPx() else 9.dp.toPx(),
                center = point,
                style = Stroke(width = if (active) 4.dp.toPx() else 3.dp.toPx()),
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOrange
import kotlin.math.max

@Composable
fun StaticWaveformCanvas(
    waveformPoints: List<Int>,
    progress: Float = 0f, // 0.0f .. 1.0f
    playedColor: Color = StudioOrange,
    unplayedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    onSeek: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
) {
    val points = if (waveformPoints.isNotEmpty()) waveformPoints else List(50) { (15..70).random() }

    Canvas(
        modifier = modifier.then(
            if (onSeek != null) {
                Modifier.pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(ratio)
                    }
                }
            } else Modifier
        )
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val count = points.size
        val barSpacing = width / count
        val barWidth = max(2f, barSpacing * 0.65f)

        for (i in 0 until count) {
            val pointProgress = i.toFloat() / count
            val isPlayed = pointProgress <= progress
            val color = if (isPlayed) playedColor else unplayedColor

            val amp = points[i].coerceIn(8, 100)
            val barH = max(6f, (amp / 100f) * (height * 0.85f))
            val halfH = barH / 2f
            val x = i * barSpacing + (barSpacing / 2f)

            drawLine(
                color = color,
                start = Offset(x, centerY - halfH),
                end = Offset(x, centerY + halfH),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun VuMeterBar(
    dbLevel: Float, // 0..100
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val numSegments = 24
        val gap = 2.dp.toPx()
        val segWidth = (width - (numSegments - 1) * gap) / numSegments

        val activeCount = ((dbLevel / 100f) * numSegments).toInt().coerceIn(0, numSegments)

        for (i in 0 until numSegments) {
            val segRatio = i.toFloat() / numSegments
            val color = when {
                segRatio > 0.85f -> Color(0xFFFF1744) // Red overload
                segRatio > 0.65f -> Color(0xFFFFC107) // Amber caution
                else -> Color(0xFF00E676)             // Green optimal
            }

            val alpha = if (i < activeCount) 1.0f else 0.15f
            val x = i * (segWidth + gap)

            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(segWidth, height)
            )
        }
    }
}

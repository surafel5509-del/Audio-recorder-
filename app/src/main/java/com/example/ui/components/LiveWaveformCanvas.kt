package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StudioCoral
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioOrange
import kotlin.math.max

@Composable
fun LiveWaveformCanvas(
    waveformBars: List<Float>, // Recent amplitudes 0..100
    durationMs: Long,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val centerLineColor = StudioOrange
    val futureDotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val rulerLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val centerX = width / 2f

        // Draw background horizontal time ticks
        val barSpacing = 7.dp.toPx()
        val barWidth = 3.5.dp.toPx()

        // 1. Draw future timeline dotted line (right of center)
        var dotX = centerX + 12f
        while (dotX < width) {
            drawLine(
                color = futureDotColor,
                start = Offset(dotX, centerY - 6f),
                end = Offset(dotX, centerY + 6f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            dotX += 8.dp.toPx()
        }

        // 2. Draw recorded waveform bars (left of center)
        val barCount = waveformBars.size
        for (i in 0 until barCount) {
            val barAmp = waveformBars[barCount - 1 - i]
            val x = centerX - (i * barSpacing) - 6f
            if (x < 0) break

            val maxBarHeight = height * 0.75f
            val calculatedHeight = max(8f, (barAmp / 100f) * maxBarHeight)
            val halfH = calculatedHeight / 2f

            drawLine(
                color = if (i < 3) StudioCoral else barColor,
                start = Offset(x, centerY - halfH),
                end = Offset(x, centerY + halfH),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }

        // 3. Draw central vertical red cursor/recording head
        drawLine(
            color = centerLineColor,
            start = Offset(centerX, 0f),
            end = Offset(centerX, height),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Top and bottom ruler tick accents on center
        drawCircle(
            color = centerLineColor,
            radius = 3.dp.toPx(),
            center = Offset(centerX, 4f)
        )
    }
}

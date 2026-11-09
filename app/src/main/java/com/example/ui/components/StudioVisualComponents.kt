package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PitchInfo
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun StereoDualVuMeter(
    leftDb: Float,  // 0..100
    rightDb: Float, // 0..100
    isClipping: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "STEREO PEAK METER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
                if (isClipping) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF1744)
                    ) {
                        Text(
                            text = "CLIP",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                } else {
                    Text(
                        text = "-0.0 dB FS",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Channel L
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("L", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp))
                VuChannelBar(level = leftDb, modifier = Modifier.weight(1f).height(6.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Channel R
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("R", fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(14.dp))
                VuChannelBar(level = rightDb, modifier = Modifier.weight(1f).height(6.dp))
            }
        }
    }
}

@Composable
private fun VuChannelBar(level: Float, modifier: Modifier = Modifier) {
    val animatedLevel by animateFloatAsState(targetValue = level, label = "vuLevel")
    Canvas(modifier = modifier.clip(RoundedCornerShape(3.dp))) {
        val w = size.width
        val h = size.height
        val activeWidth = (animatedLevel / 100f).coerceIn(0f, 1f) * w

        // Background track
        drawRect(color = Color(0xFF1E293B), size = Size(w, h))

        // Level Fill Gradient: Green -> Yellow -> Red
        val gradient = Brush.horizontalGradient(
            0.0f to Color(0xFF00E676),
            0.7f to Color(0xFFFFC107),
            0.9f to Color(0xFFFF1744),
            startX = 0f,
            endX = w
        )
        drawRect(brush = gradient, size = Size(activeWidth, h))
    }
}

@Composable
fun PitchTunerDisplay(
    pitchInfo: PitchInfo?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (pitchInfo?.isInTune == true) StudioGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (pitchInfo?.isInTune == true) StudioGreen.copy(alpha = 0.2f) else StudioCyan.copy(alpha = 0.15f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pitchInfo?.noteName ?: "--",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = if (pitchInfo?.isInTune == true) StudioGreen else StudioCyan
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Vocal Pitch Tuner",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (pitchInfo != null) "${pitchInfo.frequencyHz.toInt()} Hz (${if (pitchInfo.centsOffset >= 0) "+" else ""}${"%.1f".format(pitchInfo.centsOffset)} cents)" else "Sing into mic to detect key",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (pitchInfo?.isInTune == true) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = StudioGreen.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioGreen)
                ) {
                    Text(
                        text = "IN TUNE",
                        color = StudioGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EqCurveGraph(
    b1: Float, b2: Float, b3: Float, b4: Float, b5: Float,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(90.dp)
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        // Draw background grid lines
        drawLine(
            color = Color.White.copy(alpha = 0.1f),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 1.dp.toPx()
        )

        // Draw smooth EQ curve
        val gains = floatArrayOf(b1, b2, b3, b4, b5)
        val xPoints = floatArrayOf(w * 0.1f, w * 0.3f, w * 0.5f, w * 0.7f, w * 0.9f)
        val path = Path()

        path.moveTo(0f, centerY)
        for (i in gains.indices) {
            val targetY = centerY - (gains[i] / 12f) * (centerY * 0.85f)
            val prevX = if (i == 0) 0f else xPoints[i - 1]
            val prevY = if (i == 0) centerY else centerY - (gains[i - 1] / 12f) * (centerY * 0.85f)
            val cx = (prevX + xPoints[i]) / 2f
            path.cubicTo(cx, prevY, cx, targetY, xPoints[i], targetY)
        }
        path.lineTo(w, centerY)

        drawPath(
            path = path,
            color = StudioCyan,
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw EQ band dots
        for (i in gains.indices) {
            val targetY = centerY - (gains[i] / 12f) * (centerY * 0.85f)
            drawCircle(
                color = StudioOrange,
                radius = 4.dp.toPx(),
                center = Offset(xPoints[i], targetY)
            )
        }
    }
}

package com.example.carheadunit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.carheadunit.ui.theme.PrimaryContainer
import com.example.carheadunit.ui.theme.SurfaceLowest
import com.example.carheadunit.ui.theme.White20

/**
 * Row 1 right panel: steering-angle track with center glow line, technical
 * tick marks, an active fill segment from center, and a glowing thumb.
 */
@Composable
fun SteeringTrack(modifier: Modifier = Modifier, activeFraction: Float = 0.65f) {
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 16.dp.toPx()
            val trackH = 22.dp.toPx()
            val top = (size.height - trackH) / 2f
            val left = inset
            val trackW = size.width - inset * 2f
            val cx = left + trackW / 2f

            // Track background with fake inner shadow
            drawRoundRect(
                color = SurfaceLowest.copy(alpha = 0.6f),
                topLeft = Offset(left, top),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.35f),
                start = Offset(left, top + 1.dp.toPx()),
                end = Offset(left + trackW, top + 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(left, top + trackH - 1.dp.toPx()),
                end = Offset(left + trackW, top + trackH - 1.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )

            // Center glow line with halos
            val lineTop = top + trackH * 0.10f
            val lineBottom = top + trackH * 0.90f
            drawLine(
                color = PrimaryContainer.copy(alpha = 0.12f),
                start = Offset(cx, lineTop),
                end = Offset(cx, lineBottom),
                strokeWidth = 7.dp.toPx(),
            )
            drawLine(
                color = PrimaryContainer.copy(alpha = 0.25f),
                start = Offset(cx, lineTop),
                end = Offset(cx, lineBottom),
                strokeWidth = 3.5.dp.toPx(),
            )
            drawLine(
                color = PrimaryContainer,
                start = Offset(cx, lineTop),
                end = Offset(cx, lineBottom),
                strokeWidth = 1.5.dp.toPx(),
            )

            // 11 tick marks, alternating heights
            for (i in 0..10) {
                val x = left + trackW * i / 10f
                val th = if (i % 5 == 0) 8.dp.toPx() else 5.dp.toPx()
                drawLine(
                    color = White20,
                    start = Offset(x, size.height / 2 - th / 2),
                    end = Offset(x, size.height / 2 + th / 2),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // Active fill: 30% of the track starting at center
            val fillW = trackW * 0.30f
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(PrimaryContainer.copy(alpha = 0.4f), PrimaryContainer),
                    startX = cx,
                    endX = cx + fillW,
                ),
                topLeft = Offset(cx, top + 2.dp.toPx()),
                size = Size(fillW, trackH - 4.dp.toPx()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            // Four mini-bars inside the fill
            for (i in 0..3) {
                val bx = cx + fillW * (i + 0.5f) / 4f
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(bx, size.height / 2 - 6.dp.toPx()),
                    end = Offset(bx, size.height / 2 + 6.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                )
            }

            // Thumb at activeFraction of the track, glowing
            val tx = left + trackW * activeFraction
            drawLine(
                color = Color.White.copy(alpha = 0.30f),
                start = Offset(tx, size.height / 2 - 11.dp.toPx()),
                end = Offset(tx, size.height / 2 + 11.dp.toPx()),
                strokeWidth = 8.dp.toPx(),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(tx, size.height / 2 - 10.dp.toPx()),
                end = Offset(tx, size.height / 2 + 10.dp.toPx()),
                strokeWidth = 4.dp.toPx(),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(tx - 1.5.dp.toPx(), size.height / 2 - 10.dp.toPx()),
                size = Size(3.dp.toPx(), 20.dp.toPx()),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
    }
}

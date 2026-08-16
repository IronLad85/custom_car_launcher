package com.example.carheadunit.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.ui.theme.GlassBorder
import kotlin.math.roundToInt

// Car asset: "2D Orange Sports Car" top-down PNG (free for personal use),
// source: https://www.pngall.com/2d-car-png/
// (Futuristic-2D-Car-Model-Render-PNG)

/**
 * Full-bleed static road scene in the style of popular car-launcher widgets
 * (AGAMA / Car Launcher Pro): the road vanishes near the top of the card,
 * with perspective lane dashes, glowing reflector posts along the edges,
 * headlight beams ahead of the car, and a speed badge overlaid by the card.
 */
@Composable
fun RoadBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val carBitmap = remember {
        BitmapFactory.decodeResource(context.resources, R.drawable.car_top).asImageBitmap()
    }

    Canvas(modifier = modifier.clip(RoundedCornerShape(16.dp))) {
        val w = size.width
        val h = size.height
        drawRoadScene(w, h, carBitmap)
        // Glass border: the GlassCard's own border sits under this opaque scene,
        // so the border is redrawn here to match the other cards.
        drawRoundRect(
            color = GlassBorder,
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

private fun DrawScope.drawRoadScene(w: Float, h: Float, car: ImageBitmap) {
    val topY = h * 0.04f
    val roadLen = h - topY

    /** Road half-width at perspective position s (0 = vanishing top, 1 = viewer). */
    fun halfAt(s: Float) = w * (0.20f + 0.27f * s)

    // Distant glow where the road vanishes
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x40B39DFF), Color(0x00B39DFF)),
            center = Offset(w / 2, topY),
            radius = w * 0.30f,
        ),
        radius = w * 0.30f,
        center = Offset(w / 2, topY),
    )
    // Ground
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0C1024), Color(0xFF080B1A)),
            endY = h,
        ),
    )
    // Road: perspective trapezoid reaching the top of the card
    val road = Path().apply {
        moveTo(w / 2 - halfAt(0f), topY)
        lineTo(w / 2 + halfAt(0f), topY)
        lineTo(w / 2 + halfAt(1f), h)
        lineTo(w / 2 - halfAt(1f), h)
        close()
    }
    drawPath(
        path = road,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF3A405E), Color(0xFF262B45), Color(0xFF1A1F36)),
            start = Offset(0f, topY),
            end = Offset(0f, h),
        ),
    )
    // Edge lines: soft outer glow + bright core
    val leftEdge = Path().apply {
        moveTo(w / 2 - halfAt(0f), topY)
        lineTo(w / 2 - halfAt(1f), h)
    }
    val rightEdge = Path().apply {
        moveTo(w / 2 + halfAt(0f), topY)
        lineTo(w / 2 + halfAt(1f), h)
    }
    drawPath(leftEdge, color = Color(0x26FFFFFF), style = Stroke(width = 3.dp.toPx()))
    drawPath(rightEdge, color = Color(0x26FFFFFF), style = Stroke(width = 3.dp.toPx()))
    drawPath(leftEdge, color = Color(0x73FFFFFF), style = Stroke(width = 1.2.dp.toPx()))
    drawPath(rightEdge, color = Color(0x73FFFFFF), style = Stroke(width = 1.2.dp.toPx()))
    // Lane dashes, perspective-scaled
    val dashes = 20
    for (i in 0 until dashes) {
        val s = (i + 0.5f) / dashes
        if (s > 0.90f) continue
        val y = topY + s * roadLen
        val half = halfAt(s)
        val dashH = roadLen * (0.008f + 0.026f * s)
        drawRect(
            color = Color(0x80FFFFFF),
            topLeft = Offset(w / 2 - half * 0.045f, y),
            size = Size(half * 0.09f, dashH),
        )
    }
    // Reflector posts along both edges
    for (i in 1..9) {
        val s = 0.10f + i * 0.085f
        val half = halfAt(s)
        val y = topY + s * roadLen
        val postOff = w * (0.012f + 0.012f * s)
        val glowR = w * (0.006f + 0.010f * s)
        for (side in listOf(-1, 1)) {
            val x = w / 2 + side * (half + postOff)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x73FFFFFF), Color(0x00FFFFFF)),
                    center = Offset(x, y),
                    radius = glowR,
                ),
                radius = glowR,
                center = Offset(x, y),
            )
            drawCircle(
                color = Color(0xE6FFFFFF),
                radius = glowR * 0.35f,
                center = Offset(x, y),
            )
        }
    }
    // Car: front-view silhouette grounded on the bottom edge (hero style)
    val carW = w * 0.44f
    val carL = carW * (car.height.toFloat() / car.width.toFloat())
    val carTop = h - carL * 0.94f
    // Ground shadow under the car
    drawOval(
        color = Color(0x66000000),
        topLeft = Offset(w / 2 - carW * 0.52f, h - carL * 0.10f),
        size = Size(carW * 1.04f, carL * 0.16f),
    )
    // Soft light spill around the car
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x33B39DFF), Color(0x00B39DFF)),
            center = Offset(w / 2, h - carL * 0.55f),
            radius = carW * 0.9f,
        ),
        radius = carW * 0.9f,
        center = Offset(w / 2, h - carL * 0.55f),
    )
    drawImage(
        image = car,
        dstOffset = IntOffset(
            (w / 2 - carW / 2).roundToInt(),
            carTop.roundToInt(),
        ),
        dstSize = IntSize(carW.roundToInt(), carL.roundToInt()),
    )
}

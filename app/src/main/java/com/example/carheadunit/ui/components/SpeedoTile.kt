package com.example.carheadunit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carheadunit.R
import com.example.carheadunit.data.SpeedInfo
import com.example.carheadunit.ui.theme.ErrorRed
import com.example.carheadunit.ui.theme.OnSurface
import com.example.carheadunit.ui.theme.OnSurfaceVariant
import com.example.carheadunit.ui.theme.OutlineVariant
import com.example.carheadunit.ui.theme.PrimaryContainer
import com.example.carheadunit.ui.theme.PrimaryFixed
import com.example.carheadunit.ui.theme.PrimaryFixedDim
import com.example.carheadunit.ui.theme.SurfaceHighest
import kotlin.math.roundToInt

/** Main speedometer tile: dot-grid decor, big km/h readout, speed/RPM bars, gear, car render. */
@Composable
fun SpeedoTile(speed: SpeedInfo, modifier: Modifier = Modifier) {
    val kmh = speed.kmh
    val speedFrac = (kmh / 200f).coerceIn(0f, 1f)
    val rpm = 0.9f + kmh * 0.014f

    GlassPanel(modifier = modifier, contentPadding = 0.dp) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
            // Dot-grid decor pattern
            Canvas(Modifier.fillMaxSize()) {
                val spacing = 24.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) {
                        drawCircle(Color.White, radius = 1.dp.toPx(), center = Offset(x, y), alpha = 0.10f)
                        x += spacing
                    }
                    y += spacing
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Left 66%: metrics column with wide horizontal bars
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(0.66f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    val compact = maxHeight < 150.dp
                    // Readout scales with the tile: design 64px on tall screens
                    val readoutSize = when {
                        compact -> 46
                        maxHeight < 280.dp -> 72
                        else -> 96
                    }
                    val readoutStyle = androidx.compose.material3.MaterialTheme.typography.displayLarge
                        .copy(
                            fontSize = readoutSize.sp,
                            lineHeight = (readoutSize + 4).sp,
                            letterSpacing = (-2).sp,
                        )
                    Column(verticalArrangement = Arrangement.Center) {
                        // Speed readout with cyan glow
                        Box {
                            // Glow via a translucent duplicate — no GPU blur on weak SoCs
                            Text(
                                text = "$kmh",
                                style = readoutStyle,
                                color = PrimaryContainer.copy(alpha = 0.35f),
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "$kmh",
                                    style = readoutStyle,
                                    color = OnSurface,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "km/h",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                    color = PrimaryFixed.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(bottom = 10.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(if (compact) 3.dp else 4.dp))
                        // Speed bar: 0-200 km/h heat ramp. The filled portion shows
                        // its part of the full gradient; the unfilled portion is
                        // darkened so the fill reads clearly against it.
                        val speedStops = (0..8).map { speedHeatColor(it / 8f) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (compact) 16.dp else 26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceHighest)
                                .border(1.dp, OutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(speedFrac.coerceAtLeast(0.0001f))
                                        .fillMaxHeight(),
                                ) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        // Gradient spans the FULL bar width, so the
                                        // fill shows only its own portion of the ramp
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = speedStops,
                                                startX = 0f,
                                                endX = if (speedFrac > 0.01f) size.width / speedFrac else size.width,
                                            ),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(3.dp)
                                            .fillMaxHeight()
                                            .background(Color.White.copy(alpha = 0.7f)),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight((1f - speedFrac).coerceAtLeast(0.0001f))
                                        .fillMaxHeight()
                                        .background(Color.Black.copy(alpha = 0.30f)),
                                )
                            }
                        }
                        Spacer(Modifier.height(if (compact) 3.dp else 8.dp))
                        // RPM label + value (hidden in compact mode)
                        if (!compact) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    text = "RPM x 1000",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant.copy(alpha = 0.8f),
                                )
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f", rpm),
                                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                                    color = OnSurface,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        // RPM: stepped heat bar. The ramp is tuned to real driving:
                        // 0-2.5k cyan (daily zone), 2.5-3k cyan→yellow, 3k+ yellow→red,
                        // full red at ~8k (redline).
                        val rpmFrac = (rpm / 8f).coerceIn(0f, 1f)
                        val rpmSegments = 40
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (compact) 16.dp else 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            repeat(rpmSegments) { i ->
                                // Each segment blends into its neighbor's color, so the
                                // ramp reads as one continuous gradient through the steps.
                                val tFrom = i / (rpmSegments - 1f)
                                val tTo = (i + 1) / (rpmSegments - 1f)
                                val cFrom = rpmHeatColor(tFrom)
                                val cTo = rpmHeatColor(tTo)
                                // Fractional leading edge: the boundary segment fades
                                // from ghost (0.08) to full brightness instead of snapping.
                                val fill = rpmFrac * rpmSegments - i
                                val alpha = 0.08f + 0.92f * fill.coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(cFrom.copy(alpha = alpha), cTo.copy(alpha = alpha)),
                                            )
                                        ),
                                )
                            }
                        }
                        // Gear mark removed to keep the speed card cleaner and shorter.
                    }
                }
                // Right 33%: car image
                Box(
                    modifier = Modifier
                        .weight(0.33f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.vento),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(0.95f)
                            .padding(top = 8.dp, bottom = 6.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

/**
 * Speed heat ramp over 0-200 km/h: cyan at low speed, green through the
 * cruising band, amber as it climbs, and full red at 200.
 */
private fun speedHeatColor(t: Float): Color = when {
    t < 0.4f -> androidx.compose.ui.graphics.lerp(PrimaryContainer, Color(0xFF79FF5B), t / 0.4f)
    t < 0.65f -> androidx.compose.ui.graphics.lerp(Color(0xFF79FF5B), Color(0xFFFFC84A), (t - 0.4f) / 0.25f)
    t < 0.85f -> androidx.compose.ui.graphics.lerp(Color(0xFFFFC84A), Color(0xFFFF6B35), (t - 0.65f) / 0.2f)
    else -> androidx.compose.ui.graphics.lerp(Color(0xFFFF6B35), ErrorRed, (t - 0.85f) / 0.15f)
}

/**
 * RPM heat ramp over the 0-8k range, tuned to daily driving:
 * 0-2.5k stays cyan (normal zone), 2.5-3k blends to yellow, 3-5.5k goes
 * yellow→orange, and 5.5-8k climbs orange→full red (most red at redline).
 */
private fun rpmHeatColor(t: Float): Color = when {
    t < 0.3125f -> PrimaryContainer // 0-2.5k
    t < 0.375f -> androidx.compose.ui.graphics.lerp(
        PrimaryContainer, Color(0xFFFFC84A), (t - 0.3125f) / 0.0625f,
    ) // 2.5-3k
    t < 0.6875f -> androidx.compose.ui.graphics.lerp(
        Color(0xFFFFC84A), Color(0xFFFF6B35), (t - 0.375f) / 0.3125f,
    ) // 3-5.5k
    else -> androidx.compose.ui.graphics.lerp(
        Color(0xFFFF6B35), ErrorRed, (t - 0.6875f) / 0.3125f,
    ) // 5.5-8k
}

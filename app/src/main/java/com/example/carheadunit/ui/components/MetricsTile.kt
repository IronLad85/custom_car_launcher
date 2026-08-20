package com.example.carheadunit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.carheadunit.R
import com.example.carheadunit.data.CarSnapshot
import com.example.carheadunit.ui.theme.ErrorRed
import com.example.carheadunit.ui.theme.OnSurface
import com.example.carheadunit.ui.theme.OnSurfaceVariant
import com.example.carheadunit.ui.theme.OutlineVariant
import com.example.carheadunit.ui.theme.Primary
import com.example.carheadunit.ui.theme.PrimaryContainer
import com.example.carheadunit.ui.theme.PrimaryFixed
import com.example.carheadunit.ui.theme.PrimaryFixedDim
import com.example.carheadunit.ui.theme.SecondaryFixed

/** Vehicle metrics strip: fuel, odometer, throttle, temperature. */
@Composable
fun MetricsTile(snapshot: CarSnapshot, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier.fillMaxWidth(), contentPadding = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FuelSection(snapshot.fuelLevel)
            Divider()
            OdometerSection(snapshot.todayKm, snapshot.odometerKm)
            Divider()
            TempSection(snapshot)
            Divider()
            ThrottleSection(snapshot.throttle)
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .padding(vertical = 12.dp)
            .background(OutlineVariant.copy(alpha = 0.3f)),
    )
}

/** Fuel remaining from the gauge, in litres (0 until the signal arrives). */
@Composable
private fun FuelSection(fuelLevel: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("FUEL", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${fuelLevel.roundToInt()}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 27.sp, lineHeight = 30.sp),
                color = PrimaryFixed,
            )
            Text(
                text = "L",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryFixedDim.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        Text("REMAINING", style = MaterialTheme.typography.labelSmall, color = PrimaryFixedDim.copy(alpha = 0.6f))
    }
}

@Composable
private fun OdometerSection(todayKm: Float, odometerKm: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                // Derived in the app: live odometer minus the day-start reading.
                text = formatKm(todayKm),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 24.sp, lineHeight = 26.sp),
                color = OnSurface,
            )
            Text("TODAY KM", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.5f))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = formatKm(odometerKm),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 24.sp, lineHeight = 26.sp),
                color = OnSurface,
            )
            Text("TOTAL KM", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

/** "14,204"-style grouped formatting; 0 until ODOMETER arrives. */
private fun formatKm(km: Float): String =
    String.format(java.util.Locale.US, "%,d", km.roundToInt())

@Composable
private fun ThrottleSection(throttlePct: Float) {
    val segments = 14
    // Peak-hold with a slow fall: snap up to the live value instantly, ease
    // back down over ~800 ms so the meter doesn't stutter at 8 Hz.
    val displayPct = remember { Animatable(throttlePct) }
    LaunchedEffect(throttlePct) {
        if (throttlePct >= displayPct.value) {
            displayPct.snapTo(throttlePct)
        } else {
            displayPct.animateTo(
                throttlePct,
                tween(durationMillis = 800, easing = LinearOutSlowInEasing),
            )
        }
    }
    val litCount = (displayPct.value / 100f * segments).roundToInt().coerceIn(0, segments)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("THROTTLE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Column(
            modifier = Modifier
                .height(90.dp)
                .width(24.dp)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(segments) { i ->
                // level 0 at the bottom, rising toward the top
                val level = segments - 1 - i
                val heat = heatColor(level / (segments - 1f))
                val lit = level < litCount
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (lit) heat else heat.copy(alpha = 0.08f)),
                )
            }
        }
        Text("${displayPct.value.coerceIn(0f, 100f).roundToInt()}%", style = MaterialTheme.typography.bodySmall, color = Primary)
    }
}

/** Blue → amber → red heat ramp, rising with the level. */
private fun heatColor(t: Float): Color =
    if (t < 0.5f) {
        lerp(PrimaryContainer, Color(0xFFFFC84A), t * 2f)
    } else {
        lerp(Color(0xFFFFC84A), ErrorRed, (t - 0.5f) * 2f)
    }

@Composable
private fun TempSection(snapshot: CarSnapshot) {
    val tempC = snapshot.climate.tempC
    val fahrenheit = tempC * 9 / 5 + 32
    // Status follows the live coolant reading: no data (0) reads as COLD.
    val (statusLabel, statusColor) = when {
        tempC < 40 -> "COLD" to PrimaryFixedDim.copy(alpha = 0.6f)
        tempC < 108 -> "OPTIMAL" to PrimaryFixedDim.copy(alpha = 0.6f)
        else -> "HOT" to ErrorRed
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("TEMP", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "$fahrenheit°",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 27.sp, lineHeight = 30.sp),
                color = PrimaryFixed,
            )
            Icon(
                painter = painterResource(R.drawable.ic_trending_flat),
                contentDescription = null,
                tint = SecondaryFixed,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
    }
}

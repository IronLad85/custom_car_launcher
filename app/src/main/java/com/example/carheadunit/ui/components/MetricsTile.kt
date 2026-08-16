package com.example.carheadunit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.carheadunit.ui.theme.SurfaceHighest

/** Vehicle metrics strip: power gauge, range, odometer, throttle, temperature. */
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
            PowerGauge()
            Divider()
            RangeSection()
            Divider()
            OdometerSection()
            Divider()
            ThrottleSection()
            Divider()
            TempSection(snapshot)
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

@Composable
private fun PowerGauge() {
    BoxWithConstraints {
        // Gauge shrinks on short tiles
        val gaugeSize = (maxHeight - 26.dp).coerceIn(38.dp, 52.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("POWER", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Box(Modifier.size(gaugeSize), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 2.5.dp.toPx()
                val r = size.minDimension / 2f - stroke
                val topLeft = Offset(size.width / 2 - r, size.height / 2 - r)
                val arcSize = Size(2 * r, 2 * r)
                // Track
                drawArc(SurfaceHighest, 270f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                // Glow
                drawArc(
                    PrimaryContainer.copy(alpha = 0.25f),
                    270f,
                    360f * 0.42f,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke * 2f),
                )
                // Value: 42% sweep from 12 o'clock
                drawArc(
                    PrimaryContainer,
                    270f,
                    360f * 0.42f - 4f,
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Text("42%", style = MaterialTheme.typography.bodySmall, color = Primary)
            }
        }
    }
}

@Composable
private fun RangeSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("RANGE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        // Glow underlay for the lit segments
        Box {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .width(64.dp)
                    .height(6.dp)
                    .blur(3.dp),
            ) {
                repeat(4) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i < 3) SecondaryFixed else Color.Transparent),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .width(64.dp)
                    .height(6.dp),
            ) {
                repeat(4) { i ->
                    val lit = i < 3
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (lit) SecondaryFixed else SurfaceHighest),
                    )
                }
            }
        }
        Text("240 mi", style = MaterialTheme.typography.bodySmall, color = SecondaryFixed)
    }
}

@Composable
private fun OdometerSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_speed),
            contentDescription = null,
            tint = PrimaryFixedDim.copy(alpha = 0.8f),
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = "14,204",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = 15.sp),
            color = OnSurface,
        )
        Text("TOTAL MILES", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun ThrottleSection() {
    val segments = 12
    val litCount = 2 // 15% of 12
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("THROTTLE", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Column(
            modifier = Modifier
                .height(40.dp)
                .width(8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
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
                        .background(if (lit) heat else heat.copy(alpha = 0.22f)),
                )
            }
        }
        Text("15%", style = MaterialTheme.typography.bodySmall, color = Primary)
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
    val fahrenheit = snapshot.climate.tempC * 9 / 5 + 32
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("TEMP", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "$fahrenheit°",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 18.sp, lineHeight = 20.sp),
                color = PrimaryFixed,
            )
            Icon(
                painter = painterResource(R.drawable.ic_trending_flat),
                contentDescription = null,
                tint = SecondaryFixed,
                modifier = Modifier.size(14.dp),
            )
        }
        Text("OPTIMAL", style = MaterialTheme.typography.labelSmall, color = PrimaryFixedDim.copy(alpha = 0.6f))
    }
}

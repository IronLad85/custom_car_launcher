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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.carheadunit.ui.theme.SurfaceHighest

/** Main speedometer tile: dot-grid decor, big km/h readout, speed/RPM bars, gear, car render. */
@Composable
fun SpeedoTile(speed: SpeedInfo, modifier: Modifier = Modifier) {
    val kmh = speed.kmh
    val speedFrac = (kmh / 180f).coerceIn(0f, 1f)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Left 40%: metrics column (compact on short tiles)
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    val compact = maxHeight < 150.dp
                    // Readout scales with the tile: design 64px on tall screens
                    val readoutSize = when {
                        compact -> 30
                        maxHeight < 280.dp -> 48
                        else -> 64
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
                            Text(
                                text = "$kmh",
                                style = readoutStyle,
                                color = PrimaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.blur(6.dp),
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
                        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                        // Speed bar: cyan fill, white right-edge marker
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (compact) 10.dp else 18.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceHighest)
                                .border(1.dp, OutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(speedFrac)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(PrimaryContainer.copy(alpha = 0.6f), PrimaryContainer),
                                        )
                                    ),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.7f)),
                                )
                            }
                        }
                        Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
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
                        // RPM bar: 40% cyan fill + red zone on the right 20%
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (compact) 8.dp else 10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceHighest)
                                .border(1.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(0.4f)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(PrimaryContainer.copy(alpha = 0.4f), PrimaryContainer),
                                            )
                                        ),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(Color.White.copy(alpha = 0.5f)),
                                    )
                                }
                                Spacer(Modifier.weight(0.4f))
                                Box(
                                    modifier = Modifier
                                        .weight(0.2f)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(ErrorRed.copy(alpha = 0f), ErrorRed.copy(alpha = 0.6f)),
                                            )
                                        ),
                                )
                            }
                        }
                        Spacer(Modifier.height(if (compact) 0.dp else 10.dp))
                        // Gear "D" with cyan glow (hidden in compact mode)
                        if (!compact) {
                            val gearStyle = androidx.compose.material3.MaterialTheme.typography.displayMedium
                                .copy(fontSize = 20.sp, lineHeight = 24.sp)
                            Box {
                                Text(
                                    text = "D",
                                    style = gearStyle,
                                    color = PrimaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.blur(5.dp),
                                )
                                Text(
                                    text = "D",
                                    style = gearStyle,
                                    color = OnSurface,
                                )
                            }
                        }
                    }
                }
                // Right 60%: vehicle visualization
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawOval(
                            color = Color.Black.copy(alpha = 0.35f),
                            topLeft = Offset(size.width * 0.12f, size.height * 0.68f),
                            size = Size(size.width * 0.76f, size.height * 0.16f),
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.car),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

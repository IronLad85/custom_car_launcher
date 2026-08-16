package com.example.carheadunit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.ui.theme.ErrorRed
import com.example.carheadunit.ui.theme.SecondaryContainer

private val TurnGreen = SecondaryContainer        // turn indicators
private val FogAmber = Color(0xFFFFC84A)          // fog lamp (amber per design system)
private val BeamCyan = Color(0xFF9CF0FF)          // high beam

/**
 * Row 1 left panel: a smoked-glass telltale strip. Icons appear etched into
 * the glass when off and light up with a soft backlight bloom when on.
 * Single container + flat fills = the lightest indicator design for weak SoCs;
 * the only animation is a one-shot backlight pulse on state change.
 */
@Composable
fun IndicatorBar(
    turnLeft: Boolean = false,
    turnRight: Boolean = false,
    fog: Boolean = false,
    charge: Boolean = false,
    highBeam: Boolean = false,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x38000000), Color(0x1C000000)),
                    )
                )
                .border(1.dp, Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StripLamp(painterResource(R.drawable.ic_arrow_left_solid), TurnGreen, turnLeft, iconSize = 34.dp)
                StripLamp(rememberVectorPainter(Icons.Filled.Warning), ErrorRed, charge)
                StripLamp(painterResource(R.drawable.ic_arrow_right_solid), TurnGreen, turnRight, iconSize = 34.dp)
                StripLamp(painterResource(R.drawable.ic_filter_drama), FogAmber, fog)
                StripLamp(painterResource(R.drawable.ic_high_beam), BeamCyan, highBeam)
            }
        }
    }
}

/** One telltale: etched glyph when off, colored glyph + backlight bloom when lit. */
@Composable
private fun StripLamp(painter: Painter, tint: Color, lit: Boolean, iconSize: androidx.compose.ui.unit.Dp = 30.dp) {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(lit) {
        if (lit) {
            pulse.snapTo(1f)
            pulse.animateTo(0.25f, tween(900, easing = FastOutSlowInEasing))
        } else {
            pulse.snapTo(0f)
        }
    }
    Box(contentAlignment = Alignment.Center) {
        // Backlight bloom behind the lit glyph
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(tint.copy(alpha = 0.45f * pulse.value), tint.copy(alpha = 0f)),
                    )
                ),
        )
        Icon(
            painter = painter,
            contentDescription = null,
            tint = if (lit) tint else Color.White.copy(alpha = 0.14f),
            modifier = Modifier.size(iconSize),
        )
    }
}

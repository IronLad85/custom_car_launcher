package com.example.carheadunit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.ui.theme.ErrorRed
import com.example.carheadunit.ui.theme.OutlineVariant
import com.example.carheadunit.ui.theme.SecondaryContainer
import com.example.carheadunit.ui.theme.White20

/** Row 1 left panel: turn signals and vehicle status indicators (live from the ESP32). */
@Composable
fun IndicatorBar(
    turnLeft: Boolean = false,
    turnRight: Boolean = false,
    fog: Boolean = false,
    charge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GlowIcon(
                painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                tint = SecondaryContainer,
                lit = turnLeft,
            )
            GlowIcon(
                painter = rememberVectorPainter(Icons.Filled.Warning),
                tint = ErrorRed,
                lit = charge,
            )
            GlowIcon(
                painter = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward),
                tint = SecondaryContainer,
                lit = turnRight,
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(OutlineVariant.copy(alpha = 0.4f)),
            )
            GlowIcon(
                painter = painterResource(R.drawable.ic_filter_drama),
                tint = SecondaryContainer,
                lit = fog,
            )
            Icon(
                painter = painterResource(R.drawable.ic_stop_circle),
                contentDescription = null,
                tint = White20,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** Lit indicator: crisp icon over a blurred glow copy; 20% white when inactive. */
@Composable
private fun GlowIcon(painter: Painter, tint: Color, lit: Boolean) {
    Box {
        if (lit) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = tint.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(32.dp)
                    .blur(5.dp),
            )
            Icon(
                painter = painter,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = White20,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

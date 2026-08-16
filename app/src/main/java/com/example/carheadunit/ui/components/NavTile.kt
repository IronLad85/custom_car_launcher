package com.example.carheadunit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carheadunit.R
import com.example.carheadunit.ui.theme.OnSurface
import com.example.carheadunit.ui.theme.OnSurfaceVariant
import com.example.carheadunit.ui.theme.PrimaryContainer
import com.example.carheadunit.ui.theme.SurfaceContainer

/** Navigation tile: wireframe map background with turn-by-turn header and ETA bar. */
@Composable
fun NavTile(modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, contentPadding = 0.dp) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
            // Wireframe map, screen-blended look approximated with alpha
            Image(
                painter = painterResource(R.drawable.map),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.4f),
                contentScale = ContentScale.Crop,
            )
            // Gradient overlay: dark at the bottom for the ETA bar legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, SurfaceContainer.copy(alpha = 0.4f), SurfaceContainer.copy(alpha = 0.9f)),
                        )
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Turn-by-turn header
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(PrimaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_turn_right),
                            contentDescription = null,
                            tint = Color(0xFF00363D),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Exit 42B",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp, lineHeight = 36.sp),
                            color = OnSurface,
                        )
                        Text(
                            text = "I-95 Northbound",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 19.5.sp, lineHeight = 27.sp),
                            color = OnSurfaceVariant,
                        )
                    }
                }
                // ETA / distance bar
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 8.dp,
                    contentPadding = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ETA", style = MaterialTheme.typography.bodySmall.copy(fontSize = 18.sp, lineHeight = 24.sp), color = OnSurfaceVariant)
                            Text("14:30", style = MaterialTheme.typography.bodySmall.copy(fontSize = 18.sp, lineHeight = 24.sp), color = OnSurface)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dist", style = MaterialTheme.typography.bodySmall.copy(fontSize = 18.sp, lineHeight = 24.sp), color = OnSurfaceVariant)
                            Text("12 mi", style = MaterialTheme.typography.bodySmall.copy(fontSize = 18.sp, lineHeight = 24.sp), color = OnSurface)
                        }
                    }
                }
            }
        }
    }
}

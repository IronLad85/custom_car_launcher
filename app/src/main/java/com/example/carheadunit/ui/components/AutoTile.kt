package com.example.carheadunit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.ui.theme.OnSurface
import com.example.carheadunit.ui.theme.OnSurfaceVariant
import com.example.carheadunit.ui.theme.PrimaryContainer
import com.example.carheadunit.ui.theme.SecondaryContainer

/**
 * Android Auto launch card: hero tile that opens the real AA app or the
 * unit's link app (ZLink/AutoKit/EasyConnection) with one tap.
 */
@Composable
fun AutoTile(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val aaPackage = remember { AndroidAuto.findPackage(context) }

    GlassPanel(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { aaPackage?.let { AndroidAuto.launch(context, it) } },
        contentPadding = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Dark AA-style artwork as the tile background
            Image(
                painter = painterResource(R.drawable.aa_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // Bottom bar: title left, status right (space-between)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "ANDROID AUTO",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (aaPackage != null) SecondaryContainer else OnSurfaceVariant.copy(alpha = 0.4f),
                                CircleShape,
                            ),
                    )
                    Text(
                        text = if (aaPackage != null) "AVAILABLE" else "NOT INSTALLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (aaPackage != null) SecondaryContainer else OnSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

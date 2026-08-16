package com.example.carheadunit.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.data.MediaInfo
import com.example.carheadunit.ui.theme.GlassBorder
import com.example.carheadunit.ui.theme.GlassFill
import com.example.carheadunit.ui.theme.OnSurface
import com.example.carheadunit.ui.theme.OnSurfaceVariant
import com.example.carheadunit.ui.theme.OutlineVariant
import com.example.carheadunit.ui.theme.PrimaryContainer
import com.example.carheadunit.ui.theme.SurfaceBg
import com.example.carheadunit.ui.theme.SurfaceContainer

/**
 * Now-playing tile, driven by the system media notification (see
 * MediaNotificationService). Three states: prompt to enable notification
 * access, "nothing playing" fallback, and live track with working controls.
 */
@Composable
fun MediaTile(
    media: MediaInfo,
    mediaAccess: Boolean,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onRequestMediaAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier, contentPadding = 0.dp) {
        if (!mediaAccess) {
            AccessPrompt(onRequestMediaAccess)
            return@GlassPanel
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
            // Album art as ambient background (real art when available)
            if (media.albumArt != null) {
                Image(
                    bitmap = media.albumArt!!,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.25f),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.album),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.2f),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceContainer.copy(alpha = 0.8f)),
            )
            BoxWithConstraints {
                val compactMedia = maxHeight < 120.dp
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints {
                        val artSize = (maxHeight - 28.dp).coerceIn(64.dp, 128.dp)
                        if (media.albumArt != null) {
                            Image(
                                bitmap = media.albumArt!!,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(artSize)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.album),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(artSize)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryContainer,
                        )
                        Spacer(Modifier.height(if (compactMedia) 2.dp else 4.dp))
                        Text(
                            text = media.trackTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = MaterialTheme.typography.titleLarge.fontSize,
                            ),
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = media.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!compactMedia) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onPrev),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_skip_previous),
                                        contentDescription = stringResource(R.string.cd_pause),
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(OnSurface, CircleShape)
                                        .clickable(onClick = onTogglePlayback),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (media.isPlaying) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_pause),
                                            contentDescription = stringResource(R.string.cd_pause),
                                            tint = SurfaceBg,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = stringResource(R.string.cd_play),
                                            tint = SurfaceBg,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable(onClick = onNext),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_skip_next),
                                        contentDescription = stringResource(R.string.cd_play),
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shown until the user grants Notification access. */
@Composable
private fun AccessPrompt(onRequestMediaAccess: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = PrimaryContainer,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Enable notification access",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurface,
        )
        Text(
            text = "to show what's playing",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(GlassFill)
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                .clickable(onClick = onRequestMediaAccess)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "ENABLE",
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryContainer,
            )
        }
    }
}

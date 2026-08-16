package com.example.carheadunit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.data.CarSnapshot
import com.example.carheadunit.data.ClimateInfo
import com.example.carheadunit.data.MediaInfo
import com.example.carheadunit.data.SpeedInfo
import com.example.carheadunit.ui.theme.AccentViolet
import com.example.carheadunit.ui.theme.GlassBorder
import com.example.carheadunit.ui.theme.GlassFill
import com.example.carheadunit.ui.theme.TextPrimary
import com.example.carheadunit.ui.theme.TextSecondary

/** Row of the three car-data glass cards shown at the top of the home screen. */
@Composable
fun CarDataCards(
    snapshot: CarSnapshot,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SpeedCard(snapshot.speed, Modifier.weight(1f))
        ClimateCard(snapshot.climate, Modifier.weight(1f))
        MediaCard(snapshot.media, onTogglePlayback, Modifier.weight(1f))
    }
}

@Composable
private fun SpeedCard(speed: SpeedInfo, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxHeight(), contentPadding = 0.dp) {
        Box(Modifier.fillMaxSize()) {
            // The road scene is the card's full background
            RoadBackground(Modifier.fillMaxSize())
            // Speed readout in a glass badge over the asphalt
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(GlassFill, RoundedCornerShape(14.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${speed.kmh}",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = speed.unit,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClimateCard(climate: ClimateInfo, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier.fillMaxHeight()) {
        Icon(
            painter = painterResource(R.drawable.ic_climate),
            contentDescription = stringResource(R.string.climate_label),
            tint = AccentViolet,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${climate.tempC}°",
                style = MaterialTheme.typography.displayLarge,
                color = TextPrimary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "FAN ${climate.fanLevel}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(climate.fanMax) { level ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            color = if (level < climate.fanLevel) TextPrimary else GlassBorder,
                            shape = RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun MediaCard(
    media: MediaInfo,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_music),
                contentDescription = stringResource(R.string.media_label),
                tint = AccentViolet,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.weight(1f))
            PlayPauseButton(media.isPlaying, onTogglePlayback)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = media.trackTitle,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = media.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(GlassFill, CircleShape)
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            Icon(
                painter = painterResource(R.drawable.ic_pause),
                contentDescription = stringResource(R.string.cd_pause),
                tint = AccentViolet,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.cd_play),
                tint = AccentViolet,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

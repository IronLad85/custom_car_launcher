package com.example.carheadunit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.carheadunit.data.CarSnapshot
import com.example.carheadunit.ui.components.AutoTile
import com.example.carheadunit.ui.components.IndicatorBar
import com.example.carheadunit.ui.components.MediaTile
import com.example.carheadunit.ui.components.MetricsTile
import com.example.carheadunit.ui.components.SpeedoTile
import com.example.carheadunit.ui.components.SteeringTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * Dashboard content (indicators, steering, speed/metrics, nav/media).
 *
 * Collects the 1 Hz telemetry flow internally instead of receiving the value
 * from the screen root: when the app drawer is open this composable is not
 * composed, so the telemetry tick recomposes nothing — the drawer grid stays
 * untouched on low-end head-unit SoCs.
 */
@Composable
fun Dashboard(
    snapshotFlow: StateFlow<CarSnapshot>,
    mediaAccess: Boolean,
    onTogglePlayback: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    onRequestMediaAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot by snapshotFlow.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 80.dp),
    ) {
        // Row 1: indicators + steering track
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IndicatorBar(
                turnLeft = snapshot.turnLeftLamp,
                turnRight = snapshot.turnRightLamp,
                fog = snapshot.fogLight,
                charge = snapshot.chargeWarning,
                highBeam = snapshot.highBeam,
                modifier = Modifier.weight(7f).fillMaxHeight(),
            )
            SteeringTrack(
                activeFraction = snapshot.steeringFraction,
                modifier = Modifier.weight(5f).fillMaxHeight(),
            )
        }
        Spacer(Modifier.height(12.dp))
        // Row 2: driving zones (columns flex; metrics/media cap at design sizes)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // Top row: speed (7/12) + nav (5/12)
                Row(
                    modifier = Modifier
                        .weight(1.07f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpeedoTile(
                        speed = snapshot.speed,
                        modifier = Modifier
                            .weight(7f)
                            .fillMaxHeight()
                            .heightIn(min = 66.dp),
                    )
                    AutoTile(
                        modifier = Modifier
                            .weight(5f)
                            .fillMaxHeight()
                            .heightIn(min = 69.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                // Bottom row: media (5/12) + metrics (7/12) — same width as the speed tile
                Row(
                    modifier = Modifier
                        .weight(0.765f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaTile(
                        media = snapshot.media,
                        mediaAccess = mediaAccess,
                        onTogglePlayback = onTogglePlayback,
                        onNext = onNextTrack,
                        onPrev = onPrevTrack,
                        onRequestMediaAccess = onRequestMediaAccess,
                        modifier = Modifier
                            .weight(5f)
                            .fillMaxHeight()
                            .heightIn(min = 73.dp, max = 147.dp),
                    )
                    MetricsTile(
                        snapshot = snapshot,
                        modifier = Modifier
                            .weight(7f)
                            .fillMaxHeight()
                            .heightIn(min = 55.dp, max = 122.dp),
                    )
                }
            }
        }
    }
}

package com.example.carheadunit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.data.CarSnapshot
import com.example.carheadunit.data.UsbLinkState
import com.example.carheadunit.ui.components.IndicatorBar
import com.example.carheadunit.ui.components.MediaTile
import com.example.carheadunit.ui.components.MetricsTile
import com.example.carheadunit.ui.components.NavDock
import com.example.carheadunit.ui.components.AutoTile
import com.example.carheadunit.ui.components.SpeedoTile
import com.example.carheadunit.ui.components.SteeringTrack
import com.example.carheadunit.ui.theme.CarHeadUnitTheme
import com.example.carheadunit.ui.theme.CyanGlowAmbient
import com.example.carheadunit.ui.theme.SurfaceBg

/**
 * NovaLink OS dashboard home: solid charcoal base with an ambient cyan glow,
 * indicator + steering row, speed/metrics column and nav/media column, and
 * the persistent app dock. Tile heights flex with the screen; the metrics and
 * media tiles keep their design size on tall screens and compress on short ones.
 */
@Composable
fun HomeScreen(
    snapshot: CarSnapshot,
    apps: List<AppEntry>,
    pinnedSet: Set<String>,
    drawerOpen: Boolean,
    usbStatus: UsbLinkState,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onNextTrack: () -> Unit,
    onPrevTrack: () -> Unit,
    mediaAccess: Boolean,
    onRequestMediaAccess: () -> Unit,
    onOpenAllApps: () -> Unit,
    onCloseDrawer: () -> Unit,
) {
    val pinnedApps = apps.filter { it.packageName in pinnedSet }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg),
    ) {
        // Ambient cyan glow
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.53f, size.height * 0.48f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyanGlowAmbient, Color.Transparent),
                    center = center,
                    radius = 340.dp.toPx(),
                ),
                radius = 340.dp.toPx(),
                center = center,
            )
        }
        if (drawerOpen) {
                AllAppsScreen(
                apps = apps,
                pinned = pinnedSet,
                onLaunch = onLaunch,
                onTogglePin = onTogglePin,
                onClose = onCloseDrawer,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 74.dp),
            )
        } else {
            Column(
                modifier = Modifier
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
        NavDock(
            pinned = pinnedApps,
            drawerOpen = drawerOpen,
            usbStatus = usbStatus,
            onLaunch = onLaunch,
            onUnpin = onTogglePin,
            onOpenAllApps = onOpenAllApps,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    CarHeadUnitTheme {
        HomeScreen(
            snapshot = CarSnapshot(),
            apps = emptyList(),
            pinnedSet = emptySet(),
            drawerOpen = false,
            usbStatus = UsbLinkState.OFFLINE,
            onLaunch = {},
            onTogglePin = {},
            onTogglePlayback = {},
            onNextTrack = {},
            onPrevTrack = {},
            mediaAccess = false,
            onRequestMediaAccess = {},
            onOpenAllApps = {},
            onCloseDrawer = {},
        )
    }
}

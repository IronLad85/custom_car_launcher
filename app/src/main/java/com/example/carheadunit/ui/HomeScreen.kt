package com.example.carheadunit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.data.CarSnapshot
import com.example.carheadunit.data.UsbLinkState
import com.example.carheadunit.ui.components.NavDock
import com.example.carheadunit.ui.theme.CarHeadUnitTheme
import com.example.carheadunit.ui.theme.CyanGlowAmbient
import com.example.carheadunit.ui.theme.SurfaceBg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * NovaLink OS dashboard home: solid charcoal base with an ambient cyan glow,
 * the dashboard (indicators/steering/speed/metrics/nav/media) or the all-apps
 * drawer on top, and the persistent app dock.
 *
 * Performance note for low-end head units: the 1 Hz telemetry flow is
 * collected inside [Dashboard], which is only composed while the drawer is
 * closed. Opening the drawer stops the telemetry tick from recomposing the
 * grid. The ambient glow is a remembered modifier — it draws once and is not
 * re-shaded on recomposition.
 */
@Composable
fun HomeScreen(
    snapshotFlow: StateFlow<CarSnapshot>,
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
    // Ambient cyan glow: remembered modifier so recomposition never re-runs the
    // full-screen radial-gradient shader (only layout/size changes redraw it).
    val glowModifier = remember {
        Modifier
            .fillMaxSize()
            .drawBehind {
                val center = Offset(size.width * 0.53f, size.height * 0.48f)
                val radius = 340.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(CyanGlowAmbient, Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
    }
    val pinnedApps = remember(apps, pinnedSet) { apps.filter { it.packageName in pinnedSet } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .then(glowModifier),
    ) {
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
            Dashboard(
                snapshotFlow = snapshotFlow,
                mediaAccess = mediaAccess,
                onTogglePlayback = onTogglePlayback,
                onNextTrack = onNextTrack,
                onPrevTrack = onPrevTrack,
                onRequestMediaAccess = onRequestMediaAccess,
            )
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
            snapshotFlow = MutableStateFlow(CarSnapshot()),
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

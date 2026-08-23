package com.example.carheadunit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
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
 * the dashboard (indicators/steering/speed/metrics/nav/media) with the
 * full-screen all-apps page over it, and the persistent app dock.
 *
 * Performance notes for low-end head units:
 *  - The all-apps page is composed ONCE at startup and stays composed (alpha 0
 *    draws nothing), so opening it is instant instead of paying the full
 *    first-composition cost on tap; its pager pages are lazy, so only the
 *    visible page is ever composed.
 *  - The telemetry flow is collected inside [Dashboard]; while the page is
 *    open it is swapped for a static flow, so the tick recomposes nothing
 *    under the app grid.
 *  - The ambient glow is a remembered modifier — drawn once, not re-shaded.
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
    val pinnedApps = remember(apps, pinnedSet) { apps.filter { it.key in pinnedSet } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .then(glowModifier),
    ) {
        Dashboard(
            snapshotFlow = snapshotFlow,
            active = !drawerOpen,
            mediaAccess = mediaAccess,
            onTogglePlayback = onTogglePlayback,
            onNextTrack = onNextTrack,
            onPrevTrack = onPrevTrack,
            onRequestMediaAccess = onRequestMediaAccess,
            modifier = Modifier.zIndex(1f),
        )
        // Pre-warmed all-apps page: composed once at startup and kept
        // composed — zIndex puts it below the dashboard while closed (no
        // touch pass-through) and above when open; alpha 0 draws nothing.
        AllAppsScreen(
            apps = apps,
            pinned = pinnedSet,
            onLaunch = onLaunch,
            onTogglePin = onTogglePin,
            onClose = onCloseDrawer,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (drawerOpen) 2f else 0f)
                .alpha(if (drawerOpen) 1f else 0f),
        )
        NavDock(
            pinned = pinnedApps,
            drawerOpen = drawerOpen,
            usbStatus = usbStatus,
            onLaunch = onLaunch,
            onUnpin = onTogglePin,
            onOpenAllApps = onOpenAllApps,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(3f),
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

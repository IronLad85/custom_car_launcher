package com.example.carheadunit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.data.CarSnapshot
import com.example.carheadunit.ui.components.CarDataCards
import com.example.carheadunit.ui.components.DockBar
import com.example.carheadunit.ui.theme.CarHeadUnitTheme
import com.example.carheadunit.ui.theme.GradientEnd
import com.example.carheadunit.ui.theme.GradientMid
import com.example.carheadunit.ui.theme.GradientStart

/**
 * Launcher home: gradient background, car-data cards on top, app dock at the bottom.
 * The all-apps drawer replaces this content while open. Stateless so it previews easily.
 */
@Composable
fun HomeScreen(
    snapshot: CarSnapshot,
    apps: List<AppEntry>,
    pinnedSet: Set<String>,
    drawerOpen: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenAllApps: () -> Unit,
    onCloseDrawer: () -> Unit,
) {
    val pinnedApps = apps.filter { it.packageName in pinnedSet }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(GradientStart, GradientMid, GradientEnd),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (drawerOpen) {
            AllAppsScreen(
                apps = apps,
                pinned = pinnedSet,
                onLaunch = onLaunch,
                onTogglePin = onTogglePin,
                onClose = onCloseDrawer,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                CarDataCards(
                    snapshot = snapshot,
                    onTogglePlayback = onTogglePlayback,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(Modifier.height(16.dp))
                DockBar(
                    pinned = pinnedApps,
                    onLaunch = onLaunch,
                    onUnpin = onTogglePin,
                    onOpenAllApps = onOpenAllApps,
                )
            }
        }
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
            onLaunch = {},
            onTogglePin = {},
            onTogglePlayback = {},
            onOpenAllApps = {},
            onCloseDrawer = {},
        )
    }
}

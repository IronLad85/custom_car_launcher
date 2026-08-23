package com.example.carheadunit.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.data.UsbLinkState
import com.example.carheadunit.ui.theme.OnSurface
import com.example.carheadunit.ui.theme.OnSurfaceVariant
import com.example.carheadunit.ui.theme.OutlineVariant
import com.example.carheadunit.ui.theme.PrimaryContainer
import com.example.carheadunit.ui.theme.SurfaceContainer
import com.example.carheadunit.ui.theme.SurfaceLowest
import com.example.carheadunit.ui.theme.TopBorderWhite10

private const val MAX_DOCK_ITEMS = 8

/**
 * Bottom bar in the design's language: solid #1e2023, 80dp tall, top border,
 * split into two sections — the ESP/USB status chip in its own darker panel
 * on the left, and the app bar (pinned apps + all-apps item) filling the
 * rest. The panel and its divider span the full dock height (including the
 * system bottom inset) so the split reads edge-to-edge; the app bar's own
 * content area stays 66dp. The all-apps item takes the active (cyan + dot)
 * state while the drawer is open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavDock(
    pinned: List<AppEntry>,
    drawerOpen: Boolean,
    usbStatus: UsbLinkState,
    onLaunch: (AppEntry) -> Unit,
    onUnpin: (String) -> Unit,
    onOpenAllApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainer)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TopBorderWhite10),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ESP/USB status section: its own darker panel. The panel and the
            // divider span the full dock height — including any system bottom
            // inset — so the section reads edge-to-edge instead of floating
            // above a strip of bar color. The inset is consumed as padding
            // inside the panel (after the background), so the chip centers in
            // the same 66dp content band as the app icons.
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .background(SurfaceLowest)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                contentAlignment = Alignment.Center,
            ) {
                UsbStatusChip(state = usbStatus)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(OutlineVariant),
            )
            // App bar section: pinned apps centered in the remaining width.
            // Its own bottom inset keeps the bar's content area at 66dp.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pinned.isEmpty()) {
                            Text(
                                text = stringResource(R.string.dock_empty_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceAround,
                            ) {
                                pinned.take(MAX_DOCK_ITEMS).forEach { app ->
                                    DockItem(app = app, onLaunch = onLaunch, onUnpin = onUnpin)
                                }
                            }
                        }
                    }
                    AllAppsItem(active = drawerOpen, onClick = onOpenAllApps)
                }
            }
        }
    }
}

/** Compact USB link status: colored dot + label, at the left end of the dock. */
@Composable
private fun UsbStatusChip(state: UsbLinkState, modifier: Modifier = Modifier) {
    val dotColor = when (state) {
        UsbLinkState.DATA -> Color(0xFF4ADE80) // green — telemetry flowing
        UsbLinkState.STREAMING, UsbLinkState.CONNECTED -> PrimaryContainer // cyan
        UsbLinkState.RETRYING, UsbLinkState.CONNECTING -> Color(0xFFFBBF24) // amber
        UsbLinkState.FAILED -> Color(0xFFF87171) // red
        UsbLinkState.OFFLINE -> OnSurfaceVariant.copy(alpha = 0.45f) // gray
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OnSurfaceVariant.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(dotColor, CircleShape),
        )
        Text(
            text = state.label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    app: AppEntry,
    onLaunch: (AppEntry) -> Unit,
    onUnpin: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = { onLaunch(app) },
                onLongClick = { onUnpin(app.key) },
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(45.dp),
        )
    }
}



@Composable
private fun AllAppsItem(active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_apps_grid),
            contentDescription = stringResource(R.string.all_apps_cd),
            tint = if (active) PrimaryContainer else OnSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(45.dp),
        )
        if (active) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(PrimaryContainer, CircleShape),
            )
        }
    }
}

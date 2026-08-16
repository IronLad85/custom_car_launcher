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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.ui.theme.OnSurface
import com.example.carheadunit.ui.theme.OnSurfaceVariant
import com.example.carheadunit.ui.theme.PrimaryContainer
import com.example.carheadunit.ui.theme.SurfaceContainer
import com.example.carheadunit.ui.theme.TopBorderWhite10

private const val MAX_DOCK_ITEMS = 8

/**
 * Bottom nav dock in the design's language: solid #1e2023 bar, 80dp tall,
 * top border, 28px icons with mono labels. Pinned apps live here; the
 * all-apps item takes the active (cyan + dot) state while the drawer is open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NavDock(
    pinned: List<AppEntry>,
    drawerOpen: Boolean,
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
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            if (pinned.isEmpty()) {
                Text(
                    text = stringResource(R.string.dock_empty_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                )
            } else {
                pinned.take(MAX_DOCK_ITEMS).forEach { app ->
                    DockItem(app = app, onLaunch = onLaunch, onUnpin = onUnpin)
                }
            }
            AllAppsItem(active = drawerOpen, onClick = onOpenAllApps)
        }
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
                onLongClick = { onUnpin(app.packageName) },
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

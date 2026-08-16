package com.example.carheadunit.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.carheadunit.R
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.ui.theme.GlassBorder
import com.example.carheadunit.ui.theme.GlassFill
import com.example.carheadunit.ui.theme.TextPrimary
import com.example.carheadunit.ui.theme.TextSecondary

private const val MAX_DOCK_ITEMS = 8

/**
 * Bottom dock: pinned apps as large glass tiles plus the "all apps" button.
 * Tap launches, long-press unpins.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DockBar(
    pinned: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onUnpin: (String) -> Unit,
    onOpenAllApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxWidth(), cornerRadius = 16.dp, contentPadding = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (pinned.isEmpty()) {
                Text(
                    text = stringResource(R.string.dock_empty_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Flexbox-style: every pinned tile stretches to share the
                // dock width equally, with the icon centered in its slot.
                pinned.take(MAX_DOCK_ITEMS).forEach { app ->
                    DockItem(
                        app = app,
                        onLaunch = onLaunch,
                        onUnpin = onUnpin,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            AllAppsButton(onOpenAllApps)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    app: AppEntry,
    onLaunch: (AppEntry) -> Unit,
    onUnpin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val bitmap = remember(app.packageName) { app.icon.toBitmap() }
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = app.label,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onLongClick = { onUnpin(app.packageName) },
                )
                .padding(4.dp)
                .size(44.dp),
        )
    }
}

@Composable
private fun AllAppsButton(onOpenAllApps: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(GlassFill, CircleShape)
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(onClick = onOpenAllApps),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_apps_grid),
            contentDescription = stringResource(R.string.all_apps_cd),
            tint = TextPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

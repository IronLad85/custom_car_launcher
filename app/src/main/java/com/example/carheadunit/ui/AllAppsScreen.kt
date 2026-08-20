package com.example.carheadunit.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.ui.theme.GlassBorder
import com.example.carheadunit.ui.theme.GlassFill
import com.example.carheadunit.ui.theme.OnSurface
import com.example.carheadunit.ui.theme.PrimaryContainer

/**
 * Full-screen drawer listing every installed app in glass tiles.
 * Tap launches, long-press pins/unpins to the dock.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsScreen(
    apps: List<AppEntry>,
    pinned: Set<String>,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(GlassFill, CircleShape)
                    .border(1.dp, GlassBorder, CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = OnSurface,
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.all_apps_title),
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                // Stable per-item lambdas: when only `pinned` changes, every
                // tile whose pin state is unchanged skips recomposition.
                val launchThis = remember(app) { { onLaunch(app) } }
                val toggleThis = remember(app) { { onTogglePin(app.packageName) } }
                AppTile(
                    app = app,
                    isPinned = app.packageName in pinned,
                    rowIndex = index / 6,
                    onLaunch = launchThis,
                    onTogglePin = toggleThis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    isPinned: Boolean,
    rowIndex: Int,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val iconSize = when (rowIndex) {
        2 -> 40.dp
        3 -> 58.dp
        else -> 48.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GlassFill)
            .border(
                width = 1.dp,
                color = if (isPinned) PrimaryContainer.copy(alpha = 0.6f) else GlassBorder,
                shape = RoundedCornerShape(14.dp),
            )
            .combinedClickable(onClick = onLaunch, onLongClick = onTogglePin)
            .padding(vertical = if (rowIndex == 2) 12.dp else 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.height(if (rowIndex == 3) 10.dp else 8.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (isPinned) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(PrimaryContainer, CircleShape),
            )
        }
    }
}

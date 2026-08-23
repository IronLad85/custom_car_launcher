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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.carheadunit.R
import com.example.carheadunit.data.AppEntry
import com.example.carheadunit.ui.theme.AllAppsBackground
import com.example.carheadunit.ui.theme.GlassBorder
import com.example.carheadunit.ui.theme.GlassFill
import com.example.carheadunit.ui.theme.OnAllApps
import com.example.carheadunit.ui.theme.OnAllAppsVariant
import com.example.carheadunit.ui.theme.OnSurface
import com.example.carheadunit.ui.theme.PrimaryContainer

// Dock clearance at the bottom of the page (NavDock is 80dp tall).
private val DOCK_INSET = 74.dp

// 6 columns; 4 rows fit 720p-class panels, 3 rows fit 600p-class panels —
// a page never needs to scroll vertically.
private const val GRID_COLUMNS = 6
private const val GRID_ROWS_TALL = 4
private const val GRID_ROWS_SHORT = 3

/**
 * Full-screen all-apps page: opaque white over the dashboard (reads as its
 * own page, not a popup), apps laid out as horizontally paged grids.
 * Swipe left/right to flip pages; tap launches, long-press pins/unpins.
 *
 * Performance notes for low-end head units:
 *  - HorizontalPager is lazy: only the visible page (plus one pre-composed
 *    adjacent page, laid out but not drawn) is composed, and nothing at all
 *    while the drawer is closed (the whole subtree is alpha 0 — the
 *    background fill draws nothing when hidden).
 *  - Each page is a bounded static layout (24 tiles max) — nothing scrolls
 *    or recycles inside a page, and the pager keeps its position across
 *    close/open because the screen stays composed.
 *  - The opaque background is a single solid-color fill.
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
    // Row count follows the panel height so no page scrolls vertically.
    val rows = if (LocalConfiguration.current.screenHeightDp >= 700) GRID_ROWS_TALL else GRID_ROWS_SHORT
    val perPage = GRID_COLUMNS * rows
    // Clear the whole dock: its fixed height plus any system bottom inset
    // (the dock background paints over the inset strip, so the grid must
    // not tuck under it on devices with a soft nav area).
    val bottomInset =
        WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    // Coerced to >= 1: the pager needs at least one page while apps are loading.
    val pageCount = remember(apps.size, rows) { maxOf(1, (apps.size + perPage - 1) / perPage) }
    val pagerState = rememberPagerState(pageCount = { pageCount })
    // App uninstalled while a later page was showing → clamp to the new last page.
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) pagerState.scrollToPage(pageCount - 1)
    }
    Box(
        // Fill painted on the outer Box (before insets/padding), so the white
        // covers the whole screen including behind the dock.
        modifier = modifier
            .fillMaxSize()
            .background(AllAppsBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = DOCK_INSET + bottomInset),
        ) {
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
                    color = OnAllApps,
                )
            }
            HorizontalPager(
                state = pagerState,
                // Pre-compose the adjacent page so the first swipe into it
                // doesn't pay its composition mid-gesture on weak SoCs; the
                // pre-composed page is laid out but never drawn until it
                // scrolls into view, so the steady-state cost stays small.
                // (Renamed to beyondViewportPageCount in Compose 1.7.)
                beyondBoundsPageCount = 1,
                modifier = Modifier.weight(1f),
            ) { page ->
                val start = page * perPage
                val end = minOf(start + perPage, apps.size)
                val pageApps = remember(apps, start, end) {
                    if (start < end) apps.subList(start, end) else emptyList()
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for (row in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            for (col in 0 until GRID_COLUMNS) {
                                val app = pageApps.getOrNull(row * GRID_COLUMNS + col)
                                if (app == null) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    // Stable per-item lambdas: when only `pinned`
                                    // changes, tiles whose pin state is unchanged
                                    // skip recomposition.
                                    val launchThis = remember(app) { { onLaunch(app) } }
                                    val toggleThis = remember(app) { { onTogglePin(app.key) } }
                                    AppTile(
                                        app = app,
                                        isPinned = app.key in pinned,
                                        onLaunch = launchThis,
                                        onTogglePin = toggleThis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Page dots — only when there is more than one page.
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pageCount) { index ->
                        val active = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (active) 8.dp else 7.dp)
                                .background(
                                    if (active) OnAllApps else OnAllAppsVariant,
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppEntry,
    isPinned: Boolean,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Plain tile (no glass decoration): icon + label only — the cheapest
    // possible draw on software-rendered SoCs.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onLaunch, onLongClick = onTogglePin)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            color = OnAllApps,
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

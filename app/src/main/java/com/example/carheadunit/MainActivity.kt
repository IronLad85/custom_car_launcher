package com.example.carheadunit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.carheadunit.data.MediaActionType
import com.example.carheadunit.ui.HomeScreen
import com.example.carheadunit.ui.LauncherViewModel
import com.example.carheadunit.ui.theme.CarHeadUnitTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)        // Dark system bar icons over the dark gradient, regardless of the device's
        // system theme; the OS status bar stays visible.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        // The home screen never finishes: back closes the drawer, otherwise it is consumed.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.drawerOpen.value) viewModel.closeDrawer()
            }
        })

        setContent {
            CarHeadUnitTheme {
                val apps by viewModel.apps.collectAsState()
                val pinnedSet by viewModel.pinned.collectAsState()
                val drawerOpen by viewModel.drawerOpen.collectAsState()
                val mediaAccess by viewModel.mediaAccess.collectAsState()
                val usbStatus by viewModel.usbStatus.collectAsState()

                HomeScreen(
                    // Telemetry is collected inside Dashboard, which is only
                    // composed while the drawer is closed — the 1 Hz tick must
                    // not recompose the all-apps grid on low-end SoCs.
                    snapshotFlow = viewModel.snapshot,
                    apps = apps,
                    pinnedSet = pinnedSet,
                    drawerOpen = drawerOpen,
                    usbStatus = usbStatus,
                    onLaunch = viewModel::launchApp,
                    onTogglePin = viewModel::togglePin,
                    onTogglePlayback = { viewModel.mediaControl(MediaActionType.PLAY_PAUSE) },
                    onNextTrack = { viewModel.mediaControl(MediaActionType.NEXT) },
                    onPrevTrack = { viewModel.mediaControl(MediaActionType.PREV) },
                    mediaAccess = mediaAccess,
                    onRequestMediaAccess = viewModel::requestMediaAccess,
                    onOpenAllApps = viewModel::openDrawer,
                    onCloseDrawer = viewModel::closeDrawer,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onForeground()
    }

    override fun onStop() {
        viewModel.onBackground()
        super.onStop()
    }
}

package com.example.spacenewslaunchertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spacenewslaunchertracker.ui.AppViewModel
import com.example.spacenewslaunchertracker.ui.LaunchesTab
import com.example.spacenewslaunchertracker.ui.NewsTab
import com.example.spacenewslaunchertracker.ui.SettingsTab
import com.example.spacenewslaunchertracker.ui.theme.SpaceNewsLauncherTrackerTheme
import kotlinx.coroutines.delay
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goFullscreen()
        setContent {
            SpaceNewsLauncherTrackerTheme {
                SpaceTrackerApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The system restores the bars after a swipe or when focus returns; put them away again.
        if (hasFocus) goFullscreen()
    }

    /** Immersive fullscreen: no status or navigation bar, revealed transiently on swipe. */
    private fun goFullscreen() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private enum class Tab(val label: String, val icon: Int) {
    LAUNCHES("Launches", R.drawable.ic_rocket),
    NEWS("News", R.drawable.ic_news),
    SETTINGS("Settings", R.drawable.ic_settings),
}

@Composable
private fun SpaceTrackerApp(viewModel: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.LAUNCHES) }

    val display by viewModel.display.collectAsState()
    val bedrock by viewModel.bedrock.collectAsState()
    val launches by viewModel.launches.collectAsState()
    val news by viewModel.news.collectAsState()
    val explanations by viewModel.explanations.collectAsState()

    // The only clock in the app. When "Live countdown" is off it is sampled once per
    // composition instead of every second; either way it reads from the device clock and
    // never triggers a network call.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(display.liveCountdown) {
        if (!display.liveCountdown) {
            now = Instant.now()
            return@LaunchedEffect
        }
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        icon = {
                            Icon(painterResource(entry.icon), contentDescription = entry.label)
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(spaceGradient())
                .padding(innerPadding),
        ) {
            AppHeader(tab)
            when (tab) {
                Tab.LAUNCHES -> LaunchesTab(
                    state = launches,
                    now = now,
                    explanations = explanations,
                    isBedrockConfigured = bedrock.isComplete,
                    onRetry = viewModel::loadLaunches,
                    onExplain = viewModel::explain,
                    onRetryExplain = viewModel::retryExplain,
                )

                Tab.NEWS -> NewsTab(state = news, onRetry = viewModel::loadNews)

                Tab.SETTINGS -> SettingsTab(
                    display = display,
                    bedrock = bedrock,
                    onLaunchCountChange = viewModel::setLaunchCount,
                    onNewsCountChange = viewModel::setNewsCount,
                    onLiveCountdownChange = viewModel::setLiveCountdown,
                    onBedrockChange = viewModel::updateBedrockSettings,
                    onRefreshNow = viewModel::refreshEverything,
                )
            }
        }
    }
}

@Composable
private fun AppHeader(tab: Tab) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "MISSION CONTROL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = when (tab) {
                    Tab.LAUNCHES -> "Upcoming Launches"
                    Tab.NEWS -> "Space News"
                    Tab.SETTINGS -> "Settings"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** A subtle vertical wash so the long scrolling lists don't sit on flat black. */
@Composable
private fun spaceGradient(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        0f to colors.surfaceContainer,
        0.45f to colors.background,
        1f to colors.background,
    )
}

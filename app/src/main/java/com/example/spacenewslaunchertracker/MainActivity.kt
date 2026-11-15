package com.example.spacenewslaunchertracker

import android.animation.ValueAnimator
import android.os.Bundle
import android.provider.Settings
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.spacenewslaunchertracker.ui.AppViewModel
import com.example.spacenewslaunchertracker.ui.LaunchesTab
import com.example.spacenewslaunchertracker.ui.NewsTab
import com.example.spacenewslaunchertracker.ui.SettingsTab
import com.example.spacenewslaunchertracker.ui.components.starfield
import com.example.spacenewslaunchertracker.ui.theme.SpaceNewsLauncherTrackerTheme
import kotlinx.coroutines.delay
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchSplashRocket()
        enableEdgeToEdge()
        goFullscreen()
        setContent {
            // Respect the system "remove animations" setting: the starfield and the ignition
            // glow read this through `LocalAmbientMotion` and hold still when it is off.
            val ambientMotion = remember { animationsEnabled() }
            SpaceNewsLauncherTrackerTheme(ambientMotion = ambientMotion) {
                SpaceTrackerApp()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The system restores the bars after a swipe or when focus returns; put them away again.
        if (hasFocus) goFullscreen()
    }

    /**
     * Lift-off: the splash rocket accelerates from the centre off the top of the screen while
     * the splash fades to reveal the app.
     *
     * Nothing here delays startup. The listener only runs once the first frame is ready,
     * and the whole flight is [SPLASH_EXIT_MILLIS] — short enough to read as a transition
     * rather than a wait. It *must* end in `remove()`: until then the splash stays on top
     * and the app cannot be used.
     *
     * The icon view is moved rather than the drawable animated because an animated splash
     * icon is clipped to its circular mask; only the view can travel off screen.
     */
    private fun launchSplashRocket() {
        splashScreen.setOnExitAnimationListener { splashView ->
            if (!animationsEnabled()) {
                splashView.remove()
                return@setOnExitAnimationListener
            }
            val rocket = splashView.iconView
            rocket?.animate()
                // Far enough that the flame's tail clears the top edge too.
                ?.translationY(-(rocket.bottom + rocket.height * 0.25f))
                ?.setDuration(SPLASH_EXIT_MILLIS)
                // Slow off the pad, then fast: a launch, not a slide.
                ?.setInterpolator(AccelerateInterpolator(1.8f))
                ?.start()

            // Fade the *background* only. Fading the splash view would fade its child, the
            // rocket, too, and it would vanish mid-climb instead of leaving the screen.
            val background = splashView.background
            if (background == null) {
                splashView.animate().alpha(0f).setDuration(SPLASH_EXIT_MILLIS)
                    .withEndAction { splashView.remove() }.start()
                return@setOnExitAnimationListener
            }
            ValueAnimator.ofInt(255, 0).apply {
                duration = SPLASH_EXIT_MILLIS
                addUpdateListener { background.alpha = it.animatedValue as Int }
                // Runs on cancel as well as on end, so the splash can never be left behind.
                doOnEnd { splashView.remove() }
                start()
            }
        }
    }

    private companion object {
        /** The entire splash exit, in milliseconds. */
        const val SPLASH_EXIT_MILLIS = 450L
    }

    /** False when the user has asked the system to remove animations. */
    private fun animationsEnabled(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

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
                // An AGSL starfield (with a static fallback where RuntimeShader is
                // unavailable), drifting slowly enough to stay behind the content.
                .starfield(speed = 0.55f, density = 0.13f)
                .padding(innerPadding),
        ) {
            AppHeader(tab)
            when (tab) {
                Tab.LAUNCHES -> LaunchesTab(
                    state = launches,
                    // A lambda, so reading the clock is deferred to the few composables that
                    // display it; see LaunchesTab.
                    now = { now },
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

package com.example.spacenewslaunchertracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spacenewslaunchertracker.bedrock.BedrockExplainer
import com.example.spacenewslaunchertracker.bedrock.ExplainResult
import com.example.spacenewslaunchertracker.data.AppError
import com.example.spacenewslaunchertracker.data.BedrockSettings
import com.example.spacenewslaunchertracker.data.DisplaySettings
import com.example.spacenewslaunchertracker.data.NewsArticle
import com.example.spacenewslaunchertracker.data.SettingsStore
import com.example.spacenewslaunchertracker.data.SpaceRepository
import com.example.spacenewslaunchertracker.data.UpcomingLaunch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Loading state for one API-backed section. */
data class SectionState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val error: AppError? = null,
) {
    val isEmptyResult: Boolean get() = items.isEmpty() && !isLoading && error == null
}

/** Per-mission state of the "Explain in plain English" action. */
sealed interface ExplanationState {
    data object Loading : ExplanationState

    data class Ready(val text: String) : ExplanationState

    data class Failed(val message: String) : ExplanationState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpaceRepository(application.cacheDir)
    private val settingsStore = SettingsStore(application)
    private val explainer = BedrockExplainer()

    private val _display = MutableStateFlow(settingsStore.loadDisplay())
    val display: StateFlow<DisplaySettings> = _display.asStateFlow()

    private val _bedrock = MutableStateFlow(settingsStore.loadBedrock())
    val bedrock: StateFlow<BedrockSettings> = _bedrock.asStateFlow()

    private val _launches = MutableStateFlow(SectionState<UpcomingLaunch>())
    val launches: StateFlow<SectionState<UpcomingLaunch>> = _launches.asStateFlow()

    private val _news = MutableStateFlow(SectionState<NewsArticle>())
    val news: StateFlow<SectionState<NewsArticle>> = _news.asStateFlow()

    /**
     * Plain-English summaries keyed by launch id. Successful entries are never evicted, so a
     * mission costs at most one model call for the lifetime of the process.
     */
    private val _explanations = MutableStateFlow<Map<String, ExplanationState>>(emptyMap())
    val explanations: StateFlow<Map<String, ExplanationState>> = _explanations.asStateFlow()

    init {
        loadLaunches()
        loadNews()
    }

    fun loadLaunches() {
        val limit = _display.value.launchCount
        _launches.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.upcomingLaunches(limit) }
                .onSuccess { result -> _launches.value = SectionState(items = result) }
                .onFailure { cause ->
                    _launches.update { it.copy(isLoading = false, error = AppError.from(cause)) }
                }
        }
    }

    fun loadNews() {
        val limit = _display.value.newsCount
        _news.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.recentNews(limit) }
                .onSuccess { result -> _news.value = SectionState(items = result) }
                .onFailure { cause ->
                    _news.update { it.copy(isLoading = false, error = AppError.from(cause)) }
                }
        }
    }

    fun setLaunchCount(count: Int) {
        updateDisplay { it.copy(launchCount = count) }
        // Served from the cached batch; a slider change never sends a request.
        loadLaunches()
    }

    fun setNewsCount(count: Int) {
        updateDisplay { it.copy(newsCount = count) }
        loadNews()
    }

    fun setLiveCountdown(enabled: Boolean) = updateDisplay { it.copy(liveCountdown = enabled) }

    fun updateBedrockSettings(settings: BedrockSettings) {
        _bedrock.value = settings
        settingsStore.saveBedrock(settings)
    }

    /** "Refresh now": drop every cache and re-fetch both endpoints. */
    fun refreshEverything() {
        repository.clearCaches()
        loadLaunches()
        loadNews()
    }

    fun explain(launch: UpcomingLaunch) {
        // Already answered, or answering — never pay for the same mission twice.
        val existing = _explanations.value[launch.id]
        if (existing is ExplanationState.Ready || existing is ExplanationState.Loading) return

        _explanations.update { it + (launch.id to ExplanationState.Loading) }
        viewModelScope.launch {
            val result = explainer.explain(
                settings = _bedrock.value,
                missionName = launch.missionName ?: launch.name,
                description = launch.missionDescription.orEmpty(),
            )
            _explanations.update {
                it + (
                    launch.id to when (result) {
                        is ExplainResult.Success -> ExplanationState.Ready(result.text)
                        is ExplainResult.Failure -> ExplanationState.Failed(result.message)
                    }
                    )
            }
        }
    }

    /** Lets a failed explanation be retried; successful ones stay cached. */
    fun retryExplain(launch: UpcomingLaunch) {
        _explanations.update { it - launch.id }
        explain(launch)
    }

    private fun updateDisplay(transform: (DisplaySettings) -> DisplaySettings) {
        val updated = transform(_display.value)
        _display.value = updated
        settingsStore.saveDisplay(updated)
    }
}

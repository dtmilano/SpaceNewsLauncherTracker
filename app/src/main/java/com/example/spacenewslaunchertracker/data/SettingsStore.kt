package com.example.spacenewslaunchertracker.data

import android.content.Context
import com.example.spacenewslaunchertracker.BuildConfig

/** How much of each feed to show, and whether the countdowns tick. */
data class DisplaySettings(
    val launchCount: Int = DEFAULT_LAUNCHES,
    val newsCount: Int = DEFAULT_NEWS,
    val liveCountdown: Boolean = true,
) {
    companion object {
        const val MIN_ITEMS = 1
        const val MAX_ITEMS = 30
        const val DEFAULT_LAUNCHES = 6
        const val DEFAULT_NEWS = 10
    }
}

/**
 * Credentials and model for the "Explain in plain English" action, editable in Settings.
 *
 * Defaults come from `BuildConfig`, which the Gradle script fills in from
 * `local.properties` or the ambient AWS environment variables — so a developer who already
 * has credentials in their shell gets a working build with nothing to type, and everyone
 * else can paste them into Settings at runtime.
 */
data class BedrockSettings(
    val region: String = BuildConfig.BEDROCK_REGION,
    val modelId: String = BuildConfig.BEDROCK_MODEL_ID,
    val accessKeyId: String = BuildConfig.AWS_ACCESS_KEY_ID,
    val secretAccessKey: String = BuildConfig.AWS_SECRET_ACCESS_KEY,
    val sessionToken: String = BuildConfig.AWS_SESSION_TOKEN,
) {
    /** Enough to attempt a call. Whether AWS accepts them is a separate question. */
    val isComplete: Boolean
        get() = region.isNotBlank() &&
            modelId.isNotBlank() &&
            accessKeyId.isNotBlank() &&
            secretAccessKey.isNotBlank()

    /** Names the fields a user still has to fill in, for a precise error message. */
    val missingFields: List<String>
        get() = buildList {
            if (region.isBlank()) add("Region")
            if (modelId.isBlank()) add("Model ID")
            if (accessKeyId.isBlank()) add("Access key ID")
            if (secretAccessKey.isBlank()) add("Secret access key")
        }
}

/** SharedPreferences-backed settings; a handful of scalars needs nothing heavier. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun loadDisplay(): DisplaySettings = DisplaySettings(
        launchCount = prefs.getInt(KEY_LAUNCHES, DisplaySettings.DEFAULT_LAUNCHES),
        newsCount = prefs.getInt(KEY_NEWS, DisplaySettings.DEFAULT_NEWS),
        liveCountdown = prefs.getBoolean(KEY_LIVE, true),
    )

    fun saveDisplay(settings: DisplaySettings) {
        prefs.edit()
            .putInt(KEY_LAUNCHES, settings.launchCount)
            .putInt(KEY_NEWS, settings.newsCount)
            .putBoolean(KEY_LIVE, settings.liveCountdown)
            .apply()
    }

    fun loadBedrock(): BedrockSettings {
        val defaults = BedrockSettings()
        return BedrockSettings(
            region = prefs.getString(KEY_REGION, null) ?: defaults.region,
            modelId = prefs.getString(KEY_MODEL_ID, null) ?: defaults.modelId,
            accessKeyId = prefs.getString(KEY_ACCESS_KEY, null) ?: defaults.accessKeyId,
            secretAccessKey = prefs.getString(KEY_SECRET_KEY, null) ?: defaults.secretAccessKey,
            sessionToken = prefs.getString(KEY_SESSION_TOKEN, null) ?: defaults.sessionToken,
        )
    }

    fun saveBedrock(settings: BedrockSettings) {
        prefs.edit()
            .putString(KEY_REGION, settings.region)
            .putString(KEY_MODEL_ID, settings.modelId)
            .putString(KEY_ACCESS_KEY, settings.accessKeyId)
            .putString(KEY_SECRET_KEY, settings.secretAccessKey)
            .putString(KEY_SESSION_TOKEN, settings.sessionToken)
            .apply()
    }

    private companion object {
        const val KEY_LAUNCHES = "launch_count"
        const val KEY_NEWS = "news_count"
        const val KEY_LIVE = "live_countdown"
        const val KEY_REGION = "bedrock_region"
        const val KEY_MODEL_ID = "bedrock_model_id"
        const val KEY_ACCESS_KEY = "bedrock_access_key_id"
        const val KEY_SECRET_KEY = "bedrock_secret_access_key"
        const val KEY_SESSION_TOKEN = "bedrock_session_token"
    }
}

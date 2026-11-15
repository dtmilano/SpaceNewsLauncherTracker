package com.example.spacenewslaunchertracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.spacenewslaunchertracker.data.BedrockSettings
import com.example.spacenewslaunchertracker.data.DisplaySettings
import kotlin.math.roundToInt

@Composable
fun SettingsTab(
    display: DisplaySettings,
    bedrock: BedrockSettings,
    onLaunchCountChange: (Int) -> Unit,
    onNewsCountChange: (Int) -> Unit,
    onLiveCountdownChange: (Boolean) -> Unit,
    onBedrockChange: (BedrockSettings) -> Unit,
    onRefreshNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsCard("How much to show") {
            CountSlider(
                label = "Upcoming launches",
                value = display.launchCount,
                onValueChange = onLaunchCountChange,
            )
            Spacer(Modifier.height(12.dp))
            CountSlider(
                label = "News articles",
                value = display.newsCount,
                onValueChange = onNewsCountChange,
            )
        }

        SettingsCard("Live countdown") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Tick the launch clocks", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Refreshes the timers every second using already-cached API data. " +
                            "No extra API requests are made.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(0.dp))
                Switch(checked = display.liveCountdown, onCheckedChange = onLiveCountdownChange)
            }
        }

        SettingsCard("Cached data") {
            Text(
                "Responses are cached for 15 minutes so the app stays inside Launch Library " +
                    "2's ~15 anonymous requests per hour. Refreshing clears both caches and " +
                    "re-fetches immediately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRefreshNow) { Text("↻  Refresh now") }
        }

        BedrockCard(bedrock, onBedrockChange)

        SettingsCard("Data sources") {
            Text(
                "Upcoming launches — Launch Library 2 (The Space Devs)\n" +
                    "Space news — Spaceflight News API v4\n\n" +
                    "Both are free and need no API key.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BedrockCard(
    settings: BedrockSettings,
    onChange: (BedrockSettings) -> Unit,
) {
    // Edits are held locally and committed on Save, so a half-typed secret key never becomes
    // the value a request is signed with.
    var draft by remember(settings) { mutableStateOf(settings) }
    var revealSecrets by remember { mutableStateOf(false) }
    val isDirty = draft != settings

    SettingsCard("Amazon Bedrock") {
        Text(
            "Powers the \"Explain in plain English\" button on each launch card. Without " +
                "credentials that button reports an error instead of a summary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (settings.isComplete) {
                "✅  Configured"
            } else {
                "⚠️  Missing: ${settings.missingFields.joinToString(", ")}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (settings.isComplete) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.height(12.dp))

        SettingsField(
            label = "Region",
            value = draft.region,
            onValueChange = { draft = draft.copy(region = it.trim()) },
        )
        SettingsField(
            label = "Model ID",
            value = draft.modelId,
            onValueChange = { draft = draft.copy(modelId = it.trim()) },
        )
        SettingsField(
            label = "Access key ID",
            value = draft.accessKeyId,
            onValueChange = { draft = draft.copy(accessKeyId = it.trim()) },
        )
        SettingsField(
            label = "Secret access key",
            value = draft.secretAccessKey,
            onValueChange = { draft = draft.copy(secretAccessKey = it.trim()) },
            isSecret = !revealSecrets,
        )
        SettingsField(
            label = "Session token (temporary credentials only)",
            value = draft.sessionToken,
            onValueChange = { draft = draft.copy(sessionToken = it.trim()) },
            isSecret = !revealSecrets,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { revealSecrets = !revealSecrets }) {
                Text(if (revealSecrets) "Hide secrets" else "Show secrets")
            }
            Spacer(Modifier.weight(1f))
            if (isDirty) {
                TextButton(onClick = { draft = settings }) { Text("Discard") }
            }
            Button(onClick = { onChange(draft) }, enabled = isDirty) { Text("Save") }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun CountSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        // Continuous track, integer values: 29 tick marks would be visual noise at this width.
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = DisplaySettings.MIN_ITEMS.toFloat()..DisplaySettings.MAX_ITEMS.toFloat(),
        )
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isSecret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        singleLine = true,
        visualTransformation = if (isSecret) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isSecret) KeyboardType.Password else KeyboardType.Text,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

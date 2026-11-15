package com.example.spacenewslaunchertracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.example.spacenewslaunchertracker.data.UpcomingLaunch
import java.time.Instant

@Composable
fun LaunchesTab(
    state: SectionState<UpcomingLaunch>,
    now: Instant,
    explanations: Map<String, ExplanationState>,
    isBedrockConfigured: Boolean,
    onRetry: () -> Unit,
    onExplain: (UpcomingLaunch) -> Unit,
    onRetryExplain: (UpcomingLaunch) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.error != null && state.items.isEmpty() -> Box(
            modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            ErrorCard(state.error, onRetry)
        }

        state.isLoading && state.items.isEmpty() ->
            LoadingPane("Contacting Launch Library 2…", modifier)

        state.isEmptyResult -> EmptyPane("No upcoming launches were returned.", modifier)

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // A stale-data banner keeps a failed refresh visible without hiding the cards.
            state.error?.let { error ->
                item { ErrorCard(error, onRetry) }
            }
            items(state.items, key = { it.id }) { launch ->
                LaunchCard(
                    launch = launch,
                    now = now,
                    explanation = explanations[launch.id],
                    isBedrockConfigured = isBedrockConfigured,
                    onExplain = { onExplain(launch) },
                    onRetryExplain = { onRetryExplain(launch) },
                )
            }
        }
    }
}

@Composable
private fun LaunchCard(
    launch: UpcomingLaunch,
    now: Instant,
    explanation: ExplanationState?,
    isBedrockConfigured: Boolean,
    onExplain: () -> Unit,
    onRetryExplain: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Two columns: imagery on the left, mission details on the right.
            Row(Modifier.fillMaxWidth()) {
                // Square, so the row's height is set by the details rather than leaving a
                // tall column of dead space beside a short countdown.
                LaunchImage(
                    imageUrl = launch.imageUrl,
                    modifier = Modifier.width(108.dp).aspectRatio(1f),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = launch.missionName ?: launch.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    CountdownRow(launch = launch, now = now)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            FactRow("Launch date", formatLaunchDate(launch.liftoff, launch.isDateFirm))
            FactRow("Provider", launch.provider ?: "Unknown")
            FactRow("Status", launch.status ?: "Unknown", highlight = launch.statusIsGo)
            FactRow("Rocket", launch.rocket ?: "Unknown")
            FactRow(
                label = "Pad",
                value = listOfNotNull(launch.padName, launch.padLocation)
                    .joinToString(" · ")
                    .ifBlank { "Unknown" },
            )

            launch.coordinates?.let { (latitude, longitude) ->
                Spacer(Modifier.height(10.dp))
                MapsButton(
                    latitude = latitude,
                    longitude = longitude,
                    label = listOfNotNull(launch.padName, launch.padLocation)
                        .joinToString(", ")
                        .ifBlank { launch.name },
                )
            }

            Spacer(Modifier.height(12.dp))
            ExplainSection(
                launch = launch,
                explanation = explanation,
                isBedrockConfigured = isBedrockConfigured,
                onExplain = onExplain,
                onRetryExplain = onRetryExplain,
            )
        }
    }
}

/**
 * Launch imagery, falling back to a rocket emoji when the API has no picture — or when the
 * picture it does have fails to load.
 */
@Composable
private fun LaunchImage(imageUrl: String?, modifier: Modifier = Modifier) {
    var failed by remember(imageUrl) { mutableStateOf(false) }

    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank() || failed) {
            Text("🚀", fontSize = 40.sp)
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Launch vehicle imagery",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) failed = true
                },
            )
        }
    }
}

/** The launch clock: `T- dd : hh : mm : ss` under the mission name. */
@Composable
private fun CountdownRow(launch: UpcomingLaunch, now: Instant) {
    val clock = launchClock(launch.liftoff, now, launch.isDateFirm)
    val accent = when {
        clock.isUnknown -> MaterialTheme.colorScheme.onSurfaceVariant
        clock.isPast -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column {
        Text(
            text = clockLabel(clock, accent),
            maxLines = 1,
            // A clock split across two lines is unreadable, so this line never wraps. The
            // compact `T-02d:18h:48m:02s` form is sized to fit the narrow details column.
            softWrap = false,
            overflow = TextOverflow.Visible,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = clock.caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `T-02d:18h:48m:02s` — bold monospace digits with smaller, dimmer unit letters. */
private fun clockLabel(clock: LaunchClock, accent: Color): AnnotatedString = buildAnnotatedString {
    val digitStyle = SpanStyle(
        color = accent,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    )
    val unitStyle = SpanStyle(
        color = accent.copy(alpha = 0.7f),
        fontFamily = FontFamily.Monospace,
        fontSize = 0.62.em,
    )
    val separatorStyle = SpanStyle(
        color = accent.copy(alpha = 0.45f),
        fontFamily = FontFamily.Monospace,
    )

    withStyle(digitStyle) { append(clock.sign) }

    val segments = if (clock.isUnknown) {
        listOf("--" to "d", "--" to "h", "--" to "m", "--" to "s")
    } else {
        listOf(
            clock.days.toString().padStart(2, '0') to "d",
            clock.hours.toString().padStart(2, '0') to "h",
            clock.minutes.toString().padStart(2, '0') to "m",
            clock.seconds.toString().padStart(2, '0') to "s",
        )
    }

    segments.forEachIndexed { index, (digits, unit) ->
        if (index > 0) withStyle(separatorStyle) { append(":") }
        withStyle(digitStyle) { append(digits) }
        withStyle(unitStyle) { append(unit) }
    }
}

@Composable
private fun FactRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        // Proportional rather than a fixed dp width, so a large system font scale widens the
        // label column instead of letting "Launch date" run into its value.
        Text(
            text = label,
            modifier = Modifier.weight(0.27f).padding(end = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.73f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun MapsButton(latitude: Double, longitude: Double, label: String) {
    val context = LocalContext.current
    TextButton(
        onClick = { openInMaps(context, latitude, longitude, label) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text("📍  Open pad in Google Maps", style = MaterialTheme.typography.labelLarge)
    }
    Text(
        text = "%.4f, %.4f".format(latitude, longitude),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun ExplainSection(
    launch: UpcomingLaunch,
    explanation: ExplanationState?,
    isBedrockConfigured: Boolean,
    onExplain: () -> Unit,
    onRetryExplain: () -> Unit,
) {
    Column {
        // The button stays tappable even without credentials: pressing it surfaces a
        // concrete error telling the user what Settings is missing, which beats a
        // disabled control that never explains itself.
        if (explanation == null) {
            FilledTonalButton(onClick = onExplain) {
                Text("✨  Explain in plain English")
            }
            if (!isBedrockConfigured) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Needs AWS credentials in Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(visible = explanation != null) {
            when (explanation) {
                is ExplanationState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Asking Amazon Bedrock…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is ExplanationState.Ready -> Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "IN PLAIN ENGLISH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            explanation.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                is ExplanationState.Failed -> Column(Modifier.padding(top = 4.dp)) {
                    Text(
                        explanation.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetryExplain) { Text("Try again") }
                }

                null -> Spacer(Modifier.fillMaxHeight(0f))
            }
        }
    }
}

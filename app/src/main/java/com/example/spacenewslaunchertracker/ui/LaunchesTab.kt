package com.example.spacenewslaunchertracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacenewslaunchertracker.data.UpcomingLaunch
import com.example.spacenewslaunchertracker.ui.components.Countdown
import com.example.spacenewslaunchertracker.ui.components.CountdownRing
import com.example.spacenewslaunchertracker.ui.components.LaunchClock
import com.example.spacenewslaunchertracker.ui.components.MissionArtwork
import com.example.spacenewslaunchertracker.ui.components.StatusChip
import com.example.spacenewslaunchertracker.ui.components.countdownBetween
import com.example.spacenewslaunchertracker.ui.components.ignitionGlow
import com.example.spacenewslaunchertracker.ui.components.statusOf
import com.example.spacenewslaunchertracker.ui.theme.LocalSpaceAccents
import java.time.Duration
import java.time.Instant

/**
 * Upcoming launches.
 *
 * [now] is a *function*, not a value. The device clock ticks every second while "Live
 * countdown" is on, and passing an `Instant` down would recompose the whole list on every
 * tick. With a lambda, only the composables that actually call it — the clocks, and the
 * `derivedStateOf` blocks that turn time into a phase — are invalidated, and a card itself
 * recomposes only when its launch crosses a threshold (into the final 24 hours, past
 * liftoff).
 */
@Composable
fun LaunchesTab(
    state: SectionState<UpcomingLaunch>,
    now: () -> Instant,
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
            ErrorPanel(state.error, isRetrying = state.isLoading, onRetry = onRetry)
        }

        state.isLoading && state.items.isEmpty() ->
            LoadingPane("Contacting Launch Library 2…", modifier)

        state.isEmptyResult -> EmptyPane("No upcoming launches were returned.", modifier)

        else -> {
            // The soonest launch with a firm time that has not yet lifted off. Derived, so
            // the list only recomposes when the answer changes, not on every clock tick.
            val next by remember(state.items, now) {
                derivedStateOf {
                    val instant = now()
                    state.items.firstOrNull { it.hasCountdown && it.liftoff!!.isAfter(instant) }
                }
            }

            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // A stale-data banner keeps a failed refresh visible without hiding the cards.
                state.error?.let { error ->
                    item(key = "error", contentType = "error") {
                        ErrorPanel(
                            error = error,
                            isRetrying = state.isLoading,
                            onRetry = onRetry,
                            fallbackNote = "Showing the launches loaded earlier.",
                        )
                    }
                }
                next?.let { launch ->
                    item(key = "next", contentType = "next") {
                        NextLaunchHero(launch = launch, now = now)
                    }
                }
                items(state.items, key = { it.id }, contentType = { "launch" }) { launch ->
                    LaunchCard(
                        launch = launch,
                        now = now,
                        // The hero already glows for the next launch; two adjacent halos
                        // for the same mission merge into one heavy band.
                        glowWhenImminent = launch.id != next?.id,
                        explanation = explanations[launch.id],
                        isBedrockConfigured = isBedrockConfigured,
                        onExplain = { onExplain(launch) },
                        onRetryExplain = { onRetryExplain(launch) },
                    )
                }
            }
        }
    }
}

/** Where a launch sits relative to the clock; the only time-derived fact a card needs. */
private enum class LaunchPhase { Unscheduled, Upcoming, Imminent, LiftedOff }

private fun phaseOf(launch: UpcomingLaunch, now: Instant): LaunchPhase {
    val liftoff = launch.liftoff
    return when {
        liftoff == null || !launch.isDateFirm -> LaunchPhase.Unscheduled
        !liftoff.isAfter(now) -> LaunchPhase.LiftedOff
        Duration.between(now, liftoff) <= ImminentWindow -> LaunchPhase.Imminent
        else -> LaunchPhase.Upcoming
    }
}

/** A launch inside this window glows and its countdown ring starts to fill. */
private val ImminentWindow: Duration = Duration.ofHours(24)

/** The countdown, honouring "to be announced" (a timestamp that is not yet firm). */
private fun UpcomingLaunch.countdownAt(now: Instant): Countdown =
    countdownBetween(liftoff?.takeIf { isDateFirm }, now)

/**
 * The next firm liftoff, as a ring that fills over its final 24 hours.
 *
 * Only this composable and its [LaunchClock]-style ring read [now] every second; the hero's
 * text around them is static.
 */
@Composable
private fun NextLaunchHero(launch: UpcomingLaunch, now: () -> Instant) {
    val accents = LocalSpaceAccents.current
    val phase by remember(launch, now) { derivedStateOf { phaseOf(launch, now()) } }

    GlassCard(
        modifier = Modifier.ignitionGlow(
            color = accents.clockLive,
            active = launch.statusIsGo && phase != LaunchPhase.LiftedOff,
            cornerRadius = 20.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroRing(launch, now)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "NEXT LIFTOFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.clockLive,
                )
                Text(
                    text = launch.missionName ?: launch.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusChip(
                    status = statusOf(launch.statusIsGo, phase == LaunchPhase.LiftedOff),
                    label = launch.status ?: "Status unknown",
                )
                Text(
                    text = formatLaunchDate(launch.liftoff, launch.isDateFirm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HeroRing(launch: UpcomingLaunch, now: () -> Instant) {
    val instant = now()
    val progress = launch.liftoff?.let { liftoff ->
        val remaining = Duration.between(instant, liftoff).seconds.toFloat()
        (1f - remaining / ImminentWindow.seconds).coerceIn(0f, 1f)
    } ?: 0f
    CountdownRing(
        progress = progress,
        countdown = launch.countdownAt(instant),
        diameter = 128.dp,
        caption = launch.rocket,
    )
}

@Composable
private fun LaunchCard(
    launch: UpcomingLaunch,
    now: () -> Instant,
    glowWhenImminent: Boolean,
    explanation: ExplanationState?,
    isBedrockConfigured: Boolean,
    onExplain: () -> Unit,
    onRetryExplain: () -> Unit,
) {
    val accents = LocalSpaceAccents.current
    // Recomposes this card only when the phase flips, never on an ordinary tick.
    val phase by remember(launch, now) { derivedStateOf { phaseOf(launch, now()) } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // A launch in its final 24 hours pulses. The glow animates in a Modifier.Node,
            // so the pulse never recomposes the card. Applied only when wanted: an
            // inactive ignitionGlow still draws a resting halo, which is right for the
            // hero but would outline every card in the list.
            .then(
                if (glowWhenImminent && phase == LaunchPhase.Imminent) {
                    Modifier.ignitionGlow(color = accents.clockLive, cornerRadius = 12.dp)
                } else {
                    Modifier
                },
            ),
        colors = CardDefaults.cardColors(
            // Slightly translucent so the starfield shows through at the edges.
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Two columns: imagery on the left, mission details on the right.
            Row(Modifier.fillMaxWidth()) {
                // Square, so the row's height is set by the details rather than leaving a
                // tall column of dead space beside a short countdown. A missing or failed
                // image falls back to procedural art seeded by the launch id, so the same
                // mission always gets the same planet.
                MissionArtwork(
                    seed = launch.id,
                    imageUrl = launch.imageUrl?.takeIf { it.isNotBlank() },
                    contentDescription = "Launch vehicle imagery",
                    modifier = Modifier
                        .width(108.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
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
                    CardClock(launch = launch, now = now)
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            FactRow("Launch", formatLaunchDate(launch.liftoff, launch.isDateFirm))
            FactRow("Provider", launch.provider ?: "Unknown")
            FactRow("Status") {
                StatusChip(
                    status = statusOf(launch.statusIsGo, phase == LaunchPhase.LiftedOff),
                    label = launch.status ?: "Unknown",
                )
            }
            FactRow("Rocket", launch.rocket ?: "Unknown", secondary = true)
            PadFact(launch)

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
 * The launch clock under the mission name: `T-02d:18h:48m:02s`, one line, never wrapping.
 * The one composable in the card that reads the clock every second.
 */
@Composable
private fun CardClock(launch: UpcomingLaunch, now: () -> Instant) {
    val countdown = launch.countdownAt(now())
    Column {
        LaunchClock(countdown)
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                // The clock line already says "TO BE ANNOUNCED"; the caption says why.
                countdown.isToBeAnnounced && launch.liftoff != null -> "DATE NOT YET FIRM"
                countdown.isToBeAnnounced -> "NO DATE PUBLISHED"
                countdown.hasLiftedOff -> "LIFTOFF HAS PASSED"
                else -> "UNTIL LIFTOFF"
            },
            // One step under labelSmall: a caption to the clock, not a fact of its own.
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A fact with a text value.
 *
 * The facts come in two weights: the headline facts (date, provider) in `bodyMedium`, and the
 * vehicle facts (status, rocket, pad) in the status chip's `labelMedium`, muted. [secondary]
 * selects the latter, so the block reads as two groups rather than alternating bright and
 * muted lines, and no third style is introduced.
 */
@Composable
private fun FactRow(label: String, value: String, secondary: Boolean = false) {
    FactRow(label) {
        Text(
            text = value,
            style = if (secondary) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (secondary) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun FactRow(label: String, value: @Composable () -> Unit) {
    // Label and value share a first baseline, so a multi-line value (the pad) keeps its
    // label on its first line instead of centring it against the whole block.
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        // Proportional rather than a fixed dp width, so a large system font scale widens the
        // label column instead of letting a label run into its value.
        Text(
            text = label,
            modifier = Modifier.weight(0.27f).alignByBaseline().padding(end = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(0.73f).alignByBaseline()) { value() }
    }
}

/**
 * The pad and the way to see it on a map, as one fact.
 *
 * The map link sits in the value column under the pad it opens, rather than as a separate
 * button below the facts, where it read as an unrelated action. The pad text uses the status
 * chip's style (`labelMedium`, muted) because it is always long; no new style is introduced.
 */
@Composable
private fun PadFact(launch: UpcomingLaunch) {
    val pad = listOfNotNull(launch.padName, launch.padLocation).joinToString(" · ")
    FactRow("Pad") {
        Column {
            Text(
                text = pad.ifBlank { "Unknown" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The coordinates are used, not shown: raw lat/long tells a reader nothing the
            // pad name and the map do not.
            launch.coordinates?.let { (latitude, longitude) ->
                MapsLink(
                    latitude = latitude,
                    longitude = longitude,
                    label = listOfNotNull(launch.padName, launch.padLocation)
                        .joinToString(", ")
                        .ifBlank { launch.name },
                )
            }
        }
    }
}

@Composable
private fun MapsLink(latitude: Double, longitude: Double, label: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            // Full 48dp touch target even though the link itself is a single short line.
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { openInMaps(context, latitude, longitude, label) }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Open in Google Maps",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
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
            // Full width: it is the card's one primary action, and the width leaves room to
            // say where the explanation comes from.
            FilledTonalButton(onClick = onExplain, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "✨  Explain in plain English with AI",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

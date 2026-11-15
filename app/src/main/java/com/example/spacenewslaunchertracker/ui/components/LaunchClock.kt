package com.example.spacenewslaunchertracker.ui.components

// Ported from the Space Composify catalog (com.example.spacecomposify.components).

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import com.example.spacenewslaunchertracker.ui.theme.LaunchClockStyle
import com.example.spacenewslaunchertracker.ui.theme.LocalSpaceAccents
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import kotlin.math.absoluteValue

/*
 * A launch clock: the one widget a launch tracker cannot do without, and a good lesson in
 * how often a Compose UI should actually change.
 *
 * The naive implementation recomposes 60 times a second off the frame clock. This one
 * recomposes at most once per second, because that is the only rate at which the rendered
 * text can differ — and it aligns its tick to the wall-clock second boundary so the digits
 * roll over when a watch would say they should, not up to a second late.
 */

/** A countdown broken into display fields. `@Immutable` so holders of it stay skippable. */
@Immutable
data class Countdown(
    /** No firm liftoff time has been published. */
    val isToBeAnnounced: Boolean,
    /** Liftoff is in the past, so the clock counts up as T+. */
    val hasLiftedOff: Boolean,
    val days: Long,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
) {
    companion object {
        val ToBeAnnounced = Countdown(true, false, 0, 0, 0, 0)
    }
}

/**
 * Pure, testable countdown arithmetic — no Compose, no `Instant.now()` inside.
 *
 * Keeping the clock's logic in a function of `(target, now)` is what makes the behaviour at
 * the interesting moments (the roll from T-00:00:01 to T+00:00:00, a null target) checkable
 * in a JVM unit test with no device and no waiting. See `LaunchClockTest`.
 */
fun countdownBetween(target: Instant?, now: Instant): Countdown {
    if (target == null) return Countdown.ToBeAnnounced
    val delta = Duration.between(now, target)
    val hasLiftedOff = delta.isNegative
    // Truncate towards zero on both sides of liftoff, so T-00h:00m:00s is the instant
    // before ignition and T+00h:00m:00s the instant after, rather than one of them
    // spending an extra second on screen because `Duration.seconds` floors.
    val total = (delta.toMillis() / 1_000L).absoluteValue
    return Countdown(
        isToBeAnnounced = false,
        hasLiftedOff = hasLiftedOff,
        days = total / 86_400,
        hours = ((total % 86_400) / 3_600).toInt(),
        minutes = ((total % 3_600) / 60).toInt(),
        seconds = (total % 60).toInt(),
    )
}

/**
 * Observes a live countdown to [target], recomposing readers once per second.
 *
 * `produceState` is the right primitive here rather than `LaunchedEffect` + a `MutableState`
 * because the state and the coroutine that feeds it are created and cancelled together, and
 * keyed on [target]: point the clock at a different launch and the old loop is torn down.
 *
 * The loop sleeps only as long as remains until the next second boundary, so the clock does
 * not drift and does not wake up more often than it has something new to say.
 */
@Composable
fun rememberCountdown(target: Instant?): State<Countdown> =
    produceState(initialValue = countdownBetween(target, Instant.now()), target) {
        if (target == null) {
            value = Countdown.ToBeAnnounced
            return@produceState
        }
        while (true) {
            val now = Instant.now()
            value = countdownBetween(target, now)
            // Align to the wall-clock second so digits roll over on the beat.
            delay(1_000L - now.toEpochMilli() % 1_000L)
        }
    }

/**
 * Formats a countdown as a single compact line: `T-02d:18h:41m:05s`.
 *
 * Digits are bold and full-contrast, unit letters are smaller and dimmer — one
 * [AnnotatedString] rather than a `Row` of eight `Text`s, which means one text layout, one
 * draw, and no chance of the pieces wrapping independently. The day field is dropped when
 * it is zero so the line stays short enough for a narrow column.
 */
fun Countdown.toAnnotatedString(unitColor: Color): AnnotatedString = buildAnnotatedString {
    if (isToBeAnnounced) {
        withUnit(unitColor) { append("TO BE ANNOUNCED") }
        return@buildAnnotatedString
    }
    withUnit(unitColor) { append(if (hasLiftedOff) "T+" else "T-") }
    if (days > 0) {
        append(days.toString().padStart(2, '0'))
        withUnit(unitColor) { append("d:") }
    }
    append(hours.toString().padStart(2, '0'))
    withUnit(unitColor) { append("h:") }
    append(minutes.toString().padStart(2, '0'))
    withUnit(unitColor) { append("m:") }
    append(seconds.toString().padStart(2, '0'))
    withUnit(unitColor) { append("s") }
}

private inline fun AnnotatedString.Builder.withUnit(
    color: Color,
    block: () -> Unit,
) {
    pushStyle(SpanStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium))
    block()
    pop()
}

/** Spoken form of the countdown, for the screen reader. */
fun Countdown.toSpokenString(): String = when {
    isToBeAnnounced -> "Launch time to be announced"
    else -> buildString {
        append(if (hasLiftedOff) "Lifted off " else "Launching in ")
        if (days > 0) append("$days days, ")
        if (days > 0 || hours > 0) append("$hours hours, ")
        append("$minutes minutes, $seconds seconds")
        if (hasLiftedOff) append(" ago")
    }
}

/**
 * The launch clock itself: a single non-wrapping line of monospace digits.
 *
 * Accessibility is handled deliberately rather than inherited. The visual string
 * `T-02d:18h:41m:05s` is unreadable aloud, so [clearAndSetSemantics] replaces the node's
 * text with a spoken sentence, and [LiveRegionMode.Polite] asks the screen reader to
 * announce changes without interrupting — a clock that barged in every second with
 * `Assertive` would make the screen unusable.
 */
@Composable
fun LaunchClock(
    target: Instant?,
    modifier: Modifier = Modifier,
) {
    val countdown by rememberCountdown(target)
    LaunchClock(countdown, modifier)
}

/** Stateless overload: pass a [Countdown] you already have (previews, tests, scrubbing). */
@Composable
fun LaunchClock(
    countdown: Countdown,
    modifier: Modifier = Modifier,
) {
    val accents = LocalSpaceAccents.current
    val digitColor = when {
        countdown.isToBeAnnounced -> MaterialTheme.colorScheme.onSurfaceVariant
        countdown.hasLiftedOff -> accents.clockPassed
        else -> accents.clockLive
    }
    val unitColor = digitColor.copy(alpha = 0.65f)
    val spoken = countdown.toSpokenString()

    Text(
        text = countdown.toAnnotatedString(unitColor),
        style = LaunchClockStyle,
        color = digitColor,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
    )
}

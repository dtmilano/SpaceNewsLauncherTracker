package com.example.spacenewslaunchertracker.ui

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacenewslaunchertracker.data.AppError
import com.example.spacenewslaunchertracker.data.ErrorKind
import com.example.spacenewslaunchertracker.ui.theme.LocalSpaceAccents
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Ported from the Space Composify catalog (com.example.spacecomposify.ui.ErrorPanel).

/*
 * An error panel that discloses in three steps.
 *
 * The problem with the usual one-line banner is that it has to serve two people at once:
 * someone who just wants to know whether the launch times on screen are real, and someone
 * trying to work out *why* they are not. Serving both in one sentence produces either
 * "Something went wrong" — useless — or a sentence with an HTTP status code in it, which is
 * noise to the first person and still not enough for the second.
 *
 * So:
 *
 *   level 1  a plain sentence, plus a retry button                   (always visible)
 *   level 2  why it happened, and the one thing you could do          (tap "What happened?")
 *   level 3  status, endpoint, exception, timestamp — copyable        (tap "Technical details")
 *
 * Each level is opt-in, so the screen stays calm by default and nothing is hidden from
 * someone who wants it. The retry is at level 1 because it is the action most likely to
 * help, and it is omitted entirely when the failure cannot be fixed by retrying — offering
 * a button that is certain to fail is worse than offering none.
 */

@Composable
fun ErrorPanel(
    error: AppError,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the screen shows instead, e.g. "Showing the launches loaded earlier." */
    fallbackNote: String? = null,
) {
    var showDetail by remember(error) { mutableStateOf(false) }
    var showTechnical by remember(error) { mutableStateOf(false) }

    val accents = LocalSpaceAccents.current
    val tint = when (error.kind) {
        ErrorKind.RateLimited, ErrorKind.Timeout -> MaterialTheme.colorScheme.tertiary
        ErrorKind.BadData, ErrorKind.NotFound, ErrorKind.Rejected ->
            MaterialTheme.colorScheme.error

        else -> accents.clockPassed
    }

    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        // One child, and no `spacedBy` anywhere on the path to the collapsible sections.
        // `Arrangement.spacedBy` puts a gap on *both* sides of every child, including a
        // collapsed `AnimatedVisibility` that is zero pixels tall — which shows up as a
        // stubborn band of dead space under a closed panel. Spacing therefore lives inside
        // the content being revealed.
        Column(Modifier.fillMaxWidth()) {
        // Level 1 — what happened, in one sentence.
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = error.kind.icon(),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = error.summary,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Politely announced: a screen-reader user should learn that the data
                    // changed under them, without having whatever they were reading
                    // interrupted mid-sentence.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                fallbackNote?.let { note ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (error.canRetry) {
                TextButton(onClick = onRetry, enabled = !isRetrying) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Trying…")
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Try again")
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            DisclosureToggle(
                label = if (showDetail) "Hide" else "What happened?",
                expanded = showDetail,
                onToggle = { showDetail = !showDetail },
            )
        }

        // Level 2 — the cause, and the one thing worth doing about it.
        AnimatedVisibility(
            visible = showDetail,
            enter = fadeIn(tween(160)) + expandVertically(tween(220)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.height(0.dp))
                Text(
                    text = error.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                error.action?.let { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "→",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tint,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = action,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                DisclosureToggle(
                    label = if (showTechnical) {
                        "Hide technical details"
                    } else {
                        "Technical details"
                    },
                    expanded = showTechnical,
                    onToggle = { showTechnical = !showTechnical },
                )

                // Level 3 — the raw facts, for a bug report.
                AnimatedVisibility(
                    visible = showTechnical,
                    enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                ) {
                    TechnicalBlock(error.technical)
                }
            }
        }
        }
    }
}

/**
 * The affordance that opens each level.
 *
 * It is a single semantics node with [Role.Button] and a `stateDescription`, so a screen
 * reader says "What happened?, button, collapsed" rather than leaving the user to infer
 * that a chevron is interactive and that there is something behind it.
 */
@Composable
private fun DisclosureToggle(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "disclosure-chevron",
    )
    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                // Rotating in a graphicsLayer lambda keeps the animation in the draw
                // phase; nothing here recomposes per frame.
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/** Monospace diagnostics with a copy action, scrollable so a long URL is not truncated. */
@Composable
private fun TechnicalBlock(technical: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember(technical) { mutableStateOf(false) }

    // Confirmation, not a permanent state change: the label returns to "Copy" so the
    // control still reads as something you can use again.
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_800)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                text = technical,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = false,
            )
        }
        TextButton(
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        androidx.compose.ui.platform.ClipEntry(
                            ClipData.newPlainText("Space tracker diagnostics", technical),
                        ),
                    )
                    copied = true
                }
            },
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (copied) "Copied" else "Copy")
        }
    }
}

private fun ErrorKind.icon(): ImageVector = when (this) {
    ErrorKind.Offline -> Icons.Rounded.CloudOff
    ErrorKind.Timeout -> Icons.Rounded.Timer
    ErrorKind.RateLimited -> Icons.Rounded.HourglassTop
    ErrorKind.ServerError -> Icons.Rounded.Report
    ErrorKind.NotFound, ErrorKind.Rejected, ErrorKind.BadData -> Icons.Rounded.ErrorOutline
    ErrorKind.Unknown -> Icons.Rounded.ErrorOutline
}

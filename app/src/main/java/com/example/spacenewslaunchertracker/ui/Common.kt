package com.example.spacenewslaunchertracker.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.spacenewslaunchertracker.data.AppError

/** Full-width error card. Rate-limit failures get their own accent so they read as transient. */
@Composable
fun ErrorCard(error: AppError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (error.isRateLimit) {
                colors.tertiaryContainer
            } else {
                colors.errorContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (error.isRateLimit) "⏳  Rate limit reached" else "⚠️  Couldn't load",
                style = MaterialTheme.typography.titleMedium,
                color = if (error.isRateLimit) {
                    colors.onTertiaryContainer
                } else {
                    colors.onErrorContainer
                },
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (error.isRateLimit) {
                    colors.onTertiaryContainer
                } else {
                    colors.onErrorContainer
                },
            )
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
fun LoadingPane(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyPane(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

/**
 * Opens the pad coordinates in Google Maps.
 *
 * The `geo:` URI is aimed at the Google Maps app first; if it isn't installed (a bare
 * emulator image, for instance) we fall back to any other handler, and finally to the
 * browser, so the button never dead-ends.
 */
fun openInMaps(context: Context, latitude: Double, longitude: Double, label: String?) {
    val query = Uri.encode(label?.takeIf { it.isNotBlank() } ?: "Launch pad")
    val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($query)")

    val googleMaps = Intent(Intent.ACTION_VIEW, geoUri).setPackage("com.google.android.apps.maps")
    val anyMapApp = Intent(Intent.ACTION_VIEW, geoUri)
    val browser = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"),
    )

    for (intent in listOf(googleMaps, anyMapApp, browser)) {
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next fallback.
        }
    }
    Toast.makeText(context, "No app available to show the map", Toast.LENGTH_SHORT).show()
}

/** Opens an article in the user's browser. */
fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser available to open this article", Toast.LENGTH_SHORT)
            .show()
    }
}

package com.example.spacenewslaunchertracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spacenewslaunchertracker.data.NewsArticle
import com.example.spacenewslaunchertracker.ui.components.MissionArtwork

@Composable
fun NewsTab(
    state: SectionState<NewsArticle>,
    onRetry: () -> Unit,
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
            LoadingPane("Fetching the latest headlines…", modifier)

        state.isEmptyResult -> EmptyPane("No articles were returned.", modifier)

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { error ->
                item(key = "error", contentType = "error") {
                    ErrorPanel(
                        error = error,
                        isRetrying = state.isLoading,
                        onRetry = onRetry,
                        fallbackNote = "Showing the headlines loaded earlier.",
                    )
                }
            }
            items(state.items, key = { it.id }, contentType = { "article" }) { article ->
                ArticleCard(article)
            }
        }
    }
}

@Composable
private fun ArticleCard(article: NewsArticle) {
    val context = LocalContext.current
    Card(
        onClick = { openInBrowser(context, article.url) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            // Slightly translucent so the starfield shows through at the edges.
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        ),
    ) {
        Row(Modifier.padding(14.dp)) {
            // No image, or one that fails to load, gets procedural art seeded by the
            // article id — deterministic, so a thumbnail never changes between visits.
            MissionArtwork(
                seed = article.id,
                imageUrl = article.imageUrl?.takeIf { it.isNotBlank() },
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = listOfNotNull(
                        article.newsSite,
                        formatPublished(article.publishedAt),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                article.summary?.let { summary ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Read full article ↗",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

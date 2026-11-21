package com.mlw.serieustracker.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mlw.serieustracker.R
import com.mlw.serieustracker.data.Episode
import com.mlw.serieustracker.data.Source
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun Poster(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url.ifEmpty { null },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(width = 46.dp, height = 69.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** A card with a poster on the left, a title, optional extra content below it and optional trailing content. */
@Composable
fun ShowRow(
    poster: String,
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit = {},
) {
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Poster(poster)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                body()
            }
            trailing()
        }
    }
}

@Composable
fun EmptyState(@StringRes text: Int) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** "S1E5 · Title" for series, "Episode 5 · Title" for anime (which has no seasons). */
@Composable
fun episodeLabel(source: Source, episode: Episode): String {
    val base = if (source == Source.ANILIST) {
        stringResource(R.string.episode_label_flat, episode.number)
    } else {
        stringResource(R.string.episode_label_season, episode.season, episode.number)
    }
    return if (episode.title.isBlank()) base else "$base · ${episode.title}"
}

@Composable
fun formatDate(iso: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) }.getOrDefault(iso)
}

@Composable
fun daysUntil(iso: String): String {
    val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(iso)).toInt() }.getOrDefault(0)
    return when {
        days <= 0 -> stringResource(R.string.today)
        days == 1 -> stringResource(R.string.tomorrow)
        else -> pluralStringResource(R.plurals.in_days, days, days)
    }
}

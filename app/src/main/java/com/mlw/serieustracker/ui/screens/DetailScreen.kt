package com.mlw.serieustracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlw.serieustracker.R
import com.mlw.serieustracker.data.Source
import com.mlw.serieustracker.data.todayIso
import com.mlw.serieustracker.ui.AppViewModel
import com.mlw.serieustracker.ui.components.Poster
import com.mlw.serieustracker.ui.components.SecondaryText
import com.mlw.serieustracker.ui.components.episodeLabel
import com.mlw.serieustracker.ui.components.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, key: String, onBack: () -> Unit) {
    val shows by vm.shows.collectAsStateWithLifecycle()
    val show = shows.firstOrNull { it.key == key }
    if (show == null) {
        // The show was removed (or the key is stale): leave this screen.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val today = todayIso()
    val uriHandler = LocalUriHandler.current
    val seasons = show.episodes.groupBy { it.season }.toSortedMap()
    var expanded by rememberSaveable { mutableStateOf(if (show.source == Source.ANILIST) listOf(1) else emptyList<Int>()) }
    var confirmUnfollow by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(show.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshShow(key) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Poster(show.poster)
                    Column {
                        Text(show.name, style = MaterialTheme.typography.titleLarge)
                        SecondaryText(stringResource(R.string.progress_seen, show.seenCount(), show.airedCount(today)))
                        if (show.dropped) SecondaryText(stringResource(R.string.status_dropped))
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.setDropped(key, !show.dropped) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(if (show.dropped) R.string.action_resume else R.string.action_dropped))
                        }
                        OutlinedButton(
                            onClick = { confirmUnfollow = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.action_unfollow))
                        }
                    }
                    TextButton(onClick = { uriHandler.openUri(show.sourceUrl) }) {
                        Text(stringResource(if (show.source == Source.ANILIST) R.string.link_anilist else R.string.link_plex))
                    }
                }
            }
            if (show.info == null) {
                item { SecondaryText(stringResource(R.string.detail_no_data), Modifier.padding(horizontal = 16.dp)) }
            }
            seasons.forEach { (season, episodes) ->
                val isOpen = season in expanded
                item {
                    var menuOpen by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = if (isOpen) expanded - season else expanded + season }
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val title = if (show.source == Source.ANILIST) {
                            stringResource(R.string.season_flat)
                        } else {
                            stringResource(R.string.season_title, season)
                        }
                        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        SecondaryText("${episodes.count { it.seenKey in show.seen }}/${episodes.size}")
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_season_menu))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_mark_all_seen)) },
                                    onClick = {
                                        menuOpen = false
                                        vm.setSeasonSeen(key, season, true)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_clear_seen)) },
                                    onClick = {
                                        menuOpen = false
                                        vm.setSeasonSeen(key, season, false)
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
                if (isOpen) {
                    items(episodes) { episode ->
                        val aired = episode.hasAired(today)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (aired) 1f else 0.5f)
                                .clickable(enabled = aired) { vm.toggleEpisode(key, episode.season, episode.number) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = episode.seenKey in show.seen, onCheckedChange = null, enabled = aired)
                            Column {
                                Text(episodeLabel(show.source, episode))
                                if (!aired) {
                                    SecondaryText(
                                        if (episode.airDate.isEmpty()) stringResource(R.string.date_unknown) else formatDate(episode.airDate),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmUnfollow) {
        AlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            title = { Text(stringResource(R.string.unfollow_title, show.name)) },
            text = { Text(stringResource(R.string.unfollow_message)) },
            confirmButton = {
                TextButton(onClick = { confirmUnfollow = false; vm.unfollow(key) }) { Text(stringResource(R.string.action_unfollow)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnfollow = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

package com.mlw.serieustracker.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlw.serieustracker.R
import com.mlw.serieustracker.data.Show
import com.mlw.serieustracker.data.todayIso
import com.mlw.serieustracker.ui.AppViewModel
import com.mlw.serieustracker.ui.components.EmptyState
import com.mlw.serieustracker.ui.components.SecondaryText
import com.mlw.serieustracker.ui.components.ShowRow
import com.mlw.serieustracker.ui.components.daysUntil
import com.mlw.serieustracker.ui.components.episodeLabel
import com.mlw.serieustracker.ui.components.formatDate

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    UPCOMING(R.string.tab_upcoming, Icons.Default.DateRange),
    WATCH_NEXT(R.string.tab_watch_next, Icons.Default.PlayArrow),
    MY_LIST(R.string.tab_my_list, Icons.AutoMirrored.Filled.List),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: AppViewModel, onSearch: () -> Unit, onSettings: () -> Unit, onOpenShow: (String) -> Unit) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val shows by vm.shows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        bottomBar = { BottomTabBar(selected = tabIndex, onSelect = { tabIndex = it }) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (vm.authExpired) ExpiredBanner(onSettings)
            PullToRefreshBox(
                isRefreshing = vm.refreshing,
                onRefresh = { vm.refreshAll(force = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (Tab.entries[tabIndex]) {
                    Tab.UPCOMING -> UpcomingList(shows, onOpenShow)
                    Tab.WATCH_NEXT -> WatchNextList(shows, onOpenShow, onMarkSeen = vm::markNextSeen)
                    Tab.MY_LIST -> MyList(shows, onOpenShow)
                }
            }
        }
    }
}

/** A bottom bar where the whole selected tab is filled, not just the area around the icon. */
@Composable
private fun BottomTabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Tab.entries.forEachIndexed { index, tab ->
            val isSelected = index == selected
            val container = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            val content = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(container)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .navigationBarsPadding()
                    .height(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(tab.icon, contentDescription = null, tint = content)
                Text(
                    stringResource(tab.label),
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun ExpiredBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.banner_expired_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.banner_expired_text), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onOpenSettings) { Text(stringResource(R.string.action_sign_in)) }
        }
    }
}

@Composable
private fun UpcomingList(shows: List<Show>, onOpen: (String) -> Unit) {
    val today = todayIso()
    val items = shows.filter { !it.dropped }
        .mapNotNull { show -> show.nextEpisode(today)?.let { show to it } }
        .sortedBy { it.second.airDate }
    if (items.isEmpty()) {
        EmptyState(R.string.empty_upcoming)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.first.key }) { (show, episode) ->
            ShowRow(
                poster = show.poster,
                title = show.name,
                onClick = { onOpen(show.key) },
                trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(daysUntil(episode.airDate), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                        SecondaryText(formatDate(episode.airDate))
                    }
                },
            ) {
                SecondaryText(episodeLabel(show.source, episode))
            }
        }
    }
}

@Composable
private fun WatchNextList(shows: List<Show>, onOpen: (String) -> Unit, onMarkSeen: (String) -> Unit) {
    val today = todayIso()
    // Most recently watched first, so the show you are in the middle of is always on top.
    val items = shows.filter { !it.dropped }
        .map { it to it.unseenAired(today) }
        .filter { it.second.isNotEmpty() }
        .sortedByDescending { it.first.lastWatched }
    if (items.isEmpty()) {
        EmptyState(R.string.empty_watch_next)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.first.key }) { (show, unseen) ->
            ShowRow(
                poster = show.poster,
                title = show.name,
                onClick = { onOpen(show.key) },
                trailing = {
                    IconButton(onClick = { onMarkSeen(show.key) }) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_mark_next_seen))
                    }
                },
            ) {
                SecondaryText(stringResource(R.string.label_next, episodeLabel(show.source, unseen.first())))
                SecondaryText(pluralStringResource(R.plurals.unseen_episodes, unseen.size, unseen.size))
            }
        }
    }
}

@Composable
private fun MyList(shows: List<Show>, onOpen: (String) -> Unit) {
    val today = todayIso()
    val (dropped, active) = shows.partition { it.dropped }
    var droppedOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        if (dropped.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { droppedOpen = !droppedOpen }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.section_dropped, dropped.size), style = MaterialTheme.typography.titleMedium)
                        Icon(
                            if (droppedOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                }
            }
            if (droppedOpen) {
                // Dimmed so dropped shows are easy to tell apart from the active ones below.
                items(dropped, key = { it.key }) { show -> ListEntry(show, today, onOpen, Modifier.alpha(0.6f)) }
            }
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(thickness = 2.dp, modifier = Modifier.padding(horizontal = 16.dp))
                Text(
                    stringResource(R.string.section_active, active.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }
        }
        if (shows.isEmpty()) {
            item { SecondaryText(stringResource(R.string.empty_my_list), Modifier.padding(16.dp)) }
        }
        items(active, key = { it.key }) { show -> ListEntry(show, today, onOpen) }
    }
}

@Composable
private fun ListEntry(show: Show, today: String, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val aired = show.airedCount(today)
    val seen = show.seenCount()
    ShowRow(poster = show.poster, title = show.name, modifier = modifier, onClick = { onOpen(show.key) }) {
        val ended = if (show.info?.ended == true) " · " + stringResource(R.string.status_ended) else ""
        SecondaryText(stringResource(R.string.progress_seen, seen, aired) + ended)
        LinearProgressIndicator(
            progress = { if (aired == 0) 0f else (seen.toFloat() / aired).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

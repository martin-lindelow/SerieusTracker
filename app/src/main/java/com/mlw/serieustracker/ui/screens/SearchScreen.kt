package com.mlw.serieustracker.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mlw.serieustracker.R
import com.mlw.serieustracker.data.Source
import com.mlw.serieustracker.ui.AppViewModel
import com.mlw.serieustracker.ui.asString
import com.mlw.serieustracker.ui.components.SecondaryText
import com.mlw.serieustracker.ui.components.ShowRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: AppViewModel, onBack: () -> Unit) {
    val shows by vm.shows.collectAsStateWithLifecycle()
    val followed = remember(shows) { shows.map { it.key }.toSet() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_search)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val sources = listOf(Source.ANILIST to R.string.source_anime, Source.PLEX to R.string.source_series)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                sources.forEachIndexed { index, (source, label) ->
                    SegmentedButton(
                        selected = vm.searchSource == source,
                        onClick = { vm.updateSearchSource(source) },
                        shape = SegmentedButtonDefaults.itemShape(index, sources.size),
                    ) { Text(stringResource(label)) }
                }
            }
            OutlinedTextField(
                value = vm.searchQuery,
                onValueChange = { vm.searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
                singleLine = true,
                label = { Text(stringResource(R.string.search_hint)) },
                trailingIcon = {
                    IconButton(onClick = vm::search) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            )
            vm.searchError?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (vm.searching) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            val results = vm.searchResults
            if (results != null && !vm.searching) {
                if (results.isEmpty()) SecondaryText(stringResource(R.string.search_no_results), Modifier.padding(16.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.key }) { result ->
                        ShowRow(
                            poster = result.poster,
                            title = result.name,
                            trailing = {
                                if (result.key in followed) {
                                    OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.action_following)) }
                                } else {
                                    Button(onClick = { vm.follow(result) }) { Text(stringResource(R.string.action_follow)) }
                                }
                            },
                        ) {
                            if (result.subtitle.isNotEmpty()) SecondaryText(result.subtitle)
                        }
                    }
                }
            }
        }
    }
}

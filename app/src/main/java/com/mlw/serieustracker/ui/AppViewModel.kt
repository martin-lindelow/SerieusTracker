package com.mlw.serieustracker.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mlw.serieustracker.R
import com.mlw.serieustracker.data.HistoryProgress
import com.mlw.serieustracker.data.HistoryResult
import com.mlw.serieustracker.data.ImportResult
import com.mlw.serieustracker.data.Library
import com.mlw.serieustracker.data.Prefs
import com.mlw.serieustracker.data.SearchResult
import com.mlw.serieustracker.data.Show
import com.mlw.serieustracker.data.ShowListFile
import com.mlw.serieustracker.data.ShowRepository
import com.mlw.serieustracker.data.Source
import com.mlw.serieustracker.data.episodeKey
import com.mlw.serieustracker.data.todayIso
import com.mlw.serieustracker.network.AuthExpiredException
import com.mlw.serieustracker.network.PlexApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Holds all app state. Screens read it and call the functions below; they never touch the data layer directly. */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val library = Library(application)
    private val plex = PlexApi(prefs)
    private val repository = ShowRepository(library, plex)

    /** Followed shows, sorted by name. */
    val shows: StateFlow<List<Show>> = library.shows
        .map { map -> map.values.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, library.shows.value.values.sortedBy { it.name.lowercase() })

    var refreshing by mutableStateOf(false); private set
    var authExpired by mutableStateOf(false); private set

    // Search
    var searchSource by mutableStateOf(Source.ANILIST); private set
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<SearchResult>?>(null); private set
    var searchError by mutableStateOf<UiText?>(null); private set
    var searching by mutableStateOf(false); private set

    // Settings
    var plexLoggedIn by mutableStateOf(prefs.plexToken.isNotEmpty()); private set
    var serverUrl by mutableStateOf(prefs.serverUrl); private set
    var loginUrl by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<UiText?>(null); private set
    var diagnostic by mutableStateOf<UiText?>(null); private set

    // Watch history import
    var history by mutableStateOf<UiText?>(null); private set
    var historyBusy by mutableStateOf(false); private set
    var historyFailed by mutableStateOf(false); private set

    // Import of a list file
    var importBusy by mutableStateOf(false); private set
    var importStatus by mutableStateOf<UiText?>(null); private set

    private var loginJob: Job? = null

    init {
        refreshAll(force = false)
    }

    // ---- Refreshing ------------------------------------------------------------------------

    fun refreshAll(force: Boolean) {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            if (repository.refreshAll(force) is AuthExpiredException) authExpired = true
            refreshing = false
        }
    }

    fun refreshShow(key: String) {
        viewModelScope.launch { guarded({ notice = it.toUiText() }) { repository.refresh(key) } }
    }

    // ---- Search ----------------------------------------------------------------------------

    fun updateSearchSource(source: Source) {
        searchSource = source
        searchResults = null
        searchError = null
    }

    fun search() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            searching = true
            searchError = null
            searchResults = guarded({ searchError = it.toUiText() }) { repository.search(searchSource, query) }
            searching = false
        }
    }

    fun follow(result: SearchResult) {
        repository.follow(result)
        viewModelScope.launch { guarded({ searchError = it.toUiText() }) { repository.refresh(result.key) } }
    }

    // ---- Tracking --------------------------------------------------------------------------

    fun unfollow(key: String) = library.update { it - key }

    fun toggleEpisode(key: String, season: Int, number: Int) = library.modify(key) { show ->
        val id = episodeKey(season, number)
        if (id in show.seen) {
            show.copy(seen = show.seen - id)
        } else {
            show.copy(seen = show.seen + id, lastWatched = System.currentTimeMillis())
        }
    }

    fun setSeasonSeen(key: String, season: Int, seen: Boolean) {
        val today = todayIso()
        library.modify(key) { show ->
            val ids = show.episodes.filter { it.season == season && it.hasAired(today) }.map { it.seenKey }
            show.copy(
                seen = if (seen) show.seen + ids else show.seen - ids.toSet(),
                lastWatched = if (seen) System.currentTimeMillis() else show.lastWatched,
            )
        }
    }

    fun markNextSeen(key: String) = library.modify(key) { show ->
        show.unseenAired(todayIso()).firstOrNull()
            ?.let { show.copy(seen = show.seen + it.seenKey, lastWatched = System.currentTimeMillis()) }
            ?: show
    }

    /** Dropped shows are hidden from Upcoming and Watch Next. Resuming refreshes the show. */
    fun setDropped(key: String, dropped: Boolean) {
        library.modify(key) { it.copy(dropped = dropped) }
        if (!dropped) refreshShow(key)
    }

    // ---- Plex account ----------------------------------------------------------------------

    fun startLogin() {
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            try {
                val pin = plex.createPin()
                loginUrl = plex.authUrl(pin)
                val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
                while (System.currentTimeMillis() < deadline) {
                    delay(POLL_INTERVAL_MS)
                    val token = try {
                        plex.pollPin(pin)
                    } catch (e: IOException) {
                        null // A flaky connection should not abort the sign-in.
                    }
                    if (token != null) {
                        prefs.plexToken = token
                        plexLoggedIn = prefs.plexToken.isNotEmpty()
                        authExpired = false
                        loginUrl = null
                        notice = UiText.Res(R.string.notice_logged_in)
                        refreshAll(force = true)
                        return@launch
                    }
                }
                notice = UiText.Res(R.string.notice_login_timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.toUiText()
            }
            loginUrl = null
        }
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginUrl = null
    }

    fun saveToken(token: String) {
        prefs.plexToken = token.trim()
        plexLoggedIn = prefs.plexToken.isNotEmpty()
        authExpired = false
        notice = UiText.Res(R.string.notice_token_saved)
    }

    fun logout() {
        prefs.plexToken = ""
        plexLoggedIn = false
        notice = UiText.Res(R.string.notice_signed_out)
    }

    fun saveServerUrl(input: String) {
        var value = input.trim().trimEnd('/')
        if (value.isNotEmpty() && !value.contains("://")) value = "https://$value"
        prefs.serverUrl = value
        serverUrl = value
        notice = UiText.Res(if (value.isEmpty()) R.string.notice_server_cleared else R.string.notice_server_saved)
    }

    fun testPlex() {
        viewModelScope.launch {
            diagnostic = guarded({ diagnostic = it.toUiText() }) {
                UiText.Join(listOf(UiText.Res(R.string.diag_ok), UiText.Raw(plex.searchRaw("Frieren", 3).toString().take(800))))
            }
        }
    }

    // ---- Watch history (runs only when the user asks for it) ---------------------------------

    fun importHistory() {
        if (historyBusy) return
        historyBusy = true
        historyFailed = false
        history = UiText.Res(R.string.history_connecting)
        viewModelScope.launch {
            history = try {
                repository.importHistory { history = it.toUiText() }.toUiText()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is AuthExpiredException) authExpired = true
                historyFailed = true
                UiText.Join(listOf(UiText.Res(R.string.history_failed), e.toUiText()))
            }
            historyBusy = false
        }
    }

    // ---- Backup ----------------------------------------------------------------------------

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            notice = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { it.write(library.export().toByteArray()) }
                }
                UiText.Res(R.string.notice_exported)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiText.Res(R.string.notice_export_failed)
            }
        }
    }

    fun importFrom(uri: Uri) {
        if (importBusy) return
        importBusy = true
        viewModelScope.launch {
            try {
                notice = try {
                    val text = withContext(Dispatchers.IO) {
                        getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    }
                    val parsed = ShowListFile.parse(text)
                    val resolution = repository.resolve(parsed.shows) { done, total ->
                        importStatus = UiText.Res(R.string.import_looking_up, done, total)
                    }
                    val result = library.importMerge(resolution.shows, parsed.skipped, resolution.unmatched)
                    refreshAll(force = false)
                    UiText.Join(listOf(result.toUiText()) + listOfNotNull(resolution.error?.toUiText()))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    UiText.Res(R.string.notice_import_failed)
                }
            } finally {
                importBusy = false
                importStatus = null
            }
        }
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /** Runs [block]; on failure reports the error (and flags an expired Plex login) and returns null. */
    private suspend fun <T> guarded(onError: (Throwable) -> Unit, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e is AuthExpiredException) authExpired = true
        onError(e)
        null
    }

    private companion object {
        const val LOGIN_TIMEOUT_MS = 5 * 60 * 1000L
        const val POLL_INTERVAL_MS = 2000L
    }
}

private fun HistoryProgress.toUiText(): UiText = when (this) {
    HistoryProgress.ConnectingToServer -> UiText.Res(R.string.history_connecting)
    is HistoryProgress.Fetching -> UiText.Res(R.string.history_fetching, server)
    is HistoryProgress.Matching -> UiText.Res(R.string.history_matching, done, total)
    is HistoryProgress.LoadingShows -> UiText.Res(R.string.history_loading, count)
}

private fun HistoryResult.toUiText(): UiText = UiText.Join(
    listOfNotNull(
        UiText.Res(R.string.history_done, server, added, marked),
        unmatched.takeIf { it.isNotEmpty() }?.let {
            UiText.Res(R.string.history_unmatched, it.take(8).joinToString(", ") + if (it.size > 8) "…" else "")
        },
    ),
)

private fun ImportResult.toUiText(): UiText = UiText.Join(
    listOfNotNull(
        UiText.Res(R.string.notice_imported, added, marked),
        skipped.takeIf { it > 0 }?.let { UiText.Res(R.string.notice_import_skipped, it) },
        unmatched.takeIf { it.isNotEmpty() }?.let {
            UiText.Res(R.string.notice_import_unmatched, it.take(8).joinToString(", ") + if (it.size > 8) "…" else "")
        },
    ),
)

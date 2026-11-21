package com.mlw.serieustracker.data

import com.mlw.serieustracker.network.AniListApi
import com.mlw.serieustracker.network.AuthExpiredException
import com.mlw.serieustracker.network.PlexApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

sealed interface HistoryProgress {
    data object ConnectingToServer : HistoryProgress
    data class Fetching(val server: String) : HistoryProgress
    data class Matching(val done: Int, val total: Int) : HistoryProgress
    data class LoadingShows(val count: Int) : HistoryProgress
}

data class HistoryResult(val server: String, val added: Int, val marked: Int, val unmatched: List<String>)

/** Combines the local [Library] with the remote metadata providers. */
class ShowRepository(val library: Library, private val plex: PlexApi) {

    suspend fun search(source: Source, text: String): List<SearchResult> = when (source) {
        Source.ANILIST -> AniListApi.search(text)
        Source.PLEX -> plex.search(text)
    }

    fun follow(result: SearchResult) {
        val show = Show(result.key, result.source, result.id, result.name, result.poster)
        library.update { it + (show.key to show) }
    }

    /** Fetches fresh metadata for one show. The user's seen markers are never touched. */
    suspend fun refresh(key: String) {
        val show = library.shows.value[key] ?: return
        val remote = when (show.source) {
            Source.ANILIST -> AniListApi.fetch(show.id)
            Source.PLEX -> plex.fetch(show.id)
        }
        library.modify(key) {
            it.copy(
                name = remote.name.ifEmpty { it.name },
                poster = remote.poster.ifEmpty { it.poster },
                info = remote.info,
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    /** Refreshes stale shows (or all when [force]), skipping dropped ones. Returns the most relevant error, if any. */
    suspend fun refreshAll(force: Boolean): Throwable? = coroutineScope {
        val now = System.currentTimeMillis()
        val due = library.shows.value.values.filter {
            !it.dropped && (force || it.info == null || now - it.updatedAt > STALE_MS)
        }
        val gate = Semaphore(PARALLEL_REFRESHES)
        val errors = due.map { show ->
            async { gate.withPermit { runCatching { refresh(show.key) }.exceptionOrNull() } }
        }.awaitAll().filterNotNull()
        errors.firstOrNull { it is AuthExpiredException } ?: errors.firstOrNull()
    }

    /** A watched Plex show matched to a library entry. AniList entries only cover season 1. */
    private class Match(val watched: PlexApi.WatchedShow, val key: String, val seasonOneOnly: Boolean)

    /**
     * Reads watched episodes from the Plex server, adds missing shows and marks episodes as seen.
     * A show is matched on Plex first; if Plex does not know it, AniList is tried as a fallback for anime.
     */
    suspend fun importHistory(onProgress: (HistoryProgress) -> Unit): HistoryResult {
        onProgress(HistoryProgress.ConnectingToServer)
        val server = plex.connectToServer()
        onProgress(HistoryProgress.Fetching(server.name))
        val watched = plex.watchedEpisodes(server)

        val matches = mutableListOf<Match>()
        val unmatched = mutableListOf<String>()
        val created = mutableListOf<String>()
        watched.forEachIndexed { index, item ->
            onProgress(HistoryProgress.Matching(index + 1, watched.size))
            val plexId = PLEX_SHOW_GUID.find(item.guid)?.groupValues?.get(1)
                ?: runCatching { plex.search(item.title).firstOrNull { it.name.equals(item.title, ignoreCase = true) }?.id }.getOrNull()
            val anime = if (plexId == null) {
                runCatching { AniListApi.search(item.title).firstOrNull { it.name.equals(item.title, ignoreCase = true) } }.getOrNull()
            } else {
                null
            }
            val source = if (plexId != null) Source.PLEX else if (anime != null) Source.ANILIST else null
            val id = plexId ?: anime?.id
            if (source == null || id == null) {
                unmatched += item.title
                return@forEachIndexed
            }
            val current = library.shows.value
            val existing = current[source.prefix + id]
                ?: current.values.firstOrNull { it.source == source && it.name.equals(item.title, ignoreCase = true) }
            val key = if (existing != null) {
                existing.key
            } else {
                val show = Show(source.prefix + id, source, id, anime?.name ?: item.title, anime?.poster.orEmpty())
                library.update { map -> map + (show.key to show) }
                created += show.key
                show.key
            }
            matches += Match(item, key, seasonOneOnly = source == Source.ANILIST)
        }

        if (created.isNotEmpty()) {
            onProgress(HistoryProgress.LoadingShows(created.size))
            val gate = Semaphore(PARALLEL_REFRESHES)
            coroutineScope {
                created.map { key -> async { gate.withPermit { runCatching { refresh(key) } } } }.awaitAll()
            }
        }

        var marked = 0
        for (match in matches) {
            val show = library.shows.value[match.key] ?: continue
            val known = show.info?.episodes?.map { it.seenKey }?.toSet()
            val episodes = if (match.seasonOneOnly) match.watched.episodes.filter { it.first == 1 } else match.watched.episodes
            val additions = episodes.map { (season, number) -> episodeKey(season, number) }
                .filter { (known == null || it in known) && it !in show.seen }
                .toSet()
            val watchedAt = match.watched.lastWatched
            if (additions.isEmpty() && watchedAt <= show.lastWatched) continue
            library.modify(match.key) { it.copy(seen = it.seen + additions, lastWatched = maxOf(it.lastWatched, watchedAt)) }
            marked += additions.size
        }
        return HistoryResult(server.name, created.size, marked, unmatched)
    }

    private companion object {
        const val STALE_MS = 6 * 60 * 60 * 1000L
        const val PARALLEL_REFRESHES = 3
        val PLEX_SHOW_GUID = Regex("^plex://show/([0-9a-f]+)")
    }
}

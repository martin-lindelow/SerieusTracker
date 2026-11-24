package com.mlw.serieustracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * What an import changed: new shows, newly marked episodes, entries in the file that could not be read, and names
 * that could not be found on AniList/Plex.
 */
data class ImportResult(val added: Int, val marked: Int, val skipped: Int, val unmatched: List<String>)

/** The user's followed shows, kept in memory as a [StateFlow] and persisted to a JSON file. */
class Library(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val writer = Executors.newSingleThreadExecutor()
    private val _shows = MutableStateFlow(load())
    val shows: StateFlow<Map<String, Show>> = _shows.asStateFlow()

    fun update(transform: (Map<String, Show>) -> Map<String, Show>) {
        _shows.update(transform)
        val snapshot = serialize(_shows.value.values)
        writer.execute {
            runCatching {
                val temp = File(file.parentFile, "library.json.tmp")
                temp.writeText(snapshot)
                temp.renameTo(file)
            }
        }
    }

    fun modify(key: String, change: (Show) -> Show) = update { map ->
        map[key]?.let { map + (key to change(it)) } ?: map
    }

    /** The human-readable export file, see [ShowListFile]. Not the same as the app's internal storage format. */
    fun export(): String = ShowListFile.write(_shows.value.values)

    /**
     * Merges an export file into the library and only ever adds: new shows are added, seen episodes are added to
     * existing shows, and nothing is removed or unmarked. A show that is already followed keeps its own status
     * (dropped or not) and its downloaded episode data. Entries without an id are ignored, so look them up first.
     */
    fun importMerge(items: List<ListedShow>, skipped: Int, unmatched: List<String>): ImportResult {
        var added = 0
        var marked = 0
        update { current ->
            // update() may run this more than once, so the counters start from zero every time.
            added = 0
            marked = 0
            val merged = current.toMutableMap()
            for (item in items) {
                val id = item.id ?: continue
                val key = item.source.prefix + id
                val existing = merged[key]
                if (existing == null) {
                    merged[key] = Show(
                        key = key,
                        source = item.source,
                        id = id,
                        name = item.name.ifEmpty { id },
                        poster = "",
                        seen = item.seen,
                        dropped = item.dropped,
                        lastWatched = item.lastWatched,
                    )
                    added++
                    marked += item.seen.size
                } else {
                    val fresh = item.seen - existing.seen
                    marked += fresh.size
                    merged[key] = existing.copy(
                        seen = existing.seen + fresh,
                        lastWatched = maxOf(existing.lastWatched, item.lastWatched),
                    )
                }
            }
            merged
        }
        return ImportResult(added, marked, skipped, unmatched)
    }

    private fun load(): Map<String, Show> = try {
        if (file.exists()) {
            JSONObject(file.readText()).getJSONArray("shows").objects().map { it.toShow() }.associateBy { it.key }
        } else {
            emptyMap()
        }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun serialize(shows: Collection<Show>): String =
        JSONObject().put("version", 1).put("shows", JSONArray(shows.map { it.toJson() })).toString()
}

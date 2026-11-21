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

    fun export(): String = serialize(_shows.value.values)

    /** Merges an exported file into the library. Accepts this app's format and the earlier web prototype's. */
    fun importMerge(text: String) {
        val imported = parse(text)
        update { it + imported }
    }

    private fun load(): Map<String, Show> = try {
        if (file.exists()) parse(file.readText()) else emptyMap()
    } catch (e: Exception) {
        emptyMap()
    }

    private fun serialize(shows: Collection<Show>): String =
        JSONObject().put("version", 1).put("shows", JSONArray(shows.map { it.toJson() })).toString()

    private fun parse(text: String): Map<String, Show> {
        val root = JSONObject(text)
        root.optJSONArray("shows")?.let { array -> return array.objects().map { it.toShow() }.associateBy { it.key } }
        return parseWebPrototype(root.getJSONObject("shows"))
    }

    /** The web prototype stored shows as an object keyed by "al<id>" / "px<id>" with seen episodes as object keys. */
    private fun parseWebPrototype(shows: JSONObject): Map<String, Show> = shows.keys().asSequence().mapNotNull { key ->
        val item = shows.getJSONObject(key)
        val source = when (item.optString("src")) {
            "al" -> Source.ANILIST
            "px" -> Source.PLEX
            else -> return@mapNotNull null
        }
        Show(
            key = key,
            source = source,
            id = item.get("id").toString(),
            name = item.optString("name"),
            poster = item.optString("poster"),
            seen = item.optJSONObject("seen")?.keys()?.asSequence()?.toSet().orEmpty(),
        )
    }.associateBy { it.key }
}

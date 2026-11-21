package com.mlw.serieustracker.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Where a show's metadata comes from. [prefix] namespaces ids so AniList and Plex ids never collide. */
enum class Source(val prefix: String) {
    ANILIST("al"),
    PLEX("px");

    companion object {
        fun fromPrefix(prefix: String): Source = entries.first { it.prefix == prefix }
    }
}

/** Placeholder air date for episodes that are known to have aired but whose date is unknown (AniList). */
const val AIRED_DATE_UNKNOWN = "1970-01-01"

fun todayIso(): String = LocalDate.now().toString()

fun episodeKey(season: Int, number: Int): String = "${season}x$number"

/** [airDate] is an ISO date (yyyy-MM-dd) or empty when unknown. */
data class Episode(val season: Int, val number: Int, val title: String, val airDate: String) {
    val seenKey: String get() = episodeKey(season, number)
    fun hasAired(today: String): Boolean = airDate.isNotEmpty() && airDate <= today
}

data class ShowInfo(val ended: Boolean, val episodes: List<Episode>)

data class Show(
    val key: String,
    val source: Source,
    val id: String,
    val name: String,
    val poster: String,
    val seen: Set<String> = emptySet(),
    val info: ShowInfo? = null,
    val updatedAt: Long = 0L,
    /** Dropped shows stay in My List but are hidden from Upcoming and Watch Next. */
    val dropped: Boolean = false,
    /** Epoch millis of the last time an episode was marked as seen; used to order Watch Next. */
    val lastWatched: Long = 0L,
) {
    val episodes: List<Episode> get() = info?.episodes.orEmpty()

    /** The show's page at its metadata provider, for "read more". */
    val sourceUrl: String
        get() = when (source) {
            Source.ANILIST -> "https://anilist.co/anime/$id"
            Source.PLEX -> "https://app.plex.tv/desktop/#!/provider/tv.plex.provider.discover/details?key=%2Flibrary%2Fmetadata%2F$id"
        }

    fun unseenAired(today: String): List<Episode> = episodes
        .filter { it.hasAired(today) && it.seenKey !in seen }
        .sortedWith(compareBy<Episode>({ it.season }, { it.number }))

    fun airedCount(today: String): Int = episodes.count { it.hasAired(today) }

    fun seenCount(): Int = if (info == null) seen.size else episodes.count { it.seenKey in seen }

    fun nextEpisode(today: String): Episode? = episodes
        .filter { it.airDate.isNotEmpty() && it.airDate > today }
        .minWithOrNull(compareBy<Episode>({ it.airDate }, { it.season }, { it.number }))
}

data class SearchResult(
    val source: Source,
    val id: String,
    val name: String,
    val poster: String,
    val subtitle: String,
) {
    val key: String get() = source.prefix + id
}

/** What a metadata provider returns for one show. Empty [name]/[poster] mean "unknown, keep what we have". */
data class RemoteShow(val name: String, val poster: String, val info: ShowInfo)

// ---- JSON helpers (org.json ships with Android, so no extra dependency is needed) ----

fun JSONObject.str(name: String): String = if (isNull(name)) "" else optString(name)

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun Show.toJson(): JSONObject = JSONObject()
    .put("key", key)
    .put("source", source.prefix)
    .put("id", id)
    .put("name", name)
    .put("poster", poster)
    .put("seen", JSONArray(seen.toList()))
    .put("updatedAt", updatedAt)
    .put("dropped", dropped)
    .put("lastWatched", lastWatched)
    .put("info", info?.toJson() ?: JSONObject.NULL)

private fun ShowInfo.toJson(): JSONObject = JSONObject()
    .put("ended", ended)
    .put(
        "episodes",
        JSONArray(episodes.map { JSONObject().put("s", it.season).put("n", it.number).put("t", it.title).put("d", it.airDate) }),
    )

fun JSONObject.toShow(): Show = Show(
    key = getString("key"),
    source = Source.fromPrefix(getString("source")),
    id = getString("id"),
    name = getString("name"),
    poster = str("poster"),
    seen = optJSONArray("seen")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }.orEmpty(),
    info = optJSONObject("info")?.toShowInfo(),
    updatedAt = optLong("updatedAt"),
    dropped = optBoolean("dropped"),
    lastWatched = optLong("lastWatched"),
)

private fun JSONObject.toShowInfo(): ShowInfo = ShowInfo(
    ended = optBoolean("ended"),
    episodes = optJSONArray("episodes")?.objects().orEmpty()
        .map { Episode(it.getInt("s"), it.getInt("n"), it.str("t"), it.str("d")) },
)

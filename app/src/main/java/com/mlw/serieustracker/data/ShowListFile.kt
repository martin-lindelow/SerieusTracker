package com.mlw.serieustracker.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One show as it appears in an export/import file: only what the user created. Posters, titles of episodes,
 * air dates and so on are fetched again from AniList/Plex after an import.
 */
data class ListedShow(
    val source: Source,
    /** Null when the file only gives a name; the id is then looked up on import. */
    val id: String?,
    val name: String,
    /** Seen episodes as internal keys, see [episodeKey]. */
    val seen: Set<String>,
    val dropped: Boolean,
    /** Epoch millis, 0 when unknown. */
    val lastWatched: Long,
)

class ParsedList(val shows: List<ListedShow>, val skipped: Int)

/**
 * The export/import file format. It is meant to be easy to read and to write by hand:
 *
 * ```
 * {
 *   "app": "SerieusTracker",
 *   "version": 2,
 *   "shows": [
 *     {"name": "Sousou no Frieren", "source": "anilist", "id": 154587, "seen": {"1": "1-12, 14"}},
 *     {"name": "Severance", "source": "plex", "id": "5d9c...", "seen": {"1": "1-9", "2": "1-4"}, "dropped": true}
 *   ]
 * }
 * ```
 *
 * - `source` is "anilist" or "plex". A show also needs an `id` or a `name`; with only a name, the id is looked up
 *   on import.
 * - `seen` maps a season number to the seen episodes of that season, written as ranges. Anime has only season 1.
 * - `dropped` and `lastWatched` (a date or date and time) are optional.
 */
object ShowListFile {
    const val VERSION = 1

    fun write(shows: Collection<Show>): String {
        val lines = shows.sortedBy { it.name.lowercase() }.map { "    " + it.toLine() }
        return buildString {
            append("{\n")
            append("  \"app\": \"SerieusTracker\",\n")
            append("  \"version\": $VERSION,\n")
            append("  \"shows\": [")
            if (lines.isNotEmpty()) append("\n").append(lines.joinToString(",\n")).append("\n  ")
            append("]\n}\n")
        }
    }

    /** Throws if the text is not JSON or contains no list of shows at all. Single bad entries are counted as skipped. */
    fun parse(text: String): ParsedList {
        val root = JSONTokener(text.removePrefix("\uFEFF").trim()).nextValue()
        val shows = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("shows")
            else -> null
        }
        return parseArray(shows ?: throw IllegalArgumentException("No list of shows found"))
    }

    // ---- Writing ---------------------------------------------------------------------------

    private fun Show.toLine(): String {
        val fields = mutableListOf(
            "\"name\": ${quote(name)}",
            "\"source\": ${quote(source.name.lowercase())}",
            "\"id\": ${if (source == Source.ANILIST && id.isPlainNumber()) id else quote(id)}",
            "\"seen\": ${seenToJson(seen)}",
        )
        if (dropped) fields += "\"dropped\": true"
        if (lastWatched > 0) {
            fields += "\"lastWatched\": ${quote(Instant.ofEpochMilli(lastWatched).truncatedTo(ChronoUnit.SECONDS).toString())}"
        }
        return fields.joinToString(", ", "{", "}")
    }

    private fun seenToJson(seen: Set<String>): String {
        val bySeason = seen.mapNotNull { parseEpisodeKey(it) }.groupBy({ it.first }, { it.second }).toSortedMap()
        return bySeason.entries.joinToString(", ", "{", "}") { (season, numbers) ->
            "\"$season\": ${quote(formatRanges(numbers))}"
        }
    }

    /** Android's JSON writer escapes "/" as "\/", which is valid but ugly in titles like "Fate/Zero". */
    private fun quote(text: String): String = JSONObject.quote(text).replace("\\/", "/")

    // ---- Reading ---------------------------------------------------------------------------

    private fun parseArray(array: JSONArray): ParsedList {
        val shows = mutableListOf<ListedShow>()
        var skipped = 0
        for (index in 0 until array.length()) {
            val show = array.optJSONObject(index)?.let { parseShow(it) }
            if (show != null) shows += show else skipped++
        }
        return ParsedList(shows, skipped)
    }

    private fun parseShow(item: JSONObject): ListedShow? {
        val source = sourceOf(item.opt("source")) ?: return null
        val id = idOf(item.opt("id"))
        val name = item.str("name").trim()
        if (id == null && name.isEmpty()) return null
        if (id != null && source == Source.ANILIST && !id.isPlainNumber()) return null
        return ListedShow(
            source = source,
            id = id,
            name = name,
            seen = seenOf(item.opt("seen")),
            dropped = item.optBoolean("dropped"),
            lastWatched = timestampOf(item.opt("lastWatched")),
        )
    }
}

// ---- Value helpers ---------------------------------------------------------------------------

/** Longest episode number accepted, so a typo like "1-99999999" cannot exhaust memory. */
private const val MAX_EPISODE = 20_000

private val DASHES = Regex("\\s*[-\u2013\u2014]\\s*")
private val SEPARATORS = Regex("[,;\\s]+")

private fun String.isPlainNumber(): Boolean = isNotEmpty() && length <= 9 && this[0] != '0' && all { it in '0'..'9' }

private fun sourceOf(value: Any?): Source? {
    val text = (value as? String)?.trim() ?: return null
    return Source.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
}

private fun idOf(value: Any?): String? = when (value) {
    is Int, is Long -> value.toString()
    is String -> value.trim().ifEmpty { null }
    else -> null
}

/**
 * Seen episodes as internal keys. Accepted shapes:
 * - `{"1": "1-12, 14", "2": [1, 2, 3]}`: episodes per season
 * - `"1-12, 14"` or `[1, 2, 3]`: episodes of season 1
 */
private fun seenOf(value: Any?): Set<String> {
    val keys = linkedSetOf<String>()
    when (value) {
        is JSONObject -> {
            value.keys().forEach { name ->
                val season = name.trim().toIntOrNull()
                if (season != null && season > 0) episodeNumbers(value.opt(name)).forEach { keys += episodeKey(season, it) }
            }
        }
        is JSONArray -> {
            episodeNumbers(value).forEach { keys += episodeKey(1, it) }
        }
        is String -> episodeNumbers(value).forEach { keys += episodeKey(1, it) }
    }
    return keys
}

private fun episodeNumbers(value: Any?): Set<Int> = when (value) {
    is String -> parseRanges(value)
    is Number -> value.toLong().let { if (it in 1L..MAX_EPISODE.toLong()) setOf(it.toInt()) else emptySet<Int>() }
    is JSONArray -> (0 until value.length()).flatMap { episodeNumbers(value.opt(it)) }.toSet()
    else -> emptySet()
}

/** "1-12, 14, 16-18" becomes 1..12, 14, 16..18. Anything that is not a number or a range is ignored. */
private fun parseRanges(text: String): Set<Int> {
    val numbers = linkedSetOf<Int>()
    for (token in text.replace(DASHES, "-").split(SEPARATORS)) {
        val ends = token.split('-')
        val first = ends[0].toIntOrNull() ?: continue
        val last = when (ends.size) {
            1 -> first
            2 -> ends[1].toIntOrNull()
            else -> null
        } ?: continue
        if (first < 1 || last < first) continue
        for (number in first..minOf(last, MAX_EPISODE)) numbers += number
    }
    return numbers
}

/** The reverse of [parseRanges]: 1, 2, 3, 5 becomes "1-3, 5". */
private fun formatRanges(numbers: Collection<Int>): String {
    val sorted = numbers.distinct().sorted()
    val parts = mutableListOf<String>()
    var start = 0
    while (start < sorted.size) {
        var end = start
        while (end + 1 < sorted.size && sorted[end + 1] == sorted[end] + 1) end++
        parts += if (end == start) "${sorted[start]}" else "${sorted[start]}-${sorted[end]}"
        start = end + 1
    }
    return parts.joinToString(", ")
}

/** "2x5" to season 2, episode 5. */
private fun parseEpisodeKey(key: String): Pair<Int, Int>? {
    val season = key.substringBefore('x', "").toIntOrNull() ?: return null
    val number = key.substringAfter('x', "").toIntOrNull() ?: return null
    return if (season > 0 && number > 0) season to number else null
}

/** A date ("2026-11-20") or a date and time ("2026-11-20T21:14:00Z"). */
private fun timestampOf(value: Any?): Long = if (value is String) parseTimestamp(value.trim()) else 0L

private fun parseTimestamp(text: String): Long =
    runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        ?: 0L

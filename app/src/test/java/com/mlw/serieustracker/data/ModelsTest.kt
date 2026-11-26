package com.mlw.serieustracker.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelsTest {

    private val today = "2026-03-01"

    private fun show(
        episodes: List<Episode> = emptyList(),
        seen: Set<String> = emptySet(),
        withInfo: Boolean = true,
    ) = Show(
        key = "al1",
        source = Source.ANILIST,
        id = "1",
        name = "Test",
        poster = "",
        seen = seen,
        info = if (withInfo) ShowInfo(ended = false, episodes = episodes) else null,
    )

    // ---- Episode ---------------------------------------------------------------------------

    @Test
    fun episodeKeyFormatting() {
        assertEquals("1x1", episodeKey(1, 1))
        assertEquals("2x12", episodeKey(2, 12))
        assertEquals("10x100", episodeKey(10, 100))
    }

    @Test
    fun episodeSeenKeyMatchesEpisodeKey() {
        assertEquals(episodeKey(2, 5), Episode(2, 5, "Title", "2026-01-01").seenKey)
    }

    @Test
    fun episodeHasAired() {
        val past = Episode(1, 1, "Past", "2026-02-28")
        val sameDay = Episode(1, 2, "Today", "2026-03-01")
        val future = Episode(1, 3, "Future", "2026-03-02")
        val unknown = Episode(1, 4, "Unknown", "")

        assertTrue(past.hasAired(today))
        assertTrue(sameDay.hasAired(today))
        assertFalse(future.hasAired(today))
        assertFalse(unknown.hasAired(today))
    }

    @Test
    fun placeholderDateCountsAsAired() {
        assertTrue(Episode(1, 1, "", AIRED_DATE_UNKNOWN).hasAired(today))
    }

    // ---- Show ------------------------------------------------------------------------------

    @Test
    fun unseenAiredIsFilteredAndSortedBySeasonThenNumber() {
        val show = show(
            episodes = listOf(
                Episode(2, 1, "", "2026-01-01"),
                Episode(1, 3, "", "2026-01-01"),
                Episode(1, 1, "", "2026-01-01"),
                Episode(1, 2, "", "2026-01-01"),
                Episode(1, 4, "", "2026-04-01"), // not aired yet
                Episode(1, 5, "", ""), // unknown date
            ),
            seen = setOf("1x2"),
        )
        val unseen = show.unseenAired(today).map { it.seenKey }
        assertEquals(listOf("1x1", "1x3", "2x1"), unseen)
    }

    @Test
    fun unseenAiredIsEmptyWithoutInfo() {
        assertTrue(show(withInfo = false).unseenAired(today).isEmpty())
    }

    @Test
    fun airedCountOnlyCountsAiredEpisodes() {
        val show = show(
            episodes = listOf(
                Episode(1, 1, "", "2026-01-01"),
                Episode(1, 2, "", "2026-03-01"),
                Episode(1, 3, "", "2026-03-02"),
                Episode(1, 4, "", ""),
            ),
        )
        assertEquals(2, show.airedCount(today))
    }

    @Test
    fun seenCountWithoutInfoCountsAllSeenKeys() {
        assertEquals(3, show(seen = setOf("1x1", "1x2", "1x3"), withInfo = false).seenCount())
    }

    @Test
    fun seenCountWithInfoIgnoresEpisodesThatDoNotExist() {
        val show = show(
            episodes = listOf(Episode(1, 1, "", "2026-01-01"), Episode(1, 2, "", "2026-01-01")),
            seen = setOf("1x1", "1x2", "1x99", "2x1"),
        )
        assertEquals(2, show.seenCount())
    }

    @Test
    fun nextEpisodeIsTheEarliestFutureOne() {
        val show = show(
            episodes = listOf(
                Episode(1, 1, "", "2026-02-01"), // aired
                Episode(1, 3, "", "2026-03-15"),
                Episode(1, 2, "", "2026-03-08"),
                Episode(1, 4, "", ""), // unknown date
            ),
        )
        assertEquals(2, show.nextEpisode(today)?.number)
    }

    @Test
    fun nextEpisodeBreaksDateTiesBySeasonThenNumber() {
        val show = show(
            episodes = listOf(
                Episode(2, 1, "", "2026-03-08"),
                Episode(1, 5, "", "2026-03-08"),
                Episode(1, 4, "", "2026-03-08"),
            ),
        )
        assertEquals("1x4", show.nextEpisode(today)?.seenKey)
    }

    @Test
    fun nextEpisodeIsNullWhenNothingIsUpcoming() {
        val show = show(episodes = listOf(Episode(1, 1, "", "2026-01-01"), Episode(1, 2, "", "")))
        assertNull(show.nextEpisode(today))
        assertNull(show(withInfo = false).nextEpisode(today))
    }

    @Test
    fun nextEpisodeDoesNotCountTodayAsUpcoming() {
        val show = show(episodes = listOf(Episode(1, 1, "", today)))
        assertNull(show.nextEpisode(today))
    }

    @Test
    fun anilistSourceUrl() {
        val show = show().copy(id = "154587")
        assertEquals("https://anilist.co/anime/154587", show.sourceUrl)
    }

    @Test
    fun plexSourceUrlContainsTheId() {
        val show = show().copy(source = Source.PLEX, id = "5d9c1f")
        assertTrue(show.sourceUrl.startsWith("https://app.plex.tv/"))
        assertTrue(show.sourceUrl.endsWith("%2Flibrary%2Fmetadata%2F5d9c1f"))
    }

    // ---- Source and SearchResult -----------------------------------------------------------

    @Test
    fun sourceFromPrefix() {
        assertEquals(Source.ANILIST, Source.fromPrefix("al"))
        assertEquals(Source.PLEX, Source.fromPrefix("px"))
        assertThrows(NoSuchElementException::class.java) { Source.fromPrefix("zz") }
    }

    @Test
    fun searchResultKeyIsPrefixPlusId() {
        assertEquals("al154587", SearchResult(Source.ANILIST, "154587", "Frieren", "", "").key)
        assertEquals("px5d9c", SearchResult(Source.PLEX, "5d9c", "Severance", "", "").key)
    }

    @Test
    fun todayIsoIsAValidIsoDate() {
        assertEquals(todayIso(), LocalDate.parse(todayIso()).toString())
    }

    // ---- JSON ------------------------------------------------------------------------------

    @Test
    fun showSurvivesAJsonRoundTrip() {
        val original = Show(
            key = "px5d9c",
            source = Source.PLEX,
            id = "5d9c",
            name = "Severance",
            poster = "https://example.com/poster.jpg",
            seen = setOf("1x1", "1x2", "2x1"),
            info = ShowInfo(
                ended = true,
                episodes = listOf(
                    Episode(1, 1, "Good News About Hell", "2022-02-18"),
                    Episode(1, 2, "", AIRED_DATE_UNKNOWN),
                    Episode(2, 1, "Hello, Ms. Cobel", ""),
                ),
            ),
            updatedAt = 1_700_000_000_000L,
            dropped = true,
            lastWatched = 1_700_000_500_000L,
        )
        assertEquals(original, original.toJson().toShow())
    }

    @Test
    fun showWithoutInfoSurvivesAJsonRoundTrip() {
        val original = show(withInfo = false, seen = setOf("1x1"))
        assertEquals(original, original.toJson().toShow())
    }

    @Test
    fun showFromMinimalJsonGetsDefaults() {
        val show = JSONObject("""{"key": "al1", "source": "al", "id": "1", "name": "X"}""").toShow()
        assertEquals("", show.poster)
        assertEquals(emptySet<String>(), show.seen)
        assertNull(show.info)
        assertEquals(0L, show.updatedAt)
        assertFalse(show.dropped)
        assertEquals(0L, show.lastWatched)
    }

    @Test
    fun showFromJsonWithoutRequiredFieldsThrows() {
        assertThrows(Exception::class.java) { JSONObject("""{"source": "al", "id": "1", "name": "X"}""").toShow() }
    }

    @Test
    fun strTreatsMissingAndNullAsEmpty() {
        val json = JSONObject("""{"a": null, "b": "text"}""")
        assertEquals("", json.str("a"))
        assertEquals("", json.str("missing"))
        assertEquals("text", json.str("b"))
    }

    @Test
    fun objectsSkipsNonObjectEntries() {
        val objects = JSONArray("""[{"a": 1}, 2, "x", null, {"b": 2}]""").objects()
        assertEquals(2, objects.size)
    }
}

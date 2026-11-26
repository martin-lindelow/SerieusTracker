package com.mlw.serieustracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ShowListFileTest {

    // ---- Helpers ---------------------------------------------------------------------------

    private fun show(
        name: String,
        source: Source = Source.ANILIST,
        id: String = "1",
        seen: Set<String> = emptySet(),
        dropped: Boolean = false,
        lastWatched: Long = 0L,
    ) = Show(
        key = source.prefix + id,
        source = source,
        id = id,
        name = name,
        poster = "",
        seen = seen,
        dropped = dropped,
        lastWatched = lastWatched,
    )

    /** Parses a single show given as a JSON object. */
    private fun parseOne(json: String): ListedShow = ShowListFile.parse("[$json]").shows.single()

    /** Season 1 episode keys, e.g. `s1(1, 2)` is {"1x1", "1x2"}. */
    private fun s1(vararg numbers: Int): Set<String> = numbers.map { episodeKey(1, it) }.toSet()

    /** The seen episodes of a show whose `seen` field is the given raw JSON. */
    private fun seenFrom(rawJson: String): Set<String> =
        parseOne("""{"name": "X", "source": "anilist", "id": 1, "seen": $rawJson}""").seen

    /** The seen episodes of season 1 when written as the range string [text]. */
    private fun ranges(text: String): Set<String> = seenFrom("""{"1": "$text"}""")

    private fun lastWatched(text: String): Long =
        parseOne("""{"name": "X", "source": "anilist", "id": 1, "lastWatched": "$text"}""").lastWatched

    // ---- Writing ---------------------------------------------------------------------------

    @Test
    fun writeEmptyList() {
        val expected = "{\n  \"app\": \"SerieusTracker\",\n  \"version\": ${ShowListFile.VERSION},\n  \"shows\": []\n}\n"
        assertEquals(expected, ShowListFile.write(emptyList()))
    }

    @Test
    fun writeOneShowOnOneLine() {
        val show = show(
            name = "Sousou no Frieren",
            id = "154587",
            seen = s1(1, 2, 3, 5),
            dropped = true,
            lastWatched = 1_700_000_000_000L,
        )
        val expectedLine = "    {\"name\": \"Sousou no Frieren\", \"source\": \"anilist\", \"id\": 154587, " +
            "\"seen\": {\"1\": \"1-3, 5\"}, \"dropped\": true, \"lastWatched\": \"2023-11-14T22:13:20Z\"}"
        assertTrue(ShowListFile.write(listOf(show)).lines().contains(expectedLine))
    }

    @Test
    fun anilistIdIsWrittenAsNumberAndPlexIdAsString() {
        val text = ShowListFile.write(
            listOf(
                show("Anime", Source.ANILIST, "154587"),
                show("Series", Source.PLEX, "5d9c1f"),
                show("Numeric Plex id", Source.PLEX, "12345"),
            ),
        )
        assertTrue(text.contains("\"id\": 154587,"))
        assertTrue(text.contains("\"id\": \"5d9c1f\","))
        assertTrue(text.contains("\"id\": \"12345\","))
    }

    @Test
    fun droppedAndLastWatchedAreOnlyWrittenWhenSet() {
        val text = ShowListFile.write(listOf(show("Plain")))
        assertTrue(!text.contains("dropped"))
        assertTrue(!text.contains("lastWatched"))
    }

    @Test
    fun noSeenEpisodesGivesEmptyObject() {
        assertTrue(ShowListFile.write(listOf(show("Nothing seen"))).contains("\"seen\": {}"))
    }

    @Test
    fun seenEpisodesAreGroupedBySeasonInNumericOrder() {
        val seen = setOf("10x1", "2x2", "2x1", "2x3", "2x7")
        val text = ShowListFile.write(listOf(show("Seasons", Source.PLEX, "abc", seen)))
        assertTrue(text.contains("\"seen\": {\"2\": \"1-3, 7\", \"10\": \"1\"}"))
    }

    @Test
    fun malformedSeenKeysAreNotWritten() {
        val text = ShowListFile.write(listOf(show("Odd keys", seen = setOf("1x1", "garbage", "0x1", "1x0", "x"))))
        assertTrue(text.contains("\"seen\": {\"1\": \"1\"}"))
    }

    @Test
    fun showsAreSortedByNameIgnoringCase() {
        val text = ShowListFile.write(
            listOf(
                show("bleach", id = "2"),
                show("Attack on Titan", id = "1"),
                show("Clannad", id = "3"),
            ),
        )
        val names = ShowListFile.parse(text).shows.map { it.name }
        assertEquals(listOf("Attack on Titan", "bleach", "Clannad"), names)
    }

    @Test
    fun slashInTitleIsNotEscaped() {
        val text = ShowListFile.write(listOf(show("Fate/Zero")))
        assertTrue(text.contains("\"name\": \"Fate/Zero\""))
        assertTrue(!text.contains("\\/"))
    }

    @Test
    fun lastWatchedIsTruncatedToWholeSeconds() {
        val text = ShowListFile.write(listOf(show("Precise", lastWatched = 1_700_000_000_123L)))
        assertEquals(1_700_000_000_000L, ShowListFile.parse(text).shows.single().lastWatched)
    }

    // ---- Round trip ------------------------------------------------------------------------

    @Test
    fun roundTripKeepsEverythingTheUserCreated() {
        val shows = listOf(
            show("Sousou no Frieren", Source.ANILIST, "154587", s1(1, 2, 3, 5), lastWatched = 1_700_000_000_000L),
            show("Severance", Source.PLEX, "5d9c1f", setOf("1x1", "1x2", "2x1"), dropped = true),
            show("Say \"Hello\" / Fate/Zero", Source.ANILIST, "10087"),
        )
        val parsed = ShowListFile.parse(ShowListFile.write(shows))

        assertEquals(0, parsed.skipped)
        val expected = shows.sortedBy { it.name.lowercase() }.map {
            ListedShow(it.source, it.id, it.name, it.seen, it.dropped, it.lastWatched)
        }
        assertEquals(expected, parsed.shows)
    }

    @Test
    fun exportImportExportGivesIdenticalText() {
        val shows = listOf(
            show("Frieren", Source.ANILIST, "154587", s1(1, 2, 3, 5, 6), lastWatched = 1_700_000_000_000L),
            show("Severance", Source.PLEX, "5d9c1f", setOf("1x1", "2x1", "2x2"), dropped = true),
        )
        val first = ShowListFile.write(shows)
        val reimported = ShowListFile.parse(first).shows.map {
            show(it.name, it.source, it.id!!, it.seen, it.dropped, it.lastWatched)
        }
        assertEquals(first, ShowListFile.write(reimported))
    }

    // ---- Parsing: structure ----------------------------------------------------------------

    @Test
    fun parseAcceptsBareArray() {
        val parsed = ShowListFile.parse("""[{"name": "A", "source": "anilist", "id": 1}]""")
        assertEquals(1, parsed.shows.size)
        assertEquals("1", parsed.shows[0].id)
    }

    @Test
    fun parseAcceptsObjectWithShowsArray() {
        val parsed = ShowListFile.parse("""{"app": "SerieusTracker", "version": 2, "shows": [{"name": "A", "source": "anilist", "id": 1}]}""")
        assertEquals(1, parsed.shows.size)
    }

    @Test
    fun parseIgnoresByteOrderMarkAndSurroundingWhitespace() {
        val parsed = ShowListFile.parse("\uFEFF  \n[{\"name\": \"A\", \"source\": \"plex\", \"id\": \"x\"}]\n ")
        assertEquals(1, parsed.shows.size)
    }

    @Test
    fun parseAcceptsEmptyLists() {
        assertEquals(0, ShowListFile.parse("[]").shows.size)
        assertEquals(0, ShowListFile.parse("""{"shows": []}""").shows.size)
    }

    @Test
    fun parseThrowsWhenThereIsNoListOfShows() {
        listOf("", "not json", "{broken", "42", """{"foo": 1}""", """{"shows": "nope"}""").forEach { text ->
            assertThrows("input: '$text'", Exception::class.java) { ShowListFile.parse(text) }
        }
    }

    // ---- Parsing: single entries -----------------------------------------------------------

    @Test
    fun badEntriesAreSkippedAndCounted() {
        val parsed = ShowListFile.parse(
            """
            [
              {"name": "Ok", "source": "anilist", "id": 1},
              {"name": "No source", "id": 2},
              {"name": "Bad source", "source": "netflix", "id": 3},
              {"source": "anilist"},
              {"name": "Bad id", "source": "anilist", "id": "abc"},
              42,
              "text"
            ]
            """.trimIndent(),
        )
        assertEquals(listOf("Ok"), parsed.shows.map { it.name })
        assertEquals(6, parsed.skipped)
    }

    @Test
    fun sourceIsCaseInsensitive() {
        assertEquals(Source.ANILIST, parseOne("""{"name": "A", "source": "AniList", "id": 1}""").source)
        assertEquals(Source.PLEX, parseOne("""{"name": "A", "source": " PLEX ", "id": "x"}""").source)
    }

    @Test
    fun anilistIdMustBeAPlainNumber() {
        assertEquals("154587", parseOne("""{"source": "anilist", "id": "154587"}""").id)
        assertEquals("154587", parseOne("""{"source": "anilist", "id": " 154587 "}""").id)
        listOf("007", "1234567890", "12a", "-5", "1.5").forEach { id ->
            val parsed = ShowListFile.parse("""[{"name": "A", "source": "anilist", "id": "$id"}]""")
            assertEquals("id $id should be rejected", 1, parsed.skipped)
        }
    }

    @Test
    fun plexIdMayBeAnyTextOrNumber() {
        assertEquals("5d9c1f", parseOne("""{"source": "plex", "id": "5d9c1f"}""").id)
        assertEquals("12345", parseOne("""{"source": "plex", "id": 12345}""").id)
    }

    @Test
    fun nameOnlyEntryHasNoId() {
        val show = parseOne("""{"name": "  Frieren  ", "source": "anilist"}""")
        assertNull(show.id)
        assertEquals("Frieren", show.name)
    }

    @Test
    fun idOnlyEntryHasEmptyName() {
        val show = parseOne("""{"source": "anilist", "id": 154587}""")
        assertEquals("154587", show.id)
        assertEquals("", show.name)
    }

    @Test
    fun droppedDefaultsToFalse() {
        assertEquals(false, parseOne("""{"name": "A", "source": "anilist", "id": 1}""").dropped)
        assertEquals(true, parseOne("""{"name": "A", "source": "anilist", "id": 1, "dropped": true}""").dropped)
    }

    // ---- Parsing: seen episodes ------------------------------------------------------------

    @Test
    fun seenAsObjectWithRangesAndArrays() {
        val seen = seenFrom("""{"1": "1-3, 5", "2": [1, 2]}""")
        assertEquals(setOf("1x1", "1x2", "1x3", "1x5", "2x1", "2x2"), seen)
    }

    @Test
    fun seenAsStringOrArrayMeansSeasonOne() {
        assertEquals(s1(1, 2, 3), seenFrom("\"1-3\""))
        assertEquals(s1(1, 2), seenFrom("[1, 2]"))
    }

    @Test
    fun seenIsEmptyWhenMissingOrWrongType() {
        assertEquals(emptySet<String>(), parseOne("""{"name": "A", "source": "anilist", "id": 1}""").seen)
        assertEquals(emptySet<String>(), seenFrom("true"))
        assertEquals(emptySet<String>(), seenFrom("null"))
    }

    @Test
    fun invalidSeasonKeysAreIgnored() {
        val seen = seenFrom("""{"0": "1", "-1": "1", "abc": "1", " 3 ": "1", "2": "1"}""")
        assertEquals(setOf("3x1", "2x1"), seen)
    }

    @Test
    fun rangesSupportCommasSemicolonsAndSpaces() {
        assertEquals(s1(1, 2, 3, 5, 7), ranges("1-3, 5; 7"))
        assertEquals(s1(1, 2, 3), ranges("1 2 3"))
    }

    @Test
    fun rangesSupportEnAndEmDashesAndSpacesAroundDash() {
        assertEquals(s1(1, 2, 3), ranges("1\u20133"))
        assertEquals(s1(1, 2, 3), ranges("1\u20143"))
        assertEquals(s1(1, 2, 3), ranges("1 - 3"))
    }

    @Test
    fun overlappingRangesAreMerged() {
        assertEquals(s1(1, 2, 3, 4, 5, 6, 7), ranges("1-5, 3-7"))
    }

    @Test
    fun invalidRangeTokensAreIgnored() {
        assertEquals(emptySet<String>(), ranges("5-3"))
        assertEquals(emptySet<String>(), ranges("0-3"))
        assertEquals(emptySet<String>(), ranges("1-2-3"))
        assertEquals(emptySet<String>(), ranges("1-"))
        assertEquals(emptySet<String>(), ranges("abc"))
        assertEquals(emptySet<String>(), ranges(""))
        assertEquals(s1(2), ranges("abc, 2"))
    }

    @Test
    fun hugeRangesAreCappedAtTwentyThousand() {
        val seen = ranges("1-99999999")
        assertEquals(20_000, seen.size)
        assertTrue(seen.contains("1x20000"))
        assertTrue(!seen.contains("1x20001"))
    }

    @Test
    fun episodeNumbersAboveTheLimitAreIgnored() {
        assertEquals(s1(20_000), seenFrom("""{"1": [20000, 20001, 0, -1]}"""))
    }

    // ---- Parsing: lastWatched --------------------------------------------------------------

    @Test
    fun lastWatchedAcceptsDateTimeWithZoneOrOffset() {
        val expected = Instant.parse("2026-11-20T21:14:00Z").toEpochMilli()
        assertEquals(expected, lastWatched("2026-11-20T21:14:00Z"))
        assertEquals(expected, lastWatched("2026-11-20T22:14:00+01:00"))
    }

    @Test
    fun lastWatchedAcceptsPlainDateAsStartOfDayInLocalZone() {
        val expected = LocalDate.parse("2026-11-20").atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, lastWatched("2026-11-20"))
    }

    @Test
    fun lastWatchedIsZeroWhenMissingOrInvalid() {
        assertEquals(0L, lastWatched("yesterday"))
        assertEquals(0L, lastWatched(""))
        assertEquals(0L, parseOne("""{"name": "A", "source": "anilist", "id": 1}""").lastWatched)
        assertEquals(0L, parseOne("""{"name": "A", "source": "anilist", "id": 1, "lastWatched": 12345}""").lastWatched)
    }
}

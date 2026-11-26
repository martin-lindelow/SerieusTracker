package com.mlw.serieustracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowModelTest {

    private val sampleAnimeEpisodes = listOf(
        Episode(season = 1, number = 1, title = "Episode 1", airDate = "2024-01-01"),
        Episode(season = 1, number = 2, title = "Episode 2", airDate = "2024-01-08"),
        Episode(season = 1, number = 3, title = "Episode 3", airDate = "2024-01-15"),
        Episode(season = 1, number = 4, title = "Episode 4", airDate = "2099-01-01"), // Unaired
    )

    private val sampleSeriesEpisodes = listOf(
        Episode(season = 1, number = 1, title = "Pilot", airDate = "2024-01-01"),
        Episode(season = 1, number = 2, title = "Episode 2", airDate = "2024-01-08"),
        Episode(season = 2, number = 1, title = "S2 E1", airDate = "2024-02-01"),
        Episode(season = 2, number = 2, title = "S2 E2", airDate = "2024-02-08"),
    )

    @Test
    fun testAddAnimeShow() {
        val anime = Show(
            key = "al12345",
            source = Source.ANILIST,
            id = "12345",
            name = "Frieren",
            poster = "https://example.com/poster.jpg",
            info = ShowInfo(ended = true, episodes = sampleAnimeEpisodes),
        )

        assertEquals("al12345", anime.key)
        assertEquals(Source.ANILIST, anime.source)
        assertEquals("Frieren", anime.name)
        assertEquals("https://anilist.co/anime/12345", anime.sourceUrl)
        assertEquals(4, anime.episodes.size)
        assertFalse(anime.dropped)
    }

    @Test
    fun testToggleSingleEpisode() {
        var show = Show(
            key = "al12345",
            source = Source.ANILIST,
            id = "12345",
            name = "Frieren",
            poster = "",
            seen = emptySet(),
            info = ShowInfo(ended = false, episodes = sampleAnimeEpisodes),
        )

        // Mark episode 1x1 as seen
        val ep1Key = episodeKey(1, 1)
        show = show.copy(seen = show.seen + ep1Key)

        assertTrue(ep1Key in show.seen)
        assertEquals(1, show.seenCount())

        // Toggle/remove episode 1x1
        show = show.copy(seen = show.seen - ep1Key)
        assertFalse(ep1Key in show.seen)
        assertEquals(0, show.seenCount())
    }

    @Test
    fun testMarkAllEpisodesSeenInSeason() {
        val today = "2024-01-20"
        var show = Show(
            key = "px999",
            source = Source.PLEX,
            id = "999",
            name = "Test Series",
            poster = "",
            seen = emptySet(),
            info = ShowInfo(ended = false, episodes = sampleSeriesEpisodes),
        )

        // Mark all aired season 1 episodes seen
        val season1Aired = show.episodes.filter { it.season == 1 && it.hasAired(today) }.map { it.seenKey }
        show = show.copy(seen = show.seen + season1Aired)

        assertEquals(2, show.seen.size)
        assertTrue("1x1" in show.seen)
        assertTrue("1x2" in show.seen)
        assertFalse("2x1" in show.seen)
    }

    @Test
    fun testClearSeasonSeen() {
        var show = Show(
            key = "px999",
            source = Source.PLEX,
            id = "999",
            name = "Test Series",
            poster = "",
            seen = setOf("1x1", "1x2", "2x1", "2x2"),
            info = ShowInfo(ended = false, episodes = sampleSeriesEpisodes),
        )

        // Clear season 1 seen episodes
        val season1Keys = show.episodes.filter { it.season == 1 }.map { it.seenKey }.toSet()
        show = show.copy(seen = show.seen - season1Keys)

        assertEquals(2, show.seen.size)
        assertFalse("1x1" in show.seen)
        assertFalse("1x2" in show.seen)
        assertTrue("2x1" in show.seen)
        assertTrue("2x2" in show.seen)
    }

    @Test
    fun testDropShowAndMyListPartitioning() {
        val activeShow = Show("al1", Source.ANILIST, "1", "Active Anime", "", dropped = false)
        val droppedShow = Show("px2", Source.PLEX, "2", "Dropped Series", "", dropped = true)

        val shows = listOf(activeShow, droppedShow)

        val (dropped, active) = shows.partition { it.dropped }

        assertEquals(1, active.size)
        assertEquals("Active Anime", active.first().name)

        assertEquals(1, dropped.size)
        assertEquals("Dropped Series", dropped.first().name)
        assertTrue(dropped.first().dropped)
    }
}

package com.mlw.serieustracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ShowNavigationLogicTest {

    private val today = "2026-03-01"

    private val episodes = listOf(
        Episode(season = 1, number = 1, title = "Ep 1", airDate = "2026-01-01"),
        Episode(season = 1, number = 2, title = "Ep 2", airDate = "2026-02-01"),
        Episode(season = 1, number = 3, title = "Ep 3", airDate = "2026-03-15"), // Future
        Episode(season = 1, number = 4, title = "Ep 4", airDate = "2026-04-01"), // Future
        Episode(season = 1, number = 5, title = "Ep 5", airDate = ""),           // Unknown date
    )

    @Test
    fun testUnseenAiredFiltersOnlyAiredAndUnseenEpisodesInOrder() {
        val show = Show(
            key = "al100",
            source = Source.ANILIST,
            id = "100",
            name = "Test Show",
            poster = "",
            seen = setOf("1x1"), // Ep 1 is seen
            info = ShowInfo(ended = false, episodes = episodes),
        )

        val unseen = show.unseenAired(today)

        // Only Ep 2 has aired and is unseen. Ep 3 and Ep 4 air in the future, Ep 5 date unknown.
        assertEquals(1, unseen.size)
        assertEquals(2, unseen.first().number)
    }

    @Test
    fun testNextEpisodeFindsEarliestFutureAiringEpisode() {
        val show = Show(
            key = "al100",
            source = Source.ANILIST,
            id = "100",
            name = "Test Show",
            poster = "",
            seen = emptySet(),
            info = ShowInfo(ended = false, episodes = episodes),
        )

        val next = show.nextEpisode(today)

        assertNotNull(next)
        assertEquals(3, next!!.number)
        assertEquals("2026-03-15", next.airDate)
    }

    @Test
    fun testNextEpisodeReturnsNullWhenNoFutureEpisodesExist() {
        val pastEpisodes = listOf(
            Episode(season = 1, number = 1, title = "Ep 1", airDate = "2026-01-01"),
            Episode(season = 1, number = 2, title = "Ep 2", airDate = "2026-02-01"),
        )
        val show = Show(
            key = "al200",
            source = Source.ANILIST,
            id = "200",
            name = "Completed Show",
            poster = "",
            seen = emptySet(),
            info = ShowInfo(ended = true, episodes = pastEpisodes),
        )

        assertNull(show.nextEpisode(today))
    }
}

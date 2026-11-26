package com.mlw.serieustracker

import com.mlw.serieustracker.data.Episode
import com.mlw.serieustracker.data.episodeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeTest {

    @Test
    fun testEpisodeKeyFormatting() {
        assertEquals("1x1", episodeKey(1, 1))
        assertEquals("2x12", episodeKey(2, 12))
    }

    @Test
    fun testEpisodeHasAired() {
        val today = "2026-03-01"
        val pastEpisode = Episode(1, 1, "Past", "2026-02-28")
        val todayEpisode = Episode(1, 2, "Today", "2026-03-01")
        val futureEpisode = Episode(1, 3, "Future", "2026-03-02")
        val unknownEpisode = Episode(1, 4, "Unknown", "")

        assertTrue(pastEpisode.hasAired(today))
        assertTrue(todayEpisode.hasAired(today))
        assertFalse(futureEpisode.hasAired(today))
        assertFalse(unknownEpisode.hasAired(today))
    }
}

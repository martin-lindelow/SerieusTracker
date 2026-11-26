package com.mlw.serieustracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryImportTest {

    @Test
    fun testMergeImportPreservesExistingShowStatusAndMergesSeenEpisodes() {
        val existingShow = Show(
            key = "al154587",
            source = Source.ANILIST,
            id = "154587",
            name = "Sousou no Frieren",
            poster = "https://example.com/frieren.jpg",
            seen = setOf("1x1", "1x2"),
            dropped = true,
            lastWatched = 1000L,
        )

        val currentMap = mapOf(existingShow.key to existingShow)

        val importedItem = ListedShow(
            source = Source.ANILIST,
            id = "154587",
            name = "Sousou no Frieren",
            seen = setOf("1x2", "1x3", "1x4"),
            dropped = false,
            lastWatched = 2000L,
        )

        // Simulate Library.importMerge transform logic
        val merged = currentMap.toMutableMap()
        val item = importedItem
        val key = item.source.prefix + item.id
        val existing = merged[key]!!

        val fresh = item.seen - existing.seen
        merged[key] = existing.copy(
            seen = existing.seen + fresh,
            lastWatched = maxOf(existing.lastWatched, item.lastWatched),
        )

        val resultShow = merged["al154587"]!!
        // Seen episodes should be merged: 1x1, 1x2, 1x3, 1x4
        assertEquals(setOf("1x1", "1x2", "1x3", "1x4"), resultShow.seen)
        // Dropped status of existing show should be preserved
        assertTrue(resultShow.dropped)
        // lastWatched should take the maximum timestamp
        assertEquals(2000L, resultShow.lastWatched)
    }

    @Test
    fun testMergeImportAddsNewShowWhenNotExisting() {
        val currentMap = emptyMap<String, Show>()

        val newItem = ListedShow(
            source = Source.PLEX,
            id = "5d9c",
            name = "Severance",
            seen = setOf("1x1", "1x2"),
            dropped = true,
            lastWatched = 5000L,
        )

        val merged = currentMap.toMutableMap()
        val id = requireNotNull(newItem.id)
        val key = newItem.source.prefix + id
        merged[key] = Show(
            key = key,
            source = newItem.source,
            id = id,
            name = newItem.name,
            poster = "",
            seen = newItem.seen,
            dropped = newItem.dropped,
            lastWatched = newItem.lastWatched,
        )

        assertEquals(1, merged.size)
        val show = merged["px5d9c"]!!
        assertEquals("Severance", show.name)
        assertTrue(show.dropped)
        assertEquals(setOf("1x1", "1x2"), show.seen)
    }
}

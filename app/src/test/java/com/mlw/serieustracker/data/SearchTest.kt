package com.mlw.serieustracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SearchTest {

    @Test
    fun testSearchResultKeyGeneration() {
        val animeResult = SearchResult(
            source = Source.ANILIST,
            id = "154587",
            name = "Sousou no Frieren",
            poster = "https://example.com/poster.jpg",
            subtitle = "2023 · TV",
        )
        assertEquals("al154587", animeResult.key)
        assertEquals(Source.ANILIST, animeResult.source)
        assertEquals("154587", animeResult.id)
        assertEquals("Sousou no Frieren", animeResult.name)

        val plexResult = SearchResult(
            source = Source.PLEX,
            id = "5d9c",
            name = "Severance",
            poster = "https://example.com/severance.jpg",
            subtitle = "2022",
        )
        assertEquals("px5d9c", plexResult.key)
        assertEquals(Source.PLEX, plexResult.source)
        assertEquals("5d9c", plexResult.id)
    }

    @Test
    fun testFollowingSearchResultCreatesShowInLibraryMap() {
        val result = SearchResult(
            source = Source.ANILIST,
            id = "12345",
            name = "Cowboy Bebop",
            poster = "https://example.com/bebop.jpg",
            subtitle = "1998",
        )

        val show = Show(result.key, result.source, result.id, result.name, result.poster)
        val libraryMap = mapOf(show.key to show)

        assertNotNull(libraryMap["al12345"])
        assertEquals("Cowboy Bebop", libraryMap["al12345"]?.name)
        assertEquals(Source.ANILIST, libraryMap["al12345"]?.source)
    }
}

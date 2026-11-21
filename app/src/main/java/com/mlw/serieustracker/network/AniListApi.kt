package com.mlw.serieustracker.network

import com.mlw.serieustracker.data.AIRED_DATE_UNKNOWN
import com.mlw.serieustracker.data.Episode
import com.mlw.serieustracker.data.RemoteShow
import com.mlw.serieustracker.data.SearchResult
import com.mlw.serieustracker.data.ShowInfo
import com.mlw.serieustracker.data.Source
import com.mlw.serieustracker.data.objects
import com.mlw.serieustracker.data.str
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/** Anime metadata from AniList's public GraphQL API (no key required). */
object AniListApi {
    private const val ENDPOINT = "https://graphql.anilist.co"

    private const val SEARCH_QUERY =
        "query(\$q:String){Page(perPage:20){media(search:\$q,type:ANIME,sort:SEARCH_MATCH)" +
            "{id title{romaji english}coverImage{medium}startDate{year}format}}}"

    private const val DETAIL_QUERY =
        "query(\$id:Int){Media(id:\$id){title{romaji english}coverImage{medium}status episodes " +
            "nextAiringEpisode{episode airingAt}}}"

    suspend fun search(text: String): List<SearchResult> =
        query(SEARCH_QUERY, JSONObject().put("q", text))
            .getJSONObject("Page").getJSONArray("media").objects()
            .map { media ->
                val year = media.optJSONObject("startDate")?.optInt("year", 0) ?: 0
                SearchResult(
                    source = Source.ANILIST,
                    id = media.getInt("id").toString(),
                    name = title(media.getJSONObject("title")),
                    poster = media.optJSONObject("coverImage")?.str("medium").orEmpty(),
                    subtitle = listOfNotNull(year.takeIf { it > 0 }?.toString(), media.str("format").takeIf { it.isNotEmpty() })
                        .joinToString(" · "),
                )
            }

    suspend fun fetch(id: String): RemoteShow {
        val media = query(DETAIL_QUERY, JSONObject().put("id", id.toInt())).getJSONObject("Media")
        val total = media.optInt("episodes", 0)
        val next = media.optJSONObject("nextAiringEpisode")
        val nextNumber = next?.getInt("episode")
        val nextDate = next?.let {
            Instant.ofEpochSecond(it.getLong("airingAt")).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        }
        val status = media.str("status")
        val aired = when {
            status == "NOT_YET_RELEASED" -> 0
            nextNumber != null -> nextNumber - 1
            else -> total
        }
        val count = maxOf(total, aired, nextNumber ?: 0)
        // AniList only exposes the next airing date, so older episodes get a placeholder date.
        val episodes = (1..count).map { number ->
            val date = when {
                number <= aired -> AIRED_DATE_UNKNOWN
                number == nextNumber -> nextDate.orEmpty()
                else -> ""
            }
            Episode(season = 1, number = number, title = "", airDate = date)
        }
        return RemoteShow(
            name = title(media.getJSONObject("title")),
            poster = media.optJSONObject("coverImage")?.str("medium").orEmpty(),
            info = ShowInfo(ended = status == "FINISHED", episodes = episodes),
        )
    }

    private fun title(title: JSONObject): String = title.str("english").ifEmpty { title.str("romaji") }

    private suspend fun query(query: String, variables: JSONObject): JSONObject {
        val body = JSONObject().put("query", query).put("variables", variables).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(ENDPOINT).post(body).header("Accept", "application/json").build()
        val json = JSONObject(Http.text(request))
        json.optJSONArray("errors")?.let { throw IOException("AniList: " + it.getJSONObject(0).optString("message")) }
        return json.getJSONObject("data")
    }
}

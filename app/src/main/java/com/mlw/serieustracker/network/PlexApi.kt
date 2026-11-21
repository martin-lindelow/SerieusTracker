package com.mlw.serieustracker.network

import android.net.Uri
import com.mlw.serieustracker.data.Episode
import com.mlw.serieustracker.data.Prefs
import com.mlw.serieustracker.data.RemoteShow
import com.mlw.serieustracker.data.SearchResult
import com.mlw.serieustracker.data.ShowInfo
import com.mlw.serieustracker.data.Source
import com.mlw.serieustracker.data.objects
import com.mlw.serieustracker.data.str
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class NeedsLoginException : IOException("No Plex token stored")
class AuthExpiredException : IOException("Plex login expired")
class ServerAddressMissingException : IOException("No Plex server address saved")
class ServerUnreachableException(val address: String, cause: Throwable?) : IOException("Cannot reach server $address", cause)

/** Plex cloud metadata, plex.tv sign-in (PIN flow) and the user's own Plex Media Server (manual address). */
class PlexApi(private val prefs: Prefs) {

    data class Pin(val id: Long, val code: String)
    data class Server(val name: String, val url: String, val token: String)
    /** [lastWatched] is epoch millis of the most recent play of any episode of the show. */
    data class WatchedShow(val title: String, val guid: String, val episodes: List<Pair<Int, Int>>, val lastWatched: Long)

    // ---- Sign-in -------------------------------------------------------------------------

    suspend fun createPin(): Pin {
        val body = FormBody.Builder()
            .add("strong", "true")
            .add("X-Plex-Product", PRODUCT)
            .add("X-Plex-Client-Identifier", prefs.clientId)
            .build()
        val request = Request.Builder().url("https://plex.tv/api/v2/pins").post(body).header("Accept", "application/json").build()
        val json = JSONObject(Http.text(request))
        return Pin(json.getLong("id"), json.getString("code"))
    }

    /** The page where the user approves the sign-in. */
    fun authUrl(pin: Pin): String =
        "https://app.plex.tv/auth#?clientID=${Uri.encode(prefs.clientId)}&code=${Uri.encode(pin.code)}" +
            "&${Uri.encode("context[device][product]")}=$PRODUCT"

    /** Returns the auth token once the user has approved the PIN, otherwise null. */
    suspend fun pollPin(pin: Pin): String? {
        val url = "https://plex.tv/api/v2/pins/${pin.id}".toHttpUrl().newBuilder()
            .addQueryParameter("code", pin.code)
            .addQueryParameter("X-Plex-Client-Identifier", prefs.clientId)
            .build()
        val json = JSONObject(Http.text(Request.Builder().url(url).header("Accept", "application/json").build()))
        return json.str("authToken").ifEmpty { null }
    }

    // ---- Metadata (shows outside anime) ---------------------------------------------------

    suspend fun searchRaw(query: String, limit: Int = 20): JSONObject = cloud(
        "$DISCOVER/library/search",
        mapOf(
            "query" to query,
            "searchTypes" to "movies,tv",
            "searchProviders" to "discover",
            "limit" to limit.toString(),
            "includeMetadata" to "1",
        ),
    )

    suspend fun search(query: String): List<SearchResult> {
        val container = searchRaw(query).optJSONObject("MediaContainer") ?: return emptyList()
        val direct = container.optJSONArray("Metadata")?.objects().orEmpty()
        val nested = container.optJSONArray("SearchResults")?.objects().orEmpty()
            .flatMap { it.optJSONArray("SearchResult")?.objects().orEmpty() }
            .mapNotNull { it.optJSONObject("Metadata") }
        return (direct + nested)
            .filter { it.str("type") == "show" && it.str("ratingKey").isNotEmpty() }
            .map { item ->
                SearchResult(
                    source = Source.PLEX,
                    id = item.str("ratingKey"),
                    name = item.str("title"),
                    poster = item.str("thumb").takeIf { it.startsWith("http") }.orEmpty(),
                    subtitle = item.optInt("year", 0).takeIf { it > 0 }?.toString().orEmpty(),
                )
            }
    }

    suspend fun fetch(id: String): RemoteShow = coroutineScope {
        val details = async { runCatching { cloud("$METADATA/library/metadata/$id").metadata().firstOrNull() }.getOrNull() }
        val seasons = cloud("$METADATA/library/metadata/$id/children").metadata().filter { it.optInt("index") > 0 }
        val episodes = seasons.map { season ->
            async {
                cloud("$METADATA/library/metadata/${season.str("ratingKey")}/children").metadata().map { item ->
                    Episode(season.getInt("index"), item.optInt("index"), item.str("title"), item.str("originallyAvailableAt"))
                }
            }
        }.awaitAll().flatten().filter { it.number > 0 }
        val info = details.await()
        RemoteShow(
            name = info?.str("title").orEmpty(),
            poster = info?.str("thumb")?.takeIf { it.startsWith("http") }.orEmpty(),
            info = ShowInfo(ended = false, episodes = episodes),
        )
    }

    // ---- The user's own server (watch history) -------------------------------------------

    /** Connects to the server address saved in Settings and checks that it answers. */
    suspend fun connectToServer(): Server {
        val token = prefs.plexToken.ifEmpty { throw NeedsLoginException() }
        val address = prefs.serverUrl.ifEmpty { throw ServerAddressMissingException() }
        try {
            pms(address, token, "/library/sections", timeoutSeconds = 8)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ServerUnreachableException(address, e)
        }
        return Server(address, address, token)
    }

    /** All episodes marked as watched in the server's TV libraries, grouped by show. */
    suspend fun watchedEpisodes(server: Server): List<WatchedShow> {
        class Group(val title: String, val guid: String) {
            val episodes = mutableListOf<Pair<Int, Int>>()
            var lastViewedAt = 0L
        }

        val sections = pms(server.url, server.token, "/library/sections").container()
            ?.optJSONArray("Directory")?.objects().orEmpty().filter { it.str("type") == "show" }
        val shows = LinkedHashMap<String, Group>()
        for (section in sections) {
            var start = 0
            while (true) {
                val items = pms(
                    server.url, server.token, "/library/sections/${section.str("key")}/all",
                    mapOf(
                        "type" to "4",
                        "unwatched" to "0",
                        "X-Plex-Container-Start" to start.toString(),
                        "X-Plex-Container-Size" to PAGE_SIZE.toString(),
                    ),
                ).container()?.optJSONArray("Metadata")?.objects().orEmpty()
                for (item in items) {
                    val season = item.optInt("parentIndex")
                    val number = item.optInt("index")
                    if (season <= 0 || number <= 0) continue
                    val guid = item.str("grandparentGuid")
                    val title = item.str("grandparentTitle")
                    val group = shows.getOrPut(guid.ifEmpty { title }) { Group(title, guid) }
                    group.episodes.add(season to number)
                    group.lastViewedAt = maxOf(group.lastViewedAt, item.optLong("lastViewedAt"))
                }
                if (items.size < PAGE_SIZE) break
                start += PAGE_SIZE
            }
        }
        return shows.values.map { WatchedShow(it.title, it.guid, it.episodes, it.lastViewedAt * 1000) }
    }

    // ---- Plumbing ------------------------------------------------------------------------

    private fun JSONObject.container(): JSONObject? = optJSONObject("MediaContainer")

    private fun JSONObject.metadata(): List<JSONObject> = container()?.optJSONArray("Metadata")?.objects().orEmpty()

    private fun request(url: String, token: String, params: Map<String, String> = emptyMap()): Request {
        val builder = url.toHttpUrl().newBuilder()
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        builder.addQueryParameter("X-Plex-Token", token)
            .addQueryParameter("X-Plex-Client-Identifier", prefs.clientId)
            .addQueryParameter("X-Plex-Product", PRODUCT)
        return Request.Builder().url(builder.build()).header("Accept", "application/json").build()
    }

    /** A call to plex.tv's metadata services. A 401/403 means the stored token is no longer valid. */
    private suspend fun cloud(url: String, params: Map<String, String> = emptyMap()): JSONObject {
        val token = prefs.plexToken.ifEmpty { throw NeedsLoginException() }
        try {
            return JSONObject(Http.text(request(url, token, params)))
        } catch (e: Http.StatusException) {
            if (e.code == 401 || e.code == 403) throw AuthExpiredException()
            throw e
        }
    }

    private suspend fun pms(
        base: String,
        token: String,
        path: String,
        params: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = 30,
    ): JSONObject {
        val http = Http.client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()
        return JSONObject(Http.text(request(base.trimEnd('/') + path, token, params), http))
    }

    private companion object {
        const val PRODUCT = "SerieusTracker"
        const val DISCOVER = "https://discover.provider.plex.tv"
        const val METADATA = "https://metadata.provider.plex.tv"
        const val PAGE_SIZE = 500
    }
}

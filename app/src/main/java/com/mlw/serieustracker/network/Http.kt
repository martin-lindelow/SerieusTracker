package com.mlw.serieustracker.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    class StatusException(val code: Int, val path: String, val body: String) : IOException("HTTP $code for $path")

    suspend fun text(request: Request, http: OkHttpClient = client): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw StatusException(response.code, request.url.encodedPath, body.take(200))
            body
        }
    }
}

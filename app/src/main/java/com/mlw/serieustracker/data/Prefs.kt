package com.mlw.serieustracker.data

import android.content.Context
import java.util.UUID

/** Settings. The Plex token is kept encrypted in [SecureStore]; everything else is non-sensitive. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(context)

    init {
        migrateLegacyToken()
    }

    var plexToken: String
        get() = secure.get(KEY_TOKEN).orEmpty()
        set(value) = secure.put(KEY_TOKEN, value)

    /** Address of the user's Plex server, entered manually. Empty means not configured. */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    /** Stable id that identifies this app installation to plex.tv. */
    val clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_CLIENT_ID, it).apply() }

    /** Earlier versions stored the token in plain text; move it into encrypted storage once. */
    private fun migrateLegacyToken() {
        val legacy = prefs.getString(KEY_TOKEN, null) ?: return
        if (legacy.isNotEmpty() && secure.get(KEY_TOKEN) == null) secure.put(KEY_TOKEN, legacy)
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val KEY_TOKEN = "plex_token"
        const val KEY_SERVER = "server_url"
        const val KEY_CLIENT_ID = "client_id"
    }
}

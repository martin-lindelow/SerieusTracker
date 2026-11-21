package com.mlw.serieustracker.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mlw.serieustracker.R
import com.mlw.serieustracker.network.AuthExpiredException
import com.mlw.serieustracker.network.Http
import com.mlw.serieustracker.network.NeedsLoginException
import com.mlw.serieustracker.network.ServerAddressMissingException
import com.mlw.serieustracker.network.ServerUnreachableException
import java.io.IOException

/** Text the view model can hand to the UI without needing a Context, so it is always shown in the current language. */
sealed interface UiText {
    class Res(@StringRes val id: Int, vararg val args: Any) : UiText
    class Raw(val value: String) : UiText
    class Join(val parts: List<UiText>) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args)
    is UiText.Raw -> value
    is UiText.Join -> parts.map { it.asString() }.joinToString(" ")
}

fun Throwable.toUiText(): UiText = when (this) {
    is NeedsLoginException -> UiText.Res(R.string.error_plex_login_needed)
    is AuthExpiredException -> UiText.Res(R.string.error_plex_expired)
    is ServerAddressMissingException -> UiText.Res(R.string.error_server_address_needed)
    is ServerUnreachableException -> UiText.Res(R.string.error_server_unreachable, address)
    is Http.StatusException -> UiText.Res(R.string.error_http, code, path)
    is IOException -> UiText.Res(R.string.error_network)
    else -> UiText.Res(R.string.error_generic, message.orEmpty())
}

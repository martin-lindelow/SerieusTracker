package com.mlw.serieustracker.ui

import com.mlw.serieustracker.R
import com.mlw.serieustracker.network.AuthExpiredException
import com.mlw.serieustracker.network.NeedsLoginException
import com.mlw.serieustracker.network.ServerAddressMissingException
import com.mlw.serieustracker.network.ServerUnreachableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiTextTest {

    @Test
    fun testThrowableToUiTextMapping() {
        val loginNeeded = NeedsLoginException().toUiText() as UiText.Res
        assertEquals(R.string.error_plex_login_needed, loginNeeded.id)

        val authExpired = AuthExpiredException().toUiText() as UiText.Res
        assertEquals(R.string.error_plex_expired, authExpired.id)

        val serverAddressNeeded = ServerAddressMissingException().toUiText() as UiText.Res
        assertEquals(R.string.error_server_address_needed, serverAddressNeeded.id)

        val unreachable = ServerUnreachableException("https://192.168.1.50:32400", null).toUiText() as UiText.Res
        assertEquals(R.string.error_server_unreachable, unreachable.id)
        assertEquals("https://192.168.1.50:32400", unreachable.args[0])
    }

    @Test
    fun testRawUiText() {
        val raw = UiText.Raw("Custom message")
        assertEquals("Custom message", raw.value)
    }

    @Test
    fun testJoinUiText() {
        val join = UiText.Join(listOf(UiText.Raw("Part 1"), UiText.Raw("Part 2")))
        assertEquals(2, join.parts.size)
        assertTrue(join.parts[0] is UiText.Raw)
        assertTrue(join.parts[1] is UiText.Raw)
    }
}

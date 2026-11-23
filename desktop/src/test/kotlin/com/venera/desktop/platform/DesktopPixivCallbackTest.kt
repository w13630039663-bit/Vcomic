package com.venera.desktop.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopPixivCallbackTest {

    @Test
    fun `accepts the pixiv custom scheme forwarded by Windows`() {
        val callback = "pixiv://account/login?code=abc123&state=s"
        assertEquals(callback, desktopPixivCallbackUrl(listOf("--proxy=x", callback)))
    }

    @Test
    fun `accepts the registered HTTPS redirect URI`() {
        val callback =
            "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback?code=abc123"
        assertEquals(callback, desktopPixivCallbackUrl(listOf(callback)))
    }

    @Test
    fun `does not turn an unrelated launch argument into a login callback`() {
        assertNull(desktopPixivCallbackUrl(listOf("--open=SETTINGS", "https://example.com/?code=x")))
    }
}

package com.venera.desktop.platform

/**
 * One browser callback handed back to the already-running desktop process.
 *
 * [id] is intentionally monotonic: the same callback URL must still wake the UI when a second
 * process forwards it, while `LaunchedEffect(url)` would silently ignore an equal string.
 */
internal data class DesktopPixivCallback(
    val id: Long,
    val url: String,
)

/**
 * Finds a Pixiv OAuth callback in command-line arguments.
 *
 * PixEz desktop accepts both the custom scheme emitted by the Pixiv app-login page and the HTTPS
 * redirect URI itself. We accept both shapes for the same reason: browsers differ in whether they
 * hand the custom scheme or the final HTTPS URL to the registered protocol handler.
 */
internal fun desktopPixivCallbackUrl(arguments: List<String>): String? =
    arguments.firstOrNull(::looksLikePixivCallback)

private fun looksLikePixivCallback(raw: String): Boolean {
    val text = raw.trim()
    if (text.isEmpty() || !text.contains("code=", ignoreCase = true)) return false
    return text.startsWith("pixiv://account", ignoreCase = true) ||
        text.startsWith("venera://pixiv", ignoreCase = true) ||
        text.contains("/web/v1/users/auth/pixiv/callback", ignoreCase = true)
}

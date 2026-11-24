package com.venera.compose.data.platform

/**
 * 纯数据层向宿主上报非致命持久化异常的轻量钩子。
 * 宿主（如 `:desktop`）在启动时注入实现，避免下层模块反向依赖桌面总线。
 */
object PlatformDiagHook {
    @Volatile
    var reporter: ((category: String, headline: String, humanReason: String, detail: String, dedupKey: String) -> Unit)? = null

    fun report(
        category: String,
        headline: String,
        humanReason: String,
        detail: String,
        dedupKey: String,
    ) {
        reporter?.invoke(category, headline, humanReason, detail, dedupKey)
    }
}

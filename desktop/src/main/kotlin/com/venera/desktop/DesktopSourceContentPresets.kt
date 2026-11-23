package com.venera.desktop

import com.venera.desktop.platform.DesktopLogger

/**
 * 源级内容分级预设表 —— 桌面侧的 `sourcePresets`。
 *
 * ## 为什么需要它（安全评审 C）
 *
 * 安卓 `ContentGuardManager.maskHit` 的**第二道**判据就是这张表
 * （`ContentGuardManager.kt:441-443`）：
 *
 * ```kotlin
 * if (sourceKey != null) {
 *     val level = sourcePresets[resolveSourceKey(sourceKey)]
 *     if (level == "nsfw" || level == "mixed") return true
 * }
 * ```
 *
 * 桌面缺这一整道，于是**历史记录那种不带 tags 的模型永远判不出成人向**：
 * `HomeHomeWidgets.kt:165` 调 `coverMaskStateOf` 时只传
 * `sourceKey/title/author/comicId`，**不传 tags**，而原来的桌面判据只看 tags
 * ⇒ `looksAdult(emptyList())` 恒 false ⇒ **历史卡封面恒裸**。
 *
 * 这不是"判据更宽松"，是**少了一整道判据**。
 *
 * ## 数据从哪来
 *
 * `app/src/main/assets/source_content_warning.json`（3751 字节，按源 key 索引，
 * 三态 `safe` / `mixed` / `nsfw`，`defaultLevel` = `safe`）。
 * 桌面已把它加进 `desktop/build.gradle.kts` 的 resources include 清单 ——
 * 在此之前它**不在清单里**，所以桌面根本读不到。
 *
 * ## 解析为什么这么写
 *
 * 不引 JSON 库、不手搓完整解析器：这张表的形状是**扁平的**
 * （`"key": { "level": "...", … }`），两条线性正则取 `key → level` 就够，
 * 无嵌套量词（无 ReDoS 面）。
 *
 * **解析失败必须 fail-closed + 记可见诊断**：表读不到 / 解析 0 条时，宁可一律按
 * 「可能成人」打码（[isGuarded] 见 [lastError] 非空即返回 true），也绝不退回空表让
 * 本该遮的封面裸露。成因打到 stdout 的 `D_警告` 那一族（桌面没有通知条，那是既有落点），
 * [lastError] 与 `DesktopSourceContentPresetsTest` 一起钉住，不能静默 —— 静默最危险。
 */
internal object DesktopSourceContentPresets {

    /** 上一次解析的失败成因（`null` = 上次解析成功）。非空 = 这一道判据失效，[isGuarded] 走 fail-closed。 */
    @Volatile
    var lastError: String? = null
        private set

    /** `源 key → 等级`（`safe` / `mixed` / `nsfw`）。解析失败时是空表，但 [isGuarded] 不按空表放行。 */
    val levels: Map<String, String> by lazy { parse() }

    /**
     * 这个源要不要按"可能成人"处理。
     *
     * **fail-closed**：预设表读不到 / 解析 0 条（[lastError] 非空）时一律返回 true，
     * 让封面默认打码，而不是「表空 → 全放行」。多糊一张封面的代价远小于错放一张，
     * 这是内容安全方向，不能向"更松"的方向失败。
     *
     * `nsfw` 与 `mixed` 都算命中 —— 与安卓 `maskHit` 那句 `||` 逐字一致。
     */
    fun isGuarded(sourceKey: String): Boolean {
        val levelsSnapshot = levels
        if (lastError != null) return true
        return when (levelsSnapshot[sourceKey]) {
            "nsfw", "mixed" -> true
            else -> false
        }
    }

    /** 三个失败分支共用的唯一出口：记成因 + 打 stdout 警告 + 回空表（由 [isGuarded] 兜底）。 */
    private fun failClosed(reason: String): Map<String, String> {
        lastError = reason
        DesktopLogger.warn("内容守卫", "$reason；预设表失效，封面默认打码")
        DesktopErrorBus.report(
            category = "系统",
            headline = "内容分级预设表加载失败",
            humanReason = "安全起见已默认开启封面打码保护，请检查软件资源完整性。",
            detail = reason,
            dedupKey = "guard:presets_fail",
        )
        return emptyMap()
    }

    private fun parse(): Map<String, String> {
        lastError = null
        val text = try {
            val stream = DesktopSourceContentPresets::class.java
                .getResourceAsStream("/source_content_warning.json")
            if (stream == null) {
                // 资源清单漏了就会走到这里。
                return failClosed("包内找不到 source_content_warning.json（resources include 清单漏了？）")
            }
            stream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            return failClosed("读预设表失败：${t.javaClass.simpleName} ${t.message?.take(80)}")
        }

        // 必须从 "sources" 键之后开始匹配，避免外层标量键（version/updated）干扰首个条目正则。
        val anchored = text.substringAfter("\"sources\"", text)
        val out = LinkedHashMap<String, String>()
        val entry = Regex("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\\{([^}]*)\\}")
        val levelOf = Regex("\"level\"\\s*:\\s*\"(safe|mixed|nsfw)\"")
        for (m in entry.findAll(anchored)) {
            val key = m.groupValues[1]
            val level = levelOf.find(m.groupValues[2])?.groupValues?.get(1) ?: continue
            out[key] = level
        }

        return if (out.isEmpty()) {
            failClosed("预设表解析出 0 条 —— 文件形状变了？")
        } else {
            lastError = null
            out
        }
    }
}

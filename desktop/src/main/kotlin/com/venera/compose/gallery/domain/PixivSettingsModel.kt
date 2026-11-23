package com.venera.compose.gallery.domain

/**
 * pixiv 专属画质档位：卡片预览、作品/漫画详情页、大图看图器共用这一档。
 *
 * [SMALL] 走图床的尺寸前缀（同一张画、同一路径，只换前缀），用于格子小、要省流量的场合。
 */
enum class PixivImageQuality(val label: String) {
    SMALL("低画质"),
    MEDIUM("中画质"),
    LARGE("大图"),
    ORIGINAL("原图");
}

/**
 * pixiv 图床地址里的尺寸前缀（`/c/{宽}x{高}[_{质量}][_a2]/`）。
 *
 * 只认带质量位的写法：`/c/250x250/`（无质量位）实测 403，别照着拼。
 */
private val PIXIV_SIZE_PREFIX = Regex("""/c/\d+x\d+(?:_\d+)?(?:_a2)?/""")

/** 低画质那一档的尺寸前缀。无质量位的 `/c/250x250/` 会 403。 */
const val PIXIV_SMALL_SIZE_PREFIX: String = "/c/250x250_80_a2/"

/**
 * 同一张画的更小一档地址。
 *
 * @param url 站方给的地址；不是 pximg 或找不到尺寸前缀时原样返回，不编造未验证的形状
 */
fun pixivSmallUrlOf(url: String): String {
    if (url.isBlank() || !url.contains("pximg.net")) return url
    val hit = PIXIV_SIZE_PREFIX.find(url) ?: return url
    return url.replaceRange(hit.range, PIXIV_SMALL_SIZE_PREFIX)
}

/**
 * pixiv 网络连接模式。
 *
 * 分别用于：OAuth 授权登录、以及 API 数据与图片服务。
 */
enum class PixivNetworkMode(val label: String, val summary: String) {
    ECH("ECH", "通过 Encrypted ClientHello 绕过 SNI 审查"),
    COMPAT("兼容", "绕过 SNI 并使用 DoH"),
    STANDARD("标准", "标准连接，不使用绕过策略");
}

/**
 * pixiv 下载保存格式解析器。
 *
 * 支持占位符：{illust_id}、{part}、{title}、{user_id}、{artist_name}。
 */
object PixivSaveFormatHelper {
    const val DEFAULT_FORMAT = "{illust_id}_p{part}"

    val AVAILABLE_TOKENS = listOf(
        "作品ID" to "{illust_id}",
        "part" to "{part}",
        "标题" to "{title}",
        "用户ID" to "{user_id}",
        "画师名" to "{artist_name}",
    )

    fun format(
        template: String,
        illustId: Long,
        part: Int,
        title: String,
        userId: Long?,
        artistName: String,
        ext: String,
    ): String {
        val tpl = template.ifBlank { DEFAULT_FORMAT }
        val name = tpl
            .replace("{illust_id}", illustId.toString())
            .replace("{part}", part.toString())
            .replace("{title}", sanitize(title))
            .replace("{user_id}", userId?.toString() ?: "")
            .replace("{artist_name}", sanitize(artistName))
        val sanitized = sanitizeFileName(name)
        val cleanExt = ext.trimStart('.').ifBlank { "jpg" }
        return if (sanitized.isBlank()) "${illustId}_p${part}.$cleanExt" else "$sanitized.$cleanExt"
    }

    private fun sanitize(raw: String): String = raw
        .trim()
        .replace(Regex("[/\\\\:*?\"<>|]+"), "_")
        .trim('_', '.')

    private fun sanitizeFileName(raw: String): String = raw
        .trim()
        .replace(Regex("[/\\\\:*?\"<>|]+"), "_")
        .trim('_', '.')
}

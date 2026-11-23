package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Pixiv 图床（i.pximg.net）自定义反代域名配置与地址重写器。
 *
 * 仅在网络发包前改写域名，不改变上层传给图片缓存的原始地址，避免切换节点导致磁盘缓存全量失效。
 */
object DesktopPximgMirror {

    const val KEY_PXIMG_MIRROR = "pref_desktop_pximg_mirror"
    const val DIRECT = ""
    const val PIXIV_RE = "i.pixiv.re"
    private const val OFFICIAL_HOST = "i.pximg.net"

    private val _host = MutableStateFlow(DIRECT)

    /** 当前生效的反代域名；空串代表直连官方节点。 */
    val host: StateFlow<String> = _host.asStateFlow()

    /**
     * 冷启动从磁盘读取上次保存的反代域名配置。
     *
     * @param saved 磁盘中保存的原始域名文本。
     */
    fun restore(saved: String?) {
        _host.value = normalizeHost(saved.orEmpty())
    }

    /**
     * 规范化并写入反代域名，同时刷新内存流使其立即热生效。
     *
     * @param store 主偏好存储实例。
     * @param raw 用户输入的域名或完整 URL。
     */
    fun write(store: JsonKeyValueStore, raw: String) {
        val normalized = normalizeHost(raw)
        _host.value = normalized
        store.put(KEY_PXIMG_MIRROR, normalized)
    }

    /**
     * 清洗用户输入的域名文本，剥离协议前缀与路径片段。
     *
     * @param raw 原始输入字符串。
     */
    fun normalizeHost(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "直连") return DIRECT
        val withoutScheme = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
        val hostPart = withoutScheme.substringBefore('/').substringBefore('?').trim()
        if (hostPart.isEmpty() || hostPart.equals(OFFICIAL_HOST, ignoreCase = true)) {
            return DIRECT
        }
        return hostPart
    }

    /**
     * 将官方图床地址重写为反代地址；非官方图床地址或未配置反代时原样返回。
     *
     * @param url 原始图片或压缩包地址。
     * @param mirrorHost 目标反代域名，默认取当前生效配置。
     */
    fun rewrite(url: String, mirrorHost: String = _host.value): String {
        val normalized = normalizeHost(mirrorHost)
        if (normalized.isEmpty() || url.isBlank()) return url
        val parsed = url.toHttpUrlOrNull() ?: return url
        if (!parsed.host.equals(OFFICIAL_HOST, ignoreCase = true)) return url
        val colonIdx = normalized.lastIndexOf(':')
        val targetHost = if (colonIdx > 0) normalized.substring(0, colonIdx) else normalized
        val targetPort = if (colonIdx > 0) normalized.substring(colonIdx + 1).toIntOrNull() else null
        return runCatching {
            parsed.newBuilder()
                .host(targetHost)
                .apply {
                    if (targetPort != null && targetPort in 1..65535) {
                        port(targetPort)
                    } else if (parsed.scheme == "https") {
                        port(443)
                    } else if (parsed.scheme == "http") {
                        port(80)
                    }
                }
                .build()
                .toString()
        }.getOrDefault(url)
    }
}

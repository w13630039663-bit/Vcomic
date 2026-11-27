package com.venera.desktop.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 单主机线路延迟探测（首页「源状态」与「站点延迟检测」真测速）。
 *
 * 与 [DesktopLineProbe] 的分工：那颗钉 Gelbooru 一站、落盘当「上次测速」账；
 * 这一颗**无状态、按主机即发** —— 首页每回可见或用户点「重测」就对目标主机发 HTTP HEAD，
 * 并沿用当前软件代理口径（系统 / 直连 / 手动 HTTP 代理）。
 */
object DesktopSourceLatencyProbe {

    /** 延迟分档：≤800 正常（绿）/ ≤2500 偏慢（黄）/ 其余与失败 异常（红）。与源状态胶囊同一本账。 */
    const val GOOD_MS = 800L
    const val SLOW_MS = 2500L

    private val clientsByProxy = ConcurrentHashMap<String, OkHttpClient>()

    val client: OkHttpClient by lazy { clientFor(null) }

    private fun clientFor(proxy: String?): OkHttpClient {
        val key = proxy?.trim().orEmpty()
        return clientsByProxy.computeIfAbsent(key) { spec ->
            OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .apply {
                    when {
                        spec.equals("DIRECT", ignoreCase = true) || spec.equals("NO_PROXY", ignoreCase = true) -> {
                            proxy(Proxy.NO_PROXY)
                        }
                        spec.isNotBlank() -> {
                            val parts = spec.split(":")
                            val port = parts.getOrNull(1)?.trim()?.toIntOrNull()
                            if (parts.size == 2 && port != null && port in 1..65535) {
                                proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(parts[0].trim(), port)))
                            }
                        }
                    }
                }
                .build()
        }
    }

    /** 探一次：交回往返毫秒；发不出去交回 null（成因打一行 stdout，不上屏）。 */
    suspend fun probe(host: String, proxy: String? = null): Long? {
        val url = if (host.startsWith("http")) host else "https://$host"
        return withContext(Dispatchers.IO) {
            runCatching {
                val started = System.currentTimeMillis()
                clientFor(proxy).newCall(
                    Request.Builder().url(url).head().build(),
                ).execute().use { System.currentTimeMillis() - started }
            }.getOrElse {
                println("D_测速 主机=$host 塌了 ${it::class.java.simpleName}: ${it.message?.take(120)}")
                null
            }
        }
    }
}

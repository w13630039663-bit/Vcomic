package com.venera.desktop.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 单主机线路延迟探测（首页「源状态」真测速）。
 *
 * 与 [DesktopLineProbe] 的分工：那颗钉 Gelbooru 一站、落盘当「上次测速」账；
 * 这一颗**无状态、按主机即发** —— 首页每回可见就对全部行并行发一趟，读数只进内存。
 *
 * 口径三条：
 * - **任何 HTTP 响应都算通**（403/挑战也算到达）—— 量的是「线路到达要几毫秒」，
 *   不是站方答不答应；被 Cloudflare 挑战的主机照样有真延迟读数。
 * - 探测用**独立小客户端**（connect 3s / call 6s），不吃取图那台的缓存与拦截器，
 *   也不占引擎锁 —— 漫画源的主机是从脚本里提取的，直接打主机，不起引擎。
 * - 失败交回 null（屏上念「未通」），原文成因只进 stdout。
 */
object DesktopSourceLatencyProbe {

    /** 延迟分档：≤800 正常（绿）/ ≤2500 偏慢（黄）/ 其余与失败 异常（红）。与源状态胶囊同一本账。 */
    const val GOOD_MS = 800L
    const val SLOW_MS = 2500L

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /** 探一次：交回往返毫秒；发不出去交回 null（成因打一行 stdout，不上屏）。 */
    suspend fun probe(host: String): Long? {
        val url = if (host.startsWith("http")) host else "https://$host"
        return withContext(Dispatchers.IO) {
            runCatching {
                val started = System.currentTimeMillis()
                client.newCall(
                    Request.Builder().url(url).head().build(),
                ).execute().use { System.currentTimeMillis() - started }
            }.getOrElse {
                println("D_测速 主机=$host 塌了 ${it::class.java.simpleName}: ${it.message?.take(120)}")
                null
            }
        }
    }
}

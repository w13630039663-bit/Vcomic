package com.venera.engineprobe

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * 源更新（两步走的第 2 步）。
 *
 * ## 上游是哪
 *
 * 随包源头部本来就带着 `url = https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/<file>`
 * —— 这颗 `venera-configs` 仓库就是这些 .js 源的**上游**。更新 = 拉它的 `index.json`
 * （name/fileName/key/version 四栏）与本地对账，比 `version` 新的下载回来。
 *
 * ## 边界（不越权）
 *
 * 这颗件只做「对账 + 下载原文」两件事；**先验装载与落盘不在它手里** ——
 * `checkLoadable` 与 `DesktopSourceOverrides.writeText` 的判据留在调用点
 * （验不过不写，半截脚本是最难回头查的损坏）。
 *
 * ## 网络
 *
 * 与 `CfProbe` / `DesktopJsHost` 同一颗 OkHttp；代理吃调用点递进来的
 * `"host:port"` 字串（与 `--proxy=` 启动参数同一形状）。jsdelivr 在国内可能被墙，
 * 拉不到就交回空表，由调用点如实念成因 —— 不静默、不编造。
 */
object DesktopSourceUpdate {

    const val INDEX_URL = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/index.json"
    private const val RAW_BASE = "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Safari/537.36 Venera/1.0.0"

    /** 上游清单里的一行。`version` 空串 = 上游没声明（这一颗不参与更新对账）。 */
    data class Entry(
        val name: String,
        val fileName: String,
        val key: String,
        val version: String,
        /** 上游自己写的那句说明；没写就是 null（不编）。 */
        val description: String? = null,
    )

    /** 一颗可更新的源：`localVersion` 非空才有"从哪升到哪"那句话。 */
    data class Updatable(val entry: Entry, val localVersion: String)

    private fun client(proxy: String?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
        if (!proxy.isNullOrBlank()) {
            val parts = proxy.split(":")
            if (parts.size == 2) {
                val port = parts[1].toIntOrNull()
                if (port != null) {
                    builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(parts[0], port)))
                }
            }
        }
        return builder.build()
    }

    /**
     * 解析上游 `index.json`。entries 是统一的四栏对象，用正则抽（engine-probe 不引 JSON 库，
     * 四个键的形状固定，正则足够；坏块跳过不炸整表）。
     */
    fun parseIndex(text: String): List<Entry> =
        Regex("\\{[^{}]*\\}").findAll(text).mapNotNull { m ->
            val body = m.value
            fun field(key: String): String? =
                Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)
            val fileName = field("fileName") ?: return@mapNotNull null
            Entry(
                name = field("name") ?: fileName.removeSuffix(".js"),
                fileName = fileName,
                key = field("key") ?: fileName.removeSuffix(".js"),
                version = field("version").orEmpty(),
                description = field("description"),
            )
        }.toList()

    /** 语义版本比较：按 `.` 分段取数字比；相等/解析不出 = 不算新（不误更）。 */
    fun isNewer(remote: String, local: String): Boolean {
        if (remote.isBlank()) return false
        fun segments(v: String): List<Int> =
            v.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val a = segments(remote)
        val b = segments(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** 拉上游清单（拉不到交回空表，不抛）。 */
    fun fetchIndex(proxy: String?): List<Entry> = runCatching {
        client(proxy).newCall(
            Request.Builder().url(INDEX_URL).header("User-Agent", UA).build(),
        ).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            parseIndex(resp.body!!.string())
        }
    }.getOrDefault(emptyList())

    /** 下载单颗源原文（拉不到交回 null）。 */
    fun download(fileName: String, proxy: String?): String? = runCatching {
        client(proxy).newCall(
            Request.Builder().url(RAW_BASE + fileName).header("User-Agent", UA).build(),
        ).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body!!.string()
        }
    }.getOrNull()
}

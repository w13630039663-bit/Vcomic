package com.venera.desktop.gallery.data

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type
import java.security.MessageDigest

/**
 * pixiv 取数的**文件缓存**（「整个 pixiv 站全部页面都要做数据持久化，不然 api 限流」）。
 *
 * ## 形状
 *
 * 每个缓存键一个文件（键 sha256 + `.json`），内容是 `{ts, key, payload}` 信封：
 * payload 用 Gson 直写直读（pixiv 那批读模全是平凡 data class，反射往返够用；
 * 读回来的东西只进屏不进库，缺字段/类型漂了当"这份缓存坏了"丢掉即可）。
 * `key` 原文进信封是为了按前缀失隣（写穿：关注动了 ⇒ `following|` 全作废）。
 *
 * ## 谁在用
 *
 * 只有 [DesktopGalleryPixivWorks] 的**读**方法走它（TTL 各档写在那边）；
 * 写方法（关注/取关/收藏）不缓存、成功后按前缀失隣相关键。
 *
 * ## 为什么是文件而不是 SQLite
 *
 * pixiv 读模不带 `@Serializable` 也不在 data/db 的表单里；为缓存建两张表要动共享库 schema，
 * 而这里要的只是"整份 payload 原样睡一觉"——文件 + Gson 是最小形状，坏一个文件只丢一格缓存。
 */
internal class DesktopPixivCache(dir: File) {

    private data class Envelope(val ts: Long, val key: String, val payload: JsonElement?)

    private val gson = Gson()

    /** 目录懒建：第一次 put 才落盘，起窗零 IO。 */
    private val dir: File = dir.also { it.mkdirs() }

    private fun fileOf(key: String): File {
        // 摘要只作文件名，不参与完整性校验；旧版 SHA-1 命名的缓存不再命中，首轮自动重建。
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(dir, "$name.json")
    }

    private fun readEnvelope(key: String): Envelope? = runCatching {
        val f = fileOf(key)
        if (!f.isFile) return null
        gson.fromJson(f.readText(), Envelope::class.java)?.takeIf { it.key == key }
    }.getOrNull()

    /** 新鲜缓存（age ≤ ttl）；坏了/过期/键对不上 = null。 */
    fun <T> get(key: String, ttlMs: Long, type: Type): T? {
        val env = readEnvelope(key) ?: return null
        if (System.currentTimeMillis() - env.ts > ttlMs) return null
        return fromJson(env.payload, type)
    }

    /** 过期也交（先渲旧的再刷新那一半）；没有 = null。 */
    fun <T> getStale(key: String, type: Type): T? {
        val env = readEnvelope(key) ?: return null
        return fromJson(env.payload, type)
    }

    fun put(key: String, payload: Any?) {
        if (payload == null) return
        runCatching {
            fileOf(key).writeText(gson.toJson(Envelope(System.currentTimeMillis(), key, gson.toJsonTree(payload))))
        }
    }

    /** 按前缀失隣（写穿用：关注动了 ⇒ `following|`、`userIllusts|9289|` 全作废）。 */
    fun invalidatePrefix(prefix: String) {
        runCatching {
            dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { f ->
                val hit = runCatching {
                    gson.fromJson(f.readText(), Envelope::class.java)?.key?.startsWith(prefix) == true
                }.getOrDefault(false)
                if (hit) f.delete()
            }
        }
    }

    private fun <T> fromJson(payload: JsonElement?, type: Type): T? = runCatching {
        if (payload == null) return null
        gson.fromJson<T>(payload, type)
    }.getOrNull()
}

/** Gson 型令牌的便捷档（`tt<PixivUserPage>()`）。 */
internal inline fun <reified T> tt(): Type = object : TypeToken<T>() {}.type

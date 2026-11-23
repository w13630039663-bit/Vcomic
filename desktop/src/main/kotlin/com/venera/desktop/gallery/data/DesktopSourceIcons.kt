package com.venera.desktop.gallery.data

/**
 * 随包源图标：`assets/sources/<key>.js` 对应的站方标识，由
 * `docs/designs/site-sources-rework-2026-11-20/assets` 转制后随包分发。
 *
 * ## 为什么是 PNG 而不是抓到的原格式
 *
 * 抓到的大半是 `.ico`，Skia 不解 ICO；还有两枚扩展名是 `.ico` 实为 PNG。
 * 构建期已按「最大非空帧」统一转 PNG（少年Jump+ 的 256 帧是空占位，真图在 192 帧），
 * 运行时只管解码，不再分格式。
 *
 * ## 哪些没有
 *
 * 34 颗里有 8 颗拿不到站方标识（反爬返网页、favicon 空文件、连不上、自建服务），
 * 调用点拿不到就把显示名首字当图标——与「不编造站名」同一条纪律：没有就是没有。
 */
object DesktopSourceIcons {

    /** 源 key → 资源文件名。key 与 `assets/sources/<key>.js` 同名。 */
    private val FILE_OF = mapOf(
        "baozi" to "baozi.png",
        "ccc" to "ccc.png",
        "comic_walker" to "comic_walker.png",
        "comick" to "comick.png",
        "copy_manga" to "copy_manga.png",
        "copy_manga_multi_accounts" to "copy_manga.png",
        "ehentai" to "ehentai.png",
        "gelbooru" to "gelbooru.png",
        "goda" to "goda.png",
        "hcomic" to "hcomic.png",
        "hitomi" to "hitomi.png",
        "hot_manga" to "hot_manga.png",
        "jm" to "jm.png",
        "komga" to "komga.png",
        "komiic" to "komiic.png",
        "lanraragi" to "lanraragi.png",
        "manga_dex" to "manga_dex.png",
        "manhuaren" to "manhuaren.png",
        "manwaba" to "manwaba.png",
        "mh18" to "mh18.png",
        "mxs" to "mxs.png",
        "nhentai" to "nhentai.png",
        "picacg" to "picacg.png",
        "shonen_jump_plus" to "shonen_jump_plus.png",
        "wnacg" to "wnacg.png",
        "ykmh" to "ykmh.png",
        "zaimanhua" to "zaimanhua.png",
    )

    /** 有图标的源 key 清单（自检与文档用）。 */
    val keys: Set<String> get() = FILE_OF.keys

    /** classpath 资源路径；无图标 = `null`。 */
    fun resourceOf(key: String): String? = FILE_OF[key]?.let { "sources/icons/$it" }

    /** 图标原始字节；无图标或读失败 = `null`（调用点自己解码）。 */
    fun bytesOf(key: String): ByteArray? = resourceOf(key)?.let { path ->
        DesktopSourceIcons::class.java.getResourceAsStream("/$path")?.use { it.readBytes() }
    }
}

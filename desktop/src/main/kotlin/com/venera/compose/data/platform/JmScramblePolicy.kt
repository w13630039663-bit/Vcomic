package com.venera.compose.data.platform

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * JM（禁漫）图片混淆判据的**纯核** —— 机械拆分自 `data/network/ImagePipelinePolicy.kt`
 * （详情页收敛 /D， 路线）。
 *
 * ## 为什么要拆这一颗
 *
 * 那颗宿主文件整棵钉在 `data/network`（Android 面：位图解码 / `LruCache` / `Log`），
 * 而**共享详情页屏**（`ComicDetailScreen`）预览格要算的只是缓存键那一条判式
 * （URL + JM 块数）—— 这一族算式本来就纯 JVM（字符串、MD5、`ConcurrentHashMap`），
 * 当初和像素重排长在同一颗文件里纯属历史聚堆。屏进桌面编译面，判式必须先脱离禁引族。
 *
 * ## 单一来源怎么保（原则 3）
 *
 * 搬完后 `ImagePipelinePolicy.cacheKeyFor / getScrambleNum / calculateJmScrambleNum`
 * 逐颗改成**一行委托**转进这里 —— 全仓只剩这一份算式，宿主与共享屏读的是同一本账。
 * `scrambleRegistry` 也搬进来：注册与推导两条路（源脚本那份 / URL 推导那份）的汇合点
 * 只能有一个，留在宿主里再包一层就是两份表。
 *
 * ⚠️ 分叉告警（原 `registerScramble` 里那句 `Log.w`）在这里**不再出声**：
 * 宿主侧 `ImagePipelinePolicy.registerScramble` 仍先做对账再落表，日志留在 Android 面；
 * 屏侧只读不算账，不需要那个口子。
 */
object JmScramblePolicy {

    /** 运行时注册的 URL -> 混淆分块数（源脚本那份是权威口径）。 */
    private val scrambleRegistry = ConcurrentHashMap<String, Int>()

    /** 注册源脚本报的块数（>1 才记）。对账与告警留在宿主侧。 */
    fun register(url: String, num: Int) {
        if (num > 1) scrambleRegistry[url] = num
    }

    /** 已注册的块数（宿主对账日志用；屏侧不需要）。 */
    fun peekRegistered(url: String): Int? = scrambleRegistry[url]

    /**
     * 图片缓存 key：带块数的版本。
     * 详情页预览那批 URL 从没走过 `comic.onImageLoad`，只拿得到「URL 推导」的块数；
     * 万一它和源脚本那份不一致，两种还原结果会共用同一条内存缓存 —— 先加载的那个把
     * 后加载的锁死。key 里带上块数，两者各占一条，谁也不会覆盖谁。
     */
    fun cacheKeyFor(url: String): String {
        val num = getScrambleNum(url)
        return if (num > 1) "$url#jm$num" else url
    }

    /**
     * 判断该图片是否需要去混淆，返回分块数；<= 1 表示无需处理。
     */
    fun getScrambleNum(url: String): Int {
        scrambleRegistry[url]?.let { return it }

        // 针对禁漫 (JM) 图片地址自动推导混淆块数：
        // 典型路径: /media/photos/{epId}/{pictureName}.webp (或 .jpg/.png)
        if (isJmPhotoUrl(url)) {
            return calculateJmScrambleNum(url)
        }

        return 0
    }

    fun isJmPhotoUrl(url: String): Boolean {
        if (url.endsWith(".gif", ignoreCase = true)) return false
        val lower = url.lowercase()
        val hasPhotoPath = lower.contains("/media/photos/")
        val isJmDomain = lower.contains("jmapinode") ||
                lower.contains("jmapic") ||
                lower.contains("18comic") ||
                lower.contains("cdntwice.org") ||
                lower.contains("cdnsha.org") ||
                lower.contains("cdnaspa.cc") ||
                lower.contains("cdnntr.cc") ||
                lower.contains("asjmapic")
        // JM CDN 动态分流节点（cdn-msp.jmapic.net 等）域名难以穷举：
        // 只要路径是 /media/photos/ 且域名命中任一已知 JM 段，或显式带 cdn-msp 前缀，
        // 一律按禁漫混淆图片处理（分块数再由 epId 算法精确判定，误判代价仅一次位运算）。
        return hasPhotoPath && (isJmDomain || lower.contains("cdn-msp"))
    }

    /**
     * 对齐 jm.js 中的 1:1 分块计算算法：
     * - epId < 220980 -> num = 0 (不混淆)
     * - epId < 268850 -> num = 10
     * - epId > 421926 -> MD5(epId + pictureName) 最后一位 charCode % 8 * 2 + 2
     * - 其他 -> MD5(epId + pictureName) 最后一位 charCode % 10 * 2 + 2
     */
    fun calculateJmScrambleNum(url: String): Int {
        return try {
            if (url.endsWith(".gif", ignoreCase = true)) return 0

            // 提取 epId 与 pictureName
            val afterPhotos = url.substringAfter("/media/photos/", "")
            if (afterPhotos.isEmpty()) return 0

            val parts = afterPhotos.split("/")
            if (parts.size < 2) return 0

            val epId = parts[0].toLongOrNull() ?: return 0
            // pictureName 必须无条件截去末尾 5 个字符（而非按扩展名切分），
            // 否则 .jpg（4 字符）会多截一位数字导致 MD5 块数算错、图像条状撕裂。
            val lastSlash = url.lastIndexOf('/')
            val nameEnd = (url.length - 5).coerceAtLeast(lastSlash + 1)
            val pictureName = url.substring(lastSlash + 1, nameEnd)

            val scrambleId = 220980L
            val num = when {
                epId < scrambleId -> 0
                epId < 268850L -> 10
                epId > 421926L -> {
                    val str = "$epId$pictureName"
                    val md5 = MessageDigest.getInstance("MD5").digest(str.toByteArray(Charsets.UTF_8))
                    val hex = md5.joinToString("") { "%02x".format(it) }
                    val charCode = hex.last().code
                    val remainder = charCode % 8
                    remainder * 2 + 2
                }
                else -> {
                    val str = "$epId$pictureName"
                    val md5 = MessageDigest.getInstance("MD5").digest(str.toByteArray(Charsets.UTF_8))
                    val hex = md5.joinToString("") { "%02x".format(it) }
                    val charCode = hex.last().code
                    val remainder = charCode % 10
                    remainder * 2 + 2
                }
            }
            if (num <= 1) 0 else num
        } catch (e: Exception) {
            // 原宿主这里是一条 `Log.w` —— 纯核不引 Android 日志：宿主委托那层会兜在对账路径上，
            // 而这条 catch 只在 URL 畸形时走到，返回值语义（0 = 不处理）逐字不变。
            0
        }
    }

    /** EH 雪碧图裁剪参数（`@x=a-b&y=c-d` 那一串）。宿主与 fetcher 共用同一份形状。 */
    data class CropRange(val x1: Int, val x2: Int, val y1: Int, val y2: Int)

    fun parseCropRange(spec: String): CropRange? {
        var x1 = 0
        var x2 = Int.MAX_VALUE
        var y1 = 0
        var y2 = Int.MAX_VALUE
        var matched = false

        val parts = spec.split('&')
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("x=")) {
                val range = trimmed.removePrefix("x=").split('-')
                if (range.size == 2) {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start != null && end != null) {
                        x1 = start
                        x2 = end
                        matched = true
                    }
                }
            } else if (trimmed.startsWith("y=")) {
                val range = trimmed.removePrefix("y=").split('-')
                if (range.size == 2) {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start != null && end != null) {
                        y1 = start
                        y2 = end
                        matched = true
                    }
                }
            }
        }
        return if (matched) CropRange(x1, x2, y1, y2) else null
    }

    /**
     * 这笔图片请求要不要由字节管道接管（宿主 fetcher 的接管闸与阅读器读数共用）。
     * 判据必须与 [com.venera.compose.data.network.VeneraImageFetcher] 内部分支同源，
     * 避免出现"接管了但不处理"或"处理时没接管"。
     */
    fun needsBytePipeline(url: String): Boolean =
        url.contains("@x=") || url.contains("@y=") || getScrambleNum(url) > 1
}

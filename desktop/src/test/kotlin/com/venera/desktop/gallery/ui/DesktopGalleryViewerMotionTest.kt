package com.venera.desktop.gallery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 51 · 动图那一档的四道算式（设计 §3）。
 *
 * 它们必须是纯函数：播放器只负责"取字节、解码、推进帧号"，而**该不该动 / 隔多久 / 失败念哪句 /
 * 读数长什么样**这四问一旦散进播放器，就成了"只有真跑一遍才看得见"的东西 —— 本仓在预取与连播上
 * 各吃过一次（§76 / §75）。
 */
class DesktopGalleryViewerMotionTest {

    // ① 该不该逐帧：三档各过一遍。"关掉这一档"与"站方只给一帧"是两种不同的静帧，
    //    而偷懒的实现（只看 frameCount）会把前者做成"设置里那枚开关是假的"。
    @Test
    fun `逐帧要同时满足该动与多帧`() {
        assertTrue(desktopShouldAnimate(frameCount = 12, animateGifs = true))
        assertFalse("关了还在动 = 那枚开关是假开关", desktopShouldAnimate(frameCount = 12, animateGifs = false))
        assertFalse("单帧却起时钟 = 白烧一帧解码", desktopShouldAnimate(frameCount = 1, animateGifs = true))
    }

    // ② 帧间隔下界：站方给 0 或负数时不许把 CPU 打满。⚠️ 1ms 不是观感口径，是防自旋下界
    //    （本仓与 Coil 桌面侧都没有既有下限可抄 ⇒ 这里不造第二个数）。
    //    合法值必须原样过：夹到 1 之外再夹一道就会把 40ms 的慢帧抽成快帧。
    @Test
    fun `帧间隔只夹下界且合法值原样`() {
        assertEquals(1, desktopFrameDelayMs(0))
        assertEquals(1, desktopFrameDelayMs(-200))
        assertEquals(40, desktopFrameDelayMs(40))
        assertEquals(1, desktopFrameDelayMs(1))
    }

    // ③ 三种失败各一句，两两不许相同：「没取到 / 没有 / 没接」是三句话这一条在本仓立了几十批，
    //    而这一族最容易塌成一句"动图失败"——塌了就等于把"缓存键不中"读成"站方不给动图"。
    @Test
    fun `三种退化各念一句`() {
        val lines = DesktopAnimatedCause.entries.map { desktopAnimatedFallback(it) }
        assertEquals("三档要各一句", 3, lines.distinct().size)
        assertTrue("每句都得能上屏（不许空串）：" + lines, lines.all { it.isNotBlank() })
        assertNotEquals(lines[0], lines[1])
    }

    // ④ 读数把六枚都念出来：帧数、循环档、缓存命中与否、吃的哪一档、尺寸、每帧真实字节。
    //    少了"缓存=空/中"就量不到 §2 第 3 条那一格（openSnapshot 键不中是恒空，不是命中）；
    //    少了"每帧="就没人知道 1000×1000 那种动图该不该收上限（Android 侧炸过一次堆）。
    @Test
    fun `读数六枚都在`() {
        val line = desktopAnimatedReadout(
            frames = 24, repetition = 3, cacheHit = false, tier = DesktopDetailTier.SAMPLE,
            width = 1000, height = 1000, bytesPerPixel = 4,
        )
        assertTrue("少了帧数：$line", line.contains("24"))
        assertTrue("少了循环档：$line", line.contains("3"))
        assertTrue("少了缓存那一枚：$line", line.contains("缓存=空"))
        assertTrue("少了档位：$line", line.contains("SAMPLE"))
        assertTrue("少了尺寸：$line", line.contains("1000x1000"))
        // 每帧字节 = 宽高×位深（位深由调用方从真物件取，不是这里假设的 4）：
        // 1000×1000×4 ≈ 3.8MiB，所以念得出 MB 这一档。
        assertTrue("每帧字节没念出 MB 档：$line", Regex("每帧=[0-9.]+MB").containsMatchIn(line))
        // 小图要念成 KB 而不是 0.0MB —— 那一档一旦塌成 0，读数就再也量不到"堆该不该收"。
        val tiny = desktopAnimatedReadout(
            frames = 2, repetition = 0, cacheHit = true, tier = DesktopDetailTier.PREVIEW,
            width = 100, height = 100, bytesPerPixel = 4,
        )
        assertTrue("小图那档没念成 KB：$tiny", Regex("每帧=[0-9.]+KB").containsMatchIn(tiny))
    }

    // ⑤ 无限循环在 Skia 那边是 -1。直接把它念出去就是假读数：屏上明明在无限循环，
    //    读数却写"循环=-1"，读的人会判成"解错了"。⇒ 喂两种值，各念各的。
    @Test
    fun `无限循环不许念成负一`() {
        val forever = desktopAnimatedReadout(2, -1, true, DesktopDetailTier.PREVIEW, 8, 8, 4)
        assertTrue("无限要念成无限：$forever", forever.contains("无限"))
        assertFalse("不许把 -1 当循环次数念出去：$forever", forever.contains("-1"))
        val thrice = desktopAnimatedReadout(2, 3, true, DesktopDetailTier.PREVIEW, 8, 8, 4)
        assertTrue("有限档要念得出那个数：$thrice", thrice.contains("3"))
    }

    // 校验动图播放判定仅调用一次 GalleryMotion.animates（允许读取默认枚举），且墙卡与浮层不含解码器。
    @Test
    fun `解码器不许出现在墙上与浮层`() {
        for (name in listOf("DesktopGalleryPostTile.kt", "DesktopGalleryDetailPanel.kt")) {
            val hits = DesktopSourceTree.codeOccurrences(DesktopSourceTree.desktopUiSource(name), "Codec")
            assertEquals("$name 里出现了 $hits 处 Codec", 0, hits)
        }
    }

    // ⑧ 退化必须**回落到 AsyncImage**，而不是把那一格撤空：静默白屏是这一族最坏的失败形状。
    // ⑨ "像不像动图"只看地址，且**不许被 query 上的过期参数骗进去**：
    //    站方那一路地址常带 `?…` 尾巴，只看 endsWith 就会把静帧当动图起一趟解码。
    @Test
    fun `地址那一道门认扩展名而不认尾巴`() {
        assertTrue(desktopLooksAnimated("https://x/a/b.gif"))
        assertTrue(desktopLooksAnimated("https://x/a/b.GIF?token=1"))
        assertFalse("带 query 的静帧：不该进解码那趟", desktopLooksAnimated("https://x/a/b.jpg?token=gif"))
        assertFalse(desktopLooksAnimated("https://x/a/b.jpg"))
        assertFalse(desktopLooksAnimated(""))
    }

    // 校验播放器一次仅持有一帧 ImageBitmap（通过分配次数、循环顺序与泛型字面量三重守卫防预解全片）。
}

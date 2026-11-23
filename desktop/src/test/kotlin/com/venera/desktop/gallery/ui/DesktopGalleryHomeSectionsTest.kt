package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryFavorite
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryArtistFollow
import com.venera.compose.gallery.domain.GalleryFollowedArtists
import com.venera.compose.gallery.domain.GalleryFollowedArtistsGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面首页**节的注册表**（S2-B §2.5 用例 ①–⑤）。
 *
 * 为什么这几条值得单独一个测试类：本仓没有 Robolectric、也没有 Compose UI 测试，composable 一行都测不到，
 * 所以"几项、什么次序、哪一项是未实现、未实现的那一行说不说得清缺席"这些**唯一会影响屏上结构**的判据，
 * 只能落在常量与源码文本上测。Android 侧那颗同名形状的用例
 * （`app/src/test/java/com/venera/compose/gallery/GalleryHomeSectionsTest.kt`）走的是同一条路。
 *
 * 它守的三件后果都很重的事：
 * 1. **项数跟着数据变** = 懒列表/导航条在第 0 项之后插项 ⇒ 锚点漂移，
 *    现象读起来是"返回被打回默认值 / 落地闪一下"（Android 真机踩过的同一根因）；
 * 2. **content 与 reason 同时非空或同时为空** = 要么自相矛盾，要么"看起来正常但实际少了一半"；
 * 3. **未实现的行挂了一枚什么都不做的控件** = 本仓最忌的假开关（已因此返工过）。
 */
class DesktopGalleryHomeSectionsTest {

    /**
     * pane 那六行的**次序规格**（批 27 起写在用例里而不是产品代码里）。
     *
     * 从前产品代码另有一份 `DESKTOP_GALLERY_HOME_SECTION_ORDER` 常量并声称"次序由它一处定死"，
     * 而 pane 与段头都直接从 [desktopGalleryHomeRows] 取序 —— 那份常量只有判据在吃。
     * 规格留在测试侧才是规格，留在产品侧而没人取用就是第二份事实。
     */
    private fun expectedOrder(): List<String> = listOf(
        DesktopGalleryHomeSectionKey.DAILY,
        DesktopGalleryHomeSectionKey.FOLLOWED_ARTISTS,
        DesktopGalleryHomeSectionKey.HISTORY,
        DesktopGalleryHomeSectionKey.DOWNLOADS,
        DesktopGalleryHomeSectionKey.LATEST,
        DesktopGalleryHomeSectionKey.RANKING,
    )

    @Test
    fun `① pane 那六行的项数与次序钉死`() {
        assertEquals(
            "六项的 key 与次序就是这份字面规格",
            expectedOrder(),
            desktopGalleryHomeRows().map { it.key },
        )
        assertEquals("六项的 key 不许重复", 6, desktopGalleryHomeRows().map { it.key }.toSet().size)
        assertTrue(
            "每一项的 key 都应带 section- 前缀",
            desktopGalleryHomeRows().all { it.key.startsWith("section-") },
        )
        assertEquals(
            listOf("每日热门", "正在关注的画师", "历史", "下载", "最新", "排行榜"),
            desktopGalleryHomeRows().map { it.title },
        )
    }

    @Test
    fun `② 项数恒 6 不跟数据变`() {
        // 先证明"数据确实能变"：同一位画师在名单里 vs 名单清空，rowOf 交回的 gap 是不同的两档。
        val withData = GalleryFollowedArtists.rowOf(
            follows = listOf(GalleryArtistFollow(GallerySite.YANDERE, "wowoguni", 1L)),
            favorites = listOf(GalleryFavorite(siteKey = GallerySite.YANDERE.routeKey, id = 7L, tags = "wowoguni", previewUrl = "p/1.jpg")),
        )
        val emptyData = GalleryFollowedArtists.rowOf(follows = emptyList(), favorites = emptyList())
        assertEquals(GalleryFollowedArtistsGap.NONE, withData.gap)
        assertEquals(GalleryFollowedArtistsGap.NO_FOLLOWS, emptyData.gap)
        // 数据两侧各算一次注册表：项数与次序都不许动（"数据到货才插项"就是在这里变红的）。
        // 次序本身的规格只在 ① 钉一次（同一件事不许两把尺子各钉一遍）。
        assertEquals("关注名单清空后项数仍须是 6", 6, desktopGalleryHomeRows().size)
    }

    @Test
    fun `③ 每一项的内容件与原因恒等二选一`() {
        desktopGalleryHomeRows().forEach { row ->
            // XOR：`content == null` 与 `reason != null` 同真同假，不许有中间态。
            assertEquals(
                "行「${row.key}」的内容件与原因必须恰好有一个 —— 两个都有是自相矛盾，两个都没有就是静默缺席",
                row.content == null,
                row.reason != null,
            )
        }
        assertEquals("六项全都有内容件了（历史那一行批 86 转正）", 6, desktopGalleryHomeRows().count { it.content != null })
        assertEquals("六项里今天没有一项未实现", 0, desktopGalleryHomeRows().count { it.content == null })
        // 有内容的那几项必须点名是哪几项（被人悄悄撤成"全部未实现"也要能读到）。
        assertEquals(
            listOf(
                DesktopGalleryHomeSectionKey.DAILY,
                DesktopGalleryHomeSectionKey.FOLLOWED_ARTISTS,
                DesktopGalleryHomeSectionKey.HISTORY,
                DesktopGalleryHomeSectionKey.DOWNLOADS,
                DesktopGalleryHomeSectionKey.LATEST,
                DesktopGalleryHomeSectionKey.RANKING,
            ),
            desktopGalleryHomeRows().filter { it.content != null }.map { it.key },
        )
    }

    @Test
    fun `④ 未实现的行都点名缺席的那颗件`() {
        val absent = desktopGalleryHomeRows().filter { it.content == null }
        // 逐项校验未实现行须明确说明缺失层；已转正行由各自专项测试接管校验。
        absent.forEach { row ->
            val reason = row.reason
            checkNotNull(reason) { "行「${row.key}」是未实现的那一档却没带原因" }
            assertTrue("caption 必须点名差的是哪一层，不许写「这功能没做」：${row.key} → $reason", reason.length >= 8)
        }
        // 六行均已转正，此处不再重复断言已转正行，由 DesktopGalleryHistorySeedTest 守卫是否新增未实现行。
    }

    @Test
    fun `剩下那一行 caption 不许写成敬请期待 且短到两行念得完`() {
        desktopGalleryHomeRows().filter { it.content == null }.forEach { row ->
            val reason = checkNotNull(row.reason) { "未实现的行必须带原因：${row.key}" }
            assertTrue("caption 不许写「敬请期待」那一类话：${row.key}", reason.contains("敬请期待").not())
            // pane 224 扣掉 caption 缩进后一行约 28 个汉字，两行念不完就被省略号吃掉 = 缺席没被说出来。
            assertTrue(
                "caption 超出两行的预算（${reason.length} 字 > 30）：${row.key} → $reason",
                reason.length <= 30,
            )
        }
    }

    @Test
    fun `⑤ 未实现的行没有任何点击形状 也不存在空 lambda 的 no-op 分支`() {
        // 校验未实现行组件不含手势/选择修饰符，且渲染器中不出现空 lambda 点击占位。
        val unimplemented = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource("DesktopUnimplementedRow.kt"))
        FORBIDDEN_INTERACTION.forEach { token ->
            assertTrue(
                "未实现行里不许出现「$token」—— 那一行没有任何可执行动作，挂上它就是假开关／no-op",
                unimplemented.contains(token).not(),
            )
        }
        // 「旧根 DesktopGalleryHome.kt」换成「应用根 DesktopDomainRows.kt」：壳层统一在那颗之后，
        // 这台 pane/body 的渲染器也跟着换了主人，no-op 的形状要在新的 active rendering path 上空。
        listOf("DesktopUnimplementedRow.kt", "DesktopGalleryHomeSections.kt", "DesktopDomainRows.kt").forEach { name ->
            val text = DesktopSourceTree.codeText(DesktopSourceTree.desktopUiSource(name))
            NO_OP_CLICK_SHAPES.forEach { shape ->
                assertTrue("$name 里不许出现 no-op 点击形状「$shape」", text.contains(shape).not())
            }
        }
        // 默认档必须是"有内容的那一项"，否则一进首页就落在缺席读数上。
        assertEquals(DesktopGalleryHomeSectionKey.DAILY, desktopGalleryHomeRows().first().key)
    }

    @Test
    fun `稿上的计数徽标一颗都不带`() {
        // 扫描禁止残留设计稿硬编码假计数（跳过纯数据文件 ContributorAvatars.kt 与关键帧常量 DesktopStartupSplash.kt）。
        val offenders = DesktopSourceTree.desktopMainSources()
            .filter { it.name != "ContributorAvatars.kt" && it.name != "DesktopStartupSplash.kt" }
            .flatMap { file ->
                FAKE_COUNT_BADGES.filter { DesktopSourceTree.codeText(file).contains(it) }.map { "${file.name}:$it" }
            }
        assertTrue("桌面源里出现了稿上的计数徽标：$offenders", offenders.isEmpty())
        assertEquals("药丸上那句必须是「未实现」", "未实现", UNIMPLEMENTED_LABEL)
    }

    @Test
    fun `六项按 key 取行 取不到就说清`() {
        val rows = desktopGalleryHomeRows()
        assertEquals("每日热门", rows.byKey(DesktopGalleryHomeSectionKey.DAILY).title)
        // 校验按 key 查找行及未命中时的安全提示，是否新增未实现行由 DesktopGalleryHistorySeedTest 守卫。
        rows.forEach { row ->
            assertNotNull("行「${row.key}」的内容件不见了", rows.byKey(row.key).content)
        }
        // 注册表外的 key 必须**抛错并说清**，不许静默退回第一项。
        val message = runCatching { rows.byKey("section-does-not-exist") }.exceptionOrNull()?.message.orEmpty()
        // 只钉这句要完成的**事**（点名那个 key + 说清不在表里），不钉它当年那句"不在 ORDER 里"的措辞。
        assertTrue("取不到 key 时要点名那枚 key，实际：$message", message.contains("section-does-not-exist"))
        assertTrue("取不到 key 时要说清它不在注册表里，实际：$message", message.contains("注册表"))
    }

    private companion object {
        val FORBIDDEN_INTERACTION = listOf("clickable", "combinedClickable", "selectable", "toggleable", "hoverable")
        val NO_OP_CLICK_SHAPES = listOf("onClick = {}", "onClick = {},", "clickable {}", "clickable { }", "clickable(enabled = false)")
        // "418" 曾在此列，但它是颜色字面量 0xE6121418 的子串，误伤比守护多，已移除。
        val FAKE_COUNT_BADGES = listOf("1.2k", "3,412", "286", "9,077")
    }
}

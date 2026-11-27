package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「推荐漫画应该走 GoDa 漫画的热门更新组」的钉子尺。
 *
 * ## 为什么这一格非尺不可
 *
 * GoDa `explore(0, 1)` 回的是 `["近期更新"] / ["熱門更新"] / ["人氣排行"] / ["最新上架"]`。
 * 首页「推荐漫画 · 正常」从前取拷贝漫画最新组，现切为安全直连站 `goda` 的热门更新组；
 * 换组与点卡跳转 `sourceKey` 真相写在源码文本里，编译与离线语义树一条都不会红。
 *
 * ## 两条各挡一种漂法
 *
 *  ① 常驻取数腿必须点名 `it.title == "熱門更新" || it.title == "热门更新"`，且留着「拿不到就退回整页」的兜底
 *     （源形状改了宁可回到旧行为，也不许空屏）；
 *  ② 常驻腿 + 壳兜底两处都得是同一口径，标题如实念「推荐漫画（GoDa漫画 · 热门更新）」。
 */
class DesktopHomeRecommendSectionTest {

    private val shell = DesktopSourceTree.desktopLargeScreenCode()

    /** ① 常驻腿只取「熱門更新 / 热门更新」，但仍留整页兜底。 */
    @Test fun 推荐常驻腿只取热门更新组而不是全页摊平() {
        assertTrue(
            "首页推荐取数腿没有 `page.sections.firstOrNull { it.title == \"熱門更新\" || it.title == \"热门更新\" }` —— " +
                "可能混入了近期更新或排行榜：",
            shell.contains("""page.sections.firstOrNull { it.title == "熱門更新" || it.title == "热门更新" }"""),
        )
        assertTrue(
            "取不到「熱門更新」时仍要退回整页，别让源形状一变就空屏：",
            shell.contains("page.sections.flatMap { it.comics }.ifEmpty { page.comics }"),
        )
    }

    /** ② 两处喂屏口径一致，标题如实念 GoDa 热门更新，点卡跳转也落到 DESKTOP_HOME_RECOMMEND_SOURCE。 */
    @Test fun 两处喂屏口径一致且标题如实念热门更新() {
        assertEquals(
            "常驻腿 + 兜底都得是「熱門更新」这一组（任一处漂回摊平都会混进排行榜）：",
            2,
            Regex("""it\.title == "熱門更新"""").findAll(shell).count(),
        )
        val homePane = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("ui/DesktopHomePane.kt"))
        assertTrue(
            "推荐格标题得如实念「推荐漫画（GoDa漫画 · 热门更新）」：",
            homePane.contains("推荐漫画（GoDa漫画 · 热门更新）") || shell.contains("推荐漫画（GoDa漫画 · 热门更新）"),
        )
        assertTrue(
            "推荐漫画点卡进详情必须使用 DESKTOP_HOME_RECOMMEND_SOURCE（goda）：",
            shell.contains("if (homeRecommendR18) jmKey else DESKTOP_HOME_RECOMMEND_SOURCE"),
        )
    }
}

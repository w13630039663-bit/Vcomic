package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「推荐漫画应该走拷贝漫画的最新组」的钉子尺。
 *
 * ## 为什么这一格非尺不可
 *
 * 拷贝漫画 `explore(0, 1)` 回的是 `res["推荐"] / ["热门"] / ["最新"] / ["完结"] /
 * ["今日排行"] / ["本周排行"] / ["本月排行"]` 共 7 组。首页「推荐漫画」从前一句
 * `page.sections.flatMap { it.comics }` 把 7 组全摊平 —— 屏上照样出卡、照样能点进详情，
 * 编译与离屏语义树一条都不会红，只有**看内容**才知道推荐格里混着排行榜和完结榜。
 * 换组这件事跟换默认源一样，真相只写在源码文本里。
 *
 * ## 两条各挡一种漂法
 *
 *  ① 常驻取数腿必须点名 `it.title == "最新"`，且留着「拿不到就退回整页」的兜底
 *     （源形状改了宁可回到旧行为，也不许空屏）；
 *  ② 常驻腿 + 宽/窄两处喂屏兜底共 3 处都得是同一口径，标题如实念「拷贝漫画 · 最新」。
 */
class DesktopHomeRecommendSectionTest {

    private val shell = DesktopSourceTree.desktopLargeScreenCode()

    /** ① 常驻腿只取「最新」，但仍留整页兜底。 */
    @Test fun 推荐常驻腿只取最新组而不是七组全摊平() {
        assertTrue(
            "首页推荐取数腿没有 `page.sections.firstOrNull { it.title == \"最新\" }` —— " +
                "又退回把「推荐/热门/完结/三个排行榜」一起摊进这一格了：",
            shell.contains("""page.sections.firstOrNull { it.title == "最新" }"""),
        )
        assertTrue(
            "取不到「最新」时仍要退回整页，别让源形状一变就空屏：",
            shell.contains("page.sections.flatMap { it.comics }.ifEmpty { page.comics }"),
        )
    }

    /** ② 三处喂屏口径一致，标题如实。 */
    @Test fun 三处喂屏口径一致且标题如实念最新() {
        assertEquals(
            "常驻腿 + 双皮肤共享兜底都得是「最新」这一组（任一处漂回摊平都会混进排行榜/完结榜）：",
            2,
            Regex("""it\.title == "最新"""").findAll(shell).count(),
        )
        val homePane = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("ui/DesktopHomePane.kt"))
        assertTrue(
            "推荐格标题得如实念「拷贝漫画 · 最新」：",
            homePane.contains("推荐漫画（拷贝漫画 · 最新）") || shell.contains("推荐漫画（拷贝漫画 · 最新）"),
        )
    }
}

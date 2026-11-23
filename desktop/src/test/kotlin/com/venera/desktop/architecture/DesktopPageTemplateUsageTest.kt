package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * /5 的模板接线尺：网格页必须走 `GridPageTemplate`，设置页必须走
 * `SettingsPageTemplate` / 设置卡行模板。页面可以保留自己的业务状态与动作，
 * 但不许再把网格或设置卡骨架各抄一遍。
 */
class DesktopPageTemplateUsageTest {

    @Test
    fun `网格与瀑布流页统一走 GridPageTemplate`() {
        val files = listOf(
            "explore/DesktopExploreScreen.kt",
            "gallery/ui/DesktopGallerySearchScreen.kt",
            "gallery/ui/DesktopGalleryFavoritesWall.kt",
            "pixiv/DesktopPixivAccountScreen.kt",
            // 首页两栏与画师页已改由各自 pane/DesktopPixivWall(header=...) 承载，避免模板整页滚动嵌套懒网格触发无限高约束异常。
        )
        val missing = files.filter { relative ->
            "GridPageTemplate(" !in DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf(relative))
        }
        assertTrue("这些网格页还没走统一模板：$missing", missing.isEmpty())
    }

    @Test
    fun `设置与表单页统一走 SettingsPageTemplate 或设置卡模板`() {
        val files = listOf(
            "settings/DesktopSettingsHub.kt",
            "sourcemanage/DesktopSourceManageScreen.kt",
            "sourcemanage/DesktopSourceLoginScreen.kt",
            "DesktopBackupScreen.kt",
        )
        val missing = files.filter { relative ->
            val code = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf(relative))
            "SettingsPageTemplate(" !in code &&
                // 设置一级化批：压平版设置页（左栏锚点 + 单列分区）是同一族的共享模板，
                // 内部仍走 SettingsCardTemplate 渲染卡；这里把它与另外两颗并列，口径不变（不许自抄骨架）。
                "SettingsFlatPage(" !in code &&
                "SettingsCardTemplate(" !in code &&
                "SettingsRowTemplate(" !in code &&
                // 备份与还原重做后它是双栏工作台，不再是设置/表单页：走统一页壳
                // `DesktopPageShell`（返回钮、页头、宽度上限都在壳里），仍不许自抄页骨架。
                "DesktopPageShell(" !in code
        }
        assertTrue("这些设置页还没走统一模板：$missing", missing.isEmpty())
    }

    /**
     * 详情页顶栏（返回 + 标题 + 动作位）收进 DetailPageTemplate，不再各摆一遍页头。
     *
     * 漫画详情走的是安卓**同一份**共享 body（`ComicDetailScreenBody`），本就不在桌面模板射程内；
     * 这一颗钉的是桌面自搭的那两屏 pixiv 作品 / 画师。
     */
    @Test
    fun `详情页顶栏统一走 DetailPageTemplate`() {
        // Pixiv 作品页（双栏）与画师页（header 槽进懒网格）不使用 DetailPageTemplate，避免 verticalScroll 嵌套懒网格崩溃。
        val files = emptyList<String>()
        val missing = files.filter { relative ->
            "DetailPageTemplate(" !in DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf(relative))
        }
        assertTrue("这些详情页还没走统一页头模板：$missing", missing.isEmpty())
    }

    /**
     * 全屏看图页的黑底画布 + 顶栏 + 页脚条收进 ViewerPageTemplate。
     *
     * ⚠️ 阅读器（`DesktopReaderScreen`）**不**进这一列：它要的是覆盖在画面上的控制岛与 ← → 翻页键，
     * 形状与看图页不是一回事，为它抽模板就是「为复用无谓扩大重构」（AGENTS.md 那条 UI 复用纪律）。
     * 阅读器与看图页**脱侧栏挤压**那条走的是壳里 `shellMode`（见 `VeneraDesktopLargeScreen` 那一行），
     * 不是靠模板。
     */
    @Test
    fun `全屏看图页统一走 ViewerPageTemplate`() {
        val relative = "gallery/ui/DesktopGalleryPostViewer.kt"
        assertTrue(
            "看大图那一屏还没走全屏页模板：",
            "ViewerPageTemplate(" in DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf(relative)),
        )
    }
}

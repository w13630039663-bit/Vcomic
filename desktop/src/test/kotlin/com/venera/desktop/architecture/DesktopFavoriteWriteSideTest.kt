package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「详情页那枚收藏钮真的接到了写侧，而且没走那颗会误判的 `toggleFavorite`」。
 *
 * ## 这一格为什么值得单独立一颗
 *
 * 清单 §3 记的等待坑是这一族：`FavoriteDao` 的 `init { refresh() }` 是
 * `scope.launch { _favoritesFlow.value = … }`，**发起即返回**，而初值是 `emptyList()`。
 * 于是任何"拿 `_favoritesFlow.value` 判存在与否"的写法，在冷启动头几按都会把
 * "还没读回来"当成别的东西。收藏那一屏的读侧已经用 `first { isNotEmpty() }` 绕过去了；
 * **写侧此前根本没有**（详情页那句注释写着"后面接"）⇒ 库里 0 条收藏，
 * 那一屏的两档文案就永远只是文案，量不出谁在说话。
 *
 * ## 现在接的形状（三条各钉一条）
 *
 *  ① 装配点交的是 `isFavorite(comicId)`（真查库），**不是** `toggleFavorite`；
 *  ② 壳里按"查回来的那一档"显式选 add / remove，而不是把决定权交给 DAO 里那份内存缓存；
 *  ③ 写侧没接（`addFavorite == null`）时那枚钮**不摆**（评审 R6 `onRetest = {}` 那一族）。
 *
 * ## 为什么不是行为尺
 *
 * 真写库要 `DesktopDatabasePorts.bind`（落点、建表），而离屏 `runComposeUiTest` 渲的是屏本体、
 * 拿不到装配点那颗 lambda。屏上那四档文案 + 禁用态按不动由
 * `DesktopNewScreensSemanticsTest` 钉；**接线形状**由这里钉。
 */
class DesktopFavoriteWriteSideTest {

    private val shell = DesktopSourceTree.desktopLargeScreenCode()
    private val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
    // 批 3：详情屏拆成六颗成员文件，接线断言按成员表合并（`desktopComicDetailSources()` 就是拆分契约）。
    private val detail = DesktopSourceTree.desktopComicDetailCode()

    @Test
    fun 装配点查真值走库而不走内存缓存那一条() {
        val line = app.lines().firstOrNull { "isFavoriteOf = {" in it }
        assertTrue("`isFavoriteOf` 没在装配点接上（那屏上那枚钮就永远停在'正在读收藏…'）：", line != null)
        assertTrue(
            "查收藏走的不是 `favorite().isFavorite(comicId)`（真查库那一条）。实际：$line",
            "isFavoriteOf = { comicId -> favorite().isFavorite(comicId) }" in app,
        )
        // 反向：不许有人图省事把那颗带内存缓存的件接进来。
        val offenders = (shell.lines() + app.lines()).filter { "toggleFavorite(" in it }
        assertTrue(
            "桌面用上了 `FavoriteDao.toggleFavorite` —— 那颗拿 `_favoritesFlow.value` 判存在与否，" +
                "冷启动头几下会把'还没读回来'当成'已收过'，于是点「收藏」反而执行 DELETE：$offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun 写侧那一跳接的是add与remove两颗而不是整个DAO() {
        assertTrue("`addFavorite` 没接：", "addFavorite = { record -> favorite().addFavorite(record) }" in app)
        assertTrue("`removeFavorite` 没接：", "removeFavorite = { comicId -> favorite().removeFavorite(comicId) }" in app)
        // 与 `writeHistory` 同一条口径：只递动作，不把整颗 DAO 递进 UI（UI 不该能删全表）。
        assertFalse("把 `FavoriteDao` 整个递进了 UI：", "favorite()" in shell)
    }

    /** 壳里按查回来的那一档显式分叉（`wantAdd` 来自 `detailFavorited`，不是来自 DAO）。 */
    @Test
    fun 壳里按查回来那一档显式选add或remove() {
        // 详情页复用共享屏后，收藏写侧校验共享核 ComicDetailCore.toggleLocalFavorite 按查库状态显式增删。
        val core = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/ComicDetailCore.kt"))
        assertTrue("核里那一档不是「查回来的面板」而是 DAO 内存缓存：", "val wasAdded = folder in _favPanel.value.localAdded" in core)
        assertTrue("add 那一路没接上（`favoritesManager.addComic`）：", "favoritesManager.addComic(folder, comic.toFavoriteItem(key), null, currentUpdateTime())" in core)
        assertTrue("remove 那一路没接上（`favoritesManager.deleteComicWithId`）：", "favoritesManager.deleteComicWithId(folder, comic.id, key)" in core)
    }

    /** 写失败要把屏上那一档交给回查、把成因念出来，而不是悄悄翻一档。 */
    @Test
    fun 写失败时念成因而不留下假的那一档() {
        // ── 同上改判：写失败处理现在住在共享核的 `toggleLocalFavorite` 的 `onFailure` 分支 ──
        // 失败时不悄悄翻档：pending 那一格撤下、面板那一档交给回查、并念一句"这一步没成，再试一次"。
        val core = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/ComicDetailCore.kt"))
        val at = core.indexOf("fun toggleLocalFavorite(")
        assertTrue("读不到核里那颗 `toggleLocalFavorite`（那一处被改了而这里没跟上 ⇒ 判据失效，不是通过）：", at >= 0)
        val block = core.substring(at).take(1400)
        assertTrue("写失败那一路走的是 `onFailure`（而不是无条件翻档）：$block", ".onFailure {" in block)
        assertTrue("写失败要把 pending 那一格撤下（乐观反映不留在假的那一档）：$block", "pending = it.pending - folder" in block)
        assertTrue("写失败要把成因念出来：$block", "这一步没成" in block)
    }

    /** 从 `anchor` 那一行往后取 `span` 行（锚必须整行精确匹配，免得咬到同名的别处）。 */
    private fun blockAfter(anchor: String, span: Int): String {
        val lines = DesktopSourceTree.desktopLargeScreenLines()
        val at = lines.indexOf(anchor)
        assertTrue("读不到锚「$anchor」（那一处被改了而这里没跟上 ⇒ 判据失效，不是通过）", at >= 0)
        return lines.drop(at).take(span).joinToString("\n")
    }

    /**
     * 写侧真的接在核上，四档文案与禁用闸原样（改口径 · 详情页重建）。
     *
     * 旧稿钉的是旧仿制屏那枚「收藏」钮的形参形状（`onToggleFavorite: (() -> Unit)?` +
     * `if (onToggleFavorite != null)` 闸门）。详情页重建后写侧改走**核**：
     * `core.toggleLocalFavorite(comic, folder)` 是唯一落笔路，屏上那枚心形钮扳开
     * 收藏夹弹窗、点夹即落笔。这里钉三格：写口真接核、弹窗真摆、四句档名恰好四句
     * （禁用两档「还没读回来 / 正在写」照旧按不动，由
     * `DesktopFavoriteFoldersUiTest` ③④ 在语义树层量）。
     */
    @Test
    fun 写侧接在核上而四档文案与禁用闸原样() {
        assertEquals(
            "落笔路只许有一条（`core.toggleLocalFavorite`）：",
            1,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
                "core.toggleLocalFavorite(",
            ),
        )
        assertTrue("收藏夹弹窗没有摆（心形钮点了没去处）：", "title = \"收藏到\"" in detail)
        // 心形钮的禁用闸：还没读回来 / 正在写 那两档按不动。
        assertTrue("还没读回来 / 正在写 那两档必须禁用：", "enabled = data.favorited != null && !data.favoriteBusy," in detail)
        assertEquals(
            "那一枚钮的档名恰好四句：",
            4,
            listOf("正在写收藏…", "正在读收藏…", "已收藏", "收藏").count { "$it" in detail },
        )
    }

    /** 写进共享表的那一列口径：`sourceName` 吃 key，与桌面写历史、对岸 `toLegacyRecord` 同一条。 */

    /**
     * 清单 §3 那句「这条只被'照抄一个已验证的做法'支撑，没被实测支撑」里的那个等待写法，
     * 至今还是那一条（`first { isNotEmpty() }` + 超时回退），没被谁顺手简化成直接读 `.value`。
     */
    @Test
    fun 收藏读侧那条等待还在() {
        val block = app.lines().dropWhile { "readFavorites = {" !in it }.take(9).joinToString("\n")
        assertTrue("等一个非空的值那一句没了 ⇒ 会把'还没读回来'说成'没有收藏'：$block",
            "first { it.isNotEmpty() }" in block)
        assertTrue("超时回退没了 ⇒ 库确实空的时候白等：$block", "withTimeoutOrNull" in block)
    }
}

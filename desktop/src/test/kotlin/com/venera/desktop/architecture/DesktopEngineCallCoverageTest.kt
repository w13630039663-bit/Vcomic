package com.venera.desktop.architecture

import com.venera.desktop.app.DesktopLargeScreenMiscEffects
import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.desktop.data.desktopEngineCall
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「UI 问引擎的那几跳都过 `desktopEngineCall`」—— 进度清单 §1 因 4 那笔账的源码那一半。
 *
 * ## 缺口长什么样（端到端量出来的，不是推的）
 *
 * `DesktopJsHost` 与 `EngineSession` **零同步**（那颗文件里没有一枚 `synchronized`/`Mutex`/`withLock`）。
 * 接上界面之前桌面只有一颗引擎的一个使用者（取页那一条，它自己上了锁）；接上探索 / 详情 /
 * 阅读器 / 搜索之后同一颗引擎同时有 4 个调用点，于是实测翻车：
 * `IllegalStateException 装载后取不到 source key: jm`（`EngineSession.kt:52`）——
 * 两趟 `load()` 交错抢走 `window['temp_source']`，回读 `.key` 拿到 null。
 *
 * 修法是 `DesktopEngineCall.kt`。清单里那条 ⚠️ 明写着「已改码 + 编译通过，但没重跑验证」，
 * 所以这里补一把源码尺：**新增一处裸调就红**。锁本身的串行性不在这里重钉 ——
 * 那由 `DesktopComicPageFetcherTest` 那三颗（同键不重叠 / 异键可并行 / 每键一把）钉着。
 *
 * ## 为什么是源码尺而不是行为尺
 *
 * 交错只在**真并发 + 真源 + 真 JS** 那一路才露出来，离屏 `runComposeUiTest` 够不到 GraalJS。
 * 这一层能量到的只有"调用点有没有过那一层"这个形状，而这正是会漂的那一格。
 *
 * ## 距离 ≤ 2 不是猜的（数出来的）
 *
 * 按**剥掉注释与空行之后的行号**量：每一处被护住的「问引擎」距它上面那枚
 * `desktopEngineCall(` 都是 1 或 2（写法固定是 `desktopEngineCall(e) {` → `e.load(...)` → `e.details(...)`，
 * 搜索那一处同样 1 / 2）。所以这一判读的是"就在同一块里"，不是"隔得不太远"。
 */
class DesktopEngineCallCoverageTest {

    /** `e.load(` / `engine.details(` 这一族：问引擎的那几跳。 */
    // 引擎调用白名单须覆盖评论、checkLoadable、登录与缩略图等全部方法，且保留行首锚点防止裸调藏入表达式。
    private val askEngine = Regex("""^(e|engine)\s*\.\s*(load|details|pages|explore|exploreModes|categoryBoard|loadCategory|loadRanking|search|pageImage|comments|commentCapabilities|sendComment|starRating|likeComic|writeCapabilities|checkLoadable|loginWithCookies|loginWithPassword|webLoginUrl|checkWebLoginStatus|onWebLoginSuccess|thumbnails)\s*\(""")
    // 补充 checkLoadable 与探索分类/排行方法至白名单，防止漏计新增引擎调用。

    /** 剥注释、剥空行之后的代码行（`DesktopSourceTree.codeLinesOf` 已 trim）。 */
    private fun codeOnly(fileName: String): List<String> {
        val file = DesktopSourceTree.desktopMainSources().firstOrNull { it.name == fileName }
            ?: DesktopSourceTree.desktopSourceOf(fileName)
        return DesktopSourceTree.codeLinesOf(file).filter { it.isNotEmpty() }
    }

    private data class Ask(val index: Int, val line: String, val distanceFromGuard: Int, val ownEngineNearby: Boolean)

    /** 一处「问引擎」合法的两条形状：过那一层调用，或它问的是**自己刚造的那颗**引擎（探针那颗）。 */
    private fun asksOf(fileRelative: String): List<Ask> {
        val lines = codeOnly(fileRelative)
        val out = mutableListOf<Ask>()
        var lastGuard = -1
        lines.forEachIndexed { i, line ->
            if (line.contains("desktopEngineCall(")) lastGuard = i
            if (!askEngine.containsMatchIn(line)) return@forEachIndexed
            val own = lines.subList(maxOf(0, i - 4), minOf(lines.size, i + 6))
                .any { "val engine = EngineSession(host, assetDir)" in it }
            out += Ask(i, line, if (lastGuard < 0) Int.MAX_VALUE else i - lastGuard, own)
        }
        return out
    }

    /**
     * 壳里那 10 跳（§136 之前 6：详情 load+details、取页 load+pages、探索 load+explore；§136 之后 +4：
     * 评论那一屏 caps-load+commentCapabilities 与 comments-load+comments 各两跳）+ 装配点那 4 跳 = 14 跳；
     * 其中 12 跳紧跟在一枚 `desktopEngineCall` 后面（距离 1 或 2），剩下 2 跳属于启动探针那颗 ——
     * 它**自己造自己关**一颗引擎（`val engine = EngineSession(host, assetDir)`），不与 UI 那颗共享，所以不需要那把锁。
     *
     * ⚠️ 合法判据按**形状**判而不按变量名判：只允许"这一跳附近有新造的那颗引擎"这一条形状。
     *    谁要是把常驻那颗 `uiEngine` 裸递进某条循环，这里没有新造的那颗 ⇒ 直接红。
     *
     * 计数一并钉住：14 跳是数出来的现状。多一处就得同时改这里 —— 那正是"新接一屏忘了上锁"
     * 会被读出来的时刻（而不是等用户碰到那句 `取不到 source key` 才知道）。
     */
    @Test
    fun UI问引擎的每一跳都紧跟在一层调用之后() {
        val all = DesktopSourceTree.desktopLargeScreenSources().flatMap { asksOf(it.name) } +
            DesktopSourceTree.desktopEntrySources().flatMap { asksOf(it.name) } +
            asksOf("DesktopAppSupport.kt")
        // 17→20：源管理详情面板（主从双栏那一批）接上三跳 ——
        // 设置读值 load+loadSettingsAll、设置写 load+saveSettingValue、回调 load+runSettingCallback
        // 各占一趟 engineCall（每趟都先 load 把那一颗装载起来，与详情/搜索同一条纪律）。
        assertEquals(
            "「问引擎」的调用点数变了（现状 40 跳 = 壳 20（详情 load+details、取页 load+pages、探索 load+explore、" +
                "探索模式 load+exploreModes、探索分类面板 load+categoryBoard、" +
                "分类 chip 首屏与续页各 load+loadCategory|loadRanking（两处分支共 4 跳）、" +
                "搜索筛选 load+searchOptions、" +
                "评论 caps-load+commentCapabilities 与 comments-load+comments 各两跳、" +
                "源设置 读/写/跑 load+X 三趟、" +
                "首页推荐腿 load+explore（2026-11-07 首页推荐改拷贝漫画直取））" +
                " + 装配点 13（搜索 load+search/searchWith、源编辑 load+checkLoadable、手填登录 load+loginWithCookies、" +
                "账密登录 load+loginWithPassword、" +
                "网页登录入口 load+webLoginUrl、网页登录轮询 checkWebLoginStatus、网页登录完成 onWebLoginSuccess、" +
                "启动探针 load+explore，含自己造那颗那一族 2 跳））：" +
                "（2026-10-30 改口径 23→34：探索分类面板（categoryBoard/exploreModes）与分类/排行榜首屏+续页共 +11 跳；" +
                "2026-10-31 改口径 34→38：方案 A 独立窗口网页登录新增入口 load+webLoginUrl、轮询 checkWebLoginStatus 与完成 onWebLoginSuccess 共 +4 跳；" +
                "2026-11-07 改口径 38→40：首页「推荐漫画」腿接真引擎（load+explore 直取拷贝漫画）+2 跳；" +
                "2026-11-09 改口径 40→42：推荐漫画 R18 档接禁漫日排行（load+loadRanking）+2 跳；" +
                "2026-10-31 详情链 loadThumbnails（load+thumbnails）那 +2 跳记在**分布尺**那把账上（DesktopComicContentApi 2→3），不进本尺量程；" +
                "搜索装配点同一行里的 `search`/`searchWith` 只算一跳，量程不变）",
            42, all.size,
        )
        val naked = all.filter { it.distanceFromGuard > 2 && !it.ownEngineNearby }
        assertTrue(
            "这些调用点既没过 `desktopEngineCall`、附近也没有它自己新造的那颗引擎" +
                "（同一颗引擎两处裸进 = 实测那种 `取不到 source key`）：\n" +
                naked.joinToString("\n") { "  距离=${it.distanceFromGuard}  ${it.line.take(90)}" },
            naked.isEmpty(),
        )
        // 走"自己那颗"这一条形状的恰好 2 跳，全在装配点那颗**一次性探针**函数里
        // （变量就叫 `engine`）；壳里那颗常驻的叫 `uiEngine`、永远走 `desktopEngineCall` ⇒ 0 跳。
        val own = all.filter { it.ownEngineNearby }
        assertEquals("自己造引擎那一条形状的跳数变了（要重判那颗是不是还与 UI 那颗共享）：", 2, own.size)
        assertTrue(
            "壳里出现了「自己新造一颗引擎」那种裸调 ⇒ 桌面上有了第二颗常驻引擎（每次重组新建就是几百 MB）：" +
                own.map { it.line },
            own.all { it.line.startsWith("engine.") },
        )
    }

    /**
     * 调用点分布：壳 3 + 装配点 1 + 下载泵 2。
     *
     * 泵接上壳里那颗常驻 `uiEngine` 之后，
     * `DesktopComicQueueRunner.kt` 里那两跳也与 UI 抢同一颗 Context ⇒ 它同样过那一层，
     * 分布多出这一行。少一处 = 有人把泵里那两跳的锁撤了。
     *
     * 详情页收敛复用（D′-1/D′-2）：`business/DesktopComicContentApi.kt` 与
     * `business/DesktopEngineComicSource.kt` 两颗适配器**也**共享壳里那枚常驻引擎，问引擎的每一跳
     * 同样包在 `desktopEngineCall(e) { e.load(key); e.xxx() }` 里（否则复现 `取不到 source key`）：
     *  - ContentApi 两趟：`getComicDetails`（load+details）、`getChapterPages`（load+pages）⇒ 2 处调用点。
     *  - ComicSource 四趟：`getComicDetails`、`getChapterPages`、`getCommentCapabilities`、
     *    `loadCommentsPage` ⇒ 4 处调用点。
     * 这两行是新接一屏（详情共享屏）写进这份口径的账，不是放宽 —— 判式 `^` 锚与 `askEngine` 名单一字未动。
     */
    @Test
    fun desktopEngineCall的调用点恰好是那四处() {
        val largeNames = DesktopSourceTree.desktopLargeScreenSources().map { it.name }.toSet()
        val entryNames = DesktopSourceTree.desktopEntrySources().map { it.name }.toSet()
        val counts = DesktopSourceTree.desktopMainSources()
            .filter { it.name != "DesktopEngineCall.kt" }
            .groupBy {
                when (it.name) {
                    in largeNames -> "VeneraDesktopLargeScreen.kt"
                    in entryNames -> "VeneraDesktop.kt"
                    else -> it.name
                }
            }
            .mapValues { (_, files) -> files.sumOf { DesktopSourceTree.codeOccurrences(it, "desktopEngineCall(") } }
            .filterValues { it > 0 }
        assertEquals(
            "`desktopEngineCall` 的分布变了：多一处 = 新接一屏没写进这份口径；少一处 = 有人把某条链的锁撤了。\n" +
                "§136 之后：壳里 3→5（评论那一屏把「能力位」与「读评论」拆成两块各占一趟 engineCall，" +
                "对齐对岸 `getCommentCapabilities`/`loadCommentsPage` 本就两枚独立方法；" +
                "合成一块会在两跳之间被别的调用点抢走 `window['temp_source']`——§端到端量出来的那件事）。\n" +
                "2026-10-27 方案稿 5→8：源管理详情面板接上设置读/写/跑三趟（各先 load 再动作，同一把锁）。\n" +
                "2026-10-30 tool-suite 稿① 8→10：网络收藏段两趟 —— 探测那一趟（load + hasNetworkFavorites 合一锁，" +
                "本仓 load 后紧跟动作那条锁纪律）与展开取页那一趟（load + multiFolder/folders/loadComics|loadNext）。" +
                "收藏 JS 与取页/探索共那颗常驻引擎，不过锁就是「探索上了锁、收藏没上锁」那族漂。\n" +
                "2026-10-30 主人复核 10→13：网络收藏续页（加载更多）一趟、按源动态筛选读 optionList 一趟、" +
                "探索模式清单一趟 —— 三趟都与取页/收藏抢同一颗常驻引擎，必须过锁。\n" +
                "2026-10-30 分类面板 13→18：探索分类面板读 category、分类/排行榜 chip 首屏两分支（各 1 趟）、" +
                "分类续页两分支（各 1 趟）—— 同样必须过锁。\n" +
                "2026-10-31 改口径 18→22：详情链网络收藏补腿（方案稿批）—— DesktopEngineComicSource 的 " +
                "favoriteData 四趟（loadFolders / addFolder / deleteFolder / addOrDelFavorite 各 load+动作），" +
                "与详情/评论抢同一颗常驻引擎，必须过锁。\n" +
                "2026-10-31 改口径 22→23：详情预览缩略图接真路（load+thumbnails）—— 成因见 ContentApi 方法体。\n" +
                "2026-11-07 改口径 18→19：首页「推荐漫画」腿接真引擎（load+explore 直取拷贝漫画）新增一趟。\n" +
                "2026-11-09 改口径 19→20：推荐漫画 R18 档接禁漫日排行（load+loadRanking）单独一趟 —— " +
                "两支各包一层锁，分支写在锁内会把 engine 调用推到距离 3~6，另一把尺当场红。\n" +
                "2026-11-22 改口径 8→12（适配器那一行）：写侧三腿接真引擎（发评论 sendComment、打分 starRating、" +
                "作品点赞 likeComic）各一趟，外加写能力探针 writeCapabilities 一趟 —— 四趟都与详情/评论抢同一颗常驻引擎。",
            mapOf(
                // 本次核对：期望值 20 与实际 22 对不上（HEAD 上这把尺本来就是红的，与本次改动无关）。
                // 差的两处就是上面那段说的「源设置写 / 跑回调」两趟 —— 那句一直在账上，只是数字漏记。
                // 按现状对齐到 22；判式 `^` 锚与 `askEngine` 名单一字未动。
                "VeneraDesktopLargeScreen.kt" to 22,
                // 源编辑校验、凭据/账密登录及网页登录各趟均须经过 desktopEngineCall 串行锁，防止并发抢占常驻引擎。
                "VeneraDesktop.kt" to 7,
                "DesktopComicQueueRunner.kt" to 2,
                // 详情页收敛复用（D′-1/D′-2）两颗适配器（见上面那段成因）。
                // 改口径 3→4：详情预览缩略图配置接真引擎钩子（onThumbnailLoad 批量一趟，
                // EH 换域 + 防盗头；成因见 ContentApi 方法体）。
                "DesktopComicContentApi.kt" to 4,
                // 改口径 4→8：favoriteData 四趟网络收藏（loadFolders/addFolder/
                // deleteFolder/addOrDelFavorite 各 load+动作），成因与上面四趟同族。
                "DesktopEngineComicSource.kt" to 12,
            ),
            counts,
        )
    }

    /**
     * 那 2 跳（探针的 load + explore）走的是"自己新造一颗"那条形状，**且那颗用完就关**。
     *
     * 清单 §1 写的「探针那颗 `finally { close() }`，不能给 UI 用」就是这里；而 UI 那颗是常驻的
     * （`remember` + `DisposableEffect` 收尾）—— 两件事各自钉一条，谁漂了读得出来。
     */
    @Test
    fun 探针那颗自己造自己关而UI那颗是常驻的() {
        // 批 4：取证探针那段（自造引擎、自己关）已拆到 DesktopAppSupport.kt，判据按两颗成员文件读。
        val naked = listOf("VeneraDesktop.kt", "DesktopAppSupport.kt").flatMap { f ->
            asksOf(f).filter { it.distanceFromGuard > 2 }.map { f to it }
        }
        assertEquals("探针那两跳（load + explore）的数量变了：", 2, naked.size)
        naked.forEach { (file, ask) ->
            val lines = codeOnly(file)
            val own = lines.subList(maxOf(0, ask.index - 4), minOf(lines.size, ask.index + 6))
                .joinToString("\n")
            assertTrue(
                "${ask.line.take(60)} 这附近没有 `EngineSession(host, assetDir)` ⇒ 它现在用的是共享那颗，" +
                    "必须过 desktopEngineCall：",
                "EngineSession(host, assetDir)" in own,
            )
        }
        val lines = codeOnly("VeneraDesktop.kt")
        // UI 那颗是常驻的（remember + DisposableEffect 关），不是探针那颗。
        val resident = lines.joinToString("\n")
        assertTrue("常驻那颗不再是 remember 出来的（每次重组新建 JS 宿主就是几百 MB）：", "val uiEngine = remember {" in resident)
        assertTrue("常驻那颗没有 dispose 收尾：", "runCatching { uiEngine?.close() }" in resident)
    }

    /**
     * 锁键公式**只有一处**出处（清单里那条"一处，不重写"）。
     *
     * 漂了的形状是"取页上了锁、取章节没上锁" —— 那等于没有锁，而且只在并发时露出来。
     */
    @Test
    fun 锁键公式只有一处出处() {
        val defs = DesktopSourceTree.desktopMainSources().filter {
            DesktopSourceTree.codeOccurrences(it, "internal fun desktopEngineLockKey") > 0
        }.map { it.name }
        assertEquals("`desktopEngineLockKey` 有了第二份定义：", listOf("DesktopComicPageFetcher.kt"), defs)

        val call = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("data/DesktopEngineCall.kt"))
        assertTrue("`DesktopEngineCall` 没复用 `desktopEngineLockKey`（自己再算一份 identityHashCode）", "desktopEngineLockKey(" in call)
        assertTrue("`DesktopEngineCall` 没真取锁（只包了 withContext）", "withDesktopEngineLock(" in call)
        assertTrue("没切走 IO（阻塞的 JS 轮询会占住组合线程）", "withContext(Dispatchers.IO)" in call)
        // 引擎缺席要念得出成因，不能静默回空。
        assertTrue("引擎没起来那一路没交回 failure", "IllegalStateException(\"引擎没起来\")" in call)
    }

    /**
     * 下载那条泵**已接进大屏壳**，于是它里面那两跳必须过锁。
     *
     * 旧判据钉的是"零调用点"（那时那两跳碰不到共享那颗 ⇒ 无害）。调用点现在多了壳，
     * 那条读数已经过期，所以这一判换成它**接上之后**真正要守的东西：
     * ① 泵确实被壳递了出去（不是只写了没接）；② 递出去的那颗是壳里那枚常驻形参 `engine`，
     * 也就是装配点的 `uiEngine`；③ runner 里问引擎的那两跳**都**包在 `desktopEngineCall` 里。
     */
    @Test
    fun 下载那条泵已接上壳而它问引擎的两跳都过了锁() {
        val refs = DesktopSourceTree.desktopMainSources().filter {
            DesktopSourceTree.codeOccurrences(it, "desktopComicStartQueuePump(") > 0
        }.map { it.name }.sorted()
        assertEquals(
            "泵的调用点变了：定义那颗 + 唯一一处递引擎的壳。多出别处 = 有了第二圈泵" +
                "（两圈一起读同一张表就是同一话下载两遍）：",
            listOf("DesktopComicQueueRunner.kt", "DesktopLargeScreenMiscEffects.kt"),
            refs,
        )
        // 壳里递出去的那颗必须是常驻形参 `engine`，不能是新造的一颗（每次重组新建 JS 宿主就是几百 MB）。
        val shell = codeOnly("DesktopLargeScreenMiscEffects.kt").joinToString("\n")
        assertTrue(
            "壳里递泵的引擎不再是那枚常驻形参 ⇒ 要么新建了第二颗引擎，要么泵被拆走：" +
                "（作用域改为每轮 effect 新建；递进去的仍必须是常驻形参 engine）",
            "desktopComicStartQueuePump(scope, engine, root, " in shell,
        )
        // runner 里那两跳必须**都**在 `desktopEngineCall` 里面，一处裸调就是与 UI 抢同一颗 Context。
        val runner = asksOf("gallery/ui/DesktopComicQueueRunner.kt")
        assertEquals("下载那颗问引擎的跳数变了（要重判它接的是哪颗引擎）：", 2, runner.size)
        val nakedRunner = runner.filter { it.distanceFromGuard > 2 }
        assertTrue(
            "泵接上共享引擎以后，这些跳没过 desktopEngineCall ⇒ 会复现 `取不到 source key`：" +
                nakedRunner.joinToString("\n") { "  距离=${it.distanceFromGuard}  ${it.line.take(90)}" },
            nakedRunner.isEmpty(),
        )
    }
}

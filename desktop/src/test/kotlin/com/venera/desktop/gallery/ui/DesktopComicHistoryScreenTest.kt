package com.venera.desktop.gallery.ui

import com.venera.compose.data.db.HistoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 82 · 阅读历史那一屏的**读侧**判据（纯函数 + 源码扫描）。
 *
 * ## 两句抄对岸的话，一处公式抄对岸的算式
 *
 * 空态那两句与进度那一句都出自共享屏本体 `app/src/main/java/com/venera/compose/feature/HistoryScreenBody.kt`
 * （`VeneraEmptyView(title = …, message = …)` 那一块 + 那颗 `progressDescription()`）。
 * 阶段二把屏本体从 `HistoryScreen.kt` / `HistoryViewModel.kt` 抽进这颗文件后，它已在桌面编译面上
 * （`desktop/build.gradle.kts` 的 `feature/` 一片只点名排除 `HistoryScreen.kt`/`HistoryViewModel.kt`，
 * `HistoryScreenBody.kt` 不在排除之列），本屏那颗仿制件正是待退役的并存一份 —— 判据钉住"仿制的那一份
 * 与对岸原件没漂"，等真窗点过一轮再连这屏一起撤。
 *
 * ## 这一屏最容易被做成假的两件事
 *
 * - 读库失败画成空态：屏上「还没有阅读记录」是句实话吗？不是 —— 它是"我们没读到"。两档必须念两种话。
 * - 把别的源那一行也做成能点的样子：漫画域这一趟只装载了一个源（`config.sourceKey`），
 *   点了另一源的那本只会拿错的源去问同一个号 —— 那是拿 A 站的凭据问 B 站那一族的老账。
 */
class DesktopComicHistoryScreenTest {

    private fun record(
        chapterIndex: Int = 1,
        pageIndex: Int = 7,
        total: Int = 89,
        sourceName: String = "jm",
        comicId: String = "1461316",
    ) = HistoryRecord(
        comicId = comicId,
        title = "某本漫画",
        author = "",
        coverUrl = "",
        sourceName = sourceName,
        lastChapterTitle = "第 2 话",
        lastChapterIndex = chapterIndex,
        lastPageIndex = pageIndex,
        totalPages = total,
        updatedAt = 1_700_000_000_000L,
    )

    @Test
    fun 进度那一句与对岸那颗扩展函数同式() {
        // 对岸：`第 ${lastChapterIndex + 1} 话` 与 `第 ${lastPageIndex + 1} 页`，中间 ` - `，
        // 两截各带一枚 `>= 1` 的门（都关着时整句是空串）。四种组合各喂一次。
        assertEquals("第 2 话 - 第 8 页", desktopComicHistoryProgressText(record()))
        assertEquals("只有话号那一截时不留分隔符", "第 6 话", desktopComicHistoryProgressText(record(chapterIndex = 5, pageIndex = -1)))
        assertEquals("只有页号那一截", "第 1 页", desktopComicHistoryProgressText(record(chapterIndex = -1, pageIndex = 0)))
        assertEquals("两截都关着就是空串（调用方不摆那一格）", "", desktopComicHistoryProgressText(record(chapterIndex = -1, pageIndex = -1)))
    }

    @Test
    fun 对岸那颗算式改了这里要跟着红() {
        // 抄过来的东西一定会漂，所以把**原件**钉在判据里：对岸的算式一改，这条先红，而不是屏上悄悄不一样。
        val formula = DesktopSourceTree.androidSource("feature/HistoryScreenBody.kt").readText()
        listOf("lastChapterIndex + 1", "lastPageIndex + 1", "\"第 \$ep 话\"", "\"第 \$page 页\"", "\" - \"", "ep >= 1", "page >= 1")
            .forEach { token ->
                assertTrue("对岸那颗里不再出现「$token」—— 桌面这一句要跟着改判，不是悄悄留着：$token", formula.contains(token))
            }
        val emptyScreen = DesktopSourceTree.androidSource("feature/HistoryScreenBody.kt").readText()
        // 锚在 `VeneraEmptyView(` 那一整块上，不锚在"文件里第一个 title ="：
        // 那一屏别处也有 `title = "…"`，拿第一处会把顶栏题名当成空态那句（假绿）。
        val emptyView = Regex("VeneraEmptyView\\(\\s*title = \"([^\"]+)\"\\s*,\\s*message = \"([^\"]+)\"").find(emptyScreen)
            ?: error("对岸的空态不再是 `VeneraEmptyView(title = …, message = …)` 那一块")
        assertEquals("空态标题抄对岸那一句", emptyView.groupValues[1], DESKTOP_HISTORY_EMPTY_TITLE)
        assertEquals("空态副句抄对岸那一句", emptyView.groupValues[2], DESKTOP_HISTORY_EMPTY_MESSAGE)
    }

    @Test
    fun 读数那一行分得开空态与读不成() {
        // 批 82.8 起这一格多一枚 `名=`：那一行点不点得动由"键或显示名"两档决定，
        // 读数必须念得出当时拿哪两种东西在比（名字表没命中时那一格是空的，那也是一种形状）。
        assertEquals("D_历史 屏 源=jm 名=禁漫天堂 行=3", desktopComicHistoryScreenReading("jm", "禁漫天堂", 3, null))
        assertEquals("D_历史 屏 源=jm 名= 行=0", desktopComicHistoryScreenReading("jm", "", 0, null))
        val failed = desktopComicHistoryScreenReading("jm", "禁漫天堂", 0, "IllegalStateException: 端口没装")
        assertTrue("读不成必须念成因：$failed", failed.contains("因=IllegalStateException"))
        assertTrue("行数是几就念几（读不成时不许把 0 说成「没有记录」）：$failed", failed.contains("行=0"))
        assertEquals("读数不许带换行", 1, failed.lines().size)
    }

    @Test
    fun 首页漫画域那行从今天起点得动() {
        val rows = desktopComicRows()
        // 按 key 逐项校验设置中枢 6 个入口行，避免按索引断言在插入新行时漂移。
        assertEquals("漫画域从今天起六行（探索 / 收藏夹 / 阅读历史 / 源管理 / 本机已下载 / 下载队列）", 6, rows.size)
        val history = rows.first { it.key == "comic-history" }
        assertEquals("阅读历史", history.title)
        assertEquals("转正的那一行必须带内容件而不是理由", DesktopGallerySectionContent.ComicHistory, history.content)
        assertEquals("已转正的那一行不许再留「未实现」那句", null, history.reason)
        val sources = rows.first { it.key == "comic-sources" }
        assertEquals("站点", sources.title)
        assertEquals(DesktopGallerySectionContent.ComicSources, sources.content)
        assertEquals("源管理那一屏是真屏，不许挂「未实现」的理由", null, sources.reason)
        // 批 97 那一行：写侧在批 96 已经落地，这一行是它缺的**读侧**，所以它同样不许挂「未实现」。
        val downloads = rows.first { it.key == "comic-downloads" }
        assertEquals("本机已下载", downloads.title)
        assertEquals(DesktopGallerySectionContent.ComicDownloads, downloads.content)
        assertEquals("那一屏是真屏（数盘上那一棵树），不许留「未实现」的理由", null, downloads.reason)
    }

    @Test
    fun 两行历史各读各的那一份() {
        // 从前这一枚钉的是「图库那一行的理由不再念那一笔没写」—— 那一行点亮了，那句跟着撤，
        // 「转正了没」由 `DesktopGalleryHistorySeedTest` ①②③ 钉（同一件事只许一把尺子）。
        // 这里留下的是**只有这一枚管得着**的那一条：两行历史读的是两份不同的数据，不许谁并谁。
        val galleryHistory = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopGalleryBrowseHistory.kt"),
        )
        val comicHistory = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicHistory.kt"),
        )
        assertTrue("漫画那一颗不再写 comic_history 那张表了（并档的那一步就会把两件事记成一件）",
            comicHistory.contains("HistoryRecord"))
        assertTrue("图库那一颗不落自己那份 JSON 档了", galleryHistory.contains("gallery_browse_history.json"))
        assertFalse("图库那一颗开始写漫画那张表了（两张表不是同一件事）", galleryHistory.contains("comic_history"))
    }

    // 统一使用 desktopComicHistoryRowGate 返回四档门控状态，替代旧布尔函数以区分不可打开的具体原因。

    @Test
    fun 手机写的显示名在桌面这一侧算当前源() {
        // 对岸往 `comic_history.source_name` 写的是**显示名**（`VeneraReaderScreen.kt:445` 递的是
        // `session.sourceName`），而桌面从前只认键 ⇒ 手机记的那一行摆着而按不动。
        assertEquals(
            "库里写着「拷贝漫画」而当前源是 copy_manga ⇒ 这一行该点得动",
            DesktopComicHistoryRowGate.OPEN,
            desktopComicHistoryRowGate(record(sourceName = "拷贝漫画"), "copy_manga", "拷贝漫画"),
        )
    }

    @Test
    fun 键那一档照旧算当前源() {
        // 桌面自己写的是键（`DesktopComicHistory.kt:51`）—— 这一档不许被改坏。
        assertEquals(
            "桌面自己写的键那一档必须照旧放行",
            DesktopComicHistoryRowGate.OPEN,
            desktopComicHistoryRowGate(record(sourceName = "jm"), "jm", "禁漫天堂"),
        )
    }

    @Test
    fun 两档都吃大小写不敏感() {
        // 对岸那条口径是 `equals(…, ignoreCase = true)`，而随包 34 颗里就有两枚脚本键与文件名只差大小写
        //（`komiic → Komiic`、`manhuagui → ManHuaGui`）。
        assertEquals(
            "键那一档：", DesktopComicHistoryRowGate.OPEN,
            desktopComicHistoryRowGate(record(sourceName = "JM"), "jm", "禁漫天堂"),
        )
        assertEquals(
            "显示名那一档：", DesktopComicHistoryRowGate.OPEN,
            desktopComicHistoryRowGate(record(sourceName = "Komiic"), "komiic", "Komiic"),
        )
    }

    @Test
    fun 别颗源的显示名不许算成当前源() {
        assertEquals(
            "拿别的源的显示名冒充当前源，点下去就是拿错的源问同一个号",
            DesktopComicHistoryRowGate.OTHER_SOURCE,
            desktopComicHistoryRowGate(record(sourceName = "禁漫天堂"), "copy_manga", "拷贝漫画"),
        )
    }

    @Test
    fun 显示名缺位时不许把空串当通行证() {
        // 名字表没命中那一格是**退化输入**：这时只许按键认。写成 `record.sourceName.equals(sourceName, true)`
        // 而不挡空串，库里那行源名恰好也是空的话就被放行了。
        assertEquals(
            "库里空源名对上空显示名 ⇒ 那一档是「这一行没写源」而不是「别的源」",
            DesktopComicHistoryRowGate.ROW_SOURCE_MISSING,
            desktopComicHistoryRowGate(record(sourceName = ""), "jm", ""),
        )
        assertEquals(
            "库里空源名对得上键也一样不放行（空源名本身就是残缺行）",
            DesktopComicHistoryRowGate.ROW_SOURCE_MISSING,
            desktopComicHistoryRowGate(record(sourceName = ""), "jm", "禁漫天堂"),
        )
    }

    @Test
    fun 对岸那条键或显示名的口径改了这里要跟着红() {
        // 同轨不是嘴上说的：把对岸那两枚 `equals(…, ignoreCase = true)` 钉在判据里，它一改这条先红。
        // `resolveSourceKey` 随 VM 拆核搬进
        // `feature/ComicDetailCore.kt`（式子逐字没动，回核的**落点**变了）。
        val resolve = DesktopSourceTree.androidSource("feature/ComicDetailCore.kt").readText()
        listOf(
            "it.key.equals(sourceNameOrKey, ignoreCase = true)",
            "it.name.equals(sourceNameOrKey, ignoreCase = true)",
        ).forEach { token ->
            assertTrue("对岸 `resolveSourceKey` 不再含「$token」—— 桌面这一颗要跟着改判，不许悄悄留着：$token", resolve.contains(token))
        }
    }

    @Test
    fun 那句说明不再说启动而点名去哪儿换() {
        // 两笔假话：批 83 之后换源不必重启（源管理那一屏运行期就换），而 82.8 之后显示名的行本来就点得动。
        assertTrue("屏上不许再念「启动」：$DESKTOP_HISTORY_OTHER_SOURCE_NOTE", !DESKTOP_HISTORY_OTHER_SOURCE_NOTE.contains("启动"))
        assertTrue("要指出去哪儿换：$DESKTOP_HISTORY_OTHER_SOURCE_NOTE", DESKTOP_HISTORY_OTHER_SOURCE_NOTE.contains("站点"))
    }

    // ─────────── ：接上那一扇门时撞出来的三档 ───────────

    /**
     * ⑧ **源清单还没读回来**那一档不许塌进「别的源」。
     *
     * 这一档是这次接门时最容易写错的一笔：`comic_history.source_name` 两端写的不是同一种东西
     * （手机写显示名、桌面写键），而显示名那一档**要靠源清单**才认得出。清单还没到货的那一两帧里，
     * 若把"名字不在手"当成"这一行是别颗源的"，屏上就念出
     * 「这一行是别的源记的，去源管理换过来才打得开」—— 而用户本来就站在这颗源上，
     * 跑去源管理什么也换不出来。假话比"暂时判不了"更坏：它把用户支使去改一个没坏的东西。
     */
    @Test
    fun 名字不在手那一档不许念成别的源() {
        // 手机写显示名那一行 + 名字不在手 ⇒ 判不了，而不是"别源"。
        assertEquals(
            "库里写着显示名而名字表还没到货 ⇒ 那一档是「暂时判不了」：",
            DesktopComicHistoryRowGate.SOURCE_NOT_LOADED,
            desktopComicHistoryRowGate(record(sourceName = "拷贝漫画"), "copy_manga", null),
        )
        // 键那一档**不依赖**名字表：同一行、名字不在手，照样放行（这一条防的是"没名字就整屏判不了"）。
        assertEquals(
            "键那一档在名字不在手时照旧放行：",
            DesktopComicHistoryRowGate.OPEN,
            desktopComicHistoryRowGate(record(sourceName = "copy_manga"), "copy_manga", null),
        )
        // 名字在手（空串 = 那颗源没名字）时才是两档都不中 ⇒ 别源的。
        assertEquals(
            "名字在手而两档都不中 ⇒ 真的是别源的：",
            DesktopComicHistoryRowGate.OTHER_SOURCE,
            desktopComicHistoryRowGate(record(sourceName = "拷贝漫画"), "copy_manga", ""),
        )
        assertTrue(
            "「暂时判不了」那一句不许把用户支使去源管理（那是一句假话的处方）：$DESKTOP_HISTORY_SOURCE_NOT_LOADED_NOTE",
            !DESKTOP_HISTORY_SOURCE_NOT_LOADED_NOTE.contains("源管理"),
        )
    }

    /**
     * ⑨ 四档各有自己的一句话，而**得动的那一行什么都不多摆**。
     *
     * 为什么钉"各有一句"而不是"有一句"：三档按不动若共用一句，那"我还没读到"与"这本真是别源的"
     * 在屏上同形 —— 本仓那一族（还没读到 ≠ 确实没有）在历史这一屏的第三种宿主。
     * 为什么钉 `OPEN → null`：给得动的那行挂一句"打不开"的说明，比不挂更误导。
     */
    @Test
    fun 四档各有一句而得动那一档什么都不摆() {
        val notes = DesktopComicHistoryRowGate.entries.map { it to desktopComicHistoryRowGateNote(it) }
        assertEquals(
            "得动那一档不许多摆一句：", null,
            desktopComicHistoryRowGateNote(DesktopComicHistoryRowGate.OPEN),
        )
        val texts = notes.mapNotNull { it.second }
        assertEquals("四档要有三句不同的话（少一档就是有人把两种开不动并成一句）：", 3, texts.size)
        assertEquals("那三句必须互不相同：", 3, texts.distinct().size)
        // 逐档点名（只数颗数的话，谁被换成谁看不出来 —— 与那行历史"按索引点名"同一课）。
        assertEquals(DESKTOP_HISTORY_OTHER_SOURCE_NOTE, desktopComicHistoryRowGateNote(DesktopComicHistoryRowGate.OTHER_SOURCE))
        assertEquals(DESKTOP_HISTORY_SOURCE_NOT_LOADED_NOTE, desktopComicHistoryRowGateNote(DesktopComicHistoryRowGate.SOURCE_NOT_LOADED))
        assertEquals(DESKTOP_HISTORY_ROW_SOURCE_MISSING_NOTE, desktopComicHistoryRowGateNote(DesktopComicHistoryRowGate.ROW_SOURCE_MISSING))
        // 空源名那一档不许推给用户"你换错源了"：那是那一行自己残缺。
        assertTrue(
            "空源名那一句要念得出「这一行没写源」：$DESKTOP_HISTORY_ROW_SOURCE_MISSING_NOTE",
            DESKTOP_HISTORY_ROW_SOURCE_MISSING_NOTE.contains("没写"),
        )
    }

    /**
     * ⑩ **装载用的源键取当前那颗，而不是取库里那一枚。**
     *
     * 这是「历史 → 详情」那条路上最容易写错的一笔，而且**写错了不会红**：那一列里手机写的是显示名
     * （"拷贝漫画"），而 `EngineSession.load()` 认的是脚本键（`copy_manga`）⇒ 拿库里那一枚去装载
     * 就撞 `装载后取不到 source key`，屏上表现为"从历史点进去，一本都打不开"。
     * 看起来完全合理的写法（那一行自己写着哪颗源，就按它装载）在这里是错的，所以钉成判据。
     */
    @Test
    fun 按下某一行走的是当前那颗源键而不是库里那一枚() {
        // 手机写的显示名那一行：能开，而开的是**当前那颗键**。
        assertEquals(
            "显示名那一行交回的必须是脚本键而不是显示名：",
            "copy_manga",
            desktopComicHistoryOpenSourceOf(record(sourceName = "拷贝漫画"), "copy_manga", "拷贝漫画"),
        )
        // 键那一行同样交当前那颗（两形同值，避免"库里写的形"漏到装载那一层）。
        assertEquals(
            "copy_manga",
            desktopComicHistoryOpenSourceOf(record(sourceName = "copy_manga"), "copy_manga", "拷贝漫画"),
        )
        // 三档开不动的都交回 null：调用方**不许**猜一颗源去装载。
        listOf(
            record(sourceName = "禁漫天堂") to "别的源那一行",
            record(sourceName = "拷贝漫画") to "名字不在手那一行",
            record(sourceName = "") to "空源名那一行",
        ).forEach { (row, label) ->
            assertEquals(
                "$label 交回的必须是 null（判不了就不装载，猜一颗等于拿错的凭据问同一个号）：",
                null,
                desktopComicHistoryOpenSourceOf(row, "copy_manga", if (row.sourceName.isEmpty()) "拷贝漫画" else null),
            )
        }
    }

    /**
     * ⑪ 那一屏的**展示件只吃形参**：不许自己去开库、读 DAO、问引擎、引第二套组件库。
     *
     * 与「阅读历史那一屏零 IO」是同一条纪律的两头（那边管盘，这一头管库）：
     * 装载与展示一混，离屏语义树就渲不出来 —— 而渲不出来就没有判据，那正是这一屏空了 100 多批
     * 而一条判据都没红的成因（`DesktopDownloadsEntryTest` 头注记的那同一族）。
     */
    @Test
    fun 那一屏的展示件零库零DAO零引擎零Fluent() {
        val file = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicHistoryScreen.kt")
        val code = DesktopSourceTree.codeText(file)
        listOf(
            "HistoryDao" to "库口跑到屏里来了（读那张表的是外面那颗件）",
            "DatabasePorts" to "屏自己开库 ⇒ 离屏渲不出来",
            "EngineSession" to "屏不许问引擎：那一跳的账在壳与那颗锁上",
            "io.github.composefluent" to "又开出第二条平行线（Fluent 那批是已拆的残留）",
            "withContext" to "IO 跑到屏里来了",
            "Dispatchers" to "同上：线程调度不归展示件",
        ).forEach { (banned, why) ->
            assertEquals("$why —— 屏里不许出现 `$banned`：", 0, DesktopSourceTree.codeOccurrences(file, banned))
        }
        // 正面证据改钉共享面：屏本体退役后这颗文件只剩策略件，**永远不许有 @Composable**；
        // 屏在 `feature/HistoryScreenBody.kt`（共享面），那边才许有 @Composable。
        assertFalse(
            "策略件里出现了 @Composable ⇒ 有人把屏又搬回桌面这一侧（屏只在共享面）：",
            DesktopSourceTree.codeLinesOf(file).any { it == "@Composable" },
        )
        val body = DesktopSourceTree.androidSource("feature/HistoryScreenBody.kt")
        assertTrue(
            "共享面那颗屏本体不再有 @Composable ⇒ 历史屏没门了：",
            DesktopSourceTree.codeLinesOf(body).any { it == "@Composable" },
        )
    }

    /**
     * ⑫ "没有行"各念一句的**文案账**（仿制屏退役后的形状）：屏本体在共享面
     * `feature/HistoryScreenBody.kt`，这里只钉桌面独有的四档说明。
     *
     * 空态那两句（"还没有阅读记录 / 去随便翻两页吧"）退役后桌面侧只剩常量声明那一处，
     * **上屏出处已改钉共享面那颗屏本体**（上面第二条算式尺的 `VeneraEmptyView` 回核就是那一边）。
     * 如实登记的缺口：退役前屏里另有"正在读本地历史…"那一档（库还没读回来 ≠ 确实没有记录），
     * 共享 body 现在只有空态一档 —— 该行为级验证随未来假端口批补（交接文档 §8.5 缺口 1/2）。
     */
    @Test
    fun 那一屏备着三档没行的话() {
        val file = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicHistoryScreen.kt")
        val code = DesktopSourceTree.codeText(file)
        listOf(
            DESKTOP_HISTORY_OTHER_SOURCE_NOTE,
            DESKTOP_HISTORY_SOURCE_NOT_LOADED_NOTE,
            DESKTOP_HISTORY_ROW_SOURCE_MISSING_NOTE,
        ).forEach { needle ->
            assertTrue("策略件少了这一句 `$needle`：", code.contains(needle))
        }
        // 空态那两句的上屏唯一出处 = 共享屏本体（上面第二条算式尺回核的就是那一边）。
        val body = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/HistoryScreenBody.kt"))
        assertTrue("共享屏本体少了空态标题：", body.contains(DESKTOP_HISTORY_EMPTY_TITLE))
        assertTrue("共享屏本体少了空态副句：", body.contains(DESKTOP_HISTORY_EMPTY_MESSAGE))
        assertTrue(
            "空态那两句退役后只许各自那一枚常量的声明（出现第二次 = 有人把屏又搬回桌面侧）：",
            DesktopSourceTree.codeOccurrences(file, "DESKTOP_HISTORY_EMPTY_TITLE") == 1 &&
                DesktopSourceTree.codeOccurrences(file, "DESKTOP_HISTORY_EMPTY_MESSAGE") == 1,
        )
    }

    /**
     * ⑬ 那一行**带得走**进度那句话：库里存的行号会漂而话号不会，屏上仍念人话。
     *
     * 仿制屏退役后的形状：桌面这颗文件只留算式的**声明**（恰一次），屏不许在桌面侧调用它；
     * 上屏走共享面屏本体的 `record.progressDescription()`（一次声明 + 恰一次调用）——
     * 有人另拼一句进度就会红。算式本身由上面第一条逐字回核对岸。
     */
    @Test
    fun 那一屏把进度那句纯函数用上而不另写一句() {
        val file = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopComicHistoryScreen.kt")
        assertEquals(
            "那颗纯函数在桌面只剩「一次声明」（屏退役，出现第二次 = 有人又把屏搬回来）：",
            1,
            DesktopSourceTree.codeOccurrences(file, "desktopComicHistoryProgressText("),
        )
        assertEquals(
            "「第 N 话」那一句的字面只许出现在那颗算式里（别处再拼一遍就是两轨，一定会漂）：",
            1,
            DesktopSourceTree.codeOccurrences(file, "\"第 \$ep 话\""),
        )
        val bodyFile = DesktopSourceTree.androidSource("feature/HistoryScreenBody.kt")
        assertEquals(
            "对岸那句必须是「一次声明 + 一次上屏调用」（多一处调用 = 有人另起一轨）：",
            2,
            DesktopSourceTree.codeOccurrences(bodyFile, "progressDescription()"),
        )
    }
}

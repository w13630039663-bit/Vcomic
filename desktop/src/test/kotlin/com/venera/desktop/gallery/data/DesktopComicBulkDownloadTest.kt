package com.venera.desktop.gallery.data

import com.venera.engineprobe.ComicInfo
import com.venera.engineprobe.EngineSession
import com.venera.desktop.gallery.ui.DesktopSourceTree
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 漫画「批量/整本下载」的**决策层**。
 *
 * ## 这一批评的是哪一格
 *
 * 详情那一屏从起只有**单话**把手，整本 40 话要按 40 次。对岸那块
 * 「批量离线下载」（`app/…/feature/ComicDetailScreen.kt:1659-1782`）在桌面上从来没有对应物。
 * 判据面住 `gallery/data/DesktopComicBulkDownload.kt`（全纯函数），这里逐条钉它的语义，
 * 并且**吃真临时目录**：档位那三档来自读侧那颗件回读盘之后的结论，手拼的假数据量不到「归一没做」。
 *
 * ## 五件必须有牙的事
 *
 * 1. **归一**：盘上行的 `sourceKey`/`comicId` 是**净化形**（取自目录名），详情手上是**原始形**。
 * 不归一就是「盘上有却全判未下载 ⇒ 重下一遍」，那正是最容易留下的病（④ 那一颗直接撞它）。
 * 2. **半截那一档不能念成齐**：`PARTIAL` 必须进默认选中并挂「离线不全」（黄）。对岸
 *    `:1739-1741` 那条注释写的就是旧写法把它判成"目录里有图" ⇒ 挂绿 ⇒ 永不重下。
 * 3. **「清单说没齐而盘上其实齐了」那一支**：中断那趟把清单落回 `complete=false` 而页一颗都不删
 * ⇒ 那一档真实存在，判成缺页就是让用户重跑（③ 第四支钉它）。
 * 4. **「还没数回来」与「数不出来」都不许当成「本机都没有」**：那样弹窗会默认全选整本，
 *    用户按下就是明知故犯地重下一遍（⑤⑥⑦ 三档各钉一句）。
 * 5. **那一枚「全选未下载 ↔ 全不选」的判据是枚数相等**（对岸 `:1683`），而不是"我记没记得按过"。
 */
class DesktopComicBulkDownloadTest {

    private fun tempRoot(): File = Files.createTempDirectory("b117bulk-").toFile()

    /** 按**写侧那一条公式**造一话在盘上（目录名不对整条就认不出，所以必须共用那颗公式而不是手拼）。 */
    private fun chapterOnDisk(
        root: File, sourceKey: String, comicId: String, chapterId: String,
        order: Int, title: String, expected: Int, whole: Int, broken: Int = 0,
        /** 清单那一格 `complete`：默认跟"页够不够"算，给 `false` 时模拟**中断那一趟**落盘的形状。 */
        completeOverride: Boolean? = null,
    ): File {
        val dir = desktopComicChapterDir(root, sourceKey, comicId, order, title)
        dir.mkdirs()
        for (index in 0 until whole) DesktopComicPageFixtures.writeWholePage(dir, index)
        for (offset in 0 until broken) DesktopComicPageFixtures.writeBrokenPage(dir, whole + offset)
        desktopComicWriteChapterManifest(
            dir, chapterId, order, title, expected,
            complete = completeOverride ?: (whole >= expected),
        )
        return dir
    }

    private fun episode(id: String, name: String, placeholder: Boolean = false) =
        EngineSession.Episode(name, id, placeholder)

    private fun infoOf(vararg episodes: EngineSession.Episode) =
        ComicInfo(title = "某本", description = "", episodes = episodes.toList(), cover = "c.jpg")

    /** 读盘那一份账（与壳里同一颗件、同一条路）。 */
    private fun scanned(root: File): List<DesktopComicDownloadedChapter> = desktopComicDownloadedScan(root).first

    /** ① 三档各认各的：齐 / 半截 / 没有，且「半截」与「没有」在屏上是两句话。 */
    @Test
    fun 三档各认各的() {
        val root = tempRoot()
        chapterOnDisk(root, "jm", "1461316", "g1", order = 0, "第1話", expected = 3, whole = 3)
        chapterOnDisk(root, "jm", "1461316", "g2", order = 1, "第2話", expected = 4, whole = 2)
        val rows = desktopComicBulkRows(
            info = infoOf(episode("g1", "第 1 话"), episode("g2", "第 2 话"), episode("g3", "第 3 话")),
            chapters = scanned(root),
            sourceKey = "jm",
            comicId = "1461316",
        )
        assertEquals("齐了那一话：", DesktopComicBulkOffline.DOWNLOADED, rows[0].offline)
        assertEquals("只下了一半的那一话：", DesktopComicBulkOffline.PARTIAL, rows[1].offline)
        assertEquals("本机没有的那一话：", DesktopComicBulkOffline.NONE, rows[2].offline)
        // 徽章三档：没下过的**不摆**（对岸 `else -> Unit`）。
        assertEquals("已下载", desktopComicBulkBadgeLabel(rows[0]))
        assertEquals("离线不全", desktopComicBulkBadgeLabel(rows[1]))
        assertNull("本机没有的那一行不许挂一枚徽章：", desktopComicBulkBadgeLabel(rows[2]))
    }

    /** ② 默认只选中未下载：齐的不进，半截的**要进**（挂绿就是永不重下那条病）。 */
    @Test
    fun 默认只选中未下载而半截的要进() {
        val root = tempRoot()
        chapterOnDisk(root, "jm", "1", "g1", 0, "第1話", expected = 2, whole = 2)
        chapterOnDisk(root, "jm", "1", "g2", 1, "第2話", expected = 2, whole = 1)
        val rows = desktopComicBulkRows(infoOf(episode("g1", "第 1 话"), episode("g2", "第 2 话"), episode("g3", "第 3 话")), scanned(root), "jm", "1")
        assertEquals("默认选中该只含未下载那两话：", setOf("g2", "g3"), desktopComicBulkDefaultSelection(rows))
        assertEquals("未下载枚数（分母）：", 2, desktopComicBulkUndownloadedCount(rows))
    }

    /** ③ 四支各走真盘：第三支「清单说没齐而盘上其实齐了」是新认的那一档。 */
    @Test
    fun 清单说没齐而盘上其实齐了那一支不算缺页() {
        val root = tempRoot()
        // 这一支是真走得到的：中断那一趟会把清单落回 `complete=false` 而**页一颗都不删**，
        // 于是"清单说没齐而盘上其实齐了"是盘上真实存在的一形 ⇒ 用真盘造，而不是手喂一型数据。
        chapterOnDisk(root, "jm", "7", "g1", 0, "第1話", expected = 3, whole = 3, completeOverride = false)
        // 这一支要的是"页一颗不缺而清单说没齐"，读侧的 `complete` 正是那颗共用判式回读盘之后的结论。
        val chapter = scanned(root).single()
        assertFalse("夹具没造出中断那一形（读侧自己判成齐了）：", chapter.complete)
        assertEquals("夹具的应写/已有：", 3 to 3, chapter.expectedPages to chapter.writtenPages)
        assertEquals(
            "页一颗都不缺却说没齐 ⇒ 认成齐（判成 PARTIAL 会把它塞进默认选中，用户按下就是重跑一趟）：",
            DesktopComicBulkOffline.DOWNLOADED, desktopComicBulkOfflineOf(chapter),
        )
        // 其余三支（对岸 `chapterOfflineState` 同一支序）
        assertEquals("本机没这一条 ⇒ NONE：", DesktopComicBulkOffline.NONE, desktopComicBulkOfflineOf(null))
        assertEquals(
            "盘上一颗算整页的都没有 ⇒ NONE（对岸 pageCount<=0 那一支）：",
            DesktopComicBulkOffline.NONE, desktopComicBulkOfflineOf(chapter.copy(writtenPages = 0)),
        )
        // 半截那一支（真盘：应写 4 而只有 2 页）。
        val partial = tempRoot()
        chapterOnDisk(partial, "jm", "7", "g2", 0, "第2話", expected = 4, whole = 2)
        assertEquals(
            "只下一半 ⇒ PARTIAL（挂绿就等于永不重下，对岸 `:1739-1741` 那条旧病）：",
            DesktopComicBulkOffline.PARTIAL, desktopComicBulkOfflineOf(scanned(partial).single()),
        )
        // 那一行挂的徽章（「离线不全」而不是「已下载」）由 ① 钉，两档不塌成一句。
    }

    /** ④ 归一：详情手上是原始形而盘上是净化形，不同轨就全判未下载（最容易留下的病）。 */
    @Test
    fun 带斜杠与冒号的号也要认得出本机那一话() {
        val rawComicId = "https://e-hentai.org/g/1234/abc/"   // 号本身就是 URL（现量 3/34 档源那一族）
        val rawSource = "jm"
        val root = tempRoot()
        // 写侧按净化形落盘（目录名就是这么长出来的），盘上看不见原始形。
        chapterOnDisk(root, rawSource, rawComicId, "g1", 0, "第1話", expected = 2, whole = 2)
        val onDiskForm = desktopComicSafePathKey(rawComicId)
        assertTrue(
            "夹具没按净化形落盘（那这一颗就自证了）：",
            File(File(root, desktopComicSafePathKey(rawSource)), onDiskForm).isDirectory,
        )
        val rows = desktopComicBulkRows(infoOf(episode("g1", "第 1 话")), scanned(root), rawSource, rawComicId)
        assertEquals(
            "拿原始形去比就该认不出本机已有（后果是「盘上有却全判未下载 ⇒ 重下一遍」）：",
            DesktopComicBulkOffline.DOWNLOADED, rows.single().offline,
        )
        // 对照组：归一那一格确实住在决策件里，而不是搬进读侧（`DesktopComicShelfRawIdTest` ⑥ 钉着读侧零净化）。
        assertEquals(
            "批量那颗件里净化恰两处（源与号各一处）：",
            2,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicBulkDownload.kt"),
                "desktopComicSafePathKey(",
            ),
        )
        assertEquals(
            "读侧那颗件仍零处净化（归一不许搬进去）：",
            0,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicDownloadedList.kt"),
                "desktopComicSafePathKey(",
            ),
        )
    }

    /** ⑤ 「还没数回来」不许塌成「本机都没有」：那样默认就是全选整本。 */
    @Test
    fun 还没数回来时表是空的而那句念待读() {
        val rows = desktopComicBulkRows(infoOf(episode("g1", "第 1 话")), chapters = null, "jm", "1")
        assertTrue("没数回来时不许编出行：", rows.isEmpty())
        assertEquals(
            "那一句必须是待读，而不是「没章节」：",
            DESKTOP_COMIC_BULK_PENDING_NOTE,
            desktopComicBulkEmptyNote(infoLoaded = true, scanned = false, scanError = null),
        )
    }

    /** ⑥ 「数不出来」排第一：它比「没数回来」更该出声，否则会把"我读不了盘"说成"这本没东西"。 */
    @Test
    fun 数不出来那一句点名成因而排在最前() {
        val line = desktopComicBulkEmptyNote(infoLoaded = true, scanned = true, scanError = "SecurityException 进不去")
        assertTrue("要点名成因：$line", line.contains("SecurityException 进不去"))
        assertTrue("要出声说数不出来：$line", line.startsWith("本机那一棵树数不出来"))
        // 三档同时给"数不出来"时它赢：不许被后面两支短路掉。
        val also = desktopComicBulkEmptyNote(infoLoaded = false, scanned = false, scanError = "X")
        assertTrue("数不出来压过待读与没详情：$also", also.startsWith("本机那一棵树数不出来"))
    }

    /** ⑦ 真没章节那一句是**桌面自己的实话**：对岸那句「当前分组暂无可下载章节」在桌面是假话（没有分组那一层）。 */
    @Test
    fun 空表那一句不许提分组() {
        val line = desktopComicBulkEmptyNote(infoLoaded = true, scanned = true, scanError = null)
        assertFalse("桌面没有章节分组，不许照抄对岸那句：$line", line.contains("分组"))
        assertTrue("要念的是「没取到章节」：$line", line.contains("没取到章节"))
        // 详情没回来那一档另说一句。
        assertTrue(
            desktopComicBulkEmptyNote(infoLoaded = false, scanned = true, scanError = null).contains("详情尚未加载"),
        )
    }

    /** ⑧ 「全选未下载 ↔ 全不选」的判据是枚数相等（对岸 `:1683`），且一枚都没下时不许念「全不选」。 */
    @Test
    fun 把手两档跟着枚数走而不跟着按键记忆走() {
        val rows = listOf(
            DesktopComicBulkRow(episode("g1", "第 1 话"), DesktopComicBulkOffline.NONE),
            DesktopComicBulkRow(episode("g2", "第 2 话"), DesktopComicBulkOffline.PARTIAL),
            DesktopComicBulkRow(episode("g3", "第 3 话"), DesktopComicBulkOffline.DOWNLOADED),
        )
        assertEquals("全选未下载", desktopComicBulkToggleAllLabel(rows, setOf("g1")))
        assertEquals(
            "枚数等于未下载数就该翻成「全不选」：",
            "全不选", desktopComicBulkToggleAllLabel(rows, setOf("g1", "g2")),
        )
        assertEquals(emptySet<String>(), desktopComicBulkToggleAllNext(rows, setOf("g1", "g2")))
        assertEquals(setOf("g1", "g2"), desktopComicBulkToggleAllNext(rows, setOf("g1")))
        // 一枚都没下过：分母为 0 ⇒ 恒念「全选未下载」，按它得到的是空集而不是"看起来是全选"。
        val allDone = listOf(DesktopComicBulkRow(episode("g1", "第 1 话"), DesktopComicBulkOffline.DOWNLOADED))
        assertEquals("全下完时不许念「全不选」：", "全选未下载", desktopComicBulkToggleAllLabel(allDone, emptySet()))
        assertEquals("这时候按它得到空集：", emptySet<String>(), desktopComicBulkToggleAllNext(allDone, emptySet()))
    }

    /** ⑨ 确认那颗：枚数上屏、零枚按不动。 */
    @Test
    fun 确认那颗带枚数而零枚时按不动() {
        assertEquals("开始下载 (3)", desktopComicBulkConfirmLabel(3))
        assertTrue(desktopComicBulkConfirmEnabled(setOf("g1")))
        assertFalse("一枚都没选时不许按下去（对岸 `enabled = selectedIds.isNotEmpty()`）：", desktopComicBulkConfirmEnabled(emptySet()))
    }

    /** ⑩ 标题两档：只有那一枚占位话时念「下载整本」（对岸 `isGallery` 那一档的桌面形）。 */
    @Test
    fun 无章那一本念下载整本() {
        val wholeBook = listOf(
            DesktopComicBulkRow(episode("1461316", "", placeholder = true), DesktopComicBulkOffline.NONE),
        )
        assertEquals("下载整本", desktopComicBulkTitle(wholeBook))
        val chaptered = listOf(DesktopComicBulkRow(episode("g1", "第 1 话"), DesktopComicBulkOffline.NONE))
        assertEquals("选择下载章节", desktopComicBulkTitle(chaptered))
        // 占位那一行的题名念「整本」而不是空串，也不把那枚长号摆给用户看。
        assertEquals("整本", desktopComicBulkRowLabel(wholeBook.single()))
    }

    /** ⑪ 交出去的那几话**跟着章节表的原次序**（队列是 FIFO：按下顺序就是跑的顺序）。 */
    @Test
    fun 选中的那几话按章节表次序交出() {
        val rows = listOf(
            DesktopComicBulkRow(episode("g1", "第 1 话"), DesktopComicBulkOffline.NONE),
            DesktopComicBulkRow(episode("g2", "第 2 话"), DesktopComicBulkOffline.NONE),
            DesktopComicBulkRow(episode("g3", "第 3 话"), DesktopComicBulkOffline.NONE),
        )
        val picked = desktopComicBulkEpisodesOf(rows, setOf("g3", "g1"))
        assertEquals("次序该是 g1、g3（不是勾选顺序）：", listOf("g1", "g3"), picked.map { it.id })
        assertEquals("已将 2 话加入下载队列", desktopComicBulkEnqueueNote(picked.size))
    }

    /** ⑫ 占位话那一本也要能被批量排出去（「整本」那一级没有别的门）。 */
    @Test
    fun 整本那一档排得出去() {
        val root = tempRoot()
        val rows = desktopComicBulkRows(
            infoOf(episode("1461316", "", placeholder = true)), scanned(root), "jm", "1461316",
        )
        assertEquals(
            "空目录 + 占位话 ⇒ 判未下载并进默认选中（否则整本那一档一个门都没有）：",
            setOf("1461316"), desktopComicBulkDefaultSelection(rows),
        )
        assertEquals(DesktopComicBulkOffline.NONE, rows.single().offline)
    }

    /** ⑬ 形状差：全选那一枚只能住在内容顶部（`WindowDialog.title` 只是字符串，没有标题行那一格）。 */
    @Test
    fun 全选那一枚住在内容顶部() {
        val screen = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
        )
        // 只数那枚把手的实参这一处（屏里 `VeneraTextButton(` 还有「取消」那一枚，数调用点会把它也算进来）。
        assertEquals(
            "「全选」那枚把手不在屏里：",
            1,
            Regex("text = desktopComicBulkToggleAllLabel").findAll(screen).count(),
        )
        assertTrue(
            "它必须住在 `content` 里（弹窗标题那一格只吃字符串）：",
            Regex("content = \\{[\\s\\S]{0,400}desktopComicBulkToggleAllLabel").containsMatchIn(screen) ||
                Regex("VeneraDialog\\([\\s\\S]{0,600}desktopComicBulkToggleAllLabel").containsMatchIn(screen),
        )
    }

    /** ⑭ 屏不自己查盘、不自己判档位：那两件事只在壳与纯件里各一处。 */
    @Test
    fun 屏只搬不算() {
        val screen = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
        )
        assertEquals("屏里不许出现读盘那颗件：", 0, screen.split("desktopComicDownloadedScan(").size - 1)
        assertEquals("屏里不许自己算档位：", 0, screen.split("desktopComicBulkOfflineOf(").size - 1)
        assertEquals("屏里不许自己净化：", 0, screen.split("desktopComicSafePathKey(").size - 1)
        // 详情页复用共享屏后，校验 DesktopDownloadQueuePort 真实接入本地章节扫描与状态判定，且共享屏不直接扫盘。
        val body = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/ComicDetailScreen.kt"))
        assertEquals("共享屏不许出现读盘那颗件：", 0, body.split("desktopComicDownloadedScan(").size - 1)
        assertEquals("共享屏不许自己净化：", 0, body.split("desktopComicSafePathKey(").size - 1)
        val port = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("business/DesktopDownloadQueuePort.kt"))
        assertTrue("桌面下载口没接上读盘那颗件（下载这条链在桌面是死的）：", "desktopComicOfflineLocalChapter(root" in port)
        assertEquals("桌面下载口里那颗共用判式恰一处（不另立第三种齐不齐）：", 1, port.split("chapterOfflineState(").size - 1)
    }

    /** ⑮ 决策件不许长出第四种「齐不齐」的口径。 */
    @Test
    fun 不另立第四种齐不齐() {
        val code = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicBulkDownload.kt"),
        )
        // 齐不齐只吃读侧那颗结论（`chapter.complete`），自己不许去查页、查清单、数文件。
        listOf("desktopComicPageLooksWhole(", "chapter.json", "File(", "listFiles(").forEach { banned ->
            assertEquals("决策件里出现了自己去查盘的那一形（$banned）：", 0, code.split(banned).size - 1)
        }
        assertTrue("档位要吃读侧那颗结论：", code.contains("chapter.complete"))
    }

    /** ⑯ 屏里查本机档位必须吃**三档端口**，不许退回只认齐档的布尔接口。 */
    @Test
    fun 查本机档位吃三档端口而不吞半截() {
        val screen = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
        )
        // 主人「已经下载的也要显示已下载」：从前这里只吃 isChapterDownloaded
        // （== COMPLETE 才 true），PARTIAL 全压成 NONE ⇒ 本机有内容却既不摆「已下载」
        // 也不摆「离线不全」，还被默认勾进下载队列（截图：共 235 · 已下载 0 · 本次 235）。
        assertTrue(
            "详情屏没调 `core.downloads.chapterOffline(` —— 三档档位又丢了：",
            screen.contains("core.downloads.chapterOffline("),
        )
        assertEquals(
            "屏里不许再出现只认齐档的 `core.downloads.isChapterDownloaded(`：",
            0,
            screen.split("core.downloads.isChapterDownloaded(").size - 1,
        )
        assertTrue(
            "COMPLETE / PARTIAL 要逐字落到弹窗那两档徽章：",
            screen.contains("ChapterOffline.PARTIAL -> DesktopComicBulkOffline.PARTIAL") &&
                screen.contains("ChapterOffline.COMPLETE -> DesktopComicBulkOffline.DOWNLOADED"),
        )
        assertTrue(
            "档位未就绪时不许把整本默认全勾，也得如实念「正在核对本机」：",
            screen.contains("offlinePending") && screen.contains("正在核对本机"),
        )
    }
}

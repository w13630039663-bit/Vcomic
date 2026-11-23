package com.venera.desktop.gallery.ui

import com.venera.compose.data.db.HistoryRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 94 ·「开始/继续阅读」那一枚主胶囊的四颗算式 + 它按下到底落到哪。
 *
 * 题面是**量出来的两条**（不是推测）：
 * ① 桌面缺对岸有的那一格 —— `app/.../ComicDetailScreen.kt:552-600` 摆一枚大号胶囊，
 *    有历史时文案是「继续阅读」并带一行小字「继续 <那一话的标题> · 第 N 页」，底边还有一条进度线；
 *    而桌面详情只有「共 N 话」加那一列话号（`DesktopComicDetail.kt:234`），按下恒 `startPage = 0`（`:247`）。
 * ② **对岸自己就是半接线**：那枚胶囊的 `onClick` 走 `onTriggerRead()`（`:564`），
 *    而它打开的是 `chs.first()`（`:266-269`，`launchChapter(..., 0)`）——
 *    也就是说屏上说「继续 第 8 页」而动作是**第一章第 0 页**。
 *
 * 所以这一批的处置不是"照抄"：文案与那行小字**逐字回核对岸**（同一句话两端不同串就是漂），
 * 而**动作按它承诺的那样接上**（那条差异写在 `desktopComicResumeReading` 的注释与 rounds 里）。
 * 三条回核尺子（① ② ⑦）直接从 Android 源码文本现取正则，对岸改了文案桌面会红。
 */
class DesktopComicContinueReadingTest {

    private fun record(chapterIndex: Int, chapterTitle: String, pageIndex: Int) = HistoryRecord(
        comicId = "1461316",
        title = "某本",
        author = "",
        coverUrl = "",
        sourceName = "jm",
        lastChapterTitle = chapterTitle,
        lastChapterIndex = chapterIndex,
        lastPageIndex = pageIndex,
        totalPages = 89,
        updatedAt = 1L,
    )

    /** ① 文案两档各念一句，且**逐字**与对岸那两句对上（对岸改了桌面要红，不许桌面自己改词）。 */
    @Test
    fun 主胶囊那句两档各念一句而字面跟着对岸() {
        assertEquals("没历史那一档：", "开始阅读", desktopComicReadButtonLabel(false))
        assertEquals("有历史那一档：", "继续阅读", desktopComicReadButtonLabel(true))
        val android = DesktopSourceTree.androidSource("feature/ComicDetailScreen.kt").readText()
        val both = Regex("if \\(historyRecord != null\\) \"([^\"]+)\" else \"([^\"]+)\"").find(android)
            ?: error("对岸那句两档的写法变了（这条是回核：桌面跟它走，不改它）")
        assertEquals("有历史那档逐字照对岸：" + both.value, both.groupValues[1], desktopComicReadButtonLabel(true))
        assertEquals("没历史那档逐字照对岸：" + both.value, both.groupValues[2], desktopComicReadButtonLabel(false))
    }

    /**
     * ② 那行小字的**拼法**逐字回核（`"继续 " + lastChapterTitle + " · 第 " + (lastPageIndex + 1) + " 页"`），
     * 并真跑两档：库里那一页是 0 基数 ⇒ 人话从 1 数起（写 0 就是屏上少一页，与对岸不同轨）。
     */
    @Test
    fun 那行小字从一数起而拼法逐字照对岸() {
        assertEquals("第 0 页那一档：" , "继续 第 3 话 · 第 1 页", desktopComicContinueCaption("第 3 话", 0))
        assertEquals("第 7 页那一档：" , "继续 第 3 话 · 第 8 页", desktopComicContinueCaption("第 3 话", 7))
        val android = DesktopSourceTree.androidSource("feature/ComicDetailScreen.kt").readText()
        assertTrue(
            "对岸那句拼法变了（`继续 ` + 标题 + ` · 第 ` + (页+1) + ` 页`），桌面要跟着改而不是自己造：",
            Regex("\"继续 \" \\+ historyRecord\\.lastChapterTitle \\+ \" · 第 \" \\+ \\(historyRecord\\.lastPageIndex \\+ 1\\) \\+ \" 页\"").containsMatchIn(android),
        )
    }

    /**
     * ③ 进度线那一格：**没有分母的百分比是假数据** —— 话数为 0 时交回 0f（那一屏据此不摆线），
     * 而对岸那句 `coerceIn(0f, 1f)` 的两端各喂一次越界样例，否则写反了也量不出来。
     */
    @Test
    fun 进度线只在有分母时给而两端都夹住() {
        assertEquals("读到第 1 话而共 4 话：", 0.25f, desktopComicReadProgress(0, 4), 0.0001f)
        assertEquals("读到第 4 话而共 4 话（越界那一档）：", 1f, desktopComicReadProgress(9, 4), 0.0001f)
        assertEquals("负数那一档：", 0f, desktopComicReadProgress(-2, 4), 0.0001f)
        assertEquals("没有分母就不给线：", 0f, desktopComicReadProgress(2, 0), 0.0001f)
    }

    /**
     * ④ 按下落到哪：**这一批的正题**。有记录 ⇒ 记录那一话 + 记录那一页；
     * 无记录 ⇒ 第一话第 0 页；记录的话号越界（源把那话删了）⇒ 夹住而不是抛，且页号跟着那一话走。
     */
    @Test
    fun 按下落到记录那一话那一页而不是第一话第零页() {
        val episodes = List(4) { "e$it" }
        assertEquals("有记录那一档：", 2 to 7, desktopComicResumeReading(record(2, "第 3 话", 7), episodes))
        assertEquals("无记录那一档：", 0 to 0, desktopComicResumeReading(null, episodes))
        assertEquals("话号越界那一档夹到最后一话：", 3 to 5, desktopComicResumeReading(record(9, "没了", 5), episodes))
        assertEquals("一话都没有时交回 null 而不是抛（那一枚胶囊按下去没对象就是不摆）：", null, desktopComicResumeReading(record(0, "x", 0), emptyList()))
    }

    /** ⑤ 越界那一档**不许静默改页号**：夹话号时页号仍是库里那一枚（改了就等于悄悄丢进度）。 */
    @Test
    fun 夹话号时页号还是库里那一枚() {
        val resumed = desktopComicResumeReading(record(99, "没了", 41), List(3) { "e$it" })
        assertEquals("话号夹到最后一话：", 2, resumed!!.first)
        assertEquals("页号不许被顺手清零：", 41, resumed.second)
    }

    /**
     * ⑥ 那枚胶囊在屏上**必须带动作**：立的正是"不许摆着「继续阅读」而按下开第一章"，
     * 所以接线尺子是 文案那颗件被读一处 + 那行小字被读一处（只在有历史那两档里）+ 续读算式被调一处，缺一就红。
     * ⚠️ 刻意**不钉** `startPage = 0` 那半句：那一列话的各行按下仍从该话开头读（**照对岸** `:1206 launchChapter(ch.id, ch.title, actualIdx)`，
     * 它也不带页），只有胶囊那一走才续读。把行也改成续读就是自己定语义，不许。
     */

    @Test
    fun 那枚胶囊带动作而那三颗算式各有一处上屏() {
        listOf(
            "desktopComicReadButtonLabel(" to "胶囊那句两档",
            "desktopComicContinueCaption(" to "那行小字",
            "desktopComicReadProgress(" to "底边那条进度线",
        ).forEach { (needle, what) ->
            assertEquals(
                "$what 在详情屏要恰好一处调用（零处=那一格还是没接；两处=有人算了两遍）：",
                1,
                DesktopSourceTree.desktopComicDetailOccurrences(needle),
            )
        }
        // 校验续读胶囊点击将 desktopDetailReadCapsule 计算的页码传入 core.openChapter，而非固定第 0 页。
        assertTrue(
            "按下没把那两个数交出去 =「摆着「继续阅读」而按下开第一章」那半接线复发：",
            "capsule.pageIndex)" in DesktopSourceTree.desktopComicDetailCode(),
        )
        val code = DesktopSourceTree.desktopComicDetailCode()
        // 旧仿制屏的 `onStartReading`/`historyOutcome` 两枚形参退役 ——
        // 屏吃核（历史流在屏里收），展示件吃成品胶囊数据；判式钉新缝的两枚必传形参。
        assertTrue("续读那一跳不在屏签名上（漏传不红 = 胶囊恒按不动）：", "onResumeRead: () -> Unit," in code)
        assertTrue("阅读会话那一跳不在屏签名上：", "onStartLiveReading: (ReaderSession) -> Unit," in code)
        // 校验共享详情屏章节行点击仅传三参（从第 0 页开始），仅 readTarget 续读路径传入 target.pageIndex。
        val body = DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/ComicDetailScreen.kt"))
        assertTrue("共享屏章节表那一跳被改成续读了（那是我们自己加语义，本批不许）：", "launchChapter(ch.id, ch.title, actualIdx)" in body)
    }

    /** ⑦ 「上次读到这里」那枚标记的字面逐字回核对岸（对岸改了桌面跟着改，不许桌面自己改词）。 */

    @Test
    fun 那枚标记的字面逐字照对岸() {
        val android = DesktopSourceTree.androidSource("feature/ComicDetailScreen.kt").readText()
        // 对岸那一枚是 Icon(contentDescription = 那一串)：桌面没有那套图标，把同一串摆在文字位上，
        // 所以串本身必须逐字同（改了词就是两端念成两个名字）。
        assertTrue(
            "对岸那枚标记的 contentDescription 不再写着那句 —— 桌面这一句要跟着改判而不是悄悄留着：",
            Regex("contentDescription = \"" + Regex.escape(DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION) + "\"").containsMatchIn(android),
        )
        assertEquals("那一枚串只许一颗出处：", "上次读到这里", DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION)
        // 数的是**上屏那一形**（`text = 那枚常量`），不是裸名字：详情屏还有一行 `import`，
        // 拿裸名字数会数出 2 处，而那条"恰一处"就恒不可能成立（假红，不是抓到漂移）。
        val sites = DesktopSourceTree.desktopMainSources()
            .map { it.name to DesktopSourceTree.codeOccurrences(it, "text = DESKTOP_HISTORY_CHAPTER_MARK_DESCRIPTION") }
            .filter { it.second > 0 }
            .sortedBy { it.first }
        assertEquals(
            "那枚串的上屏分布变了（期望「详情屏恰好一处」—— 零处=那句还是没人念）：$sites",
            listOf("DesktopComicDetailSections.kt" to 1),
            sites,
        )
    }

    /**
     * ⑧ 分家的规矩不许破：**装载那一件**吃历史读口（要开库），**展示那一件**只吃纯数据那块 `HistoryRecord?`
     *    —— 展示层一吃 DAO 句柄，语义树那一层就渲不出来了（同 `EngineSession` 那一条，批 80 立的）。
     * 两枚都是**必填、不给默认值**：漏传的形状是"胶囊恒说「开始阅读」"，那种半接线在屏上看不出来。
     */

    @Test
    fun 装载吃读口而展示只吃纯数据而两枚都必填() {
        val code = DesktopSourceTree.desktopComicDetailCode()
        // 装载那一件吃**核**（历史流 `core.historyFlow`），
        // 展示那一件只吃纯数据那块 `DesktopDetailViewData`（胶囊是其中一枚字段）。
        assertTrue("屏侧没吃核的历史流（胶囊永远读不到历史）：", "core.historyFlow.collectAsState()" in code)
        assertTrue("展示件不吃纯数据那块（有人把读口塞回去了）：", "data: DesktopDetailViewData," in code)
        listOf("HistoryDao" to "屏自己去开库", "DatabasePorts" to "屏自己建端口", "EngineSession(" to "屏自己造引擎")
            .forEach { (banned, why) ->
                assertEquals("$why（展示层一吃句柄，语义树那层就渲不出来）：", 0, DesktopSourceTree.desktopComicDetailOccurrences(banned))
            }
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue("壳里那颗 effect 没问读口 = 那一枚胶囊永远读不到历史：", "readHistoryRow" in shell)
        assertTrue("壳里不许自己 HistoryDao.getInstance（读口由装配点递下来）：", "HistoryDao.getInstance" !in shell)
        val app = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt"))
        assertTrue("装配点没递 readHistoryRow = 真窗口里那一枚根本不摆：", "readHistoryRow = { comicId ->" in app)
    }

    /**
     * ⑨ stdout 那一行念得出**三档**（有 / 无 / 没读到），而「没读到」那一档必须带原因。
     *
     * 为什么这一档要有读数：那一档在屏上的表现是"胶囊不见了"，而"库里没这一本"与"库没开到"
     * 在像素上同形 —— 只有这一行分得出（同 `D_详情` 那一条纪律：`Loading` 与 `Failed` 在屏上都是左上角一行小字）。
     * 原因那一格吃的是**一行化**之后的原文：站方的报错常带 stack 尾巴，两行的读数会被按行的抓取漏掉后半句。
     */
    @Test
    fun 续读那一行三档各念一句而没读到那档带原因() {
        assertEquals(
            "有记录那一档：",
            "D_续读 号=9 记录=有 话=2 页=7",
            desktopComicHistoryProgressLine("9", Result.success(record(2, "第 3 话", 7))),
        )
        assertEquals(
            "库里没这一本那一档：",
            "D_续读 号=9 记录=无",
            desktopComicHistoryProgressLine("9", Result.success(null)),
        )
        val broken = desktopComicHistoryProgressLine(
            "9",
            Result.failure(IllegalStateException("no such table\nsecond line")),
        )
        assertTrue("没读到那一档要念出原因：" + broken, broken.startsWith("D_续读 号=9 记录=没读到 因=IllegalStateException: no such table"))
        assertEquals("那一行不许被原因拆成两行（抓读数的脚本按行锚）：" + broken, 1, broken.lines().size)
    }

    /**
     * ⑩ 那枚胶囊的**填色必须走我们自己的档**，不许挂在库那条链上（批 95）。
     *
     * 为什么立这一条：批 94 的包内图证抓到那枚 `AccentButton` 拍出来是天蓝 —— 成因在
     * `DesktopAccentFillTest`（`darkColors(accent = 藤紫)` 那一枚参数对 `fillAccent` 不起作用：库里那张预置表
     * **只有一颗键** `#0078D4`，而 `defaultShades` 就是这颗键自己的值 ⇒ 六枚候选量到同一枚蓝）。
     * 换色解决不了，只能**不经过那条链**。
     * 这一条钉的就是"不经过"：`DesktopTheme.AccentFill`（稿 `--accent-fill` = `#5B47A8`）在详情这一屏恰好一处，
     * 而 `AccentButton(` 零处 —— 有人把它换回库控件，屏上那枚就会静默变蓝（本仓那一族"看着像接上了"）。
     */

    @Test
    fun 那枚胶囊的填色走自家那档而不经库那条链() {
        assertEquals(
            "填充档在详情屏要恰好一处（零处=又回库控件；两处=有人另起一格）：",
            1,
            DesktopSourceTree.desktopComicDetailOccurrences("DesktopTheme.AccentFill"),
        )
        assertEquals("不许用库里那颗 AccentButton（它会静默吃 #0078D4 那一枚蓝）：", 0, DesktopSourceTree.desktopComicDetailOccurrences("AccentButton("))
    }

    /**
     * ⑪ 两枚文字档不许自己造：胶囊上那两句吃的是 [DesktopTheme.TextPrimary]（稿 `--on-accent` = `#FFFFFF`，
     *     与既有文字最亮档同值 ⇒ 不新增色值），而**不许**用 alpha 压主色那一族写法。
     */

    @Test
    fun 那两枚文字档走主题而不拿alpha造档() {
        assertTrue(
            "胶囊那两枚不吃 DesktopTheme.TextPrimary = 有人在详情屏自造一档色值：",
            DesktopSourceTree.desktopComicDetailOccurrences("DesktopTheme.TextPrimary") >= 3,
        )
        // 对岸那两格用的都是 onPrimary.copy(alpha = ...)，而「拿 alpha 压出一档」在本仓是禁止形状：
        // 那枚色值不在任何表里，只有「看起来淡一点」。这里钉整颗文件零处，别处再压一次同样是不作数的第五档。
        assertEquals("详情屏不许拿 alpha 压主题文字档：", 0, DesktopSourceTree.desktopComicDetailOccurrences("DesktopTheme.TextPrimary.copy("))
        val literals = DesktopSourceTree.desktopComicDetailLines().filter { "Color(0x" in it }
        assertTrue("详情屏写死了色值字面量（那成了第二份色板）：$literals", literals.isEmpty())
    }


    /**
     * ⑫ 那一枚「上次读到这里」挂第几行 —— 必须与按下真会打开的那一行**同一个数**。
     *
     * 为什么另拆一颗而不叫调用方自己拿 lastChapterIndex：那一枚行号会越界（源站删了那一话），
     * 而那颗算式里那枚「夹进现有目录」才是按下落到的那一行。两处各算一定漂，而漂成的样子屏上看着最像真的：
     * 标记指着第 3 行而按下开第 4 行 —— 用户据此判断「我读到这儿了」，那是个假指针。
     */
    @Test
    fun 那一枚标记挂的是按下真会打开的那一行() {
        val episodes = List(4) { "e$it" }
        assertEquals("有记录那一档：", 2, desktopComicMarkedChapterIndex(Result.success(record(2, "第 3 话", 7)), episodes))
        assertEquals("越界那一档跟着夹住之后的那一行：", 3, desktopComicMarkedChapterIndex(Result.success(record(9, "没了", 5)), episodes))
        assertEquals("还没读回来不挂：", null, desktopComicMarkedChapterIndex(null, episodes))
        assertEquals("读不成不挂（不知道不等于没读过）：", null, desktopComicMarkedChapterIndex(Result.failure(IllegalStateException("no such table")), episodes))
        assertEquals("库里没这一本不挂：", null, desktopComicMarkedChapterIndex(Result.success(null), episodes))
        assertEquals("一话都没有不挂：", null, desktopComicMarkedChapterIndex(Result.success(record(0, "x", 0)), emptyList()))
    }

    /**
     * ⑬ 「还没读回来」那一档**不摆胶囊**（按下去会挪走另一端的进度）。
     *
     * comic_history 两端共用而没有版本号/迁移，所以「按一枚「开始阅读」把库里第 40 页写成第 0 页」
     * 是静默的错，比少一枚钮严重得多；屏上那一枚不摆的代价只是这一趟先不回书。
     * 与 ⑨ 那句读数（记录=没读到）配成一对：屏上少一格 + stdout 说得出为什么。
     */
    @Test
    fun 还没读回来那一档不摆胶囊而确实没历史那一档摆() {
        val code = DesktopSourceTree.desktopComicDetailCode()
        // 加载中与失败态由详情加载视图覆盖，ReadCapsule 仅在 capsule 非空时渲染。
        assertTrue("装载态没了（还没读回来会被当成没历史，按下清零另一端进度）：", "if (details == null) {" in code)
        assertTrue("展示件的胶囊闸门没了（没给数据也摆一枚按了编落点的钮）：", "if (capsule == null) return" in code)
        // 那两枚串仍由那颗件现取（屏不许硬写）。
        assertEquals("「开始阅读」那枚串不许在屏里硬写：", 0, DesktopSourceTree.desktopComicDetailOccurrences("\"开始阅读\""))
    }

}

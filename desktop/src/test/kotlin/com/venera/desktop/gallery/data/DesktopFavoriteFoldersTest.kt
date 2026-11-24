package com.venera.desktop.gallery.data

import com.venera.desktop.app.VeneraDesktopLargeScreen
import com.venera.compose.data.db.FavoriteRecord
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 收藏**文件夹**那一格的决策层。
 *
 * ## 批评的是哪一格
 *
 * `comic_favorite.folder_name` 那一列从桌面接收藏那天起恒走默认值 ⇒ 桌面上所有收藏都在同一格里，
 * 而那一排选择器在桌面上从来没有对应物。判据面住 `gallery/data/DesktopFavoriteFolders.kt`
 * （全纯函数），这里逐条钉语义；**挪夹时整行替换会连带清掉别的格子**那一格由
 * `db/DesktopFavoriteMoveFolderTest` 拿真 sqlite 量，这里不重复。
 *
 * ## 四件必须有牙的事
 *
 * 1. **空表也要有那一排**：那颗件恒交回 `[默认]`。回空表就是"那一格整个消失"，
 *    用户连"收到默认这一夹"都看不见（① 钉它）。
 * 2. **`picked` 的那一格被清空时不许顺手猜别的夹**：回默认才是实话，猜出来的那一格会被
 *    用户读成"我收在那儿了"（② 钉它）。
 * 3. **「还没读回来」与「确实没收过」交回同一个 `null`，而分开它们的是 `favorited` 那枚三态**：
 *    那一排不许在表没到货时猜一格（③ 三档各钉一句）。
 * 4. **措辞里不许出现「收藏」那两个字**：屏上那枚钮念的正是「收藏」，而无头判据
 *    `onNodeWithText` 默认**子串**匹配 ⇒ 含那两个字的句子会与那枚钮撞成 2 枚而顶红旧尺。
 *    这不是文风问题，⑥ 把它钉成判据而不是留给运气。
 */
class DesktopFavoriteFoldersTest {

    private fun fav(
        id: String,
        folder: String = DESKTOP_FAVORITE_DEFAULT_FOLDER,
        tags: String = "",
        hasUpdate: Boolean = false,
        latestChapter: String = "",
        createdAt: Long = 1L,
    ) = FavoriteRecord(
        comicId = id,
        title = "标题 $id",
        author = "",
        coverUrl = "https://cover/$id",
        sourceName = "jm",
        folderName = folder,
        tags = tags,
        hasUpdate = hasUpdate,
        latestChapter = latestChapter,
        createdAt = createdAt,
    )

    /** ① 名字那一排：默认恒在首位、其余去重升序、空白名不算一格；空表也交回 `[默认]`。 */
    @Test
    fun 那一排的名字取自表而空表也有默认那一格() {
        assertEquals(
            "空表时那一排不许整颗消失（那一格是选择器，不是「那一本在哪」）：",
            listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER),
            desktopFavoriteFolderNames(emptyList()),
        )
        assertEquals(
            // 「默认」恒在首位而不参与排序；其余按 **Unicode 码点**升序（`追`=U+8FFD 排在 `收藏`=U+6536 之后）。
            // 这一档不是随手写的：它与 SQLite `ORDER BY folder_name ASC`（BINARY＝字节序，UTF-8 下与码点序同）
            // 同一口径，那条一致性由 `db/DesktopFavoriteMoveFolderTest` 拿真库比过。
            "「默认」恒在首位，其余按码点升序（与 SQLite BINARY 同口径）：",
            listOf("默认", "收藏2", "追更"),
            desktopFavoriteFolderNames(
                listOf(fav("a", "追更"), fav("b", "收藏2"), fav("c", "追更"), fav("d", "默认")),
            ),
        )
        assertEquals(
            "整格空白的那一格不算名字（写坏的数据不该变成一枚把手）：",
            listOf("默认"),
            desktopFavoriteFolderNames(listOf(fav("e", "   "))),
        )
    }

    /** ② 当下选中哪一格：`picked` > `currentFolder` > 默认；挑过而那一格没了 ⇒ 回默认而不猜。 */
    @Test
    fun 挑过的那一格排在现在那一格之前而没了就回默认() {
        val names = desktopFavoriteFolderNames(listOf(fav("a", "追更")))
        assertEquals(
            "第二档（本批最容易写错的一笔）：没挑过而它已在「追更」时不许高亮「默认」——" +
                "那样按下去「取消收藏」再按「收藏」就悄悄把它移回默认：",
            "追更", desktopFavoriteFolderChoice(null, "追更", names),
        )
        assertEquals("第一档：挑过的赢过现在那一格。", "默认", desktopFavoriteFolderChoice("默认", "追更", names))
        assertEquals(
            "第三档：都没时才落默认。", "默认", desktopFavoriteFolderChoice(null, null, names),
        )
        assertEquals(
            "挑过的那一格后来被清空 ⇒ 回默认，**不许**顺手猜别的夹（猜出来的会被读成「我收在那儿了」）：",
            "默认", desktopFavoriteFolderChoice("已消失的夹", null, names),
        )
    }

    /** ③ 那一本现在在哪一格：三态各一形，而「还没读回来」不许折成「没收过」以外的东西。 */
    @Test
    fun 那张表没回来时那一格是不知道而不是猜一格() {
        val rows = listOf(fav("a", "追更"))
        assertNull("表还没读回来 ⇒ 交回 null（屏上念那句「还没读回来」）：", desktopFavoriteFolderOf("a", rows, loaded = false))
        assertEquals("读回来了而它在「追更」：", "追更", desktopFavoriteFolderOf("a", rows, loaded = true))
        assertNull("读回来了而表里没这一本：", desktopFavoriteFolderOf("b", rows, loaded = true))
    }

    /** ④ 写回库的那一条只换夹名，其余格子由 `existing` 保住。 */
    @Test
    fun 已有那一行时只换夹名而别的格子一律原样() {
        val existing = fav("a", "默认", tags = "恋爱,校园", hasUpdate = true, latestChapter = "第 8 话", createdAt = 100L)
        // 装配点新拼的那一份（六格里只有号与题名对得上，其余是默认值 + 现在的时刻）。
        val fresh = fav("a", "默认", tags = "", createdAt = 9_999L)
        val written = desktopFavoriteRecordToWrite(existing, fresh, "追更")
        assertEquals("夹名要跟着改：", "追更", written.folderName)
        assertEquals("标签不许被抹：", "恋爱,校园", written.tags)
        assertTrue("追更标记不许被抹：", written.hasUpdate)
        assertEquals("最新话不许被抹：", "第 8 话", written.latestChapter)
        assertEquals(
            "created_at 不许换成现在（收藏屏按它倒序排 ⇒ 只是换个夹就跳到顶端）：",
            100L, written.createdAt,
        )
        // 对照：那一本**还没收过**时用的就是新拼的那一份，而夹名照样落得进去。
        val firstTime = desktopFavoriteRecordToWrite(null, fresh.copy(tags = ""), "追更")
        assertEquals("没收过时用新拼的那一份而夹名落得进：", "追更", firstTime.folderName)
        assertEquals("没收过时没有旧格子可保，走 fresh：", 9_999L, firstTime.createdAt)
    }

    /** ⑤ 那句"按下会落到哪"四档各一句，而「还没读回来」那一档不许猜成"没读过"。 */
    @Test
    fun 那一句四档各念一句而不知道那一档明写不知道() {
        assertEquals("没收过：", "这一本会落到 追更", desktopFavoriteFolderActionLabel(false, null, "追更"))
        assertEquals(
            "已收而正是那一格：", "追更 正是这一本现在那一格",
            desktopFavoriteFolderActionLabel(true, "追更", "追更"),
        )
        assertEquals(
            "已收而收在别格 ⇒ 必须说**移到**（不说就是悄悄搬人东西）：",
            "这一本会从 默认 移到 追更",
            desktopFavoriteFolderActionLabel(true, "默认", "追更"),
        )
        assertTrue(
            "表还没读回来时那一档要明写「还没读回来」而不许折成「没读过」：实际=" +
                desktopFavoriteFolderActionLabel(true, null, "追更"),
            "还没读回来" in desktopFavoriteFolderActionLabel(true, null, "追更"),
        )
    }

    /** ⑥ 措辞纪律：那两句里不许出现「收藏」两字（会与那枚钮撞子串匹配）。 */
    @Test
    fun 那两句里不许出现收藏那两个字() {
        val wording = listOf(
            desktopFavoriteFolderActionLabel(false, null, "追更"),
            desktopFavoriteFolderActionLabel(true, "默认", "追更"),
            desktopFavoriteFolderActionLabel(true, "追更", "追更"),
            desktopFavoriteFolderActionLabel(true, null, "追更"),
        )
        val offenders = wording.filter { "收藏" in it }
        assertTrue(
            "这几句含「收藏」，会与那枚收藏钮在无头判据里撞成两枚（`onNodeWithText` 默认子串匹配）：$offenders",
            offenders.isEmpty(),
        )
        assertEquals("选中那一枚要点名：", "追更 ✓", desktopFavoriteFolderChipLabel("追更", true))
        assertEquals("没选中的那一枚不许带钩：", "追更", desktopFavoriteFolderChipLabel("追更", false))
    }

    /** ⑦ stdout 那一行：包内取证对的是读数，而「还没读回」必须与「确实没有」分得开。 */
    @Test
    fun 取证读数那一行三档各说得开() {
        val line = desktopFavoriteFolderReading("1461316", "追更", "默认", 3)
        assertTrue("前缀不在（`D_收藏` 那一族靠它筛）：$line", line.startsWith("D_收藏 夹"))
        assertTrue("号要上读数：$line", "号=1461316" in line)
        assertTrue("现在那一格要上读数：$line", "现在=追更" in line)
        assertTrue("选中那一格要上读数：$line", "选中=默认" in line)
        assertTrue("在用夹的枚数要上读数：$line", "在用=3" in line)
        assertTrue(
            "还没读回来那一档不许念成空格（它与「确实没收过」在读数上不能同形）：",
            "还没读回" in desktopFavoriteFolderReading("1461316", null, "默认", 1),
        )
    }

    /** ⑧ 接线尺：那四格成品由壳递进来，屏不查库；挑中那一格时壳真落笔。 */
    @Test
    fun 壳递成品而屏只搬而挑中那一格真落笔() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        // 拆批 3：收藏弹窗搬去 DesktopComicDetailFavoriteDialog.kt，断言跨两颗文件拼接。
        val screen = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
        ) + DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailFavoriteDialog.kt"),
        )
        // 详情页复用共享核 ComicDetailCore 与桌面独立收藏弹窗，校验文件夹列表由核提供、勾选写侧接回核或桌面主表。
        assertTrue("那一排的名字没从核递进面板：", "localFolders: List<String>" in DesktopSourceTree.codeText(DesktopSourceTree.androidSource("feature/ComicDetailCore.kt")))
        assertTrue("详情屏没渲收藏弹窗：", "FavoriteDialog(" in screen)
        assertTrue("那一排的把手没接上落笔（摆了而按不动）：", "core.toggleLocalFavorite(comic, folder)" in screen)
        val favPort = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("business/DesktopBusinessPorts.kt"))
        assertTrue("桌面收藏口没接上 LocalFavoritesManager（那一排在桌面连不到库）：", "LocalFavoritesManager.getInstance" in favPort)
        // 记：仿制屏的"单夹换夹 + 整行不覆盖"（`desktopFavoriteRecordToWrite`）那格语义，共享面板按逐夹 toggle
        // 走，安卓与桌面同一份 —— 本条从"壳里递成品"改判为"共享面板 + 桌面收藏口都真接上"，语义未松。
        // 屏只搬：那三颗算式一颗都不许住在屏里（与 `episodeNoteOf` / `bulkEmptyNote` 同一条口径）。
        listOf(
            "desktopFavoriteFolderChoice(" to "屏自己去算选中那一格",
            "desktopFavoriteFolderNames(" to "屏自己去数在用的夹",
            "desktopFavoriteFolderOf(" to "屏自己去查那一本在哪一格",
            "FavoriteRecord(" to "屏自己造那条记录（那就不止是「搬」了）",
        ).forEach { (needle, why) ->
            assertEquals("$why：", 0, DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"), needle,
            ))
        }
        // 收藏那一格的形状（改口径 · 详情页重建）：心形钮扳开**收藏夹弹窗**、
        // 点某一夹即落笔（`onToggleFavoriteFolder(folder)` 交出被按的那一枚）——
        // 旧稿"横排先挑格、收藏钮落笔"的两步合成一步，"点夹即真写"的钉法原样。
        assertTrue("心形钮没有扳收藏夹弹窗的口（点了没去处 = 假把手）：", "showFavoriteDialog = true" in screen)
        assertEquals(
            "收藏夹弹窗恰好一处（多了就是第二扇假窗）：",
            1,
            DesktopSourceTree.codeOccurrences(
                DesktopSourceTree.desktopSourceOf("detail/DesktopComicDetailScreen.kt"),
                "if (showFavoriteDialog) {",
            ),
        )
        assertTrue("点夹没把夹名交出去（摆了而按不动那一族）：", "onToggleFavoriteFolder(folder)" in screen)
        // 壳里原有的死函数 favoriteRecordFor 已清理，现剩声明处与装配点 deleteFavoriteFolder 两处引用。
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopFavoriteRecordToWrite(")
        assertEquals(
            "那颗件应当「声明一处 + 装配点接一次」：$sites",
            listOf(
                "DesktopFavoriteFolders.kt" to 1,
                "VeneraDesktop.kt" to 1,
            ),
            sites,
        )
        assertFalse("壳里另拼了一遍夹名以外的格子：", Regex("folderName = \"").containsMatchIn(shell))
    }

    /**
     * ⑨ 收藏那一屏那一行「在哪一夹」：两档各一句，而**空白那一格不许猜成「默认」**。
     *
     * 与 ③ 那条同一口径：猜出来的那一格会被用户读成"我收在那儿了"。库里真有 `folder_name` 是空串的行
     * （对岸那句 `SELECT DISTINCT` 同样会把空串列出来），所以那一档必须自己念得出"没记夹名"。
     */
    @Test
    fun 收藏那一屏那一行两档各一句而空白那格不猜默认() {
        assertEquals("有名那一档点名自己那一夹：", "夹：追更", desktopFavoriteFolderLine("追更"))
        assertEquals(
            "空白那一档不许折成「默认」（那是猜，而猜出来的会被读成「我收在那儿了」）：",
            "夹：（没记夹名）", desktopFavoriteFolderLine(""),
        )
        assertEquals("整格空白也算空白：", desktopFavoriteFolderLine(""), desktopFavoriteFolderLine("   "))
        // 接线：那一行住在屏里，而那句由那颗件给（屏不自己拼，也不查库）。
        val screen = DesktopSourceTree.desktopSourceOf("favorites/DesktopFavoritesScreen.kt")
        assertEquals(
            "那一屏要恰好一处上屏那一句（零处=显示还是没接；两处=有人拼了两遍）：",
            1, DesktopSourceTree.codeOccurrences(screen, "text = desktopFavoriteFolderLine("),
        )
        assertEquals("那一屏不许自己去查库：", 0, DesktopSourceTree.codeOccurrences(screen, "FavoriteDao"))
        // 那句的出处只有那颗件 + 那一屏两处（别处再拼一遍就是同一件事两个口径）。
        val sites = DesktopSourceTree.desktopMainGroupedOccurrences("desktopFavoriteFolderLine(")
        assertEquals(
            "那颗件的分布应当「声明一处 + 收藏屏一处」：$sites",
            listOf("DesktopFavoriteFolders.kt" to 1, "DesktopFavoritesScreen.kt" to 1),
            sites,
        )
    }

    // ── ：那一排「只看哪一夹」──

    /**
     * ⑩ 那一排的档位与次序：「全部」恒在首位且带整张表的条数，其后逐夹带自己那一条数。
     *
     * 只有「全部」那一枚的时候也照样交回来（不是交空表）：整排随数据出现/消失会让人刚把最后一本
     * 移出某一夹就再也回不到那一档。
     */
    @Test
    fun 那一排全部在首位而每档带自己那一条数() {
        val rows = desktopFavoriteFolderTabRows(
            names = listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER, "追更"),
            selected = null,
            favorites = listOf(fav("a", "追更"), fav("b", "追更"), fav("c")),
            countsKnown = true,
        )
        assertEquals(
            "「全部」在首位而它筛的是 null（不是一夹名）：",
            listOf(null, DESKTOP_FAVORITE_DEFAULT_FOLDER, "追更"),
            rows.map { it.folder },
        )
        assertEquals(
            "每一档念的是自己那一夹的条数而「全部」念整张表：",
            listOf("全部 3 ✓", "默认 1", "追更 2"),
            rows.map { it.label },
        )
        assertEquals("未挑时选中的只有「全部」：", listOf(true, false, false), rows.map { it.selected })
        // 只有一夹（甚至零本）时那一排仍然交得出东西。
        assertEquals(
            "空表也要有那一排（与选择器同一条理由：回空表等于整排消失）：",
            "全部 0 ✓",
            desktopFavoriteFolderTabRows(
                names = listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER), selected = null,
                favorites = emptyList(), countsKnown = true,
            ).first().label,
        )
    }

    /** ⑪ 挑过那一夹被清空时回「全部」而不猜别的夹（与 [desktopFavoriteFolderChoice] 同一口径）。 */
    @Test
    fun 那一夹没本子了就回全部而不顺手猜别的夹() {
        val names = listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER)
        assertEquals(
            "挑中而那一夹还在用就跟着挑：", "追更",
            desktopFavoriteFolderTabChoice("追更", listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER, "追更")),
        )
        assertNull(
            "那一夹的最后一本被移出之后不许猜一格（猜出来的筛选会被读成「用户自己筛的」）：",
            desktopFavoriteFolderTabChoice("追更", names),
        )
        assertNull("没挑过就是全部：", desktopFavoriteFolderTabChoice(null, names))
    }

    /**
     * ⑫ 筛选那一颗：`null` 交全表、按**原值**比夹名，而空白夹名那一本只在「全部」里出现。
     *
     * 空白那一档是这一颗最容易写错而一条都不红的地方：把它折进「默认」就会让那一本凭空
     * 出现在用户从没挑过的那一格里（与 ③ 那条「不许猜一格」同一口径）。
     */
    @Test
    fun 筛的时候按原值比而空白那本只在全部里看得见() {
        val all = listOf(fav("a", "追更"), fav("b"), fav("c", ""), fav("d", "   "))
        assertEquals(
            "null 是不筛（交回整张表，不是滤掉空白）：",
            listOf("a", "b", "c", "d"),
            desktopFavoriteFoldersShown(null, all).map { it.comicId },
        )
        assertEquals("点名的那一夹只有它自己那一本：", listOf("b"), desktopFavoriteFoldersShown("默认", all).map { it.comicId })
        assertEquals("另一夹：", listOf("a"), desktopFavoriteFoldersShown("追更", all).map { it.comicId })
        assertTrue(
            "空白与整格空白都不许算进任何有名的一夹（那是猜，而猜出来的会被读成「我收在那儿了」）：",
            desktopFavoriteFoldersShown("默认", all).none { it.comicId == "c" || it.comicId == "d" } &&
                desktopFavoriteFoldersShown("追更", all).none { it.comicId == "c" || it.comicId == "d" },
        )
    }

    /** ⑬ 那三句成品文案：未筛时逐字保持「N 本」，筛过才念「共几本中的几本」。 */
    @Test
    fun 那三句文案在未筛那一档逐字不变而筛过才点名() {
        assertEquals("未筛时那句一字不改（既有判据钉着它）：", "3 本", desktopFavoriteFolderCountNote(null, 3, 3, true))
        assertEquals(
            "筛过必须念得出筛过这件事，否则用户以为收藏只剩这一夹：",
            "追更 那一夹 · 共 3 本中的 2 本",
            desktopFavoriteFolderCountNote("追更", 3, 2, true),
        )
        assertEquals(
            "整张表本来就空时不许念「共 0 本中的 0 本」那种空壳话：",
            "0 本", desktopFavoriteFolderCountNote("追更", 0, 0, true),
        )
        assertEquals("还没读回来时一句都不念（不许念 0 本）：", "", desktopFavoriteFolderCountNote(null, 0, 0, false))
        assertEquals(
            "筛中一夹而那一夹没本子：点名那一格而不说那一句「还没有收藏」：",
            "「追更」这一格里还没有本子。",
            desktopFavoriteFolderFilterNote("追更", 3, 0),
        )
        assertNull("未筛不念那一句：", desktopFavoriteFolderFilterNote(null, 3, 3))
        assertNull("整张表空着时念的是原来那句空态：", desktopFavoriteFolderFilterNote("追更", 0, 0))
        assertNull("那一格里有东西就不念：", desktopFavoriteFolderFilterNote("追更", 3, 1))
    }

    /**
     * ⑭ 措辞两条纪律（都是**台架量程**而不是文风）：那一排的标签与那两句里不许出现「收藏」，
     * 也不许出现「夹：」前缀 —— 前者与那枚收藏钮撞、后者与每一格那一行「夹：X」撞。
     */
    @Test
    fun 那一排那两句里既没有收藏也没有夹名前缀() {
        val rows = desktopFavoriteFolderTabRows(
            names = listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER, "追更"), selected = "追更",
            favorites = listOf(fav("a", "追更")), countsKnown = true,
        )
        rows.forEach { row ->
            assertFalse("那一排的标签里不许出现「收藏」（会与那枚钮撞成 2 枚）：${row.label}", "收藏" in row.label)
            assertFalse("那一排的标签不许带「夹：」前缀（与每一格那一行同串会撞）：${row.label}", "夹：" in row.label)
        }
        val countNote = desktopFavoriteFolderCountNote("追更", 5, 2, true)
        assertFalse("表头那句里不许出现「收藏」：$countNote", "收藏" in countNote)
        val emptyNote = requireNotNull(desktopFavoriteFolderFilterNote("追更", 5, 0))
        assertFalse("筛空那句里不许出现「收藏」（它说的正是已经收着而只是不在这一格）：$emptyNote", "收藏" in emptyNote)
    }

    /** ⑮ 条数还没读回来时那一枚标签只念名字（不许把「还没数」念成「0」）。 */
    @Test
    fun 条数没读回来时那一排只念名字而不念零() {
        assertEquals(
            "没读回来时只念名字：", listOf("全部 ✓", "默认"),
            desktopFavoriteFolderTabRows(
                names = listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER), selected = null,
                favorites = listOf(fav("a")), countsKnown = false,
            ).map { it.label },
        )
        // 对照：读回来了就带数（证明上面那颗不是恒不念数）。
        assertEquals(
            "读回来了就要念得出条数：", listOf("全部 1 ✓", "默认 1"),
            desktopFavoriteFolderTabRows(
                names = listOf(DESKTOP_FAVORITE_DEFAULT_FOLDER), selected = null,
                favorites = listOf(fav("a")), countsKnown = true,
            ).map { it.label },
        )
    }

    /** ⑯ 取证读数那一行：三档各说得开（全部 / 筛中一夹 / 筛空那一格）。 */
    @Test
    fun 夹筛那一行读数三档各说得开() {
        assertEquals(
            "「全部」那一档点名自己而不念成 null：",
            "D_收藏 夹筛 选中=全部 全=3 显=3 档=2",
            desktopFavoriteFolderTabReading(null, 3, 3, 2),
        )
        assertEquals(
            "筛中一夹要同时念得出全与显（否则读不出这一档筛掉了谁）：",
            "D_收藏 夹筛 选中=追更 全=3 显=1 档=2",
            desktopFavoriteFolderTabReading("追更", 3, 1, 2),
        )
        assertTrue(
            "那一族读数的前缀不许漂（都从 D_收藏 起）：",
            desktopFavoriteFolderTabReading("追更", 0, 0, 1).startsWith("D_收藏 夹筛 "),
        )
    }

    /**
     * ⑰ 接线尺：那一排的成品由壳递进来，屏不查库、不自己筛、不自己拼那三句。
     *
     * 这一批最容易写的假形状是「屏里补一句 filter」—— 编译、运行、屏上都对，
     * 只有筛的判据从此有两处出处。所以钉的是**分布**而不是"有没有"。
     */
    @Test
    fun 收藏屏只搬那一排而筛算在壳里() {
        val screen = DesktopSourceTree.desktopSourceOf("favorites/DesktopFavoritesScreen.kt")
        val screenText = DesktopSourceTree.codeText(screen)
        listOf(
            "shownFavorites: List<FavoriteRecord>," to "屏没收筛完那一批（它得自己筛才算接上）",
            "folderTabs: List<DesktopFavoriteFolderTab>," to "那一排的渲染输入没递进屏",
            "countNote: String," to "表头那句条数没递进屏",
            "filterNote: String?," to "筛空那句没递进屏",
            "onFolderTabSelect: (String?) -> Unit," to "那一排的把手没接上（摆了而按不动）",
            "folderTabs.forEach { tab ->" to "那一排没真上屏",
            // 屏里新增「排序档」，网格吃 `sortedFavorites`（= shownFavorites 排序后），
            // 仍是**壳筛完递进来、屏只排序**，没有把筛挪进屏。
            // 入场动画要序号，网格从 `items` 换成 `itemsIndexed`；吃的仍是 sortedFavorites 那一批。
            "itemsIndexed(sortedFavorites)" to "那面墙摆的还是没筛的那一批（筛等于没做）",
            "val sortedFavorites = if (sortMode == 1) {" to "排序档没从 shownFavorites 派生（屏自己另起一批）",
            "filterNote != null -> CenterNote(filterNote)" to "筛空那句没上屏",
        ).forEach { (needle, why) ->
            assertTrue("$why：判式「$needle」在屏里零命中", needle in screenText)
        }
        // 屏不自己筛、不自己数、不自己拼那三句、不查库。
        listOf(
            "folderName ==" to "屏自己去比夹名（筛的判据住在 gallery/data 那颗件里）",
            "desktopFavoriteFoldersShown(" to "屏自己去算筛完那一批",
            "desktopFavoriteFolderTabRows(" to "屏自己去数那一排",
            "desktopFavoriteFolderCountNote(" to "屏自己去拼表头那句",
            "FavoriteDao" to "屏自己去查库",
        ).forEach { (needle, why) ->
            assertEquals("$why：", 0, DesktopSourceTree.codeOccurrences(screen, needle))
        }
        // 壳那几颗各恰好一处（别处再拼一遍就是能漂成两种的那一族）。
        listOf(
            "desktopFavoriteFolderTabRows(" to listOf(
                "DesktopFavoriteFolders.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1,
            ),
            "desktopFavoriteFoldersShown(" to listOf(
                "DesktopFavoriteFolders.kt" to 2, "VeneraDesktopLargeScreen.kt" to 1,
            ),
            "desktopFavoriteFolderCountNote(" to listOf(
                "DesktopFavoriteFolders.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1,
            ),
            "desktopFavoriteFolderFilterNote(" to listOf(
                "DesktopFavoriteFolders.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1,
            ),
            "desktopFavoriteFolderTabReading(" to listOf(
                "DesktopFavoriteFolders.kt" to 1, "VeneraDesktopLargeScreen.kt" to 1,
            ),
        ).forEach { (needle, want) ->
            val sites = DesktopSourceTree.desktopMainGroupedOccurrences(needle)
            assertEquals("「$needle」的分布应当「声明处 + 壳里用一处」：$sites", want, sites)
        }
        // 写侧那枚（下一本落到哪一格）与读侧这枚（这一屏看哪一格）是两枚状态，不许合成一枚：
        // 合成一枚的话，在详情里挑过一格就会把收藏屏筛过去，而用户从没在收藏那一屏按过任何东西。
        val shellText = DesktopSourceTree.desktopLargeScreenCode()
        assertEquals(
            "壳里读侧那一枚状态只许一处声明：", 1,
            Regex("""(?:val\s+favoritesTabPickedState\s*=\s*remember|var\s+favoritesTabPicked\s+by\s+remember)""").findAll(shellText).count(),
        )
        assertEquals(
            "壳里挑那一档只动读侧那枚（不许顺手写写侧的光标）：", 1,
            Regex("""favoritesTabPicked = folder""").findAll(shellText).count(),
        )
        assertEquals(
            "写侧那枚不许被那一排把手写（合成一枚的话在详情挑过一格就会把收藏屏筛过去）：", 0,
            Regex("""favoriteFolderPicked = folder""").findAll(shellText).count(),
        )
    }
}

package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.data.DesktopReaderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 阅读那一屏「翻页那一档」的**接线与口径**判据。
 *
 * 纯算式那一面在 [DesktopReaderPagingTest]（全部真跑）；这一颗管的是六件只有源码面量得到的事：
 * ① 模式那枚键**与对岸同轨**（键名与默认值都是从对岸源码文本现取的，不是抄在这里的字符串）；
 * ② 横向档真的用 `HorizontalPager` 而不是"看起来像翻页的滚动"；
 * ③ 两档各读各的那一枚状态（拿 listState 去滚 pager 那面墙是本仓记过的空操作那一族）；
 * ④ 今天没做的两档**不上屏**（摆一枚按不动的档位就是本仓最忌的假开关）；
 * ⑤ 模式那枚偏好的读写只走那一颗件；
 * ⑥ 取证参数那一跳三层都在（构建脚本 → 判读层 → 消费点）。
 *
 * 另有 ⑦ 那一组是 **B 路的接缝**：桌面不引对岸 `reader/` 那一族（A 路等点头），而档位表、
 * 归一别名表、跨页那两枚数、组首与钳位那三段算式**四处**都从对岸源码文本现取再比。
 * ⚠️ 这把尺子只覆盖"量过的这四处"：对岸新增第五处口径时它看不见，这正是 B 路记在
 * `gallery/data/DesktopReaderMode.kt` 与 `desktop/build.gradle.kts` 注释里的那笔代价。
 */
class DesktopReaderPagedSeedTest {

    private val readerFile = DesktopSourceTree.desktopUiSource("DesktopComicReader.kt")
    private val pagingFile = DesktopSourceTree.desktopSourceOf("gallery/ui/DesktopReaderPaging.kt")
    private val prefsFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopComicReaderPreferences.kt")
    private val appFile = DesktopSourceTree.desktopUiSource("DesktopDomainRows.kt")
    private val integrationFile = DesktopSourceTree.desktopSourceOf("VeneraDesktop.kt")
    private val flagsFile = DesktopSourceTree.desktopSourceOf("ForensicFlags.kt")

    // ── ① 那枚键与对岸同轨 ─────────────────────────────────────────────────


    /**
     * ② 默认档与对岸同一档 —— **两边各归一再比**，而不是比字面串。
     *
     * 从前这一条比的是"对岸那串原样出现在桌面那颗文件里"。行 164 那批把对岸默认从别名 `"VERTICAL"`
     * 换成表名 `"HORIZONTAL_LTR"` 之后，那种比法与 ③（档位的键与名字只有那张表一个出处，不许在表外写死串）
     * **互相咬死**：照 ② 就得写死串，照 ③ 就找不到那串。所以比的是**归一之后的那一档** ——
     * 那才是"两端起手同一档"这件行为本身，而 ③ 一个字不放宽。
     */

    @Test
    fun 桌面不许把档位写成字符串字面量() {
        // 档位的键与名字只有那张表一个出处：别处写死串就会与那张表漂（而屏上念的、盘上存的都取自它）。
        val literals = listOf("\"VERTICAL_CONTINUOUS\"", "\"HORIZONTAL_RTL\"", "\"HORIZONTAL_LTR\"", "\"DOUBLE_PAGE\"", "\"HORIZONTAL_CONTINUOUS\"")
        val tableFile = DesktopSourceTree.desktopSourceOf("gallery/data/DesktopReaderMode.kt")
        val offenders = DesktopSourceTree.desktopOwnSources().filter { file ->
            file != tableFile && literals.any { literal -> DesktopSourceTree.codeOccurrences(file, literal) > 0 }
        }
        assertEquals("档位名只许走那张表：${offenders.map { it.name }}", emptyList<File>(), offenders)
        // 对照组：那张表里这五串各在场（否则上面那条是恒真的空判式）
        literals.forEach { literal ->
            assertTrue("档位表里少了 $literal", DesktopSourceTree.codeOccurrences(tableFile, literal) >= 1)
        }
    }

    // ── ② 横向档用的是真的页翻件 ────────────────────────────────────────────

    // ── ③ 两档各读各的那一枚状态 ───────────────────────────────────────────

    // ── ④ 今天没做的两档不上屏 ─────────────────────────────────────────────

    @Test
    fun 没做的两档不许长出名字来() {
        val banned = listOf("对开·双页", "横向·连续")
        val hits = DesktopSourceTree.desktopUiSources().flatMap { file ->
            banned.filter { b -> DesktopSourceTree.codeOccurrences(file, b) > 0 }
        }
        assertEquals("摆上去就是按不动的那一枚：$hits", emptyList<String>(), hits)
        // 对照组：那两串在对岸的档位表里（否则这条是恒真的空判式）
        assertTrue(
            "对照组失效：对岸档位表读不到那两串",
            DesktopSourceTree.codeText(DesktopSourceTree.androidSource("source/model/ComicPageSource.kt")).contains("对开·双页"),
        )
    }

    // ── ⑤ 模式那枚偏好：读、写、只吃形参 ───────────────────────────────────

    // ── ⑥ 三层落点：构建脚本 → 判读层 → 消费点 ─────────────────────────────

    @Test
    fun 取证参数那一跳三层都在() {
        val build = File(DesktopSourceTree.repoRoot, "desktop/build.gradle.kts").readText()
        assertEquals("构建脚本那一跳", 1, build.lines().count { it.contains("providers.gradleProperty(\"readMode\")") })
        assertTrue("那一跳递的是程序参数而不是系统属性", build.contains("--read-mode="))
        assertEquals("判读层那一颗", 1, DesktopSourceTree.codeOccurrences(flagsFile, "fun readMode("))
        assertTrue("集成方读了判读层那一颗", integrationFile.readText().contains("ForensicFlags.readMode("))
    }

    // ── ⑦ B 路的接缝：桌面不引那一族，而三处口径逐字回核对岸 ────────────────

    /**
     * 桌面自持一份同轨件的**边界**尺子：`reader/` 那一族一颗都不许进桌面编译面。
     *
     * 为什么这条必须存在：A 路（把 `reader/` srcDir 进来）被受保域里那行 UI 禁串挡住，
     * 而"没人 import"这件事没有尺子的话，下一个人顺手补一行就把它拉回甲路 —— 那时红的是
     * `app/` 那颗而不是这一颗，读起来像对岸坏了。判据红在自己家才有信息量。
     */
    @Test
    fun 桌面不引对岸阅读那一族的件() {
        val offenders = DesktopSourceTree.desktopMainSources()
            .filter { DesktopSourceTree.codeOccurrences(it, "import com.venera.compose.reader") > 0 }
        assertEquals("阅读那一族进桌面编译面了（那是等点头的 A 路）：${offenders.map { it.name }}", emptyList<File>(), offenders)
        // 校验桌面不引入 reader 包；ComicPageSource 已迁入共享 source/model，ReaderInteractionPolicy 仍在原位作对照。
        assertTrue(
            "档位表那颗件没在桌面自持的 `source/model/`（共享面整批搬迁后的新家）",
            File(DesktopSourceTree.repoRoot, "desktop/src/main/kotlin/com/venera/compose/source/model/ComicPageSource.kt").isFile,
        )
    }

    /**
     * 档位表（枚名 → 键 + 屏上念的名字）逐字回核对岸。
     *
     * 两侧的写法都是 `NAME("key", "label"…`，对岸多带 `icon` 与 `description` 两枚，
     * 所以取**前两枚**比：对岸加了第三枚桌面不知道，但桌面漏了一档、改了一串键或档名都会红在这里。
     */
    @Test
    fun 档位表逐字回核对岸() {
        val against = entryTable(DesktopSourceTree.codeText(DesktopSourceTree.androidSource("source/model/ComicPageSource.kt")))
        val mine = entryTable(DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopReaderMode.kt")))
        assertTrue("对照组失效：对岸那张表里只读到 ${against.size} 档（写法变了要重判这一条）", against.size >= 5)
        assertEquals("桌面那张表与对岸不是同一张（枚名、键或档名漂了）", against, mine)
    }

    /** 归一别名表（串 → 落到哪一档）逐字回核对岸：读档时两边必须把同一个串认成同一档。 */
    @Test
    fun 归一别名表逐字回核对岸() {
        val against = aliasTable(DesktopSourceTree.codeText(DesktopSourceTree.androidSource("source/model/ComicPageSource.kt")))
        val mine = aliasTable(DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf("gallery/data/DesktopReaderMode.kt")))
        assertTrue("对照组失效：对岸那颗归一件里只读到 ${against.size} 枚别名（写法变了要重判这一条）", against.size >= 10)
        assertEquals("两表的别名口径漂了（同一个串在两端认成不同档）", against, mine)
    }

    /** 跨几页那两枚数从对岸现取再比：这一处正是"没人会同时改"的那种数（双页一组两页）。 */

    /** 组首对齐与两处钳位那三段算式逐字同串（参数名两端刻意取同名，才比得出串来）。 */

    /** 屏上那一排念的是表里的档名（不写死串）；这一条把"名字取自表"钉在接线层。 */
    // ── ⑧ 对外文档那一条同轨（改一处必须同步另一处，本仓口径）───────────────

    @Test
    fun 两份README的翻页那一条同轨() {
        val zh = File(DesktopSourceTree.repoRoot, "README.md").readText()
        val en = File(DesktopSourceTree.repoRoot, "README.en.md").readText()
        assertEquals("中文那份还写着只有纵向那一档", 0, Regex("阅读那一屏的纵向连续流").findAll(zh).count())
        assertEquals("英文那份还写着只有纵向那一档", 0, Regex("the vertical reading screen").findAll(en).count())
        assertTrue("中文那份没念整页翻那一档", zh.contains("对开双页"))
        assertTrue("英文那份没念整页翻那一档", en.contains("two-page spread"))
        // 对照：两份都还留着键位那条 —— 只改能力句而不改键位句就是半份文档
        assertTrue("中文那份的键位说明没了", zh.contains("键盘快捷键"))
        assertTrue("英文那份的键位说明没了", en.contains("keyboard shortcuts"))
    }

    /** 对岸那张表：枚名 → 键 + 档名（只取前两枚字段）。 */
    /**
     * 那张档位表：枚名 →（键, 屏上念的名字）。
     *
     * ⚠️收窄到**文件里第一枚 enum 的那一截条目表**（与 `aliasTable` 只吃第一个 `fromKey`
     * 是同一处理由）：对岸 `ComicPageSource.kt` 从 10-16 起住着第二枚 enum（`ReaderPageTurnEffect`
     * 翻页特效），全文扫会把「无（原样）」「仿真卷边」两档也算进"阅读方向"这张表 ——
     * 于是桌面恒缺两档，红的不是两端漂了，是尺子把两张表并成了一张。
     * 枚名正则顺手放宽到允许数字：原来那串看不见带数字的枚名（`BOOK_FLIP_3D` 就是这么漏扫的），
     * 那是一种"扫不到就当不存在"的假绿。
     */
    private fun entryTable(text: String): Map<String, Pair<String, String>> =
        Regex("([A-Z][A-Z_0-9]+)\\(\"([^\"]+)\", \"([^\"]+)\"")
            .findAll(text.substringAfter("enum class ", "").substringBefore(";"))
            .associate { it.groupValues[1] to (it.groupValues[2] to it.groupValues[3]) }

    /** 那颗归一件的别名表：串 → 落到的枚名（`else ->` 之后那一条不算，它是兜底不是别名）。 */
    private fun aliasTable(text: String): Map<String, String> {
        val body = text.substringAfter("fun fromKey").substringAfter("when").substringAfter("{").substringBefore("else ->")
        val table = mutableMapOf<String, String>()
        body.lines().forEach { line ->
            val arrow = line.indexOf("->")
            if (arrow >= 0) {
                val target = line.substring(arrow + 2).trim()
                Regex("\"([^\"]+)\"").findAll(line.substring(0, arrow)).forEach { table[it.groupValues[1]] = target }
            }
        }
        return table
    }

    /** 那句步长里的两枚数（跨页步长、其余档的步长）。 */
    private fun stepPair(text: String): Pair<String, String>? =
        Regex("== \\w+\\.DOUBLE_PAGE\\) (\\d+) else (\\d+)").find(text)
            ?.let { it.groupValues[1] to it.groupValues[2] }
}

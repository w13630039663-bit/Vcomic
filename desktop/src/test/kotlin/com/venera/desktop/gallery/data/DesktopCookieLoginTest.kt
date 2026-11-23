package com.venera.desktop.gallery.data

import com.venera.desktop.gallery.ui.DesktopSourceTree
import com.venera.engineprobe.DesktopCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 钉住「手填凭据那一趟」（网页登录在桌面上**成立**的那一形）。
 *
 * ## 为什么批 88 那颗件今天才有尺子
 *
 * §88 交付时只有那颗件本身（跑一趟 + 回核），**一条行为尺都没写** —— 屏不在活路径上，
 * 尺子没有落点。把它接进渲染路，这一批正是补账的时机。
 *
 * ## 判据（六格）
 *
 * ① `payload` 按**脚本名单的次序**摊平，而不是按用户填写的插入次序（源那句 `validate(values)`
 *    靠下标取名字，顺序错 = 把四枚装成任意一套）；
 * ② 没填的那一枚交回空串而不是 null（`values` 是 `List<String>`，null 会把整趟打成异常）；
 * ③ 三档回话**两两不同**，且中间那一档（源认了而盘上一枚没落）**绝不出现「已登录」**；
 * ④ 回核真的**读盘**：写完能数到、清完数不到（内存清了盘上没清 = 两条轨，与罐那把尺同一判据族）；
 * ⑤ 屏上摆不摆「登录」那枚，由**脚本自己那份名单**决定（空表不摆 —— 摆一枚按不动的框比不摆更坏）；
 * ⑥ 名单从脚本文本现取，且 `fields` 只认 `loginWithCookies` 之后那一窗口（别处的 `fields:` 不许被抓来）。
 */
class DesktopCookieLoginTest {

    private val ehat = "https://forums.e-hentai.org/index.php".toHttpUrl()

    private fun tempRoot(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "venera-cookie-login-$name-${System.nanoTime()}")
        assertTrue("建不出临时目录 $dir", dir.mkdirs())
        dir.deleteOnExit()
        return dir
    }

    private fun cookie(name: String, value: String): Cookie =
        Cookie.Builder().name(name).value(value).domain("e-hentai.org").secure().build()

    /** ① + ② 摊平那一颗：次序是源认的口径，缺的那一枚是空串。 */
    @Test
    fun 摊平按名单次序而不是用户填写的次序() {
        val fields = listOf("ipb_member_id", "ipb_pass_hash", "igneous", "star")
        // 用户先填 star、再填 ipb_pass_hash（屏上格子是竖着排的，字典那份顺序不是源认的那一份）。
        val draft = mapOf("star" to "G", "ipb_pass_hash" to "B", "igneous" to "C")
        assertEquals(
            "摊出来的实参表不按名单次序：源那句 validate(values) 会按 fields[i] 取名字，装错就是错",
            listOf("", "B", "C", "G"),
            DesktopCookieLogin.payload(fields, draft),
        )
    }

    /** ② 空那一格单独钉：缺键与填了空串都得是空串（null 会把那一趟打成异常）。 */
    @Test
    fun 没填的那一枚交回空串而不是缺位() {
        val values = DesktopCookieLogin.payload(listOf("a", "b"), mapOf("a" to "x"))
        assertEquals("少了一枚（源那边按下标取，缺位直接越界）：", 2, values.size)
        assertEquals("空着那一枚不是空串：", "", values[1])
    }

    /** ③ 三档两两不同，且中间那一档不许被「已登录」盖掉。 */
    @Test
    fun 三档回话两两不同而中间那一档绝不说已登录() {
        val fields = listOf("ipb_member_id", "ipb_pass_hash")
        val refused = DesktopCookieOutcome(false, "站方说这两枚不对", fields, mapOf("ipb_member_id" to 1))
        val notLanded = DesktopCookieOutcome(true, "源认了这批凭据", fields, mapOf("ipb_member_id" to 0, "ipb_pass_hash" to 0))
        val landed = DesktopCookieOutcome(true, "源认了这批凭据", fields, mapOf("ipb_member_id" to 1, "ipb_pass_hash" to 1))
        val notes = listOf(refused, notLanded, landed).map(DesktopCookieLogin::screenNote)
        assertEquals("三档里有两档说了同一句话：", 3, notes.distinct().size)
        assertTrue("没过那一档没点名成因：" + notes[0], notes[0].contains("站方说这两枚不对"))
        // 这一档是整批存在的理由：源认了而盘上没落 = 重启就丢。它被"登录成功"盖掉就是本仓最忌的假开关。
        assertFalse("源认了而盘上一枚没落，屏上却念了已登录：" + notes[1], notes[1].contains("已登录"))
        assertTrue("盘上落成了却没数出枚数：" + notes[2], notes[2].contains("已登录") && notes[2].contains("2 枚"))
    }

    /** ④ 回核真的读盘：写完数得到、清完数不到（内存与盘上同一颗件才算量过）。 */
    @Test
    fun 回核读的是盘上那一份而不是刚才那颗罐的记忆() {
        val dir = tempRoot("tally")
        val store = DesktopCookieJar.fileIn(dir)
        val fields = listOf("ipb_member_id", "ipb_pass_hash")
        DesktopCookieJar(store).saveFromResponse(ehat, listOf(cookie("ipb_member_id", "42")))
        // 新起一颗实例去读：常驻那颗内存里那份不算证据（批 88「自己给自己签字」那条）。
        val tallied = DesktopCookieLogin.tallyOnDisk(dir, fields)
        assertEquals("盘上落了 1 枚而回核数出别的：" + tallied, 1, tallied["ipb_member_id"])
        assertEquals("没落的那枚被数成了有：" + tallied, 0, tallied["ipb_pass_hash"])
        DesktopCookieJar(store).clearForUrl(ehat)
        val after = DesktopCookieLogin.tallyOnDisk(dir, fields)
        assertEquals("清了而盘上还留着（两条轨）：" + after, 0, after.values.sum())
    }

    /** ④′ 落点只从 `fileIn` 拿：这里重拼一遍就会读错那份文件。 */
    @Test
    fun 回核与宿主读同一颗文件() {
        val dir = tempRoot("samefile")
        assertEquals(
            "回核自己拼了文件名（宿主写 A、屏上读 B 时那句「重启也带得上」就成了假话）：",
            File(dir, "cookies.json").path,
            DesktopCookieJar.fileIn(dir).path,
        )
    }

    /** ⑤ 门只在脚本给了名单时才摆（源码尺：既不许漏掉那一问，也不许摆一枚按不动的框）。 */
    @Test
    fun 登录那一枚只在脚本给了名单时才摆() {
        val screen = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("sourcemanage/DesktopSourceManageScreen.kt"),
        )
        // 源管理改主从双栏，登录入口挪进右栏「账号」卡。
        // 门**没有换**：屏侧读的是 account.canLoginWithCookies（壳按清单里那份名单现算的
        // 成品布尔 —— 名单为空 ⇒ false ⇒ 那一格整颗不摆，与旧 row.cookieFields.isNotEmpty() 同一判）。
        assertTrue(
            "「登录」那一格不再问 canLoginWithCookies（名单为空也摆 ⇒ 用户按进去是一排零个格子）：",
            "canLogin && onSourceLogin != null" in screen,
        )
        // 那一枚是**双档门槛**：装配点没接上（回调为 null）时同样不摆。回调带 `? = null` 默认值
        // 与 §119 那条不冲突 —— 那条禁的是「默认 `{}` 的必传把手」（忘了接也不红还摆着一枚能按的钮），
        // 而这里默认值让把手**消失**，比红更保守。所以这一问钉的是"壳里真的接了"。
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue(
            "壳里没给源管理屏递 `onSourceLogin`（那枚把手在真跑的屏上压根不会出现）：",
            "onSourceLogin = if (loginWithCookies == null && loginWithPassword == null)" in shell,
        )
        assertTrue(
            "壳里切进登录屏时没把脚本那份名单一起递过去（屏上会摆出一排没有格子的登录）：",
            "fields = row?.cookieFields.orEmpty()" in shell,
        )
    }

    /** ⑥ 名单从脚本文本现取，且只认 `loginWithCookies` 之后那一窗口。 */
    @Test
    fun 名单从脚本文本现取而不写死在桌面() {
        val script = """
            class T extends ComicSource {
                name = "t"
                account = {
                    loginWithCookies: {
                        fields: ["ipb_member_id", "ipb_pass_hash", "igneous", "star"],
                        validate: async (values) => true,
                    },
                }
            }
        """.trimIndent()
        assertEquals(
            "脚本自己声明的四枚没读全（站方改名单时屏上照旧摆老几格，答案永远过不了）：",
            listOf("ipb_member_id", "ipb_pass_hash", "igneous", "star"),
            DesktopSourceNames.cookieFieldsOf(script),
        )
        // 没有那一形 ⇒ 空表（屏上据此**不摆**把手）。
        assertEquals(
            "这颗源没声明那一形却读出了名单：",
            emptyList<String>(),
            DesktopSourceNames.cookieFieldsOf("class A extends ComicSource { }"),
        )
        // 窗口之外的那一枚 `fields:` 不许被抓来（选项列表那一族同名）。
        // 垫的这一段要**确实**超过那 400 字符窗口（现量每轮 33 字符，25 轮 ≈ 825）。
        val far = "account = { loginWithCookies: { validate: async (v) => true } } " +
            "// 垫长把后面的 fields 推出窗口之外。".repeat(25) +
            "explore = { sorts: [ { name: \"a\", fields: [\"not\", \"cookies\"] } ] }"
        val got = DesktopSourceNames.cookieFieldsOf(far)
        assertEquals("把别处的 fields 当成 cookie 名单抓了：", emptyList<String>(), got)
    }
}

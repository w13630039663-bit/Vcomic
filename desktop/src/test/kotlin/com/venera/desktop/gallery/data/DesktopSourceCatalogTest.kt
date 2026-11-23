package com.venera.desktop.gallery.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批 83 · 源管理那一屏的**纯数据层**（不启引擎、不开库）。
 *
 * ## 为什么这一颗要存在
 *
 * 桌面今天只认**包内**那 34 颗源脚本（`D_资源 来源=包内 /sources/ 清单=34 颗`），而"选哪一颗源"
 * 是命令行上的一枚 `-Pkey=`。要把它变成屏上的一行一行，缺的第一格不是界面而是**名字**：
 * 引擎装载那趟确实回一个显示名（`EngineSession.load` 的返回值），可"列表"这一屏若为了报名字
 * 就去起一颗 GraalJS + 整源装载，就是批 82.7 刚立的那条 `desktopComicEngineNeeded` 反着来。
 * 所以名字从脚本头部**现取**，而取不到时不许编一个（编出来的站名会被当成站方给的）。
 *
 * ## 口径的回核对象
 *
 * 对岸那颗 `JsComicSource` 的 `name` 就是源脚本里那一行赋值 —— 这里逐字回核 `jm.js`，
 * 不自造第二套解析（同一份脚本两处读成两种形状，就是"桌面看到的与手机不同"那一族缺陷的成因）。
 */
class DesktopSourceCatalogTest {

    @Test
    fun 名字从脚本头部现取而口径回核对岸那一行() {
        // 与 app/src/main/assets/sources/jm.js:5 逐字同串（那行是 `    name = "禁漫天堂"`）
        val jm = DesktopSourceCatalogTest::class.java.classLoader
            .getResource("sources/jm.js")?.readText()
        val script = jm ?: "    name = \"禁漫天堂\"\n"
        val hit = DesktopSourceNames.displayNameOf(script)
        val expected = if (jm == null) "禁漫天堂" else Regex("""^\s*name\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
            .find(jm)!!.groupValues[1]
        assertEquals("名字必须等于脚本里那一行赋值（对岸读的就是它）：$hit", expected, hit)
    }

    @Test
    fun 头部没有那一行时不猜名字() {
        assertNull(
            "取不到就交回 null 让屏上念 key —— 编一个站名等于替站方作伪证",
            DesktopSourceNames.displayNameOf("let x = 1\nfunction fetch() {}\n"),
        )
        assertNull("空脚本也不许得到一个假名", DesktopSourceNames.displayNameOf(""))
    }

    @Test
    fun 只认顶层那一行赋值而分区名不算() {
        // 真形状：`name = "禁漫天堂"` 在前，`name: "每週必看"` 在后（jm.js:433 那种是**分区**名）。
        // 冒号形式不是源名 ⇒ 只有冒号形式时算没取到。
        val onlySections = "    name: \"每週必看\",\n    name: \"本月排行\",\n"
        assertNull("分区名不许当源名", DesktopSourceNames.displayNameOf(onlySections))
    }

    @Test
    fun 关着的那一颗只吃掉启用格而不把行抹掉() {
        val rows = DesktopSourceCatalog.rows(
            keys = listOf("jm", "goda"),
            names = mapOf("jm" to "禁漫天堂"),
            enabled = setOf("jm"),
        )
        assertEquals("两颗源就是两行（关着不是删除）", 2, rows.size)
        assertEquals("jm", rows[0].key)
        assertEquals("禁漫天堂", rows[0].title)
        assertTrue("开着的那颗必须是可以用的", rows[0].enabled)
        assertFalse("没开着的那颗要标着关", rows[1].enabled)
        // 取不到名字的那一行念 key，而不是念一个空标题
        assertEquals("goda", rows[1].title)
    }

    @Test
    fun 关掉当前在用的那颗时读数念得出而屏上不静默() {
        val reading = DesktopSourceCatalog.reading(
            sourceKey = "jm",
            rows = DesktopSourceCatalog.rows(listOf("jm"), emptyMap(), enabled = emptySet()),
        )
        assertTrue("那一行要念得出在用哪一颗：$reading", reading.startsWith("D_源管理"))
        assertTrue("关掉了正在用的那颗必须念出来（否则屏上摆着这一颗而它是关的）：$reading", reading.contains("停用=在用"))
        assertTrue(reading.contains("jm"))
    }

    @Test
    fun 开启数要念得出而没关时不许念停用等于在用() {
        val reading = DesktopSourceCatalog.reading(
            sourceKey = "jm",
            rows = DesktopSourceCatalog.rows(listOf("jm", "goda"), mapOf("jm" to "禁漫天堂"), enabled = setOf("jm", "goda")),
        )
        assertTrue("颗数要念得出：$reading", reading.contains("清单=2"))
        assertTrue("开启数要念得出（翻向后屏上那句「N/34 开启」吃它）：$reading", reading.contains("开着=2"))
        assertFalse("没关掉在用那颗就不许念「停用=在用」", reading.contains("停用=在用"))
    }

    // ───────────────────── 选源：没开着的不许被装，落回要念得出 ─────────────────────

    @Test
    fun 没开着的那颗不许被选为当前源而落回要念得出落点() {
        val outcome = DesktopSourceSelection.resolve(keys = listOf("jm", "goda"), enabled = setOf("goda"), requested = "jm")
        assertEquals("关着还照它装载 = 那枚开关是假的", DesktopSourcePick.FellBack, outcome.kind)
        assertEquals("goda", outcome.key)
        assertTrue("回落那一句要念得出从谁落到谁：${outcome.reading}", outcome.reading.contains("jm") && outcome.reading.contains("goda"))
    }

    @Test
    fun 开着的那颗照它走而不许多落一句回落() {
        val outcome = DesktopSourceSelection.resolve(listOf("jm", "goda"), enabled = setOf("jm"), requested = "jm")
        assertEquals(DesktopSourcePick.Exact, outcome.kind)
        assertEquals("jm", outcome.key)
        assertFalse("没回落就不许念回落（读数多一句假话与少一句实话同罪）", outcome.reading.contains("回落"))
    }

    @Test
    fun 全没开着时不许硬挑一颗关着的要交回空手() {
        val outcome = DesktopSourceSelection.resolve(listOf("jm", "goda"), enabled = emptySet(), requested = "jm")
        assertEquals("一颗开着的都没有 ⇒ 不能假装某颗在用", DesktopSourcePick.NoneEnabled, outcome.kind)
        assertEquals("", outcome.key)
        assertTrue("那一句要念得出是「全都关了」而不是「没找到」：${outcome.reading}", outcome.reading.contains("全关着"))
    }

    @Test
    fun 请求的键压根不存在时念没这颗而不算回落() {
        // 与"没开着"分开一档：拼错的键与用户真关了那颗，修法完全不同。
        val outcome = DesktopSourceSelection.resolve(listOf("jm", "goda"), enabled = setOf("jm", "goda"), requested = "nope")
        assertEquals(DesktopSourcePick.UnknownKey, outcome.kind)
        assertEquals("jm", outcome.key)
        assertTrue("那一句要念得出是「没这颗」：${outcome.reading}", outcome.reading.contains("没这颗"))
    }

    @Test
    fun 开启与关闭是同一颗开关的两档而不许叠成两份() {
        val on = DesktopSourceSelection.toggle(enabled = setOf("goda"), key = "jm")
        assertEquals(setOf("goda", "jm"), on)
        val off = DesktopSourceSelection.toggle(enabled = on, key = "jm")
        assertEquals("再点一次必须回到原样（叠成两份就永远开不了）", setOf("goda"), off)
    }

    @Test
    fun 只剩一颗开着的那一颗不许再关() {
        // 全关之后 resolve 只能交回空手 ⇒ 漫画域一屏都装不起来，那是一枚把自己锁在门外的开关。
        assertFalse(
            "最后一颗开着的那颗必须关不掉",
            DesktopSourceSelection.canDisable(listOf("jm", "goda"), enabled = setOf("jm"), key = "jm"),
        )
        assertTrue("还剩两颗以上开着时可关", DesktopSourceSelection.canDisable(listOf("jm", "goda"), enabled = setOf("jm", "goda"), key = "jm"))
        assertTrue("已经关着的那颗不存在「再关一遍」", DesktopSourceSelection.canDisable(listOf("jm"), enabled = emptySet(), key = "jm"))
    }

    // ───────────────────── 版本那一行─────────────────────

    @Test
    fun 版本从脚本头部现取而取不到时交回空串() {
        val script = "    name = \"禁漫天堂\"\n    key = \"jm\"\n    version = \"1.4.1\"\n"
        assertEquals("1.4.1", DesktopSourceNames.versionOf(script))
        assertEquals("没声明版本 = 空串（屏上那一格不摆，编版本号与编站名同罪）", "", DesktopSourceNames.versionOf("    name = \"x\"\n"))
    }

    // ───────────────────── 登录能力现取（账号密码 / 网页）─────────────────────

    /**
     * 桌面不能替源方谎报登录方式：源管理屏那颗登录按钮的有无，全靠这里从**真包内脚本**现算。
     * 逐颗回核 `app/src/main/assets/sources` 下的脚本，改形状（增删 `account.login` /
     * `loginWithWebview`）时这一把会红，逼着同步屏上口径 —— 与 cookie 名单那把同一纪律。
     */
    @Test
    fun 登录能力从真脚本现取而不许替源方作伪证() {
        fun script(key: String): String =
            DesktopSourceCatalogTest::class.java.classLoader
                .getResource("sources/$key.js")!!.readText()

        // 有账号密码登录（account.login）的代表源
        for (key in listOf("jm", "copy_manga", "manhuagui", "wnacg")) {
            assertTrue(
                "$key 脚本声明了 account.login，桌面就该认出能账密登录：",
                DesktopSourceNames.supportsPasswordLoginOf(script(key)),
            )
        }
        // 只有网页登录、桌面没有内嵌浏览器宿主的源，绝不算成能账密登录
        for (key in listOf("manga_dex", "nhentai")) {
            assertFalse(
                "$key 只有 loginWithWebview，不能谎成账密登录：",
                DesktopSourceNames.supportsPasswordLoginOf(script(key)),
            )
        }
        // 网页登录识别
        assertTrue("manga_dex 声明了 loginWithWebview：", DesktopSourceNames.supportsWebLoginOf(script("manga_dex")))
        assertFalse("jm 没有网页登录：", DesktopSourceNames.supportsWebLoginOf(script("jm")))
    }
}

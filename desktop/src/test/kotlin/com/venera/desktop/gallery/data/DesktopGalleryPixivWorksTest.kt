package com.venera.desktop.gallery.data

import java.io.File
import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pixiv 那两枚端口在桌面上**真接上了**（合主线后）。
 *
 * ## 这批要钉的三件事
 *
 * 1. **装配点真递了件**：`DesktopGalleryPorts` 里 `pixiv` / `pixivAccount` 两枚必须显式赋值。
 *    此前它们走 `GalleryPorts` 的默认 `null` ⇒ 桌面上 pixiv 那一整面是"端口没接"，
 *    而不是"平台做不到"（这两件事在屏上是两句话，混起来会让后来的人去找一条不存在的墙）。
 * 2. **27 枚方法一枚不漏地转发**：`GalleryPixivWorks` 的接口成员是 27 枚，
 *    漏一枚就是运行期 `AbstractMethodError`，而那种错在编译期看不见。
 * 3. **`ugoiraClip` 诚实交 `null`**：动图逐帧包要 `PixivUgoiraStore`（吃 `Context`，桌面编译面外）。
 *    它必须交 `Result.success(null)` 而不是抛 —— 抛网络形状的异常会把"桌面不支持"
 *    念成"站方没给"，那正是本仓明令禁的假读数。
 *
 * ## 为什么这一颗是源码尺而不是行为尺
 *
 * 27 枚方法的行为全在 `PixivClient` / `PixivAppClient` 里（两端口共享同一份源码），
 * 桌面这一颗**一条判据都不写**（见它的类注释）。所以桌面能钉的只有"转发有没有漏"。
 * 真行为要真起引擎、真打 pixiv —— 那是包内取证，不是这一颗。
 *
 * ⚠️ 证据等级：**只到源码形状**。没有真发过一枚对 `app-api.pixiv.net` 的请求，
 * 没有真起过一颗 `PixivCredentialState`（账号件那一半由 `DesktopPixivAccountTest` 用假 store 覆盖）。
 */
class DesktopGalleryPixivWorksTest {

    private val portsFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryPorts.kt",
    )

    private val worksFile = File(
        DesktopSourceTree.repoRoot,
        "desktop/src/main/kotlin/com/venera/desktop/gallery/data/DesktopGalleryPixivWorks.kt",
    )

    private val apiFile = DesktopSourceTree.androidSource("gallery/data/GalleryBusinessApi.kt")

    /** ① 装配点那两枚不再是默认 `null`。 */
    @Test
    fun 装配点真递了那两枚件() {
        val code = DesktopSourceTree.codeText(portsFile)
        assertTrue(
            "装配点没递 `pixiv`（走默认 null ⇒ 桌面 pixiv 那一面是「端口没接」）：",
            "pixiv = DesktopGalleryPixivWorks(" in code,
        )
        assertTrue(
            "装配点没递 `pixivAccount`：",
            "pixivAccount = DesktopPixivAccount(" in code,
        )
        // 对照组：那颗 `GalleryPorts(...)` 调用确实还在（抽空了上面两条会恒真）。
        assertTrue("对照组失效：装配点里没有 GalleryPorts( 那一处", "GalleryPorts(" in code)
    }

    /** ② 接口上那 27 枚，桌面这颗逐枚 override，一枚不漏。 */
    @Test
    fun 二十七枚方法一枚不漏() {
        val iface = DesktopSourceTree.codeText(apiFile)
        val start = iface.indexOf("interface GalleryPixivWorks")
        assertTrue("对照组失效：读不到 GalleryPixivWorks 接口", start >= 0)
        val body = iface.substring(start)
        // 取到接口的右花括号为止（第一个顶格 `}`）。
        val end = body.indexOf("\n}")
        val ifaceBody = if (end > 0) body.substring(0, end) else body
        val names = Regex("""suspend fun ([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(ifaceBody)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("对照组失效：接口上一枚方法都没抽到（正则漂了）", names.size >= 25)

        val impl = DesktopSourceTree.codeText(worksFile)
        val missing = names.filter { "override suspend fun $it(" !in impl }
        assertTrue("桌面这颗漏了这些方法（运行期 AbstractMethodError，编译期看不见）：$missing", missing.isEmpty())
        assertEquals("接口方法数与桌面 override 数对不上", names.size, names.size - missing.size + missing.size)
    }

    /** ③ `ugoiraClip` 真接逐帧包（Store 只收平台口、已在桌面编译面），且**不抛**。 */
    @Test
    fun 动图逐帧包真接且诚实不抛() {
        val code = DesktopSourceTree.codeText(worksFile)
        val anchor = code.indexOf("override suspend fun ugoiraClip")
        assertTrue("这颗没 override ugoiraClip（接口上有，漏了就是 AbstractMethodError）", anchor >= 0)
        val tail = code.substring(anchor, minOf(anchor + 600, code.length))
        // 正向：必须走那条双兜底 + 解包，逐字照 Android `GalleryAdaptersAndroid`。
        assertTrue("ugoiraClip 没接匿名 www 腿 client.ugoiraMeta：\n$tail", "client.ugoiraMeta" in tail)
        assertTrue("ugoiraClip 没接带凭据的 app-api 退路 appClient.ugoiraMetadata：\n$tail", "appClient.ugoiraMetadata" in tail)
        assertTrue("ugoiraClip 没交 PixivUgoiraStore 解包：\n$tail", "PixivUgoiraStore.getInstance" in tail)
        // 站方没给帧表时仍**诚实交 null**（契约语义 = 没有可用逐帧，屏层退回静帧），不许抛/判失败。
        assertTrue("ugoiraClip 没保留『没帧表交 null』那一档：\n$tail", "return@runCatching null" in tail)
        // 反向：整枚裹在 runCatching 里，裸 throw / Result.failure 都会把「站方没这一作」念成「桌面不支持」。
        assertTrue("ugoiraClip 没裹 runCatching（异常必须咽成 Result）：\n$tail", "runCatching" in tail)
        assertFalse("ugoiraClip 里出现了裸 throw", "throw " in tail.substringBefore("\n\n"))
        assertFalse("ugoiraClip 里出现了 Result.failure", "Result.failure" in tail.substringBefore("\n\n"))
    }

    /** ④ 账号件一条判据都不写：只转发给 `PixivCredentialState`。 */
    @Test
    fun 账号件不写第二条判据() {
        val code = DesktopSourceTree.codeText(worksFile)
        val start = code.indexOf("class DesktopPixivAccount")
        assertTrue("读不到 DesktopPixivAccount", start >= 0)
        val body = code.substring(start)
        assertTrue(
            "账号件没把判据转给 PixivCredentialState（两端各写一份判据 = 界面说已登录而请求全 401）：",
            "PixivCredentialState(" in body,
        )
        // 反向：这一颗里不许出现"过期怎么算"那一族的字面量（那些只许住判据件）。
        listOf("expires_at", "refresh_token", "grant_type").forEach { token ->
            assertFalse("账号件里出现了凭据协议字面量「$token」—— 判据该住在 PixivCredentialState 里", token in body)
        }
    }
}

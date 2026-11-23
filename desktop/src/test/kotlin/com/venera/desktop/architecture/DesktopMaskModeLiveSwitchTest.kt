package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「遮罩档实时切换」的源码尺。
 *
 * ## 这一批修的是什么
 *
 * 旧装配点把 `maskModeOf` 传成 `{ guard.nsfwMaskMode.value }` 普通 lambda：写侧虽推了
 * StateFlow，但 Compose 不订阅它，主屏各取数 `LaunchedEffect` 的 key 也不含档值 ——
 * 于是「设置里切档」只改了选中态与落盘，**已铺出来的封面遮罩停在旧快照**。
 *
 * 修法三件（本尺逐件钉住，防以后有人图省事删回去）：
 *  ① 装配点 `VeneraDesktop.kt` 订阅 `guard.nsfwMaskMode.collectAsState()`，喂进 maskModeOf；
 *  ② 主屏 `VeneraDesktopLargeScreen.kt` 取 `currentMaskMode`，5 个取数副作用与
 *     `desktopUx` 的 remember 都把它当 key/依赖，切档即重算；
 *  ③ 设置屏 `DesktopSettingsHub.kt` 写盘失败落界面 note，不再只打 stdout（桌面 toast 是空实现）。
 */
class DesktopMaskModeLiveSwitchTest {

    private fun text(rel: String) = DesktopSourceTree.codeText(DesktopSourceTree.desktopSourceOf(rel))

    private fun codeLines(rel: String) = DesktopSourceTree.codeLinesOf(DesktopSourceTree.desktopSourceOf(rel))

    /** ① 装配点必须订阅成 Compose 状态，裸读 .value 的 lambda 不触发重组。 */
    @Test
    fun 装配点把遮罩档订阅成状态() {
        val app = text("VeneraDesktop.kt")
        assertTrue(
            "装配点没订阅 guard.nsfwMaskMode —— 切档不会让主屏重组（退回旧「假开关」形状）。",
            "guard.nsfwMaskMode.collectAsState()" in app,
        )
        assertTrue(
            "maskModeOf 吃的不是订阅到的状态值。",
            "maskModeOf = { guardMaskMode }" in app,
        )
    }

    /** ② 主屏把当前档值取出来，并让取数副作用与判据对象都跟着它重算。 */
    @Test
    fun 主屏取数副作用与判据对象都含当前档() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue(
            "主屏没取 currentMaskMode —— 没有可进 key 的当前档值。",
            "val currentMaskMode = maskModeOf()" in shell,
        )
        // 5 个取数 LaunchedEffect（主墙 / 榜单 / 推荐 / 发现 / 收藏）key 含当前档。
        val keyedEffects = Regex("""LaunchedEffect\([^)]*currentMaskMode[^)]*\)""")
            .findAll(shell).count()
        assertTrue(
            "含 currentMaskMode 的取数副作用应有 5 个，实际 $keyedEffects 个 —— 少了就有一面墙切档不重算。",
            keyedEffects >= 5,
        )
        // 判据对象 remember 也把当前档当 key，切档必换到新对象。
        assertTrue(
            "desktopUx 的 remember 没含 currentMaskMode —— 封面打码判据可能停在旧对象。",
            Regex("""remember\(maskModeOf, blockedByUserOf, currentMaskMode\)""").containsMatchIn(shell),
        )
        // 二轮：masked 是取数时固化进墙卡模型的，缓存键不含档会命中旧页早退，
        // 用户必须再手动刷一遍才看得到新遮罩。档必须进 wallCacheKey 的 remember key 与拼串。
        assertTrue(
            "wallCacheKey 的 remember key 没含 currentMaskMode —— 切档会撞旧缓存早退（需手动刷新）。",
            Regex("""val wallCacheKey = remember\([^)]*currentMaskMode[^)]*\)""").containsMatchIn(shell),
        )
        assertTrue(
            "wallCacheKey 拼串没含 currentMaskMode —— 不同档会共用同一把缓存键。",
            shell.contains("|\$currentMaskMode\"") || shell.contains("|\$currentMaskMode|"),
        )
    }

    /** ④ 漫画各域封面走真判据件，不是 provider 外读到的 NoOp（恒 VISIBLE、从来不打码）。 */
    @Test
    fun 漫画封面打码走真件而非NoOp() {
        val shell = DesktopSourceTree.desktopLargeScreenCode()
        assertTrue(
            "壳体里仍有 ux.coverMaskStateOf —— 那枚 ux 是 provider 外的 NoOp，漫画封面恒不打码。",
            !Regex("""\bux\.coverMaskStateOf\(""").containsMatchIn(shell),
        )
        val realCalls = Regex("""desktopUx\.coverMaskStateOf\(""").findAll(shell).count()
        assertTrue(
            "真判据件 desktopUx 的 coverMaskStateOf 调用应有 9 处（8 域 + 端口），实际 $realCalls 处。",
            realCalls >= 9,
        )
    }

    /** ③ 写盘失败在界面有出口，而不是只打一行 stdout（桌面 toast 空实现，等于静默）。 */
    @Test
    fun 写盘失败在设置页有可见提示() {
        val hub = codeLines("settings/DesktopSettingsHub.kt").joinToString("\n")
        assertTrue(
            "设置页没有 maskModeError 状态 —— 写盘失败没有界面出口。",
            "maskModeError" in hub,
        )
        assertTrue(
            "SettingsPageTemplate 没把 maskModeError 当 note 传出去。",
            "note = maskModeError" in hub,
        )
        assertTrue(
            "写失败没落那句可见提示。",
            "切换失败：设置未保存" in hub,
        )
    }
}

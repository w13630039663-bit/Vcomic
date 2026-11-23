package com.venera.desktop.gallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.venera.compose.data.platform.AppearanceStyle
import com.venera.compose.data.platform.SurfaceMaterial
import com.venera.compose.ui.tokens.VeneraPreviewTheme

/**
 * 续 · 桌面第一次吃到**安卓那一套 token 与色板**（第 140 批：主题层）。
 *
 * ## 这一格补的是哪笔账
 *
 * 桌面外层从前是 `FluentTheme { … MiuixTheme { … } }`，而那一枚 `MiuixTheme {}` **空参调用** ⇒
 * 它吃的是 miuix 库自带默认色板，从来不是安卓那一份。后果就是主人量到的那句"差距有点过大"：
 * 同一颗 `VeneraSideBar` / `VeneraCard` / `VeneraCover`（§126 起两端逐字同一份源码），
 * 安卓那边由 `feature/VeneraTheme.kt` 同时 provide 五枚 Local（`LocalVeneraTokens` /
 * `LocalVeneraColorTokens` / `LocalSurfaceMaterial` / 两家 `LocalContentColor`）并把色板喂进
 * `MiuixTheme(colors=…)`；桌面**一枚都没喂**，那些件全在穿"库默认衣服"。
 *
 * ## 为什么走 [VeneraPreviewTheme] 而不搬 `VeneraTheme.kt`
 *
 * 那颗本体 import `android.app.Activity` / `WindowCompat` / `LocalView` / `BusinessPorts`，
 * 在 `desktop/build.gradle.kts` 的排除名单里（且 `app/` 是受保域，一字未动）。
 * 而 `ui/tokens/VeneraPreviewTheme.kt` 是**同一套 token 推导规则的纯函数复刻**（它自己的类注释写着
 * "预览只需要同一套 Token 推导逻辑，不读取任何持久化状态"），现量 `import android.` **0 处**，
 * 且已在桌面 srcDir 编译面内 ⇒ 桌面直接复用它，就是"两端共用一份推导"，不是抄第二份色板。
 *
 * ## 主题轴接上了（档二）：这枚常量换成偏好读数
 *
 * - **明暗**：不再钉死深色——`dark` 读 `DesktopThemeMode` 那颗单例流（三档：深色/浅色/跟随系统，
 *   由设置区写、主壳冷启动回填）。本文件只**转交**读数，自己不算明暗、不出色值 ——
 *   ③ 那条"薄壳不许长成第二套主题"的原训照旧成立，只是"两枚常量"已经兑成"一枚偏好转交"。
 * - **MD3**：仍吃安卓**出厂默认**那一档 —— `VeneraPreferences.kt:441` 现量
 * `MutableStateFlow(readEnum(KEY_APPEARANCE_STYLE, AppearanceStyle.MD3))`，且裁决
 *   就是"不要 miuix 风格、只保留 md 风格" ⇒ 桌面走 MD3 不是本喵挑的，是对岸默认值与主人口径同向。
 * 种子色/风格轴不做（浅色只做深色/浅色/跟随三档）。
 * - ⚠️ `dark` 仍**显式递值**：那颗件的默认值是 `isSystemInDarkTheme()`（AWT 侧跟的是系统"深色任务栏"
 *   那一族，跟不全），而窗钉的是主题档 ⇒ 必须把读数算出来递进去，不能让共享件自己猜。
 * - ⚠️ `material` 递 `SurfaceMaterial.SOLID`：玻璃/模糊那一族在桌面今天没接（`isRuntimeShaderSupported()`
 *   那一族的旧账），递非 SOLID 就是摆一档画不出来的形状（假开关口径）。
 *
 * ## 这颗薄壳不许长成第二套主题
 *
 * 它只做一件事：把两档常量递进那颗共享件。等桌面接上主题轴时，**这两枚常量应该换成偏好**，
 * 而不是在这里再写一套色板 —— 由源码尺钉住"本文件不许出现色值字面量"。
  *
  * ## 两格没接的账（别把"接了主题层"读成"接了主题轴"）
  *
  * - **对岸那句"仅被 @Preview 使用"从今天起对桌面不再成立**：`ui/tokens/VeneraPreviewTheme.kt`
  *   的类注释写着"它不参与运行时" —— 安卓侧仍真（运行时走 `feature/VeneraTheme.kt`），而桌面运行时
  *   **确实**走它。不去改 `app/` 那颗的注释，是因为那一句在安卓域准确，翻正反而会诱导"桌面可以搬
  *   `feature/VeneraTheme.kt`"的误读（那颗 import `android.app.Activity`，搬不动）。真正的口径住在这里。
 * - **色板轴（色批 1+2 →主人明令撤强调色档）**：从前这里吃 baseline 深色板，
 *   色批 1+2 改成按 `DesktopAccentMode` 六档取种子走 MaterialKolor 派生；但四套色源里只有共享组件
 *   这一支跟随强调色，桌面自制屏 93 处直读 `GalleryTokens` 固定 Hex，切档大半屏纹丝不动 ——
 * 半残的开关不如不要，**只保留深浅模式**。现在种子固定为出厂蓝 `DesktopFixedAccent`，
 *   派生链路（MaterialKolor 完整 MD3 色调：tinted neutrals + surfaceContainer 阶梯）保持不变，
 *   深 / 浅两套随 [DesktopThemeMode] 切换。Miuix 那一族仍吃库默认板（桌面运行时后端是 MD3，
 *   见 `DesktopThemeLayerTest` ⑤ 的账）。
 */
@Composable
fun DesktopVeneraTheme(content: @Composable () -> Unit) {
    // 明暗只**转交**单例读数，不在本文件算（③：桥不许长成第二套主题；档由设置区写、主壳回填）。
    val mode by com.venera.desktop.settings.DesktopThemeMode.mode.collectAsState()
    val dark = com.venera.desktop.settings.DesktopThemeMode.isDark(mode)
    // 固定使用出厂蓝种子通过 MaterialKolor 派生完整色调表面阶梯，不在此处硬编码色值。
    VeneraPreviewTheme(
        appearance = AppearanceStyle.MD3,
        dark = dark,
        material = SurfaceMaterial.SOLID,
        colorScheme = com.materialkolor.rememberDynamicColorScheme(
            com.venera.desktop.settings.DesktopFixedAccent,
            dark,
        ),
        content = {
            // 禁用涟漪须置于主题 content 槽内（主题之下、内容之上），防止内部主题重新提供 LocalIndication 覆盖窗根设置。
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.LocalIndication provides
                    com.venera.desktop.ui.components.DesktopNoIndication,
                androidx.compose.material3.LocalRippleConfiguration provides null,
            ) {
                content()
            }
        },
    )
}

package com.venera.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import com.venera.desktop.gallery.ui.DesktopVeneraTheme

/**
 * 台架的**统一入口**：把屏铺上去时，一起吃桌面运行时那一枚主题壳。
 *
 * ## 为什么台架不能省这一层（第 140 批的真账）
 *
 * 共享控件族 `VeneraControls.kt` 里有 **8 处** `if (useMiuixWidgets)` 分派，而那枚布尔读的是
 * `VeneraTokens.appearance`（`:62`）。`LocalVeneraTokens` 的**默认值**是 `MIUIX`
 * （`VeneraTokens.kt:133-141`，它自己的注释写着"默认值保证预览/单测无需主题也能取到合法 Token"）
 * ⇒ 台架只要不套主题壳，它量到的永远是 **miuix 那一档后端**；而桌面运行时从第 140 批起钉 `MD3`
 * ⇒ 走的是 `Md3Button` / `Md3AlertDialog` / `Md3Checkbox` 那一档。
 *
 * 后果不是"测试红"，而是本仓最恨的那一族：**台架全绿而它量的是运行时不用的那一档**
 * （假证据）。`VeneraDialog` 在运行时已经从 miuix `WindowDialog` 换成 `Md3AlertDialog` ——
 * 那两档的离屏合并树形状并不保证一样。
 *
 * ## 为什么是一枚 helper 而不是在每处 `setContent` 外面插一层壳
 *
 * 全量台架 18 颗文件 / 98 处 `setContent`。机械插壳会留下 98 处缩进不齐、且**读起来像真的多了一层
 * 主题**；换成入口名以后，"台架吃不吃主题"这件事变成**一眼可见的调用名**，也能被源码尺钉死
 * （见 `DesktopThemeLayerTest` ⑥：除白名单那两颗探针外，测试树里不许再有裸 `setContent {`）。
 *
 * ⚠️ 与运行时**不是逐字同一棵树**：运行时那一层还包括 `FluentTheme`、`LocalWindowWidth`、
 * `LocalUxPlatformServices`、`LocalVeneraDarkTheme` 等一堆 provide（`VeneraDesktopLargeScreen.kt`）。
 * 这里只对齐"控件后端 + token + 色板"那一格 —— 那是发现的失真点，其余provide 由各自屏的形参递进来。
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.setContentThemed(content: @Composable () -> Unit) =
    setContent { DesktopVeneraTheme(content = content) }

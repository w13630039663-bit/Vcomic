package com.venera.desktop

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 「这一屏的返回**已由壳层承担**」—— 一屏一返回的单一源。
 *
 * ## 为什么需要这颗
 *
 * 项目里返回曾经有三条并行装配路径：经典档 `DesktopWindowTitleBar` 的 VeneraBackHandle、
 * 浅皮档轨内返回胶囊（v3，已撤）、以及**各屏自己顶栏里**的 VeneraBackHandle。
 * 后两者同屏出现就是用户说的「两个返回按钮」；历史测试
 * `DesktopGallerySearchUiTest` 也写过一次「与窗口 titlebar 的面包屑重复（同屏两个返回），已撤」，
 * 但只撤了搜索那一屏，其余屏仍在。
 *
 * v4 把返回收口到壳层命令带（B 案 ContentHead），经典档则仍在 titlebar。
 * **两者存在的条件恰好都是 `!fullscreenChrome`**：全屏页（阅读器 / 看图播放器）
 * 壳层不画任何 chrome，返回必须留在屏内玻璃把手上。所以这颗 local 由窗根
 * 一次 provide `!fullscreenChrome`，所有普通页的屏内返回读它自行隐去，
 * 全屏页与「画师面板 → 作品信息」那类**局部**返回不受影响。
 *
 * 默认 false = 不注入时（预览 / 单测 / 别的组合根）屏内返回照旧，行为逐字节不变。
 */
internal val LocalShellBackHandled = staticCompositionLocalOf { false }

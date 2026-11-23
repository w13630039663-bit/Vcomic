package com.venera.desktop.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor

/**
 * 桌面页面宽度类型。
 *
 * 为什么不能继续复用 `contentMaxWidthFor()`：
 * 那颗件描述的是"一行一行读"的正文列宽（设置 / 表单 / 长文），
 * 而画廊、首页、探索、搜索、收藏是浏览型页面，必须吃满桌面宽度。
 * 继续复用会把 1422 宽的窗口压回 840，右侧留下一条无意义空白。
 */
internal enum class DesktopPageWidth {
    /** 画廊、首页、探索、搜索、收藏：宽屏浏览，最大 1680。 */
    Browse,

    /** 详情、Pixiv 作品、源管理：需要双栏，但不适合无限拉宽。 */
    Detail,

    /** 设置、表单、备份、登录：保持可读列宽。 */
    Read,

    /** 阅读器、看图器、全屏预览：不设上限。 */
    Full,
}

internal fun desktopPageMaxWidth(
    kind: DesktopPageWidth,
    mode: WideScreenLayoutMode,
): Dp? = when (kind) {
    DesktopPageWidth.Browse -> 1680.dp
    DesktopPageWidth.Detail -> 1280.dp
    DesktopPageWidth.Read -> contentMaxWidthFor(mode)
    DesktopPageWidth.Full -> null
}

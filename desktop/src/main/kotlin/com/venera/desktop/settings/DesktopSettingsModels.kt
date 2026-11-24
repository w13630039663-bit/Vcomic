package com.venera.desktop.settings

import androidx.compose.ui.graphics.Color

/** 桌面版本号：关于区、更新检查与弹窗尾注的唯一出处。 */
internal const val DESKTOP_APP_VERSION = "1.0.0"

internal enum class DesktopSettingsSection(
    val titleZh: String,
    val titleEn: String,
    val groupZh: String,
    val groupEn: String,
) {
    /** 通用与外观：装饰档（素/纹样/神社）。 */
    APPEARANCE("通用与外观", "Appearance", "浏览", "Browse"),

    /** 画廊与浏览：墙上布局 + 大图页 + 内容标记 + 桌面暂不支持。 */
    GALLERY("画廊与浏览", "Gallery", "浏览", "Browse"),

    /** 探索与卡片：遮罩档。 */
    EXPLORE("探索与卡片", "Explore", "浏览", "Browse"),

    /** 阅读器：漫画阅读模式五档。 */
    READER("阅读器", "Reader", "阅读与收藏", "Reading & Favorites"),

    /** 源与账号：源管理、Gelbooru 登录、Pixiv 账号入口。 */
    SOURCES("源与账号", "Sources & Accounts", "系统", "System"),

    /** 视频与动图：自动播放总闸 + 动图逐帧档 + 大图观感。 */
    MEDIA("视频与动图", "Media & Video", "系统", "System"),

    /** 收藏：快捷夹 / 新收藏位置 / 排序 / 追更夹（档一接线）。 */
    FAVORITES("收藏", "Favorites", "阅读与收藏", "Reading & Favorites"),

    /** 网络：代理三枚 / 缓存上限（改档重启生效，行上如实标注）。 */
    NETWORK("网络", "Network", "系统", "System"),

    /** 数据与备份：备份/历史/统计入口。 */
    DATA("数据与备份", "Data & Backup", "系统", "System"),

    /** 桌面能力：尚未接入独立设置子页的项，照实点名，不摆假开关。 */
    DESKTOP_CAPABILITIES("桌面能力", "Capabilities", "系统", "System"),

    /** 关于与更新：版本一句。 */
    ABOUT("关于与更新", "About & Updates", "系统", "System");

    val title: String get() = if (DesktopLanguage.isEnglish()) titleEn else titleZh
    val group: String get() = if (DesktopLanguage.isEnglish()) groupEn else groupZh
}

/** 一枚设置行：屏上「左标题右控件」那一行。 */
internal sealed interface DesktopSettingRow {

    /** 行标题。 */
    val title: String

    /** 说明（可空 = 不摆那行小字）。 */
    val caption: String?

    /** 开关。 */
    data class Toggle(
        override val title: String,
        override val caption: String?,
        val checked: Boolean,
        val enabled: Boolean = true,
        val onToggle: (Boolean) -> Unit,
    ) : DesktopSettingRow

    /** 枚举档：短档名走分段器，长档名走下拉。 */
    data class Options(
        override val title: String,
        override val caption: String?,
        val options: List<String>,
        val selectedIndex: Int,
        val onSelect: (Int) -> Unit,
    ) : DesktopSettingRow

    /** 二级入口（源管理、备份那一族）。[clickable]=false = 纯展示行，不摆 ›、不计徽标。 */
    data class Entry(
        override val title: String,
        override val caption: String?,
        val value: String?,
        val onOpen: () -> Unit,
        val clickable: Boolean = true,
    ) : DesktopSettingRow

    /** 操作按钮（登录、退出等行内把手）。 */
    data class Action(
        override val title: String,
        override val caption: String?,
        val buttonLabel: String,
        val onAction: () -> Unit,
        val buttonColor: Color? = null,
        val enabled: Boolean = true,
    ) : DesktopSettingRow

    /** 自由文本行（代理主机/端口那一族）：失焦或回车提交 [onCommit]。 */
    data class Text(
        override val title: String,
        override val caption: String?,
        val value: String,
        val onCommit: (String) -> Unit,
    ) : DesktopSettingRow

    /** 点名缺席（「不可用」小签那档）。 */
    data class Absent(
        override val title: String,
        override val caption: String?,
    ) : DesktopSettingRow

    /** 滑条行：[value] 受控，[display] 把值转成右侧短文本。 */
    data class Slider(
        override val title: String,
        override val caption: String?,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        /** 中间刻度数（0=连续）。 */
        val steps: Int,
        val display: (Float) -> String,
        val onChange: (Float) -> Unit,
    ) : DesktopSettingRow
}

/** 一张卡：卡头（标题+副题）+ 若干行。 */
internal data class DesktopSettingsCard(
    val title: String,
    val caption: String?,
    val rows: List<DesktopSettingRow>,
)

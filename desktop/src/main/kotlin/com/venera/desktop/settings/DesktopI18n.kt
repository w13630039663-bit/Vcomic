package com.venera.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** 当前桌面语言 CompositionLocal，随 [DesktopLanguage.mode] 热切并触发全树局部重组。 */
val LocalDesktopLanguage = compositionLocalOf { DesktopLanguage.ZH }

/**
 * 桌面端国际化词典与翻译服务。
 *
 * 统一管理全软件中英文双语映射，支持即时热切。
 */
object DesktopI18n {

    /** 响应式翻译入口：订阅 [LocalDesktopLanguage]，语言切换时自动重组当前节点。 */
    @Composable
    fun t(text: String): String {
        val isEn = LocalDesktopLanguage.current == DesktopLanguage.EN
        return translate(text, isEn)
    }

    /** 响应式双语直传重载。 */
    @Composable
    fun t(zh: String, en: String): String =
        if (LocalDesktopLanguage.current == DesktopLanguage.EN) en else zh

    /** 核心翻译入口：根据当前语言模式返回本地化文案（非 Composable 语境用）。 */
    fun tr(text: String): String = translate(text, DesktopLanguage.isEnglish())

    private fun translate(text: String, isEnglish: Boolean): String {
        if (!isEnglish) return text
        DICTIONARY[text]?.let { return it }

        // 动态复合串前缀处理
        if (text.startsWith("编辑源 · ")) return "Edit Source · " + text.removePrefix("编辑源 · ")
        if (text.startsWith("登录源 · ")) return "Login Source · " + text.removePrefix("登录源 · ")
        if (text.startsWith("作品详情 · ")) return "Post Detail · " + text.removePrefix("作品详情 · ")
        if (text.startsWith("屏蔽规则 · ")) return "Block Rules · " + text.removePrefix("屏蔽规则 · ")
        if (text.startsWith("没有匹配「") && text.endsWith("」的设置项。")) {
            val q = text.removePrefix("没有匹配「").removeSuffix("」的设置项。")
            return "No settings matching '$q'."
        }
        if (text.startsWith("没有匹配「") && text.endsWith("」的分类")) {
            val q = text.removePrefix("没有匹配「").removeSuffix("」的分类")
            return "No category matching '$q'."
        }

        return text
    }

    /** 双语直传快捷重载。 */
    fun tr(zh: String, en: String): String = if (DesktopLanguage.isEnglish()) en else zh

    private val DICTIONARY: Map<String, String> = mapOf(
        // ── 导航与基础 ──
        "首页" to "Home",
        "搜索" to "Search",
        "画廊" to "Gallery",
        "收藏" to "Favorites",
        "探索" to "Explore",
        "设置" to "Settings",
        "总览" to "Overview",
        "漫画" to "Comics",
        "工具" to "Tools",
        "更多" to "More",
        "返回" to "Back",
        "刷新" to "Refresh",
        "关闭" to "Close",
        "取消" to "Cancel",
        "确定" to "Confirm",
        "保存" to "Save",
        "重置" to "Reset",
        "删除" to "Delete",
        "清除" to "Clear",
        "编辑" to "Edit",
        "分享" to "Share",
        "复制" to "Copy",
        "复制链接" to "Copy Link",
        "全选" to "Select All",
        "状态" to "Status",
        "版本" to "Version",
        "不可用" to "Unavailable",
        "未设置" to "Not set",
        "未测" to "Unmeasured",
        "未检测" to "Not tested",
        "正在读…" to "Reading…",
        "加载中…" to "Loading…",
        "暂无数据" to "No data",
        "无记录" to "No records",

        // ── 侧栏与二级入口 ──
        "下载与本地" to "Downloads",
        "下载队列" to "Download Queue",
        "本机已下载" to "Downloaded",
        "本地漫画" to "Local Comics",
        "阅读历史" to "Reading History",
        "收藏与历史" to "Favorites & History",
        "关注画师" to "Followed Artists",
        "我的 pixiv" to "My Pixiv",
        "搜图" to "Image Search",
        "画廊搜索" to "Gallery Search",
        "画廊收藏" to "Gallery Bookmarks",
        "每日热门" to "Daily Popular",
        "pixivision 文章" to "Pixivision Articles",
        "珍藏册" to "Collections",
        "阅读统计" to "Reading Stats",
        "站点" to "Sources",
        "备份与还原" to "Backup & Restore",
        "画廊频道" to "Gallery Channel",
        "漫画频道" to "Comics Channel",
        "工具频道" to "Tools Channel",
        "漫画搜索" to "Comic Search",
        "漫画搜索 · 探索" to "Comic Search · Explore",
        "漫画详情" to "Comic Detail",
        "查看大图" to "Image Viewer",
        "登录 pixiv" to "Login to Pixiv",
        "pixiv 书签" to "Pixiv Bookmarks",
        "评论" to "Comments",
        "屏蔽与管理" to "Blocking & Rules",
        "目录与缓存" to "Path & Cache",
        "规则" to "Rules",
        "数据" to "Data",

        // ── 设置分区与分组 ──
        "通用与外观" to "Appearance",
        "画廊与浏览" to "Gallery",
        "探索与卡片" to "Explore",
        "阅读器" to "Reader",
        "源与账号" to "Sources & Accounts",
        "视频与动图" to "Media & Video",
        "网络" to "Network",
        "数据与备份" to "Data & Backup",
        "桌面能力" to "Capabilities",
        "关于与更新" to "About & Updates",
        "浏览" to "Browse",
        "阅读与收藏" to "Reading & Favorites",
        "系统" to "System",

        // ── 设置卡片与行 ──
        "语言" to "Language",
        "界面显示语言" to "Interface display language",
        "界面语言" to "App Language",
        "支持中文与英文即时切换" to "Switch between Chinese and English",
        "简体中文" to "简体中文",
        "English" to "English",
        "主题" to "Theme",
        "深色 / 浅色 / 跟随系统（即改即生效）" to "Dark / Light / Follow System",
        "主题模式" to "Theme Mode",
        "浅色为 2026-11-04 首版，深色装饰件或有残留" to "Dark, Light or System mode",
        "深色" to "Dark",
        "浅色" to "Light",
        "跟随系统" to "Follow System",
        "底板模糊" to "Backdrop Blur",
        "关闭后内容区为实底" to "Solid background when disabled",
        "侧栏模糊" to "Rail Blur",
        "独立开关，关闭为实底" to "Solid sidebar when disabled",
        "顶栏模糊" to "Top Bar Blur"
    ) + mapOf(
        "首页皮肤" to "Home Skin",
        "Acrylic：玻璃侧栏与通透首页" to "Acrylic: translucent sidebar & home",
        "侧栏桌宠" to "Sidebar Mascot",
        "在侧栏底部显示小鱼桌宠" to "Display mascot fish at sidebar bottom",
        "缩放" to "Scaling",
        "界面缩放与显示比例" to "UI scaling & display ratio",
        "界面缩放" to "UI Scale",
        "调整界面的显示比例" to "Adjust interface display scale",
        "标签译名" to "Tag Translation",
        "画廊标签的中文显示档" to "Chinese display options for gallery tags",
        "译文档位" to "Translation Mode",
        "简体/繁体共用 cn 列，无真差异故不列" to "Both simplified and traditional share cn column",
        "不显示" to "Off",
        "浏览布局" to "Browse Layout",
        "网格布局与预览画质" to "Grid layout & preview quality",
        "作品墙布局" to "Wall Layout",
        "瀑布流或等宽方图网格" to "Waterfall flow or square grid",
        "瀑布流" to "Waterfall",
        "等宽网格" to "Grid",
        "卡片密度" to "Card Density",
        "紧凑卡小，宽松卡大" to "Compact for smaller cards, Loose for larger",
        "紧凑" to "Compact",
        "标准" to "Standard",
        "宽松" to "Loose",
        "卡片大小" to "Card Size",
        "在密度之上再缩放单卡" to "Scale individual cards above density",
        "小" to "Small",
        "中" to "Medium",
        "大" to "Large",
        "网格密度" to "Grid Density",
        "自适应或固定 x 行 x 列，全部卡片统一生效；行数决定封面裁切比例" to "Adaptive or fixed rows and columns",
        "封面画幅" to "Cover Aspect Ratio",
        "仅瀑布流与等宽自适应档生效；固定行数档由网格密度决定" to "Only applies to waterfall & adaptive grid",
        "预览清晰度" to "Preview Quality",
        "流量约十倍，刷新后生效" to "Changes take effect after refresh",
        "低画质" to "Low",
        "中画质" to "Medium",
        "大图" to "High",
        "原图" to "Original",
        "详情页清晰度" to "Detail Quality",
        "详情大图取预览/大图/原图" to "Detail image resolution",
        "详情栏宽度" to "Detail Pane Width",
        "详情页右栏信息位宽度" to "Width of detail right information pane",
        "详情栏默认展开" to "Detail Pane Expanded by Default",
        "打开详情页时右栏先展开（页内收起不写回）" to "Expand right pane when opening detail page",
        "图片缓存上限" to "Image Cache Limit",
        "重启后生效" to "Takes effect after restart",
        "内容标记" to "Content Tags",
        "依据站方标签，不靠画风推断" to "Based on official tags",
        "屏蔽 AI 作品" to "Block AI Works",
        "与漫画侧开关相互独立" to "Independent from comic side switch",
        "显示 AI 角标" to "Show AI Badge",
        "在卡片上标注 AI 标识" to "Display AI indicator on cards",
        "自动轮播" to "Auto Play",
        "自动轮播间隔" to "Auto Play Interval",
        "预加载相邻图" to "Preload Adjacent Images",
        "大图页前后各预载一张" to "Preload one image ahead and behind",
        "允许动图播放" to "Allow Animation",
        "动图逐帧播放与动效" to "Play animated GIF / Ugoira frames",
        "画布底色" to "Canvas Background",
        "图片查看器的背景色彩" to "Background color of image viewer",
        "内容分级" to "Content Rating",
        "成人内容显示档位" to "Adult content display levels",
        "遮罩档" to "Mask Level",
        "不过滤 / 封面打码 / 彻底隐藏" to "Unfiltered / Mask cover / Hide completely",
        "不过滤" to "Unfiltered",
        "封面打码" to "Mask Cover",
        "彻底隐藏" to "Hide Completely",
        "漫画阅读模式" to "Reading Mode",
        "条漫" to "Webtoon",
        "对开双页" to "Dual Page",
        "日漫" to "Manga (R to L)",
        "美漫" to "Comic (L to R)",
        "连续" to "Continuous",
        "章节倒序" to "Reverse Chapter Order",
        "将最新章节排在最前" to "Show latest chapters first",
        "源管理" to "Source Management",
        "网络与代理" to "Network & Proxy",
        "代理类型" to "Proxy Type",
        "代理主机" to "Proxy Host",
        "代理端口" to "Proxy Port",
        "HTTP 缓存上限" to "HTTP Cache Limit",
        "下载并发数" to "Download Concurrency",
        "清理缓存" to "Clear Cache",
        "导出备份" to "Export Backup",
        "导入备份" to "Import Backup",
        "关于 VComic" to "About VComic",
        "现代化跨平台漫画与画廊阅览中枢" to "Modern cross-platform comic & gallery reader",
        "与 packageVersion 同源" to "Same as packageVersion",
        "检查更新" to "Check for Updates",
        "检查中…" to "Checking…",
        "检查" to "Check",
        "去发布页下载" to "Download Releases",
        "项目主页" to "Project Homepage",
        "在系统浏览器打开" to "Open in system browser",
        "更新发布" to "Release Page",
        "在系统浏览器打开发布页" to "Open releases page in system browser",
        "搜索设置项" to "Search settings",
        "本机状态" to "Local Status",
        "备份与维护" to "Maintenance",
        "本机操作" to "Local Operations",
        "下载目录" to "Download Path",
        "屏蔽规则" to "Block Rules",
        "源测速" to "Probe Speed",
        "立即释放" to "Free space",
        "打开更新页" to "Open releases",
        "导出" to "Export",
        "本机备份" to "Local backup",
        "这一组还没有设置项。" to "No settings in this section.",
        "搜索结果" to "Search Results",
        "图片" to "Images",
        "按画师" to "By Artist",
        "按时间" to "By Date",
        "按站点" to "By Site",
        "不分组" to "Ungrouped",
        "图片加载" to "Image Load",
        "程序异常" to "App Error",
        "系统" to "System",
        "正常" to "OK",
        "偏慢" to "Slow",
        "异常" to "Error",
        "测速中" to "Testing",
        "离线" to "Offline",
        "阅读器" to "Reader",
        "网络收藏" to "Network Favorites",
        "书架总览" to "Library",
        "收藏到" to "Add to Favorites",
    )
}

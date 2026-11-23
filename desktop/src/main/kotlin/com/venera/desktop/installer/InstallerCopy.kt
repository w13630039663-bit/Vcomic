package com.venera.desktop.installer

/** 安装页单幕介绍文案。 */
data class InstallSceneItem(
    val title: String,
    val subtitle: String,
)

/** 安装向导全部文案（严格遵守主标 ≤ 6 字、副标 ≤ 15 字及克制桌面端术语）。 */
object InstallerCopy {
    const val AppTitle = "VComic 安装程序 · 1.0.0"
    const val Brand = "VComic"
    const val VersionBadge = "v1.0.0"
    const val Tagline = "开源跨源桌面漫画阅读器"

    const val InstallNow = "立即安装"
    const val UpgradeNow = "立即升级"
    const val CustomPath = "自定义目录"
    const val Browse = "浏览…"
    const val DefaultPathHint = "%LOCALAPPDATA%\\venera-desktop"
    const val DataDirHint = "用户数据目录固定位于 %LOCALAPPDATA%\\venera"

    const val UpgradeNotice = "检测到已安装旧版本，本次将覆盖升级并保留全部数据与设置"
    const val CompleteTitle = "安装已完成"
    const val CompleteSubtitle = "VComic 1.0.0 已就绪，可立即开启阅读体验"
    const val LaunchOnFinish = "立即启动 VComic"
    const val StartUsing = "开始使用"

    /** 安装中五幕介绍，与分层矢量插画同步 6s 轮播。 */
    val installScenes = listOf(
        InstallSceneItem("多源聚合阅读", "统一管理图库与漫画源，一处直达"),
        InstallSceneItem("双页沉浸视图", "高清缩放与快捷键翻页流畅跟手"),
        InstallSceneItem("标签筛选检索", "多维标签组合过滤，快速定位作品"),
        InstallSceneItem("本地离线缓存", "支持章节批量下载，断网也可翻阅"),
        InstallSceneItem("本地数据独立", "收藏与历史独立保存，升级无忧"),
    )

    /** 安装进度阶段文案。 */
    fun progressStageText(percent: Int): String = when {
        percent >= 94 -> "正在完成收尾校验…"
        percent >= 76 -> "正在写入快捷方式与启动项…"
        percent >= 48 -> "正在部署内置图像引擎…"
        percent >= 18 -> "正在解压桌面核心组件…"
        else -> "正在校验运行环境…"
    }

    // 扩展态文案
    const val MigrationTitle = "检测到旧版安装"
    const val MigrationDesc = "当前系统已安装旧版 Venera 桌面端（MSI 版），需先卸载旧版再继续安装 VComic 1.0.0"
    const val MigrationConfirm = "卸载旧版并继续"

    const val ConflictTitle = "应用正在运行"
    const val ConflictDesc = "检测到 VComic（venera-desktop.exe）仍在运行，请先关闭后再更新文件"
    const val ConflictConfirm = "关闭应用并继续"

    const val CancelModalTitle = "确认取消安装？"
    const val CancelModalDesc = "正在写入桌面核心组件，立即取消将自动清理已生成的临时文件，不会影响现有用户数据。"
    const val CancelModalKeep = "继续安装"
    const val CancelModalAbort = "取消安装"

    const val MaintTitle = "管理已安装的 VComic"
    const val MaintDesc = "当前已安装 VComic 1.0.0，请选择需要执行的维护操作"
}

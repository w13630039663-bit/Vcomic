package com.venera.desktop.gallery.data

import com.venera.engineprobe.DesktopSourceUpdate.SourceRepo
import java.io.File
import java.util.Properties

/**
 * 改写层里哪些稿是**自动更新/源库安装的**（而不是用户手改的），并记录所属仓库来源。
 *
 * 两种稿在盘上完全同形，靠清单一并区分：
 * - `<key>`：上次写入的版本号（与现盘一致时允许自动升级覆盖，防止覆盖用户手改脚本）；
 * - `<key>.repo`：来源仓库标识（`official` / `vensrc_main` / `vensrc_ext` / `local`）。
 */
object DesktopSourceUpdater {
    private const val MANIFEST = ".auto-updated.properties"
    const val REPO_LOCAL = "local"

    /** 这一篇能不能被自动更新覆盖：没有稿（纯随包）或稿正是上次自动/源库写的那一版。 */
    fun canAutoOverwrite(dir: File, fileKey: String, localVersion: String): Boolean {
        val overridden = DesktopSourceOverrides.textOf(dir, fileKey) ?: return true
        if (overridden.isBlank()) return true
        return read(dir).getProperty(fileKey) == localVersion
    }

    /** 兼容旧签名：记下这一篇是官方源自动更新写的。 */
    fun markAutoUpdated(dir: File, fileKey: String, version: String) {
        markInstalled(dir, fileKey, version, SourceRepo.OFFICIAL.id)
    }

    /** 记下这一篇的版本与所属仓库（供后续升级比对与卡片/抽屉来源标注）。 */
    fun markInstalled(dir: File, fileKey: String, version: String, repoId: String) {
        val props = read(dir)
        props.setProperty(fileKey, version)
        props.setProperty("$fileKey.repo", repoId)
        write(dir, props)
    }

    /** 读这一颗源最近一次登记的仓库 ID；未登记交回 null。 */
    fun repoIdOf(dir: File, fileKey: String): String? =
        read(dir).getProperty("$fileKey.repo")?.takeIf { it.isNotBlank() }

    /** 读这一颗源最近一次安装时登记的版本号；未登记交回 null。 */
    fun installedVersionOf(dir: File, fileKey: String): String? =
        read(dir).getProperty(fileKey)?.takeIf { it.isNotBlank() }

    /** 撤销或移除一颗源时同步清理登记记录。 */
    fun clearRecord(dir: File, fileKey: String) {
        val props = read(dir)
        val changed = props.remove(fileKey) != null || props.remove("$fileKey.repo") != null
        if (changed) write(dir, props)
    }

    /**
     * 计算展示在卡片副行与抽屉头部的来源短标签：
     * - 未进入覆盖层的随包源保持 `"内置站点"`（与既往语义及测试一致）；
     * - 来自 `laincat/vensrc` 主/扩展订阅的标 `"laincat/vensrc"`；
     * - 来自官方源同步的标 `"官方源"`；
     * - 来自本地文件导入或新增本地脚本标 `"本地脚本"`。
     */
    fun originLabelOf(
        isBundled: Boolean,
        overridden: Boolean,
        repoId: String?,
    ): String {
        if (!overridden) return "内置站点"
        return when (repoId) {
            SourceRepo.VENSRC_MAIN.id, SourceRepo.VENSRC_EXT.id -> "laincat/vensrc"
            SourceRepo.OFFICIAL.id -> "官方源"
            REPO_LOCAL -> "本地脚本"
            else -> if (isBundled) "内置站点" else "本地脚本"
        }
    }

    /**
     * 归一化本机已装源的仓库家族标识：
     * - 未覆写的随包内置站点与来自官方源仓库的脚本同属 `venera-app/venera-configs`；
     * - `laincat/vensrc` 主订阅与扩展订阅同属 `laincat/vensrc`；
     * - 本地导入或手改脚本归为 `local`。
     */
    fun repoFamilyOf(
        isBundled: Boolean,
        overridden: Boolean,
        repoId: String?,
    ): String {
        if (!overridden) return if (isBundled) SourceRepo.OFFICIAL.repoSlug else REPO_LOCAL
        return when (repoId) {
            SourceRepo.OFFICIAL.id -> SourceRepo.OFFICIAL.repoSlug
            SourceRepo.VENSRC_MAIN.id, SourceRepo.VENSRC_EXT.id -> SourceRepo.VENSRC_MAIN.repoSlug
            else -> REPO_LOCAL
        }
    }

    /** 本机这颗源与目标在线仓库是否同源（同源才走升级，异源只能手动切换）。 */
    fun isSameRepoOrigin(
        isBundled: Boolean,
        overridden: Boolean,
        repoId: String?,
        targetRepo: SourceRepo,
    ): Boolean = repoFamilyOf(isBundled, overridden, repoId) == targetRepo.repoSlug

    private fun read(dir: File): Properties = Properties().apply {
        runCatching { File(dir, MANIFEST).inputStream().use { load(it) } }
    }

    private fun write(dir: File, props: Properties) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            File(dir, MANIFEST).outputStream().use { props.store(it, "auto-updated source versions") }
        }
    }
}

package com.venera.desktop.downloads

import com.venera.desktop.app.DesktopRoute
import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.venera.VeneraButton
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DESKTOP_LOCAL_COMIC_IMPORTED_SOURCE
import com.venera.desktop.gallery.data.DesktopLocalComic
import com.venera.desktop.gallery.data.DesktopLocalComicLibraryScan
import com.venera.desktop.gallery.data.desktopLocalComicLibraryHeader
import com.venera.desktop.gallery.data.desktopLocalComicLibraryPendingLine
import com.venera.desktop.gallery.data.desktopLocalComicLibraryRowLine
import com.venera.desktop.ui.DesktopPageShell
import com.venera.desktop.ui.DesktopPageWidth
import com.venera.desktop.ui.ListPageTemplate
import com.venera.desktop.ui.components.desktopCoverFloat
import com.venera.desktop.ui.components.desktopUiFloat

/**
 * 桌面「本地漫画」那一域屏 —— （A 档最后一颗纯功能缺口）。
 *
 * ## 这一批把对岸那一整屏搬到了桌面上，但**只搬认得出的那一半**
 *
 * 对岸 `feature/LocalComicScreen`（由 `LocalComicManager.getLocalComics()` 喂）摆的是**本机所有攒下来的本子**
 * （目录名兜底题名 + `comic_info.json.title` 覆盖），桌面从前一处都看不见：`Downloads` 屏
 * 吃的是带 `chapter.json` 清单的话级树，**只认桌面自己下的那些**，而安卓同机攒的书 / CBZ 导进来的裸目录
 * 在它那里连一行都不摆。本屏开的是**另一棵树的门**：认所有有图的本子，并念得出**题名**（本级域的招牌，
 * `Downloads` 只念号）。
 *
 * ## 形状照批 115 屏 / 批 134 屏，组件只用同一套
 *
 * `DesktopPageShell` / `ListPageTemplate` / `VeneraCard` / `VeneraTokens` + material3 `Text`，
 * **不引 Fluent 第二套**。
 * 表头与行内那两句逐字复用纯件（`desktopLocalComicLibraryHeader` / `...RowLine` / `...PendingLine`），屏一个
 * 字都不另写。屏**不碰盘**：本级扫描、`runCatching` 兜底、取证读数全住在壳（与批 115「屏零 IO」同口径），
 * 这一颗文件里 `withContext` 是 **0 处**。
 *
 * ## 这一批刻意不摆的把手（不悄悄省掉）
 *
 * - **没有「点开裸目录本子」**：桌面那条离线翻开路吃的是**带清单的话级目录 + 源站话号**，
 *   而本地域这一棵树里那些裸目录根本没有可落地的阅读口 —— 摆一枚点开就是"按了没反应"的假门（评审 R6 那一族），
 *   所以裸目录行只念不点。**导入的本子（`sourceKey == "imported"`）例外**：归档自含页图，
 * 走 `DesktopRoute.LocalComicReader` 那条不依赖引擎的取页路（tool-suite 稿⑤）。
 * - **没有导出 CBZ / 批量删**：那是会对多行同时扳确认的形状，且导出绑 Android 那套 zip/share 宿主，不碰。
 *
 * ##导入把手
 *
 * 「导入 CBZ/ZIP」吃 [onImportPick]（壳侧弹 AWT FileDialog → `desktopLocalComicImport` → tick 重数那棵树）；
 * 导入中/导入果都在 [busy] / [note] 里（busy 时把手禁用，防止同一颗包双击导入两份）。
 */
@Composable
internal fun DesktopLocalComicLibraryScreen(
    mode: WideScreenLayoutMode,
    /** 盘上本级那一棵树数回来的结果；`null` = **还没数回来**（念另一句，不念「本机没有」）。 */
    scan: DesktopLocalComicLibraryScan?,
    /** 上一次按下的果（导入成败原话等；非空照原话上屏）。 */
    note: String?,
    /** 回底栏那一格。与源管理/队列/下载/统计同口径：二级页必须有出路。 */
    onBack: () -> Unit,
    /** 弹文件框选一颗 CBZ/ZIP（壳侧跑导入腿）；`null` = 数据根没定，把手整颗不摆（摆了按不动是假门）。 */
    onImportPick: (() -> Unit)? = null,
    /** 导入进行中（把手禁用那一档）。 */
    busy: Boolean = false,
    /** 点开一本**导入的**本子（归档自含页图，不依赖引擎）。裸目录行不挂。 */
    onOpenImported: ((DesktopLocalComic) -> Unit)? = null,
    /** 并进「下载与本地」合并页时传 true（不自画页壳）。 */
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val comics = scan?.comics.orEmpty()
    val body: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶行：一句读数 + 导入把手（与「本地漫画库」同形）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = scan?.let(::desktopLocalComicLibraryHeader)
                        ?: desktopLocalComicLibraryPendingLine(),
                    fontSize = VeneraTokens.type.caption,
                    color = VeneraTokens.color.textTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (onImportPick != null) {
                    com.venera.desktop.ui.DesktopActionButton(
                        text = if (busy) com.venera.desktop.settings.DesktopI18n.t("正在导入…", "Importing…") else com.venera.desktop.settings.DesktopI18n.t("导入本地漫画 (.zip / .cbz)", "Import Local Comic (.zip / .cbz)"),
                        onClick = { if (!busy) onImportPick() },
                    )
                }
            }
            when {
                scan == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // 顶行已经念了「正在数…」；这里不再摆第二句同义话（一屏两句同一件事）。
                }
                comics.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // 空/塌两句同样由顶行那句 header 念（纯函数原话，屏不另写）。
                }
                else -> androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val cardLayout by com.venera.desktop.settings.DesktopComicCardLayout.config.collectAsState()
                    val columnsCount = cardLayout.resolveGridColumns(this.maxWidth, 150.dp, 3, 6)
                    val coverAspect = cardLayout.resolveCoverAspect(0.72f)
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(columnsCount),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(comics, key = { it.rootPath }) { comic ->
                            val open: (() -> Unit)? = onOpenImported
                                ?.takeIf { comic.sourceKey == DESKTOP_LOCAL_COMIC_IMPORTED_SOURCE }
                                ?.let { cb -> { cb(comic) } }
                            LocalComicCard(
                                comic = comic,
                                coverAspect = coverAspect,
                                onOpen = open,
                            )
                        }
                    }
                }
            }
        }
    }
    if (embedded) {
        androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) { body() }
        return
    }
    DesktopPageShell(
        mode = mode,
        title = "本地漫画",
        // 读数那句在 body 顶行念（合并页 embedded 时也看得见）；页壳这里不再重复一遍，
        // 免得上屏出现两枚同文本节点（语义判据按恰好一枚数）。
        subtitle = null,
        note = note,
        onBack = onBack,
        modifier = modifier,
        width = DesktopPageWidth.Browse,
    ) {
        body()
    }
}

/** 一本一张卡（封面 + 类型角标 + 题名 + 话/页）。 */
@Composable
private fun LocalComicCard(
    comic: DesktopLocalComic,
    coverAspect: Float = 0.72f,
    onOpen: (() -> Unit)?,
) {
    val tokens = VeneraTokens
    val coverFile = remember(comic.rootPath) {
        val dir = java.io.File(comic.rootPath)
        if (dir.isDirectory) {
            dir.walkTopDown().firstOrNull { f ->
                f.isFile && (f.name.endsWith(".jpg", ignoreCase = true) || f.name.endsWith(".png", ignoreCase = true))
            }
        } else null
    }
    val kind = when {
        comic.sourceKey == DESKTOP_LOCAL_COMIC_IMPORTED_SOURCE -> "导入"
        comic.rootPath.endsWith(".cbz", ignoreCase = true) -> "CBZ 归档"
        comic.rootPath.endsWith(".zip", ignoreCase = true) -> "ZIP 归档"
        else -> "本地目录"
    }

    // 本地库卡补右键（打开 / 复制标题；本地没有在线源，不摆搜标签）+ 画廊卡那份悬停抬升。
    com.venera.desktop.ui.components.DesktopComicCardMenuArea(
        actions = com.venera.desktop.ui.components.DesktopComicCardActions(
            sourceKey = comic.sourceKey,
            onOpenDetail = onOpen,
            onCopyTitle = {
                val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                sel.setContents(java.awt.datatransfer.StringSelection(comic.title), null)
            },
        ),
    ) {
    // 立体浮动走公共浮卡框；类型角标（导入 / CBZ / ZIP）就是这张卡的站标，悬停点亮。
    com.venera.desktop.ui.components.DesktopFloatingCard(
        cornerRadius = 12.dp,
        surface = tokens.color.surface,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier),
    ) { floatState ->
        Column {
            val kindEdge by androidx.compose.animation.animateColorAsState(
                targetValue = if (floatState.isHovered) tokens.color.primary.copy(alpha = 0.55f)
                else Color.White.copy(alpha = 0.2f),
                animationSpec = androidx.compose.animation.core.tween(200),
                label = "localKindEdge",
            )
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(coverAspect)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tokens.color.surfaceContainerHigh),
                ) {
                    if (coverFile != null) {
                        AsyncImage(
                            model = coverFile,
                            contentDescription = comic.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .desktopCoverFloat(floatState),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .desktopUiFloat(floatState)
                        .clip(RoundedCornerShape(999.dp))
                        .background(com.venera.desktop.gallery.ui.GalleryTokens.Color.OverlayScrim)
                        .border(1.dp, kindEdge, RoundedCornerShape(999.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = kind,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = Color.White,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Text(
                        text = comic.title,
                        fontSize = VeneraType.aux.size,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                        maxLines = 2,
                        lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "共 ${comic.chapterCount} 话 · ${comic.totalPages} 页" +
                        if (onOpen != null) " · 可直接阅读" else "",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = VeneraType.control.weight,
                    color = if (onOpen != null) tokens.color.primary else tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}

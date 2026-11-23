package com.venera.desktop.gallery.ui

import com.venera.desktop.gallery.ui.selection.LocalSelectionDockHazeState
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.ui.theme.VeneraType
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.venera.compose.components.LocalWindowWidth
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.imageWallColumnCount
import com.venera.compose.components.imageWallHorizontalPadding
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.selection.DockActionPill
import com.venera.desktop.ui.GridPageTemplate
import top.yukonga.miuix.kmp.basic.Text

/**
 * 收藏屏第二分段「画廊收藏」那一面墙（对齐安卓 `GalleryFavoritesBody`）。
 *
 * ## 这一格补的是哪笔账
 *
 * `projectFavorites`与「清空」那两颗纯件（批 77）早就都在，写口 `clear` 也逐枚转交了，
 * 而当前这条活渲染路里**没有一屏**把画廊收藏摆出来（那颗 `DesktopGalleryFavoritesPane.kt` 的
 * `@Composable` 是 0，是 Fluent 遗骸）—— 与 §114/§115/§138 同形的「有逻辑没有门」。
 * 这一批把门开上：收藏屏顶上一排分段（本地 / 画廊 / 网络），画廊那一档渲的就是这颗屏。
 *
 * ## 两条纪律写明
 *
 * ① 投卡走**那颗唯一守卫起口**：壳里 `desktopWallGuardedCards(` 仍恰一处
 *    （`DesktopBrowseHistoryEntryTest` ⑥ 那把账尺不漂），投卡腿转交住在
 *    [desktopGalleryFavoritesWallCards] —— 与 §149 `DesktopPixivWall` 同一手法。
 * ② 单条移除**不在这一屏摆**：卡上不摆单删钮；移除统一走悬停勾选 + 底部悬浮坞的
 *    「取消收藏 (N)」，与主墙同一套多选语言。清空是安卓本就有的，桌面沿用批 77 那两步
 *    （武装 → 再按才真清），label 与可见性都由壳从纯件取，屏不重拼判据。
 *
 * 屏只吃形参（成品），不查库、不自己投卡、不自拼那两句计数 —— 与 `DesktopGallerySearchScreen` 同口径。
 */

/** 画廊收藏那一面墙的投卡腿（守卫起口在 [desktopWallGuardedCards]，这里只转交，不另判）。 */
internal fun desktopGalleryFavoritesWallCards(
    posts: List<GalleryPost>,
    quality: GalleryPreviewQuality,
    guard: GalleryContentGuard?,
    maskMode: String,
    blockAi: Boolean,
): List<DesktopWallCardModel> = desktopWallGuardedCards(posts, quality, guard, maskMode, blockAi)

@Composable
internal fun DesktopGalleryFavoritesWallScreen(
    mode: WideScreenLayoutMode,
    /** 存档读回来了没有。`false` 那一档不许改口成「还没有收藏」。 */
    loaded: Boolean,
    /** 存档总条数（表头那句「共 N 张」吃的就是它，不是上过屏的张数）。 */
    total: Int,
    /** 过了守卫、真画得出来的那些卡。 */
    cards: List<DesktopWallCardModel>,
    /** 挡下的张数（投影可摆 减去 守卫放行）——「已挡下 M 张」那句。 */
    blockedCount: Int,
    /** 「站方键认不出 N 条…」那一句成品（投影算的，空串 = 整行不摆）。 */
    unrenderableLine: String,
    clearVisible: Boolean,
    clearLabel: String,
    onClearClick: () -> Unit,
    /** 清空落定那一句；null = 还没按过。 */
    clearNote: String?,
    onOpenPost: (DesktopWallCardModel) -> Unit,
    /**
     * 批量取消收藏（收到的是 `GalleryFavorite.uid`）。null = 这一侧没有写口 ⇒ 不进入多选。
     */
    onRemoveSelected: ((List<String>) -> Unit)?,
    /** 已被外层统一页壳约束过宽度/内边距时不再重复加一圈。 */
    embedded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val removeSelected = onRemoveSelected
    // 收藏墙的选择账住屏内：这一屏的写口只有「取消收藏」一条，选择不跨屏复用。
    val selectedUids = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val selectedSet = selectedUids.toSet()
    val lastAnchorUid = remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    fun toggle(card: DesktopWallCardModel) {
        if (card.uid.isBlank()) return
        val anchor = lastAnchorUid.value
        if (com.venera.desktop.ui.DesktopModifierState.isShiftDown && anchor != null) {
            val ordered = cards.map { it.uid }.filter { it.isNotBlank() }
            val next = com.venera.desktop.ui.computeShiftRangeSelection(
                orderedKeys = ordered,
                anchorKey = anchor,
                targetKey = card.uid,
                currentSelected = selectedUids.toSet(),
            )
            selectedUids.clear()
            selectedUids.addAll(next)
        } else {
            if (card.uid in selectedUids) selectedUids.remove(card.uid) else selectedUids.add(card.uid)
        }
        lastAnchorUid.value = card.uid
    }
    com.venera.desktop.ui.RegisterDesktopShortcutHandler(
        cards.size,
        selectedSet.size,
        removeSelected != null,
        priority = 100,
    ) { signal ->
        if (removeSelected == null) return@RegisterDesktopShortcutHandler false
        when (com.venera.desktop.ui.matchListShortcut(signal)) {
            com.venera.desktop.ui.DesktopListShortcutAction.SELECT_ALL -> {
                val all = cards.map { it.uid }.filter { it.isNotBlank() }
                if (all.isEmpty()) return@RegisterDesktopShortcutHandler false
                selectedUids.clear()
                selectedUids.addAll(all)
                true
            }
            com.venera.desktop.ui.DesktopListShortcutAction.DELETE_SELECTED -> {
                if (selectedSet.isEmpty()) return@RegisterDesktopShortcutHandler false
                val uids = selectedSet.toList()
                selectedUids.clear()
                removeSelected(uids)
                true
            }
            else -> {
                if (com.venera.desktop.ui.matchGlobalNavShortcut(signal) ==
                    com.venera.desktop.ui.DesktopGlobalNavAction.ESCAPE && selectedSet.isNotEmpty()
                ) {
                    selectedUids.clear()
                    true
                } else {
                    false
                }
            }
        }
    }
    fun copySelected() {
        val text = cards.filter { it.uid in selectedSet }
            .joinToString("\n") { it.fileUrl.ifBlank { it.url } }
        if (text.isBlank()) return
        val sel = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        sel.setContents(java.awt.datatransfer.StringSelection(text), null)
    }
    val windowWidth = LocalWindowWidth.current ?: 1280.dp
    // 叠统一规格的密度偏置，与主墙 / 搜索墙同读一档。
    val wallSpec = LocalDesktopWallSpec.current
    // 列数改走统一网格密度档（固定档钉死 / 自适应 3-6 列）。
    val columns = wallSpec.columnsFor(
        with(androidx.compose.ui.platform.LocalDensity.current) { windowWidth.toPx() },
        com.venera.compose.gallery.domain.GalleryColumnMode.AUTO,
    ).coerceAtLeast(2)
    val dockHazeState = rememberHazeState()
    androidx.compose.runtime.CompositionLocalProvider(LocalSelectionDockHazeState provides dockHazeState) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(dockHazeState, zIndex = 0f)
                .then(
                    if (embedded) Modifier else Modifier.padding(horizontal = tokens.spacing.screenHorizontal),
                ),
        ) {
            // 计数行（安卓 GalleryFavoritesBody:211-217：共 N 张 · 已挡下 M 张 +「清空」）。
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            ) {
                Text(
                    text = when {
                        !loaded -> "正在读画廊收藏…"
                        blockedCount > 0 -> "共 $total 张 · 已挡下 $blockedCount 张"
                        else -> "共 $total 张"
                    },
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (clearVisible) {
                    DesktopPushButton(onClick = onClearClick) {
                        Text(clearLabel, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight)
                    }
                }
            }
            if (clearNote != null) {
                Text(
                    text = clearNote,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = tokens.spacing.space2),
                )
            }
            if (unrenderableLine.isNotEmpty()) {
                Text(
                    text = unrenderableLine,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(bottom = tokens.spacing.space2),
                )
            }
            GridPageTemplate(
                items = cards,
                columns = columns,
                itemKey = { card -> card.uid.ifBlank { card.url } },
                modifier = Modifier.fillMaxSize(),
                spacing = wallSpec.wallGap,
                emptyContent = {
                    // 三档空态各说各话（对齐安卓那两个专属空态 + 还没读回来）。
                    when {
                        !loaded -> CenterNote("加载中…")
                        total > 0 -> CenterNote("收藏内容已隐藏")
                        else -> CenterNote("暂无收藏")
                    }
                },
            ) { card ->
                DesktopWallCard(
                    card = card,
                    onOpenPost = onOpenPost,
                    showInfo = false,
                    selected = card.uid.isNotBlank() && card.uid in selectedSet,
                    onToggleSelect = if (removeSelected != null) ({ toggle(card) }) else null,
                    // 收藏墙不提供画师跳转。
                    onOpenArtist = null,
                )
            }
        }
        if (removeSelected != null) {
            com.venera.desktop.gallery.ui.selection.DesktopSelectionDock(
                selectedCount = selectedSet.size,
                totalCount = cards.size,
                onSelectAll = {
                    val all = cards.map { it.uid }.filter { it.isNotBlank() }
                    selectedUids.clear()
                    selectedUids.addAll(all)
                },
                onInvertSelect = {
                    val all = cards.map { it.uid }.filter { it.isNotBlank() }
                    val next = all.filter { it !in selectedSet }
                    selectedUids.clear()
                    selectedUids.addAll(next)
                },
                onClearSelect = { selectedUids.clear() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = com.venera.desktop.gallery.ui.selection.DesktopSelectionDockBottomInset,
                    ),
            ) {
                DockActionPill(
                    text = "取消收藏 (${selectedSet.size})",
                    danger = true,
                    onClick = {
                        val uids = selectedSet.toList()
                        selectedUids.clear()
                        removeSelected(uids)
                    },
                )
                com.venera.desktop.gallery.ui.selection.DockIconAction(
                    icon = Icons.Outlined.ContentCopy,
                    description = "复制所选图片链接",
                    onClick = { copySelected() },
                )
            }
        }
    }
    }
}

/** 一张收藏卡：与搜索墙那张 `WallResultCard` 同形（封面 + 标题；打码卡点不开，`openable` 一处判）。 */
// 收藏墙卡片也不再自绘：与主墙 / 搜索墙共用同一颗 `DesktopWallCard`（见那颗的头注）。

@Composable
private fun CenterNote(text: String) {
    // 统一空态：居中容器 + 玻璃卡由 DesktopEmptyState 提供，文案仍是逐字钉着的成品句。
    com.venera.desktop.ui.DesktopEmptyState(text = text)
}

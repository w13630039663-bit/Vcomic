package com.venera.desktop.sourcemanage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopSourceUpdater
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.DesktopSegmented
import com.venera.desktop.ui.DesktopSwitch
import com.venera.desktop.ui.components.DesktopGlassCard
import com.venera.desktop.ui.components.DesktopGlassCardLevel
import com.venera.desktop.ui.components.DesktopSearchField
import com.venera.desktop.ui.theme.VeneraType
import com.venera.engineprobe.DesktopSourceUpdate
import com.venera.engineprobe.DesktopSourceUpdate.SourceRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 在线源库条目相对本机的状态（同源比较版本决定升级，异源统一走手动切换）。 */
internal enum class StoreItemState(val rank: Int) {
    Upgradable(0),
    Switchable(1),
    NotInstalled(2),
    UpToDate(3),
}

/** 根据本机实际来源与版本计算在线条目展示状态。 */
internal fun resolveStoreItemState(
    entry: DesktopSourceUpdate.Entry,
    localRow: DesktopSourceRowUi?,
    selectedRepo: SourceRepo,
): StoreItemState {
    if (localRow == null) return StoreItemState.NotInstalled
    val sameOrigin = DesktopSourceUpdater.isSameRepoOrigin(
        isBundled = localRow.isBundled,
        overridden = localRow.overridden,
        repoId = localRow.repoId,
        targetRepo = selectedRepo,
    )
    if (!sameOrigin) return StoreItemState.Switchable
    return if (DesktopSourceUpdate.isNewer(entry.version, localRow.version)) {
        StoreItemState.Upgradable
    } else {
        StoreItemState.UpToDate
    }
}

/**
 * 在线站点库弹窗：全部文案大白话、去技术黑话，同源可一键升级，异源提示手动切换。
 */
@Composable
internal fun DesktopSourceStore(
    selectedRepo: SourceRepo = SourceRepo.OFFICIAL,
    onSelectRepo: (SourceRepo) -> Unit = {},
    proxyProvider: () -> String? = { System.getProperty("venera.proxy")?.takeIf { it.isNotBlank() } },
    /** 某个条目在本机的完整行信息（含版本号、是否开启、真实来源）；null = 未安装。 */
    localRowOf: (entry: DesktopSourceUpdate.Entry) -> DesktopSourceRowUi? = { null },
    /** 开关某颗已安装源是否在站点列表显示。 */
    onToggleEnabled: ((key: String, enabled: Boolean) -> Unit)? = null,
    /** 安装、升级或切换单条；交回 null = 成功，非 null = 失败成因。 */
    onInstall: suspend (entry: DesktopSourceUpdate.Entry) -> String?,
    /** 一键升级当前仓库里本机同源且有新版的全部站。 */
    onUpgradeAll: (() -> Unit)? = null,
    upgradeAllBusy: Boolean = false,
    externalNote: String? = null,
    /** 预置清单（测试用）；null 时弹窗打开后直接拉在线仓库清单。 */
    initialEntries: List<DesktopSourceUpdate.Entry>? = null,
    onClose: () -> Unit,
) {
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    var entries by remember(selectedRepo, initialEntries) { mutableStateOf(initialEntries) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filterTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedRepo, reload) {
        if (initialEntries != null && reload == 0) return@LaunchedEffect
        entries = null
        error = null
        val proxy = proxyProvider()
        val got = withContext(Dispatchers.IO) {
            DesktopSourceUpdate.fetchIndexResult(selectedRepo, proxy)
        }
        got.fold(
            onSuccess = { list -> entries = list },
            onFailure = { t ->
                println("D_源清单更新 拉取失败 repo=${selectedRepo.id} ${t.javaClass.simpleName}: ${t.message?.take(160) ?: ""}")
                com.venera.desktop.data.DesktopErrorBus.report(
                    category = "漫画",
                    headline = "${selectedRepo.displayName} 清单拉取失败",
                    humanReason = "仓库清单没拉到，多半是网络受阻或需要开启代理；可刷新重试或检查网络设置。",
                    detail = "${selectedRepo.indexUrl}: ${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    dedupKey = "comic:source_index_fetch:${selectedRepo.id}",
                )
                error = "清单拉不到：${com.venera.desktop.platform.desktopFailureNoteOf(t)}"
            },
        )
    }

    val rankedItems = entries.orEmpty().map { entry ->
        val localRow = localRowOf(entry)
        val state = resolveStoreItemState(entry, localRow, selectedRepo)
        StoreEntryItem(entry, localRow, state)
    }.sortedBy { it.state.rank }

    val upgradableCount = rankedItems.count { it.state == StoreItemState.Upgradable }
    val switchableCount = rankedItems.count { it.state == StoreItemState.Switchable }
    val filteredItems = rankedItems.filter { item ->
        val matchQuery = query.isBlank() ||
            item.entry.name.contains(query, ignoreCase = true) ||
            item.entry.key.contains(query, ignoreCase = true)
        if (!matchQuery) return@filter false
        when (filterTab) {
            1 -> item.state == StoreItemState.Upgradable
            2 -> item.state == StoreItemState.Switchable
            3 -> item.state == StoreItemState.NotInstalled
            4 -> item.localRow != null
            else -> true
        }
    }

    val anyBusy = busyId != null || upgradeAllBusy

    Dialog(onDismissRequest = { if (!anyBusy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val modalShape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .heightIn(max = (maxHeight - 8.dp).coerceAtMost(660.dp))
                    .background(tokens.color.surface, modalShape)
                    .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.28f), modalShape)
                    .clip(modalShape)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "在线站点库",
                                fontSize = VeneraType.pageTitle.size,
                                lineHeight = VeneraType.pageTitle.lineHeight,
                                fontWeight = FontWeight.SemiBold,
                                color = tokens.color.textPrimary,
                            )
                            StoreOriginChip(
                                text = if (selectedRepo.isCommunity) "来自 laincat/vensrc" else "来自官方仓库",
                            )
                        }
                        val countDesc = entries?.let { "共 ${it.size} 个" } ?: "读取中"
                        Text(
                            text = "${selectedRepo.summaryHint}（$countDesc）",
                            fontSize = VeneraType.aux.size,
                            lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    if (upgradableCount > 0 && onUpgradeAll != null) {
                        DesktopButton(
                            text = if (upgradeAllBusy) "升级中…" else "一键升级 ($upgradableCount)",
                            onClick = { note = null; onUpgradeAll() },
                            size = DesktopButtonSize.Medium,
                            variant = DesktopButtonVariant.Primary,
                            enabled = !anyBusy,
                            loading = upgradeAllBusy,
                        )
                    }
                    DesktopButton(
                        text = "刷新",
                        onClick = { reload++ },
                        size = DesktopButtonSize.Medium,
                        variant = DesktopButtonVariant.Secondary,
                        enabled = !anyBusy,
                    )
                    DesktopButton(
                        text = "关闭",
                        onClick = onClose,
                        size = DesktopButtonSize.Medium,
                        variant = DesktopButtonVariant.Secondary,
                        enabled = !anyBusy,
                    )
                }

                Spacer(Modifier.height(12.dp))

                DesktopSegmented(
                    options = listOf("官方源", "社区稳定 · laincat/vensrc", "社区扩展 · laincat/vensrc"),
                    selectedIndex = SourceRepo.entries.indexOf(selectedRepo).coerceAtLeast(0),
                    onSelect = { idx ->
                        if (anyBusy) return@DesktopSegmented
                        note = null
                        onSelectRepo(SourceRepo.entries[idx])
                    },
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DesktopSegmented(
                        options = listOf("全部", "可升级 ($upgradableCount)", "可切换 ($switchableCount)", "未安装", "已安装"),
                        selectedIndex = filterTab,
                        onSelect = { filterTab = it },
                    )
                    Spacer(Modifier.weight(1f))
                    DesktopSearchField(
                        value = query,
                        onValueChange = { query = it },
                        onSearch = {},
                        collapsedWidth = 180.dp,
                        expandedWidth = 220.dp,
                        placeholder = "搜索漫画站名称…",
                    )
                }

                error?.let {
                    Text(
                        text = it,
                        fontSize = VeneraType.aux.size,
                        lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                (note ?: externalNote)?.let {
                    Text(
                        text = it,
                        fontSize = VeneraType.aux.size,
                        lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                val list = entries
                when {
                    list == null && error == null -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("正在加载 ${selectedRepo.displayName} 清单…", fontSize = VeneraType.aux.size, color = tokens.color.textTertiary)
                        }
                    }
                    list != null && filteredItems.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("这一栏没有匹配的漫画站。", fontSize = VeneraType.body.size, color = tokens.color.textTertiary)
                        }
                    }
                    list != null -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filteredItems, key = { it.entry.let { e -> "${e.repoId}:${e.key}:${e.fileName}" } }) { item ->
                                val entry = item.entry
                                val itemId = "${entry.key}:${entry.fileName}"
                                StoreRow(
                                    entry = entry,
                                    repoBadge = selectedRepo.badgeLabel,
                                    localRow = item.localRow,
                                    state = item.state,
                                    busy = busyId == itemId,
                                    anyBusy = anyBusy,
                                    onToggleEnabled = onToggleEnabled,
                                    onInstall = {
                                        busyId = itemId
                                        note = null
                                        scope.launch {
                                            val why = onInstall(entry)
                                            busyId = null
                                            note = if (why == null) {
                                                when (item.state) {
                                                    StoreItemState.Switchable -> "已将「${entry.name}」切换为 ${selectedRepo.badgeLabel}"
                                                    StoreItemState.Upgradable -> "已升级「${entry.name}」到 v${entry.version.ifBlank { "最新" }}（${selectedRepo.badgeLabel}）"
                                                    StoreItemState.NotInstalled, StoreItemState.UpToDate -> "已安装「${entry.name}」（来自 ${selectedRepo.badgeLabel}），可直接在站点页开启使用"
                                                }
                                            } else {
                                                "${entry.name}: $why"
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class StoreEntryItem(
    val entry: DesktopSourceUpdate.Entry,
    val localRow: DesktopSourceRowUi?,
    val state: StoreItemState,
)

@Composable
private fun StoreRow(
    entry: DesktopSourceUpdate.Entry,
    repoBadge: String,
    localRow: DesktopSourceRowUi?,
    state: StoreItemState,
    busy: Boolean,
    anyBusy: Boolean,
    onToggleEnabled: ((key: String, enabled: Boolean) -> Unit)?,
    onInstall: () -> Unit,
) {
    val tokens = VeneraTokens
    val localVersion = localRow?.version.orEmpty()
    DesktopGlassCard(
        level = DesktopGlassCardLevel.Nested,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = entry.name,
                        fontSize = VeneraType.body.size,
                        lineHeight = VeneraType.body.lineHeight,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when (state) {
                        StoreItemState.Upgradable -> StoreStatusChip(
                            text = "有新版可升级",
                            fg = tokens.color.primary,
                            bg = tokens.color.primary.copy(alpha = 0.14f),
                        )
                        StoreItemState.Switchable -> StoreStatusChip(
                            text = "已装 ${localRow?.originLabel ?: "其它源"}",
                            fg = GalleryTokens.Color.ToneCaution,
                            bg = GalleryTokens.Color.ToneCaution.copy(alpha = 0.14f),
                        )
                        StoreItemState.UpToDate -> StoreStatusChip(
                            text = "已安装",
                            fg = GalleryTokens.Color.StatusSuccess,
                            bg = GalleryTokens.Color.StatusSuccess.copy(alpha = 0.14f),
                        )
                        StoreItemState.NotInstalled -> Unit
                    }
                }
                val localOrigin = localRow?.originLabel ?: repoBadge
                val versionLine = when (state) {
                    StoreItemState.Upgradable -> {
                        val cur = if (localVersion.isNotBlank()) "当前 v$localVersion" else "当前内置版"
                        "$cur → 新版 v${entry.version} · 同源（$localOrigin）"
                    }
                    StoreItemState.Switchable -> {
                        val curVer = if (localVersion.isNotBlank()) " v$localVersion" else ""
                        val targetVer = if (entry.version.isNotBlank()) " v${entry.version}" else ""
                        "当前：$localOrigin$curVer → 切为 $repoBadge$targetVer"
                    }
                    StoreItemState.UpToDate -> {
                        val cur = if (localVersion.isNotBlank()) "已装 v$localVersion · " else ""
                        "${cur}来源：$localOrigin"
                    }
                    StoreItemState.NotInstalled -> "版本 v${entry.version.ifBlank { "-" }} · 来源：$repoBadge"
                }
                Text(
                    text = versionLine,
                    fontSize = VeneraType.micro.size,
                    lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            when (state) {
                StoreItemState.NotInstalled -> DesktopButton(
                    text = if (busy) "安装中" else "安装",
                    onClick = onInstall,
                    size = DesktopButtonSize.Compact,
                    variant = DesktopButtonVariant.Secondary,
                    enabled = !anyBusy,
                    loading = busy,
                )
                StoreItemState.Switchable -> DesktopButton(
                    text = if (busy) "切换中" else "切换",
                    onClick = onInstall,
                    size = DesktopButtonSize.Compact,
                    variant = DesktopButtonVariant.Secondary,
                    enabled = !anyBusy,
                    loading = busy,
                )
                StoreItemState.Upgradable -> DesktopButton(
                    text = if (busy) "升级中" else "升级",
                    onClick = onInstall,
                    size = DesktopButtonSize.Compact,
                    variant = DesktopButtonVariant.Primary,
                    enabled = !anyBusy,
                    loading = busy,
                )
                StoreItemState.UpToDate -> Text(
                    text = "已是最新",
                    fontSize = VeneraType.aux.size,
                    lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (localRow != null && onToggleEnabled != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (localRow.enabled) "显示" else "隐藏",
                        fontSize = VeneraType.micro.size,
                        color = if (localRow.enabled) tokens.color.textSecondary else tokens.color.textTertiary,
                    )
                    DesktopSwitch(
                        checked = localRow.enabled,
                        onCheckedChange = { next -> onToggleEnabled(localRow.key, next) },
                        enabled = !anyBusy && localRow.canDisable,
                        modifier = Modifier.semantics { contentDescription = "源库开关 ${localRow.key}" },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreOriginChip(text: String) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.60f), RoundedCornerShape(999.dp))
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = tokens.color.textSecondary,
        )
    }
}

@Composable
private fun StoreStatusChip(text: String, fg: Color, bg: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    ) {
        Text(text = text, fontSize = 10.sp, lineHeight = 12.sp, color = fg)
    }
}

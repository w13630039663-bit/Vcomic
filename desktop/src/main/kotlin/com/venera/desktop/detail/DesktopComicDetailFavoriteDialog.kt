package com.venera.desktop.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.DesktopMacDialog
import com.venera.desktop.ui.MacDialogCheckbox
import com.venera.desktop.ui.MacDialogTextButton
import com.venera.desktop.ui.theme.VeneraType
// ════════════════════════════ 收藏弹窗 ════════════════════════════

/**
 * 收藏弹窗：本地段复选、网络段逐夹复选，点一下立即落笔且不关窗，底部统一收口。
 *
 * 与旧版的关键差异：条目不再点一下就把弹窗关掉，勾选状态以复选框呈现（旧版圆钮
 * 的 `+` 看起来像"新建"）；本地段多出「新建收藏夹」行内输入。
 *
 * @param data 详情页展示数据，本地夹名单与已收档都从它读。
 * @param onCreateFolder 新建夹写口；null = 写口没接，新建行不摆。
 * @param onToggleFavoriteFolder 加入 / 移出某个本地夹（同一夹名二态切换）。
 * @param onToggleNetworkFavorite 网络收藏那一跳：夹 id + 当前是否已收。
 * @param onDismiss 关闭弹窗（遮罩点击、「关闭」「完成」同语义）。
 */
@Composable
internal fun FavoriteDialog(
    data: DesktopDetailViewData,
    onCreateFolder: ((String) -> Unit)?,
    onToggleFavoriteFolder: (String) -> Unit,
    onToggleNetworkFavorite: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = VeneraTokens
    var localExpanded by remember { mutableStateOf(true) }
    var networkExpanded by remember { mutableStateOf(true) }
    var creatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val selectedCount = data.favoriteAddedFolders.size +
        (if (data.networkSingle == true) 1 else data.networkAdded.size)

    DesktopMacDialog(
        onDismissRequest = onDismiss,
        title = "收藏到",
        width = 460.dp,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── 本地收藏夹 ──
                FavoritePanelGroup {
                    FavoriteGroupHead(
                        title = com.venera.desktop.settings.DesktopI18n.t("本地收藏夹", "Local folders"),
                        trailing = com.venera.desktop.settings.DesktopI18n.t("${data.favoriteFolders.size} 个夹", "${data.favoriteFolders.size} folders"),
                        expanded = localExpanded,
                        onToggle = { localExpanded = !localExpanded },
                    )
                    androidx.compose.animation.AnimatedVisibility(visible = localExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 264.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            if (data.favoriteFolders.isEmpty() && !creatingFolder) {
                                Text(
                                    text = com.venera.desktop.settings.DesktopI18n.t("还没有收藏夹", "No folders yet"),
                                    fontSize = VeneraType.aux.size,
                                    lineHeight = VeneraType.aux.lineHeight,
                                    color = tokens.color.textTertiary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                            data.favoriteFolders.forEach { folder ->
                                FavoriteCheckRow(
                                    name = folder,
                                    checked = folder in data.favoriteAddedFolders,
                                    onClick = { onToggleFavoriteFolder(folder) },
                                )
                            }
                            if (creatingFolder) {
                                FavoriteNewFolderInput(
                                    value = newFolderName,
                                    onValueChange = { newFolderName = it },
                                    onCancel = {
                                        creatingFolder = false
                                        newFolderName = ""
                                    },
                                    onConfirm = {
                                        newFolderName.trim().takeIf { it.isNotEmpty() }?.let { name ->
                                            onCreateFolder?.invoke(name)
                                            creatingFolder = false
                                            newFolderName = ""
                                        }
                                    },
                                )
                            } else if (onCreateFolder != null) {
                                FavoriteNewFolderRow(onClick = { creatingFolder = true })
                            }
                        }
                    }
                }

                // ── 网络收藏夹 ──
                FavoritePanelGroup {
                    FavoriteGroupHead(
                        title = com.venera.desktop.settings.DesktopI18n.t("网络收藏夹", "Network favorites"),
                        trailing = if (data.networkAvailable) com.venera.desktop.settings.DesktopI18n.t("当前源", "Active source") else com.venera.desktop.settings.DesktopI18n.t("不可用", "Unavailable"),
                        expanded = networkExpanded && data.networkAvailable,
                        onToggle = { networkExpanded = !networkExpanded },
                    )
                    if (!data.networkAvailable) {
                        Text(
                            text = com.venera.desktop.settings.DesktopI18n.t("未登录或这一源不支持网络收藏", "Not signed in or unsupported by this source"),
                            fontSize = VeneraType.aux.size,
                            lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    } else {
                        androidx.compose.animation.AnimatedVisibility(visible = networkExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                when {
                                    data.networkFolders.isNotEmpty() -> {
                                        data.networkFolders.forEach { (folderId, folderName) ->
                                            val added = folderId in data.networkAdded
                                            FavoriteCheckRow(
                                                name = folderName,
                                                checked = added,
                                                onClick = { onToggleNetworkFavorite(folderId, added) },
                                            )
                                        }
                                    }
                                    data.networkSingle != null -> {
                                        FavoriteCheckRow(
                                            name = if (data.networkSingle) {
                                                com.venera.desktop.settings.DesktopI18n.t("已加入这一源的网络收藏", "Added to source favorites")
                                            } else {
                                                com.venera.desktop.settings.DesktopI18n.t("加入这一源的网络收藏", "Add to source favorites")
                                            },
                                            checked = data.networkSingle,
                                            onClick = { onToggleNetworkFavorite("", data.networkSingle) },
                                        )
                                    }
                                    data.networkLoading -> {
                                        Text(
                                            text = com.venera.desktop.settings.DesktopI18n.t("正在加载…", "Loading…"),
                                            fontSize = VeneraType.aux.size,
                                            lineHeight = VeneraType.aux.lineHeight,
                                            color = tokens.color.textTertiary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        )
                                    }
                                    data.networkError != null -> {
                                        Text(
                                            text = com.venera.desktop.settings.DesktopI18n.t("网络收藏加载失败", "Failed to load network favorites"),
                                            fontSize = VeneraType.aux.size,
                                            lineHeight = VeneraType.aux.lineHeight,
                                            color = StatusColors.Degraded,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = com.venera.desktop.settings.DesktopI18n.t("这一源没有网络收藏夹", "No network folders for this source"),
                                            fontSize = VeneraType.aux.size,
                                            lineHeight = VeneraType.aux.lineHeight,
                                            color = tokens.color.textTertiary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = com.venera.desktop.settings.DesktopI18n.t("已选 $selectedCount 个夹", "$selectedCount selected"),
                        fontSize = VeneraType.aux.size,
                        lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    MacDialogTextButton(text = com.venera.desktop.settings.DesktopI18n.t("关闭", "Close"), onClick = onDismiss)
                    MacDialogTextButton(text = com.venera.desktop.settings.DesktopI18n.t("完成", "Done"), primary = true, onClick = onDismiss)
                }
            }
        },
    )
}

/** 弹窗内一段分区容器：同底、同描边、同心圆角，两段视觉一致。 */
@Composable
private fun FavoritePanelGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.55f))
            .border(0.5.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(6.dp),
        content = content,
    )
}

/** 分区头：箭头 + 标题 + 右侧计数，整行可点折叠。箭头自绘，不用字符字形。 */
@Composable
private fun FavoriteGroupHead(
    title: String,
    trailing: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FavoriteChevron(down = expanded, tint = tokens.color.textTertiary, modifier = Modifier.size(12.dp))
        Text(
            text = title,
            fontSize = VeneraType.cardTitle.size,
            lineHeight = VeneraType.cardTitle.lineHeight,
            fontWeight = FontWeight.SemiBold,
            color = tokens.color.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = trailing,
            fontSize = VeneraType.micro.size,
            lineHeight = VeneraType.micro.lineHeight,
            color = tokens.color.textTertiary,
        )
    }
}

/** 折叠箭头：两笔画 V，展开朝下、收起朝右。 */
@Composable
private fun FavoriteChevron(down: Boolean, tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            if (down) {
                moveTo(w * 0.18f, h * 0.34f)
                lineTo(w * 0.5f, h * 0.66f)
                lineTo(w * 0.82f, h * 0.34f)
            } else {
                moveTo(w * 0.34f, h * 0.18f)
                lineTo(w * 0.66f, h * 0.5f)
                lineTo(w * 0.34f, h * 0.82f)
            }
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = h * 0.12f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

/** 夹条目：复选块 + 名称 +（已收时）右侧状态字。整行可点。 */
@Composable
private fun FavoriteCheckRow(
    name: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (checked) tokens.color.primary.copy(alpha = 0.12f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MacDialogCheckbox(checked = checked, onCheckedChange = { onClick() })
        Text(
            text = name,
            fontSize = VeneraType.body.size,
            lineHeight = VeneraType.body.lineHeight,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (checked) {
            Text(
                text = com.venera.desktop.settings.DesktopI18n.t("已收藏", "Favorited"),
                fontSize = VeneraType.micro.size,
                lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.primary,
            )
        }
    }
}

/** 「新建收藏夹」入口行：虚线方框 + 灰字，暗示这是动作不是勾选项。 */
@Composable
private fun FavoriteNewFolderRow(onClick: () -> Unit) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(shape)
                .border(0.5.dp, tokens.color.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+", fontSize = 10.sp, color = tokens.color.textSecondary)
        }
        Text(
            text = com.venera.desktop.settings.DesktopI18n.t("新建收藏夹", "New folder"),
            fontSize = VeneraType.body.size,
            lineHeight = VeneraType.body.lineHeight,
            color = tokens.color.textSecondary,
        )
    }
}

/** 新建夹的行内输入：就地编辑，不弹第二层对话框。 */
@Composable
private fun FavoriteNewFolderInput(
    value: String,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    com.venera.desktop.ui.components.DesktopInputField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = com.venera.desktop.settings.DesktopI18n.t("输入收藏夹名", "Enter folder name"),
        trailing = {
            MacDialogTextButton(text = com.venera.desktop.settings.DesktopI18n.t("创建", "Create"), onClick = onConfirm)
            MacDialogTextButton(text = com.venera.desktop.settings.DesktopI18n.t("取消", "Cancel"), onClick = onCancel)
        },
    )
}

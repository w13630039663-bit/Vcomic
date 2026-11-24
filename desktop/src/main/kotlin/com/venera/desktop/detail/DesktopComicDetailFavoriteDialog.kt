package com.venera.desktop.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.settings.DesktopI18n
import com.venera.desktop.ui.DesktopMacDialog
import com.venera.desktop.ui.MacDialogCheckbox
import com.venera.desktop.ui.MacDialogTextButton
import com.venera.desktop.ui.components.DesktopInputField
import com.venera.desktop.ui.theme.VeneraType

// ════════════════════════════ 收藏弹窗 ════════════════════════════

/** 行尾收藏状态枚举，区分“原本已收藏”、“本次新勾选待收藏”与“原本已收但本次取消”。 */
private enum class FavoriteRowStatus {
    NONE,
    FAVORITED,
    PENDING_ADD,
    PENDING_REMOVE,
}

/**
 * 收藏弹窗：本地段复选、网络段逐夹复选。
 *
 * 弹窗内点选仅更新草稿勾选状态，不抢先落库也不显示「已收藏」；
 * 点「完成」时统一提交与初始状态的差异，点「关闭」或遮罩则放弃草稿。
 *
 * @param data 详情页展示数据，本地夹名单与已收档都从它读。
 * @param onCreateFolder 新建夹写口；null = 写口没接，新建行不摆。
 * @param onToggleFavoriteFolder 加入 / 移出某个本地夹（同一夹名二态切换）。
 * @param onToggleNetworkFavorite 网络收藏那一跳：夹 id + 当前是否已收。
 * @param onDismiss 关闭弹窗（遮罩点击与「关闭」放弃草稿；「完成」先提交再关窗）。
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
    val reduceMotion = VeneraMotion.reduceMotion
    val density = LocalDensity.current

    var localExpanded by remember { mutableStateOf(true) }
    var networkExpanded by remember { mutableStateOf(true) }
    var creatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // 记录打开弹窗时的基线已收状态，用于区分「已收藏」与「待收藏」以及计算点「完成」时的增量。
    val initialLocal = remember { data.favoriteAddedFolders }
    val initialNetwork = remember { data.networkAdded }
    val initialNetworkSingle = remember { data.networkSingle }

    var draftLocal by remember { mutableStateOf(initialLocal) }
    var draftNetwork by remember { mutableStateOf(initialNetwork) }
    var draftNetworkSingle by remember { mutableStateOf(initialNetworkSingle) }

    // 异步拉取的网络收藏夹在弹窗打开后才就位时，同步一次基线与草稿（仅当草稿尚未被用户改动）。
    LaunchedEffect(data.networkAdded, data.networkSingle) {
        if (draftNetwork == initialNetwork && data.networkAdded != initialNetwork) {
            draftNetwork = data.networkAdded
        }
        if (draftNetworkSingle == initialNetworkSingle && data.networkSingle != initialNetworkSingle) {
            draftNetworkSingle = data.networkSingle
        }
    }

    val selectedCount = draftLocal.size +
        (if (draftNetworkSingle == true) 1 else draftNetwork.size)

    val hasChanges = draftLocal != initialLocal ||
        draftNetwork != initialNetwork ||
        draftNetworkSingle != initialNetworkSingle

    // 弹窗入场动画：轻柔弹性缩放 + 上浮淡入。
    val enterProgress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            enterProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }
    val slideOffsetPx = with(density) { VeneraMotion.enterTranslationY.toPx() }

    DesktopMacDialog(
        onDismissRequest = onDismiss,
        title = "收藏到",
        width = 460.dp,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = enterProgress.value
                        alpha = p.coerceIn(0f, 1f)
                        if (!reduceMotion) {
                            val s = 0.95f + 0.05f * p
                            scaleX = s
                            scaleY = s
                            translationY = (1f - p) * slideOffsetPx
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ── 本地收藏夹 ──
                FavoritePanelGroup {
                    FavoriteGroupHead(
                        title = DesktopI18n.t("本地收藏夹", "Local folders"),
                        trailing = DesktopI18n.t("${data.favoriteFolders.size} 个夹", "${data.favoriteFolders.size} folders"),
                        expanded = localExpanded,
                        onToggle = { localExpanded = !localExpanded },
                    )
                    AnimatedVisibility(visible = localExpanded) {
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
                                    text = DesktopI18n.t("还没有收藏夹", "No folders yet"),
                                    fontSize = VeneraType.aux.size,
                                    lineHeight = VeneraType.aux.lineHeight,
                                    color = tokens.color.textTertiary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                            data.favoriteFolders.forEach { folder ->
                                val isChecked = folder in draftLocal
                                val wasAdded = folder in initialLocal
                                val rowStatus = when {
                                    wasAdded && isChecked -> FavoriteRowStatus.FAVORITED
                                    wasAdded && !isChecked -> FavoriteRowStatus.PENDING_REMOVE
                                    !wasAdded && isChecked -> FavoriteRowStatus.PENDING_ADD
                                    else -> FavoriteRowStatus.NONE
                                }
                                FavoriteCheckRow(
                                    name = folder,
                                    checked = isChecked,
                                    status = rowStatus,
                                    onClick = {
                                        draftLocal = if (isChecked) draftLocal - folder else draftLocal + folder
                                    },
                                )
                            }
                            if (onCreateFolder != null) {
                                AnimatedContent(
                                    targetState = creatingFolder,
                                    transitionSpec = {
                                        (fadeIn(VeneraMotion.tweenFast()) + slideInVertically(VeneraMotion.tweenFast()) { it / 4 })
                                            .togetherWith(fadeOut(VeneraMotion.tweenFast()) + slideOutVertically(VeneraMotion.tweenFast()) { -it / 4 })
                                    },
                                    label = "NewFolderTransition",
                                ) { isCreating ->
                                    if (isCreating) {
                                        FavoriteNewFolderInput(
                                            value = newFolderName,
                                            onValueChange = { newFolderName = it },
                                            onCancel = {
                                                creatingFolder = false
                                                newFolderName = ""
                                            },
                                            onConfirm = {
                                                newFolderName.trim().takeIf { it.isNotEmpty() }?.let { name ->
                                                    onCreateFolder(name)
                                                    draftLocal = draftLocal + name
                                                    creatingFolder = false
                                                    newFolderName = ""
                                                }
                                            },
                                        )
                                    } else {
                                        FavoriteNewFolderRow(onClick = { creatingFolder = true })
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 网络收藏夹 ──
                FavoritePanelGroup {
                    FavoriteGroupHead(
                        title = DesktopI18n.t("网络收藏夹", "Network favorites"),
                        trailing = if (data.networkAvailable) DesktopI18n.t("当前源", "Active source") else DesktopI18n.t("不可用", "Unavailable"),
                        expanded = networkExpanded && data.networkAvailable,
                        onToggle = { networkExpanded = !networkExpanded },
                    )
                    if (!data.networkAvailable) {
                        Text(
                            text = DesktopI18n.t("未登录或这一源不支持网络收藏", "Not signed in or unsupported by this source"),
                            fontSize = VeneraType.aux.size,
                            lineHeight = VeneraType.aux.lineHeight,
                            color = tokens.color.textTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    } else {
                        AnimatedVisibility(visible = networkExpanded) {
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
                                            val isChecked = folderId in draftNetwork
                                            val wasAdded = folderId in initialNetwork
                                            val rowStatus = when {
                                                wasAdded && isChecked -> FavoriteRowStatus.FAVORITED
                                                wasAdded && !isChecked -> FavoriteRowStatus.PENDING_REMOVE
                                                !wasAdded && isChecked -> FavoriteRowStatus.PENDING_ADD
                                                else -> FavoriteRowStatus.NONE
                                            }
                                            FavoriteCheckRow(
                                                name = folderName,
                                                checked = isChecked,
                                                status = rowStatus,
                                                onClick = {
                                                    draftNetwork = if (isChecked) draftNetwork - folderId else draftNetwork + folderId
                                                },
                                            )
                                        }
                                    }
                                    draftNetworkSingle != null -> {
                                        val isChecked = draftNetworkSingle == true
                                        val wasAdded = initialNetworkSingle == true
                                        val rowStatus = when {
                                            wasAdded && isChecked -> FavoriteRowStatus.FAVORITED
                                            wasAdded && !isChecked -> FavoriteRowStatus.PENDING_REMOVE
                                            !wasAdded && isChecked -> FavoriteRowStatus.PENDING_ADD
                                            else -> FavoriteRowStatus.NONE
                                        }
                                        FavoriteCheckRow(
                                            name = if (wasAdded && isChecked) {
                                                DesktopI18n.t("已加入这一源的网络收藏", "Added to source favorites")
                                            } else {
                                                DesktopI18n.t("加入这一源的网络收藏", "Add to source favorites")
                                            },
                                            checked = isChecked,
                                            status = rowStatus,
                                            onClick = { draftNetworkSingle = !isChecked },
                                        )
                                    }
                                    data.networkLoading -> {
                                        Text(
                                            text = DesktopI18n.t("正在加载…", "Loading…"),
                                            fontSize = VeneraType.aux.size,
                                            lineHeight = VeneraType.aux.lineHeight,
                                            color = tokens.color.textTertiary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        )
                                    }
                                    data.networkError != null -> {
                                        Text(
                                            text = DesktopI18n.t("网络收藏加载失败", "Failed to load network favorites"),
                                            fontSize = VeneraType.aux.size,
                                            lineHeight = VeneraType.aux.lineHeight,
                                            color = StatusColors.Degraded,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = DesktopI18n.t("这一源没有网络收藏夹", "No network folders for this source"),
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

                // ── 底部操作栏 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = selectedCount,
                            transitionSpec = {
                                val dir = if (targetState > initialState) 1 else -1
                                (fadeIn(VeneraMotion.tweenFast()) + slideInVertically(VeneraMotion.tweenFast()) { dir * it / 2 })
                                    .togetherWith(fadeOut(VeneraMotion.tweenFast()) + slideOutVertically(VeneraMotion.tweenFast()) { -dir * it / 2 })
                            },
                            label = "SelectedCountAnim",
                        ) { count ->
                            Text(
                                text = DesktopI18n.t("已选 $count 个夹", "$count selected"),
                                fontSize = VeneraType.aux.size,
                                lineHeight = VeneraType.aux.lineHeight,
                                color = if (hasChanges) tokens.color.primary else tokens.color.textTertiary,
                            )
                        }
                    }
                    MacDialogTextButton(
                        text = DesktopI18n.t("关闭", "Close"),
                        onClick = onDismiss,
                    )
                    MacDialogTextButton(
                        text = DesktopI18n.t("完成", "Done"),
                        primary = true,
                        onClick = {
                            // 仅提交有变化的本地收藏夹。
                            val addedLocal = draftLocal - initialLocal
                            val removedLocal = initialLocal - draftLocal
                            addedLocal.forEach { folder -> onToggleFavoriteFolder(folder) }
                            removedLocal.forEach { folder -> onToggleFavoriteFolder(folder) }

                            // 仅提交有变化的网络收藏夹。
                            val addedNet = draftNetwork - initialNetwork
                            val removedNet = initialNetwork - draftNetwork
                            addedNet.forEach { onToggleNetworkFavorite(it, false) }
                            removedNet.forEach { onToggleNetworkFavorite(it, true) }

                            if (draftNetworkSingle != initialNetworkSingle && draftNetworkSingle != null) {
                                onToggleNetworkFavorite("", initialNetworkSingle == true)
                            }
                            onDismiss()
                        },
                    )
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

/** 分区头：旋转箭头 + 标题 + 右侧计数，整行可点折叠。 */
@Composable
private fun FavoriteGroupHead(
    title: String,
    trailing: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = VeneraTokens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bgColor by animateColorAsState(
        targetValue = if (hovered) tokens.color.surfaceContainerHigh.copy(alpha = 0.85f) else Color.Transparent,
        animationSpec = VeneraMotion.tweenFast(),
        label = "GroupHeadBg",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
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

/** 折叠箭头：自绘向右折线，通过旋转动画在收起（0°）与展开（90°）间平滑过渡。 */
@Composable
private fun FavoriteChevron(down: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(
        targetValue = if (down) 90f else 0f,
        animationSpec = if (VeneraMotion.reduceMotion) {
            VeneraMotion.tweenFast()
        } else {
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
        },
        label = "ChevronRotation",
    )
    Canvas(
        modifier.graphicsLayer { rotationZ = rotation },
    ) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.34f, h * 0.18f)
            lineTo(w * 0.66f, h * 0.5f)
            lineTo(w * 0.34f, h * 0.82f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = h * 0.12f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/** 夹条目：弹性按压 + 悬停高亮 + 弹性复选块 + 名称 + 状态标签滑入。 */
@Composable
private fun FavoriteCheckRow(
    name: String,
    checked: Boolean,
    status: FavoriteRowStatus,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    val reduceMotion = VeneraMotion.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val rowBg by animateColorAsState(
        targetValue = when {
            checked && hovered -> tokens.color.primary.copy(alpha = 0.18f)
            checked -> tokens.color.primary.copy(alpha = 0.12f)
            hovered -> tokens.color.surfaceContainerHigh.copy(alpha = 0.80f)
            else -> Color.Transparent
        },
        animationSpec = VeneraMotion.tweenFast(),
        label = "CheckRowBg",
    )

    val rowScale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.975f else 1f,
        animationSpec = if (reduceMotion) VeneraMotion.tweenFast() else spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "CheckRowScale",
    )

    val checkBounce by animateFloatAsState(
        targetValue = if (checked) 1f else 0.92f,
        animationSpec = if (reduceMotion) VeneraMotion.tweenFast() else spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMedium),
        label = "CheckboxBounce",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(RoundedCornerShape(7.dp))
            .background(rowBg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MacDialogCheckbox(
            checked = checked,
            onCheckedChange = { onClick() },
            modifier = Modifier.graphicsLayer {
                if (!reduceMotion) {
                    scaleX = checkBounce
                    scaleY = checkBounce
                }
            },
        )
        Text(
            text = name,
            fontSize = VeneraType.body.size,
            lineHeight = VeneraType.body.lineHeight,
            color = tokens.color.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = status != FavoriteRowStatus.NONE,
            enter = fadeIn(VeneraMotion.tweenFast()) + slideInHorizontally(VeneraMotion.tweenFast()) { it / 3 },
            exit = fadeOut(VeneraMotion.tweenFast()) + slideOutHorizontally(VeneraMotion.tweenFast()) { it / 3 },
        ) {
            val (label, color) = when (status) {
                FavoriteRowStatus.FAVORITED -> DesktopI18n.t("已收藏", "Favorited") to tokens.color.primary
                FavoriteRowStatus.PENDING_ADD -> DesktopI18n.t("待收藏", "To add") to tokens.color.primary.copy(alpha = 0.8f)
                FavoriteRowStatus.PENDING_REMOVE -> DesktopI18n.t("将移出", "To remove") to StatusColors.Degraded
                FavoriteRowStatus.NONE -> "" to Color.Transparent
            }
            Text(
                text = label,
                fontSize = VeneraType.micro.size,
                lineHeight = VeneraType.micro.lineHeight,
                color = color,
            )
        }
    }
}

/** 「新建收藏夹」入口行：自绘居中加号方框 + 悬停高亮，与上方复选框严丝合缝对齐。 */
@Composable
private fun FavoriteNewFolderRow(onClick: () -> Unit) {
    val tokens = VeneraTokens
    val shape = RoundedCornerShape(4.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val bgColor by animateColorAsState(
        targetValue = if (hovered) tokens.color.surfaceContainerHigh.copy(alpha = 0.80f) else Color.Transparent,
        animationSpec = VeneraMotion.tweenFast(),
        label = "NewFolderRowBg",
    )
    val rowScale by animateFloatAsState(
        targetValue = if (pressed && !VeneraMotion.reduceMotion) 0.975f else 1f,
        animationSpec = VeneraMotion.tweenFast(),
        label = "NewFolderRowScale",
    )
    val iconTint = if (hovered) tokens.color.textPrimary else tokens.color.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = rowScale
                scaleY = rowScale
            }
            .clip(RoundedCornerShape(7.dp))
            .background(bgColor)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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
            // 自绘居中加号，避免 Text 字体行高与基线导致的右下角偏位。
            Canvas(modifier = Modifier.size(8.dp)) {
                val stroke = 1.25.dp.toPx()
                val midX = size.width / 2f
                val midY = size.height / 2f
                drawLine(
                    color = iconTint,
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = iconTint,
                    start = Offset(midX, 0f),
                    end = Offset(midX, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
        Text(
            text = DesktopI18n.t("新建收藏夹", "New folder"),
            fontSize = VeneraType.body.size,
            lineHeight = VeneraType.body.lineHeight,
            color = iconTint,
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
    DesktopInputField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = DesktopI18n.t("输入收藏夹名", "Enter folder name"),
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MacDialogTextButton(text = DesktopI18n.t("创建", "Create"), onClick = onConfirm)
                MacDialogTextButton(text = DesktopI18n.t("取消", "Cancel"), onClick = onCancel)
            }
        },
    )
}

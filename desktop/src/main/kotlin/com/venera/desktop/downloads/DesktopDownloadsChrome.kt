package com.venera.desktop.downloads

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.ui.DesktopPushButton

/**
 * 「下载与本地」的设计稿件集（的 .badge/.bar/.ib/.mini/.urlbox/.errcard/.thread/
 * .filelist/.thumb 逐颗落地，令"完全按设计稿，不复用旧 UI"）。
 *
 * 队列三屏（下载队列 / 本机已下载 / 图片库）只从这一套件里取形状 —— 旧文本按钮排、
 * 纯文本行那些不再上这一页。稿件三色（ok/warn/danger）不在主题色板里，按稿做**局部常量**，
 * 不进 tokens（那是全站语义色，不该由这一页造）。
 */
internal object DqTone {
    // 状态三色转发全局槽（阶段 2.3 收编）：F28B82/F2B87C/78D6C0 的语义真身在
    // GalleryColors.Tone*，这里只留名字 —— 值单源，DqTone 不再是第二处色板。
    // GalleryTokens.Color 是随明暗取档的 @Composable getter，这里同形转发。
    val ok: Color
        @Composable @ReadOnlyComposable get() = com.venera.desktop.gallery.ui.GalleryTokens.Color.ToneSafe
    val warn: Color
        @Composable @ReadOnlyComposable get() = com.venera.desktop.gallery.ui.GalleryTokens.Color.ToneCaution
    val fail: Color
        @Composable @ReadOnlyComposable get() = com.venera.desktop.gallery.ui.GalleryTokens.Color.ToneDanger
}

/** 稿 `.badge`：五档状态胶囊（b-run/b-ok/b-fail/b-wait/b-pause 用 tone 传色）。 */
@Composable
internal fun DqBadge(label: String, tone: Color, filled: Boolean = false) {
    val tokens = VeneraTokens
    Text(
        text = label,
        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
        fontWeight = VeneraType.cardTitle.weight,
        color = if (filled) Color.White else tone,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (filled) tone else tone.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** 稿 `.bar`：5px 进度条。 */
@Composable
internal fun DqBar(frac: Float, tone: Color, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(tokens.color.surfaceContainerHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(frac.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(tone),
        )
    }
}

/** 稿 `.ib`：32×32 图标把手（hover 语义桌面端用点击态描边；danger 给红边档）。 */
@Composable
internal fun DqIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tokens = VeneraTokens
    val line = if (danger) DqTone.fail.copy(alpha = 0.55f) else tokens.color.outlineVariant.copy(alpha = 0.5f)
    // 焦点环（焦点环扩展）：常驻 2dp 位，焦点 error/主色环，宽度恒定。
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(tokens.color.surfaceVariant.copy(alpha = 0.4f))
            .border(2.dp, if (focused) tokens.color.primary else Color.Transparent, RoundedCornerShape(9.dp))
            .border(1.dp, line, RoundedCornerShape(9.dp))
            .hoverable(interaction)
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (danger) DqTone.fail else tokens.color.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** 稿 `.mini`：详情里的小胶囊按钮（primary = 主题实底）。 */
@Composable
internal fun DqMiniButton(text: String, onClick: () -> Unit, primary: Boolean = false) {
    // 全仓钮归一：删壳委托 DesktopPushButton（原 r8/1dp 描边/无 hover 的独门形退役；
    // 主档走 accent 色板，与全仓主钮一族同源）。
    DesktopPushButton(text = text, onClick = onClick, primary = primary)
}

/** 稿 `.dh`：详情小节的引导题（大写字距感的小灰字）。 */
@Composable
internal fun DqSectionHead(text: String) {
    Text(
        text = text,
        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
        fontWeight = VeneraType.cardTitle.weight,
        color = VeneraTokens.color.textTertiary,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

/** 稿 `.urlbox`：等宽文本框 + 自带复制（点击落剪贴板，不摆假把手）。 */
@Composable
internal fun DqUrlBox(text: String) {
    val tokens = VeneraTokens
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(tokens.color.surface.copy(alpha = 0.5f))
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            fontFamily = FontFamily.Monospace,
            color = tokens.color.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "复制这一串",
            tint = tokens.color.textTertiary,
            modifier = Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { clipboard.setText(AnnotatedString(text)) },
        )
    }
}

/** 稿 `.errcard`：失败原因红卡（errhead 警示 + 正文原话）。 */
@Composable
internal fun DqErrCard(message: String) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DqTone.fail.copy(alpha = 0.08f))
            .border(1.dp, DqTone.fail.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = DqTone.fail,
                modifier = Modifier.size(15.dp),
            )
            Text("失败原因", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = DqTone.fail)
        }
        Text(message, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textSecondary, modifier = Modifier.padding(top = 5.dp))
    }
}

/** 稿 `.thread`：线程一行（#号主题色 + 等宽工单 + 状态尾段）。 */
@Composable
internal fun DqThreadRow(tid: String, work: String, state: String, active: Boolean = false) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(tokens.color.surface.copy(alpha = 0.45f))
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tid, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, fontWeight = VeneraType.cardTitle.weight, color = tokens.color.primary)
        Text(
            work,
            fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
            fontFamily = FontFamily.Monospace,
            color = if (active) tokens.color.textSecondary else tokens.color.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(state, fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary, maxLines = 1)
    }
}

/** 稿 `.filelist`：带框滚动文件清单（fn + 在盘角 + 等宽体积）。 */
@Composable
internal fun DqFileList(
    files: List<Pair<String, Long>>,
    loading: Boolean,
    emptyLine: String,
    fromDesktopCache: Boolean = false,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .background(tokens.color.surface.copy(alpha = if (fromDesktopCache) 0.25f else 0.4f))
            .verticalScroll(rememberScrollState()),
    ) {
        when {
            loading -> Text(
                "正在数那一格…",
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(12.dp),
            )
            files.isEmpty() -> Text(
                emptyLine,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(12.dp),
            )
            else -> files.forEach { (name, bytes) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(DqTone.ok),
                    )
                    Text(
                        name,
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        com.venera.desktop.gallery.data.formatBytes(bytes),
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        fontFamily = FontFamily.Monospace,
                        color = tokens.color.textSecondary,
                    )
                }
            }
        }
    }
}

/** 稿 `.btn.dgr`：危险动作中号钮（红描边 + 垃圾桶类图标 + 文案）。 */
@Composable
internal fun DqDangerButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    // 全仓钮归一：删壳委托 DesktopPushButton 的 danger 档（原 r10/1dp 红描边独门形退役）。
    DesktopPushButton(onClick = onClick, danger = true) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LocalChromePalette.current.danger,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontWeight = VeneraType.cardTitle.weight, maxLines = 1)
    }
}

/** 稿 `.thumb`：44×60 缩略。给 url 就真图，不给就按稿摆占位格（不假装有封面）。 */
@Composable
internal fun DqThumb(url: String, contentDescription: String? = null, modifier: Modifier = Modifier) {
    val tokens = VeneraTokens
    if (url.isNotBlank()) {
        VeneraCover(
            url = url,
            contentDescription = contentDescription,
            mask = VeneraCoverMask.Visible,
            modifier = modifier
                .size(width = 44.dp, height = 60.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .size(width = 44.dp, height = 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tokens.color.surfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("封面", fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary)
        }
    }
}

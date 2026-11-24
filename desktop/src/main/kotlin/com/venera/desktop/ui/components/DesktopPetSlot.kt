package com.venera.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.data.DesktopErrorBus
import com.venera.desktop.gallery.ui.LocalChromePalette
import com.venera.desktop.platform.desktopErrorKindOfText

/** 折叠档竖排字号（11sp 太小看不清，调大）。 */
private val PET_COMPACT_FONT = 15.sp
private val PET_COMPACT_LINE = 18.sp

/** 气泡状态点：1 = 异常（danger）、2 = 警示（accent）、0 = 不显示。 */
private fun badgeFor(cue: DesktopPetCue): Int = when {
    cue.priority >= 8 -> 1
    cue.priority == 7 -> 2
    else -> 0
}

/** 长句按 [cols] 字一列切成多列竖排，不省略任何字；列名按古书竖直朗读：右→左。 */
private fun verticalPetColumns(line: String, cols: Int): List<String> {
    if (line.isEmpty()) return emptyList()
    val per = (line.length + cols - 1) / cols
    return (0 until cols).map { c ->
        val from = c * per
        if (from >= line.length) return@map ""
        line.substring(from, minOf(from + per, line.length))
            .map { it.toString() }.joinToString("\n")
    }
}

/**
 * 侧栏角色位：薇薇剪影 + 台词气泡 + 状态点 + 免打扰开关。
 *
 * 色值全部走 `LocalChromePalette`，本文件零色值字面量。
 * 去掉全部时间节制 —— 无本地计时器、无自动回退，
 * 每次事件直接上屏，台词常驻到下一个事件；其余档位由各屏 emit。
 *
 * [compact] = 折叠档：整体缩到轨内，台词放大字号、逐字竖排。
 * 折叠态不再隐藏（覆盖 rail-pet-稿「折叠必须隐藏」）；
 * 长句破例放行——按弹性区可用高度算「每列字数 / 分几列」，整句完整显示、不截断；
 * 高窗一列长句、矮窗自动分成两三列，连短版都塞不下才整块不画，绝不留半截字。
 * 高度来自 BoxWithConstraints 的 maxHeight，窗口拉高/拉矮自动重算，无需固定阈值。
 */
@Composable
internal fun DesktopPetSlot(modifier: Modifier = Modifier, compact: Boolean = false) {
    val palette = LocalChromePalette.current
    val event by DesktopPetCueBus.current.collectAsState()
    val quiet by DesktopPetCueBus.quietMode.collectAsState()
    var pose by remember { mutableStateOf(MascotPetPose.IDLE) }
    var badge by remember { mutableStateOf(0) }
    var bubble by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf("") }
    var shortLine by remember { mutableStateOf("") }
    // 连戳计数：纯手势识别，不含任何时间窗口；出现别的事件就归零。
    var tapStreak by remember { mutableStateOf(0) }

    // 全局未捕获异常：按归档成因分类，不只当通用错误。
    LaunchedEffect(Unit) {
        var lastErrorId = -1L
        DesktopErrorBus.latest.collect { entry ->
            if (entry != null && entry.id != lastErrorId) {
                lastErrorId = entry.id
                DesktopPetCueBus.emit(petCueForError(desktopErrorKindOfText(entry.reading)))
            }
        }
    }

    // 去掉全部时间节制。台词常驻到下一个事件，
    // 不再有 90 秒无操作 / 5 分钟打盹的本地计时器，也不按 holdMs 自动清。
    LaunchedEffect(event) {
        val e = event
        if (e == null) {
            bubble = false
            pose = MascotPetPose.IDLE
            badge = 0
            return@LaunchedEffect
        }
        if (e.cue != DesktopPetCue.TAP_PET && e.cue != DesktopPetCue.TAP_AGAIN) tapStreak = 0
        line = DesktopPetLines.pick(e.cue, e.payload)
        shortLine = DesktopPetLines.short(e.cue)
        pose = petPoseFor(e.cue)
        badge = badgeFor(e.cue)
        bubble = line.isNotEmpty()
    }

    val artWidth = if (compact) 52.dp else 160.dp
    val artHeight = if (compact) 46.dp else 138.dp
    val quietDot = if (compact) 9.dp else 12.dp
    val bubbleShape = RoundedCornerShape(if (compact) 7.dp else 9.dp)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 折叠档：弹性区扣掉角色帧 = 气泡可用高度。按行高算「一列能站几字」，
        // 再看整句需要分几列、宽不宽得下。全句 → 分列 → 短版 → 不画，逐档退让。
        val compactLineDp = with(LocalDensity.current) { PET_COMPACT_LINE.toDp() }
        val perCol = ((maxHeight - artHeight - 12.dp) / compactLineDp).toInt().coerceAtLeast(1)
        val maxCols = 3
        val mainCols = if (line.isEmpty()) 0 else (line.length + perCol - 1) / perCol
        val shortCols = if (shortLine.isEmpty()) 0 else (shortLine.length + perCol - 1) / perCol
        val columns: List<String> = when {
            !bubble || line.isEmpty() -> emptyList()
            mainCols <= maxCols -> verticalPetColumns(line, mainCols)
            shortCols <= maxCols -> verticalPetColumns(shortLine, shortCols)
            else -> emptyList()
        }
        val bubbleMaxHeight = (compactLineDp * perCol + 12.dp).coerceAtMost(maxHeight - artHeight)
        val showBubble = columns.isNotEmpty()

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = (if (compact) {
                    Modifier.heightIn(max = bubbleMaxHeight)
                } else {
                    Modifier.heightIn(min = 34.dp, max = 96.dp)
                }).fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (showBubble) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = if (compact) 64.dp else 196.dp)
                            .clip(bubbleShape)
                            .background(palette.card)
                            .border(0.5.dp, palette.hairline, bubbleShape)
                            .padding(
                                horizontal = if (compact) 5.dp else 10.dp,
                                vertical = 6.dp,
                            ),
                    ) {
                        if (compact) {
                            // 古书竖直口吻：右列先读，逐列往左排。
                            Row(verticalAlignment = Alignment.Top) {
                                columns.asReversed().forEach { col ->
                                    Text(
                                        col,
                                        fontSize = PET_COMPACT_FONT,
                                        lineHeight = PET_COMPACT_LINE,
                                        color = palette.ink1,
                                        textAlign = TextAlign.Center,
                                        softWrap = false,
                                    )
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (badge != 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (badge == 1) palette.danger else palette.accent),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    line,
                                    fontSize = 12.sp,
                                    color = palette.ink1,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(width = artWidth, height = artHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        tapStreak++
                        DesktopPetCueBus.emit(
                            if (tapStreak >= 3) DesktopPetCue.TAP_AGAIN else DesktopPetCue.TAP_PET,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                DesktopMascotPet(
                    pose = pose,
                    modifier = Modifier.fillMaxSize(),
                    size = if (compact) 46.dp else 124.dp,
                )

                // 免打扰开关：静音时只放行 P8 异常；零新增文本节点，语义标签走 accessibility。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(quietDot)
                        .clip(CircleShape)
                        .background(if (quiet) palette.ink3 else palette.accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            // 关提示时自己收一句（QUIET_ON 在免打扰闸下仍放行）；
                            // 开回来就直接恢复，不啰嗦。
                            if (!quiet) DesktopPetCueBus.emit(DesktopPetCue.QUIET_ON)
                            DesktopPetCueBus.toggleQuietMode()
                        }
                        .semantics {
                            contentDescription = if (quiet) "开启角色提示" else "关闭角色提示"
                        },
                )
            }
        }
    }
}

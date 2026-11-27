package com.venera.desktop.sourcemanage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.DesktopSegmented
import com.venera.desktop.ui.components.DesktopGlassCard
import com.venera.desktop.ui.components.DesktopGlassCardLevel
import com.venera.desktop.ui.theme.VeneraType
import com.venera.engineprobe.DesktopSourceUpdate.SourceRepo

/** 源仓库控制条状态模型。 */
internal data class DesktopSourceRepoBarUi(
    val selectedRepo: SourceRepo,
    val summaryText: String,
    val hasUpdatables: Boolean,
    val busy: Boolean,
    val onSelectRepo: (SourceRepo) -> Unit,
    val onOpenStore: () -> Unit,
    val onCheckSync: () -> Unit,
)

/**
 * 站点墙页头下方的源仓库控制条：在官方仓库与 `laincat/vensrc` 社区巡检源间一键切换，
 * 并直达「浏览源库」与「检查同步」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceRepoBar(
    state: DesktopSourceRepoBarUi,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val isCommunity = state.selectedRepo.isCommunity
    val trackIndex = if (state.selectedRepo == SourceRepo.VENSRC_EXT) 1 else 0

    DesktopGlassCard(
        level = DesktopGlassCardLevel.Nested,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .padding(bottom = 14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "源仓库",
                    fontSize = VeneraType.aux.size,
                    lineHeight = VeneraType.aux.lineHeight,
                    color = tokens.color.textTertiary,
                )
                DesktopSegmented(
                    options = listOf("官方源", "社区源 · laincat/vensrc"),
                    selectedIndex = if (isCommunity) 1 else 0,
                    onSelect = { idx ->
                        if (state.busy) return@DesktopSegmented
                        val next = if (idx == 0) {
                            SourceRepo.OFFICIAL
                        } else if (state.selectedRepo == SourceRepo.VENSRC_EXT) {
                            SourceRepo.VENSRC_EXT
                        } else {
                            SourceRepo.VENSRC_MAIN
                        }
                        state.onSelectRepo(next)
                    },
                )
                AnimatedVisibility(
                    visible = isCommunity,
                    enter = fadeIn(VeneraMotion.tweenFast()) + expandHorizontally(VeneraMotion.tweenFast()),
                    exit = fadeOut(VeneraMotion.tweenFast()) + shrinkHorizontally(VeneraMotion.tweenFast()),
                ) {
                    DesktopSegmented(
                        options = listOf("稳定源 (25)", "扩展源 (71)"),
                        selectedIndex = trackIndex,
                        onSelect = { idx ->
                            if (state.busy) return@DesktopSegmented
                            state.onSelectRepo(if (idx == 1) SourceRepo.VENSRC_EXT else SourceRepo.VENSRC_MAIN)
                        },
                    )
                }
                RepoOriginPill(
                    text = if (isCommunity) "来自 laincat/vensrc" else "来自官方仓库",
                )
            }

            Spacer(Modifier.weight(1f, fill = false))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.summaryText.isNotBlank()) {
                    Text(
                        text = state.summaryText,
                        fontSize = VeneraType.aux.size,
                        lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                DesktopButton(
                    text = "浏览源库",
                    onClick = state.onOpenStore,
                    size = DesktopButtonSize.Compact,
                    variant = DesktopButtonVariant.Secondary,
                    enabled = !state.busy,
                    modifier = Modifier.semantics { contentDescription = "浏览在线源库" },
                )
                DesktopButton(
                    text = if (state.busy) "升级中…" else "升级已有站",
                    onClick = state.onCheckSync,
                    size = DesktopButtonSize.Compact,
                    variant = if (state.hasUpdatables) DesktopButtonVariant.Primary else DesktopButtonVariant.Secondary,
                    enabled = !state.busy,
                    loading = state.busy,
                    modifier = Modifier.semantics { contentDescription = "检查源仓库同步" },
                )
            }
        }
    }
}

@Composable
private fun RepoOriginPill(text: String) {
    val tokens = VeneraTokens
    Box(
        modifier = Modifier
            .background(tokens.color.surfaceContainerHigh.copy(alpha = 0.60f), RoundedCornerShape(999.dp))
            .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.color.textSecondary,
        )
    }
}

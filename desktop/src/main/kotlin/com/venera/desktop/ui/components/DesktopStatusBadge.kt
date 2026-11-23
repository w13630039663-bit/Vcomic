package com.venera.desktop.ui.components

import com.venera.desktop.ui.theme.VeneraType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.gallery.ui.GalleryTokens
import com.venera.desktop.platform.DesktopErrorKind
import com.venera.compose.ui.tokens.VeneraTokens

/** 偏慢/过频/登录失效那三档共用的警示黄（与源状态胶囊、设计稿同值）。 */
val DesktopWarnYellow = Color(0xFFD9A441)

/**
 * 错误态记号：图标 + 语义色 + 短句，全桌面错误态只走这一族。
 *
 * - [DesktopStatusMark]：单图标（行内/Toast/占位用），色随档位。
 * - [DesktopFailedState]：大失败位（圆座图标 + 主句 + 副句 + 重试），替代裸文本空态。
 *
 * 文案纪律照旧：主句 ≤6 字、副句 ≤15 字；原文不进屏（归档见 `DesktopErrorCopy`）。
 */
fun desktopErrorIcon(kind: DesktopErrorKind): ImageVector = when (kind) {
    DesktopErrorKind.OFFLINE -> Icons.Outlined.WifiOff
    DesktopErrorKind.TIMEOUT -> Icons.Outlined.Schedule
    // 网络被阻断（TLS 握手被重置）单独一档。图标复用 Block（与站点 403 同图标，
    // 但两档的**短句**不同：这一档念「网络受阻 · 需配置代理后重试」，站点 403 念「源访问受限」）。
    DesktopErrorKind.BLOCKED -> Icons.Outlined.Block
    DesktopErrorKind.RESTRICTED -> Icons.Outlined.Block
    DesktopErrorKind.RATE_LIMITED -> Icons.Outlined.Schedule
    DesktopErrorKind.AUTH_EXPIRED -> Icons.Outlined.PersonOff
    DesktopErrorKind.NOT_LOGGED_IN -> Icons.Outlined.PersonOff
    DesktopErrorKind.SERVER_DOWN -> Icons.Outlined.CloudOff
    DesktopErrorKind.FAILED -> Icons.Outlined.ErrorOutline
}

@Composable
fun desktopErrorColor(kind: DesktopErrorKind): Color = when (kind) {
    DesktopErrorKind.TIMEOUT, DesktopErrorKind.RATE_LIMITED, DesktopErrorKind.AUTH_EXPIRED -> DesktopWarnYellow
    DesktopErrorKind.NOT_LOGGED_IN -> Color.Transparent // 未登录 = 中性灰（占位底为透明，色由调用点给）
    else -> GalleryTokens.Color.StatusDanger
}

/** 单枚记号图标（16–20dp 线性）。[tint] 非 null 时覆盖档位色（未登录等中性档用）。 */
@Composable
fun DesktopStatusMark(kind: DesktopErrorKind, modifier: Modifier = Modifier, size: Int = 16, tint: Color? = null) {
    val tokens = VeneraTokens
    val color = tint ?: if (kind == DesktopErrorKind.NOT_LOGGED_IN) {
        tokens.color.textTertiary
    } else {
        desktopErrorColor(kind)
    }
    Icon(
        imageVector = desktopErrorIcon(kind),
        contentDescription = kind.label,
        tint = color,
        modifier = modifier.size(size.dp),
    )
}

/**
 * 大失败位：淡色圆座 + 档位图标 + 主句 + 可选副句 + 可选重试。
 * 替代「一行灰字」式空态——错误要看得见是什么档、给得出路。
 */
@Composable
fun DesktopFailedState(
    kind: DesktopErrorKind,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    // 大失败位是桌面错误态唯一的上屏组件：在这里按档位归一次类，所有走它的
    // 「取不到内容 / 无网络 / 源异常」都自动落到角色 ERROR / WARNING，各屏不用各接一遍。
    LaunchedEffect(kind, title, subtitle) {
        DesktopPetCueBus.emit(petCueForError(kind))
    }
    val tokens = VeneraTokens
    val color = if (kind == DesktopErrorKind.NOT_LOGGED_IN) tokens.color.textTertiary else desktopErrorColor(kind)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            DesktopStatusMark(kind = kind, size = 19)
        }
        Text(
            text = title,
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (onRetry != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(GalleryTokens.Shape.Small)
                    .background(color.copy(alpha = 0.14f))
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "重试",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = color,
                )
            }
        }
    }
}

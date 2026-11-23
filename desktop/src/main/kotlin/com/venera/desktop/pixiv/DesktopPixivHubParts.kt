package com.venera.desktop.pixiv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.ui.theme.VeneraType

/**
 * pixiv 融合页（`docs/designs/pixiv-fuse-2026-11-09/`）的三件栏内件：
 * 左栏身份、右栏作品面板、取数失败态。都只吃形参（`DesktopUiIsolationTest` 那条口径）。
 */

/**
 * 左栏身份。
 *
 * 已登录 = 身份卡（头像 / 名 / @handle / 统计）+ 段位列表 + 退出登录 + 画质；
 * 未登录 = 登录引导卡（三条真路仍在登录页，这里只做入口与说明 —— 不摆点不动的假段）。
 * 后三段（关注动态 / 我的收藏 / 已关注）仅登录可见。
 */
@Composable
internal fun PixivIdentityRail(
    signed: Boolean,
    displayName: String,
    handle: String?,
    avatarUrl: String?,
    stats: List<Pair<String, String>>,
    tab: Int,
    onTabChanged: (Int) -> Unit,
    onOpenLogin: () -> Unit,
    onSignOut: () -> Unit,
    onOpenQuality: (() -> Unit)?,
    /** 连接状态那一句（未登录 / 正在取… / 已连接 / 未连接）。 */
    feedStateLabel: String,
    /** 最近一次取数成功的时刻（HH:mm；`null` = 本会话还没成功过）。 */
    lastRefreshLabel: String?,
    onRefresh: () -> Unit,
) {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier
            .width(262.dp)
            .fillMaxHeight()
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!signed) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.color.surfaceContainerHigh)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "登录 pixiv",
                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.color.textPrimary,
                )
                Text(
                    text = "关注动态、收藏、关注列表都存在账号里",
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = tokens.color.textTertiary,
                    textAlign = TextAlign.Center,
                )
                DesktopPushButton(onClick = onOpenLogin) { Text("登录 pixiv") }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(tokens.color.surfaceContainerHigh),
                ) {
                    if (!avatarUrl.isNullOrBlank()) {
                        VeneraCover(
                            url = avatarUrl,
                            contentDescription = displayName,
                            mask = VeneraCoverMask.Visible,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = displayName.take(1),
                                fontSize = VeneraType.pageTitle.size,
                                color = tokens.color.textSecondary,
                            )
                        }
                    }
                }
                Text(
                    text = displayName,
                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                    fontWeight = FontWeight.Bold,
                    color = tokens.color.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (handle != null) {
                    Text(
                        text = "@$handle",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (stats.isNotEmpty()) {
                    // 统计四等分：等宽列 + 发丝分隔，数字档提到 14/700（原来挤成一串）
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        stats.forEachIndexed { index, pair ->
                            val label = pair.first
                            val value = pair.second
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .width(0.5.dp)
                                        .height(22.dp)
                                        .background(tokens.color.outlineVariant.copy(alpha = 0.5f)),
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = value,
                                    fontSize = 14.sp, lineHeight = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.color.textPrimary,
                                )
                                Text(
                                    text = label,
                                    fontSize = 10.sp, lineHeight = 12.sp,
                                    color = tokens.color.textTertiary,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(tokens.spacing.space3))

        // 段位列表（与中栏分段联动；后三段仅登录可见）
        listOf("我的作品", "关注动态", "我的收藏", "已关注").forEachIndexed { index, label ->
            if (signed || index == 0) {
                val selected = index == tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (selected) tokens.color.primary.copy(alpha = 0.14f)
                            else Color.Transparent,
                        )
                        .clickable { onTabChanged(index) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 选中档：左侧 2dp 竖条（mac 侧栏那种窄指示器，不做整块实底）
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (selected) tokens.color.primary else Color.Transparent),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        fontSize = 12.5.sp, lineHeight = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) tokens.color.primary else tokens.color.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── 底部状态卡：撤掉右栏选中面板后，这里放真实读数，不留空白 ──
        // 连接状态 + 最近刷新时刻 + 刷新；画质紧随其后，退出登录收成文字链接。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(tokens.color.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when (feedStateLabel) {
                                "已连接" -> Color(0xFF34C759)
                                "未连接" -> Color(0xFFFF9F0A)
                                "未登录" -> Color(0xFF8E8E93)
                                else -> Color(0xFF8E8E93)
                            },
                        ),
                )
                Text(
                    text = feedStateLabel,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = FontWeight.Medium,
                    color = tokens.color.textPrimary,
                )
                Spacer(Modifier.weight(1f))
                if (lastRefreshLabel != null) {
                    Text(
                        text = "$lastRefreshLabel 刷新",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DesktopPushButton(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("刷新") }
                if (onOpenQuality != null) {
                    DesktopPushButton(onClick = onOpenQuality, modifier = Modifier.weight(1f)) { Text("画质") }
                }
            }
        }
        if (signed) {
            Text(
                text = "退出登录",
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSignOut)
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 布局切换器（「对齐瀑布流或者等宽，不是可以选择的吗」+ 顶栏那枚切换器要回来）。
 *
 * 两档 = 等宽网格 / 瀑布流，写的是**同一颗** `galleryPrefs.wallLayout`（主墙、收藏、搜索共用），
 * 不新造第二份状态 —— 在这里切完，主墙那面也一起变。
 */
@Composable
internal fun PixivLayoutSwitch(
    current: com.venera.compose.gallery.domain.GalleryWallLayout,
    onPick: (com.venera.compose.gallery.domain.GalleryWallLayout) -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(tokens.color.surfaceContainerHigh)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            com.venera.compose.gallery.domain.GalleryWallLayout.Grid to "等宽",
            com.venera.compose.gallery.domain.GalleryWallLayout.Staggered to "瀑布流",
        ).forEach { (layout, label) ->
            val selected = layout == current
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(5.5.dp))
                    .background(if (selected) tokens.color.surface else Color.Transparent)
                    .clickable(enabled = !selected) { onPick(layout) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) tokens.color.textPrimary else tokens.color.textTertiary,
                )
            }
        }
    }
}

/** 取数失败态：无网 / 连接超时 / TLS 被重置都念成因，并给一枚「再试」。 */
@Composable
internal fun PixivFeedFailure(note: String, onRetry: () -> Unit) {
    val tokens = VeneraTokens
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = note,
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            DesktopPushButton(onClick = onRetry) { Text("再试") }
        }
    }
}

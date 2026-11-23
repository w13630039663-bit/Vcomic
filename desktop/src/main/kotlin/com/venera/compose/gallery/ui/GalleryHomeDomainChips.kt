package com.venera.compose.gallery.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import com.venera.compose.components.venera.veneraFaceSheen
import com.venera.compose.components.venera.veneraGlassSurface
import com.venera.compose.gallery.domain.GalleryHomeDomain
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.CornerRadius
import com.venera.compose.components.venera.PILL_TINT_BOTTOM
import com.venera.compose.components.venera.PILL_TINT_TOP
import com.venera.compose.ui.tokens.VeneraGlassRole
import com.venera.compose.ui.tokens.veneraGlassEnabled
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

private const val TRACK_SHEEN_LIFT = 0.35f

/**
 * 内容域切换分段器（图库站 / Pixiv）——从 `GalleryPixivHome.kt` 搬出来的。
 *
 * ## 为什么单独一颗文件
 *
 * `GalleryPixivHome.kt` 是 A+B 双类成因（`android.*` + 依赖一堆被排除的同包兄弟），
 * 整颗在桌面编译面外。而这一颗分段器自己**零禁引**，只有它俩的消费方
 * （`GalleryHomeChrome` 与 pixiv 首页）需要它 —— 把常量搬进独立件之后，
 * 那条被它拖成「依赖被排除兄弟」的链就断了。这与把 `TEXT_LINE_HEIGHT`
 * 搬出 pixiv 脏件是同一手法（本仓记过：可复制手法，逐个排）。
 *
 * 搬的时候**函数体一字未改**（只补了 import），所以屏上观感与搬之前逐字相同。
 */
/**
 * 内容域切换：**分段器**。
 *
 * ## 为什么是这一版（改下拉 ⇒ 本轮改回）
 *
 * 下拉那一版把左侧收到约 88dp（"图库站"那一档最宽），腾出来的宽度全给了搜索条 —— 宽度账是对的，
 * 屏上读起来却不对：一颗只写当前源的胶囊**看不出"还有另一个源"**，要戳一下才知道能换；
 * 而这一行左边的任务恰恰是"我在看哪一站、还能切到哪一站"。分段器把两档同时摆出来，一眼扫到。
 *
 * 用户原话：「站源选择器还是改回前面版本的分段切换器吧，这样好看点」。
 * 所以本体是之前那一版**原样**：`surfaceVariant` 轨道 + `primaryContainer` 滑动胶囊
 * （弹簧缓动，不是硬切），文字选中时换 `onPrimaryContainer` + semibold。
 *
 * ## 材质：跟着**全局材质轴**走，不是写死玻璃
 *
 * 用户原话：「搜索框和源切换器都要做成我们已经有的液态玻璃材质，但是要在全局切换材质保留
 * 切换原来的 md3 样式」。这一条不因形态改回分段器而失效 ——
 * - `veneraGlassEnabled()` 真 ⇒ 轨道透底 + `veneraGlassSurface(CHIP, …)` 采氛围层做真模糊；
 * - 假（= `SurfaceMaterial.SOLID`，也是默认档） ⇒ `surfaceVariant` 实色轨道，就是前面那一版的样子。
 *
 * ⚠️ 这里没有 miuix `Surface`，因此不存在"玻璃档投影透出来"那一条（它只挂在 `Surface` 上）。
 *
 * ## 宽度
 *
 * 外框**不铺满、不居中**：它住在搜索条左边那一格里，整排按字长收（每格吃 `space6` 横向内衬）。
 * 高度仍吃 `segmentedHeight`（48dp），与搜索条同档 —— 那句
 * 「搜索框缩成和切换源一样大小」是这一行唯一的对齐契约。
 *
 * ## 胶囊的位置：**量宽 + 前缀和**，不走父子坐标换算
 *
 * ⚠️ 从前每格报的是
 * `coords.parentCoordinates?.localBoundingBoxOf(coords)` —— 把子的框换算到**父坐标**再画。
 * 那条路在真机上失效过一次：选中态已经切到右格（字的颜色换了档），底色却停在左格
 * （判据 KDoc 记在 `UiAuditG3Test` 那一笔，真机截图）。
 * 换算链上任何一环拿不到真值（`parentCoordinates` 为空、父节点不是你以为的那一层），
 * 落地的都是**上一帧的矩形**，而胶囊的四个动画值只认当期目标 ⇒ 屏上读作"胶囊不跟手"。
 *
 * 现在改成：每格只报**自己的尺寸**（`onSizeChanged`，量的是节点自身，不牵涉任何人），
 * 胶囊左缘 = **前面各格宽度之和**。格子是 `Row` 里依次摆的，这条恒等式在 Row 自己的
 * 坐标系里成立，整条路上没有"换算"这一步 —— 少一环就少一个能错的地方。
 * 纵坐标恒 0：这一排每格都吃同一档 `.height(segmentedHeight - space1 * 2)`，
 * `Row` 的交叉轴报高就是它，胶囊贴着 Row 顶边画即是贴着格子画。
 */
@Composable
internal fun GalleryHomeDomainChips(
    selected: GalleryHomeDomain,
    onPick: (GalleryHomeDomain) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    // 条目表取**伴生里那一份实例**（页面上这一排只有这一处消费，别每次组合新建一个 List）。
    val entries = GalleryHomeDomain.HOME_OPTIONS
    val pill = RoundedCornerShape(percent = 50)
    // 组合期读取颜色供 drawBehind 使用；选中胶囊向 primary 插值以增强对比，不改动全局选中槽位。
    val pillFill = tokens.color.primaryContainer
    val pillTop = lerp(pillFill, tokens.color.primary, PILL_TINT_TOP)
    val pillBottom = lerp(pillFill, tokens.color.primary, PILL_TINT_BOTTOM)
    // 用固定槽位记录各格 onSizeChanged 尺寸，避免 Map 混淆未测量与零宽，也避免坐标换算失真导致胶囊错位。
    val sizes = remember(entries) { mutableStateListOf(*Array(entries.size) { IntSize.Zero }) }
    val selectedIndex = entries.indexOf(selected)
    // 选中格的左缘 = 前面各格宽之和（Row 里依次摆 ⇒ 恒等式）；宽与高 = 选中格自己那一格。
    // 纵坐标恒 0（每格同一档定高，Row 的交叉轴报高就是它）。
    val pillLeftTarget = sizes.take(selectedIndex).fold(0f) { acc, size -> acc + size.width }
    val pillWidthTarget = sizes.getOrNull(selectedIndex)?.width?.toFloat() ?: 0f
    val pillHeightTarget = sizes.getOrNull(selectedIndex)?.height?.toFloat() ?: 0f
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    // 三个名都带 `pill` 前缀：裸名 `height` 会把 `Modifier.height` 遮掉（编译器直接报
    // "Function invocation 'height(...)' expected"），而这三个值只在绘制期读。
    val pillLeft by animateFloatAsState(pillLeftTarget, spec, label = "domainPillLeft")
    val pillWidth by animateFloatAsState(pillWidthTarget, spec, label = "domainPillWidth")
    val pillHeight by animateFloatAsState(pillHeightTarget, spec, label = "domainPillHeight")

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(pill)
                // 玻璃档轨道透底，实色档铺弱高光面材质，与浮起的搜索条形成一浮一沉的主次对比。
                .background(
                    if (veneraGlassEnabled()) Color.Transparent else tokens.color.surfaceVariant,
                )
                .veneraGlassSurface(VeneraGlassRole.CHIP, tokens.spacing.segmentedHeight / 2)
                .veneraFaceSheen(
                    topColor = lerp(tokens.color.surfaceVariant, tokens.color.surface, TRACK_SHEEN_LIFT),
                    bottomColor = tokens.color.surfaceVariant,
                    // 比搜索条低一档：轨道是背景层，它的高光只负责"这里也是一块面"，
                    // 不负责"这里是我要看的东西"。两个数字挨在一起写死会让"改一个忘了另一个"。
                    highlightAlpha = 0.55f,
                )
                .padding(tokens.spacing.space1),
        ) {
            // `drawBehind` 挂在**这一层**（Row）：每格报上来的宽度与底色共用同一套坐标
            // （都在 Row 自己的空间里，差一圈内衬就会整体偏掉）。
            Row(
                modifier = Modifier
                    .clip(pill)
                    .drawBehind {
                        // 第一帧还没量 ⇒ 宽 0 ⇒ 什么都不画（画一枚贴在左上角的 0 宽圆角矩形是噪声）。
                        if (pillWidth > 0f) {
                            drawRoundRect(
                                // 胶囊渐变端点随 size.height 动态变化，弹簧动画期间尺寸每帧改变也不会错位。
                                brush = Brush.verticalGradient(listOf(pillTop, pillBottom)),
                                // 纵坐标恒 0：每格同一档定高，Row 的交叉轴报高就是它。
                                topLeft = Offset(pillLeft, 0f),
                                size = Size(pillWidth, pillHeight),
                                cornerRadius = CornerRadius(pillHeight / 2f),
                            )
                        }
                    },
            ) {
                entries.forEachIndexed { index, item ->
                    val isOn = item == selected
                    Box(
                        modifier = Modifier
                            .height(tokens.spacing.segmentedHeight - tokens.spacing.space1 * 2)
                            // 只报**自己的尺寸**（不进任何父子坐标换算，见头注「胶囊的位置」）
                            .onSizeChanged { sizes[index] = it }
                            .clip(pill)
                            .clickable { onPick(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.label,
                            fontSize = tokens.type.itemTitle,
                            fontWeight = if (isOn) tokens.type.weightSemibold else tokens.type.weightRegular,
                            // 未选中项使用 textSecondary，避免比选中态更艳造成当前站误读。
                            color = if (isOn) tokens.color.onPrimaryContainer else tokens.color.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.padding(horizontal = tokens.spacing.space6),
                        )
                    }
                }
            }
        }
    }
}

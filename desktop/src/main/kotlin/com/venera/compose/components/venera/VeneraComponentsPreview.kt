package com.venera.compose.components.venera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.venera.compose.components.VeneraAmbientBackground
import com.venera.compose.data.platform.AppearanceStyle
import com.venera.compose.data.platform.SurfaceMaterial
import com.venera.compose.ui.tokens.StatusColors
import com.venera.compose.ui.tokens.VeneraPreviewTheme
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

/**
 * 组件层统一 Preview 矩阵。
 *
 * 覆盖：**三轴里的两轴** × 明暗 = 8 张 —— {MIUIX, MD3} × {SOLID, LIQUID_GLASS} × {Light, Dark}。
 * 每个组合展示关键状态：normal / selected / disabled / loading。
 *
 * 关于 error 状态：VeneraCover 的错误态需要真实网络失败才能触发，
 * 静态 Preview 无法构造，故不展示（已在组件内通过 onError 处理）。
 */

@Composable
private fun ChipRow() {
    val tokens = VeneraTokens
    Column(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space6),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
    ) {
        // normal
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "添加条件", leadingIcon = Icons.Outlined.Add)
            VeneraChip(text = "已选条件", trailingIcon = Icons.Outlined.Close, selected = true)
            VeneraTagChip(text = "同人")
        }
        // selected
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "已选中", selected = true)
            VeneraTagChip(text = "选中标签", selected = true)
        }
        // disabled
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "不可用", enabled = false)
            VeneraTagChip(text = "禁用标签", enabled = false)
        }
        // with leading icon
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "搜索", leadingIcon = Icons.Outlined.Search, selected = true)
        }
        // 纯展示（onClick = null）：无按压反馈，用于卡片上的只读标签
        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing)) {
            VeneraChip(text = "只读", onClick = null)
            VeneraTagChip(text = "只读标签", onClick = null)
        }
    }
}

// 静态 Preview 仅覆盖 selected/disabled 态；pressed/focused 依赖运行时输入事件，需真机验证。

@Composable
private fun CardAndCoverRow() {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space6),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
    ) {
        // 正常卡片：封面 + SourceBadge 覆盖层
        Row(Modifier.width(tokens.spacing.comicCardMinWidth)) {
            VeneraCard {
                VeneraCover(url = "", contentDescription = "空 url 显示占位") {
                    VeneraSourceBadge(name = "Picacg")
                }
                Spacer(Modifier.height(tokens.spacing.cardCoverGap))
                Text(
                    text = "卡片标题（两行省略示例）",
                    fontSize = tokens.type.caption,
                    color = tokens.color.textPrimary,
                )
            }
        }
        // 加载态卡片：骨架
        Row(Modifier.width(tokens.spacing.comicCardMinWidth)) {
            VeneraCard {
                VeneraCoverShimmer()
                Spacer(Modifier.height(tokens.spacing.cardCoverGap))
                VeneraShimmer(Modifier.fillMaxWidth().height(tokens.spacing.barHeight))
            }
        }
    }
}

@Composable
private fun ComponentMatrix() {
    // 必须套 VeneraAmbientBackground：它就是全站玻璃唯一采样源的**安装点**。
    // 不套它，LIQUID_GLASS 档的预览永远只会走"没有录制层 ⇒ no-op"那条分支，
    // 于是"两档看起来一样"会被误读成"玻璃没生效"——其实只是没人装采样层。
    VeneraAmbientBackground {
        Column(Modifier.fillMaxWidth()) {
            ChipRow()
            CardAndCoverRow()
        }
    }
}

// 静态 Preview 仅验证布局度量与不崩溃；RuntimeShader 模糊与深色氛围光强度需真机验证。

@Preview(name = "Components MIUIX Light", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixLight() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = false) { ComponentMatrix() }
}

@Preview(name = "Components MIUIX Dark", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixDark() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = true) { ComponentMatrix() }
}

@Preview(name = "Components MD3 Light", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3Light() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = false) { ComponentMatrix() }
}

@Preview(name = "Components MD3 Dark", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3Dark() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = true) { ComponentMatrix() }
}

@Preview(name = "Components MIUIX Light · 液态玻璃", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixLightGlass() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = false, material = SurfaceMaterial.LIQUID_GLASS) {
        ComponentMatrix()
    }
}

@Preview(name = "Components MIUIX Dark · 液态玻璃", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMiuixDarkGlass() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = true, material = SurfaceMaterial.LIQUID_GLASS) {
        ComponentMatrix()
    }
}

@Preview(name = "Components MD3 Light · 液态玻璃", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3LightGlass() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = false, material = SurfaceMaterial.LIQUID_GLASS) {
        ComponentMatrix()
    }
}

@Preview(name = "Components MD3 Dark · 液态玻璃", showBackground = true, widthDp = 400)
@Composable
private fun VeneraComponentsMd3DarkGlass() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = true, material = SurfaceMaterial.LIQUID_GLASS) {
        ComponentMatrix()
    }
}

/**
 * 顶栏圆座矩阵：[provideSource] 决定 `LocalTopBarBackdrop` 给不给，等价于真机上
 * 「这一屏接没接 `rememberTopBarBackdrop()`」——追更页那类自绘 chrome 就是不给的那档。
 *
 * 每颗都挂到真实配方上：普通（textPrimary 图标）/ 低频（textSecondary）/ 高亮（primary）/
 * 禁用 / 带角标。角标由**调用点**用 Box 叠，不在圆座内部 —— 与首页、本地书架两处一致。
 */
@Composable
private fun TopBarPillMatrix(provideSource: Boolean) {
    val tokens = VeneraTokens
    val source = if (provideSource) rememberTopBarBackdrop() else null
    VeneraAmbientBackground(modifier = Modifier.blurBackdropSource(source)) {
        CompositionLocalProvider(LocalTopBarBackdrop provides source) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(tokens.spacing.space6),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.chipSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VeneraTopBarPill(onClick = {}) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = tokens.color.textPrimary,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
                VeneraTopBarPill(onClick = {}) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "低频动作",
                        tint = tokens.color.textSecondary,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
                VeneraTopBarPill(onClick = {}, enabled = false) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "禁用态",
                        tint = tokens.color.textDisabled,
                        modifier = Modifier.size(tokens.spacing.chipIconSize),
                    )
                }
                // 高亮态 + 角标：与"下载中心/源更新"那两处同构
                Box {
                    VeneraTopBarPill(onClick = {}) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "高亮态",
                            tint = tokens.color.primary,
                            modifier = Modifier.size(tokens.spacing.chipIconSize),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(tokens.spacing.space1)
                            .clip(CircleShape)
                            .background(StatusColors.Degraded),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "12",
                            fontSize = tokens.type.badge,
                            color = StatusColors.OnBadgeSurface,
                            modifier = Modifier.padding(horizontal = tokens.spacing.badgeHorizontalPadding),
                        )
                    }
                }
            }
        }
    }
}

// 静态 Preview 仅验证圆座几何、描边与回落底板；磨砂与按压缩放需真机验证。

@Preview(name = "顶栏圆座 MIUIX Light · 有采样层", showBackground = true, widthDp = 400)
@Composable
private fun TopBarPillMiuixLightWithSource() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = false) { TopBarPillMatrix(provideSource = true) }
}

@Preview(name = "顶栏圆座 MIUIX Light · 无采样层(回落)", showBackground = true, widthDp = 400)
@Composable
private fun TopBarPillMiuixLightFallback() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = false) { TopBarPillMatrix(provideSource = false) }
}

@Preview(name = "顶栏圆座 MIUIX Dark · 有采样层", showBackground = true, widthDp = 400)
@Composable
private fun TopBarPillMiuixDarkWithSource() {
    VeneraPreviewTheme(AppearanceStyle.MIUIX, dark = true) { TopBarPillMatrix(provideSource = true) }
}

@Preview(name = "顶栏圆座 MD3 Dark · 无采样层(回落)", showBackground = true, widthDp = 400)
@Composable
private fun TopBarPillMd3DarkFallback() {
    VeneraPreviewTheme(AppearanceStyle.MD3, dark = true) { TopBarPillMatrix(provideSource = false) }
}

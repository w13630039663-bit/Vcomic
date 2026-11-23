package com.venera.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.venera.compose.ui.tokens.LocalVeneraDarkTheme
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.vShadow

/**
 * 卡片悬停浮动的绘制态快照。
 *
 * 五项立体效果的**绘制层**共用这一份读数：只驱动 graphicsLayer / offset 与绘制，
 * 不参与任何测量——卡片的信息栏、动作槽高度全靠自己原来的布局定，悬停不会让格子重排。
 *
 * @param isHovered 指针在卡内且卡片可用（不可用卡不响应）。
 * @param percentX 指针横坐标归一化到 `-1..1`（0 = 卡中线）。
 * @param percentY 指针纵坐标归一化到 `-1..1`（0 = 卡中线）。
 * @param uiOffsetX 前景（角标 / 胶囊 / 动作坞）横向微移，与封面反相，制造三层景深。
 * @param uiOffsetY 前景纵向微移。
 * @param coverScale 封面缩放（常态即略有放大，悬停再推一档，避免边缘露底）。
 * @param coverPanX 封面横向视差位移。
 * @param coverPanY 封面纵向视差位移。
 */
@Immutable
data class DesktopCardFloatState(
    val isHovered: Boolean = false,
    val percentX: Float = 0f,
    val percentY: Float = 0f,
    val uiOffsetX: Dp = 0.dp,
    val uiOffsetY: Dp = 0.dp,
    val coverScale: Float = 1f,
    val coverPanX: Dp = 0.dp,
    val coverPanY: Dp = 0.dp,
)

/** 封面层：只走 graphicsLayer 的缩放与视差，不改测量。 */
fun Modifier.desktopCoverFloat(state: DesktopCardFloatState): Modifier = this.graphicsLayer {
    scaleX = state.coverScale
    scaleY = state.coverScale
    translationX = state.coverPanX.toPx()
    translationY = state.coverPanY.toPx()
}

/** 前景层：角标、胶囊、动作坞与封面反向微移，只走 offset。 */
fun Modifier.desktopUiFloat(state: DesktopCardFloatState): Modifier =
    this.offset(x = state.uiOffsetX, y = state.uiOffsetY)

/**
 * 漫画/画廊卡共用的浮动卡框：把画廊卡 v2 的五项立体效果收成一处，各屏只喂自己的内容。
 *
 * 与 `DesktopGalleryCard` 同语言：
 * 1. **环境弥散光**：卡片下沿外铺一层自绘渐变（暗色不画 Skia 投影，纵深靠它表达）；
 * 2. **指针追光边框**：悬停时描边改成以指针为圆心的径向渐变，近鼠侧点亮、远端回落；
 * 3. **受光渐变底托 + 顶边高光刃**：底托按卡片自身底色提亮/压暗，顶沿画 70% 宽的 1px 高光；
 * 4. **三层视差**：整卡倾斜缩放，封面反向位移放大，前景角标同向微移；
 * 5. 悬停态通过 [DesktopCardFloatState.isHovered] 交回给调用点，信息栏站标据此唤醒。
 *
 * 全部改动只落在绘制层：`cornerRadius` / `chromeAspect` 都不参与测量。
 *
 * @param cornerRadius 卡片外圆角；描边、底托、裁切共用同一档。
 * @param surface 卡片本体底色，底托渐变以它为基准（不是写死的白/黑）。
 * @param modifier 外部布局修饰符（宽度、比例、点击都在这里，接在最外层）。
 * @param enabled 卡片是否可交互；不可用时只画静态面，不响应悬停。
 * @param tray 是否铺受光渐变底托（封面直出、无卡面的卡传 false）。
 * @param border 是否画描边与指针追光。
 * @param specular 是否画顶边高光刃。
 * @param liftDp 悬停抬升量。
 * @param restElevation 常态投影（暗色档由 vShadow 统一不画）。
 * @param hoverElevation 悬停投影。
 * @param chromeAspect 描边 / 高光刃 / 追光只画在卡面上半区时传封面比例（宽/高）；
 *   null = 画满整卡。字与卡面分离的封面直出形（收藏、首页浅皮）用它。
 * @param selected 选中态：描边换纯色加粗。
 * @param selectedColor 选中态描边色。
 * @param content 卡片内容；形参给出本帧的浮动读数，封面与前景各自接 [desktopCoverFloat] /
 *   [desktopUiFloat]。
 */
@Composable
fun DesktopFloatingCard(
    cornerRadius: Dp,
    surface: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tray: Boolean = true,
    border: Boolean = true,
    specular: Boolean = true,
    liftDp: Dp = 6.dp,
    restElevation: Dp = 0.dp,
    hoverElevation: Dp = 14.dp,
    chromeAspect: Float? = null,
    selected: Boolean = false,
    selectedColor: Color? = null,
    content: @Composable BoxScope.(DesktopCardFloatState) -> Unit,
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val isDark = LocalVeneraDarkTheme.current
    val tokens = VeneraTokens
    val primary = tokens.color.primary

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var pointerInside by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf<Offset?>(null) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val active = enabled && (hovered || pointerInside)
    val calmSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val calmSpringDp = spring<Dp>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val panSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    val percentX = remember(pointer, cardSize) {
        val pos = pointer ?: return@remember 0f
        if (cardSize.width == 0) 0f
        else ((pos.x - cardSize.width / 2f) / (cardSize.width / 2f)).coerceIn(-1f, 1f)
    }
    val percentY = remember(pointer, cardSize) {
        val pos = pointer ?: return@remember 0f
        if (cardSize.height == 0) 0f
        else ((pos.y - cardSize.height / 2f) / (cardSize.height / 2f)).coerceIn(-1f, 1f)
    }

    val animRotX by animateFloatAsState(
        targetValue = if (active && !pressed) percentY * -5f else 0f,
        animationSpec = calmSpring,
        label = "floatTiltX",
    )
    val animRotY by animateFloatAsState(
        targetValue = if (active && !pressed) percentX * 5f else 0f,
        animationSpec = calmSpring,
        label = "floatTiltY",
    )
    val animScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else if (active) 1.018f else 1f,
        animationSpec = calmSpring,
        label = "floatScale",
    )
    val liftOffset by animateDpAsState(
        targetValue = if (active) -liftDp else 0.dp,
        animationSpec = calmSpringDp,
        label = "floatLift",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (active) hoverElevation else restElevation,
        animationSpec = tween(durationMillis = 200),
        label = "floatShadow",
    )
    val ambientAlpha by animateFloatAsState(
        targetValue = if (active) 0.34f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "floatAmbient",
    )
    val glintAlpha by animateFloatAsState(
        targetValue = if (active && pointer != null) (if (isDark) 0.16f else 0.26f) else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "floatGlint",
    )
    val trayTop by animateColorAsState(
        targetValue = if (isDark) {
            lerp(surface, Color.White, if (active) 0.115f else 0.08f)
        } else {
            lerp(surface, Color.White, if (active) 0.10f else 0.02f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "floatTrayTop",
    )
    val trayBottom by animateColorAsState(
        targetValue = if (isDark) {
            lerp(surface, Color.Black, if (active) 0.26f else 0.32f)
        } else {
            lerp(lerp(surface, Color(0xFFEFF3F9), 0.85f), Color.White, if (active) 0.40f else 0f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "floatTrayBottom",
    )

    val panYPercent = remember(pointer, cardSize) {
        val pos = pointer ?: return@remember 0.5f
        val coverH = if (cardSize.height > 0) {
            if (chromeAspect != null && cardSize.width > 0) cardSize.width / chromeAspect else cardSize.height * 0.72f
        } else 220f
        (pos.y / coverH.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    val panXPercent = remember(pointer, cardSize) {
        val pos = pointer ?: return@remember 0.5f
        if (cardSize.width == 0) 0.5f else (pos.x / cardSize.width.toFloat()).coerceIn(0f, 1f)
    }
    val animPanOffsetX by animateDpAsState(
        targetValue = if (active) ((0.5f - panXPercent) * 11f).dp else 0.dp,
        animationSpec = panSpec,
        label = "floatPanX",
    )
    val animPanOffsetY by animateDpAsState(
        targetValue = if (active) ((0.5f - panYPercent) * 11f).dp else 0.dp,
        animationSpec = panSpec,
        label = "floatPanY",
    )
    val animUiOffsetX by animateDpAsState(
        targetValue = if (active) (percentX * 2.8f).dp else 0.dp,
        animationSpec = panSpec,
        label = "floatUiX",
    )
    val animUiOffsetY by animateDpAsState(
        targetValue = if (active) (percentY * 2.8f).dp else 0.dp,
        animationSpec = panSpec,
        label = "floatUiY",
    )
    val animCoverScale by animateFloatAsState(
        targetValue = if (active) 1.09f else 1.03f,
        animationSpec = calmSpring,
        label = "floatCoverScale",
    )

    val baseTop = if (isDark) Color(0x33FFFFFF) else Color(0x2417202E)
    val borderBrush = when {
        selected && selectedColor != null -> SolidColor(selectedColor)
        active && pointer != null -> {
            val localSpan = with(androidx.compose.ui.platform.LocalDensity.current) { 210.dp.toPx() }
            Brush.radialGradient(
                colors = listOf(
                    if (isDark) Color(0x85FFFFFF) else primary.copy(alpha = 0.48f),
                    if (isDark) Color(0x1EFFFFFF) else Color(0x1C17202E),
                    baseTop,
                ),
                center = pointer!!,
                radius = localSpan,
            )
        }
        else -> Brush.verticalGradient(
            0f to baseTop,
            0.5f to if (isDark) Color(0x14FFFFFF) else Color(0x1C17202E),
            1f to if (isDark) Color(0x08FFFFFF) else Color(0x1C17202E).copy(alpha = 0.08f),
        )
    }
    val ambientColor = if (isDark) primary.copy(alpha = 0.28f) else Color(0xFF17202E).copy(alpha = 0.20f)
    val chromeShape = shape

    val state = DesktopCardFloatState(
        isHovered = active,
        percentX = percentX,
        percentY = percentY,
        uiOffsetX = animUiOffsetX,
        uiOffsetY = animUiOffsetY,
        coverScale = animCoverScale,
        coverPanX = animPanOffsetX,
        coverPanY = animPanOffsetY,
    )

    Box(
        modifier = Modifier
            .zIndex(if (active) 1f else 0f)
            .then(modifier)
            .drawBehind {
                if (ambientAlpha > 0.001f) {
                    val w = size.width
                    val h = if (chromeAspect != null) w / chromeAspect else size.height
                    val bleed = 16.dp.toPx()
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, ambientColor),
                            startY = h * 0.45f,
                            endY = h + bleed,
                        ),
                        topLeft = Offset(w * 0.07f, h * 0.4f),
                        size = Size(w * 0.86f, h * 0.6f + bleed),
                        cornerRadius = CornerRadius((cornerRadius + 12.dp).toPx()),
                        alpha = ambientAlpha,
                    )
                }
            }
            .onGloballyPositioned { cardSize = it.size }
            .graphicsLayer {
                scaleX = animScale
                scaleY = animScale
                rotationX = animRotX
                rotationY = animRotY
                cameraDistance = 14f * density
                // 抬升走绘制层而不是布局层：`offset` 会把命中区一起挪走，
                // 指针贴着卡边时「抬起 → 指针出卡 → 落下 → 指针又进卡」会自我循环，
                // 悬停永远收敛不了（测试里就是 waitForIdle 超时）。
                translationY = liftOffset.toPx()
            }
            .vShadow(elevation = shadowElevation, shape = shape)
            .clip(shape)
            .then(
                if (tray) {
                    Modifier.background(Brush.verticalGradient(listOf(trayTop, trayBottom)))
                } else {
                    Modifier
                },
            )
            .hoverable(interactionSource = interaction)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> {
                                event.changes.firstOrNull()?.position?.let {
                                    pointer = it
                                    pointerInside = true
                                }
                            }
                            PointerEventType.Enter -> pointerInside = true
                            PointerEventType.Exit -> {
                                pointerInside = false
                                pointer = null
                                // 按住拖出卡外时收不到 Release，这里一并复位，免得卡缩着不回弹。
                                pressed = false
                            }
                            PointerEventType.Press -> {
                                if (!event.buttons.isSecondaryPressed) pressed = true
                            }
                            PointerEventType.Release -> pressed = false
                        }
                    }
                }
            },
    ) {
        content(state)

        // 顶边受光高光刃：宽约 70%，描出卡面上沿的物理厚度。
        if (specular) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 1.dp)
                    .fillMaxWidth(0.7f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                if (isDark) Color(0x57FFFFFF) else Color(0xCCFFFFFF),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }

        // 指针处的柔光：盖在内容之上、裁在卡面之内，让受光面有真实的照明落点。
        if (glintAlpha > 0.005f && pointer != null) {
            val glare = pointer!!
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val clipH = if (chromeAspect != null) size.width / chromeAspect else size.height
                        clipRect(0f, 0f, size.width, clipH) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White.copy(alpha = glintAlpha), Color.Transparent),
                                    center = glare,
                                    radius = 220.dp.toPx(),
                                ),
                                center = glare,
                                radius = 220.dp.toPx(),
                            )
                        }
                    },
            )
        }

        // 描边在最上层：卡面（封面）齐边时画在下面会被封面盖掉。
        if (border) {
            val chromeModifier = if (chromeAspect != null) {
                Modifier.fillMaxWidth().aspectRatio(chromeAspect)
            } else {
                Modifier.matchParentSize()
            }
            Box(
                modifier = chromeModifier.border(
                    width = if (selected) 1.5.dp else 1.dp,
                    brush = borderBrush,
                    shape = chromeShape,
                ),
            )
        }
    }
}

/**
 * 信息栏站标胶囊：常态中性低饱和，卡片悬停时点亮为主题色（第五项「站标唤醒」）。
 *
 * 与 `DesktopGalleryCardInfoLayout` 里的站徽标逐项同色：底 12%/8%、框 28%/14%、
 * 字主色/次级 82%，切换只动颜色不动尺寸——胶囊悬停前后占位一致，信息栏不跳位。
 *
 * @param text 站名（空串不渲染）。
 * @param highlighted 是否处于悬停唤醒态。
 * @param modifier 外部布局修饰符。
 */
@Composable
fun DesktopCardSourcePill(
    text: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return
    val tokens = VeneraTokens
    val pillShape = RoundedCornerShape(5.dp)
    val bg by animateColorAsState(
        targetValue = if (highlighted) tokens.color.primary.copy(alpha = 0.12f)
        else tokens.color.outlineVariant.copy(alpha = 0.08f),
        animationSpec = tween(200),
        label = "sourcePillBg",
    )
    val stroke by animateColorAsState(
        targetValue = if (highlighted) tokens.color.primary.copy(alpha = 0.28f)
        else tokens.color.outlineVariant.copy(alpha = 0.14f),
        animationSpec = tween(200),
        label = "sourcePillBorder",
    )
    val ink by animateColorAsState(
        targetValue = if (highlighted) tokens.color.primary
        else tokens.color.textSecondary.copy(alpha = 0.82f),
        animationSpec = tween(200),
        label = "sourcePillText",
    )
    Box(
        modifier = modifier
            .clip(pillShape)
            .background(bg)
            .border(1.dp, stroke, pillShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

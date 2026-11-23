# Pixiv 终极进化作品卡片 · 组件规格与实现规范

> 归档路径：`desktop/docs/designs/pixiv-card-ultimate-2026-11-16/component-spec.md`  
> 日期：2026-11-16  

---

## 1. 组件分解结构

```
DesktopGalleryCard (或 Pixiv 专用特化变体)
├── Box (Card Wrapper, 3D 透视视口, 坐标与手势捕捉)
│   ├── Modifier.pointerInput (捕捉 mouse move / exit 坐标计算 percentX, percentY)
│   └── CardContainer (Column)
│       ├── Modifier.graphicsLayer (3D 倾斜 rotationX/Y + 悬停 scale + 按压 scale + 入场 spring)
│       ├── Modifier.vShadow (动态色晕与深阴影)
│       ├── Modifier.background (CardBackground: 0xFF161618)
│       ├── Modifier.border (描边状态机：常态 / 悬停 / 选中)
│       │
│       ├── CoverArea (Box, aspectRatio: 1f 或 card.aspect, clip: 顶部圆角 16dp)
│       │   ├── CoverImage (VeneraCover, scale: 1.08f, translationY: panOffset)
│       │   ├── CoverGradientScrim (Box, 底部 48dp 黑色渐隐，无缝接信息区)
│       │   ├── TagsOverlay (Row, 左上角 10dp, 悬停阶梯动画浮现 #tag 药丸)
│       │   ├── SelectionBadge (右上角多选态高亮 ✓ 胶囊)
│       │   └── HoverQuickActions (悬停快捷选卡与复制按钮坞)
│       │
│       ├── SpecularGlareLayer (Canvas / DrawModifier, 随鼠标绘制径向微反光层)
│       │
│       └── InfoArea (Column, 10dp 上下 padding, 12dp 水平 padding)
│           ├── TitleText (13.5sp SemiBold, 单行截断)
│           └── AuthorRow (Row, 垂直居中)
│               ├── Avatar (18dp 圆形头像，1dp 描边)
│               ├── SourceBadge (PIXIV 蓝底微药丸)
│               └── AuthorNameText (12sp 次级文本)
```

---

## 2. 状态机规范 (State Machine)

| 状态 | 3D 旋转 | 缩放 (Scale) | 阴影深度 | 描边色 | 标签药丸 | 长图平移 |
|---|---|---|---|---|---|---|
| **默认态 (Default)** | (0°, 0°) | 1.00f | 4dp (暗色无) | `0x0FFFFFFF` (6% 白) | 隐藏 (`alpha 0`) | 原位 (0.dp) |
| **悬停态 (Hover)** | 实时 `(-5°*y, 5°*x)` | 1.02f | 16dp + 光晕 | `0x2EFFFFFF` (18% 白) | 错落浮现 (`alpha 1`) | 依指针 Y 轴平移 ±10dp |
| **按压态 (Press)** | 保持当前角度 | 0.97f 弹性收缩 | 8dp | 当前悬停色 | 保持显示 | 保持当前平移 |
| **多选激活 (Selected)**| 遵循悬停态 | 遵循对应手势 | 遵循对应手势 | `Primary` 强调色 (1.5dp) | 遵循对应手势 | 遵循对应手势 |
| **打码态 (Masked)** | 仅微浮动 | 仅微浮动 | 默认 | 默认 | 隐藏 | 禁用漫游，展示磨砂遮罩 |

---

## 3. Compose 实现映射与技术细节

### 3.1 坐标捕获与 3D 倾角计算
```kotlin
var localMousePos by remember { mutableStateOf<Offset?>(null) }
var cardSize by remember { mutableStateOf(IntSize.Zero) }

// 计算归一化 [-1f, 1f]
val percentX = remember(localMousePos, cardSize) {
    val pos = localMousePos ?: return@remember 0f
    if (cardSize.width == 0) 0f
    else ((pos.x - cardSize.width / 2f) / (cardSize.width / 2f)).coerceIn(-1f, 1f)
}
val percentY = remember(localMousePos, cardSize) {
    val pos = localMousePos ?: return@remember 0f
    if (cardSize.height == 0) 0f
    else ((pos.y - cardSize.height / 2f) / (cardSize.height / 2f)).coerceIn(-1f, 1f)
}

val animRotX by animateFloatAsState(
    targetValue = if (isHovered) percentY * -5.0f else 0f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
)
val animRotY by animateFloatAsState(
    targetValue = if (isHovered) percentX * 5.0f else 0f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
)
val animScale by animateFloatAsState(
    targetValue = if (isPressed) 0.97f else if (isHovered) 1.02f else 1.0f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
)
```

### 3.2 封面长图上下漫游 (Pan & Scan)
```kotlin
val panYPercent = remember(localMousePos, cardSize) {
    val pos = localMousePos ?: return@remember 0.5f
    // 图片视口高度约为总卡片高度的大部
    (pos.y / (cardSize.height * 0.75f).coerceAtLeast(1f)).coerceIn(0f, 1f)
}
val animPanOffsetY by animateFloatAsState(
    targetValue = if (isHovered && !card.masked) (0.5f - panYPercent) * 16.dp.value else 0f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
)
```

### 3.3 悬停错落标签药丸 (Staggered Tags)
```kotlin
val tags = remember(card) {
    // 优先提取 card 携带的 tags，无则展示站方标签或类型
    card.sourcePost?.tags?.take(3)?.map { it.name } ?: emptyList()
}
```
每个标签药丸：
```kotlin
val tagAlpha by animateFloatAsState(
    targetValue = if (isHovered) 1f else 0f,
    animationSpec = if (isHovered) {
        tween(durationMillis = 200, delayMillis = tagIndex * 35)
    } else {
        tween(durationMillis = 100)
    },
)
val tagSlideY by animateDpAsState(
    targetValue = if (isHovered) 0.dp else (-8).dp,
    animationSpec = if (isHovered) {
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
    } else {
        tween(durationMillis = 100)
    },
)
```

### 3.4 动态光斑层 (Specular Glare)
```kotlin
Modifier.drawWithContent {
    drawContent()
    if (isHovered && localMousePos != null) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                center = localMousePos!!,
                radius = 220.dp.toPx(),
            ),
            center = localMousePos!!,
            radius = 220.dp.toPx(),
        )
    }
}
```

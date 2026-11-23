# 画廊浮动卡片 v2 — 组件规格与 Compose 映射

## 1. 组件树（在 v1 基础上增量）

```text
DesktopGalleryCard (DesktopContextMenuArea + zIndex)
├── CardAmbientGlow (drawBehind, 暗色自绘弥散光, 随悬停渐显)   ← 新增
└── Box (graphicsLayer 3D 倾角 + 抬升 + 双向视差; 追光边框; 渐变底托; 顶边高光刃)
    ├── TopSpecularEdge (Box, 1dp 水平渐变, 宽 70%, TopCenter)   ← 新增
    ├── Column(padding 5dp)
    │   └── MediaFrame (Box, aspectRatio, clip(10dp), inner ring)
    │       ├── VeneraCover (graphicsLayer: scale 1.03→1.09 + X/Y 双向 pan)  ← X 轴新增
    │       ├── TopLeftOverlay (反向 ±2.8dp 位移)                            ← 新增位移
    │       ├── FavRestBadge
    │       └── FloatingActionPopout (反向 ±2.8dp 位移, 22px 抬升)           ← 新增位移
    └── DesktopGalleryCardInfo(..., highlightSource = isCardHovered)         ← 新增传参
        └── SourcePill (常态中性 → 悬停主题色, 200ms)                        ← 新增状态
```

## 2. 交互状态矩阵

| 状态 | 底托 | 边框 | 环境光 | 封面 | 站标 |
|---|---|---|---|---|---|
| 常态 | 垂直渐变 top→bot | 固定顶光渐变 | 暗色淡隐 / 浅色静态影 | scale 1.03, 无 pan | 中性半透明 |
| 悬停 | 渐变提亮一阶 | 近鼠侧径向点亮 | 弥散光渐显 + 抬升 -7dp | scale 1.09 + X/Y 双向 pan | 主题色点亮 |
| 按压 | 同悬停 | 同悬停 | 光保持 | scale 0.97 | 保持 |
| 选中 | 渐变 + 主色边 | 主色 1.5dp | 同悬停 | 同悬停 | 同悬停 |
| 动作回执 | 保持悬停 | 保持 | 保持 | 保持 | 保持 |

## 3. 实现约束与红线

1. **暗色不画 Skia 投影**：`vShadow` 暗色短路是本仓铁律（黑楔子 bug 规避），环境纵深只用 `drawBehind` 自绘可控色弥散光实现，不改全局 `vShadow`。
2. **绘制层不进测量**：所有位移/倾斜/缩放/视差只在 `graphicsLayer`、`offset`、`drawBehind`，不得改变组件测量尺寸；22dp 尾槽继续恒占测量（跳位批结论）。
3. **公开函数 KDoc**：`DesktopGalleryCardInfo` 新增 `highlightSource` 形参须补 `@param`；默认值 `false` 保证其他调用点零改动。
4. **最小补丁**：仅改 `DesktopGalleryCard.kt` 与 `DesktopGalleryCardInfo.kt`；不碰墙布局、数据源、动作管道。
5. **判据**：`卡片尾槽恒占测量且入场不重放` 的字符串断言保持不变，本轮改动不得破坏其中 `Box(modifier = Modifier.size(22.dp))` / `rememberSaveable(cardKey)` / `Animatable(if (hasEntered.value)` 三处。

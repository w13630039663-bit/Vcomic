# Pixiv 终极进化作品卡片 · 设计规范 Token 表

> 归档路径：`desktop/docs/designs/pixiv-card-ultimate-2026-11-16/design-tokens.md`  
> 日期：2026-11-16  

---

## 1. 颜色 Token (Color Tokens)

| Token 键名 | 确定值 / 表达式 | 语义用途 |
|---|---|---|
| `CardBackground` | `Color(0xFF161618)` | 卡片容器深暗底色（高纯度抗噪背景） |
| `CardBorderHairline` | `Color(0x0FFFFFFF)` (~6% 白) | 常态发丝微光描边 |
| `CardBorderHover` | `Color(0x2EFFFFFF)` (~18% 白) | 悬停强化微光描边 |
| `CardBorderSelected` | `tokens.color.primary` (`0xFF3880FF`) | 选中多选态边框色 |
| `CardShadowDeep` | `Color(0x99000000)` | 静态深邃悬浮阴影 |
| `CoverGradientScrim` | `Brush.verticalGradient(Transparent -> 0xFF161618)` | 封面底部融底过渡遮罩（48dp） |
| `TagPillBackground` | `Color(0x8A000000)` (~54% 黑高斯) | 悬停标签药丸磨砂底色 |
| `TagPillBorder` | `Color(0x26FFFFFF)` (~15% 白) | 标签药丸细边框 |
| `TagPillText` | `Color(0xEEFFFFFF)` (~93% 白) | 标签药丸文本颜色 |
| `GlareCenter` | `Color(0x1FFFFFFF)` (~12% 白) | 鼠标跟随动态光斑中心色 |
| `TextTitle` | `tokens.color.textPrimary` (`0xFFF5F5F7`) | 卡片主标题 |
| `TextAuthor` | `tokens.color.textSecondary` (`0xFF86868B`) | 画师昵称与次级信息 |
| `BadgePixivBg` | `Color(0x1F0096FA)` (~12% 站方蓝) | PIXIV 站源角标底色 |
| `BadgePixivText` | `Color(0xFF0096FA)` | PIXIV 站源文字色 |

---

## 2. 尺寸与间距 Token (Dimension & Spacing Tokens)

| Token 键名 | 确定值 | 说明 |
|---|---|---|
| `CardRadiusOuter` | `16.dp` | 卡片外轮廓圆角（对齐 `GalleryTokens.Shape.Large`） |
| `TagRadiusInner` | `100.dp` (`CircleShape`) | 标签药丸胶囊圆角 |
| `AvatarSize` | `18.dp` | 画师圆形头像直径 |
| `InfoPaddingVertical` | `10.dp` | 信息区垂直内衬（标题与作者行） |
| `InfoPaddingHorizontal` | `12.dp` | 信息区横向内衬 |
| `TagContainerInset` | `10.dp` | 悬停标签容器距封面左/上间距 |
| `TagGap` | `6.dp` | 药丸之间的横向间距 |
| `TagPaddingHorizontal` | `8.dp` | 药丸内部文本横向 Padding |
| `TagPaddingVertical` | `3.dp` | 药丸内部文本纵向 Padding |

---

## 3. 字体排印 Token (Typography Tokens)

| 语义角色 | 字号 | 行高 | 字重 | 截断规则 |
|---|---|---|---|---|
| 作品标题 | 13.5sp | 18sp | `FontWeight.SemiBold` (600) | 单行截断，末尾省略号 (`Ellipsis`) |
| 画师昵称 | 12.0sp | 16sp | `FontWeight.Normal` (400) | 单行截断，末尾省略号 (`Ellipsis`) |
| 标签药丸 | 10.5sp | 14sp | `FontWeight.Medium` (500) | 文本紧凑居中 |
| 站源角标 | 9.0sp | 12sp | `FontWeight.Bold` (700) | 大写全拼徽标 |

---

## 4. 动效与手势 Token (Motion Tokens)

| 动效场景 | 属性与范围 | 曲线 / 规格 | 说明 |
|---|---|---|---|
| **3D 倾斜** | `rotationX`: ±5.0°<br>`rotationY`: ±5.0° | `spring(DampingRatioLowBouncy, StiffnessMedium)` | 跟随指针实时归一化坐标计算 |
| **悬停浮动** | `scale`: 1.0f → 1.02f<br>`liftY`: 0.dp → -4.dp | `spring(DampingRatioLowBouncy, StiffnessMedium)` | 移入上浮放大，移出平滑弹回 |
| **长图漫游** | `scale`: 1.08f (常态)<br>`offsetY`: ±10.dp | 惯性阻尼跟踪 (`animateFloatAsState(120ms)`) | 随指针纵向比例上下平移展示长图细节 |
| **标签浮现** | `alpha`: 0f → 1f<br>`translateY`: -8.dp → 0.dp | `delay(index * 35ms)` + `spring` 弹入 | 阶梯错落弹现，移出时 100ms 同步淡出 |
| **反光光斑** | 径向高光半透明层扩散 | 跟随鼠标偏移坐标动态绘制 | 悬停透明度 1f，离开衰减至 0f |
| **按压反馈** | `scale`: 1.02f → 0.97f | `tween(100ms)` 按下，`spring` 回弹 | 遵循桌面端按压反馈准则 |

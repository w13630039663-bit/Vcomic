# 画廊桌面端组件规格与 Compose 映射实现指南 (Component Spec)

> **版本**：2026-11-05 v3.3（设计规格审查修正版）  
> **目标**：指导在 Jetpack Compose / Compose Multiplatform (JVM Desktop) 架构下，如何高效、无冗余、类型安全地实现设计稿中的各个组件。  
> **遵循架构边界**：桌面侧零 `import android.*`，副作用统一收口于 `LocalUxPlatformServices`，单向数据流与状态提升。

---

## 一、 顶栏桌面指挥组件 (Desktop Topbar Component) [补充规格]

### 1.1 结构与布局
- 高度固定 `52dp`，外边框 `1px` 底线分割。
- 左侧：双源切换分段器（`图库站 | Pixiv`，高度 32dp）。
- 居中：满宽搜索输入框（高度 36dp，最小宽 280dp，圆角 10dp，背景色 `#161B26`）。
  - 前缀：`16dp` 放大镜矢量图标；
  - 后缀：`Ctrl+K` 等宽胶囊（高 18dp，Padding 2dp 6dp，圆角 4dp，背景 `rgba(255,255,255,0.06)`）；
- 右侧：快捷工具组（即时刷新按钮 32dp、账号/登录入口 32dp、设置中枢齿轮 32dp）。

### 1.2 Compose 推荐实现
```kotlin
@Composable
fun DesktopGalleryChromeRow(
    domain: GalleryHomeDomain,
    onPickDomain: (GalleryHomeDomain) -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPerformSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
)
```

---

## 二、 发现带双轨滑轨组件 (FindBand Component)

### 1.1 结构分解图
```text
┌───────────────────────────────── Bento Container (padding: 14dp 16dp) ─────────────────────────────────┐
│                                                                                                          │
│  [人气标签]                              [换一批] │  [推荐画师]                        [换一批] [全部画师]   │
│ ┌────────────────── RailWrap ──────────────────┐ │ ┌────────────────── RailWrap ──────────────────────┐ │
│ │ ‹                                          › │ │ │ ‹                                              › │ │
│ │ ┌─────────┐ ┌─────────┐ ┌─────────┐          │ │ │ ┌─────────────────┐ ┌─────────────────┐        │ │
│ │ │ ↑ 1     │ │ ↑ 2     │ │ ↑ 3     │          │ │ │ │  代表作三联图   │ │  代表作三联图   │        │ │
│ │ │ 封面长图│ │ 封面长图│ │ 封面长图│   ...    │ │ │ │   (高 160dp)    │ │   (高 160dp)    │  ...   │ │
│ │ │ 渐变蒙层│ │ 渐变蒙层│ │ 渐变蒙层│          │ │ │ ├─────────────────┤ ├─────────────────┤        │ │
│ │ │ #碧蓝   │ │ #东方   │ │ #原神   │          │ │ │ │ 头像 昵称 [+关注]│ │ 头像 昵称 [+关注]│        │ │
│ │ └─────────┘ └─────────┘ └─────────┘          │ │ │ └─────────────────┘ └─────────────────┘        │ │
│ └──────────────────────────────────────────────┘ │ └──────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 关键尺寸与约束
- **容器等高**：人气标签卡片与推荐画师卡片的高度**恒定为 224dp** [确定值]，杜绝高低差与空洞。
- **隐藏滚动条**：使用 `Modifier.horizontalScroll(rememberScrollState())` 时，不能挂载任何系统的垂直/水平滚动条指示器。
- **悬浮翻页把手**：
  - 位于滑轨两端，通过 `Box` 叠加并居中对齐 `Alignment.CenterStart` / `Alignment.CenterEnd`。
  - 判定边缘可见性：根据 `scrollState.value > 0` 和 `scrollState.value < scrollState.maxValue` 动态显隐（带透明度渐变）。

---

## 三、 等宽作品墙与卡片 (Equal-width Wall & GridCard)

### 3.1 结构与双格等高数学纠偏 [纠偏说明]
- 设单格卡片宽度为 $W$，网格间距为 $G = 16\text{dp}$；
- 单格方图卡片（1:1）图片高度为 $W$；跨双格横图卡片（2:1）宽度为 $2W + G$，图片理论高度为 $W + G/2 = W + 8\text{dp}$。
- **Compose 等高落地规则**：
  在实际装行渲染中（`desktopGridRows`）：
  每行卡片外层 `Row` 必须声明 `Modifier.height(IntrinsicSize.Max)`！单格卡片的图片容器使用 `aspectRatio(1f)`，并由外层 Row 的统一底栏对其；或者将宽卡的高度固定钳制为单格图片高度，图片内容采用 `ContentScale.Crop`，彻底消除 8dp 细微误差，使同一行所有卡片的底部信息栏处于绝对水平线上！

### 3.2 悬停动作坞 (Hover Action Dock)
- **拒绝大黑遮罩**：严禁使用覆盖半张画面的黑色大渐变，卡片点击默认即为“打开大图/详情”。
- **状态采集与轻量浮层**：
  ```kotlin
  val interactionSource = remember { MutableInteractionSource() }
  val isHovered by interactionSource.collectIsHoveredAsState()
  ```
  右上角浮出半透明高斯模糊药丸（`高 28dp`，圆角 `14dp`，毛玻璃 `blur(10dp)`，底色 `rgba(18, 22, 31, 0.85)`）。
  内嵌 4 枚 `26dp` 圆形微按钮（收藏、下载、选卡、更多）。

### 3.3 多选与指针手势隔离 (Selection Affordance)
- 鼠标左键点击：若按住 `Ctrl / Cmd` 键，触发 `onToggleSelect(card)`；若未按住，触发 `onOpenPost(card)`；
- 鼠标右键点击：**严禁在 pointer 手势抬起时误调用 `onOpenPost`**，必须在 `down` 阶段判定 `currentEvent.buttons.isSecondaryPressed`，并直接 return，使右键事件 100% 独占传递给外部 `ContextMenuArea`！
- 选中态：外边框高亮成品牌主色 `1.5dp`，右上角常驻蓝色实心角标（高 22dp，内嵌纯白粗体 `✓`）。

---

## 四、 现代暗色右键上下文菜单 (Dark Context Menu)

### 4.1 容器与边缘避让算法
- **宽度**：固定 `216dp` [确定值]，圆角 `10dp`，内边距 `5dp`，背景色 `#161B26`。
- **避免跑偏与裁切算法**：
  在指针落点 `(x, y)` 触发，当靠近窗口右侧或底部时自动向左/向上翻折，严禁任何超出视口截断行为。

### 4.2 菜单项规范实现
```kotlin
@Composable
fun DesktopContextMenuItem(
    icon: @Composable () -> Unit,
    title: String,
    shortcut: String? = null,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val bg = when {
        isHovered && isDanger -> GalleryColorTokens.StatusDanger.copy(alpha = 0.12f)
        isHovered -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isDanger -> GalleryColorTokens.StatusDanger
        else -> GalleryColorTokens.TextPrimary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(8.dp))
        Text(text = title, style = GalleryTypeTokens.CardTitle.copy(fontSize = 12.sp), color = contentColor)
        if (shortcut != null) {
            Spacer(Modifier.weight(1f))
            Text(text = shortcut, style = GalleryTypeTokens.KeyHintMono)
        }
    }
}
```

---

## 五、 二级页面：pixivision 文章详情屏 (Article Detail Screen) [补充规格]

### 5.1 结构分解
1. **文章指挥顶栏**：返回把手 + 特辑类别徽标（高度 22dp）+ 日期与作者署名 + 站方直达按钮。
2. **正文头部**：大标题（32sp 加粗，行高 1.3）+ 导读段落（14sp，行高 1.8，最大宽度 820dp）+ 标签流。
3. **作品单元流 (Work Unit Stream)**：
   - 作品单元纵向间距：`30dp` [确定值]。
   - 大图容器：圆角 `12dp`，外边框 `1px`，原图自然比例大图呈现。
   - 画师底栏：高度 `48dp`，圆角 `10dp`，包含 32dp 头像、画师名、作品名、搜画师按钮、收藏按钮、下载按钮。
   - 配文说明段落：12.5sp，字间距微调，行高 1.7。
4. **底部相关特辑推荐轨 (Related Articles Rail)**：
   - 标题：“相关特辑”；
   - 单卡宽 **236dp**，封面高 **132dp**，圆角 `12dp`，标题 12sp。

### 5.2 Compose 推荐实现签名
```kotlin
@Composable
fun DesktopGalleryPixivisionArticleScreen(
    article: DesktopPixivisionArticle,
    body: DesktopPixivisionArticleBody?,
    loading: Boolean,
    onBack: () -> Unit,
    onSearchArtist: (String) -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenSite: () -> Unit,
    onToggleFavoriteWork: ((String) -> Unit)? = null,
    onSaveWork: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
)
```

---

## 六、 Booru 图库站控制与分页舱 (Booru Control & Pager Spec) [补充规格]

### 6.1 标签维度舱 (Tag Dimensions Bento)
- **4 列等宽网格**：
  `Row` 内部均分 4 列（每列占比 25%），分别对应：
  - 画师（Artist）：紫色标；
  - 角色（Character）：绿色标；
  - 系列/作品（Copyright/Series）：蓝色标；
  - 通用描述（General）：灰色标。
- 标签项（`t-chip`）：高度 `28dp`，圆角 `14dp`，带中英双语与数量徽标。

### 6.2 分页舱 (Pagination Dock)
- **居中翻页器**：
  - 上一页 `←` 与 下一页 `→`（尺寸 32dp × 32dp）；
  - 页码按钮序列：当前页主色高亮；
  - 每页数量分段：`每页 50 | 100 | 200`（高度 28dp）；
  - 键盘提示：底部微文本 `<kbd>←</kbd> <kbd>→</kbd> 键盘翻页`。

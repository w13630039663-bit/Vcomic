# 阅读器对齐重做 — 组件与机制规格

本文件 = 实现唯一依据。分两部分：UI 组件映射 + 翻页/阅读机制对齐规格（含原版机制逆向结论，注释与文案中不得出现原版项目名）。

## 1. DesktopReaderTopBar 重做

文件：`desktop/desktop/src/main/kotlin/com/venera/desktop/reader/DesktopReaderTopBar.kt`

- 布局改横向分组流（可滚 Row）：`[返回] | [上一章 下一章] | [首页 上一页 下一页 末页] | [书签 书签列表] | [适配 1:1 缩小 放大] | [放大镜 跳页] | [页码文本] | [电子书 页 筛选 AI] | [更多]`，组间 1dp 竖线。
- 按钮清单与真功能接线（全部为真，不许空 lambda）：
  - 首页/末页 → jumpToLogical(0) / jumpToLogical(last)
  - 上一页/下一页 → step(-1)/(+1)（复用现有翻页函数）
  - 书签列表 → 开 ReaderTocOverlay 书签 tab
  - 适配 → 循环 prefs.fitIndex 档；1:1 → zoomTarget=1f, panTarget=0
  - 跳页 → 现有跳页对话框
  - 电子书/页/筛选/AI → 开设置弹窗并定位对应 tab
  - 更多(⋮) → 下拉菜单：阅读方向倒序、自动翻页档（关/2s/4s/8s）、显示页码开关（prefs.showPageNumber，控制页码文本显隐）、隐藏顶栏（chromeVisible=false）
- 页码文本显示 `当前/总页`；prefs.showPageNumber 关时隐藏。
- 保留 windowDragArea 与 chrome 显隐联动。

## 2. ReaderSettingsOverlay 重做（四段 tab）

文件：`desktop/desktop/src/main/kotlin/com/venera/desktop/reader/DesktopReaderHud.kt`（现单列列表改 tab 壳）

- 外壳：宽 600dp、maxH 0.8 窗高、圆角 16dp、玻璃底、发丝描边、内部 verticalScroll。
- tab 条：电子书 / 页 / 筛选 / AI；选中胶囊淡蓝底，未选描边胶囊；150ms 切换。
- 新增 prefs 键（`DesktopReaderPreferences.kt`，命名延续 reader_ 前缀）：
  - `reader_turn_anim_seconds: Float = 0.30f`
  - `reader_shadow_on: Boolean = true`；`reader_shadow_size: Float = 7f`；`reader_shadow_opacity: Float = 50f`；`reader_shadow_displace: Float = 5f`
  - `reader_align_next_horizontal: Boolean = true`
  - `reader_blank_page: Boolean = false`
  - `reader_fit_screen: Boolean = true`（适应屏幕；与 fitIndex 共存，fitScreen=false 时用原始尺寸口径）
  - `reader_no_enlarge: Boolean = true`
  - `reader_single_in_scroll: Boolean = false`
  - `reader_rotate_h: Int = 0`（横图旋转档，复用 0/1/2/3 语义）
  - `reader_margin_h: Int = 16`；`reader_margin_v: Int = 16`
  - `reader_show_page_number: Boolean = true`
  - `reader_filter_hue: Float = 0f`；`reader_filter_sepia: Float = 0f`；`reader_filter_invert: Boolean = false`；`reader_filter_negative: Boolean = false`
  - `reader_ai_artifact: Boolean = false`；`reader_ai_artifact_model: Int = 1`；`reader_ai_artifact_tone: Boolean = true`；`reader_ai_artifact_size: Float = 3f`
  - `reader_ai_descreen: Boolean = false`；`reader_ai_descreen_model: Int = 1`；`reader_ai_descreen_keep: Boolean = true`；`reader_ai_descreen_size: Float = 3f`
  - `reader_ai_upscale: Boolean = false`；`reader_ai_upscale_model: Int = 0`；`reader_ai_upscale_maxmp: Float = 2f`；`reader_ai_upscale_auto: Boolean = true`；`reader_ai_upscale_scale: Int = 4`

### 页 tab 条目（顺序与 UI 见 design-analysis.md §2；每条的真实生效点）

| 条目 | 生效点（必须全部真实接线） |
|---|---|
| 模式 6 卡 | prefs.turnEffect 0–5 现档保留；⚙ 三卡各开参数弹窗（翻页(Beta)/平滑翻页(Alpha) 开透视度弹窗；导览视图 开导览配置弹窗——现有两弹窗保留接回） |
| 翻页音效 ⚙ | prefs.soundOn 保留；⚙ 开音效弹窗（音量滑条 prefs.soundVolume 新键，喂 DesktopReaderPageSound 音量参数） |
| 倒序 | prefs.reverseReading 新键（语义同现 desktopReaderReverses 源头，接通 HORIZONTAL_RTL 模式映射） |
| 条漫 | prefs.readWebtoon 新键 = 开启后强制 VERTICAL_CONTINUOUS 模式、禁双页组、垂直边距禁用 |
| 双页展示 | prefs.coverSinglePage 语义反转接对（现键=封面独占；新 UI 口径=双页总开关，关=每次只显示一页） |
| 阴影效果 ⚙ | prefs.shadow_* 四键喂双页中缝渐变参数（现有固定 0.7f/宽度口径改为吃 prefs；size=宽度%、opacity=黑度%、displace=偏移%） |
| 不用于横幅 | prefs.keepWideSingle 现键接对 |
| 与下一水平对齐 | prefs.alignNextHorizontal 新键 → buildDoublePageGroups 前置补白逻辑：对每个位置预计算到下一横图距离，距离为奇数时插空白页 |
| 开头空白页 | prefs.blankPage 新键 → 分组时在首页前插 BLANK_PAGE，使第 1 页单独落右侧 |
| 适应屏幕 | prefs.fitScreen 新键 → false 时不做 fit 缩放（页面按原始像素显示，可滚动），true 时维持现 fitIndex 口径 |
| 勿放大超原始 | prefs.noEnlarge 新键 → zoomTarget 上限 = 1f（clampPanOffset 上界同步钳原始尺寸） |
| 每次只显示一页 | prefs.singleInScroll 新键 → 仅垂直连续档可用；开=垂直滚动改一屏一页（LazyColumn 页高=视口高钳页原始高） |
| 旋转/旋转横图 | prefs.rotate 现键（全图）+ rotateH 新键（仅横图）；旋转档 0/1/2/3 = 0°/90°左/90°右/180°，作用于 ReaderPagedCell graphicsLayer 且分组宽度口径同步翻转 |
| 水平/垂直边距 | prefs.marginH/marginV 新键 → 画布区 padding（paged 档每页外边距 + 连续档页间隙）；条漫档 marginV 禁用 |
| 动画速度 | prefs.turnAnimSeconds 新键 → 替换现有 tween(300)/tween(260) 两处硬编码，所有翻页档统一吃该值 |

### 筛选 tab

- 五颗预设 chips 保留；预设=把下列滑条打包存 JsonKeyValueStore 列表（现实现已有预设机制则沿用）。
- 亮度/对比度/饱和度：现有 prefs.brightness/contrast/saturation 三滑条保留，范围改 0–400% 显示口径（内部 0–4f）。
- 色相旋转 prefs.filterHue → DesktopReaderImageFilter 输出 ColorMatrix 链接 hue 旋转矩阵（20 元素标准 hueRotate 矩阵）。
- 黑白/负片两开关：黑白=饱和度 0 矩阵；负片=invert 矩阵（-1 RGB + 保持 alpha）；两者可叠加。移除现「黑白反色」合体开关与「护眼暖色/深褐复古」开关（暖褐由 sepia 色温滑条替代：sepia 标准 ColorMatrix 按强度插值）。
- 全部滤镜合成为单一 ColorMatrix（依序相乘）喂现有图像滤镜路径，禁止每页重复计算—— remember(prefs) 一次算好。

### AI tab

- 纯本地算法实现（不接网络模型下载），三功能区开关默认关：
  - **AI 痕迹去除**：实现=双边滤波/导向滤波（Skia ImageFilter 链或 Skia 高斯+矩阵混合近似）压缩 JPEG 块痕迹；模型下拉=三档强度（轻/中/重，映射不同参数组）；「保持色调平缓」=处理后在明度域做 15% 原图混合；尺寸滑条=滤波半径基准（1–10% 页宽）。
  - **AI 去网纹**：实现=频域近似（多半径形态学：先小半径均值滤波估计网点底色，再与原图按 mask 混合）；「保持大网点」=大半径通道检出后反选保护；模型下拉=两档（标准/硬核=迭代次数 1/2）。
  - **AI 增强**：实现=锐化链（Unsharp Mask：高斯模糊差值放大叠加）+ 2x/4x 上采样（Skia Bitmap.scale 或双线性+后锐化）；「最大输入分辨率」=超限时跳过增强并显示提示胶囊；「自动比例」=按剩余内存预算自动选 2x/4x。
- 三卡对比图：当前页中央区域取 240×120dp 两格实时渲染（before=原图 / after=过当前功能区参数的 Skia 管线结果），开关切换即时刷新；页变化即重算。
- 处理管线挂接：ReaderPagedCell 渲染前，若任一 AI 开关开，页面 Bitmap 过管线再上屏；逐页处理须在 IO 线程 + remember 页级缓存，禁止主线程卡顿；缓存上限 4 页 LRU。
- 禁止网络请求；全部 Skia/纯 JVM 实现。

### 电子书 tab

- 空态卡两行文案（主「暂无电子书内容」副「支持 PDF / EPUB 后解锁提取」），无任何开关。

## 3. 翻页机制对齐规格（核心）

统一动画时长 = prefs.turnAnimSeconds（默认 0.30s）。

### 3.1 横向阅读（档 0，对齐原版 slide）

- 引擎保持 HorizontalPager，但翻页手感对齐：
  - 键盘 Left/Right/A/D/Space=翻页（现 DesktopReaderKeyTest 口径保留）。
  - 滚轮：paged 档下滚轮=翻页（累计阈值 0.9 防触控板连翻，复用 2026-11-01 口径），Ctrl+滚轮=缩放（不变）。当前实现 paged 滚轮只缩放，必须改。
  - 近距 jumpToLogical tween 时长改吃 turnAnimSeconds。
- 双页：turnEffect 0–3、5 档下 HorizontalPager 页数=组数（现行为保留）。

### 3.2 垂直阅读（档 1，对齐原版 scroll）

- LazyColumn 连续滚动（现行为），条漫开关开启强制此档。
- 滚轮：原生滚动（不劫持），滚轮翻页步长=视口高×0.4（对齐原版 scrollMovePercent 40%）；触控板自然滚动不受影响。
- 页间隙 = prefs.marginV。
- singleInScroll 开时：页高=视口高，一次一页。

### 3.3 淡出（档 3，对齐原版 fade）

- 实现：HorizontalPager 上叠交叉淡化层——出视口页 alpha=1-offset、入视口页 alpha=offset（读 currentPageOffsetFraction，线性，无缓动曲线，与原版一致）。
- 替换现 graphicsLayer 档 3 分支的 scale/位移组合为纯 alpha 交叉；两页同帧交叉（旧页 1→0、新页 0→1）。

### 3.4 翻页(Beta)（档 4，3D 翻页对齐）

- 现 DesktopBookTurnViewport 保留骨架，对齐点：
  - 两段式：前 50% 旧页绕左/右缘 rotateY 0→90°（带 perspective≈6000px），后 50% 新页 rotateY -90°→0；透视度弹窗滑条映射 40 档透视值（现弹窗保留改档表）。
  - clipPath inset 按进度插值（页缘裁剪）+ 动态投影（blur 随进度 6→120pt、spread 2→20pt、alpha 0.15→0.65），方向按 RTL 翻转。
  - 动画时长吃 turnAnimSeconds；双页=两页各自做（现双页排布保留）。

### 3.5 平滑翻页(Alpha)（档 5，对齐原版 smooth）

- 真·卷曲：翻起页 = 平移+旋转+matrix 剪切变形（Compose graphicsLayer Matrix + clipPath path 贝塞尔卷缝）；卷缝阴影=线性渐变模糊带（预模糊替代 backdrop，复用仓内既有预模糊手法）。
- 拖动进度实时驱动卷曲程度（gesture 层在 graphicsLayer 外，避拖拽三坑）；无拖动的点击翻页=turnAnimSeconds 补间完整卷曲；配置角度默认 30°（透视度弹窗复用）。

### 3.6 导览视图(Alpha)（档 2，接线 DesktopGuidedViewEngine）

- **现状=引擎存在但渲染分支没调用，必须接线**：
  - paged 分支前判 prefs.turnEffect==2 && guidedViewOn && 本页有面板数据 → 走 DesktopGuidedViewEngine 驱动的缩放聚焦渲染：当前面板 scale-fit 居中（maxZoom 钳制），goNext/goPrev=下一格/上一格（格序=角点最近邻排序）；无面板数据页整页显示。
  - 面板检测：仓内无 AI 分格模型，降级实现=按页图像连通域/固定网格切分（首版固定三分格: 上/中/下 或 左/中/右 按页宽高比选轴），后续可换模型。
  - 三样式（focus/hide/immersive）接现有导览配置弹窗参数：focus=仅缩放；hide=非当前格压黑遮罩；immersive=渐进揭示。
  - 导览档下键盘/点击=格序翻步，Esc/缩放手势退出聚焦回整页。

### 3.7 已知假开关转真（本次必须清零）

- dragTurnOn：边缘拖拽跟随翻页——实现=paged 档画布左右 15% 热区拖拽增量映射 pager offset（映射系数 0.5），松手过半翻页。
- filterQualityIndex：ReaderPagedCell AsyncImage 显式传 filterQuality（resolveFilterQuality 映射 FilterQuality）。
- guidedViewOn 全族：按 3.6 接线。

## 4. 状态与测试

- 新 prefs 全部走 JsonKeyValueStore 现有模式；单本覆盖键逻辑不动。
- 判据红线（动码前先读）：
  - `DesktopMaskJudgementSingleSourceTest`：Screen.kt 中 "0.7f" 行数敏感（现 4 行）——书缝阴影改吃 prefs 后该行会动，须同步改判据口径并保持"恰 1 行"约束成立（或按实际语义更新断言）。
  - `DesktopNewScreensSemanticsTest`：条漫/单页/双页 chips、上一话/下一话/目录、空页表文案、页格比例——改 UI 文案时保持这些 contentDescription 存活。
  - `DesktopReaderKeyTest`/`DesktopReaderPagingTest`/`DesktopReaderViewportTest` 纯函数口径。
- 编译验证：每改一处 compileKotlin；收尾点名跑上述测试类。

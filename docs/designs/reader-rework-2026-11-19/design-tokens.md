# 阅读器重做 — Design Tokens

沿用 `sidebar-macos-rework-2026-11-19` 的品牌令牌，以下为阅读器专属补充。

## 1. 画布

- `canvasDark`: `Color(0xFF0B0C0F)`
- `canvasLight`: `Color(0xFFF2F3F5)`
- `canvasDim`: `Color(0xFF05060A)`（纯享档）
- `pageOutlineDark`: `oklch(1 0 0 / 0.10)`（图片外描边）
- `pageOutlineLight`: `oklch(0 0 0 / 0.10)`

## 2. 浮层表面

- `hudSurfaceDark`: `Color(0xCC16171C)`
- `hudSurfaceLight`: `Color(0xF2FFFFFF)`
- `hudBorder`: `Color(0x1CFFFFFF)` / light `Color(0x14000000)`
- `scrimColor`: `Color(0x66000000)`（抽屉背板）

## 3. 文字与图标

- `inkPrimary`: `Color(0xF0FFFFFF)` / light `Color(0xE6000000)`
- `inkSecondary`: `Color(0x9EFFFFFF)` / light `Color(0x8A000000)`
- `inkTertiary`: `Color(0x5CFFFFFF)` / light `Color(0x61000000)`
- `accent`: `Color(0xFF8B7CF6)`（进度、选中态）
- `accentSoft`: `Color(0x298B7CF6)`（选中底）

## 4. 尺寸

### 控制条
- `controlBarHeight`: `44.dp`
- `controlBarInset`: `20.dp`（左右内缩，不贴边）
- `controlBarRadius`: `14.dp`
- `controlBarPaddingH`: `10.dp`
- `controlBarPaddingV`: `6.dp`
- `iconButtonSize`: `32.dp`
- `iconGlyphSize`: `16.dp`

### 进度条（常驻方案 B）
- `progressBarHeight`: `22.dp`
- `progressTrackHeight`: `2.dp`
- `progressBarRadius`: `11.dp`
- `progressBarPaddingH`: `12.dp`

### 竖向工具轨（常驻方案 C）
- `railWidth`: `44.dp`
- `railRadius`: `22.dp`
- `railGap`: `4.dp`
- `railInset`: `16.dp`

### 顶栏（常驻方案 D）
- `topBarHeight`: `40.dp`
- `topBarRadius`: `0.dp`（贴边通栏，不用圆角）

### 抽屉
- `drawerWidth`: `300.dp`
- `drawerRadius`: `16.dp`
- `drawerPadding`: `12.dp`
- `drawerInset`: `12.dp`
- `drawerSectionGap`: `12.dp`

### 左侧缩略条（常驻方案 E）
- `leftStripWidth`: `120.dp`
- `leftStripTopOffset`: `40.dp`（让开顶栏）
- `leftStripListPaddingH`: `8.dp`
- `leftStripListPaddingV`: `10.dp`
- `leftStripRowGap`: `8.dp`
- `leftStripFootHeight`: `44.dp`
- `leftStripFootPadding`: `9.dp 10.dp 11.dp`
- `leftStripThumbRadius`: `6.dp`
- `leftStripSelectedRing`: `2.dp`
- `leftStripSurface`: `Color(0xF2171218)`（比画布更深一档）
- `leftStripBorderRight`: `Color(0x1CFFFFFF)`
- 画布让位：`padding-left: 146.dp`（120 + 26 安全距），画布**不被压**，是布局让出

### 缩略格
- `thumbWidth`: `84.dp`
- `thumbRadius`: `6.dp`
- `thumbGap`: `8.dp`
- `thumbSelectedRing`: `2.dp`

## 5. 圆角规则

外圆角 = 内圆角 + 内边距。

- 抽屉外 `16`，内件 `8`，内边距 `12`（视觉近似，内件半径按 8 取）
- 控制条外 `14`，按钮 `8`，内边距 `6`
- 缩略格 `6`，选中描边不改变半径

## 6. 动效

- `pressScale`: `0.96f`，`150ms`，`ease-out`
- `chromeEnter`: `220ms`，透明度 + `translateY(-8.dp)`
- `chromeExit`: `150ms`，透明度 + `translateY(-8.dp)`（退出短于进入）
- `drawerEnter`: `260ms`，横向滑入 + 透明度
- `drawerExit`: `160ms`，横向滑出
- `autoHideDelay`: `2000ms`（鼠标静止后控制条淡出）
- `hintShowDelay`: `300ms`
- 高频交互（悬停、翻页）：`≤150ms` 或即时，不做缓动花活
- 全部动效在系统"减少动画"开启时退化为透明度过渡

## 7. 阴影

层次用阴影，不用边框画卡片感：

- `hudShadow`: `0 4 16 rgba(0,0,0,0.28)`
- `drawerShadow`: `0 12 40 rgba(0,0,0,0.40)`
- `controlBarShadow`: `0 2 10 rgba(0,0,0,0.24)`

# 阅读器重做 — 组件规格

## 0. 结构

```
L0 画布      只有图，零常驻
L1 瞬时层    控制条 · 翻页提示 · 加载骨架 · 边界胶囊
L2 面板层    导航抽屉 · 设置抽屉      （互斥，同时只开一个）
```

状态收口：

```kotlin
sealed interface ReaderSurface {
    data object None : ReaderSurface
    data object Chrome : ReaderSurface
    data object Nav : ReaderSurface
    data object Settings : ReaderSurface
}
```

放大镜不占浮层，是模式位（`magnifierOn` 独立布尔，只影响控制条按钮态）。

## 1. 控制条（ControlBar）

- 形态：上控制条 + 下控制条，共用一套显隐
- 高度 `44.dp`，左右内缩 `20.dp`，圆角 `14.dp`，半透明玻璃底 + `hudShadow`
- 上条内容：返回 · 标题（单行省略）· 章节切换 · 书签
- 下条内容：页码胶囊 · 进度滑轨 · 模式切换 · 设置入口
- 显隐：鼠标移动立即显示；静止 `2000ms` 淡出；退出 `150ms`，进入 `220ms`
- 键盘/滚轮翻页不唤醒控制条（静默翻页保留）

## 2. 常驻件（四选一）

### A 零常驻
静止后画布完全干净。恢复方式：鼠标移动 / 按 `Enter`。

### B 进度条
- 高度 `22.dp`，圆角 `11.dp`，底部居中，`12.dp` 内缩
- 内容：页码文本 + `2.dp` 进度线，进度用 `accent`
- 点击进度条任意位置跳页；右键打开跳页输入

### C 竖向工具轨
- 宽 `44.dp`，圆角 `22.dp`，垂直居中，右侧 `16.dp` 内缩
- 五枚按钮：返回 / 目录 / 设置 / 缩放 / 书签
- 悬停底色 `hoverBg`，选中底 `accentSoft` + 图标 `accent`

### D 顶栏
- 高 `40.dp`，通栏贴边，无圆角
- 左：返回 + 标题；右：页码 + 章节
- 半透明 `hudSurfaceDark`，底部 `1.dp` 分隔线

### E 左侧缩略条（当前选定）
- 宽 `120.dp`，从顶栏下沿开始贴左，右侧 `1.dp` 分隔线，底色比画布深一档
- 结构：可滚缩略列表（上）+ 页码/百分比/进度（下，`44.dp` 固定）
- 缩略格：宽撑满列表（约 `104.dp`），`2:3`，圆角 `6.dp`，间距 `8.dp`
- 当前页：`2.dp` accent 描边 + 提亮底；列表自动 `scrollIntoView` 到当前页
- 翻页联动：单击缩略图跳页；页码变化时同步滚动与高亮
- **布局语义**：占用真实宽度，画布按 `padding-left:146.dp` 让位，不叠加、不遮挡
- 底部页码与顶栏页码互为镜像，顶栏保留导航与工具组

## 3. 导航抽屉（NavDrawer）

- 宽 `300.dp`，右侧滑入，内缩 `12.dp`，圆角 `16.dp`
- 三段切换：缩略图 / 章节 / 跳页
- 缩略图格：`84.dp` 宽，`6.dp` 圆角，选中 `2.dp` accent 描边
- 章节列表：当前章 accent 文字 + 左侧 `3.dp` 竖条
- 跳页：数字输入 + 快捷按钮（首页 / 末页）
- 打开时画布不动，不加暗幕；点击画布关闭

## 4. 设置抽屉（SettingsDrawer）

- 宽 `300.dp`，右侧滑入，与导航抽屉同位置互斥
- 分组：显示（适配模式 / 背景 / 旋转）· 阅读（模式 / 方向 / 翻页动效）· 图像（滤镜 / 亮度）
- 分组标题 `12.sp` `inkTertiary`；行高 `36.dp`
- 底部：重置全部

## 5. 边界胶囊（BoundaryHint）

- 末页/首页继续翻时出现，居中底部 `92.dp` 处
- `12.dp` 圆角，深底 `0xE616181D`，文字 `ink-1`
- 自动消失 `1600ms`；不做动画进出，避免打断连续翻页

## 6. 放大镜（MagnifierLens）

- 跟随指针的圆形窗口，直径 `180.dp`
- 边缘 `1.dp` `hudBorder`，内部按 `2.0x` 采样
- 关：立即消失；开：`120ms` 淡入

## 7. 输入契约

| 输入 | 行为 |
|---|---|
| `←` `↑` | 上翻（方向随阅读模式） |
| `→` `↓` `PageDown` `D` | 下翻 |
| `Space` / `Shift+Space` | 下翻 / 上翻 |
| `Home` / `End` | 首页 / 末页 |
| `Ctrl` + 滚轮 | 以光标为锚缩放 |
| 滚轮（翻页模式） | 下翻 / 上翻 |
| `+` `-` `0` | 放大 / 缩小 / 还原 |
| `F` | 切换适配模式 |
| `R` | 旋转 90° |
| `G` | 跳页 |
| `B` | 书签 |
| `M` | 放大镜 |
| `Esc` | 依次关闭面板 → 收起控制条 → 返回 |
| `,` `.` | 上章 / 下章 |
| 左键拖拽（放大态） | 平移 |
| 双击 | 以光标为锚缩放 |
| 单击左右 15% | 翻页 |
| 单击中部 | 切换控制条 |

**唯一通道**：全部按键只走窗口级总线一处派发，浮层打开时由浮层以更高优先级接管，禁止组件级 `onKeyEvent` 再实现一遍。

## 8. 落码顺序

1. `ReaderSurface` 状态收口 + 控制条合并
2. 输入通道收敛（删组件级重复实现）
3. 常驻件按选定方案接入
4. 令牌替换（裸 dp → `VeneraSpacing` / 本文件令牌）
5. 动效与降级

## 9. 视图状态模型（借鉴 OpenComic）

### 9.1 出处

OpenComic 的阅读视图只维护两个量，翻页与缩放都交给同一个 `transform`：

| 手法 | 出处 |
|---|---|
| 翻页 = 整条横带平移 | `scripts/reading.js:536` `translate(-(contentWidth * (index - 1))px, 0)` |
| 缩放 = 整条 scale + 动态原点 | `scripts/reading.js:1084-1087` `transform-origin: center Npx` + `scale(s)` |
| 缩放与平移复合 | `scripts/reading.js:1106` `scale(s) translate(-offset, 0)` |
| 离屏页不渲染 | `themes/material-design/reading.css:213-215` `content-visibility` |
| 裁切收敛 | `themes/material-design/reading.css:196` `contain: paint` |

它没有自由平移量，所以从来不需要夹紧边界；我们现在的 `clampPanOffset` 是因为引入了第四个状态 `pan` 才不得不写。

### 9.2 状态定义

```kotlin
/**
 * 阅读视图唯一几何状态。
 *
 * @property page 当前页索引，翻页只改这个值。
 * @property scale 缩放倍率，1f 为适配档。
 * @property origin 变换原点，取归一化坐标（0..1），由锚点事件换算。
 */
internal data class ReaderTransform(
    val page: Int = 0,
    val scale: Float = 1f,
    val origin: Offset = Offset(0.5f, 0.5f),
)
```

删除项：`zoomTarget`、`zoom`、`panTarget`、`pan` 四个独立状态，以及
`clampPanOffset`、`computeCursorAnchoredZoom` 的手算夹紧分支。

### 9.3 布局模型

- 页面按分组表（`distribution`）排成一条横带，宽度 = `组数 × 视口宽`
- 渲染层是一个容器 + 一个 `Row`，容器负责变换，页只负责画自己

```kotlin
Box(
    modifier = Modifier.graphicsLayer {
        translationX = -state.page * viewportWidthPx
        scaleX = state.scale
        scaleY = state.scale
        transformOrigin = TransformOrigin(state.origin.x, state.origin.y)
    },
) {
    Row { groups.forEach { PageCell(it) } }
}
```

### 9.4 交互映射

| 操作 | 只改 | 说明 |
|---|---|---|
| 下一/上一页 | `page ± 1` | 平移由 `translationX` 推导，动画交给 `animateFloatAsState` |
| 双击 | `scale` 与 `origin` | 原点取双击位置的归一化坐标 |
| Ctrl + 滚轮 | `scale`，`origin` 取光标 | 系数 1.15 / 0.87 |
| 拖拽（放大态） | `origin` 的平移补偿 | 位移量除以 `scale` 再折算，不单独存 pan |
| 切换适配 | `scale = 1f`，`origin` 归中 | 同时清空平移补偿 |
| 旋转 | 独立 `rotationDegrees` | 只影响绘制，不进 `ReaderTransform` |

### 9.5 边界规则

- `scale` 锁 `1f..4f`，等于 `1f` 时 `origin` 强制回 `(0.5, 0.5)`
- 放大后允许把图拖到视口外，但 `origin` 的平移补偿换算后仍限制在
  `±(scale - 1) / 2 / scale` 的归一化区间，保证不会无限漂走
- 页索引变化时 `scale` 与 `origin` 复位，避免带着放大态翻页

### 9.6 验收判据

落码完成后检查：

1. 放大 2x 后双击复位，图像回到正中，无残留偏移
2. 放大状态下滚轮缩放，光标下的内容位置不跳
3. 快速连续翻 10 页，末页完整可见（无半页偏移）
4. `ReaderTransform` 之外不存在任何几何状态写入点
5. `clampPanOffset` 与手写手势位移逻辑已删除

# Venera 侧栏 macOS 激进重做 — 组件规格（Component Spec）

## 1. `VeneraSideBar`（贴边主容器）
- 去掉外层悬浮圆角卡片边距，改为贴左边缘 `fillMaxHeight()`，宽度在 `248.dp` 与 `60.dp` 间平滑动画过渡。
- 右侧绘制 `1.dp` 细分割线，顶部绘制 `1.dp` 微弱高光。

## 2. `MacNavRow`（导航行组件）
- 状态：默认 / 悬停（`hoverBg` + 图标微放大 `1.06f`）/ 按压（`scale 0.96f`）/ 激活（`activeBg` + 左侧 `3.dp` 强调色竖条 + 图标高亮）。
- 右侧可选 `meta` 弱提示（数字或 `HOT` 标签）。
- 折叠态下仅居中显示图标与左侧激活竖条。

## 3. `MacCollapsibleGroup`（可折叠分组）
- 标题行可点击切换展开/折叠状态（`remember { mutableStateOf(true) }` 或按分组 key 记忆）。
- 左侧小箭头随状态旋转 `0f` ↔ `-90f`。
- 子项列表使用 `AnimatedVisibility(expandVertically + fadeIn / shrinkVertically + fadeOut)` 实现丝滑折叠。

## 4. `MacPetZone`（桌宠区）
- 保留现有桌宠组件与点击交互，增加轻柔呼吸浮动动效与脚底淡紫色环境光斑。
- 折叠态下缩小为 `40.dp` 迷你态并隐藏气泡。

## 5. `MacFooterBar`（极简单行底栏）
- 顶边：有活跃下载任务时显示 `1.5.dp` 强调色进度光带。
- 左侧：头像（`24.dp` 圆形 + 在线绿点）+ 用户名（单行省略）。
- 右侧：下载图标（带环形进度与角标，保留 `onGloballyPositioned` 飞入落点挂手）、设置图标、折叠/展开图标。
- 折叠态下转为纵向紧凑排列（头像 + 展开按钮）。

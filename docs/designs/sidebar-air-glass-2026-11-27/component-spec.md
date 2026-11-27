# Venera 侧栏与浅色全窗通透重做 — 组件规格

## 1. `DesktopGlass.surface(palette)`（全窗唯一玻璃取值点）

- **位置**：[DesktopTheme.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopTheme.kt)
- **约束**：侧栏背景 `LightGlassBackdrop`、顶栏 `DesktopCommandBar`、内容区 `contentSurface` 必须同时消费此单源取值，保证全窗同一透明度。
- **状态变化**：
  - 浅色：`Color(0xFFFAFCFF)` @ `0.78f` alpha
  - 深色：`palette.raised` @ `0.56f` alpha

## 2. `VeneraLightRailSkin` — 顶部品牌与主导航

- **品牌行**：24dp 图标 + 13sp SemiBold 标题，上下边距收紧至 `top = 12.dp, bottom = 8.dp`。
- **主导航行 (`MacNavRow`)**：
  - 默认：透明底 + `ink2` 图标与文字
  - 悬停：`palette.hover`（即时/平滑变色）
  - 选中：`palette.pill` 圆角 `8.dp` 胶囊 + `ink1` 加微重文字，**不绘制左侧 3dp 竖条**；主图标切至 `filledIcon` 并使用 `ink1` 保持纯净克制（不再染亮紫）。

## 3. `MacCollapsibleGroup` — 分组头

- 结构调整：`Row` 内先文字 `Text(title)`，紧随 `Spacer(4.dp)`，再摆 `KeyboardArrowDown`（14dp）；文字左缘对齐下方图标左缘（`padding(horizontal = 10.dp)`）。
- 旋转状态：展开 `0f`，折叠 `-90f`。
- 取消 `MacDivider()` 横线，组与组之间仅以 `top = 12.dp` 间隔。

## 4. 二级功能图标去重与徽标

- `DesktopShellPanes.kt` 替换撞车图标：搜图→`ImageSearch`、每日热门→`LocalFireDepartment` / `TrendingUp`、收藏与历史→`Bookmarks`、关注画师→`Group`、珍藏册→`CollectionsBookmark`、我的 pixiv→`AccountCircle`。
- 在 `VeneraLightRailSkin` 中，二级条目（`group.title` 非空）以 `20.dp` `RoundedCornerShape(5.dp)` 柔和彩色微标底配 `12.dp` 白色线框图标呈现，提高 10+ 项并排时的第一眼辨识度。

## 5. `MacFooterBar` — 极简底栏

- 移除顶边 `1.dp` 分割线与 `0x0F000000` 暗底条；
- 取消昵称长文字（彻底消除截断省略号），`Row` 均布 4 枚图标按钮：
  1. 折叠侧栏（`ViewSidebar`）
  2. pixiv 头像圆点（`22.dp` `LightAvatar` + 在线状态点，保留点击进账号页与无障碍 `contentDescription = accountCard.name`）
  3. 下载与本地（`Download`，保留 `LocalVeneraSideBarDownloadAnchor` 飞入锚点与 `popTick` 角标弹跳）
  4. 设置（`Settings`）

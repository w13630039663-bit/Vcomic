# 侧栏个性化重做（方案一：Bento 磁贴 + 悬浮身份底坞）— 组件规格

规格名：`sidebar-bento-atmosphere-2026-11-27` · 日期：2026-11-27 · 配套：`proposal.md` / `design-analysis.md` / `design-tokens.md`  
实施文件（已落码）：`VeneraLightRailSkin.kt`（742 行，净减 233 行） + 新建 `DesktopRailBentoSections.kt`（244 行） + 新建 `DesktopRailFloatingDock.kt`（347 行）。

## 1. `DesktopRailBentoGrid` — 主导航 2×2 磁贴网格

**解构**：`Column(spacedBy 6dp)` → 两行 `Row(spacedBy 6dp)` → 每格 `Box(weight(1f), height 58dp)` → 顶部行（`Icon` 17dp + 激活圆点 `6dp`） + 底部 `Text`。

**视觉参数**：圆角 `11.dp`；默认底 `controlFace` + `1.dp` `hairline` 描边；选中底 `accentSoft` + `1.dp` `accentBorder` 描边。

**状态**：

| 状态 | 表现 |
|---|---|
| 默认 | `controlFace` 底，`ink2` 图标与文字，右上角圆点透明度 `0f` |
| 悬停 | 底色切为 `controlHover`，图标文字切 `ink1` |
| 按压 | 桌面语义无缩放，即时切换目标 Tab |
| 激活 / 选中 | `accentSoft` 底 + `accentBorder` 描边，图标着 `controlAccent`，右上圆点显现 |
| 禁用 | 不适用（核心四项常驻可用） |

**动画**：背景/文字颜色 `tween(140)`，激活圆点显隐 `tween(150)`。

**Compose 映射**：新建于 `DesktopRailBentoSections.kt`，遍历 `VeneraNavTab.entries`（跳过 `EXPLORE`）按 `chunked(2)` 渲染；点击直接调用既有 `onTabSelected(tab)` 与 `ux.hapticTap()`。

## 2. `DesktopRailFloatingDock` — 悬浮双层身份底坞

**解构**：外层 `Box(padding horizontal 8dp, bottom 8dp)` → 卡片 `Column(r12, controlFace, hairline 1dp, padding 6dp)` → 上层账号行 `Row`（头像 `24dp` + 在线点 `7dp` + 昵称/副文列 + 右箭头 `14dp`） → `1dp` 发丝分割线 → 下层功能行 `Row`（下载按钮带徽标 / 设置按钮 / 收起侧栏按钮）。

**视觉参数**：外圆角 `12.dp`，内按钮与账号行圆角 `8.dp`（同心圆）；下载进行中或有任务数显示 `controlAccent` 实底白字圆丸角标；保留 `LocalVeneraSideBarDownloadAnchor` 飞入坐标上报 `boundsInRoot`。

**状态**：

| 状态 | 表现 |
|---|---|
| 默认 | 账号行显示 `accountCard.name` 与 `accountCard.subtitle`（未登录显示「未登录 / 点按登录」）；功能钮透明底 + `ink2` 图标 |
| 悬停 | 账号行/按钮高亮 `controlHover`，图标切 `ink1` |
| 按压 | 无放大跳变，维持稳定桌面按压反馈 |
| 激活 / 选中 | 下载页选中或设置页选中时，对应操作钮亮 `accentSoft` 底 + `controlAccent` 图标 |
| 禁用 | 对应回调为空时不渲染该按钮 |

**动画**：角标弹出沿用既有 `anchorPopScale` 弹簧；按钮底色 `tween(140)`。

**Compose 映射**：新建于 `DesktopRailBentoSections.kt` 替代原 `MacFooterBar`；直接复用 `VeneraSideBarAccountCard`、`VeneraSideBarDownloadActivity`、`VeneraCover`（头像）与 `LocalVeneraSideBarDownloadAnchor`。

## 3. 单色二级导航行 (`MacNavRow`)

**解构**：`Row(height 31dp, r8)` → 15dp 单色线框 `Icon` → `10dp` 间距 → 单行标题 `Text`。

**视觉参数**：撤除原 `LightGroupBadgeIcon` 彩色背景方块，统一改为单色矢量图标（未选 `ink2`，选中 `controlAccent`）；选中行底色为 `accentSoft`（或轻玻璃底 + `accentBorder`），圆角 `8.dp`。

**状态**：

| 状态 | 表现 |
|---|---|
| 默认 | 透明底，`ink2` 图标与文字 |
| 悬停 | `controlHover` 底，`ink1` 文字与图标 |
| 按压 | 移除原有 0.96 缩放，符合桌面按压语义 |
| 激活 / 选中 | `accentSoft` 底 + `controlAccent` 图标 + `ink1` SemiBold 文字 |
| 禁用 | 无 |

**动画**：悬停/选中底色 `tween(140)`。

**Compose 映射**：收口在 `VeneraLightRailSkin.kt` 与 `DesktopRailBentoSections.kt`，不改动 `VeneraSideBarGroup` 与 `VeneraSideBarItem` 签名。

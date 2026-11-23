# 画廊搜索 UI 重做 — 设计 Token

规格名：`gallery-search-redesign-2026-11-21` · 日期：2026-11-21  
色值完全取自既有 `GalleryTokens.Color`（`Deep` / `Light`）与 `VeneraTokens` 对位，**不造新色**。  
标注口径：**【确定】= 规范文档 / 代码直接可读**；**【规格值】= 本方案新设定值**。

---

## 1. 全局颜色 Token

| Token | 浅色 (`Light`) | 深色 (`Deep`) | 用途 | 标注 |
|---|---|---|---|---|
| `Background` | `#F0F2F6` | `#0A0D12` | 页面底与透明指挥栏底 | 确定 · `GalleryTokens.kt:66,96` |
| `SurfaceLevel1` | `#FFFFFF` | `#12161F` | 浮层面板、筛选卡片、结果卡、选中分段滑块、聚焦输入框 | 确定 · `GalleryTokens.kt:67,99` |
| `SurfaceLevel2` | `#E8ECF2` | `#161B26` | 输入框默认槽、分段器轨道、二级按钮、日期槽 | 确定 · `GalleryTokens.kt:68,100` |
| `SurfaceLevel3` | `#DBE2EC` | `#1B2130` | 控件悬停态、输入框内嵌胶囊底 | 确定 · `GalleryTokens.kt:69,101` |
| `OutlineHairline` | `rgba(23,32,46,0.18)` | `rgba(255,255,255,0.06)` | 控件、卡片常态 1dp 细描边 | 确定 · `GalleryTokens.kt:71,108` |
| `OutlineHover` | `rgba(23,32,46,0.28)` | `rgba(255,255,255,0.16)` | 悬停描边、深色浮层外框描边 | 确定 · `GalleryTokens.kt:72,109` |
| `Divider` | `rgba(23,32,46,0.08)` | `rgba(255,255,255,0.08)` | 顶栏底线、抽屉右边界、浮层内部分隔线 | 确定 · `GalleryTokens.kt:74,111` |
| `AccentPrimary` | `#0A63F6` | `#4D9FFF` | 唯一强调色：主搜索钮、选中字色、焦点边框、开关开启态 | 确定 · `GalleryTokens.kt:85,110` |
| `AccentSoft` | `rgba(10,99,246,0.10)` | `rgba(77,159,255,0.16)` | 激活芯片、高级筛选开启底、菜单选中底 | 确定 · `GalleryTokens.kt:86` + 浅色对位 |
| `TextPrimary` | `#17202E` | `#E8ECF4` | 输入文字、主标题、选项正文 | 确定 · `GalleryTokens.kt:81,118` |
| `TextSecondary` | `#55617A` | `#98A2B6` | 次级按钮文字、未选中标签、副信息 | 确定 · `GalleryTokens.kt:82,119` |
| `TextTertiary` | `#8791A6` | `#6F7890` | 占位提示、节小标、元信息读数 | 确定 · `GalleryTokens.kt:83,120` |
| `ImageHairline` | `rgba(23,32,46,0.08)` | `Transparent` | 画师头像与作品图 1px 内嵌淡框 | 确定 · `GalleryTokens.kt:75,112` |
| `ToneDanger/Caution/Safe` | `#F28B82 / #F2B87C / #78D6C0` | 同左（常量） | 删除悬停预警、标签分类标识圆点 | 确定 · `GalleryTokens.kt:77-79` |
| *停用：半透明灰顶栏底板* | — | — | **本规格停用** `surfaceContainerHigh.copy(0.5f)` | 规格值 |

---

## 2. 尺寸 Token

| 件 | 值 | 标注 |
|---|---|---|
| 顶栏输入槽高 | `34.dp` | 规格值 |
| 顶栏输入槽宽 | `widthIn(min = 220.dp, max = 480.dp)` | 规格值 |
| 搜索页主指挥栏控件统一高 | `36.dp`（分段器、主搜索框、目标钮、搜索主钮、高级筛选钮） | 规格值 |
| 快捷条筛选芯片高 | `28.dp` | 规格值 |
| 联想/历史浮层宽度 | 顶栏：`widthIn(min = 380.dp, max = 560.dp)`；搜索页：`widthIn(min = 400.dp, max = 560.dp)` | 规格值 |
| 浮层内单行高度 | `34.dp`（历史与联想行）；`36.dp`（全年龄开关行） | 规格值 |
| 物理滑块开关尺寸 | `34.dp × 20.dp`（内圆滑块 `16.dp`） | 确定 · `DesktopGalleryTopSearch.kt:493` |
| 左侧高级筛选抽屉宽 | `284.dp` | 规格值 |
| 抽屉日期输入框高 | `32.dp` | 规格值 |
| 图标尺寸规范 | 主检索栏 `16.dp`；浮层行内 `14.dp`；清空/删除/箭头 `12.dp`；线条统一 `Outlined` | 规格值 + `README.md:335` |

---

## 3. 间距 Token

| 位置 | 值 | 标注 |
|---|---|---|
| 搜索页水平外边距 | `20.dp` | 确定 · `README.md:200` |
| 主指挥栏行内控件间距 | `10.dp` | 规格值 |
| 指挥栏行与快捷条行距 | `10.dp` | 规格值 |
| 浮层与输入槽垂直间隔 | `8.dp` | 确定 · `DesktopGalleryTopSearch.kt:348` |
| 浮层容器内边距 | `8.dp`（内部芯片流间距 `6.dp`） | 确定 · `DesktopGalleryTopSearch.kt:358,363` |
| 快捷条内芯片间距 / 组分隔线外距 | `6.dp` / `6.dp` | 规格值 |
| 高级筛选抽屉外边距与卡片间距 | 外距 `start = 20.dp, end = 14.dp, top = 14.dp`；卡距 `10.dp`；卡内距 `12.dp` | 规格值 |

---

## 4. 字阶 Token

| 元素 | 字号 / 字重 | 颜色角色 | 标注 |
|---|---|---|---|
| 主输入文本 / 列表项主名 | `13.sp` (`VeneraType.bodySecondary`) / Normal | `TextPrimary` | 确定 · `DesktopGalleryTopSearch.kt:310` |
| 主按钮「搜索」文字 | `13.sp` / SemiBold | `TextOnAccent` | 规格值 |
| 分段器 / 目标钮 / 高级筛选钮 | `12.5.sp` / Medium 或 SemiBold(选中) | `TextSecondary` / `AccentPrimary` | 规格值 |
| 筛选芯片 / Pop-up 下拉芯片 | `12.sp` / Medium 或 SemiBold(选中) | `TextSecondary` / `AccentPrimary` | 规格值 |
| 浮层节标题 / 作品计数（`tabular`） | `11.sp` (`VeneraType.micro`) / SemiBold(标题)·Normal(计数) | `TextTertiary` | 确定 + 规格值 |
| 抽屉分组标题 | `12.5.sp` / SemiBold | `TextPrimary` | 规格值 |

---

## 5. 圆角 Token（严格同心圆几何 `Outer = Inner + Padding`）

| 件 | 外层圆角 | 内距 | 内层圆角 | 关系 | 标注 |
|---|---|---|---|---|---|
| 顶栏输入槽 ↔ 内嵌范围胶囊 | `8.dp` | `4.dp` | `4.dp` | `8 - 4 = 4` | 规格值 |
| 域分段器 ↔ 选中滑块 | `10.dp` | `3.dp` | `7.dp` | `10 - 3 = 7` | 规格值 |
| 搜索页主按钮 / 输入框 / 下拉钮 | `10.dp` | — | — | 同行外轮廓对齐 | 规格值 |
| 下拉浮层 ↔ 内部站点芯片/列表行 | `12.dp` | `6.dp~8.dp` | `6.dp` | `12 - 6 = 6` | 规格值 |
| Pop-up 菜单容器 ↔ 菜单选项行 | `10.dp` | `4.dp~5.dp` | `6.dp` | `10 - 4 = 6` | 规格值 |
| 高级筛选分组卡片 ↔ 内部筛选芯片 | `12.dp` | `12.dp`(>6dp 视觉匹配) | `6.dp` | 卡片内小组件统一 `6.dp` | 规格值 |
| 画师结果卡片 / 作品墙卡 | `12.dp` / `10.dp` | — | 头像 `CircleShape` | 符合全站卡圆角档位 | 确定 · `GalleryTokens.kt:71-72` |

---

## 6. 阴影与动效 Token

| 项 | 值 | 标注 |
|---|---|---|
| 常规卡片阴影 | 深色 `0.dp`（无阴影）；浅色 `4.dp` 软影（`ShadowSpot = #101828`） | 确定 · `README.md:189` |
| 下拉浮层 / Pop-up 阴影 | 深色：`0.dp`（使用 `OutlineHover` 描边区分）或极微压暗；浅色：`vShadow(16.dp)` | 确定 · `README.md:334` |
| 浮层 / 菜单进离场 | `150.ms` fade + 轻微位移（`VeneraMotion` 快档家族） | 确定 · `DesktopGallerySearchScreen.kt:1263` |
| 抽屉开合过渡 | `expandHorizontally + fadeIn` (`220.ms`) / `shrinkHorizontally + fadeOut` (`180.ms`) | 确定 · `DesktopGallerySearchScreen.kt:353-366` |
| 悬停与按压 | 悬停即时切 `SurfaceLevel3` / `OutlineHover`；桌面语义默认无按压缩放，仅即时色阶反馈 | 确定 · `venera-design-flow` |

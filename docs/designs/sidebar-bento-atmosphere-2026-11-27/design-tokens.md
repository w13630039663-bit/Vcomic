# 侧栏个性化重做（方案一：Bento 磁贴 + 悬浮身份底坞）— 设计 Token

规格名：`sidebar-bento-atmosphere-2026-11-27` · 日期：2026-11-27  
全部取 `LocalChromePalette` 既有格位与全局令牌正典，**不造新色**。  
标注口径：**【确定】= 规范文档 / 代码直接可读**；**【规格值】= 本方案新设定值**。

## 1. 全局颜色 Token

| Token | 浅色 | 深色 | 用途 | 标注 |
|---|---|---|---|---|
| `glassTileFace` | `White @ 16%`（模糊开）/ `card`（实底） | `White @ 4.5%`（模糊开）/ `card`（实底） | Bento 未选磁贴极薄玻璃面（透出 DWM 实时模糊） | 规格值（2026-11-27 二轮） |
| `glassDockFace` | `White @ 20%` + `hazeBlur(26dp)` | `White @ 6%` + `hazeBlur(26dp)` | 悬浮双层底坞极薄玻璃面，叠加穿越背部的实时模糊 | 规格值（2026-11-27 二轮） |
| `glassSelectedFace` | `White @ 36%` | `White @ 13%` | 选中磁贴/胶囊高透玻璃面（不铺实色蓝底） | 规格值（2026-11-27 二轮） |
| `controlAccent` | `#0A63F6` (`palette.accent`) | `#8FB4FF` (`palette.accent`) | 选中边框（45%/55%）、指示圆点、图标与下载角标 | 确定 · 全站唯一装饰色 |
| `glassStroke` | 上 `White @ 62%` → 下 `20%` | 上 `White @ 16%` → 下 `5%` | 玻璃卡片亮高光渐变斜边 | 规格值（2026-11-27 二轮） |
| `hairline` | `palette.hairline` / `Black @ 7%` | `palette.hairline` / `White @ 10%` | 底坞内部分割线 | 确定 · `DesktopChromePalette` |
| `statusOnline` | `palette.ok` | `palette.ok` | 账号登录状态绿点 | 确定 · `VeneraLightRailSkin.kt:109` |
| `LightGroupBadgeIcon` 彩色方砖 | — | — | **本规格停用**，统一替换为单色线框矢量图标 | 规格值 |

## 2. 尺寸 Token

| 件 | 值 | 标注 |
|---|---|---|
| Bento 磁贴高 | `58.dp` | 规格值 |
| Bento 磁贴图标 | `17.dp` | 规格值 |
| 二级列表行高 | `31.dp` | 规格值 |
| 二级列表图标 | `15.dp` | 规格值 |
| 悬浮底坞账号头像 | `24.dp`（在线点 `7.dp`） | 规格值 |
| 底坞操作按钮高 | `30.dp`（图标 `16.dp`） | 规格值 |

## 3. 间距 Token

| 位置 | 值 | 标注 |
|---|---|---|
| Bento 网格行列间距 | `6.dp` | 规格值 |
| 展开区左右内距 | `8.dp` | 确定 · `VeneraLightRailSkin.kt:247` |
| 悬浮底坞内部边距 | `6.dp`（行间 `4.dp`） | 规格值 |
| 列表行内图标文字距 | `10.dp` | 确定 · `VeneraLightRailSkin.kt:817` |

## 4. 字阶 Token

| 元素 | 字号/字重 | 灰阶 | 标注 |
|---|---|---|---|
| 磁贴标签 | `12.5.sp` / SemiBold | 选中 `ink1`，未选 `ink2` | 规格值 |
| 分组标题 | `11.5.sp` / SemiBold | `ink3`（悬停 `ink2`） | 规格值 |
| 二级条目名称 | `12.5.sp` / 选中 SemiBold · 常态 Normal | 选中 `ink1`，未选 `ink2` | 规格值 |
| 底坞账号主标题 / 副标题 | `12.sp` SemiBold / `10.5.sp` Normal | `ink1` / `ink3` | 规格值 |
| 下载计数徽标 | `10.sp` / Bold | 白字（`accent` 胶囊底）+ tabular | 规格值 |

## 5. 圆角 Token（同心圆几何）

| 件 | 外层圆角 | 内层圆角 | 关系（外 = 内 + 内距） | 标注 |
|---|---|---|---|---|
| 悬浮底坞 | `12.dp` | `8.dp`（内部账号行与操作钮） | `12 = 8 + 4`（内距 `4~6dp` 收齐） | 规格值 |
| Bento 磁贴 | `11.dp` | — | 独立圆角玻璃块面 | 规格值 |
| 二级列表行 | `8.dp` | — | 与底坞按钮圆角对齐 | 规格值 |

## 6. 阴影与动效 Token

| 项 | 值 | 标注 |
|---|---|---|
| 阴影档 | `0.dp`（禁低 elevation `Modifier.shadow`，全靠半透玻璃 + `1.dp` `hairline` 分层） | 确定 · `notation-rules.md §3.1` |
| 分组折叠与颜色过渡 | `150ms` ~ `180ms` `FastOutSlowInEasing` | 确定 · `VeneraLightRailSkin.kt:378` |
| 按压反馈 | 桌面语义：悬停即时变色，点击无夸张缩放 | 确定 · `notation-rules.md §3.3` |

# 备份与还原 — 设计 Token

规格名:`backup-restore-rework-2026-11-20` · 日期:2026-11-20
全部颜色取 `VeneraTokens.color` 与 `StatusColors` 既有格位，**不造新色**。
标注口径:**【确定】= 规范文档 / 代码直接可读**;**【规格值】= 本方案新设定值**。

## 1. 全局颜色 Token

| Token | 浅色 | 深色 | 用途 | 标注 |
|---|---|---|---|---|
| `VeneraTokens.color.background` | 系统浅背景 | 系统深背景 | 页面根底 | 确定 · `README.md:305` |
| `VeneraTokens.color.surface` | 纯白卡片底 | 深灰一级面 | 资产指标卡、三大主工作区面板 | 确定 · `README.md:306` |
| `VeneraTokens.color.surfaceContainerLow` | 浅灰槽底 | 深灰二级面 | 拖拽区、输入槽、勾选行、状态条 | 确定 · `README.md:307` |
| `VeneraTokens.color.surfaceContainer` | 次浅悬停底 | 深灰三级面 | 清单行悬停、切换未选背景 | 确定 · `README.md:308` |
| `VeneraTokens.color.primary` | `#0A63F6` | `#0A84FF` | 主按钮、选中复选框、激活策略边框 | 确定 · 全站唯一装饰色 |
| `StatusColors` (`ToneSafe`) | `#128766` | `#78D6C0` | 数据库就绪点、解析成功、导入完成横幅 | 确定 · `README.md:318` |
| `StatusColors` (`ToneCaution`) | `#B4630B` | `#F2B87C` | 源账号安全提示、跳过条目提示条 | 确定 · `README.md:318` |
| `VeneraTokens.color.error` (`ToneDanger`) | `#C9302C` | `#F28B82` | 覆盖模式警示条、危险覆盖确认按钮 | 确定 · `README.md:318` |
| `outlineHairline` | `outlineVariant(0.10f)` | `outlineVariant(0.08f)` | 卡片与输入槽 1dp 细描边 | 确定 · `README.md:309` |

## 2. 尺寸 Token

| 件 | 值 | 标注 |
|---|---|---|
| 顶部资产指标卡最小高 | `84.dp` | 规格值 |
| 主按钮高度 | `36.dp` | 规格值 |
| 次级按钮高度 / 文本输入框高度 | `34.dp` | 规格值 |
| 图标主尺寸（按钮/标题/导航） | `16.dp` ~ `18.dp` | 确定 · `README.md:335` |
| 拖拽选包区最小高度 | `220.dp` | 规格值 |

## 3. 间距 Token

| 位置 | 值 | 标注 |
|---|---|---|
| 页面外边距（宽档/窄档） | `24.dp` / `16.dp` | 规格值 |
| 区域间纵向间距 | `18.dp` | 规格值 |
| 双栏列间距 | `18.dp` | 规格值 |
| 卡片内边距 | `16.dp` | 规格值 |
| 资产卡网格间距 | `10.dp` | 规格值 |
| 清单行/按钮内部元素间距 | `8.dp` | 确定 · `VeneraSpacing` |

## 4. 字阶 Token

| 元素 | 字号/字重 | 灰阶 | 标注 |
|---|---|---|---|
| 页面主标题 | `22.sp` / Bold | `onSurface` | 规格值 |
| 副标题 / 说明文字 | `12.5.sp` / Normal | `onSurfaceVariant` | 规格值 |
| 面板标题 | `14.5.sp` / SemiBold | `onSurface` | 规格值 |
| 指标卡大数字 | `20.sp` / Bold | `onSurface` | 规格值 |
| 清单标题 / 按钮文本 | `12.5.sp` / Medium~SemiBold | `onSurface` | 规格值 |
| 计数 / 路径 / 格式小字 | `11.5.sp` / Normal | `onSurfaceVariant(0.75f)` | 规格值 |

## 5. 圆角 Token（同心圆几何）

| 件 | 外层圆角 | 内层圆角 | 关系（外 ≈ 内 + 内距） | 标注 |
|---|---|---|---|---|
| 工作区主面板 | `14.dp` | `9.dp` ~ `10.dp` | 主面板内距 16dp，子容器取 9~10dp 收敛 | 规格值 |
| 资产指标卡 | `12.dp` | — | 独立紧凑卡片 | 规格值 |
| 按钮 / 输入框 / 徽标 | `8.dp` ~ `9.dp` | — | 与次级控件对齐 | 确定 · `VeneraShapeTokens` |
| 状态胶囊 | `999.dp` (Pill) | — | 全圆角胶囊 | 确定 · `VeneraShapeTokens` |

## 6. 阴影与动效 Token

| 项 | 值 | 标注 |
|---|---|---|
| 卡片阴影 | 深色 `0.dp`；浅色轻量卡片投影 | 确定 · `README.md:189` |
| 状态切换过渡 | `AnimatedContent` + `fadeIn/fadeOut` (`VeneraMotion.tweenFast()` 120ms) | 确定 · `VeneraMotion` |
| 按压反馈 | 桌面语义：无缩放，即时底色变化；悬停切换 `surfaceContainer` | 确定 · `venera-design-flow` |

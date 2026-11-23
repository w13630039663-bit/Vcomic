# 报错提醒与问题反馈系统 — 设计 Token

规格名:`error-feedback-system-2026-11-22` · 日期:2026-11-22
全部取 `LocalChromePalette` 既有格位与全局令牌正典，**不造新色**。
标注口径:**【确定】= 规范文档 / 代码直接可读**；**【规格值】= 本方案新设定值**。

## 1. 全局颜色 Token

| Token | 浅色 | 深色 | 用途 | 标注 |
|---|---|---|---|---|
| `controlAccent` | `#0A63F6` | `#0A84FF` | 主提交按钮、激活筛选、Tab 滑块、去反馈边框 | 确定 · 全站唯一装饰色 |
| `surfacePrimary` | `surfaceContainerHigh @ 0.84` / `surface` | `surfaceContainerHigh @ 0.78` / `elevated` | 胶囊、下拉面板、反馈面板主底 | 确定 · `glass-card-spec.md` |
| `surfaceNested` | `surfaceContainerHigh @ 0.74` | `surfaceContainerHigh @ 0.52` | 报错卡片、分段 Tab 底槽、截图区 | 确定 · `glass-card-spec.md` |
| `outlineHairline` | `outlineVariant @ 0.25` | `outlineVariant @ 0.25` | 浮层外框 1dp 描边 | 确定 · `glass-card-spec.md` |
| `errorSemantic` | `#D93838` | `#FF5C5C` | 胶囊红点、漫画报错标签、清空操作 | 确定 · HTML 方案稿 |
| `warnSemantic` | `#C97A00` | `#F5A623` | 图库报错标签 | 确定 · HTML 方案稿 |
| `sysSemantic` | `#5161E0` | `#7C8CFF` | 系统报错标签 | 确定 · HTML 方案稿 |

## 2. 尺寸 Token

| 件 | 值 | 标注 |
|---|---|---|
| 顶部胶囊高 | `28.dp` | 规格值 |
| 顶部胶囊最大宽（展开摘要 / 缩略角标） | `420.dp` / `84.dp` | 规格值 |
| 反馈入口按钮高 | `28.dp` | 规格值 |
| 报错下拉面板宽 / 最大高 | `540.dp` / `520.dp` | 规格值 |
| 反馈面板宽 / 最大高 | `440.dp` / `590.dp` | 规格值 |
| 分段 Tab 滑块高 | `30.dp` | 规格值 |

## 3. 间距 Token

| 位置 | 值 | 标注 |
|---|---|---|
| 反馈入口距最小化按钮 | `6.dp` | 规格值 |
| 面板内容内距 | `14.dp`（报错面板）/ `16.dp`（反馈面板） | 规格值 |
| 列表项间距 | `8.dp` | 规格值 |
| 同行小控件间距 | `6.dp` | 规格值 |

## 4. 字阶 Token

| 元素 | 字号/字重 | 灰阶 | 标注 |
|---|---|---|---|
| 面板标题 | `14.sp` / `SemiBold` | `textPrimary` | 规格值 |
| 报错卡片主标题 | `13.sp` / `SemiBold` | `textPrimary` | 规格值 |
| 报错人话原因 / 表单输入 | `12.sp` / `Normal` | `textSecondary` / `textPrimary` | 规格值 |
| 分类标签 / 计数 / 次级操作 | `10~11.sp` / `Medium~SemiBold` | `textTertiary` / 语义色 | 确定 · HTML 方案稿 |

## 5. 圆角 Token（同心圆几何）

| 件 | 外层圆角 | 内层圆角 | 关系（外 = 内 + 内距） | 标注 |
|---|---|---|---|---|
| 报错/反馈浮层面板与内嵌卡 | `16.dp` | `10.dp` | `16 = 10 + 6`（视觉内收） | 确定 · `glass-card-spec.md` |
| 分段 Tab 槽与滑块 | `10.dp` | `7.dp` | `10 = 7 + 3`（内距 `3.dp`） | 确定 · HTML 方案稿 |
| 胶囊与筛选 Chip | `999.dp` | — | 全圆角胶囊 | 规格值 |

## 6. 阴影与动效 Token

| 项 | 值 | 标注 |
|---|---|---|
| 浮层阴影 | `vShadow(12.dp, shape)`（暗色走玻璃描边层，不恢复 Skia 低投影） | 确定 · 桌面语义硬点 |
| 胶囊滑入/缩略过渡 | `240ms FastOutSlowInEasing` + `animateContentSize` | 规格值 |
| 下拉面板 / 反馈面板展开收起 | `220ms` 展开 / `160ms` 收起（`fade + scale(0.95->1) + slideY`） | 规格值 |
| 技术详情折叠 / 关联报错折叠 | `AnimatedVisibility(expandVertically + fadeIn)` `210ms` | 规格值 |
| Tab 滑块平移动画 | `animateDpAsState` `220ms` | 规格值 |
| 按压反馈 | 桌面语义：无缩放，悬停即时提亮底色与描边 | 确定 · 桌面语义硬点 |

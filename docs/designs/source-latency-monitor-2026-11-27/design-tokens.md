# 首页源状态收齐与站点延迟检测页 — 视觉 Token 定义

全局正典参见：[desktop-ui-standard-2026-11-05/README.md](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/README.md)、[glass-card-spec.md](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/glass-card-spec.md)、[controls-standard-spec.md](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/controls-standard-spec.md)。本表只补充本功能消费映射，不造新色与私自覆盖主标准。

## 1. 颜色 Token

| 语义角色 | Compose / Token 取值 | 用途 |
|---|---|---|
| 页面背景 | `VeneraTokens.color.surface` | 延迟检测独立页根底色 |
| 卡片 / 面板外框 | `DesktopGlassCardLevel.Nested`（圆角 `12.dp` / `10.dp`） | KPI 卡、工具条、明细行 |
| 主强调色 | `VeneraTokens.color.primary`（深 `#0A84FF` / 浅 `#0A63F6`） | 「全部重测」主按钮、链文字、测速中胶囊 |
| 状态 · 正常 | `GalleryTokens.Color.StatusSuccess`（底 `alpha = 0.16f`） | ≤800ms 延迟条、药丸、KPI 数字 |
| 状态 · 偏慢 | `GalleryTokens.Color.ToneCaution`（底 `alpha = 0.16f`） | 801~2500ms 延迟条、药丸、KPI 数字 |
| 状态 · 异常 / 未通 | `GalleryTokens.Color.StatusDanger`（底 `alpha = 0.16f`） | >2500ms 或请求失败延迟条、药丸、KPI 数字 |
| 状态 · 未测 | `tokens.color.outlineVariant.copy(alpha = 0.25f)` / `textTertiary` | 尚未开始探测时的进度槽与胶囊 |
| 辅助标签 | `tokens.color.surfaceContainerHigh.copy(alpha = 0.60f)` / `textSecondary` | 来源标（内置 / 社区）、需代理、未启用 |

## 2. 排版与字体 Token

| 层级 | Token | 用途 |
|---|---|---|
| 页面主标题 | `VeneraType.pageTitle` + `SemiBold` | 延迟检测页顶部标题「站点延迟检测」 |
| KPI 大数值 | `VeneraType.heroTitle` / `22.sp` + `SemiBold` | 四张概览卡主数值 |
| 卡片/行主标题 | `VeneraType.body` / `bodySecondary` + `SemiBold` | 站点名称 |
| 主机域名 | `VeneraType.micro` + `FontFamily.Monospace` + `textSecondary` | 探测目标 host 字符串 |
| 延迟毫秒数值 | `VeneraType.aux` + `tabular-nums` 样式等宽/对齐 | 延迟 `ms` 读数 |
| 状态药丸与标签 | `11.sp` / `10.sp`（微标） | 四态胶囊与来源标记 |

## 3. 间距、圆角与阈值常量

| 项目 | 参数 | 来源 / 约定 |
|---|---|---|
| 首页限高行数上限 | `HOME_SOURCE_STATUS_MAX_ROWS = 5` | 本规格锁定，确保与最近阅读卡齐平 |
| 页面外间距 | `28.dp` 水平 / `22.dp` 顶 / `32.dp` 底 | 对齐 `DesktopSourceManageScreen` |
| KPI 与行间距 | `12.dp` 栏间距 / `8.dp` 列表行间距 | 同心圆几何体系（行卡 `10.dp`，内部微标 `6.dp~999.dp`） |
| 延迟分档阈值 | `GOOD_MS = 800L`，`SLOW_MS = 2500L` | 确定 · `DesktopSourceLatencyProbe.kt` |
| 延迟进度条满刻度参考 | `1500f` ms 线性截断 `[0.08f, 1.0f]`（异常满格红条） | 规格值 · 对齐 `ui-mockup.html` |

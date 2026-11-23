# 备份与还原 — 设计分析

规格名:`backup-restore-rework-2026-11-20` · 日期:2026-11-20
标注口径:**【确定】= 规范文档 / 代码直接可读**;**【估算】= 观感推算**,禁止把估算伪装成确定。

## 1. 页面结构

- 根容器：`DesktopPageShell`（沿用桌面壳层 `VeneraTokens.color.background` 底，零平台 import）【确定 · `DesktopPageTemplates.kt:64`】
  - 页头区：单行主标题「备份与还原」+ 副标题「跨端数据迁移、本机备份与云端同步」+ 右侧状态胶囊（数据库就绪）与「打开数据目录」次级按钮【估算 · `ui-mockup.html`】
  - 提示条（可选）：最近一次操作结果或异常提示条，带关闭按钮【确定 · `DesktopBackupScreen.kt:50`】
  - 岛屿 A（顶部资产概览条）：6 个等宽统计卡横向网格，展示阅读历史、漫画收藏（含夹数）、插图收藏、画廊收藏与关注、阅读统计、屏蔽规则实时条数【估算 · `ui-mockup.html`】
  - 主工作台双栏区（`Row` 按 `0.92f : 1.25f` 分配，窄档自动堆叠为单列）：
    - 岛屿 B1（左栏上 · 打包导出卡）：标题与 `v5 互通格式` 徽标 + 导出覆盖范围 3×2 标签预览 + 「包含漫画源账号与设置」安全开关（含开启警示条）+ 导出主按钮【估算 · `ui-mockup.html`】
    - 岛屿 B2（左栏下 · WebDAV 云端同步卡）：标题与配置状态徽标 + 服务器地址 / 账号 / 密码（显隐切换）/ 远端目录四格输入表单 + 连接状态与测试按钮 + 「上传最新归档」与「拉取云端并预览」双操作按钮【估算 · `ui-mockup.html`】【确定 · `DesktopWebDavSync.kt:44-49`】
    - 岛屿 C（右栏 · 导入与还原工作区卡）：三态演化工作区：
      - 待选文件态：大号虚线拖拽与点击选包区 + 格式徽标 + 已暂存云端归档快捷解析条【估算 · `ui-mockup.html`】
      - 解析预览态：归档摘要头（文件名/来源应用/格式版本/导出时间/重选按钮）+ 冲突策略分段器（智能合并 vs 清空覆盖）+ 全选/全不选控制 + 7 项模块勾选清单 + 覆盖警示条 + 执行按钮【估算 · `ui-mockup.html`】
      - 完成报告态：成功横幅 + 6 格分项写入结果网格 + 异常/跳过明细条（无效正则与未安装源计数）+ 「继续导入其他文件」按钮【估算 · `ui-mockup.html`】

## 2. 区域尺寸与比例

| 区域 | 尺寸 / 占比 | 标注 |
|---|---|---|
| 内容区水平内距 | 28dp（宽档）/ 16dp（窄档） | 确定 · `ui-mockup.html` / `VeneraTokens.spacing` |
| 顶部资产概览条 | 6 列等宽（`weight(1f)`），间距 10dp，卡高自适应（min 84dp） | 估算 · `ui-mockup.html` |
| 主工作台双栏比例 | 左栏 `weight(0.92f)` / 右栏 `weight(1.25f)`，列间距 18dp | 估算 · `ui-mockup.html` |
| 左栏导出范围标签格 | 3 列×2 行，格间距 7dp | 估算 · `ui-mockup.html` |
| 右栏拖拽选包区 | 满宽，最小高度 220dp，内边距 24dp | 估算 · `ui-mockup.html` |

## 3. 视觉层次

- **表面三级递进**：页面底 `background` → 主面板/资产卡 `surface`（14dp/12dp 圆角）→ 内嵌槽位/输入框/清单行 `surfaceContainerLow` → 悬停与开关轨道 `surfaceContainer`【确定 · `desktop-ui-standard-2026-11-05/README.md:305-308`】。
- **深浅模式区分**：
  - 深色模式：无阴影（0dp），所有卡片与输入槽靠 `1dp` 细描边 `outlineVariant.copy(alpha = 0.08f)` 与背景色阶差区隔；
  - 浅色模式：卡片采用纯白面色 + 1dp 细描边 + 轻度卡片阴影，控件槽使用浅灰底区隔【确定 · `desktop-ui-standard-2026-11-05/README.md:189`】。
- **无裸玻璃模糊**：桌面桥钉死 `SurfaceMaterial.SOLID`，不使用高斯模糊【确定 · `DesktopThemeBridge.kt:72`】。

## 4. 逐元素设计参数

| 元素 | 颜色 | 字阶 | 圆角 | 其他 | 标注 |
|---|---|---|---|---|---|
| 页头主标题 | `onSurface` | 22sp / Bold | — | ≤ 6 字 | 确定 · `desktop-ui-standard-2026-11-05/README.md:343` |
| 页头副标题 | `onSurfaceVariant` | 12.5sp / Normal | — | ≤ 15 字 | 确定 · `desktop-ui-standard-2026-11-05/README.md:343` |
| 资产指标卡数值 | `onSurface` | 20sp / Bold | 12dp | tabular figures | 估算 · `ui-mockup.html` |
| 工作区主卡片 | `surface` | — | 14dp | padding 16dp，1dp 描边 | 估算 · `ui-mockup.html` |
| 内嵌输入槽/清单行 | `surfaceContainerLow` | 12.5sp / Medium | 9dp | 悬停切 `surfaceContainer` | 估算 · `ui-mockup.html` |
| 主行动按钮 | `primary` / `onPrimary` | 13sp / SemiBold | 9dp | 高 36dp | 确定 · `desktop-ui-standard-2026-11-05/README.md:313` |
| 危险覆盖按钮 | `error` / `onError` | 13sp / SemiBold | 9dp | 高 36dp，需二次确认 | 确定 · `DesktopBackupScreen.kt:41` |

## 5. 响应式视口规则

| 视口档 | 断点 | 重排规则 |
|---|---|---|
| 宽档 | ≥ 1040dp | 顶部资产条单行 6 列；主工作台左右双栏并行（`0.92f : 1.25f`）；WebDAV 表单双列 |
| 中/窄档 | < 1040dp | 顶部资产条折为 3 列×2 行（极窄 < 640dp 折为 2×3）；主工作台堆叠为单列纵向排列 |

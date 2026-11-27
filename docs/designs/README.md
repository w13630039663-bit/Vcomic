# UI 设计稿执行与动效规范（操作手册）

本文件收纳从 `AGENTS.md` 外移的设计稿执行细节，作为大型 UI 重做、视觉规范与组件对照的权威操作手册。

## 1. 设计稿执行流程

1. **方案先行**：大型 UI 重做先出 HTML 方案稿（放至 `desktop/docs/designs/`），获批后再进入代码实现；小型布局或样式调整可直接改。
2. **深度逆向分析（三件套归档）**：依据设计稿或 HTML 方案稿实现前，必须先完成视觉解构并将三件套归档至 `desktop/docs/designs/<spec-name>/`，三件齐全并归档前禁止写业务代码：
   - `design-analysis.md`：页面结构、区域尺寸与比例（解耦岛屿 / 容器）、视觉层次（毛玻璃 / 描边 / 高斯模糊）、各元素设计参数（确定值与估算值严格标注，禁止伪装）、响应式视口规则。
   - `design-tokens.md`：全局与组件级的颜色 / 尺寸 / 间距 / 圆角（`Outer = Inner + Padding` 同心圆几何法则）/ 字阶 / 阴影微光 / 动效手势 Token。
   - `component-spec.md`：组件内部解构、视觉参数、状态变化（默认 / 悬停 / 按压 / 激活）、动画要求（按压 0.96 弹性、150ms 反馈）、Compose 实现映射。
3. **高保真验收**：设计稿是验收目标，不是参考建议。逐区域全量实现，保留原有业务逻辑、数据来源、状态管理与功能入口，不擅自删减，不另起方案。完成后逐项核对设计稿区域与渲染结果，确保编译与相关测试全绿。

## 2. 动效与桌面标准件

1. **卡片入场单源**：统一使用 `VeneraMotion` 的 `VeneraCardEnter` 或 `rememberCardEnterProgress`，禁止散写错峰、时长与弹簧参数。
2. **页面级进出场**：一律使用 `VeneraMotion` 预设（`VeneraPageEnter` / `VeneraPageExit` / `VeneraPagePopEnter` / `VeneraPagePopExit` / `VeneraSheetEnter` / `VeneraSheetExit`）。
3. **令牌单源**：动画时长 / 缓动 / 位移令牌只认 `VeneraMotion.kt`（新写 UI 不落裸数，存量屏不追溯）。
4. **多状态按钮**：新增按钮必须覆盖默认 / 悬停 / 按压 / 焦点 / 禁用态过渡（异步操作另加加载态）。
5. **桌面端标准件**：基础控件与多选栏一律走唯一标准件：`DesktopButton` / `DesktopPushButton`（40/32/26dp）、`DesktopSwitch`、`DesktopSearchField` / `DesktopInputField`、`DesktopSegmented`、`DesktopPopUpButton`、`DesktopSelectionDock`（Haze 模糊透明）、`DesktopGlassCard`；共享层走 `VeneraButton` 等对应件。自拼可点面须挂 `VeneraPressFeedback.indication` 并自绘悬停过渡。详细规格见 `desktop-ui-standard-2026-11-05/controls-standard-spec.md` 与 `button-state-spec.md`。

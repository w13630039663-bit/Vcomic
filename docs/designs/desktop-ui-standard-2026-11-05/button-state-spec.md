# 按钮状态与过渡规格

> **性质**：硬规格。新增 UI 按钮的验收依据，与 [`AGENTS.md`](../../../AGENTS.md)「动效统一」节第 4、5 条配套。
> **适用范围**：`:desktop` 模块全部 Compose UI，包括按钮、图标钮、文字钮、胶囊、分段钮、下拉钮、可点卡面。
> **上游来源**：[desktop-ui-standard README §4.4 组件状态契约](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/README.md)。本文件把那份契约细化为可执行清单，不另建一套口径。
> **存量口径**：本规格只管新增与重做的界面；存量屏不追溯，哪屏动到哪屏补。

---

## 1. 强制的状态清单

新增按钮至少具备下表前五态；有异步动作的必须补加载态。**少一态即不合格**。

| 状态 | 何时必须有 | 视觉变化（择一或组合） | 过渡 |
|---|---|---|---|
| 默认 | 恒有 | 基准底色 / 描边 / 文字色 | — |
| 悬停 | 恒有（桌面鼠标环境） | 底色抬一档、描边转 `OutlineHover`、或图标加亮 | 颜色过渡，吃 `VeneraMotion.tweenFast()` |
| 按压 | 恒有 | 等比缩到 `0.96`（小件）或 `0.94`（图片面 / 大面积），可叠 alpha | 弹簧，用统一件内部参数，不另写 |
| 焦点 | 恒有（键盘可达） | 可见焦点环 / 描边加亮 | 颜色过渡 |
| 禁用 | 可禁用时必须有 | 文字降到三级色 + 降 alpha，**按压反馈关闭** | 颜色过渡 |
| 激活 / 选中 | 有选中语义时 | 主色描边或表面抬到 `SurfaceLevel3` + 字重加粗 | 颜色过渡 |
| 加载中 | 点击后触发异步动作时 | 钮内换进度指示或图标自转，交互锁住 | 显隐过渡，禁瞬切 |
| 完成 / 失败 | 一次性反馈动作（复制、下载、收藏） | 短暂图标或文案替换后回落 | 进出一组过渡 + 停留时长 |

## 2. 统一件清单（优先复用，禁止重复造）

| 件 | 定义 | 自带状态 |
|---|---|---|
| `DesktopButton` / `DesktopPushButton` | `ui/DesktopMacControls.kt` | 桌面端核心标准按钮（支持 `Large 40dp / Medium 32dp / Compact 26dp` 与 `Primary / Secondary / Danger / Ghost`），含默认 / 悬停 / 按压(0.96) / 禁用 / 选中 / 加载平滑过渡，详见 [controls-standard-spec.md](controls-standard-spec.md) |
| `VeneraButton` / `VeneraIconButton` / `VeneraTextButton` | `components/venera/VeneraControls.kt` | 仅共享层使用；默认 / 悬停 / 按压 / 禁用由 miuix 与 M3 后端提供 |
| `VeneraChip` | `components/venera/VeneraChip.kt` | 默认 / 悬停 / 按压（`chipPressedScale`）/ 焦点 / 选中 |
| `VeneraFilterPill` | 同名文件族 | 默认 / 悬停 / 选中 |
| `DesktopSegmented` | `ui/DesktopMacControls.kt` | 桌面端唯一分段选择框，含悬停 / 选中与色点过渡 |
| `DesktopPopUpButton` | `ui/DesktopMacControls.kt` | 桌面端唯一下拉选择器，含悬停 / 展开 |
| 可点卡面 | `VeneraCard` / `DesktopGlassCard` | 经 `pressFeedback` 透传 `VeneraPressFeedback.card` |
| 自拼可点面 | `Modifier.clickable` / `combinedClickable` | 必须挂 `VeneraPressFeedback.indication` 并自绘悬停过渡 |

## 3. 禁例（出现即打回）

1. 裸 `Box` / `Row` 加 `clickable` 当按钮，无底色、无描边、无按压反馈 —— 读起来像纯文字，用户不知道能点。
2. 只有单态：按下、悬停屏幕纹丝不动。
3. 状态瞬切：`if (hovered) A else B` 直接换值，没有 `animateColorAsState` / `animateFloatAsState`。
4. 假开关：能按但什么都不发生，或异步行已在进行却仍可重复触发。禁用态必须真禁，不能只改颜色。
5. 禁用态仍播按压反馈（miuix `Card` 挂 `pressFeedback` 后按态无条件生效，必须显式退回 `PressFeedbackType.None`）。

## 4. 过渡取值

- 时长 / 缓动 / 位移一律吃 [`VeneraMotion`](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/compose/ui/tokens/VeneraMotion.kt)：微反馈走 `durationFast`，常规状态切换走 `durationNormal`。不落裸 `tween(150)`。
- 按压缩放两档：小件 `VeneraTokens.chipPressedScale`（0.96）；图片面与大卡走 `VeneraPressFeedback`（`Sink`，0.94）。两档各管各的尺寸，不互相挪用。
- 图标自转、加载指示等循环动画按各域既有实现，不新造曲线。

## 5. 提交前自查

1. 这个钮有哪几个状态？逐个数出来。
2. 每个状态的切换有没有过渡，用的是哪档令牌？
3. 异步动作期间，钮是否锁住并有加载表现？
4. 禁用时按压反馈是否真的关了？
5. 能不能换成统一件？能换就不许自拼。

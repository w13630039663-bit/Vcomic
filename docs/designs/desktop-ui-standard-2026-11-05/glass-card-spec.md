# 全站透明玻璃卡片规范（源自站点页卡片设计）

> **状态**：已定稿生效（2026-11-22）
> **适用范围**：`:desktop` 全部**新建或重做的功能与信息页面**（默认卡片载体）；历史旧页面不强制立即迁移
> **共用原语**：`desktop/src/main/kotlin/com/venera/desktop/ui/components/DesktopGlassCard.kt`（`DesktopGlassCard`、`DesktopGlassCardDefaults`、`DesktopGlassCardLevel`）

---

## 1. 材质分级与 Token 口径

卡片分为两个结构层级，均禁止额外叠加低 elevation `Modifier.shadow`，且必须按 **`.background(...)` → `.border(...)` → `.clip(shape)`** 的顺序应用，防止 `1.dp` 描边被圆角裁剪掉一半。

### 1.1 透底玻璃档（`DesktopBackdropMode.isTransparentActive() == true`）

| 层级 | `DesktopGlassCardLevel` | 默认底色 | 悬停底色 | 默认描边 | 可交互悬停描边 | 推荐圆角 |
|---|---|---|---|---|---|---|
| 一级主卡 | `Primary` | `surfaceContainerHigh @ 0.45` | `surfaceContainerHigh @ 0.60` | `1.dp outlineVariant @ 0.25` | `1.dp primary @ 0.38` | `16.dp` |
| 二级内嵌块 | `Nested` | `surfaceContainerHigh @ 0.35` | `surfaceContainerHigh @ 0.50` | `1.dp outlineVariant @ 0.18` | `1.dp primary @ 0.35` | `10.dp`（或随同心圆公式） |

### 1.2 实底对比档（用户关闭底板模糊 / `isTransparentActive() == false`）

当用户在「通用与外观」关闭底板模糊进入实色模式时，原语自动抬高不透明度以保持明暗对比度与层级边界：

| 层级 | `DesktopGlassCardLevel` | 默认底色 | 悬停底色 | 默认描边 | 可交互悬停描边 |
|---|---|---|---|---|---|
| 一级主卡 | `Primary` | `tokens.color.surface` | `tokens.color.surfaceContainerHigh` | `1.dp outlineVariant @ 0.22` | `1.dp primary @ 0.42` |
| 二级内嵌块 | `Nested` | `tokens.color.surfaceContainerHigh` | `tokens.color.surfaceVariant` | `1.dp outlineVariant @ 0.14` | `1.dp primary @ 0.38` |

---

## 2. 使用与约束

1. **直接复用共用原语**：新建或重做功能页的卡片容器直接调用 `DesktopGlassCard(...)`；非 `Column` 容器（`Row`、`Box`、输入框槽）调用 `DesktopGlassCardDefaults.surfaceModifier(...)` 或 `backgroundColor(...)` / `borderColor(...)`，禁止再手写实色 `tokens.color.surface` + `Modifier.shadow(...)`。
2. **嵌套同心圆几何**：一级主卡内嵌二级子卡/输入槽时，满足 `外层圆角 = 内层圆角 + 内边距`，避免角对角打架。
3. **无缩放按压语义**：卡片悬停通过即时底色/描边提亮反馈，按下不做弹性缩放。
4. **不在适用范围的组件**：浮层弹窗、右键菜单、作品封面图卡继续沿用各自原有遮罩/浮层或图卡规范，不套用此透明度规则。

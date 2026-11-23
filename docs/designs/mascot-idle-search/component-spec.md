# 薇薇待机态 · 组件规格与 Compose 映射

## 组件

- 新件 `DesktopMascotIdle`（`ui/components/`），`internal`。
- 参数：`awakeKey: Any?`（变化即重置打盹计时）、`modifier`（调用点传空态全区的命中/视线范围）、`size: Dp`、`spec: DesktopMascot.Spec`、`focusedListening: Boolean`、`prompt`（伴随提示语槽，形状与文案整体居中）。
- 装饰件不进语义树；轻点用 `DesktopNoIndication`。

## 设置区（主人追加口径）

- 存储件 `DesktopMascot`（`settings/`，键前缀 `pref_desktop_mascot_*`，`restore` 挂壳启动）。
- 设置页「吉祥物」卡：状态模式覆写（自动/安静待机/我在听/打盹）、待机透明度 0.12–0.60、图标尺寸 40–96dp、呼吸周期 2.5–8s、呼吸幅度 0.4–2.0x、打盹延迟 3–20s、视线跟随开关、重置。
- 打盹口径变更（主人令）：聚焦「我在听」不再豁免闲置计时——待机态与输入框聚焦态，长时间无互动都打盹。
- 调参抽屉里的演示控件（模拟搜索、历史标签、Toast）不进应用。

## 状态机（内部私有类，数值只认 design-tokens.md）

- 字段：呼吸相位/当前周期、透明度、下沉、眼高系数、打盹混合、视线 x/y、眨眼与低头子状态、活动时间戳。
- 每帧 `tick(dt)`：呼吸相位累加 → 目标值 → 阻尼平滑 → 写几何。
- 眨眼优先级最高；打盹时禁眨眼与低头；低头看书 = 眨眼后 35% 概率延迟 520ms 触发，视线 (±0.48, 0.72)，时长 1.8–3s。
- 随机源用 `Random`，首帧即静置形状（无空白帧）；周期切换走相位累加器（无收尾跳变）。

## 绘制映射

- `Canvas` + `graphicsLayer { compositingStrategy = Offscreen }`，几何按 100 视口坐标等比缩放。
- 四件形状（V 臂折线描边、双翼 Q 描边、身体填充）→ 同色 `textPrimary.copy(alpha)`。
- 眼胶囊与底部缺口 → `BlendMode.DstOut` 抠穿（等价 HTML mask）。
- 每帧只动 Path 参数与两枚镂空件坐标；Path 实例复用不重建。

## 交互映射

- `pointerInput`：指针移动/按压 → 记录归一化视线 + 重置打盹计时。
- `clickable`（无涟漪）：打盹唤醒 / 清醒慢眨眼。
- 调用点：`DesktopSearchScreen` 空态分支 Column（吉祥物 + 原提示语，间距 18）；`awakeKey = query`。

## 验收对照

- 形状与镂空、呼吸/眨眼/打盹/视线/低头全部按稿；调参面板、演示搜索、历史标签、Toast 不进应用。
- 原空态文案与测试断言不变。

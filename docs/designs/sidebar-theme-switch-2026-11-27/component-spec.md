# 侧栏星穹天气昼夜切换开关与全窗转场 — 组件规格

规格名：`sidebar-theme-switch-2026-11-27` · 日期：2026-11-27 · 配套：`proposal.md` / `design-analysis.md` / `design-tokens.md`  
实施文件（待执行）：
- `desktop/src/main/kotlin/com/venera/desktop/ui/DesktopRailThemeSwitch.kt`（新建）
- `desktop/src/main/kotlin/com/venera/desktop/ui/DesktopThemeRevealHost.kt`（新建）
- `desktop/src/main/kotlin/com/venera/desktop/settings/DesktopRailWeatherService.kt`（新建）
- `desktop/src/main/kotlin/com/venera/desktop/settings/DesktopThemeMode.kt`（扩展全窗特效与天气偏好持久化，或拆分）
- `desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt` / `desktop/src/main/kotlin/com/venera/desktop/app/DesktopLargeScreenChrome.kt` / `desktop/src/main/kotlin/com/venera/desktop/settings/DesktopSettingsCardsGallery.kt` / `desktop/src/main/kotlin/com/venera/desktop/app/DesktopStartupBootstrap.kt`
- 对应单测：`DesktopRailWeatherPolicyTest.kt`（新建）

## 1. `DesktopRailThemeSwitch` — 侧栏星穹实时天气昼夜开关

**解构**：
- 展开态：`Column(fillMaxWidth, padding(horizontal = 8.dp))` → 右对齐微角标行（`跟随系统` 小胶囊） → `Spacer(4.dp)` → `Box(fillMaxWidth, height = 48.dp, clip(12.dp))`（内含全尺寸 `Canvas` 天象画布 + 镜面斜光扫边 + 左右错位文字层 + `1.dp` 高光斜边描边）。
- 折叠态：`Box(size = 40.dp, clip(11.dp))`（内含精简天象 `Canvas` 与描边）。

**视觉参数**：全部引用 `design-tokens.md` 定义的 `sky*`、`sunBody`、`moonBody`、`12.dp / 11.dp` 圆角及 `desktopRailGlassStrokeBrush`。

**状态**：

| 状态 | 表现 |
|---|---|
| 默认（白天 / 黑夜） | 浅态显示左炽日 + 右端「晴空 · 浅色 / 城市 温度」；深态显示右弯月星芒 + 左端「星夜 · 深色 / 城市 温度」 |
| 悬停 | 触发柔和微外光晕；若为雨/雪天气，唤醒轻柔细雨微丝与飘雪粒子 60fps 流动 |
| 按压 | 按下微缩 `0.975f` 蓄力 feedback（属于明确标注的特色动效例外场合） |
| 切换进行中 | `progress` 驱动三段暮色天幕 + 太阳/弯月抛物线轨迹 3 帧残影 + 地平线脉冲 + 落位 12 星尘溅射 + 镜面扫光 |
| 跟随系统激活 | 上方微角标高亮 `accent` 底 + 白色文字；主开关显示当前系统实际解析的明暗，点按主开关则切换并退出「跟随系统」 |

**动画**：主进度 `Animatable(if (dark) 1f else 0f)`，`720ms` 线性基准配合内部各子阶段缓动曲线；`VeneraMotion.reduceMotion` 为真时直接 `snapTo` 目标态。

**Compose 映射**：新建 `DesktopRailThemeSwitch.kt`，复读 `DesktopThemeMode.mode` 与 `DesktopRailWeatherService.snapshot`；通过 `LocalDesktopPrefsStore` 或外传回调完成 `DesktopThemeMode.write` 持久化，不修改 `VeneraSideBar` 共享签名。

## 2. `DesktopThemeRevealHost` — 全窗 GPU 离屏截帧转场容器

**解构**：`Box(fillMaxSize)` → 底层实时窗口内容（挂载 `Modifier.drawWithContent { graphicsLayer.record { this@drawWithContent.drawContent() }; drawLayer(graphicsLayer) }`） + 顶层条件组合揭幕层 `Canvas(fillMaxSize)`（仅在切换动画 `0 < t < 1` 期间存在，完成后销毁释放纹理）。

**视觉参数**：覆盖三种可选全窗转场：
1. `星穹冲击波`：以记录到的点击坐标（或默认左下侧栏开关中心）为圆心，在离屏层用 `BlendMode.DstOut` 挖出羽化扩张圆孔，并绘制 `28.dp` 极光/炽金主光环 + 延迟回声波 + 36 颗拖尾飞溅星尘粒子；
2. `极光裂隙`：38° 对角中心缝向两侧推开揭幕并绘制炫光刀锋；
3. `螺旋星轨`：360° `SweepGradient` / 扇形角度擦除旧纹理。

**动画**：`Animatable(0f)` → `animateTo(1f, tween(760, LinearEasing))`，播完立刻释放捕获的 `ImageBitmap` 防止内存常驻。

**Compose 映射**：新建 `DesktopThemeRevealHost.kt`，通过 `CompositionLocalProvider(LocalDesktopThemeTrigger)` 向侧栏或任意主题按钮提供 `triggerThemeToggle(originInRoot: Offset?, targetMode: String)`，先同步截帧旧画面再改 `DesktopThemeMode`。

## 3. `DesktopRailWeatherService` 与设置项 — 位置天气服务

**解构**：单例热流 `StateFlow<DesktopRailWeatherSnapshot>` + 后台协程拉取器 + 设置中枢「主题」卡片内新增天气联动选项与手动城市对话框/输入行。

**行为契约**：
- 支持模式：`关闭` / `自动定位（IP）` / `指定城市`（默认建议「自动定位」，允许设置中一键关闭或填写自定义城市名以规避代理 IP 漂移）。
- 缓存与降级：结果落盘 `JsonKeyValueStore`，30 分钟内复用缓存不重复发请求；请求失败静默保留上次有效天气或降级为 `CLEAR`（晴朗），不发任何报错弹窗。
- WMO 天气代码映射为 5 档：`CLEAR` / `CLOUDY` / `RAIN` / `STORM` / `SNOW`。

# VComic 桌面端安装器 v3 — 组件规格

规格名：`installer-wizard-v3-2026-11-22` · 日期：2026-11-22 · 配套：`design-analysis.md` / `design-tokens.md`
实施文件（待落码）：`desktop/src/main/kotlin/com/venera/desktop/installer/` 下 `InstallerSpec.kt`、`InstallerTimeline.kt`、`InstallerEmblem.kt`、`InstallerScenes.kt`、`InstallerChrome.kt`、`InstallerCopy.kt` 及对应单测。

## 1. InstallerEmblem — 31 片生长与原版 VComic 图标

**解构**：白底圆角徽章框 → Canvas 绘制 31 段连续粗圆笔分段（V 字左/右臂 13 段 + 左书页弧 9 段 + 右书页弧 9 段）→ 800ms 完整接管原版矢量轮廓（保留中央书脊白缝）。

**视觉参数**：Splash 尺寸 `76.dp`（圆角 `20.dp`），收拢尺寸 `52.dp`（圆角 `14.dp`），底色 `iconMark` 白底 `#FFFFFF`，图形 `#0B0D11`。

**状态**：
| 状态 | 表现 |
|---|---|
| 0ms 初始 | 徽章底已就位（无空白帧），31 段从中心依次生长 |
| 800ms 生长完成 | 31 段全部落位，无缝贴合原版书脊白缝轮廓 |
| 收拢态 | 平滑缩放至 52dp 并左移进入顶部水平品牌头 |

**动画**：800ms 错峰生长，随后 300ms 收拢。

**Compose 映射**：重写 `InstallerEmblem.kt`，将原盾牌路径替换为 31 段采样路径 + 原版 `VComic` 摊开书轮廓。

---

## 2. SplashAndReadyBrandHeader — 启动与选项共享品牌头

**解构**：居中 Column（Splash）平滑过渡为水平 Row（Ready）：`InstallerEmblem` + `VComic` 字标 + `v2.1.1` 胶囊 + 副标题。

**视觉参数**：字标 `24.sp → 20.sp`，`accentSoft` 版本胶囊。

**状态**：
| 状态 | 表现 |
|---|---|
| Splash (0~800ms) | 仅徽章居中生长，字标透明 |
| 字标入场 (800~1100ms) | 字标 `scale(0.92f -> 1f)` + 淡入 |
| 收拢态 (1250~1550ms) | 300ms 向上收拢为顶部品牌头，下方选项卡片同步浮现 |

**动画**：`InstallerTimeline` 新增 `brandSettleProgress(elapsedMs)` 纯函数控制 1250~1550ms 过渡。

**Compose 映射**：重构 `InstallerScenes.kt` 中 `SplashScene` 与 `OptionsScene` 共享顶栏或过渡逻辑，保持与 `InstallerTimeline` 单测一致。

---

## 3. ReadyOptionsPanel — 安装选项与 300ms 展开目录面板

**解构**：可选「覆盖升级」警告横幅 → 摘要卡片（目标路径预览 + 数据隔离说明） → `AnimatedVisibility` 300ms 展开面板（路径输入 + 浏览按钮 + 快捷方式勾选 + 数据目录提示） → 底部操作行（「自定义目录」幽灵按钮 + 「立即安装」主按钮）。

**视觉参数**：摘要卡片与展开面板采用 `surface` + `borderSubtle`，圆角 `12.dp`；按钮按压统一 `120ms` `scale(0.97f)`。

**状态**：
| 状态 | 表现 |
|---|---|
| 默认折叠 | 仅显示摘要卡片与操作行 |
| 展开目录 | 300ms 平滑展开路径输入框与复选框 |
| 覆盖升级模式 | 顶部显示琥珀色安全升级横幅，主按钮文案为「立即升级」 |

**动画**：300ms 高度/透明度展开，按钮按压 120ms `0.97f`。

**Compose 映射**：映射至 `InstallerScenes.kt` 的 `OptionsScene` 与 `InstallerChrome.kt` 按钮控件。

---

## 4. InstallingCarouselStage — 2s ±33 浮动与 6s 五幕介绍轮播

**解构**：安全净空视口（`274.dp`）→ 外层 `FloatRig`（2s 周期 `±33.dp` 纵向往返） → 内层 `SlideTrack`（5 幕矢量玻璃插画 + 主标 ≤6 字 + 副标 ≤15 字） → 可点击指示点 → 底部 12s 单调进度卡片。

**视觉参数**：插画 `196×114.dp`，进度条高 `6.dp`，颜色为 `accent`。

**状态**：
| 状态 | 表现 |
|---|---|
| 自动轮播 | 每幕 `6000ms`（1s 入 / 4s 持 / 1s 出），与外层浮动解耦不重置相位 |
| 点击指示点 | 立即切换至对应幕并重置 6s 计时 |
| 12s 进度推进 | 单调曲线推进至 100%，自动转入完成幕 |

**动画**：`bobOffsetDp`（2s ±33）、`sceneAlpha`（6s 1s/4s/1s）、`installProgress`（12s 严格单调）。

**Compose 映射**：扩充 `InstallerCopy.kt` 为 5 幕文案，`InstallerTimeline.kt` 调整 `sceneIndex` 模数为 5，`installProgress` 更新为 `0.72 * smoothstep + 0.28 * t`。

---

## 5. LifecycleExtendedScenes — 四组全生命周期扩展态与取消弹窗

**解构**：
1. `MigrationScene`（旧版 MSI 迁移确认 + 数据保留说明）
2. `ConflictScene`（运行中进程冲突提示 + 关闭继续）
3. `ErrorScene`（磁盘不足 / 权限受限 / 文件占用三态切换 + 回退重试）
4. `UninstallScene`（修复程序 / 卸载 VComic 双选卡片）
5. `CancelConfirmModal`（安装中点关闭触发的内嵌毛玻璃确认遮罩）

**视觉参数**：统一 `60.dp` 状态徽章（`warn` / `accent` / `danger`）+ `surface` 详情卡 + 标准操作行；弹窗宽 `380.dp`，带背景遮罩 `scrim`。

**状态**：与主流程共享同一云母窗口与 6s/10s 环境光层，切幕无节点重建。

**动画**：切幕 `260ms` 淡入微移；取消弹窗 `220ms` 遮罩淡入 + `240ms` `scale(0.95f -> 1f)`。

**Compose 映射**：在 `InstallerChrome.kt` / `InstallerScenes.kt` 中补充对应状态枚举与 Compose 场景。

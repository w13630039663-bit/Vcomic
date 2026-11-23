# 安装器向导

> 2026-11-22 · 桌面端安装器首版（UI 复刻分析见 `designs/installer-wizard-2026-11-22/`）

## 1. 背景与目标

- 解决痛点：现有 Inno 安装器是系统默认外观，与产品视觉脱节；安装过程无进度叙事，用户对"装了什么、装到哪里"缺少感知。
- 核心目标：给桌面端一套自绘安装向导，窗口 640×440、云母玻璃卡片；启动有徽记逐帧绘制与环境光漂移，安装中有插画浮动与文案轮播，完成后落到"开始使用"。
- 2026-11-22 升级：已接入真实安装/卸载引擎（`InstallerEngine.kt`），并通过 `packageSetupInstaller`（7z SFX + 内置 `runtime`）生成单文件自研云母安装器，彻底移除 Inno Setup。
- 仍保留独立预览模式：`gradlew :desktop:installer`（无 `--source` 参数时仍走 12s 模拟进度，不写磁盘）。

## 2. 用户操作流

1. 入口：`gradlew :desktop:installer` 起独立窗口；窗口居中、尺寸 640×440、无系统边框，标题栏区域可拖，最小化/关闭走自绘按钮。
2. 过程：徽记 800ms 逐帧绘出 → 品牌字与副题淡入 → 300ms 收拢为标题栏 + 安装选项页；点「自定义目录」时路径面板 300ms 展开，窗口高度同步增长；点「立即安装」进入安装页，插画按 2s 周期浮动、五幕文案按 6s 轮播，进度条走满后转完成页。
3. 结果：完成页显示完成态徽记与「开始使用」，点击后窗口退出。安装中点关闭按钮弹取消确认，确认后回选项页。

## 3. 范围

- 涉及屏与路由：独立窗口 `installer/InstallerWindow.kt`，四态 `SPLASH / OPTIONS / INSTALLING / COMPLETE`，不进入主应用路由。
- 涉及表与列（含迁移版本号）：无。
- 涉及启动参数或构建项：Gradle 任务 `:desktop:installer`；`-PinstallerPage=SPLASH|OPTIONS|INSTALLING|COMPLETE` 直达某一态，`-PinstallerTheme=light|dark` 选主题（缺省浅色）。

## 4. 边界与异常

- 空态：安装目录输入框为空时回落到默认目录，不阻断。
- 加载中：安装页进度按 12 秒单调曲线推进到 100%，文案轮播不依赖真实解包。
- 失败：不接真实安装，故无失败分支；扩展态的失败页只有界面，未接主链。
- 能力缺失（目录框不可用）：保留原路径并打一行读数，不弹错误。
- 主题与背景：宿主走 Nucleus 装饰窗，系统材质 Win11 取云母（Mica），Win10 由 `WindowsBackdropTier.Auto` 回退亚克力模糊；窗口外观用 `WindowAppearance` 跟随向导主题强制亮/暗，避免系统深色时云母为深、内容为浅叠成灰罩。卡片本身只铺一层半透纱（浅色 `0xA6F9FBFE`、深色 `0xA0161B24`），透出材质底色同时压住背后桌面内容。
- 起窗宿主：必须走 `nucleusApplication` + `DecoratedWindow`。标准 Compose `application` + `Window` 在整包类路径下会被 `nucleus.decorated-window-tao` 注册的 Tao 主调度器顶掉 `Dispatchers.Main`，lifecycle 判组合线程非主线程而崩 `addObserver`；且标准 `Window(transparent = true)` 是分层窗，DWM 材质不生效。因此 `installer` 任务恢复整包类路径，不再剔除该 jar。

## 5. 验收

- 判据（测试类名或源码尺）：`DesktopInstallerTimelineTest` 钉住进度曲线、幕序、浮动偏移与淡入淡出取值域；`DesktopInstallerHostTest` 钉住四态解析与自动推进时刻；`:desktop:compileKotlin` 通过。
- 端到端验证（谁、什么环境、验什么）：用户在本机 `gradlew :desktop:installer` 起窗，按四态逐屏核对动效。
- 取证读数：编译输出与点名测试结果；材质侧读到窗口 `DWMWA_SYSTEMBACKDROP_TYPE = 2`（Mica）、浅/深主题下 `DWMWA_USE_IMMERSIVE_DARK_MODE` 分别为 0/1，证明外观跟随主题；最终视觉仍以用户截图为准。

## 6. 文档影响

- [x] `docs/02-architecture/architecture.md`（登记 `desktop.installer` 包）
- [ ] `docs/02-architecture/database-schema.md`（无表变更，不适用）
- [x] `docs/03-guides/build-and-release.md`（`installer` 任务与两个参数）
- [x] `docs/01-product/roadmap.md`（登记安装向导窗口；缺口清单记"真实安装仍走 Inno"）
- [x] `README.md` 与 `README.en.md`（补一条运行命令）

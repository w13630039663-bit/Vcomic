# 变更记录（新功能 & Bug 修复）

> **维护规则（见 `desktop/AGENTS.md`「文档同步」第 6 条）**：
> 1. 每次修复 Bug 或新增功能，提交前必须同步记录到本文件对应分区（按时间倒序置顶）。
> 2. **Bug 修复**须写明「原因」与「解决方案」；**新功能**须写明「功能说明」与「涉及模块」。
> 3. **重复修复去重**：若对同一问题/同一功能点进行再次修复，**必须删掉原有的旧修复记录**，只保留最新一条置顶，避免过时方案堆积。

---

## 新增功能（Features）

- **2026-11-24 · 清理并拆分 TOP 10 巨型源码文件，完成 `app/`、`data/`、`ui/` 根目录三层归位与静态体积/分层护栏**（`VeneraDesktop`、`DesktopSettingsHub`、`DesktopReaderScreen`、`DesktopLargeScreenStateInit`、`DesktopTabHost`、`DesktopGalleryAndAccountHost`、`DesktopComicAndSourceHost`、`ForensicFlags`、`EngineSession`、`ContributorAvatars`、`DesktopFileSizeAndLayerGuardTest.kt`）
  - **功能说明**：① 将贡献者像素画内置为二进制资源，移除死代码，并将 139.6 KB–81.4 KB 的 10 个头部巨型文件按单一职责拆分为模块化小文件（均控制在 40 KB 以内）；② 将 `com.venera.desktop` 根目录 66 个散落裸文件按 `app/`（38）、`data/`（11）、`ui/`（17）三层归位，实现根目录零散落文件且 `data` 层零 Compose 依赖；③ 新增 `DesktopFileSizeAndLayerGuardTest` 静态护栏，强制约束新文件与已拆分文件 <= 40 KB 及三层单向依赖。

- **2026-11-24 · 补齐 Gradle 多模块边界（`:core-data`、`:gallery-core`）并完成调试探针源码集隔离**（`settings.gradle.kts`、`core-data/`、`gallery-core/`、`DesktopWin32WindowUtils.kt`、`MicaProbe.kt`、`PlatformDiagHook.kt`、`DesktopSourceTree.kt`、`docs/02-architecture/architecture.md`）
  - **功能说明**：① 拆分出纯 JVM 无 UI 依赖的 `:core-data`（承接 `data/platform`、`data/db`、`data/update`、`security/guard`）与 `:gallery-core`（承接 `gallery/data`、`gallery/domain`），解耦 `GalleryArtistAvatarStore` 对桌面错误总线的反向依赖（改走 `PlatformDiagHook`），从编译期强制杜绝数据/算法层反向依赖 UI；② 将混在生产源码树中的 `MicaProbe.kt` 拆出生产用 Win32 窗口工具函数（`DesktopWin32WindowUtils.kt`）后整体移入 `src/probe/kotlin`，确保打包与 `compileKotlin` 零探针残留。

- **2026-11-24 · 美化「关于与更新」页面并新增自动检查更新与一键下载安装**（`DesktopUpdateCheck.kt`、`DesktopAboutUpdateSection.kt`、`SettingsDialogShell.kt`、`DesktopSettingsHub.kt`、`DesktopLargeScreenMiscEffects.kt`、`DesktopUpdateCheckTest.kt`）
  - **功能说明**：① 在设置弹窗「关于与更新」新增软件介绍 Hero 卡片（应用徽章、版本胶囊、功能简介与技术特性标签）；② 新增「应用启动时自动检查更新」开关，并与启动后台 24 小时节流检查真正接线；③ 扩展桌面端 GitHub Release 解析以提取更新说明（`body`）与 Windows 安装包（`.exe`/`.msi`/`.zip`），发现新版后页内展示更新说明，并提供带实时进度、下载速度、SHA-256 校验与一键拉起安装程序的「一键下载安装」功能。

- **2026-11-24 · 完善中英文 README 关于网络代理环境与加速使用的重要说明**（`README.md`、`README.en.md`）
  - **功能说明**：在首页显著位置（`[!IMPORTANT]`）及「环境要求」章节明确标注网络代理环境建议，告知多数境外漫画源（如 CopyManga、JM、MangaDex）与 Pixiv/图库服务依赖国际网络访问，并指引系统代理跟随与应用内手动 HTTP 代理（`-Pproxy`）配置方式。

- **2026-11-24 · 完整落地 Linux 端跨平台抽象与打包流水线**（`DesktopPaths.kt`、`DesktopBrowserDetector.kt`、`DesktopEdgeWebLogin.kt`、`DesktopCloudflareBridge.kt`、`DesktopDirChooser.kt`、`DesktopProtocolRegistrar.kt`、`VeneraDesktop.kt`、`package-linux.sh`、`ci.yml`、`release.yml`）
  - **功能说明**：① 数据与缓存路径全面适配 Linux XDG 规范（`$XDG_DATA_HOME/venera` 与 `$XDG_CACHE_HOME/venera`），无环境变量时缺省回退至 `~/.local/share` 与 `~/.cache`；② 建立跨平台 Chromium 探测链（覆盖 `google-chrome(-stable)`、`chromium(-browser)`、`brave(-browser)`、`microsoft-edge(-stable)`），并支持 `VENERA_BROWSER_PATH` 环境变量指定自定义浏览器，实现 Linux 下无缝 CDP 自动化登录与 Cloudflare 过盾；③ 文件夹选择器在 Linux 下优先调起原生 `zenity` / `kdialog` GTK/KDE 对话框，无命令时平滑回退至 Swing；④ 支持生成标准 `venera.desktop` 文件并通过 `xdg-mime` 自动注册 `pixiv://` 与 `venera://` 协议（带 `StartupWMClass=venera-desktop`）；⑤ 窗口材质在非 Windows 环境自动跳过 DWM 调用；⑥ 新增轻量独立打包脚本 `scripts/package-linux.sh`，并在 `.github/workflows/ci.yml` 与 `release.yml` 中并入 `ubuntu-latest` 自动化构建与 AppImage / tar.gz 产物发布。

- **2026-11-24 · 升级系统托盘右键菜单为 Windows 11 同步样式并加入实用后台功能**（`DesktopSystemTray.kt`、`VeneraDesktop.kt`、`DesktopLargeScreenRouter.kt`、`DesktopSystemTrayTest.kt`）
  - **功能说明**：托盘右键菜单由默认老式 `JPopupMenu` 升级为自绘无边框圆角弹窗（10px 圆角、抗锯齿字体与手绘线性图标），自动读取 Windows `SystemUsesLightTheme`/`AppsUseLightTheme` 同步深浅配色，并在任务栏呼出时自动贴合工作区边缘；菜单新增实时状态头、显示/隐藏主窗口、打开「下载与本地」、打开下载文件夹、立即检查追更、暂停/继续全部下载与隐私遮罩开关。

- **2026-11-23 · 新增本地漫画「导入文件夹（原地关联）」功能**（`DesktopDownloadsDb.kt`、`DesktopLocalComicLibrary.kt`、`DesktopLocalComicImport.kt`、`DesktopDownloadsPipelineScreen.kt`、`DownloadsPipelineCards.kt`、`DesktopComicAndSourceHost.kt`、`DesktopComicAndFilesEffects.kt`、`DesktopWallUiStates.kt`）
  - **功能说明**：在「下载与本地 → 本机内容 → 漫画」新增「导入文件夹」入口，通过系统文件夹选择器选中单个漫画文件夹后原地关联写入 `local_comic_imports` 表（零文件拷贝），自动兼容单话直接放图与多话子目录结构，支持直接阅读或先选话再阅读；切换下载目录不丢失记录，移除关联时仅清理台账不删除硬盘原文件夹。

## Bug 修复（Fixes）

- **2026-11-24 · 修复详情页收藏弹窗未点完成即提前显示「已收藏」、加号图标偏位，并补全交互动效**（`DesktopComicDetailFavoriteDialog.kt`、`DesktopFavoriteFoldersUiTest.kt`、`DesktopNewScreensSemanticsTest.kt`）
  - **原因**：① 收藏弹窗内点击复选框即立刻落库并显示「已收藏」，与底部的「关闭 / 完成」双按钮语义冲突，导致未收藏或仅点选时就抢先显示「已收藏」且点「关闭」无法取消；② 「新建收藏夹」左侧方框内使用 `Text("+")`，受字体行高与基线影响向右下偏位；③ 弹窗与列表行缺少交互过渡反馈。
  - **解决方案**：① 改为草稿勾选模式：新勾选显示「待收藏」且不落库，原本已收项取消显示「将移出」，点「完成」统一提交差异，点「关闭」或遮罩放弃草稿；② 改用 `Canvas` 自绘居中加号，与复选框严丝合缝对齐；③ 按 `VeneraMotion` 规范补齐弹窗弹性入场、折叠箭头旋转、行悬停/按压缩放、复选框弹跳与状态徽章滑入动效。

- **2026-11-24 · 清理仓库历史大图资产与重复截图并将 README 展示图统一优化为 WebP**（`README.md`、`README.en.md`、`docs/images/`、`assets/`）
  - **原因**：① `desktop/assets/screenshots/` 与 `desktop/assets/` 堆积了大量 2MB+ 的巨大未压缩 PNG 截图，以及与正式图片完全相同的 UUID 临时测试截图，无代码和文档引用，导致仓库被多塞了近 20MB 冗余数据；② README 展示图此前使用 JPG 格式，体积偏大且存在局部未去重；③ 外围 `_backup` 与 `_archive` 存有大量 2MB~3MB 的历史截图原图。
  - **解决方案**：① 在 `desktop` 仓库中移除无引用的 2MB+ PNG、1MB+ 冗余原图及两份重复 UUID 截图，Git 跟踪图片体积立降 20.69 MB（降幅 63.5%）；② 将 `docs/images/` 核心展示图统一压制为高质量 WebP（Quality 82%），体积从 3.57 MB 压缩至 2.12 MB 并同步更新中英两份 README 引用；③ 清理外围原始截图备份与归档目录，全工作区累计释放 105.36 MB 硬盘空间。

- **2026-11-23 · 切换下载路径后旧漫画丢失封面和标题，以及下载中在线漫画误显示为「导入」**（`DesktopComicDownloadedList.kt`、`DesktopDownloadsDb.kt`、`DesktopDownloadsPipelineScreen.kt`、`DesktopComicAndSourceHost.kt`）
  - **原因**：① `DesktopDownloadsDb.listComicListing()` 未将表内已保存的 `comic_title`、`cover_url`、`local_dir` 传出，`DesktopDownloadsPipelineScreen` 与 `onOpenDownloadedChapter` 仅从当前 `downloadsRoot` 与当前队列取名和封面，切换路径后查询落空导致退回话名和空封面；② `DesktopDownloadsPipelineScreen` 将 `localComicScan` 扫出的全部非 `downloaded` 目录（含正在下载或跨路径未归一化的 `picacg`/`ehentai` 等在线源目录）全部包装成「导入」卡片。
  - **解决方案**：① `DesktopComicDownloadedChapter` 透传台账的 `comicTitle`、`coverUrl`、`localDir`，作品名和封面优先命中台账与真实落盘作品目录（含本地 `cover.*`、URL 与首张页图兜底）；② 仅展示台账导入表与 `imported/` 目录下的真正导入漫画，且排除正在下载队列中的任务编号，彻底消除下载中漫画变成「导入」的异常。

- **2026-11-23 · 下载队列所有非下载中任务均假报「排队中」且吞掉报错，同时跨源下载未切换源导致 `loadEp` 失败**（`EngineSession.kt`、`DesktopComicQueueRunner.kt`、`DesktopComicDownloadQueue.kt`、`DesktopComicAndSourceHost.kt`、`DesktopDownloadsChrome.kt`、`DesktopDownloadsPipelineScreen.kt`）
  - **原因**：① 队列行 UI 仅判断 `status == RUNNING`，把 `FAILED` 和 `STOPPED` 也一律显示为黄色「排队中」，且未在行内渲染 `task.error`；② 顶栏「全部暂停」与宿主 `onQueuePauseResume` 未区分 `PENDING` / `STOPPED` / `FAILED`，点击「全部暂停」反而把等待/失败项重新 `resume`；③ `DesktopComicQueueRunner` 调 `engine.pages` / `engine.pageImage` 未传入 `task.sourceKey`，`EngineSession` 始终按主会话当前最后加载的 `key` 取源，导致跨源或未加载源时 `loadEp` 错打到别的源脚本并报错；④ 长标题未设 `weight(1f, fill = false)` 挤压右侧「漫画」徽章。
  - **解决方案**：① `EngineSession.pages` / `pageImage` 新增可选 `sourceKey` 参数并自动通过 `ensureSourceLoaded` 装载对应源（不污染主界面当前选中源），`DesktopComicQueueRunner` 传入 `task.sourceKey`（同时走 `desktopEngineCall` 代理直连前置校验）；② 队列补齐 `pause()`，修正宿主暂停/继续/重试分发与顶栏「重试失败」「全部暂停」「全部继续」按态生效；③ 队列行完整区分进行中（「下载中」主色）、等待态（「等待中」中性灰）、警告态（「已暂停」琥珀黄）、失败态（「下载失败」红 + 行内报错条与一键复制 + 重试按钮）以及短时成功态（「下载成功」绿，展示 4 秒后自动隐去、下方「本机内容」不展示成功态），并修复长标题挤压徽章问题。

- **2026-11-23 · 官网下载按钮把 Windows 用户导向手机端仓库**（`index.html`）
  - **原因**：「下载 VComic」卡片的「前往 Releases 下载」按钮与页脚「GitHub 仓库」都写成 `https://github.com/w13630039663-bit/venera-miuix/releases`，该仓库 Release 只有 `app-arm64-v8a-release.apk`、`app-universal-release.apk` 两个 Android 资产，桌面用户点进去拿不到 Windows 安装包；官网由 Vcomic 仓库根目录 `index.html` 发布，属于链接写错仓库。
  - **解决方案**：下载按钮与页脚链接统一改为 `https://github.com/w13630039663-bit/Vcomic`，按钮文案由「前往 Releases 下载」改为「前往 GitHub 仓库」（Vcomic 仓库当前无 Release、README 注明正式发布前从源码构建，故不保留 Releases 入口措辞）；Android 区块仍指向手机端仓库，未改程序内更新检查。

- **2026-11-23 · 改用独立下载台账数据库 `desktop_downloads.db` 彻底取消磁盘扫盘，并支持自由选目录与通知**（`DesktopDownloadsDb.kt`、`DesktopComicDownloadQueue.kt`、`DesktopImageSaver.kt`、`DesktopPixivImageBytes.kt`、`DesktopComicAndFilesEffects.kt`、`DesktopLargeScreenMiscEffects.kt`、`DesktopLargeScreenStateInit.kt`、`DesktopComicOfflineReading.kt`、`DesktopFollowUpdateChecker.kt`、`DesktopGalleryPreferences.kt`、`DesktopSettingsHub.kt`、`DesktopComicDetailScreen.kt`、`VeneraDesktop.kt`）
  - **原因**：① 「本机内容」依赖递归遍历电脑目录树，更换大目录会卡死在「正在加载漫画列表...」，且跨目录存图会漏扫；② `businessHandle` 缓存旧 `downloadsRoot` 闭包；③ 图片默认开启固定目录直存，显得地址绑死。
  - **解决方案**：① 新增独立库 `desktop_downloads.db`（`comic_download_records` 与 `image_download_records` 两张表，分别记录下载内容、源地址与本地保存路径），「本机内容 → 漫画 / 图片」纯读库不扫盘，阅读/删除/打开文件夹按库记录直达目标目录；② 补齐追更与图片下载系统通知；③ 修复 `businessHandle` 动态读取最新 `downloadsRoot`，并将图片保存默认改为弹出选目录（可在设置页开启「默认目录直存」）。

- **2026-11-23 · 修复安装版搜图缺少 `java.net.http` 崩溃、托盘右键中文乱码与切换页面卡死**（`desktop/build.gradle.kts`、`DesktopSystemTray.kt`、`DesktopNotifier.kt`、`DesktopLargeScreenMiscEffects.kt`、`docs/03-guides/build-and-release.md`）
  - **原因**：① `nativeDistributions.modules` 遗漏 `java.net.http`，导致打包后运行时缺少 `HttpClient`；② `DesktopSystemTray` 使用老旧 AWT `PopupMenu` 走 ANSI 渲染导致中文变方块乱码，且与 `DesktopNotifier` 并存两颗托盘图标；③ 运行时参数限制 `-Xmx256m` + `-XX:+UseSerialGC` + `-XX:TieredStopAtLevel=1` + `32M` GPU 缓存，切页时易触发单线程 Full GC 冻屏。
  - **解决方案**：① `modules` 补齐 `java.net.http`；② `DesktopSystemTray` 改用 `JPopupMenu` + `Microsoft YaHei UI` 字体与无边框 `JDialog` 宿主，并将 `DesktopNotifier` 统一委托至 `DesktopSystemTray`、移除重复安装调用；③ 调整运行时参数为 `-Xmx1024m`、`-XX:+UseG1GC`、移除 `TieredStopAtLevel=1`、并将 Skia 缓存调至 `128M`。

- **2026-11-23 · 清零全量测试红灯、清理重复引入与本机路径注释**（`DesktopLargeScreenHelpers.kt`、`DesktopFavoritesScreen.kt`、`DesktopGalleryPostViewer.kt`、`DesktopSqliteNative.kt`、`DesktopComicMapping.kt`、`DesktopCrashReadingTest.kt`、`DesktopNetworkFollowImporterTest.kt`）
  - **原因**：① `DesktopLargeScreenHelpers.kt` 重复引入 `desktopComicDeleteRowKey` 导致静态护栏失败；② `DesktopFavoritesScreen.kt` 在双列视图新增了第二处 `desktopFavoriteFolderLine` 调用且空态时顶栏与 `CenterNote` 同时渲染两次 `note`；③ `DesktopGalleryPostViewer.kt` 的 `ViewerImage` 回调 `onError` 带默认空实现违背无默认回调规矩；④ `DesktopCrashReadingTest` 与 `DesktopNetworkFollowImporterTest` 未同步「拉图失败静默不刷屏」及友好错误翻译口径；⑤ `DesktopSqliteNative.kt`、`DesktopComicMapping.kt` 注释含本机绝对路径。
  - **解决方案**：① 删除重复 `import`；② 抽取 `FavoriteFolderText` 统一双列与网格视图的夹名渲染，并仅在非空态或加载/筛选提示时渲染顶部 `note`；③ 移除 `ViewerImage` 的 `onError` 默认值；④ 同步 `DesktopCrashReadingTest` 为静默校验与 `DesktopNetworkFollowImporterTest` 友好提示断言；⑤ 将源码注释中的本机绝对路径改为通用表述。

- **2026-11-23 · 清理多屏原始异常透传、修复密码/网页登录源误判及本地空态混用**（`business/DesktopComicMapping.kt`、`business/DesktopEngineComicSource.kt`、`business/DesktopSourceCatalogPort.kt`、`history/DesktopHistoryScreen.kt`、`favorites/DesktopFavoritesScreen.kt`、`search/DesktopAggregateSearchRunner.kt`、`gallery/ui/DesktopReverseSearchPane.kt`、`gallery/ui/DesktopBrowseHistoryRecovery.kt`）
  - **原因**：① `sourceSupportsLogin` 与 `desktopAccountInfo` 仅认 `cookieFields`，把 `picacg`、`jm`、`baozi`、`copy_manga` 等密码/网页登录源误判为不支持登录，401/403 被错报成「源未放行」；② 本地历史与本地收藏读失败时未判 `note`，直接显示「还没有阅读记录/还没有收藏」；③ 聚合搜索、以图搜图与浏览历史恢复直接把 `javaClass.simpleName + message` 原文晒在界面上。
  - **解决方案**：① `sourceSupportsLogin` 与 `desktopAccountInfo` 补齐 `supportsPasswordLoginOf` / `supportsWebLoginOf` 判断；② 本地历史与本地收藏空列表分支优先展示错误 `note`；③ 聚合搜索、以图搜图与浏览历史恢复统一改走 `desktopFailureNoteOf` / `desktopFailureNoteForSource`，消除英文原始异常透传并使需代理源超时提示「需配置代理后重试」。

- **2026-11-23 · 修复画廊与漫画多屏永久加载卡死、失败误走空态及下载任务隐藏**（`DesktopGalleryAndAccountHost.kt`、`DesktopPixivSearch.kt`、`DesktopSearchAndExploreEffects.kt`、`pixiv/DesktopPixivWorkScreen.kt`、`pixiv/DesktopPixivArtistScreen.kt`、`pixiv/DesktopPixivAccountScreen.kt`、`gallery/ui/DesktopGalleryPixivisionArticleScreen.kt`、`gallery/ui/DesktopGalleryPostViewer.kt`、`favorites/DesktopNetworkFavoritesScreen.kt`、`downloads/DesktopDownloadsPipelineScreen.kt`、`detail/DesktopComicDetailModels.kt`、`detail/DesktopComicDetailScreen.kt`、`detail/DesktopComicDetailSections.kt`、`DesktopHomeLightScreen.kt`）
  - **原因**：① Pixiv 搜索页在宿主把 `failed` 写死为 `false`、重试不刷新 `pixivSearchTick` 且 `tagWorks` 吞错；② Pixiv 作品详情与画师页在接口失败时 `detail`/`feed` 保持 `null`，界面永久停在「正在取…」；③ Pixiv 账号页 `All` 关注流全失败仍回空列表且 `gate.showLogin`/`gate.showRetry` 未渲染按钮；④ 全屏看图器加载过程中提前显示「没加载出来」，Pixivision 正文失败变白板；⑤ 漫画首页推荐 `recommendFailed` 未置 `true`，网络收藏第 2 页失败清空第 1 页，详情内嵌评论未透传 `error` 误显示「还没人评论」，下载流水线过滤掉了 `FAILED`/`STOPPED` 任务且仪表盘计数写反。
  - **解决方案**：① 接通 Pixiv 搜索失败态与重试，`tagWorks` 失败正常上抛走 `desktopFailureNoteOf`；② Pixiv 作品详情与画师页保留 `Result` 并补齐 `DesktopFailedState` / 错误文案与重试；③ `desktopPixivScopedFeed` 全失败时返 failure，账号页按 `gate.showLogin`/`showRetry` 补齐「登录 pixiv」「重试」按钮；④ 看图器改为 `onError` 真正失败才提示，Pixivision 文章失败显示引导文案；⑤ 首页推荐支持按 `recommendNote` 进入失败重试槽，网络收藏仅在首屏为空时整屏报错，详情评论优先展示 `commentsError`，下载流水线展示非 `DONE` 全部任务并修正失败/暂停计数及列表读盘报错显示。

- **2026-11-23 · 需代理源与 Pixiv 在直连下长时间干等且不提示需配置代理**（`engine-probe/DesktopCloudflareCenter.kt`、`desktop/platform/DesktopHttpEngine.kt`、`desktop/platform/DesktopErrorCopy.kt`、`desktop/gallery/data/DesktopPixivisionFeed.kt`、`desktop/DesktopEngineCall.kt`、`desktop/business/DesktopComicContentApi.kt`、`desktop/business/DesktopEngineComicSource.kt`、`desktop/sourcemanage/DesktopSourceManageScreen.kt`）
  - **原因**：`DesktopHttpEngine` 与引擎 HTTP 客户端在直连或系统无代理时仍按 10~45 秒超时等待，超时后 `TIMEOUT` 只提示「稍后重试」；`DesktopCloudflareCenter` 缺少 `callTimeout`；`DesktopPixivisionFeed` 自建裸 `OkHttpClient()` 绕过应用代理设置。
  - **解决方案**：① 新增 `DesktopCloudflareCenter.ProxyFastFailInterceptor` 并接入 `DesktopHttpEngine` 与 `newClientBuilder`（同时补齐 `callTimeout = 30s`）：软件内明确设为「直连」或「手动」未填有效地址时，访问 Pixiv 或 11 个需代理海外源直接秒拒并提示需配置代理；选「系统」但系统无可用代理时压短超时至 4.5s 连接 / 8s 读取，失败统一归档为 `BLOCKED（网络受阻 · 需配置代理后重试）`（不误杀全局 TUN）。② `DesktopEngineCall` 为 `PROXY_REQUIRED_SOURCE_KEYS` 增设直连前置秒拒。③ `DesktopPixivisionFeed` 改为复用 `DesktopHttpEngine` 客户端，不再绕过应用代理。

- **2026-11-23 · 阅读器底部工具栏在总页数归零退场时崩溃（`IllegalArgumentException: maximum 0 is less than minimum 1`）**（`desktop/src/main/kotlin/com/venera/desktop/reader/DesktopReaderHud.kt`）
  - **原因**：`ReaderBottomHud` 的 `AnimatedVisibility(visible = visible && totalPages > 0)` 在 `totalPages` 变为 0 触发退场动画期间仍会执行内部重组，页码文本与滑块回调直接调用 `coerceIn(1, totalPages)` / `coerceIn(0, totalPages - 1)`，因上界小于下界抛出 `IllegalArgumentException`。
  - **解决方案**：页码文本在 `totalPages <= 0` 时安全回落为 `0`（避免调用 `coerceIn(1, 0)`），并将滑块 `onValueChange` 的上界同步改为 `(totalPages - 1).coerceAtLeast(0)`。

- **2026-11-23 · 拷贝漫画探索与搜索报 `unexpected token: 'error'` 失败**（`desktop/src/main/resources/sources/copy_manga.js`）
  - **原因**：原版内置 `copy_manga.js` 默认域名 `api.copy2000.online`（及部分旧备用域名）当前即使携带 App 请求头也会返回 HTTP 200 与纯文本 `error`，且原版脚本的 `refreshAppApi()` 仅改写 `settings.base_url` 定义对象而不生效，探索页与搜索页也未接入多线路轮换与安全 JSON 解析，直接 `JSON.parse(body)` 导致解析报错并掩盖真实原因。
  - **解决方案**：以 `moonskycake/venera-next` 的 `copy_manga.js`（`1.4.15-debug1`）为基础同步更新内置源（同步 `url` 指向该社区仓库，保留其动态域名发现、210 频控退避、网页版搜索路径 `searchcl` 与网页版章节图解密兜底）；同时将默认与首选备用域名调整为实测可用的 `api.manga2026.xyz`，并将探索页与搜索页请求统一接入 `requestResults` 多线路轮换与非 JSON 响应安全报错。

- **2026-11-23 · Konachan R18 / Konachan / Safebooru CF 过盾与 WebView2 桥接修复（累计修复 5 轮，已解决）**（`gallery/data/DesktopKonachanBridge.kt`、`gallery/data/DesktopKonachanComClient.kt`、`gallery/data/DesktopKonachanClient.kt`、`gallery/data/SafebooruClient.kt`、`gallery/data/DesktopGalleryBoards.kt`、`engine-probe/DesktopCloudflareCenter.kt`、`platform/DesktopHttpEngine.kt`、`platform/DesktopCloudflareBridge.kt`、`gallery/ui/DesktopWallFirstPage.kt`、`gallery/ui/DesktopGallerySearchPane.kt`、`VeneraDesktop.kt`、`GallerySite.kt`、`DesktopImagePipelineTest.kt`、专篇见 `docs/cloudflare-bypass-architecture-2026-11-23.md`）
  - **为什么之前一直有问题（5 轮复盘）**：
    1. **第 1 轮误判传输层**：误以为把 Edge 拿到的 `cf_clearance` 交给 `OkHttp` 就能替掉慢吞吞的 `WebView2` 桥，未核实 `konachan.com` 绑定了浏览器 TLS 指纹校验，导致 `OkHttp` 拿到 `cf_clearance` 后重试仍恒 403。
    2. **第 2 轮过盾假成功与 12s 截断**：`DesktopCloudflareBridge` 直接拿 `.json` 接口给 Edge 打开且只按页面标题判断完成，抓到 1 枚普通 Cookie（非 `cf_clearance`）就误判过盾成功；同时画廊首屏与单站搜索对 `KONACHAN`/`SAFEBOORU` 仍用 12 秒预算，过盾未完就被 `GalleryLegGuard` 截断。
    3. **第 3 轮回到 WebView2 后未切主线程且缺状态读数**：切回并优化 `DesktopKonachanBridge` 后出现 `WebView 挂载超时`，早期代码只在 `awaitActive()` 空等 30 秒，未读取 `WebViewState.error`（吞掉了真实异常）且从 IO 线程直接写 Compose `mountRequested` 状态。
    4. **第 4 轮抓出 `LocalAwtWindow` 缺失**：补上 `st.error` 读数并反编译底层库后，定位到 `WebView requires an AWT window`——`LocalAwtWindow.current` 在该壳层级下为 `null`，导致 `WebView2` 初始化直接停在 `Closed`。
    5. **第 5 轮解决遮挡层级**：出图打通后，验证窗在 `DesktopLargeScreenChrome` 中位于主内容之前、无高 `zIndex`，切换站点触发验证时被画廊瀑布流图片遮挡无法点击。
  - **最终解决方案**：① `DesktopKonachanBridge` 改用目标站轻量 `/robots.txt` 着陆（403/503 自动跳首页过盾）、5 分钟保活、`Mutex` 串行、主线程切换挂载状态、兜底注入 `LocalAwtWindow`，并在亮出验证面板时挂 `zIndex(100f)` + 半透明遮罩拦截穿透点击；② `Konachan R18`、`Konachan`（主动搜索/详情）及 `Safebooru`（主站失败自动回退并记忆切桥）统一走该轻量桥；③ 保留 `DesktopHttpEngine` 对 `DesktopCloudflareCenter` 的接入与 `NoInteractiveBypassTag` 豁免、单站 45s 预算及 `DesktopCloudflareBridge` 的 `cf_clearance` 校验与代理透传。

- **2026-11-23 · 哔咔发评论「提示成功却没发出去」、禁漫撞 502 直接失败、评论首页不按最新排、发送后不刷新列表**（`resources/sources/picacg.js`、`resources/sources/jm.js`、`compose/feature/ComicDetailCore.kt`、`desktop/src/test/kotlin/com/venera/compose/feature/DesktopCommentOrderTest.kt`）
  - **原因**：① 哔咔 `sendComment` 参与签名的 path 带前导斜杠（`/comments/…`、`/comics/…/comments`），与脚本内其他接口（都无斜杠）不一致，服务端按无斜杠重算签名验不过，评论根本没落库；而成功判定只看 `res.status === 200`、不看返回体，于是客户端播「成功」，用户站上却找不到这条。② 禁漫 `POST /comment` 被镜像域的网关拦（日志 `HTTP 500/502 POST`），旧实现固定打 `baseUrl` 单域、不换域，502 直接抛出，界面只能报失败。③ 详情页评论沿用源分页原序（禁漫按论坛楼层、哔咔按创建序），首屏常混进旧帖，不符合「默认按最新展示」。④ 旧逻辑先 `onComplete(true)` 再异步 `loadComments()`，界面挂「已发表」时列表仍是旧的，用户会以为没发出去。
  - **解决方案**：① 哔咔签名 path 与请求路径统一去掉前导斜杠（回复/顶层共用一条模板），补 `401 → reLogin → 重发一次`，并按返回体 `error` / `code !== 200` 判失败（不再把 200 当落库）；`likeComment` 同样修正。② 禁漫发评论改成按 `getAcrossLines` 的思路换镜像域，最多试 3 个域，只对 5xx 与坏响应重试，未登录/内容被拒等真失败原样抛出。③ 核里新增 `commentsNewestFirst` / `commentTimeKey`：把时间串归一成 14 位数字键（ISO 带毫秒与 `yyyy-MM-dd HH:mm:ss` 都认），稳定倒序；解不出时间的条目保持原序、置后；刷新与续页都重排。④ `sendComment` 成功分支改为先重读第一页、把新列表写回状态，再回调成功；刷新失败时仍算发送成功，但如实提示「评论已发出，但列表没刷新成功」。⑤ 源站 5xx 仍由状态行与失败态照原文呈现，不再重复往顶部报错胶囊里塞「评论发送失败」条目。

- **2026-11-23 · 漫画搜索 / 探索 / 首页推荐的失败文案把异常原文照搬上屏，且不进错误面板**（`platform/DesktopErrorCopy.kt`、`search/DesktopComicSearchOutcome.kt`、`DesktopSearchAndExploreEffects.kt`）
  - **原因**：这几处把 `javaClass.simpleName + message`、或源站信封里的 `__err` 直接拼成屏上文案，于是 TLS 握手被切断（`Remote host terminated the handshake`）原样念给用户，那句话既看不出能做什么、也不该出现在界面上；同一笔错误又只落在 stdout，顶部错误胶囊与面板收不到，用户拿不到可复制的技术详情，也就无法反馈。
  - **解决方案**：新增 `desktopFailureNoteOfText`（只有字符串那一路的归档，与异常路 `desktopFailureNoteOf` 同口径：认得出档位念「网络受阻 · 需配置代理后重试」，认不出=源站业务句则原样交回不改写）；搜索空档与页尾、探索首屏与分类、首页推荐全部改走归档、原文一律 `println` 进日志；上述失败同时 `DesktopErrorBus.report`（漫画分类 + 大白话标题 + 原文详情 + 去重键），进顶部报错胶囊与「异常与操作提醒」面板。

- **2026-11-23 · 漫画侧 21 处失败仍把异常原文念在屏上、不进错误面板（P1+P2 批量接线）**（`DesktopComicAndFilesEffects.kt`、`compose/feature/ComicDetailCore.kt`、`DesktopTabHost.kt`、`detail/DesktopComicDetailScreen.kt`、`follow/DesktopFollowUpdateChecker.kt`、`follow/DesktopNetworkFollowImporter.kt`、`sourcemanage/DesktopSourceStore.kt`、`DesktopComicAndSourceHost.kt`）
  - **原因**：这批失败各自拼一句带 `javaClass.simpleName` / `message` 的 toast、内联提示或页脚，异常原文（TLS 握手被切断、读盘失败等）直接显示给用户；同一笔错误只落 stdout，顶部胶囊与「异常与操作提醒」面板一无所知，用户既看不出能做什么，也无处复制详情反馈。
  - **解决方案**：屏上文案统一走 `desktopFailureNoteOf` / `desktopFailureNoteOfText` 归档（认得出档位就念档位提示，认不出则照原句），原文 `println` 落日志并作为 `DesktopErrorBus.report` 的 detail（漫画分类 + 大白话标题 + 按源/按操作的去重键）；覆盖评论读取与发送、本地与网络收藏、追更开关与批量取消、追更巡检单源熔断与本轮汇总、网络收藏导入取数、源清单/顺序/启停/撤销/退出登录/站点设置写盘。

- **2026-11-23 · 详情页评论只显示首页、发送无状态，网络收藏取消要连点多次**（`detail/DesktopComicDetailSections.kt`、`detail/DesktopComicDetailModels.kt`、`detail/DesktopComicDetailScreen.kt`、`business/DesktopEngineComicSource.kt`、`screens/DesktopComicBulkDownloadUiTest.kt`、`screens/DesktopComicContinueReadingUiTest.kt`、`screens/DesktopComicDetailFollowTest.kt`、`screens/DesktopFavoriteFoldersUiTest.kt`、`screens/DesktopNewScreensSemanticsTest.kt`）
  - **原因**：① 详情页评论区只吃 `loadComments()` 的首页，核里的 `hasMore` / `maxPage` 没接到屏上（65 条评论只显示一页），独立评论屏反而有「再读一页」；② 发送结果只有一次性 toast，输入区没有常驻状态，源站返回 500 时用户既看不出失败、也分不清「发出去了」；③ JM 那族的 `favorites.addOrDelFavorite(false)` 实际是 toggle 接口，本地「已收藏」与源站不同步时点一次会反向加回，旧实现又只按返回值播成功、不读回校验，于是「点了取消还在、要多点几次」。
  - **解决方案**：① 详情数据补 `commentsHasMore` / `commentsLoadingMore` / `commentsPage` / `commentsMaxPage`，评论区底部按 `hasMore` 摆「加载更多评论」按钮（带页码与已显示条数，续页走 `loadComments(loadMore = true)` 追加不替换）；② 数据补 `isSendingComment`，输入区常驻状态行：发送中 / 评论已发表 / 发送失败（附核翻好的原话，草稿保留可直接重试），发送中按钮禁用防重复提交；③ 适配层取消收藏后读回 `loadFolders(comicId)` 校验，仍在收藏就再补一次（最多两跳），两跳仍不干净如实报失败，不再假装成功。

- **2026-11-22 · 修复图库多选栏与批量操作弹窗无毛玻璃模糊及标签维度去底色外框**（`gallery/ui/selection/DesktopSelectionDock.kt`、`gallery/ui/selection/GalleryBatchActionDialog.kt`、`gallery/ui/DesktopGalleryTagDimensions.kt`、`DesktopWallScaffold.kt`、`favorites/DesktopFavoritesScreen.kt`、`explore/DesktopExploreScreen.kt`、`gallery/ui/DesktopGalleryFavoritesWall.kt`）
  - **原因**：① 悬浮多选栏 `DesktopSelectionDock` 的模糊开关绑死系统透明底板，且各屏外层 `if (selected.isNotEmpty())` 与无 `zIndex` 层次包裹导致 `Haze` 采样与动画挂载时机不稳定；② 「批量操作」与批量确认弹窗使用独立窗口 `Dialog` 并铺设实心底色，无法采样宿主窗口 `HazeState` 实现毛玻璃；③ 标签维度舱外层容器带有实心底色和边框。
  - **解决方案**：① 将页面内悬浮栏 `hazeBlur` 改按 `hazeState != null && !DesktopEffects.reduce` 常开并提升半径与噪点纹理，统一 `hazeSource(..., zIndex = 0f)`，去掉调用方多余的 `if (selected.isNotEmpty())` 包裹改由内部 `AnimatedVisibility` 控制显隐；② 将 `GalleryBatchActionDialog` 与 `DesktopBatchConfirmDialog` 重构为宿主内居中毛玻璃浮层 `GalleryGlassModalOverlay`，接入 `LocalSelectionDockHazeState` 实时模糊背后画廊并支持遮罩点击与 `Esc` 关闭；③ 移除 `DesktopGalleryTagDimensions` 外层背景色与外框线。

---

## 新功能（Features）

- **2026-11-23 · 站点管理页为需代理访问的海外内置源增加「需代理」徽章**（`sourcemanage/DesktopSourceManageScreen.kt`）
  - **说明**：在站点墙大卡片、小卡片及站点管理抽屉顶部状态徽章栏，为 11 个常规境内需代理的海外内置源（`comic_walker`、`comick`、`ehentai`、`gelbooru`、`hcomic`、`hitomi`、`jcomic`、`manga_dex`、`mh18`、`nhentai`、`shonen_jump_plus`）统一展示中性 `StatusBadge("需代理")` 提示，仅做网络环境提醒，不影响请求与代理配置逻辑。

- **2026-11-22 · 漫画详情写侧接通：发评论、打分、作品点赞**（`engine-probe/EngineSession.kt`、`engine-probe/DesktopComments.kt`、`compose/source/ComicSource.kt`、`compose/source/model/ComicSourceModels.kt`、`business/DesktopEngineComicSource.kt`、`business/DesktopComicMapping.kt`、`compose/feature/ComicDetailCore.kt`、`detail/DesktopComicDetailScreen.kt`、`detail/DesktopComicDetailModels.kt`、`detail/DesktopComicDetailSections.kt`、`detail/DesktopComicDetailHero.kt`、`resources/sources/*.js`）
  - **说明**：① 引擎新增 `sendComment` / `starRating` / `likeComic` / `writeCapabilities` 四条调用口：发评论的三档判据与评论能力位同源（`comic.sendComment` 退 `comments.send`），打分按源脚本规范把界面 5 星换算成 0–10（`rating * 2`），作品点赞兼容 `comic.likeComic` 与旧形 `comic.like`；源未声明时如实回 `false`，源站抛错原样上报，不再有恒失败的占位实现。② 桌面适配层接真引擎；详情页作品信息区新增「打分」入口与五星星级弹窗，读数优先展示刚提交的星级；点赞钮在源声明能点时即使读数缺失也摆，读数跟随核的乐观状态。③ 发评论失败保留草稿，只有成功才清空输入框。④ 随包脚本去掉三处假能力：nhentai 必抛的 `sendComment`、kavita 与 manga_dex 的空 `starRating` 改为不声明，避免能力位误报「可发送 / 可评分」。章节评论仍无桥，保持如实不支持。
- **2026-11-22 · 追更双视图按钮化与追更/网络收藏/画廊收藏历史/漫画阅读历史全面补齐多选菜单**（`follow/DesktopFollowUpdatesScreen.kt`、`favorites/DesktopNetworkFavoritesScreen.kt`、`favorites/DesktopFavoritesHubScreen.kt`、`DesktopTabHost.kt`、`business/DesktopBusinessPorts.kt`、`gallery/ui/selection/DesktopSelectionDock.kt`、`gallery/ui/DesktopGallerySavedScreen.kt`、`gallery/data/DesktopGalleryBrowseHistory.kt`、`history/DesktopHistoryScreen.kt`、`compose/feature/HistoryCore.kt`、`DesktopGalleryAndAccountHost.kt`）
  - **说明**：① 新增公共封面选择圆标 `DesktopCardSelectBadge`，为追更中心（网格 / 双列两视图）、网络收藏（网格 / 双列两视图）、画廊「收藏与历史」（收藏 / 浏览历史两视图）以及漫画「阅读历史」统一接入封面选择按钮、选中蓝框、Shift 区间连选、`Ctrl+A / Delete / Esc` 快捷键及底部 `Haze` 毛玻璃多选悬浮栏 `DesktopSelectionDock`；② 将追更两视图卡片底部的纯文字操作（打开 / 标为已读 / 单本重查 / 暂停 / 恢复 / 标完结 / 取消追更）全部升级为标准 `DesktopButton(Compact)` 小按钮，并在底栏接通批量重查、标为已读、暂停/恢复、标完结与取消追更；③ 接通各多选栏真写口（网络收藏批量调用源 `addOrDelFavorite` 取消收藏及批量加入追更账本、画廊收藏批量移除与浏览历史 `removeMany` 批量删除、`HistoryCore.deleteRecords` 批量删史）并修复阅读历史筛选记忆缺少 `records` 依赖键导致的删后不刷新问题。
- **2026-11-22 · 全仓基础控件（按钮 / 开关 / 搜索框 / 分段选择框）与毛玻璃多选栏统一及规范归档**（`ui/DesktopMacControls.kt`、`ui/components/DesktopSearchField.kt`、`gallery/ui/selection/DesktopSelectionDock.kt`、`gallery/ui/DesktopGallerySearchScreen.kt`、`gallery/ui/GallerySearchControls.kt`、`gallery/ui/DesktopGalleryChrome.kt`、`gallery/ui/DesktopGalleryTopSearch.kt`、`detail/DesktopComicDetailHero.kt`、`detail/DesktopComicDetailSections.kt`、`installer/InstallerChrome.kt`、`DesktopWallScaffold.kt`、`favorites/DesktopFavoritesScreen.kt`、`explore/DesktopExploreScreen.kt`、`gallery/ui/DesktopGalleryFavoritesWall.kt`、`DesktopBackupScreen.kt`、`DesktopErrorReport.kt`、`downloads/DesktopLocalImageLibraryScreen.kt`、`reader/DesktopReaderHud.kt`、`reader/DesktopReaderSettingsOverlay.kt`、`settings/*`、`search/*`、`follow/DesktopFollowUpdatesScreen.kt`、`sourcemanage/DesktopSourceManageScreen.kt`、`AGENTS.md`、`docs/designs/desktop-ui-standard-2026-11-05/controls-standard-spec.md`）
  - **说明**：① 新增统一按钮组件 `DesktopButton`（继承详情页主色实底 / 半透微底 + 0.5dp 细描边质感，提供 `Large 40dp / Medium 32dp / Compact 26dp` 三档尺寸及默认/悬停/按压 0.96/焦点/禁用/加载全状态过渡），并将 `DesktopPushButton`、`HeroActionButton`、`ReadCapsule`、安装向导按钮、备份页按钮、画廊搜索页「搜索 / 高级筛选 / 筛选钮」、源管理按钮、追更筛选钮、搜索卡浮层按钮、弹窗 M3 按钮与错误卡片小钮全部收编；② 以 `DesktopSwitch`（`36×20dp` / `16dp` 球）为全仓唯一开关，替换画廊搜索开关、画廊顶栏 R18 开关、聚合搜索开关、阅读器 HUD 浮层、设置弹窗 `DialogSwitch`、屏蔽规则页与设置总览里的原生/自绘开关；③ 巩固 `DesktopSearchField`（优化清除圆点图标内缩边距）与 `DesktopSegmented`（补齐平滑过渡与发丝描边），并将画廊搜索页模式切换与全部下拉菜单分别统一到 `DesktopSegmented(large = true)` 与 `DesktopPopUpButton`；④ 将 `DesktopSelectionDock` 升级为 `16dp` 圆角 Haze 毛玻璃透明岛，为四大列表/网格宿主屏接入 `HazeState` 模糊源，内部「已选 / 全选 / 反选 / 关闭 / 动作」按钮统一接入 `DesktopButton(Medium)`；⑤ 新增 `controls-standard-spec.md` 并在 `AGENTS.md` 登记为后续新 UI 硬性规范。
- **2026-11-22 · 全仓输入场 / 切换器 / 选择框统一到画廊标准**（`ui/components/DesktopSearchField.kt`、`ui/DesktopMacControls.kt`、`ui/DesktopMacDialog.kt`、`DesktopFeedbackPanel.kt`、`downloads/DesktopFilesHubScreen.kt`、`history/DesktopHistoryScreen.kt`、`gallery/ui/*`、`search/*`、`settings/*`、`sourcemanage/*`、`pixiv/*`、`detail/*`、`favorites/*`、`installer/InstallerScenes.kt`、`compose/feature/ComicDetailScreen.kt`、`docs/designs/desktop-ui-standard-2026-11-05/input-control-spec.md`）
  - **说明**：① 新增 `DesktopInputField` 为唯一输入场，几何与画廊顶栏搜索场一致（34dp / r8 / 1dp 描边，悬停转 frame、聚焦转 accent 2dp），`DesktopSearchField` 改为其搜索薄壳；② 替换桌面全部单行输入：搜索屏 / 设置中枢 / 设置总览 / 历史 / 收藏 / 图库收藏 / 关注画师 / 以图搜图 / 来源搜索 / 搜索筛选 / 备份 / 反馈 / 收藏夹命名 / 漫画详情 / 章节搜索 / 来源登录 / 来源设置 / Pixiv 登录 / 屏蔽规则 / 阅读器跳页 / API Key 等，共享 `VeneraTextField` 仅留给共享屏与代码编辑器；③ 切换器收口到 `DesktopSegmented`，退役零调用的 `VeneraSegmentedButton`，反馈面板 / 文件中心 / 历史排序 / 对话窗四颗自制分段全部改走统一件；④ 选择框收口到 `DesktopPopUpButton`，搜索筛选下拉与 Pixiv 可见性下拉改走统一件。
- **2026-11-22 · 顶部胶囊报错提醒重做与问题反馈（GitHub Issue）集成**（`DesktopErrorReport.kt`、`DesktopFeedbackPanel.kt`、`ui/DesktopCommandBar.kt`、`ui/DesktopWindowTitleBar.kt`、`ComicDetailCore.kt`、`DesktopComicAndFilesEffects.kt`、`VeneraDesktop.kt`、`DesktopVideoLauncher.kt`、`DesktopCopyImage.kt`、`GalleryArtistAvatarStore.kt`、`DesktopHttpEngine.kt`、`DesktopProtocolRegistrar.kt`、`DesktopSourceContentPresets.kt`、`engine-probe/EngineSession.kt`、`engine-probe/DesktopCookieJar.kt`）
  - **说明**：① 将原居中白底报错弹窗重做为跟随深浅主题的透明磨砂「顶部报错胶囊 + 轻量下拉面板」（无错隐藏、新报错滑出摘要并自动缩成角标），支持「全部 / 漫画 / 图库 / 系统」筛选、大白话原因展示与技术栈详情折叠；② 顶栏最小化按钮左侧常驻「反馈」入口，提供「问题反馈 / 新功能建议」双 Tab 滑块面板，支持关联最近报错（含错误卡片「去反馈」双向跳转）、本地选图与 `Ctrl+V` 剪贴板贴图，点击提交自动复制截图并跳转 GitHub 预填 Issue 页面；③ 接入漫画、图库、系统共 14 处高价值隐藏报错上报点。
- **2026-11-22 · 按钮状态与过渡硬规格**（`AGENTS.md`、`docs/designs/desktop-ui-standard-2026-11-05/button-state-spec.md`、`docs/designs/desktop-ui-standard-2026-11-05/README.md`）
  - **说明**：新增 UI 按钮必须多状态且每态有过渡，至少默认 / 悬停 / 按压 / 焦点 / 禁用五态，异步动作另加加载态，单态按钮与瞬切状态一律不合格；按钮一律走统一件（`VeneraButton` / `VeneraIconButton` / `VeneraTextButton` / `VeneraChip` / `VeneraFilterPill` / `VeneraSegmentedButton` / `VeneraSelectBox` / `DesktopGlassCard` 透传 `pressFeedback`），自拼可点面必须挂 `VeneraPressFeedback` 并自绘悬停过渡。落到 `AGENTS.md`「动效统一」第 4、5 条，执行细则与禁例归入新建的 `button-state-spec.md`，上游为 UI 标准 README §4.4 组件状态契约；存量屏不追溯。
- **2026-11-22 · 卡片入场动画单源化与五屏补齐**（`ui/tokens/VeneraMotion.kt`、`gallery/ui/DesktopGalleryCard.kt`、`ui/DesktopPageTemplates.kt`、`explore/DesktopExploreScreen.kt`、`gallery/ui/DesktopArtistInboxScreen.kt`、`gallery/ui/DesktopPixivCollectionDetailScreen.kt`、`history/DesktopHistoryScreen.kt`、`sourcemanage/DesktopSourceManageScreen.kt`、`feature/HistoryScreenBody.kt`、`AGENTS.md`、`docs/02-architecture/architecture.md`）
  - **说明**：把画廊卡片的入场口径（错峰 35ms × 上限 6 档、低弹弹簧、淡入 + 上浮 14dp + 0.985 微缩放、`rememberSaveable` 防重放）提炼为 `VeneraMotion` 的 `VeneraCardEnter` / `rememberCardEnterProgress` 单源工具，参数全部落令牌；画廊卡片本体改为吃标准工具；退役零调用的旧入场工具 `VeneraEnterVisibility` / `rememberEnterState`。同批给五屏补齐入场：漫画探索（分类网格与 `GridPageTemplate` 墙体，后者惠及收藏墙 / 搜索屏 / pixiv 账号屏）、关注画师、珍藏册详情三处迷你墙、阅读历史（桌面自制屏时间轴与共享 `HistoryScreenBody`）、站点墙三段（大卡 / 普通卡 / 未启用索引带）。`AGENTS.md` 新增「动效统一」节：新功能卡片入场只认标准工具，页面转场只认 `VeneraMotion` 预设，禁散写时长、错峰与弹簧。
- **2026-11-22 · 漫画卡片全局布局（自适应 / x 行 × y 列）、收藏侧栏收起展开动画与追漫卡片改造**（`settings/DesktopComicCardLayout.kt`、`settings/DesktopSettingsHub.kt`、`favorites/DesktopFavoritesHubScreen.kt`、`favorites/DesktopFavoritesScreen.kt`、`favorites/DesktopNetworkFavoritesScreen.kt`、`follow/DesktopFollowUpdatesScreen.kt`、`search/DesktopSearchScreen.kt`、`explore/DesktopExploreScreen.kt`、`downloads/DesktopLocalComicLibraryScreen.kt`、`feature/HistoryScreenBody.kt`）
  - **说明**：① 新增独立「漫画卡片布局」设置（自适应 / 自定义 1～6 行 × 1～8 列），全站漫画卡片页面的网格与双列模式统一接入；② 本地收藏、网络收藏与追漫中心补齐「网格 / 双列」视图切换，追漫中心改用漫画卡片风格与错峰渐显上浮载入动画并保留全量操作按钮；③ 收藏页左侧书库面板改用透明玻璃材质与真实站点图标，并新增「收起 / 展开」非线性弹簧过渡动画（收起态仅展示图标与角标）。
- **2026-11-22 · 漫画追更（账本 / 巡检判定 / 详情追更 / 追更中心）**（`data/db/ComicFollowDao.kt`、`follow/*`、`detail/DesktopComicDetailHero.kt`、`favorites/DesktopFavoritesHubScreen.kt`、`DesktopTabHost.kt`、`DesktopLargeScreenRouter.kt`、`docs/01-product/features/2026-11-22-comic-follow-updates.md`）
  - **说明**：`venera_core.db` 升 v4 新增 `comic_follow` 与 `comic_follow_sync_source` 两表，追更账本独立于收藏账本；更新判定走双因子（总话数 / 末话 ID / 非空标题变化，无章节回退更新时间），首轮只建基线不标红、源删章不误报、未读红点未消时新更累加；详情页 Hero 新增「追更」钮（追更 / 已追更 / 正在写 / 正在读四档），进入详情即读取态并清红点；收藏中心左侧书库新增「追更」入口（未读角标）与独立追更视图：筛选胶囊（全部 / 有更新 / 追更中 / 已完结·暂停 / 异常）、串行巡检的进度与「停止检查」、更新置顶卡片（更新至第 N 话 +M 话）、单本重查 / 暂停恢复 / 标完结 / 取消追更。巡检器由路由层持引擎构造，同源 1800ms、跨源 400ms 节流，6 小时冷却，同源连续 3 次失败熔断该源本轮剩余；网站收藏页顶栏可把当前夹「加入追更 / 同步追更」（分页拉取、单次上限 200 本、两段式确认与随时停止，已订阅夹与已追条目在卡面标出），本地收藏右键补「加入追更」；追更两表随「收藏」域进入整包 `.venera` 备份与恢复（覆盖/合并两档都支持）；设置页新增「启动时自动检查」（默认关）与「巡检冷却」（6 / 12 / 24 小时三档）。
- **2026-11-22 · 自研 7z SFX 云母安装器与打包链路**（`desktop/installer/InstallerEngine.kt`、`InstallerWindow.kt`、`desktop/build.gradle.kts`）
  - **说明**：移除 Inno Setup，接入真实文件复制、旧版 MSI/Inno 清理、快捷方式与 HKCU 卸载注册表逻辑；新增 `:desktop:packageSetupInstaller` 任务，自动提取 `7zSD.sfx` 并打包生成调用内置 `runtime` 拉起云母玻璃安装向导的单文件 `setup.exe`。
- **2026-11-22 · 全站透明玻璃卡片规范与共用原语**（`ui/components/DesktopGlassCard.kt`、`docs/designs/desktop-ui-standard-2026-11-05/glass-card-spec.md`）
  - **说明**：提炼站点同款透明卡片为新建/重做功能页默认规范与 `DesktopGlassCard` 原语，区分 `Primary` / `Nested` 两级材质并支持透底/实底自动切换，禁止叠加 `Modifier.shadow`。
- **2026-11-22 · 珍藏册入口移入侧栏画廊分组**（`DesktopShellPanes.kt`、`DesktopGalleryHomeRows.kt`、`DesktopPixivCollectionsScreen.kt`）
  - **说明**：将「珍藏册」独立放进侧栏画廊分组，移除画廊与珍藏册页顶部的「推荐 | 珍藏册」切换胶囊条，简化页头为标题与返回按钮。

---

## Bug 修复（Bug Fixes）

- **2026-11-22 · 下拉选择器菜单向上生长盖住触发按钮**（`ui/DesktopMacControls.kt`）
  - **原因**：`Popup` 的 `alignment` 是相对父布局的对齐，`DesktopPopUpButton` 旧值用 `BottomStart` 让菜单底边贴触发面底边、向上展开，视觉上盖住「评分 / 全部 ⇅」这一整块。
  - **解决方案**：改 `TopStart` 并按触发面实测高度下移 4dp，菜单从下缘向下展开；锚点高度用 `onGloballyPositioned` 实测，不写死档位。
- **2026-11-22 · 修复报错胶囊与反馈面板未跟随深色主题、底色透字及图床 403 噪音推送**（`VeneraDesktop.kt`、`DesktopErrorReport.kt`、`DesktopFeedbackPanel.kt`）
  - **原因**：① `DesktopErrorToastOverlay()` 挂载于 `VeneraDesktopApp` 外部，未进入 `DesktopVeneraTheme` 主题作用域，导致读到 Material3 默认浅色紫色板；② 浮层底色透明度过低且无底层局部模糊，导致底层白色文字透出重影；③ `reportImageError` 仍向胶囊推送高频图床 403 噪音。
  - **解决方案**：在 `VeneraDesktop.kt` 中为 `DesktopErrorToastOverlay()` 包裹 `DesktopVeneraTheme`；将胶囊、下拉面板与反馈面板底色抬高至高不透实底档（`0.96f~0.97f`）；将 `reportImageError` 收敛为仅留运行日志，不再刷屏顶部胶囊。
- **2026-11-22 · 补齐禁漫备用域名过盾豁免并区分 S3 签名过期 403 提示**（`DesktopCloudflareCenter.kt`、`DesktopErrorReport.kt`）
  - **原因**：`jm` 部分备用/图床域名未在 `isBypassExemptHost` 列表中，且 `DesktopErrorReport` 将全部 HTTP 403 统一提示为“被站方防盗链或盾拦截”，导致 S3 预签名地址过期（`X-Amz-Date`）也被误报为撞盾。
  - **解决方案**：在 `isBypassExemptHost` 补入 `cdnhjk`、`cdngwc`、`cdn-msp`；`humanizeImageError` 针对带 `X-Amz-` 签名参数与禁漫系域名的 403 单独提示为签名过期或防盗链失效。
- **2026-11-22 · 修复追更网站收藏分页截断、引擎串锁、占位章不判更与恢复不刷新等 4 处缺陷**（`DesktopNetworkFollowImporter.kt`、`NetworkFavoritesFollowSource.kt`、`EngineFollowDetailSource.kt`、`DesktopFollowDetailPort.kt`、`DesktopFollowUpdateChecker.kt`、`DesktopVeneraArchive.kt`、`DesktopLargeScreenRouter.kt`、`DesktopTabHost.kt`、`ComicFollowDao.kt`、`DesktopAppSupport.kt`）
  - **原因**：① 网站收藏导入器把 `next == null` 当成翻页终点，导致仅靠 `maxPage` 翻页的源第 2 页起被截断；② 导入器直接复用主窗口 `EngineSession` 且未隔离上下文，并发浏览会串源/抢锁；③ 占位章只清零话数与标题而保留了 `latestChapterId`，导致无章节源走不进 `remoteUpdateTime` 副判据；④ `.venera` 恢复写库后未通知 `ComicFollowDao` 刷新内存流；另顺手修复改冷却时长重建巡检器、关窗漏关追更作用域及本地右键追更无提示。
  - **解决方案**：导入器改以 `hasMore` 与空页判定终止并派生独立临时会话、结束统一 `close()` 释放；占位章同步置空 `latestChapterId`；备份恢复事务外触发 `comicFollowDao().refresh()`；巡检器改为动态读取冷却时长；`closePersistenceInOrder()` 补 `ComicFollowDao.closeIfInitialized()`；本地收藏右键加入追更补 Toast 反馈。
- **2026-11-22 · 圆角与色值两把守卫尺把安装向导算进漫画 UI，基线起全量存量红灯**（`DesktopShapeLiteralTest.kt`、`DesktopSurfaceLiterals.kt`、`DesktopMaskJudgementSingleSourceTest.kt`）
  - **原因**：`24afb1d0` 重做安装向导后 `installer/` 自带 Light/Dark 两套 `InstallerPalette`（5 处写死色值）与 60/64dp 图标容器的 18dp 圆角，两把守卫尺仍按漫画 UI 口径扫全部桌面自有片，基线 `26eb3141` 上就已有这 2 项红（本次全量 1771 项里暴露）。另追更中心接线新增一处 `ux.coverMaskStateOf` 消费者，遮罩判据计数 10→11 未记说明。
  - **方案**：`installer/InstallerSpec.kt` 记入色值合法居所、`installer/` 目录排除出圆角令牌尺（均注明"独立视觉层，不属于漫画 UI 令牌账"）；遮罩判据计数改 11 并补"与收藏/网络收藏同一颗判据"的书面说明。产品代码未改。
- **2026-11-22 · 自研 setup.exe 触发火绒拦截、点安装瞬间闪退及补全原程序卸载入口**（`InstallerEngine.kt`、`InstallerWindow.kt`、`InstallerScenes.kt`、`VeneraDesktop.kt`、`desktop/build.gradle.kts`、`scripts/set-exe-icon.ps1`）
  - **原因**：原版调用 `powershell.exe` 触发火绒拦截；`terminateOtherAppProcesses` 仅排除 `selfPid` 而按文件名匹配 `venera-desktop.exe`，在 `jpackage` 启动器进程树下会误杀关联宿主进程导致点安装瞬间退出无异常日志。
  - **方案**：彻底移除 `powershell.exe` 改用 JNA 原生 API 与 `ProcessHandle`；`terminateTargetAppProcess` 改为仅按规范化路径匹配目标安装目录的 `venera-desktop.exe` 并增加分步日志；在选项页检测到已有原程序时新增「卸载原程序」按钮直达 `UninstallScene` 执行清理（保留用户数据）。
- **2026-11-22 · 设置弹窗二级图标高度重复、卡片密度空壳与文本防抖丢字**（`SettingsDialogShell.kt`、`DesktopLargeScreenChrome.kt`、`VeneraDesktopLargeScreen.kt`）
  - **原因**：`dialogRowIcon` 仅按控件类型硬映射导致满屏同质图标；`DesktopSettingsOverlayLayer` 漏传 `wallDensity` / `onPickWallDensity` 使卡片密度成为空壳；`DialogTextControl` 依赖 700ms 延迟提交，快关弹窗时最后输入未保存。
  - **方案**：`dialogRowIcon` 改为按设置项标题语义匹配 Material Outlined 图标；透传 `state.wallDensityState` 读写口打通卡片密度；为 `DialogTextControl` 增加 `DisposableEffect` 离场兜底提交。
- **2026-11-22 · 设置弹窗背板未全窗口模糊且点击内部空白误退出**（`SettingsDialogShell.kt`、`VeneraDesktopLargeScreen.kt`、`DesktopLargeScreenChrome.kt`、`DesktopLargeScreenRouter.kt`）
  - **原因**：设置叠层及 `Modifier.blur` 仅挂载在主内容区内部，导致侧栏与顶栏未被遮罩模糊且边缘发虚；`SettingsDialogShell` 根容器未拦截点击，事件穿透到外层遮罩触发关闭。
  - **方案**：将 `DesktopSettingsOverlayLayer` 移至全窗口层覆盖侧栏、顶栏与内容区，把 `Modifier.blur(12.dp, BlurredEdgeTreatment.Rectangle)` 统一挂在底层壳容器上；在 `SettingsDialogShell` 根容器增加无涟漪 `.clickable` 拦截内部点击穿透。

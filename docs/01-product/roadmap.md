# 产品路线

当前版本 `2.1.1`。本文只记录代码里能查到的能力与缺口，每条附代码依据。已实现标 `[x]`，未实现或不完整标 `[ ]`。

## 1. 已实现能力

### 1.1 导航骨架

| 状态 | 内容 | 依据 |
|---|---|---|
| [x] | 一级入口：首页 / 漫画搜索 / 画廊 / 收藏 | `VeneraDesktopLargeScreen.kt:8122-8184` |
| [x] | 侧栏二级分组：画廊组、漫画组、工具组 | 同上 |
| [x] | 侧栏底部：设置、下载与本地、pixiv 账号卡 | 同上 `:4374-4519` |
| [x] | 二级路由 34 个，含详情、阅读器、队列、统计、备份等 | `DesktopRoute.kt` |
| [ ] | 探索一级标签隐藏（仍可从搜索页内切换进入） | `VeneraDesktopLargeScreen.kt:4421` |

### 1.2 漫画

| 状态 | 能力 | 依据 |
|---|---|---|
| [x] | 探索：分类面板、排行榜真接引擎 | `explore/DesktopExploreScreen.kt:61-74` |
| [x] | 详情：章节表、简介、标签、推荐、已读进度 | `detail/DesktopComicDetailScreen.kt:250-300` |
| [x] | 继续阅读胶囊（页码 + 进度线） | 同上 `:157-195` |
| [x] | 搜索：单源、多源聚合、来源选择、页尾四态、视图切换 | `search/DesktopSearchScreen.kt:104-199` |
| [x] | 搜索历史落盘 | `search/DesktopComicSearchHistory.kt` |
| [x] | 阅读器四档模式：条漫连续 / 对开双页 / 日漫 / 美漫 | `gallery/ui/DesktopReaderPaging.kt:35-40` |
| [x] | 缩放三档 + 滚轮 0.5–4 倍 + 双击往返 + 拖拽平移 | `reader/DesktopReaderScreen.kt:140-141` |
| [x] | 翻页动画：导览视图 / 淡出 / 3D 翻页 | `reader/DesktopBookTurnViewport.kt`、`DesktopGuidedViewEngine.kt` |
| [x] | 键位全套（方向键、PageUp/Down、Home/End、空格等） | `gallery/ui/DesktopKeyNavigation.kt:125-130` |
| [x] | 沉浸态、自动翻页、翻页音效、书签、放大镜 | `DesktopReaderScreen.kt:137-175,274,347,570` |
| [x] | 滤镜：亮度/对比度/饱和度/色温/黑白/负片 + AI 去痕/去网纹/超分 | `reader/DesktopReaderPreferences.kt`、`DesktopAiUpscaleManager.kt` |
| [x] | 目录切话、预取下一话 | `DesktopReaderScreen.kt:165-175` |
| [x] | 评论：详情评论只读，含分页与五档状态 | `detail/DesktopCommentsScreen.kt:25-37` |
| [x] | 阅读统计：今日/本周/连续、14 天趋势、Top 10、热力、环比 | `DesktopReadingStatsScreen.kt:59-73` |
| [x] | 阅读历史：时间轴、源筛选、搁置标记、时段分布、两按清空 | `history/DesktopHistoryScreen.kt:78-90` |

### 1.3 下载与本地

| 状态 | 能力 | 依据 |
|---|---|---|
| [x] | 下载队列：多话排队、重启续排、去重、FIFO、状态五档 | `gallery/data/DesktopComicDownloadQueue.kt:8-60` |
| [x] | 协作式暂停与恢复、单笔移除、批量删除、速度与进度 | `downloads/DesktopDownloadsPipelineScreen.kt:405-440,930-1000` |
| [x] | 本机已下载：话级清单、页数、缺页判定、补齐、两步删除、打开目录 | `downloads/DesktopComicDownloadsScreen.kt:50-110` |
| [x] | 本地漫画：裸目录本子识别、题名念出、CBZ/ZIP 导入、导入本可阅读 | `downloads/DesktopLocalComicLibraryScreen.kt:49-70` |
| [x] | 离线阅读：整话齐则读盘，缺页回源 | `gallery/data/DesktopComicOfflineReading.kt` |
| [x] | 图片库：站点与画师分组、计数、两步删除 | `downloads/DesktopLocalImageLibraryScreen.kt:54-60` |
| [ ] | 裸目录本子不可点开（无落地阅读口） | `DesktopLocalComicLibraryScreen.kt:53-60` |
| [ ] | 无导出 CBZ、无批量删 | 同上 `:60-62` |
| [ ] | 图片域无下载队列（存图同步落盘） | `downloads/DesktopFilesHubScreen.kt:47-48` |

### 1.4 收藏

| 状态 | 能力 | 依据 |
|---|---|---|
| [x] | 本地收藏主屏：网格、点开、移出、多选、悬浮坞 | `favorites/DesktopFavoritesScreen.kt:88-90,187-205` |
| [x] | 收藏夹：新建、删除、切换、按夹筛选、计数与排序 | `gallery/data/DesktopFavoriteFolders.kt` |
| [x] | 详情页选夹（单选语义，点夹即落笔） | `detail/DesktopComicDetailScreen.kt:274-280` |
| [x] | 收藏 hub：本地 / 画廊 / 网络三路并列 | `favorites/DesktopFavoritesHubScreen.kt:53-140` |
| [x] | 网络收藏：按登录态分组、文件夹平铺、续页 | `favorites/DesktopNetworkFavoritesScreen.kt:57-90` |
| [x] | 画廊收藏墙：多选、批量取消、两步清空 | `gallery/ui/DesktopGalleryFavoritesWall.kt:32-50,235-265` |
| [x] | 追更：独立账本、详情一键追更、串行巡检、追更中心、网站收藏导入 | `follow/DesktopFollowUpdateChecker.kt`、`follow/DesktopFollowUpdatesScreen.kt`、`data/db/ComicFollowDao.kt` |
| [x] | 收藏更新检测（改由追更账本承担，不回写收藏表角标） | `follow/FollowUpdateDiff.kt` |
| [ ] | 坞内批量下载与移动文件夹无写口 | 同上 |

### 1.5 图库

| 状态 | 能力 | 依据 |
|---|---|---|
| [x] | 主墙：瀑布流 / 等宽网格、密度三档、卡片大小三档 | `gallery/ui/DesktopWallSpec.kt:10-30` |
| [x] | 翻页、续页、页尾四态、滚动锚点保留 | `DesktopGalleryWallFooter.kt`、`DesktopWallReadings.kt` |
| [x] | 来源筛选：评分四档、排序三档、子源胶囊 | `DesktopGalleryFilterRow.kt:43-105` |
| [x] | 顶栏搜索（booru 与 pixiv 双腿）、多选批量动作 | `VeneraDesktopLargeScreen.kt:6798-6814,9187-9250` |
| [x] | 标签搜索：浮层历史与联想、高级筛选、来源轴、分页 | `gallery/ui/DesktopGallerySearchScreen.kt:103-218` |
| [x] | 以图搜图：SauceNAO + ascii2d 同屏、四通道输入 | `gallery/ui/DesktopReverseSearchPane.kt:132-138` |
| [x] | 收藏与历史一页两视图、回原卡 | `gallery/ui/DesktopGallerySavedScreen.kt:68-120` |
| [x] | 全屏看图：画质三档、缩放、翻页、收藏、下载、信息抽屉 | `gallery/ui/DesktopGalleryPostViewer.kt:89-210` |
| [x] | 视频 / 动图 / Ugoira 就地播放 | `DesktopVideoPlayer.kt`、`DesktopUgoiraPlayer.kt` |
| [x] | 作品详情页：左画布 + 右信息栏 + 标签五桶 | `gallery/ui/DesktopGalleryPostDetailScreen.kt:93-106` |
| [x] | 每日热门：三栏编辑台，各自续页与终态 | `gallery/ui/DesktopDailyEditorialScreen.kt:56-78` |
| [x] | 关注画师收件箱：双源分轴、新作水位 | `gallery/ui/DesktopArtistInboxScreen.kt:91-113` |
| [x] | 站点墙：启停、搜索、过滤、拖拽重排、导入脚本 | `sourcemanage/DesktopSourceManageScreen.kt:86-145` |
| [x] | 源账号登录、cookie 登录、脚本编辑、撤销改写 | 同上 `:135-145` |
| [x] | 源自动更新、线路测速 | `DesktopSourceUpdater.kt`、`DesktopSourceLatencyProbe.kt` |
| [x] | pixivision 文章页、珍藏册发现与详情 | `DesktopGalleryPixivisionArticleScreen.kt:47-58` |
| [ ] | 珍藏册 rail「查看全部」无落点，故不摆 | `DesktopPixivCollectionsScreen.kt:366-368` |

### 1.6 Pixiv

| 状态 | 能力 | 依据 |
|---|---|---|
| [x] | 登录三条路：PKCE 回跳、粘贴链接、粘贴 token | `pixiv/DesktopPixivLoginScreen.kt:38-53` |
| [x] | 账号页四段：作品 / 动态 / 收藏 / 已关注 | `pixiv/DesktopPixivHubParts.kt:47,186` |
| [x] | 作品页：多图竖排、作者行、标签墙、评论、相关作品、上一作下一作 | `pixiv/DesktopPixivWorkScreen.kt:131-250` |
| [x] | 收藏与关注写站方 | 同上 `:2195-2260,242-250` |
| [x] | 画师页：横幅、头像、统计、关注、三段作品、续页 | `pixiv/DesktopPixivArtistScreen.kt:60-75` |
| [x] | 日 / 周 / 月榜真取数 | `VeneraDesktopLargeScreen.kt:9415-9417` |
| [x] | 跨站跳转：结果卡画师名、标签交接搜索、pixivision 搜画师 | `DesktopSearchHandoff.kt` |
| [x] | 系统浏览器打开、复制链接、分享卡片 | `DesktopOpenInBrowser.kt:42`、`DesktopCopyLink.kt:151-155` |
| [ ] | Pixiv 书签独立屏未接（账号页收藏段可用） | `DesktopRoute.kt:326-334` |

### 1.7 设置与外观

| 状态 | 能力 | 依据 |
|---|---|---|
| [x] | 主题深/浅/跟随系统 | `settings/DesktopThemeMode.kt:8-45` |
| [x] | 中英双语切换（设置中枢与导航骨架支持即改即生效与冷启动回填） | `settings/DesktopLanguage.kt:8-37` |
| [x] | 底板 Mica / Acrylic / 实底，侧栏与顶栏模糊独立开关 | `DesktopBackdropMode.kt`、`DesktopRailBlur.kt` |
| [x] | 界面缩放 80–150，封面画幅四档，网格密度六档 | `DesktopUiScale.kt`、`DesktopCoverAspect.kt`、`DesktopGridDensity.kt` |
| [x] | 吉祥物六参、桌宠显隐、精简特效总开关 | `DesktopMascot.kt`、`DesktopEffects.kt` |
| [x] | 阅读器设置全域（模式、底纹、滤镜、动画、预加载等） | `DesktopSettingsHub.kt:959-1290` |
| [x] | 网络设置：代理三档、图床反代、缓存上限、下载并发、命名模板 | 同上 `:1564-1700` |
| [x] | 数据与备份：整包归档、缓存清理、WebDAV 同步 | 同上 `:1443-1780` |
| [ ] | 导航动效无用户档位（固定常量） | `DesktopNavMotion.kt:11-45` |

### 1.8 系统能力

| 状态 | 能力 | 依据 |
|---|---|---|
| [x] | 代理三档，冷启动读盘 | `platform/DesktopHttpEngine.kt:70-90` |
| [x] | 漫画源 Cloudflare 过盾（Edge + CDP） | `platform/DesktopCloudflareBridge.kt:20-25` |
| [x] | 备份还原：整包归档、模块勾选、旧版 JSON 兼容、二次确认 | `DesktopVeneraArchive.kt`、`DesktopBackupScreen.kt` |
| [x] | WebDAV 同步：上传归档、取回最新 | `platform/DesktopWebDavSync.kt:20-31` |
| [x] | 单实例、协议回传（`pixiv://`、`venera://`） | `DesktopSingleInstance.kt`、`DesktopProtocolRegistrar.kt` |
| [x] | 系统托盘、崩溃处理、错误日志面板、运行日志 | `DesktopSystemTray.kt`、`DesktopCrashReading.kt`、`DesktopErrorReport.kt` |
| [x] | 窗口记忆、全局快捷键 | `VeneraDesktop.kt:891+`、`ui/DesktopGlobalShortcuts.kt:99-155` |
| [x] | 更新检查（仅设置页手动，有新版本开下载页） | `platform/DesktopUpdateCheck.kt:12-24` |
| [x] | 安装向导独立窗口：四态串联、目录面板随窗延展、`installer` 运行任务 | `installer/InstallerWindow.kt` |
| [ ] | 画廊 HTTP 层无交互式过盾、无每主机熔断与全局限流 | `DesktopHttpEngine.kt:22-27` |
| [ ] | 无启动自动检查更新，无应用内自动更新 | `DesktopSettingsHub.kt:1867`、`DesktopUpdateCheck.kt:22-24` |

## 2. 已知缺口清单

| 类别 | 缺口 | 依据 |
|---|---|---|
| 漫画 | 章节评论无桥，明确不做 | `DesktopRoute.kt:300-306` |
| 阅读器 | 背景色与适配档只活本会话 | `reader/DesktopReaderScreen.kt:147-150` |
| 阅读器 | 双页封面独占右页位移对齐、跨页合并未做 | 同上 |
| 阅读器 | 音量键 / 侧键翻页无桌面键位 | 同上 |
| 阅读器 | 横向连续档未上屏（枚举保留） | `gallery/ui/DesktopReaderPaging.kt:27-40` |
| 下载 | 队列端口暂停族为 NoOp，屏走队列件直连 | `business/DesktopDownloadQueuePort.kt:96-102` |
| 登录 | 无内嵌 WebView（桌面无宿主，属取舍） | `pixiv/DesktopPixivLoginScreen.kt:50-53` |
| 登录 | 源密码登录需源脚本自行重写 | `compose/source/ComicSource.kt:223-229` |
| 安装 | 自研云母安装器已接通文件释放、快捷方式与卸载登记，卸载入口仍走轻量 `.cmd` 清理脚本 | `installer/InstallerWindow.kt`、`installer/InstallerEngine.kt` |

## 3. 下一阶段规划

### P0 发布门槛

| 状态 | 事项 | 完成口径 |
|---|---|---|
| [x] | 清全量测试红灯 | 2026-11-21 清零：删 1 条过期迁移用例后全量 1695 项 0 失败（`8dd4e1c3`、`d4c2a50e`） |
| [x] | 修迁移空库锁死 | 已改逐表数行数判据，空库不再落迁移标记（`DesktopLegacyFavoriteMigration.kt:120`） |
| [x] | 修 prime 并发写穿透 | 同根只读一次并与认领共用一把闸（`DesktopComicDownloadQueue.kt:286-289`） |
| [ ] | 分享卡片截屏不越界 | 只截卡片区域，不包含遮挡窗口内容（审计 S1，待复核是否已修） |
| [ ] | 过盾 CDP 端口加鉴权 | 端口仅本机可连，带随机凭据（审计 S3，待复核是否已修） |
| [ ] | 补真机发布检查 | `03-guides/build-and-release.md` 第七节全部通过 |

### P1 体验补齐

| 状态 | 事项 | 理由 |
|---|---|---|
| [x] | 顶部胶囊报错提醒与问题反馈集成 | `DesktopErrorReport.kt`、`DesktopFeedbackPanel.kt` 已落地，接入 14 处隐藏报错并对接 GitHub Issue 预填提交 |
| [ ] | 章节评论桥接 | 章节评论仍无 Kotlin 桥，阅读器内无法回帖（详情页发评论已接） |
| [ ] | Pixiv 书签独立入口 | 现在只有坦白页，账号页收藏段是唯一路径 |
| [x] | 收藏更新检测 | core v4 追更账本 + 串行巡检已落地（计划 2026-11-22 六阶段） |
| [ ] | 裸目录本子点开阅读 | 本地库能识别却不能读，落差最明显 |
| [ ] | 阅读器会话外持久化 | 背景色与适配档每次重启复位 |
| [ ] | 启动自动检查更新 | 更新检查只在设置页有调用点 |

### P2 能力扩展

| 状态 | 事项 | 理由 |
|---|---|---|
| [ ] | 导出 CBZ / 本地批量删 | 本地库目前只进不出 |
| [ ] | 图片域下载队列 | 存图同步落盘，大图集会卡界面 |
| [ ] | 画廊 HTTP 层交互式过盾 | 部分站点在图库侧无路可过 |
| [ ] | 应用内自动更新 | 现在只提示去发布页 |
| [ ] | 珍藏册查看全部页 | rail 无落点，入口不摆 |
| [ ] | Microsoft Store 上架与 MSIX 双轨打包 | 纯净空壳版 MSIX 打包、协议清单静态声明、双轨构建（见 `features/2026-11-24-microsoft-store-msix.md`） |

### P3 工程债

| 状态 | 事项 | 理由 |
|---|---|---|
| [ ] | 接入 ktlint 与 detekt（存量冻结、增量拦截） | `docs/CODE_REVIEW.md` 自陈 L1 缺失 |
| [ ] | 补 CI | 仓内无 workflow，全部校验靠本地 |
| [x] | 修正 README | 2026-11-21 完成：中英两版与代码对齐，截图压缩至 2.8MB |
| [ ] | 拆分超大文件 | 根屏单文件 9558 行 |
| [ ] | 清理历史残留 | Android 相关 gradle 配置、指向 `app/src/main` 的脚本、永不 apply 的插件别名 |
| [ ] | 统一收藏账本 | 两套收藏表并存，行为差异易出错 |
| [x] | 解耦 README 尺与具体措辞 | 2026-11-21 五条已改钉关键事实短语（回原卡/缺页/对开双页等），措辞可换、事实必须在 |

## 4. 文档维护

四份文档与代码同步的责任在各批次提交者。改动涉及以下任一项时，同批更新对应文档：

| 改动 | 需更新 |
|---|---|
| 新功能、跨屏改动、数据结构变化 | 事前按 `features/_template.md` 填写功能说明；事后更新下表对应文档 |
| 分层、包结构、依赖方向 | `02-architecture/architecture.md` |
| 表结构、迁移、DAO | `02-architecture/database-schema.md` |
| 构建任务、参数、打包链路 | `03-guides/build-and-release.md` |
| 新屏、新能力、关闭已记缺口 | `01-product/roadmap.md` |

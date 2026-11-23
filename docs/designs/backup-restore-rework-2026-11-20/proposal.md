# 备份与还原重做 · 方案稿

规格名:`backup-restore-rework-2026-11-20` · 日期:2026-11-20
状态:**待批**。获批后进三件套,未批前不动码。

依据:主人提供截图、`DesktopBackupScreen.kt`、`DesktopBackup.kt`、`DesktopWebDavSync.kt`、`DesktopSettingsHub.kt`、公开仓库 `w13630039663-bit/venera-miuix` 的 `BackupManager.kt` 格式定义、`desktop/docs/designs/desktop-ui-standard-2026-11-05/README.md`。
配套:`ui-mockup.html`（已产出）；`design-analysis.md` / `design-tokens.md` / `component-spec.md`（获批后补）。

## 1. 结论

采用**跨端 `.venera` 互通工作台方案**：全面兼容安卓端 `venera-miuix` 的 `.venera` ZIP 归档导入与导出（向后兼容旧版桌面单文件 JSON 导入），重做为「顶部本机资产概览 + 左栏打包导出与内嵌 WebDAV 同步 + 右栏拖拽解析预览与分项勾选导入」双栏工作台布局。分三层落地:

| 层 | 目标 |
|---|---|
| 视觉 | 对齐 `VeneraTokens.color` 深浅双色板与表面三级递进（深色细描边、浅色轻卡阴影），统一 `Icons.Outlined` 图标与同心圆圆角 |
| 交互 | 从"单栏 3 格盲盒弹窗式按钮"改为"资产可见、拖拽/选包先解析预览、支持分项勾选与合并/覆盖策略切换、透明报错与跳过计数" |
| 数据 | 从仅导出 3 张核心表升级为全量 `.venera` v5 归档（9 项基础数据 + 可选源账号设置），并保持与既有数据库、管理器单例与 WebDAV 偏好键一致 |

保留全部业务逻辑、数据来源、状态管理与功能入口（包括旧版桌面 JSON 恢复兼容、headless 无文件框保护、防连点状态锁、正则表达式编译校验与既有 WebDAV 存储键）。

## 2. 现状问题

### 2.1 与对标的差距

| 项 | 现状 | 对标 | 差距 |
|---|---|---|---|
| 备份数据覆盖面 | 仅覆盖阅读历史 / 阅读统计 / 屏蔽规则 3 张表【确定 · `DesktopBackup.kt:28-31`】 | 全量 9 项基础数据 + 可选源账号（安卓 `BackupManager` v5） | 缺失漫画收藏与收藏夹、插图收藏、画廊收藏、画廊关注画师、源账号 |
| 归档格式互通 | 自创单文件 JSON，明确不兼容安卓 `.venera`【确定 · `DesktopBackup.kt:33-38`】 | 标准 `.venera` ZIP 归档（含 `meta.json` + 分项 JSON） | 无法从安卓端导入数据，WebDAV 上传虽后缀 `.venera` 实为 JSON 导致跨端互读失败【确定 · `DesktopWebDavSync.kt:84-85`】 |
| 页面布局与信息密度 | 复用了窄长单栏设置模板，只有 3 张文字卡，顶部与右侧大量空旷【估算 · 截图】【确定 · `DesktopBackupScreen.kt:166-175`】 | 桌面端宽屏双栏工作台 + 顶部本机资产条 | 无法提前看到本机有多少条数据、包内有什么数据，操作盲目 |
| 导入安全与控制 | 无解析预览与分项控制，固定覆盖三张表【确定 · `DesktopBackupScreen.kt:35-36`】 | 选包后先解析展示清单，默认智能合并，可选清空覆盖与模块勾选 | 缺乏部分导入能力与合并模式，误操作成本高 |
| WebDAV 同步入口 | 分散在设置中心网络页，无法直达导入预览【确定 · `DesktopSettingsHub.kt:1451-1519`】 | 内嵌于备份与还原工作台，下载后直接送入右侧解析预览 | 现行链路割裂，用户需跳出页面配置后再回来手动选文件 |

### 2.2 体验缺口

1. **格式错配**：`DesktopWebDavSync.kt:84` 上传时使用了 `.venera` 文件名后缀，但内容实际调用 `desktopBackupExportTo` 写入单文件 JSON，与安卓端标准 `.venera` ZIP 格式冲突【确定 · `DesktopWebDavSync.kt:84-88`】。
2. **还原缺乏前置反馈**：目前还原按钮只提示“需二次确认”，用户在打开文件并覆盖数据前看不到目标备份里究竟包含多少条历史/收藏，也无法只还原其中某一类【确定 · `DesktopBackupScreen.kt:112-128`】。
3. **无拖放与状态汇总视图**：当前仅靠点击按钮呼出 AWT `FileDialog`，没有文件拖放区，也没有清晰展示本机当前存量与导入跳过明细（如无效正则或未安装源数量）的汇总视图【估算 · 截图】。

## 3. 方案明细

### 3.1 视觉层

- **整体框架**：沿用桌面壳层（`46dp` 标题栏 + `72dp/264dp` 侧栏），内容区 `padding = 22dp / 28dp`，页头区使用单行主标题「备份与还原」（5 字）与副标题「跨端数据迁移、本机备份与云端同步」（15 字），右侧放置数据库就绪胶囊与打开数据目录按钮。
- **顶部资产概览条**：6 列自适应指标卡（窄屏折行成 3×2），`12dp` 圆角，`SurfaceLevel1` 卡底，展示阅读历史、漫画收藏（含夹数）、插图收藏、画廊收藏与关注、阅读统计、屏蔽规则实时条数。
- **双栏主工作台**：左栏（约 42% 宽）放置「打包导出」卡与「WebDAV 云端同步」卡；右栏（约 58% 宽）放置「导入与还原」工作区卡。卡片圆角 `14dp`、内边距 `16dp`、内部组件圆角 `8~10dp`（遵循同心圆几何规则），深色仅用 `1dp` 细描边与表面抬亮，浅色采用纯白卡底配轻投影。
- **强调色与状态色**：主行动按钮唯一使用 `VeneraTokens.color.primary`（浅 `#0A63F6` / 深 `#0A84FF`）；安全提示/导出源账号开关采用 `ToneCaution`（`#F2B87C` / 浅色对位）；覆盖模式确认与警告条采用 `ToneDanger`；解析成功态采用 `ToneSafe`。

### 3.2 交互层

| 能力 | 做法 | 效果 |
|---|---|---|
| 格式自适应导入 | 导入入口支持文件拖放与系统文件框选取，按文件头魔数自动识别 ZIP(`.venera`) 与 JSON(旧版桌面快照) | 无需用户手动区分格式，安卓备份与旧本机备份无缝同屏导入 |
| 解析预览与分项勾选 | 选定文件后先在内存解析 `meta.json` 及各成员行数，右侧切换为预览清单，支持全选/单选 | 导入前看清来源端、时间与精确条数，可只导入收藏或只导入历史 |
| 冲突策略切换 | 预览区顶部提供分段选择：默认「智能合并」（`INSERT OR REPLACE`/去重追加，保留本机其他数据）；可选「清空并覆盖」 | 兼顾日常跨设备合并与整机重置还原，覆盖模式强制触发红色警示条与二次点击确认 |
| 源账号安全导出/跳过 | 导出卡默认关闭「包含漫画源账号与设置」开关，开启即现安全警示；导入时自动匹配本机已安装源，未安装者跳过并汇报数量 | 防止误将明文账号密码随备份分享出去，同时让未安装源导致的跳过完全透明 |
| WebDAV 完整内嵌 | 在左栏内嵌服务器地址、账号、密码（可切换显隐）、远端目录表单，保留与设置页共用同一底层存储；提供连接测试、一键上传 `.venera`、拉取最新归档并直接载入右侧预览 | 配置、上传、下载、解析还原形成单页闭环，同时设置中心现有入口可复用或跳转至本页 |

### 3.3 数据层

- **归档编解码器升级（`DesktopVeneraArchive`）**：
  - 实现与安卓 `venera-miuix` 格式 v5 完全对齐的 ZIP 归档读写：`meta.json`、`history.json`、`favorites.json`（兼容早期 `favorite.json`）、`favorite_folders.json`、`stats.json`、`guard_rules.json`、`gallery_follows.json`、`gallery_favorites.json`、`image_favorites.json`，以及可选成员 `source_accounts.json`（未勾选时不写入该条目，不写空数组）。
  - 保留 `desktopBackupDecodeJson` 向后兼容旧版桌面单文件 JSON 备份；导出默认生成 `venera_backup_<timestamp>.venera` 通用包。
- **既有管理器与事务接线**：
  - 阅读历史 / 阅读统计 / 屏蔽规则：继续走 `CoreTableBackup` + `SqlDatabase.inTransaction`（屏蔽规则保留 `GuardRulePattern.compiles` 校验，非法正则跳过并计数）；在「清空覆盖」模式下于同一事务内先执行对应表的 `DELETE` 再写入，合并模式下 `reading_stats` / `content_guard_rules` 增加防重判据或按需合并、`comic_history` 按较新 `updated_at` 或 `INSERT OR REPLACE` 更新。
  - 本地漫画收藏与收藏夹：接入桌面端 `LocalFavoritesManager`（或对应 `local_favorite.db` 端口），还原夹顺序、网络夹绑定及条目字段，触发状态流刷新，无需重启即生效。
  - 插图收藏：接入 `FavoriteImagesStore`（清除失效 `local_path`，保留远程 `image_url` 按地址去重导入）。
  - 画廊收藏与关注画师：接入 `DesktopGalleryFavorites` / `DesktopGalleryArtistFollows`（即 `GalleryFavoritesStore` / `GalleryArtistFollowsStore`），内存 `StateFlow` 与磁盘 JSON 同步更新。
  - 漫画源账号与设置：按 `source_accounts.json` 的 `key` / `fileName` 匹配桌面端已安装源目录，存在则写入对应 `.data`（与启用/置顶元数据），不存在则计入跳过总数。
- **WebDAV 同步升级**：
  - `DesktopWebDavSync` 读写依旧走 `PreferenceKeys.PREFS_WEBDAV`（键名 `server_url` / `username` / `password` / `remote_path` 不变），上传调用新的全量 `.venera` 导出链路，下载后直接将本地中转文件路径交给备份工作区预览态。

## 4. 验收口径

- 逐区域对照表（获批进入实现阶段时落地）：
  - 顶部本机资产概览条 → `DesktopBackupScreen.kt` 概览区块 + 本机数据只读统计查询；
  - 左栏打包导出与源账号开关 → `DesktopBackupScreen.kt` 导出卡 + `DesktopBackup.kt` 全量 `.venera` 编码；
  - 左栏内嵌 WebDAV 同步卡 → `DesktopBackupScreen.kt` WebDAV 区块 + `DesktopWebDavSync.kt`；
  - 右栏拖拽/选取 + 解析预览 + 策略切换 + 结果报告 → `DesktopBackupScreen.kt` 导入状态机 + `DesktopBackup.kt` 解码与分模块事务写入；
- 单元测试覆盖安卓 `.venera` v5 样本归档导入、旧版桌面 `.json` 导入、合并/覆盖两种策略、无效正则与未安装源跳过计数、以及 UI 状态机测试；`:desktop:compileKotlin` 与相关测试全绿。

## 5. 实施记录

待方案审批通过并进入实现阶段后补写。

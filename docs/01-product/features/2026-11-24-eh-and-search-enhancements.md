# 全源快速搜索书签、通用种子扩展与网络收藏增强

> 创建日期：2026-11-24  
> 状态：已获用户确认，分模块落地实施。

## 1. 背景与目标

- **解决痛点**：
  1. 桌面端搜索缺少「快速搜索书签」，高频组合条件（关键词 + 站源/聚合 + 筛选选项）每次都要重新输入和勾选。
  2. E-Hentai / ExHentai 站源缺少 `.torrent` 种子列表查看与下载能力。
  3. 网络收藏不支持收藏备注（`favnote`）、不支持收藏夹内搜索，且多选后缺少批量移动/批量取消收藏操作。
- **核心目标**：
  1. 新增全源通用的「快速搜索书签」（单源与聚合搜索均可用），支持保存、一键回填并搜索、重命名、置顶、删除。
  2. 新增通用可选的站源种子扩展协议（`comic.torrents`），并在 `ehentai.js` 实现，详情页按站源能力自动显隐「种子」入口。
  3. 补齐网络收藏三项能力：站源可选的收藏备注（`favnote`）、云端收藏夹内搜索（服务端优先 + 本地兜底过滤）、网络收藏多选批量移动与批量取消收藏（EH 原生批量提交 + 通用逐条兜底）。
- **非目标（Non-Goals）**：
  - 不为 EH 单独定制独立搜索页（保持全源统一搜索架构）。
  - 本期不做以图搜图与内置 Hosts/域名前置（留待后续独立评估）。
  - 不改动数据库表结构与迁移版本号（搜索书签走现有 `KeyValueStore` 独立键）。

## 2. 用户操作流

### 2.1 全源通用「快速搜索书签」
1. **入口**：漫画搜索页（`DesktopSearchScreen`）顶部搜索栏右侧「存为书签」按钮，以及「最近搜索」同区展示的「搜索书签」胶囊行。
2. **过程**：
   - 保存：输入关键词或调整筛选条件后点击「存为书签」→ 弹出轻量命名框（默认填充关键词或「站源 · 关键词」）→ 确认保存。
   - 使用：点击某条书签胶囊 → 自动切至对应站源（或聚合模式）、回填关键词与筛选选项并立即发起搜索。
   - 管理：右键或操作图标支持置顶、重命名、删除。
3. **结果**：书签持久化至本地 `KeyValueStore`；指向已禁用/不存在站源的书签点击时给出友好提示。

### 2.2 通用种子下载扩展（EH 首批适配）
1. **入口**：漫画详情页（`DesktopComicDetailScreen`）操作区「种子」按钮（仅当当前站源声明支持 `torrents` 时显示）。
2. **过程**：点击「种子」→ 弹窗加载并展示种子列表（文件名、大小、上传时间、做种/下载数）→ 点击「下载种子」或「复制链接」。
3. **结果**：打开/下载 `.torrent` 文件或将链接写入剪贴板，点击关闭退出弹窗。

### 2.3 网络收藏增强（备注 / 搜收藏 / 批量移动）
1. **收藏备注**：详情页收藏弹窗中，若站源支持备注（`supportNote`），展示「备注」输入框（EH 限 200 字符），收藏/修改时一并提交并回显。
2. **收藏夹内搜索**：网络收藏页顶部文件夹栏右侧提供「搜收藏」输入框；支持服务端搜索的站源（EH）直接请求云端，其余站源对当前已加载列表做本地标题/标签过滤。
3. **批量移动与取消收藏**：网络收藏页进入多选后，底部悬浮栏新增「移动到文件夹」与「取消收藏」；EH 走单次批量 POST，其余站源逐条调用并展示进度。

## 3. 范围

- **涉及屏与路由**：
  - 漫画搜索页（`DesktopSearchScreen` + 新增 `DesktopSearchBookmarks.kt` / `DesktopSearchBookmarksBar.kt`）
  - 漫画详情页（`DesktopComicDetailScreen` + 新增 `DesktopComicTorrentDialog.kt` + `DesktopComicDetailFavoriteDialog.kt`）
  - 网络收藏页（`DesktopNetworkFavoritesScreen` + 新增 `DesktopNetworkFavoritesActions.kt`）
  - 站源桥接与脚本（`DesktopEngineComicSource.kt`、`FavoriteData.kt`、`ehentai.js`）
- **涉及表与列（含迁移版本号）**：无数据库变更；搜索书签使用 `KeyValueStore` 独立键 `venera_search_bookmarks`。
- **涉及启动参数或构建项**：无。

## 4. 边界与异常

- **空态**：无搜索书签时隐藏书签行；种子列表为空时弹窗展示「暂无可用种子」；收藏搜索无匹配时展示空态提示。
- **加载中**：种子列表加载、云端收藏搜索、批量移动执行期间显示加载状态与进度提示，禁止重复提交。
- **失败**：网络异常或站点返回错误时通过提示条反馈具体原因，不闪退、不丢失当前选中态或输入内容。
- **能力缺失**：站源未实现 `torrents` / `supportNote` / `searchFavorites` / `batchMoveFavorites` 时自动隐藏或走通用兜底；未登录时提示先登录。

## 5. 验收

- **判据**：
  - `DesktopSearchBookmarksTest`：书签读写、新增、去重、置顶、重命名、删除、空串拦截、筛选条件还原。
  - 既有架构与体量护栏：`FileSizeGuardTest`、`DesktopEngineCallCoverageTest` 及全量 `:desktop:test` 0 失败。
- **端到端验证**：用户在桌面端真窗验收搜索书签保存/回填、EH 详情页种子弹窗、EH 收藏备注、网络收藏搜索与批量移动。
- **取证读数**：增量 `:desktop:compileKotlin` 与全量 `:desktop:test --console=plain -Djava.awt.headless=true` 全绿。

## 6. 文档影响

- [x] `docs/CHANGELOG.md`（用户截图确认后更新并提交）
- [ ] `docs/02-architecture/architecture.md`（不涉及模块架构重构）
- [ ] `docs/02-architecture/database-schema.md`（无表结构变更）
- [ ] `docs/03-guides/build-and-release.md`（无构建项变更）
- [ ] `docs/01-product/roadmap.md`
- [ ] `README.md` 与 `README.en.md`

# 数据库与本地存储

本机使用三个 SQLite 库。业务表全部有 DDL 实证，来源为 `compose/data/db`。

## 1. 概览

| 库 | 文件名 | 版本 | 性质 | 归属 |
|---|---|---|---:|---|
| 核心库 | `venera_core.db` | 4 | 应用自维护 | 历史、收藏、源元数据、统计、插图收藏、屏蔽规则、追更账本与订阅绑定 |
| 本地收藏夹库 | `local_favorite.db` | 1 | 遗留体系，仍在使用 | 一夹一表收藏、夹排序、网络夹绑定 |
| 桌面下载台账库 | `desktop_downloads.db` | 1 | 桌面自维护 | 漫画下载台账（`comic_download_records`）、图片下载台账（`image_download_records`）与本地导入/关联漫画台账（`local_comic_imports`），「本机内容」纯读库不扫盘 |
| 图库词典 | `gallery_tags_79415.sqlite` | 无 | 只读资产 | 标签离线词典 |

落点统一为 `%LOCALAPPDATA%\venera\db\`。数据根取不到时直接抛异常，不退回临时目录。

建库时机：首次调用 `reader()` / `writer()` 时才创建 `db/` 目录与连接，随后执行 `ensureOn` 版本校验。

## 2. 版本与迁移

| 场景 | 行为 |
|---|---|
| 新库，`user_version = 0` | 执行 `create` 建全量 |
| `0 < user_version < 目标` | 执行 `upgrade` |
| `user_version > 目标` | 抛异常，拒绝降级打开 |
| 版本实际变化 | 回写 `PRAGMA user_version` |

| 迁移 | 内容 | 风险 |
|---|---|---|
| core v1 → v2 | **DROP 三张老表**（历史、收藏、源）后重建，随后提前 `return` | 老数据丢失，测试明确断言「进度没了」 |
| core v2 → v3 | 只补三张新表及索引，不动老数据 | 低 |
| core v3 → v4 | 只补 `comic_follow` / `comic_follow_sync_source` 两张追更表及索引，不动老数据 | 低 |
| local_favorite | v1 为首版，无升级路径 | — |
| 旧收藏迁移 | 一次性把旧夹表搬进 `comic_favorite`，用 `migration_flags` 幂等 | 需与搬运同分支落标记 |

v1 建表清单已提前包含 v3 三张表，因此 v2 分支提前 `return` 后结果看起来一致。把这三张表移出 v1 清单会导致 v1→v2 缺表。

## 3. `venera_core.db`

### 3.1 `comic_history` 阅读历史

主键 `(comic_id, source_name)`，索引 `idx_history_updated_at(updated_at DESC)`。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `comic_id` | TEXT | 主键、非空 | 源内漫画 ID |
| `title` | TEXT | 非空 | 标题 |
| `author` | TEXT | 可空 | 作者，读出 null 折空串 |
| `cover_url` | TEXT | 非空 | 封面地址，空串表示沿用旧封面 |
| `source_name` | TEXT | 主键、非空 | 源标识，桌面写 `sourceKey` |
| `last_chapter_title` | TEXT | 非空 | 最后阅读章节标题 |
| `last_chapter_index` | INTEGER | 非空 | 最后阅读章节序号 |
| `last_page_index` | INTEGER | 非空 | 最后阅读页序号（0 基） |
| `total_pages` | INTEGER | 非空 | 该章总页数 |
| `updated_at` | INTEGER | 非空 | 最近阅读时间，epoch 毫秒 |

### 3.2 `comic_favorite` 核心收藏

主键 `(comic_id, source_name)`，索引 `idx_favorite_folder(folder_name)`、`idx_favorite_created(created_at DESC)`。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `comic_id` | TEXT | 主键、非空 | 源内漫画 ID |
| `title` | TEXT | 非空 | 标题 |
| `author` | TEXT | 可空 | 作者 |
| `cover_url` | TEXT | 非空 | 封面地址 |
| `source_name` | TEXT | 主键、非空 | 源标识 |
| `folder_name` | TEXT | 非空、默认 `'默认'` | 收藏夹名，桌面主表通常恒为默认 |
| `tags` | TEXT | 非空、默认 `''` | 标签串，逗号分隔 |
| `has_update` | INTEGER | 非空、默认 `0` | 是否有新章节，0/1 |
| `latest_chapter` | TEXT | 可空 | 最新章节标题 |
| `created_at` | INTEGER | 非空 | 收藏时间，epoch 毫秒 |

### 3.3 `comic_source` 漫画源元数据

主键 `source_id`。桌面当前不写此表，源状态走本地 JSON。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `source_id` | TEXT | 主键 | 源 ID，播种值如 `copymanga`、`ehentai` |
| `name` | TEXT | 非空 | 源显示名 |
| `version` | TEXT | 非空 | 源脚本版本 |
| `icon_url` | TEXT | 可空 | 图标地址 |
| `is_enabled` | INTEGER | 非空、默认 `1` | 是否启用 |
| `sort_order` | INTEGER | 非空、默认 `0` | 显示排序 |
| `config_json` | TEXT | 非空、默认 `'{}'` | 源配置 JSON |

### 3.4 `reading_stats` 阅读统计

主键 `id` 自增，索引 `idx_reading_stats_date(read_date)`、`idx_reading_stats_comic(comic_id, source_name)`。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `id` | INTEGER | 主键、自增 | 会话行 ID |
| `comic_id` | TEXT | 非空 | 源内漫画 ID |
| `comic_title` | TEXT | 非空 | 标题快照 |
| `source_name` | TEXT | 非空 | 源标识 |
| `tags` | TEXT | 非空、默认 `''` | 标签串，**桌面用 U+001F 分隔** |
| `chapter_title` | TEXT | 非空 | 本次章节标题 |
| `pages_read` | INTEGER | 非空、默认 `0` | 本次页数 |
| `duration_seconds` | INTEGER | 非空、默认 `0` | 本次秒数 |
| `read_date` | TEXT | 非空 | 本地日期 `yyyy-MM-dd` |
| `created_at` | INTEGER | 非空 | 落库时刻，epoch 毫秒 |

### 3.5 `favorite_images` 单页插图收藏

主键 `id` 自增，索引 `idx_favorite_images_created(created_at DESC)`。桌面目前只参与备份与恢复，无写入调用点。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `id` | INTEGER | 主键、自增 | 行 ID，备份不带 |
| `comic_id` | TEXT | 非空 | 源内漫画 ID |
| `comic_title` | TEXT | 非空 | 标题快照 |
| `source_name` | TEXT | 非空 | 源标识 |
| `chapter_title` | TEXT | 非空 | 章节标题，本表不存章节 ID |
| `page_index` | INTEGER | 非空 | 页序号 |
| `image_url` | TEXT | 非空 | 图片地址，应用层去重键，无唯一索引 |
| `local_path` | TEXT | 可空 | 本机落盘路径 |
| `created_at` | INTEGER | 非空 | 收藏时间，epoch 毫秒 |

### 3.6 `content_guard_rules` 屏蔽规则

主键 `id` 自增。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `id` | INTEGER | 主键、自增 | 规则 ID |
| `rule_type` | TEXT | 非空 | 规则类型：关键词 / 标签 / 画师 / 作品 ID |
| `pattern` | TEXT | 非空 | 匹配模式或正则 |
| `is_regex` | INTEGER | 非空、默认 `0` | 是否正则 |
| `is_enabled` | INTEGER | 非空、默认 `1` | 是否启用 |
| `created_at` | INTEGER | 非空 | 创建时间，epoch 毫秒 |

### 3.7 `migration_flags` 一次性迁移标记

运行期创建，不在 `SchemaSql`，不参与 `user_version`。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `name` | TEXT | 主键 | 标记名，当前只有 `legacy_local_favorites_v1` |
| `applied_at` | INTEGER | 非空 | 完成时刻，epoch 毫秒 |

### 3.8 `comic_follow` 追更账本

主键 `(comic_id, source_name)`，索引 `idx_follow_status_updated(status, has_new_update DESC, last_update_detected_at DESC)`、`idx_follow_source(source_name)`。
独立于 `comic_favorite`：收藏是"我想留着"，追更是"我在等更"，两张表互不联动。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `comic_id` | TEXT | 主键、非空 | 源内漫画 ID |
| `source_name` | TEXT | 主键、非空 | 源标识 |
| `title` | TEXT | 非空 | 标题快照 |
| `author` | TEXT | 非空、默认 `''` | 作者快照 |
| `cover_url` | TEXT | 非空、默认 `''` | 封面地址快照 |
| `status` | TEXT | 非空、默认 `'ACTIVE'` | `ACTIVE` / `PAUSED` / `COMPLETED`，只有 ACTIVE 参与巡检 |
| `origin_type` | TEXT | 非空、默认 `'MANUAL'` | `MANUAL` / `LOCAL_FAV` / `NET_FOLDER`，仅展示与排查 |
| `origin_folder_id` | TEXT | 非空、默认 `''` | 网站收藏夹 ID（导入时记） |
| `origin_folder_name` | TEXT | 非空、默认 `''` | 网站收藏夹名快照 |
| `baseline_initialized` | INTEGER | 非空、默认 `0` | 是否已建基线；0 时首轮巡检只写基线不标红 |
| `latest_chapter_id` | TEXT | 非空、默认 `''` | 最新话 ID 快照（判据之一） |
| `latest_chapter_title` | TEXT | 非空、默认 `''` | 最新话标题快照 |
| `chapter_count` | INTEGER | 非空、默认 `0` | 非占位章节总数（判据之一） |
| `remote_update_time` | TEXT | 非空、默认 `''` | 源站更新时间，无章节时作副判据 |
| `has_new_update` | INTEGER | 非空、默认 `0` | 未读红点 0/1 |
| `new_chapters_count` | INTEGER | 非空、默认 `0` | 本轮累计新增话数（红点未消时继续累加） |
| `last_update_detected_at` | INTEGER | 非空、默认 `0` | 最近一次发现更新的时刻 |
| `last_check_at` | INTEGER | 非空、默认 `0` | 最近一次检查时刻，冷却判据 |
| `last_error` | TEXT | 非空、默认 `''` | 最近一次失败原话，非空归入「异常」筛选 |
| `consecutive_failures` | INTEGER | 非空、默认 `0` | 连续失败次数，同源 3 次熔断 |
| `created_at` | INTEGER | 非空、默认 `0` | 加入追更时刻 |

### 3.9 `comic_follow_sync_source` 网站收藏夹订阅绑定

主键 `(source_name, network_folder_id)`；单夹源用空串作夹 ID。只记"这夹导过"，不做双向删除同步。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `source_name` | TEXT | 主键、非空 | 漫画源 key |
| `network_folder_id` | TEXT | 主键、非空 | 网站收藏夹 ID |
| `network_folder_name` | TEXT | 非空、默认 `''` | 夹名快照 |
| `last_synced_at` | INTEGER | 非空、默认 `0` | 上次同步时刻 |
| `last_synced_count` | INTEGER | 非空、默认 `0` | 上次同步拉到的本数 |
| `auto_import_new` | INTEGER | 非空、默认 `1` | 预留：同步时是否自动纳入网站新增收藏 |

## 4. `local_favorite.db`

### 4.1 `folder_order` 收藏夹排序

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `folder_name` | TEXT | 主键 | 收藏夹名，同时是动态表名 |
| `order_value` | INTEGER | 可空 | 排序值，缺列按 0 处理 |

### 4.2 `folder_sync` 网络收藏夹绑定

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `folder_name` | TEXT | 主键 | 本地收藏夹名 |
| `source_key` | TEXT | 可空 | 绑定的网络源 |
| `source_folder` | TEXT | 可空 | 网络源侧收藏夹标识 |

### 4.3 动态收藏夹表 `<folder_name>`

每个收藏夹一张表，表名即夹名，经 `quoteIdentifier` 转义。主键 `(id, type)`。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `id` | TEXT | 主键 | 漫画源内 ID |
| `name` | TEXT | 可空 | 标题 |
| `author` | TEXT | 可空 | 作者 |
| `type` | INTEGER | 主键 | `sourceKey` 的 JVM `hashCode()` |
| `source_key` | TEXT | 可空 | 可读源标识，弥补哈希不可逆 |
| `tags` | TEXT | 可空 | 标签串，逗号分隔 |
| `cover_path` | TEXT | 可空 | 封面路径 |
| `time` | TEXT | 可空 | 收藏时间 `yyyy-MM-dd HH:mm:ss` |
| `display_order` | INTEGER | 可空 | 手动排序值 |
| `translated_tags` | TEXT | 可空 | 写入时复制 `tags`，读取路径不读 |
| `last_update_time` | TEXT | 可空 | 追更列，运行时追加 |
| `has_new_update` | INTEGER | 可空 | 追更列，0/1 |
| `last_check_time` | INTEGER | 可空 | 追更列，epoch 毫秒 |

追更三列不在建表 DDL 内，由运行期 `ALTER TABLE ADD COLUMN` 补齐，因此不同夹表的实际列集合可能不同，`user_version` 不反映这一点。

## 5. `gallery_tags_79415.sqlite`

只读词典，随包资产，首次使用复制到数据根 `db/`。构建期由 `scripts/build_tag_dictionaries.mjs` 生成。

| 列 | 类型 | 约束 | 用途 |
|---|---|---|---|
| `name` | TEXT | 主键（`WITHOUT ROWID`） | 小写标签标识符 |
| `category` | INTEGER | 可空 | 0 通用 / 1 画师 / 3 作品 / 4 角色 / 5 元数据 |
| `cn` | TEXT | 可空 | 中文译名，null 表示无译名 |

## 6. DAO 与模型映射

| 表 | DAO / Store | 模型 |
|---|---|---|
| `comic_history` | `HistoryDao` | `HistoryRecord` |
| `comic_favorite` | `FavoriteDao` | `FavoriteRecord` |
| `comic_source` | `ComicSourceDao` | `ComicSourceRecord` |
| `reading_stats` | `ReadingStatsStore` | `StatsSummaryRow` / `TrendRow` / `TopComicRow` / `TaggedReadRow` 等投影 |
| `favorite_images` | `FavoriteImagesStore` | `FavoriteImageRow` / `FavoriteImageBackupFields` |
| `content_guard_rules` | `GuardRuleStore` | `GuardRuleRow` / `GuardRule` |
| `comic_follow` | `ComicFollowDao` | `ComicFollowRecord` / `FollowCheckOutcome` |
| `comic_follow_sync_source` | `ComicFollowDao` | `FollowSyncSourceRecord` |
| `tags` | `TagDictionaryStore` | `TagDictRow` |
| `folder_order` / `folder_sync` / 动态夹表 | `LocalFavoriteDatabase`、`LocalFavoritesManager` | `FavoriteItem` / `FavoriteItemWithUpdateInfo` |
| `comic_download_records` / `image_download_records` / `local_comic_imports` | `DesktopDownloadsDb` | `DesktopComicDownloadedChapter` / `DesktopLocalImageItem` / `DesktopLocalComic` |
| core 表备份 | `CoreTableBackup`、`DesktopBackup` | `DesktopBackupSnapshot` |
| 全量归档 | `DesktopVeneraArchive` | — |

## 7. 写入安全

契约写在 `SqlDatabase`：SQL 值一律 `?` 参数绑定，标识符只能走 `quoteIdentifier`。

实证结论：全部业务 DAO 的值写入均走绑定；标识符拼接（表名、夹名）均经 `quoteIdentifier` 转义，内部反引号翻倍并拒绝空值与换行。

唯一记录的片段拼接：`LocalFavoritesManager` 的排序更新语句把 `display_order = <整数>` 拼进 SQL。值来自库内 `MAX/MIN` 计算结果与固定字面量，非用户输入，注入面不成立；但绕过了参数绑定，属于可改进点。

## 8. 易误解点

| 误解 | 实际 |
|---|---|
| 只有一个收藏表 | 两套账本：`comic_favorite` 是桌面收藏屏主表；`local_favorite.db` 动态夹表是官方详情核路径与迁移源 |
| `comic_favorite` 已废弃 | 在官方路径是旧单表，在桌面是主表 |
| `comic_source` 是桌面源列表来源 | 桌面无该表的生产调用点，源状态走本地状态存储 |
| `source_id` 与 `source_name` 同域 | 无外键无校验，取值可能一个是键名、一个是显示名 |
| DAO 按联合主键精确定位 | `FavoriteDao` 的删除与判存在只按 `comic_id`，跨源同号可能误删 |
| 收藏标签与统计标签同分隔符 | 收藏用逗号；阅读统计用 U+001F |
| `favorite_images.image_url` 有唯一约束 | 无唯一索引，去重靠应用层先查后插 |
| `folder_order` 在核心库 | 它在 `local_favorite.db` |
| `.venera` 归档的收藏来自 `comic_favorite` | 归档走动态夹表；本机 JSON 备份含历史、统计、屏蔽三表；v4 起追更两表随「收藏」域一并入包 |
| 追更红点与收藏 `has_update` 同源 | 两套账本：追更读 `comic_follow.has_new_update`，收藏的 `has_update` 不被巡检回写 |
| 夹名可任意取 | 表名经转义，但未做保留名校验，与元表同名会静默命中已有表 |

## 9. 不在 SQLite 中的数据

| 数据 | 存储形式 |
|---|---|
| 下载队列 | 文件系统：`chapter.json` + 图片落盘，无队列表 |
| 图库收藏、浏览历史、画师关注 | JSON 文件存储 |
| 设置与偏好 | 键值 JSON 存储 |
| 源启用状态与排序 | 本地状态存储，非 `comic_source` 表 |
| Cookie 与凭据 | 数据根下 `cookies.json` |

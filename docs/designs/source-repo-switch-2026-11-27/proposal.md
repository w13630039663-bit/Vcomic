# 站点墙源仓库切换（接入 laincat/vensrc）· 方案稿

规格名:`source-repo-switch-2026-11-27` · 日期:2026-11-27
状态:**待批**。获批后进三件套,未批前不动码。

依据:
- 代码现状:`desktop/src/main/kotlin/com/venera/desktop/sourcemanage/DesktopSourceManageScreen.kt`、`DesktopSourceStore.kt`、`engine-probe/src/main/kotlin/com/venera/engineprobe/DesktopSourceUpdate.kt`
- 既有设计规范:`desktop/docs/designs/site-sources-rework-2026-11-20/`、`desktop/docs/designs/desktop-ui-standard-2026-11-05/controls-standard-spec.md`、`glass-card-spec.md`
配套:`ui-mockup.html`(本目录,含深/浅两套、`官方源 / laincat/vensrc` 切换、在线源弹窗与抽屉预览)

## 1. 结论

**在站点墙页头下方增设紧凑型「源仓库条」,并打通在线源弹窗与卡片来源标注**:让用户在「站点」页无需进深层设置即可一键切换上游源仓库(`官方源 venera-configs` ↔ `社区巡检 laincat/vensrc`,并在社区档下区分 `主订阅 / 扩展订阅`),所有来自 `laincat/vensrc` 的列表、弹窗与安装后站点卡片/抽屉均显式标注 `laincat/vensrc`。

分三层落地:

| 层 | 目标 |
|---|---|
| 视觉 | 复用 `DesktopGlassCard(Nested)`、`DesktopSegmented`、`DesktopPopUpButton`、`DesktopButton(Compact/Medium)` 与中性胶囊 `StatusBadge`;全站唯一 accent 蓝,暗色无阴影,同心圆几何 |
| 交互 | 站点墙顶部仓库条一行完成「仓库切换 + 轨道切换 + 浏览源库 + 检查同步」;接通「添加站点」下拉中的「在线站点」与「检查更新」;弹窗内同步支持切换仓库并标注 `laincat/vensrc` |
| 数据 | 保留全部现有启停、排序、登录、站点设置、脚本编辑、撤销改写、导入能力;扩展 `DesktopSourceUpdate` 支持多仓库配置与条目直链 `url` 下载;在 `DesktopSourceUpdater` 清单中附带记录已装/更新源的所属仓库标识(`official` / `vensrc-main` / `vensrc-ext`)以供卡片与抽屉真实标注 |

保留全部业务逻辑、数据来源、状态管理与功能入口。

## 2. 现状问题

### 2.1 与本次需求的差距

| 项 | 现状 | 目标 | 差距 |
|---|---|---|---|
| 上游源仓库地址 | `INDEX_URL` 与 `RAW_BASE` 写死为 `venera-app/venera-configs`【确定 · `DesktopSourceUpdate.kt:32-33`】 | 支持在官方仓库与 `laincat/vensrc`(含主订阅/扩展订阅)之间切换 | 单仓写死,无法订阅第三方社区源 |
| 源脚本下载方式 | 强制拼接 `RAW_BASE + fileName`【确定 · `DesktopSourceUpdate.kt:116`】 | 支持直接读取清单条目的 `url` 字段(含代理/镜像回退)【确定 · `vensrc` 实测 `subscriptions/*.json` 每条含独立 `url`】 | 社区聚合清单指向多作者仓库,拼接固定前缀会 404 |
| 在线源列表弹窗 | `DesktopSourceStore` 已实现但「添加站点」下拉仅接了 `onImportSource`【确定 · `DesktopSourceManageScreen.kt:480-485`】 | 将「在线站点」与「同步仓库更新」接回页面与菜单,并在弹窗内展示当前所选仓库及 `laincat/vensrc` 标注 | 现存弹窗处于无入口状态,无法安装社区新增源 |
| 卡片与抽屉来源标注 | 卡片副行与抽屉统一硬编码 `"内置站点"`【确定 · `DesktopSourceManageScreen.kt:589,669,927`】 | 区分显示 `内置站点` / `官方源` / `laincat/vensrc` / `本地脚本`,让社区源出处一眼可辨 | 装入社区源后无法从 UI 辨认来源仓库 |

### 2.2 既有排版容量制约

1. `WallHeader` 右侧当前已排布搜索框(`172dp`)、三档分段(`全部 / 已开启 / 未启用`)与「添加站点」主按钮【确定 · `DesktopSourceManageScreen.kt:462-488`】,在 `1100dp` 窄窗下(扣除侧栏与边距余约 `978dp`)若再并排塞入含 `laincat/vensrc` 的仓库切换器会造成挤压或文字裁切。
2. `DesktopSourceStore` 弹窗副标题当前写死为 `"官方仓库 · N 颗"`【确定 · `DesktopSourceStore.kt:114`】,与 `site-sources-rework` 既定的去「颗」化术语口径(`"个"`)不一致。

## 3. 方案明细

### 3.1 视觉层

- **源仓库控制条(新增,位于 `WallHeader` 与卡片网格之间)**:
  - 容器:采用 `DesktopGlassCard(level = Nested)`,圆角 `12.dp`,左右与主滚动区对齐(`28.dp` 边距),内边距 `horizontal = 14.dp, vertical = 10.dp`,底距 `14.dp`。
  - 左区(仓库切换 + 显式标注):
    - 前导标签 `源仓库`(`12.sp`,三级字);
    - `DesktopSegmented` 两档切换:`官方源` | `社区源 · laincat/vensrc`(选中态滑块 `5.5.dp` 圆角,严格对齐 `controls-standard-spec §2`);
    - 当选中 `社区源 · laincat/vensrc` 时,紧随其后平滑展开二级轨道选择(优先用 `DesktopPopUpButton` 或紧凑两档分段):`主订阅 (全绿稳定)` / `扩展订阅 (核心可用)`,并附带只读来源标签 `github.com/laincat/vensrc`(中性灰底 `surfaceContainerHigh @ 0.60` + `ink2` 字,胶囊 `999.dp`,不另立彩色)。
  - 右区(状态读数 + 行动钮):
    - 简短状态文本(`12.sp`,三级字,数字用 `tabular`):如 `已选官方源 · 34 个` 或 `laincat/vensrc 主订阅 · 25 个全绿` / `发现 N 个可升级`;
    - 两枚标准 `DesktopButton(size = Compact, 26.dp 高, r6)`:
      - `浏览源库`(`variant = Secondary`)→ 打开在线站点弹窗(`DesktopSourceStore`);
      - `检查同步`(`variant = Primary` 当有可用升级或首次切换未同步时,其余 `Secondary`;异步时切 `loading = true`)→ 从当前所选仓库拉取清单并更新同 `key` 源。
- **「添加站点」下拉菜单补齐(`WallHeader`)**:
  - 菜单项调整为三项:`浏览源库(含 laincat/vensrc)`、`导入本地脚本 (.js)`、`立即检查更新`。
- **在线站点弹窗(`DesktopSourceStore` 升级)**:
  - 弹窗材质升级为桌面标准弹窗圆角 `16.dp` + 内嵌玻璃列表行(`Nested`,圆角 `10.dp`,同心圆内边距 `12.dp`);
  - 顶部保留标题「在线站点」,下方并列**仓库切换分段**(`官方源` | `laincat/vensrc 主订阅` | `laincat/vensrc 扩展`),右侧保留「筛选(全部 / 未装 / 可更新)」、搜索框(`DesktopSearchField`)、`刷新` 与 `关闭`(`DesktopButton Medium 32dp`);
  - 每行站点卡片显式带上来源胶囊徽章(`laincat/vensrc` 或 `官方源`),描述行保留原作者/来源仓库说明与本地/线上版本对比(术语统一用「个」不用「颗」);右侧按钮(`Compact 26dp`):`安装` / `升级` / `已是最新`。
- **站点墙卡片与右侧抽屉标注**:
  - 副行与抽屉头部来源徽章按实际出处展示:`内置站点`(随包原版)、`官方源`(官方仓库更新)、`laincat/vensrc`(社区巡检安装/更新)、`本地修改`(用户手改/导入);
  - 抽屉「脚本」卡片中同步展示 `v<版本号> · 来源：laincat/vensrc`,并提供 `恢复内置脚本`(若存在随包原版)或 `移除社区源`(若为社区新增源)。

### 3.2 交互层

| 能力 | 做法 | 效果 |
|---|---|---|
| 切换上游源仓库 | 点仓库条「官方源 / 社区源 · laincat/vensrc」 | 即时记忆选中的仓库并刷新状态计数,不强行破坏用户已启用的站点配置 |
| 切换 vensrc 订阅档 | 选「主订阅 (25)」或「扩展订阅 (71)」 | 默认推荐主订阅(全绿稳定),需要更多站点(如包子修订/mh1234新版/漫蛙修复版)时可一键切到扩展订阅 |
| 浏览并单选安装社区源 | 点仓库条「浏览源库」或菜单「浏览源库」 | 弹出在线站点列表,可搜索并按需安装任意新增源(如 `dm5`、`baozi_fixed`、`mh1234_fixed`) |
| 批量同步同名社区升级 | 点仓库条「检查同步」 | 将本地已启用/存在的同名源升级到 `laincat/vensrc` 更高版本(经 `checkLoadable` 验过才落盘,手改稿不覆盖) |
| 退回出厂版本 | 卡片「⋯」抽屉 →「恢复内置脚本」 | 一键撤回覆盖层中的社区脚本,恢复随包原版 |

### 3.3 数据层

- 仓库枚举与持久化:在偏好存储中新增 `pref_source_repo_id`(`"official"` / `"vensrc_main"` / `"vensrc_ext"`,默认 `"official"`),冷启动自检与手动刷新共用当前选中的仓库配置。
- `DesktopSourceUpdate`:
  - `Entry` 增加 `url: String?` 与 `repoId: String`;
  - 解析 `index.json` / `extended.json` 时同时兼容 `url`(直链下载,国内未开手工代理时支持按需走 GitHub raw → jsDelivr 回退)与传统 `RAW_BASE + fileName` 拼接。
- `DesktopSourceUpdater`:
  - 在既有 `.auto-updated.properties` 旁记录 `<fileKey>.repo=<repoId>` 与 `<fileKey>.version=<ver>`,供 `DesktopSourceNames` / `DesktopSourceRowUi` 准确读取 `sourceOrigin`(内置 / 官方 / `laincat/vensrc` / 本地修改),不编造字段。
- 扩展新增源落盘后由 `DesktopSourceCatalog` 从 `overrideDir` 扫入(安全 key 校验沿用 `DesktopSourceOverrides.isSafeKey`)。

## 4. 验收口径

- 逐区域对照 `ui-mockup.html`:深/浅两套主题 × 仓库条(官方态 / `laincat/vensrc` 展开态)× 在线源弹窗 × 抽屉来源标注;
- 术语统一:屏上无「颗」字与「源管理」旧称,且 `laincat/vensrc` 标识在切换条、在线源弹窗、卡片副行与抽屉四处一致可见;
- 保留全部既有入口(搜索、三档启停过滤、长按拖拽重排、卡片进站、「⋯」抽屉、登录/设置/编辑脚本/恢复内置/导入本地脚本);
- 落码前先按 `venera-design-flow` 补齐三件套(`design-analysis.md`、`design-tokens.md`、`component-spec.md`),落码后 `:desktop:compileKotlin` 与相关单测全绿并交主人真窗截图确认。

## 5. 实施记录

已按三件套完成全部 UI 与底层仓库切换、本地脚本导入与源同步更新修复落码：
- `DesktopSourceUpdate.kt`：与原稿相比额外补充了未配置手工代理时对 `raw.githubusercontent.com` 的 jsDelivr CDN 自动镜像与 `fastly.jsdelivr.net` 回退，确保直连网络下也能顺利拉取 `laincat/vensrc` 清单与分散在不同作者仓库的 `.js` 脚本。
- `DesktopSourceManageScreen.kt`：抽屉脚本区在保持 `Built-in` 既有语义测试兼容的同时，对社区/官方/本地来源显式标注来源仓库，并对非随包新增源将「恢复内置脚本」改为「移除脚本」。
- 修复排查发现的导入与同步既有 Bug：导入反馈由抽屉隐藏变量改回页面顶部 `sourceNote` 可见提示并自动启用新源；`writeDesktopSourceEnabled` 补齐合并 `overrideDir` 新增源；源同步更新与在线源库补齐读取设置页手动代理与 `Dispatchers.IO` 异步执行。

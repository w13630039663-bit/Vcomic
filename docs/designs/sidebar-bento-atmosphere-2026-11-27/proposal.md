# 侧栏个性化重做（Bento 磁贴 & 画报氛围）· 方案稿

规格名：`sidebar-bento-atmosphere-2026-11-27` · 日期：2026-11-27  
状态：**已批定案（方案一：2×2 Bento 磁贴 + 悬浮双层身份底坞）**，进入阶段 B 三件套归档与落码。

依据：用户侧栏现状截图与确认截图 / [VeneraLightRailSkin.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt) / [DesktopTheme.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopTheme.kt) / [notation-rules.md](file:///g:/venera-desktop/.agents/skills/venera-design-flow/references/notation-rules.md)  
交互预览：[ui-mockup.html](file:///g:/venera-desktop/desktop/docs/designs/sidebar-bento-atmosphere-2026-11-27/ui-mockup.html)  
配套：[design-analysis.md](file:///g:/venera-desktop/desktop/docs/designs/sidebar-bento-atmosphere-2026-11-27/design-analysis.md) / [design-tokens.md](file:///g:/venera-desktop/desktop/docs/designs/sidebar-bento-atmosphere-2026-11-27/design-tokens.md) / [component-spec.md](file:///g:/venera-desktop/desktop/docs/designs/sidebar-bento-atmosphere-2026-11-27/component-spec.md)

## 1. 结论

提供**方案一（2×2 Bento 磁贴 + 悬浮身份底坞）**、**方案二（画报氛围头卡 + 灵动主色光标）**与二者融合的**合璧推荐版**在 [ui-mockup.html](file:///g:/venera-desktop/desktop/docs/designs/sidebar-bento-atmosphere-2026-11-27/ui-mockup.html) 中交互预览，供主人拍板其一进入阶段 B。

| 层 | 目标 |
|---|---|
| 视觉 | 收口主导航与子项的图标语言（撤除彩色小方砖，统一单色线框 + 唯一 `accent` 点缀）；消除中下段大片空白与孤立底栏，用磁贴岛屿、氛围头卡与悬浮底坞建立收藏馆级的层次与秩序 |
| 交互 | 保留 4 项主导航、全部二级折叠分组与折叠窄轨；新增磁贴/光标态即时切换、底部账号状态直达、折叠态快捷频道弹窗 |
| 数据 | 保留全部 `VeneraNavTab`、`VeneraSideBarGroup`、`VeneraSideBarAccountCard`、`VeneraSideBarDownloadActivity`、桌宠可见开关及路由回调，不改变状态归属与业务逻辑 |

## 2. 现状问题

### 2.1 与个性化目标的差距

| 项 | 现状 | 目标方向 | 差距 |
|---|---|---|---|
| 图标语言 | 顶部 4 项用单色线框（`:256`），二级 10 项用高饱和彩色圆角方砖 `LightGroupBadgeIcon`（`:874-889`）【确定 · `VeneraLightRailSkin.kt:256,874-889`】 | 全栏统一单色细线图标，选中项仅由全站唯一 `accent`（浅 `#0A63F6` / 深 `#0A84FF`）点亮 | 两套图标系统混拼，类似传统系统设置页 |
| 空间分布 | 中部列表结束后留出约 1/3 高度的空玻璃区域，底栏 4 枚图标均匀散列于 46dp 空白条上（`:265-318,453-520`）【确定 · `VeneraLightRailSkin.kt:265-318,453-520`】 | 上部建立视觉锚点（2×2 磁贴 / 氛围头卡），底部收拢为悬浮身份控制台 | 上中下视觉重量脱节，缺乏重心与呼吸感 |
| 品牌与个性 | 顶部仅 `22dp` 小图标 + `VCOMIC` 字样（`:220-242`），全部导航均为主次无差别的竖向列表行【确定 · `VeneraLightRailSkin.kt:220-242,251-308`】 | 体现“漫画 + 插画收藏馆”性格：高频入口块面化或融入柔焦氛围封面与续读锚点 | 辨识度偏低，像普通文件浏览器 |

### 2.2 其它细节缺口

1. **底部账号与下载态表达过弱**【确定 · `VeneraLightRailSkin.kt:471-518`】：Pixiv 登录态仅剩 22dp 头像小圆点，无法一眼看出账号昵称/登录摘要；下载入口与普通工具按钮并排缺乏质感区分。
2. **选中态缺乏焦点感**【估算 · 截图】：当前选中行仅为一块低对比灰色矩形 `railSelected`，在浅色云母底上区分度偏平淡。

## 3. 方案明细

> 以下两套方案均严格遵守桌面硬点：全局仅保留 `accent`（浅 `#0A63F6` / 深 `#0A84FF`）作为唯一装饰色；圆角严格满足外层 = 内层 + 内边距（同心圆法则）；零低 elevation 硬边阴影，全靠半透玻璃层级与 1px 发丝描边隔离。

### 3.1 视觉层

* **方案一 · 2×2 Bento 磁贴 + 悬浮双层底坞**：
  * **核心磁贴区（2×2 Grid）**：将「首页 / 漫画搜索 / 画廊 / 收藏」改为 `2×2` 玻璃磁贴（圆角 `12dp`、间距 `6dp`、高 `56dp`），未选中呈微透玻璃卡片 + 1px 发丝描边；选中态切换为 `accent` 柔光底衬（浅 10% / 深 16% 填充 + 32% 描边 + 右上角 5px 指示光点 + `accent` 图标着色）。
  * **精简二级列表**：撤除彩色方砖，改用 `15dp` 单色线框图标；分组头维持右侧轻旋转箭头；选中行使用主色轻胶囊底 + `accent` 图标。
  * **悬浮双层底坞**：收拢为独立 `12dp` 圆角玻璃岛屿（外距左右 `8dp` 底部 `8dp`、内距 `4dp`，内按钮严格 `8dp` 圆角同心），上层为「Pixiv 头像 + 账号短名/在线点 + 展开/收起钮」，下层或同卡右侧集成「下载（含数量徽标）+ 设置」。
* **方案二 · 画报氛围头卡 + 灵动主色光标**：
  * **顶部氛围头卡**：顶部配置约 `96dp` 高圆角 `12dp` 画报卡片，使用最近阅读/默认封面柔焦渐变作为暗纹底图，叠加双向可读性遮罩，左上呈现 `VCOMIC` 铭牌，左下显示最近阅读入口（主标题 ≤ 6 字、副标题 ≤ 15 字，点击可快速续读/跳最近阅读）。
  * **灵动光标导航行**：主导航与子导航采用清晰分区，选中态取消死板整行灰块，改用左侧 `3dp × 16dp` 竖向 `accent` 柔光光标 + 轻玻璃内衬底面；图标统一单色线框。
  * **底部控制台**：与方案一共享悬浮微玻璃底座，留白处自然承接桌宠栖息位（如启用桌宠）。
* **合璧推荐版（在 HTML 中同步提供切换预览）**：
  * 顶部精简 `72dp` 氛围微头卡（品牌 + 最近阅读捷径） + `2×2` 核心 Bento 磁贴 + 统一单色线框分组 + 悬浮身份底坞，兼顾个性化记忆点与高效率分层。

### 3.2 交互层

| 能力 | 做法 | 效果 |
|---|---|---|
| 导航点击与选中 | 主入口（4 项）与二级分组（画廊 6 项 / 漫画 2 项 / 工具 2 项）沿用现有分发回调 | 所有页面与路由零改动，切换零闪烁 |
| 分组折叠 | 分组头保留点击展开/收起，右侧箭头平滑旋转 | 收放紧凑，避免垂直溢出 |
| 折叠窄轨（`64dp`） | 收起后自动转为极简居中图标轨，保持 `LightChannelPopup` 二级浮层 | 给画廊/阅读保留最大空间 |
| 桌面按压与悬停 | 悬停即时提亮玻璃底；点击反馈克制平滑，不堆叠夸张缩放 | 符合桌面端原生交互手感 |

### 3.3 数据层

* 不改动 `VeneraNavTab`、`desktopSideBarGroups`、`DesktopRoute`、后端接口或本地存储结构。
* 氛围头卡的“最近阅读”如接入真实数据，直接复用既有的浏览/阅读历史快照流（缺省为空时优雅退回品牌极简渐变底纹，不制造假数据）。

## 4. 验收口径

* 确认方案一、方案二或合璧版中的唯一目标方案后，先补齐 `design-analysis.md` / `design-tokens.md` / `component-spec.md` 三件套并归档，再行落码。
* 落码对照：`VeneraLightRailSkin.kt`（需控制体积，超出 400 行部分剥离至新 `*Sections.kt`）及关联测试（`DesktopTabOwnershipTest` 等）。
* 编译与相关测试全绿，并通过用户真窗截图确认深浅主题及展开/折叠双态效果。

## 5. 实施记录

* 2026-11-27 已按方案一定案完成落码（待主人真窗确认截图效果后统一记入 `docs/CHANGELOG.md` 并提交）：
  * 新增 `desktop/src/main/kotlin/com/venera/desktop/ui/DesktopRailBentoSections.kt`：实现全通透磨砂玻璃 2×2 核心 Bento 磁贴网格（玻璃亮高光斜边 + 无蓝底透明选中态）与单色二级导航行。
  * 新增 `desktop/src/main/kotlin/com/venera/desktop/ui/DesktopRailFloatingDock.kt`：实现底部悬浮双层身份底坞（极薄高透玻璃面 + `hazeBlur` 实时虚化穿过背部的滚动条目与桌宠，取消实白不透明底）。
  * 更新 `desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt`：接通 Bento 磁贴、单色二级列表、下半区 `hazeSource` 挂载与悬浮底坞，折叠态一并切换为透明高光胶囊与单色图标。
  * 新增护栏单测 `DesktopRailBentoPolicyTest.kt` 并连同 `DesktopFileSizeAndLayerGuardTest`、`DesktopNavRailPortTest`、`DesktopGpuCacheGuardTest`、`DesktopTabOwnershipTest` 等定向全绿通过。

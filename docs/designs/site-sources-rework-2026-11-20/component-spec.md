# 站点墙 · 组件规格

规格名:`site-sources-rework-2026-11-20` · 实现文件:`desktop/src/main/kotlin/com/venera/desktop/sourcemanage/DesktopSourceManageScreen.kt`

## 1. 组件树

```
DesktopSourceManageScreen
├─ WallHeader            标题 / 计数 / 搜索 / 三档 / 添加站点(+菜单)
├─ Column(verticalScroll)
│  ├─ SiteHeroCard ×3    已开启前 3,跨两行
│  ├─ Row(SiteCard ×3)   其余已开启,每行 3 个
│  ├─ Row(AddSiteCard)   末尾补位
│  ├─ SectionDivider     「未启用 N 个」
│  └─ IdleIndexStrip     chunked(4),每行 4 条
└─ SiteDrawer            遮罩 + 右侧面板(账号/设置/脚本/启用)
```

## 2. 逐组件

### 2.1 WallHeader

| 属性 | 值 | Compose |
|---|---|---|
| 标题 | 「站点」24/700 | `Text(VeneraType.pageTitle)` |
| 计数 | 「8 个已开启 · 26 个未启用」11.5 三级 | `row.size` 现算 |
| 搜索 | 172×32,圆角 9,占位「搜站点」 | `VeneraTextField` 或自绘 Row |
| 三档 | 全部 / 已开启 / 未启用 | `DesktopSegmented` |
| 添加 | 32 高主按钮 + 下拉三项 | `DesktopPushButton` + `DropdownMenu` |

「添加」下拉三项:在线站点 / 导入站点脚本 / 检查更新;对应回调 null 时该项不摆(§119)。

### 2.2 SiteIconSlot

| 状态 | 表现 |
|---|---|
| 有站方图标 | 34×34 槽,内 26×26 `contain`,圆角 10 |
| 无图标 | 槽内写显示名首字;英文名取前两字母;字 13/700,三级色 |
| 加载失败 | 同「无图标」,不摆破图 |

Compose:`remember(key) { DesktopSourceIcons.bytesOf(key)?.let { Image.makeFromEncoded(it).toComposeImageBitmap() } }`;
解码放在 `Image` + `toComposeImageBitmap`,与 `DesktopComicPane` 同一条轨。

### 2.3 SiteHeroCard

| 区域 | 值 |
|---|---|
| 尺寸 | 高 206,宽 3 等分 |
| 图标 | 52×52 槽 + 38 内图,竖向排布,与站名间距 13 |
| 站名 | 27/650,单行省略 |
| 副行 | 徽章 + 「内置站点 · N 项设置」11.5 |
| 底部 | 「浏览 →」12.5,默认三级色,悬停转 accent |
| 右上 | 「⋯」26×26,默认 50% 透明,悬停全显 |
| 点击 | 卡身 = 浏览(回调为 null 时整卡改为「⋯」行为) |

### 2.4 SiteCard

高 96,横排:图标 34 + 名字块 + 「⋯」;底部「浏览 →」。其余同上,字号降档(16/650)。

### 2.5 AddSiteCard

高 96,虚线描边白 14%,居中「+ 添加站点」13/550;点击等价页头「添加」按钮——直接开同一个下拉(定位到按钮处)。

### 2.6 IdleIndexStrip

4 列,条高 44,虚线白 9%,左站名 12.5/550、右「启用」11;
悬停:描边转 accent、站名转主字、右侧转 accent;点击 = 就地启用(回调走 onToggleSource)。

### 2.7 SiteDrawer

| 区 | 内容 | 来源 |
|---|---|---|
| 头部 | 站名 18/700 + 徽章行 + 关闭 | row |
| 开关行 | 「在站点列表中显示」+ 开关 + 说明 | onToggleSource / canDisable |
| 账号 | 状态 + 能力说明 + 登录/退出 | account / onSourceLogin / onSignOutSource |
| 站点设置 | 逐项控件(switch/select/input/callback) | settings / onSettingChange / onSettingCallback |
| 脚本 | 版本 + 编辑 / 恢复内置 + 说明 | onEditSource / onClearSourceOverride |

- 宽 424,右侧滑入,背后遮罩 52% 黑,点击遮罩关闭。
- 关窗条件:点关闭、点遮罩、切换选中站。
- 无内容段整段不摆(账号 null、settings 空、回调 null)。

## 3. 状态与数据

| 状态 | 归属 | 说明 |
|---|---|---|
| rows | 壳(不变) | `List<DesktopSourceRowUi>` |
| selectedKey | 壳(不变) | 抽屉宿主 = 选中站 |
| 搜索词 / 三档 | 屏内 remember | 出去回来清掉 |
| 抽屉开合 | 屏内 | 由 selectedKey 派生:非 null 即开;关闭 = onSelectedChange(null) |

⚠️ `onSelectedChange` 签名是 `(String) -> Unit`(非空),抽屉关闭需要「清空选中」——
本批把入参改为 `(String?) -> Unit`,装配点一处跟着改(仅此一处)。

## 4. 交互细节

- 点卡身 = 浏览该站:`onOpenSite` 回调,装配点暂不接时退化为打开抽屉(不摆假把手)。
- 幽灵条「启用」就地开启,不改选中。
- 三档筛选与搜索同时生效;「未启用」时卡区隐藏、只留索引带。
- 滚动位置保持(不因开关切换重置)。

## 5. 验收对照

| 区域 | 实现 | 稿 |
|---|---|---|
| 页头 | WallHeader | 顶部一行 |
| 大卡 | SiteHeroCard | 前 3 张 |
| 普通卡 | SiteCard | 第 4~8 张 |
| 补位 | AddSiteCard | 第 9 格 |
| 索引带 | IdleIndexStrip | 未启用区 |
| 抽屉 | SiteDrawer | `#drawer` 态 |

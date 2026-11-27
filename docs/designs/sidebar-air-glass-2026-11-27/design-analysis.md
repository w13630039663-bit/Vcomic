# Venera 侧栏与浅色全窗通透重做 — 视觉解构与页面分析

规格名：`sidebar-air-glass-2026-11-27` · 日期：2026-11-27  
参考基线：用户参考图、浅/深色真机截图、[VeneraLightRailSkin.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt)、[DesktopTheme.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopTheme.kt)

## 1. 核心建筑约束

1. **全窗同一透明度**（用户明确硬约束）：左侧栏、顶部命令带、右侧主内容区继续共用单一取值源 `DesktopGlass.surface(palette)`，不拆分成“左透右实”。
2. **浅色去灰提亮**：浅色玻璃底从偏灰蓝 `0xFFEAEDF1`（0.72 alpha）升为高明度净白 `0xFFFAFCFF`（0.78 alpha），并同步窗口 `DWMWA_USE_IMMERSIVE_DARK_MODE` 浅色标志，消除 Win11 系统深色主题下 Acrylic 底层带来的灰泥污渍。
3. **深色可读性防穿透**：深色 `DesktopGlass.surface` 从 `0.20f` 抬升至 `0.56f`，保留背景氛围色同时避免桌面文字直接透出干扰菜单识别。
4. **全量保留业务与功能入口**：保留全部主标签、二级分组、下载角标及飞入锚点、账号头像点击、`DesktopPetVisibility` 桌宠开关与折叠记忆。

## 2. 页面结构（自上而下）

1. **贴边侧栏容器**：展开宽 `240.dp`（原 `260.dp`【确定 · `DesktopLargeScreenChrome.kt:356`】），折叠宽 `64.dp`，右缘补 `1.dp` 极轻垂直发丝线 `palette.railLine` 以在全窗同透明度下切分左右层次。
2. **顶部品牌区**：精简 `VCOMIC` 标题块高度与字重（SemiBold、13sp），去掉厚重压顶感，上下留白更轻盈。
3. **高频主导航区**：保留 4 项真实入口（首页、漫画搜索、画廊、收藏），行高 `34.dp`，圆角 `8.dp`。彻底移除左侧 3dp 紫色竖条与亮紫图标着色，改为柔和半透明圆角胶囊选区 + 提亮文字。
4. **可折叠分组区**：移除 `MacDivider()` 横线，以 `12.dp` 组间距代替；分组标题改为 `标题 ⌄`（小箭头置右），左缘与下方图标左缘对齐成同一纵线；二级条目换用互不撞车的微色彩圆角图标徽标或独立轮廓图标。
5. **桌宠区**：继续受 `DesktopPetVisibility.visible` 控制，开关打开时展示于滚动区底部，关闭时保持极简纯列表。
6. **极简单行底栏**：移除顶边分割线与暗色底衬，移除导致截断省略号的昵称文本，等距排列 4 枚纯净图标控件（折叠钮、pixiv 头像圆点、下载与本地角标、设置）。

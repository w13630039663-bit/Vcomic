# 侧栏通透极简玻璃重做 · 方案稿

规格名：`sidebar-air-glass-2026-11-27` · 日期：2026-11-27  
状态：**已落地并经实机确认定案**。

依据：用户参考图 / [VeneraLightRailSkin.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/ui/VeneraLightRailSkin.kt) / [DesktopTheme.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopTheme.kt) / [DesktopCommandBar.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/ui/DesktopCommandBar.kt)  
交互预览：[ui-mockup.html](file:///g:/venera-desktop/desktop/docs/designs/sidebar-air-glass-2026-11-27/ui-mockup.html)  
配套：[design-analysis.md](file:///g:/venera-desktop/desktop/docs/designs/sidebar-air-glass-2026-11-27/design-analysis.md) / [design-tokens.md](file:///g:/venera-desktop/desktop/docs/designs/sidebar-air-glass-2026-11-27/design-tokens.md) / [component-spec.md](file:///g:/venera-desktop/desktop/docs/designs/sidebar-air-glass-2026-11-27/component-spec.md)

## 1. 结论

保留全部导航逻辑、路由与状态管理，仅对桌面端侧栏皮肤与主内容区材质反差进行视觉升级，并在 HTML 方案稿中提供两套窗控方案（左上角红绿灯 vs Windows 左上折叠钮 + 右上轻窗控）供主人拍板。

| 层 | 目标 |
|---|---|
| 视觉 | 拉开「左侧高通透磨砂玻璃 + 右侧高不透明度干净实面」反差；去紫竖条与分割线，改用极简灰胶囊选中态与右侧箭头的轻分组头 |
| 交互 | 支持分组折叠、侧栏展开/收起；红绿灯方案支持失焦变灰与悬停显符 |
| 数据 | 保留全部 `VeneraNavTab`、`desktopSideBarGroups`、`DesktopRoute` 与状态回调签名 |

## 2. 现状问题

1. **左右材质混成一片**【确定 · `DesktopTheme.kt:268-271`】：`DesktopGlass.surface` 将侧栏、顶栏、内容区统一为同色同透明度，且 `VeneraLightRailSkin.kt:143` 移除了右缘发丝线，缺少「左透壁纸、右干净工作面」的呼吸层次。
2. **顶部与底栏偏满**【确定 · `VeneraLightRailSkin.kt:219-241,470-583`】：顶部保留图标与 `VCOMIC` 标题块；底栏同时承载分割线、灰底衬、长条用户名与多枚按钮。
3. **选中态与分组头偏重**【确定 · `VeneraLightRailSkin.kt:366-392,871-880`】：选中行带 3dp 紫色竖条与 `accentIcon` 亮紫，分组之间夹 `MacDivider()` 横线，箭头置左。

## 3. 待拍板决策项

1. **窗控风格**：默认用「左上角红绿灯」、默认用「Windows 折叠钮 + 右上角轻窗控」，还是做成外观设置项可切换。
2. **底部账号入口**：底栏缩成纯图标排布后，pixiv 账号改为仅显示头像圆点（点击进入账号页）。
3. **桌宠布局共存**：开启桌宠时仍会在列表底部占出预留区，关闭桌宠即为纯粹极简列表。

## 4. 实施记录

- 按用户明确要求维持「全窗同一透明度」，仅修改全窗统一玻璃取值 `DesktopGlass.surface`（浅 `Color.White @ 0.54f`、深 `0xFF14161C @ 0.44f`）及补回 1dp 右缘发丝线。
- 窗口控制按钮保留 Windows 右上角轻窗控与左上角微标，未改成左上角红绿灯；桌宠入口继续受 `DesktopPetVisibility` 原开关控制。
- 排查字节码发现 Nucleus `WindowsBackdrop` 在未传 `tint` 且未调用 `WindowAppearance` 时默认叠加 60% 系统深灰雾；落地 `WindowAppearance` 与显式近透明 `tint` 后解决浅色发暗问题并获用户真窗确认。

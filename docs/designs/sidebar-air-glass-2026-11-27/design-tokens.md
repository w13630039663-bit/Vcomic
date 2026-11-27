# Venera 侧栏与浅色全窗通透重做 — Design Tokens

全局令牌正典引用：[desktop-ui-standard-2026-11-05/README.md](file:///g:/venera-desktop/desktop/docs/designs/desktop-ui-standard-2026-11-05/README.md)  
色值唯一居所：[DesktopTheme.kt](file:///g:/venera-desktop/desktop/desktop/src/main/kotlin/com/venera/desktop/gallery/ui/DesktopTheme.kt)（通过 `DesktopSurfaceLiterals` 护栏）

## 1. 尺寸与间距（规格值）

| Token | 原值 | 新值 | 来源/说明 |
|---|---|---|---|
| `railExpandedWidth` | `260.dp`【确定 · `DesktopLargeScreenChrome.kt:356`】 | `240.dp` | 比例更修长 |
| `railCollapsedWidth` | `64.dp`【确定 · `DesktopLargeScreenChrome.kt:357`】 | `64.dp` | 保持 |
| `navRowHeightPrimary` | `36.dp`【确定 · `VeneraLightRailSkin.kt:254`】 | `34.dp` | 精简行高 |
| `navRowHeightGroup` | `34.dp`【确定 · `VeneraLightRailSkin.kt:281`】 | `32.dp` | 紧凑分组项 |
| `groupTopSpacing` | `8.dp` + `MacDivider` | `12.dp`（无横线） | 组间留白呼吸 |
| `footerHeight` | `52.dp`【确定 · `VeneraLightRailSkin.kt:473`】 | `46.dp` | 纯图标底栏 |

## 2. 圆角（同心圆几何）

- `NavRowShape`: `8.dp`（内距 `9.dp`，内部 18dp 微标徽标圆角 `5.dp`）
- `GroupHeaderShape`: `6.dp`
- `FooterIconShape`: `8.dp`
- `BrandLogoShape`: `7.dp`

## 3. 色彩与全窗透明口径（收编进 `DesktopTheme.kt`）

- **浅色 `Light.page`**：`Color(0xFFF6F8FB)`（替代偏灰 `0xFFEAEDF1`，提供干净亮底）
- **浅色 `DesktopGlass.surface`**：`Color.White.copy(alpha = 0.54f)`（配合 `WindowAppearance(Light)` 与无灰 `tint`，全窗统一呈现明亮通透的浅色玻璃）
- **深色 `DesktopGlass.surface`**：`Color(0xFF14161C).copy(alpha = 0.44f)`（全窗统一：暗底低透，保留明显彩色光晕且避免杂字穿透）
- **选中行胶囊底**：
  - 浅色 `Light.railSelected`: `Color(0xFF1A1D21).copy(alpha = 0.085f)`（高透浅底上清晰的轻灰胶囊）
  - 深色 `Dark.pill`: `Color(0xFFFFFFFF).copy(alpha = 0.11f)`（柔和磨砂高亮，取消左紫竖条）
- **侧栏右缘细线 `railLine`**：浅色 `0x121A1D21`、深色 `0x14FFFFFF`
- **分组微图标徽标色系（集中于 `DesktopTheme.kt`）**：蓝/橙/玫红/青绿/紫/琥珀/青灰 7 档柔和低饱和底色 + 白前景图标，仅用于二级分组小微标区分，不引入第二套主装饰色。

## 4. 动效

- 悬停背景即时切色、按压缩放遵循 `VeneraLightRailSkin` 现有轻弹簧 `0.96f`；
- 分组箭头旋转 `180ms` `FastOutSlowInEasing`，展开收起 `expandVertically` / `shrinkVertically` `180ms`。

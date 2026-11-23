# Venera 侧栏 macOS 激进重做 — Design Tokens

## 1. 尺寸与间距（Dimensions & Spacing）
- `sidebarWidthExpanded`: `248.dp`
- `sidebarWidthCollapsed`: `60.dp`
- `primaryRowHeight`: `30.dp`
- `groupRowHeight`: `28.dp`
- `groupHeaderHeight`: `24.dp`
- `footerHeight`: `46.dp`
- `footerIconSize`: `28.dp`
- `navIconSize`: `15.dp`（Compose 侧 `16.dp`）
- `activeIndicatorWidth`: `3.dp`
- `rowHorizontalPadding`: `10.dp`
- `sectionHorizontalPadding`: `8.dp`

## 2. 圆角（Radius）
- `brandLogoRadius`: `7.dp`
- `navRowRadius`: `7.dp`
- `groupHeaderRadius`: `6.dp`
- `footerChipRadius`: `8.dp`
- `footerIconRadius`: `7.dp`
- `petBubbleRadius`: `10.dp`

## 3. 颜色（Colors）
- `sidebarBg`: `Color(0xBD16171C)`（深色半透明毛玻璃底）
- `sidebarBorderRight`: `Color(0x1CFFFFFF)`
- `sidebarTopHighlight`: `Color(0x14FFFFFF)`
- `inkPrimary`: `Color(0xF0FFFFFF)`
- `inkSecondary`: `Color(0x9EFFFFFF)`
- `inkTertiary`: `Color(0x5CFFFFFF)`
- `hoverBg`: `Color(0x13FFFFFF)`
- `activeBg`: `Color(0x1CFFFFFF)`
- `accent`: `Color(0xFF8B7CF6)`
- `accentIcon`: `Color(0xFFC4B5FD)`
- `statusOnline`: `Color(0xFF34D399)`
- `hotBadgeBg`: `Color(0x29F472B6)`
- `hotBadgeInk`: `Color(0xFFF472B6)`

## 4. 动效（Motion）
- `pressScale`: `0.96f`（150ms 弹性回弹）
- `collapseDuration`: `260ms`（FastOutSlowIn / EaseOut）
- `groupExpandDuration`: `220ms`（AnimatedVisibility 垂直展开/收起）
- `chevronRotateDuration`: `200ms`
- `petFloatDuration`: `4200ms`（无限往返上下 `3.dp` 呼吸浮动）

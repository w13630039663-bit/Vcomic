# 侧栏个性化重做（方案一：Bento 磁贴 + 悬浮身份底坞）— 设计分析

规格名：`sidebar-bento-atmosphere-2026-11-27` · 日期：2026-11-27  
标注口径：**【确定】= 规范文档 / 代码直接可读**；**【估算】= 观感推算**，禁止把估算伪装成确定。

## 1. 页面结构

- 根容器：全高玻璃侧栏（沿用 `LightGlassBackdrop` 云母/亚克力玻璃底 + 右缘 `1dp` `railLine` 分割线）【确定 · `VeneraLightRailSkin.kt:136-180`】
  - 展开态（`240dp..248dp`）：自上而下分为四块解耦岛屿
    - 顶部品牌铭牌区：`VCOMIC` 图标与标题（维持轻量化识别区）
    - 主导航 `2×2 Bento` 磁贴网格岛屿：4 项核心入口（首页 / 漫画搜索 / 画廊 / 收藏）按 2 列 2 行排布
    - 二级可折叠导航列表区：单色细线图标 + 折叠分组头（画廊 / 漫画 / 工具），无彩色小方砖，底部预留桌宠栖息空间
    - 底部悬浮双层身份底坞（Floating Dock）：独立圆角玻璃卡片，上层为 Pixiv 头像 + 昵称 + 在线绿点 + 状态副文 + 右箭头，分割线之下并排 3 枚功能格（下载带数量角标 / 设置 / 收起侧栏）
  - 折叠态（`64dp`）：保留既有垂直居中图标窄轨与 `LightChannelPopup` 二级频道浮层

## 2. 区域尺寸与比例

| 区域 | 尺寸 / 占比 | 标注 |
|---|---|---|
| 侧栏展开总宽 | `scope.expandedWidth`（默认 `240.dp`） | 确定 · `VeneraLightRailSkin.kt:134` |
| 侧栏折叠总宽 | `scope.collapsedWidth`（默认 `64.dp`） | 确定 · `VeneraLightRailSkin.kt:135` |
| `2×2 Bento` 单格磁贴 | 高 `58.dp`、列间距 `6.dp`、行间距 `6.dp` | 规格值 · 对齐预览稿 `.bento-tile` |
| 二级导航行 | 高 `31.dp`、内距水平 `10.dp` | 规格值 · 紧凑单色行 |
| 悬浮身份底坞外距 | 水平 `8.dp`、底部 `8.dp`（内部 padding `6.dp`） | 规格值 · 满足同心圆几何 |
| 底坞操作按钮槽 | 高 `30.dp`、3 等分列宽 | 规格值 · 对齐预览稿 `.dock-actions` |

## 3. 视觉层次

- **底层（Layer 0）**：侧栏容器采用 `LightGlassBackdrop`（复用 `DesktopGlass.currentSurface(palette)` 与全窗同透明度；右缘 `1.dp` `railLine` 发丝分割）。
- **岛屿层（Layer 1）**：`2×2 Bento` 磁贴未选中态采用半透微卡片面（浅色白透 / 深色抬升底 + `1.dp` `hairline` 描边）；悬浮底坞采用同级半透卡片面 + `1.dp` `hairline` 描边，不加低 elevation 阴影。
- **激活/选中层（Layer 2）**：磁贴选中态与底坞激活格均切换为全站唯一 `accent` 柔光底衬（浅 `#0A63F6` / 深 `#0A84FF` 的 11%~18% 填充 + 30%~38% 边框），图标与点缀光点着 `accent` 色；二级列表选中态采用主色轻胶囊底衬 + `accent` 图标。

## 4. 逐元素设计参数

| 元素 | 颜色 | 字阶 | 圆角 | 其他 | 标注 |
|---|---|---|---|---|---|
| Bento 磁贴 | 未选 `card` / 选中 `accentSoft` | `12.5sp` Medium/SemiBold | `11.dp` | 描边 `1.dp` `hairline`/`accentBorder`，右上 `6.dp` 激活光点 | 规格值 |
| 二级子项图标 | 未选 `ink2` / 选中 `accent` | — | — | `15.dp` 单色矢量线框（停用彩色方砖） | 规格值 |
| 悬浮底坞外框 | `card`（深色 `raised` 级半透） | — | `12.dp` | 描边 `1.dp` `hairline`，内距 `6.dp` | 规格值 |
| 底坞账号行 | 头像 + `ink1` 昵称 / `ink3` 副文 | `12.sp` SemiBold / `10.sp` | `8.dp` | 在线绿点 `7.dp`，右箭头 `14.dp` | 规格值 |
| 底坞三连功能格 | 未选透明 / 选中 `accentSoft` | `10.sp` Bold 角标 | `8.dp` | 下载角标为 `accent` 实底白字圆丸 | 规格值 |

## 5. 响应式视口规则

| 视口档 | 断点 | 重排规则 |
|---|---|---|
| 展开档 | `scope.isExpanded == true` | 固定内部 `requiredWidth(expandedW)` 排版，展示完整 Bento 磁贴、可折叠分组与悬浮底坞 |
| 折叠档 | `scope.isExpanded == false` | 切换为 `64.dp` 垂直居中图标窄轨 + 频道弹窗，无横向裁切文字 |

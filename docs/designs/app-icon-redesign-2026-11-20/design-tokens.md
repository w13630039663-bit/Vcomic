# 应用图标 · 设计令牌与多尺寸规格（阶段 B 精简版）

- spec: `app-icon-redesign-2026-11-20`
- 定稿: 开卷承字 × 剪影派（白 squircle + 圆笔黑 V + 摊开书 + 书脊白条），主人在 `book-silhouette-final-2026-11-20.html` 获批
- 应用名: **VComic**（桌面端；包名 `com.venera.*` 与数据目录不动）

## 颜色令牌

| 令牌 | 值 | 用途 |
|---|---|---|
| `icon.board` | `#FFFFFF` | 底板 squircle 填充 |
| `icon.edge` | `#ECEFF4` | 底板描边（防浅色任务栏融边，仅 ≥64px 档绘制） |
| `icon.figure` | `#0A0A0A` | V 与书，全案唯一图形色 |

- 禁令：无渐变、无阴影、无第二图形色；眼点/白条等镂空一律用底板白。

## 形状令牌（512 基准画布）

| 令牌 | 值 |
|---|---|
| 底板圆角 | `rx = 116`（22.66%，squircle 近似） |
| V 路径 | `M168 148 L256 318 L344 148`，笔画宽 `52`，圆头圆拐 |
| 书左页 | `M256 336 C240 312 202 300 162 300 C122 300 98 312 96 330 C94 344 104 352 120 350 C164 346 214 358 256 386 Z` |
| 书右页 | 左页水平镜像 |
| 书脊白条 | `x 250-262, y 342-386`，宽 12，`rx 6`；上端停在 V 尖圆头下缘，下端与书下缘平齐 |
| 底板描边宽 | `3`（随档位缩放，<64px 档省略） |

## 多尺寸规格表

| 档 | 来源 | 书脊白条补偿 | 描边 |
|---|---|---|---|
| 256 / 128 | 矢量重绘 512 逐档 | ×1.0 | 有 |
| 64 / 48 | 矢量重绘 | ×1.3 | 有 |
| 32 | 矢量重绘 | ×1.6 | 无 |
| 24 | 矢量重绘 | ×2.0 | 无 |
| 16 | 矢量重绘 | ×2.4 | 无 |

- 全档独立按比例矢量重绘，禁位图缩放糊化；白条是 16px 生死线上唯一可能消失的结构，按上表正向加宽补偿。
- ICO 内嵌：≤64px 档用 BMP 条目（兼容老 API），128/256 用 PNG 条目。

## 输出物与接入点

| 输出 | 接入点 |
|---|---|
| `src/main/resources/vcomic_icon.png`（512 透明底） | `VeneraDesktop.kt:709` 窗口图标、`DesktopSystemTray.kt:29` 托盘 |
| `packaging/vcomic-icon.ico`（七档） | `build.gradle.kts:316` jpackage iconFile、`venera-installer.iss:26` SetupIconFile |
| `packaging/vcomic-icon-transparent.png`（512） | 原 `venera-icon-transparent.png` 同位替代（仓内无代码引用，仅保留同角色资产） |

## 显示名改名清单

- `VeneraDesktop.kt:105` `APP_TITLE`："Venera 桌面端（漫画与图库）" → VComic 系
- `venera-installer.iss:10` `MyAppName`："Venera 桌面端" → VComic 系
- 全仓再扫 `Venera 桌面端` / `Venera桌面端` 字样兜底
- 不动项：包名 `com.venera.*`、数据目录 `%LOCALAPPDATA%\venera`、协议键、`rootProject.name`（仓库层，双端共用，另议）

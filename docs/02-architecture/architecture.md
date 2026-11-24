# 系统架构

本文描述 `:desktop` 的实际分层与隔离方式，内容以代码为准。仓库根为 `desktop/`。

## 1. 定位

单窗口 Compose Multiplatform 桌面应用，承担漫画阅读与图库浏览两条业务线。默认不联网启动，全部数据落在本机数据根。

| 项 | 值 |
|---|---|
| Gradle 模块 | 四个：`:desktop`、`:core-data`、`:gallery-core`、`:engine-probe` |
| UI 框架 | Compose Multiplatform 1.12.2（纯 JVM，非 KMP 多源集） |
| 语言 | Kotlin 2.4.10 |
| 构建 | Gradle 9.7.1 |
| 窗口宿主 | Nucleus（`nucleusApplication` + `DecoratedWindow`） |
| 生产源码规模 | 610 个 `.kt`，约 161,500 行 |
| 测试规模 | 264 个测试类，1690 条断言 |

## 2. 分层模型

四层，分布在四个 Gradle 模块内。`:core-data` 与 `:gallery-core` 为纯 JVM 模块（零 Compose UI 依赖），从构建边界上强制隔离数据层、纯算法层与桌面 UI 层。

| 层 | Gradle 模块 | 包 | 文件数 | JDK | 职责 |
|---|---|---|---:|---|---|
| L0 进程外壳与路由装配 | `:desktop` | `com.venera.desktop.app` | 38 | 25 | `main`、窗口钩子、路由表、大屏状态与 Effects、Win32 窗口工具、取证标志 |
| L1 桌面 UI 与数据实现 | `:desktop` | `com.venera.desktop.{ui,data}` 及各功能子包 | 319+ | 25 | 桌面主屏与弹窗组件（`ui`）、纯数据与读源/备份归档（`data`，零 Compose 依赖）、平台适配器 |
| L2 UI 与公共组件 | `:desktop` | `com.venera.compose.components/feature/ui/source/download/stats` | 150 | 25 | 端口接口、共享组件、功能内核与令牌 |
| L2 纯数据与安全层 | `:core-data` | `com.venera.compose.data.{platform,db,update}`、`security.guard` | 38 | 25 | 平台契约、SQLite DAO/Schema、更新模型、内容守卫 |
| L2 画廊领域与客户端 | `:gallery-core` | `com.venera.compose.gallery.{data,domain}` | 96 | 25 | 图库纯算法、筛选排序策略、各站点 API 客户端 |
| L3 引擎 | `:engine-probe` | `com.venera.engineprobe`、`com.venera.compose.engine` | 25 | 21 | JS 宿主、`EngineSession`、Cookie 罐、JS handler |

`:desktop` 另有一个 `src/probe/kotlin` 源集（`probe` compilation，`associateWith(main)`）：调试探针入口（如 `MicaProbe`、`GlassProbe`、`BlurCostProbe` 等）全部隔离在这里，不进安装包，也不参与 `compileKotlin`；测试源集同时关联 probe，以便直接验探针里的纯函数。兼作生产工具的窗口函数（`DesktopWin32WindowUtils`）已从 `MicaProbe` 拆出留在 `src/main`。

### L1 桌面实现层分布

| 包 | 文件数 | 职责 |
|---|---:|---|
| `desktop.gallery` | 181 | 画廊屏、卡片、详情面板、画师页；站点客户端；收藏、历史、下载管线 |
| `desktop.ui` | 28 | 命令栏、空态、快捷键、页模板、标题栏、公共组件 |
| `desktop.installer` | 8 | 安装向导独立窗口、四态场景、时间轴、外壳控件 |
| `desktop.settings` | 24 | 设置中枢与各类偏好单例 |
| `desktop.platform` | 20 | 路径、数据库端口、HTTP、文件选择、托盘、单实例、协议注册、更新检查 |
| `desktop.search` | 12 | 搜索屏、聚合搜索、来源筛选 |
| `desktop.reader` | 12 | 阅读器主屏、HUD、视口、图片滤镜 |
| `desktop.downloads` | 7 | 下载管线、本地漫画库、本地图片库 |
| `desktop.pixiv` | 6 | 账号、画师、登录、作品页 |
| `desktop.business` | 6 | `BusinessPorts` 的桌面适配器 |
| `desktop.sourcemanage` | 4 | 源管理、源登录 |
| `desktop.detail` | 4 | 详情屏、评论屏 |
| `desktop.favorites` | 3 | 收藏中枢与收藏屏 |
| `desktop.follow` | 7 | 追更：双因子判定、串行巡检器、详情追更端口、追更中心屏、网站收藏导入 |
| 其余 | 4 | 队列、历史、探索、缓存 |

### L2 公共基建层分布

| 包 | 文件数 | 职责 |
|---|---:|---|
| `compose.gallery` | 118 | 共享画廊层：data 40、domain 56、ui 22 |
| `compose.components` | 55 | 共享组件：卡片、侧栏、顶栏、玻璃、多选 |
| `compose.data` | 43 | api 8（端口）、db 16（DAO/Schema）、platform 15（契约）、update 4 |
| `compose.feature` | 22 | 共享功能内核与屏 |
| `compose.ui` | 9 | 颜色、间距、字体、动效令牌（`VeneraMotion` 是卡片入场与页面转场的唯一令牌源） |
| `compose.source` | 9 | 漫画源抽象、JS 载荷解析 |
| `compose.download` | 4 | 下载模型、章节选择、文件命名 |
| `compose.security` | 3 | 内容分级与屏蔽规则匹配 |
| `compose.stats` | 2 | 阅读统计模型与聚合 |

## 3. 依赖方向

方向是单向的，数字为 `import` 命中数。

| 方向 | 命中数 | 说明 |
|---|---:|---|
| `desktop` → `compose` | 936 | 允许 |
| `compose` → `desktop` | 0 | 禁止，由源码尺钉死 |
| `desktop` → `engineprobe` | 47 | 允许 |
| `compose` → `engineprobe` | 0 | 禁止 |
| `compose.gallery` → `compose.components` | 45 | 允许 |
| `compose.components` → `compose.gallery` | 0 | 禁止 |
| `compose.ui` → `compose.gallery` | 0 | 禁止 |

## 4. 隔离机制

### 4.1 单向包依赖

公共层不出现任何指向桌面实现的 `import`。这条由 `DesktopUiIsolationTest` 与 `DesktopNoAndroidImportTest` 守护。

### 4.2 依赖倒置

公共层只定义接口，不持有实现：

- 业务端口：`compose/data/api/BusinessPorts.kt`，17 个构造参数（`contentGuard`、`comics`、`sources`、`history`、`stats`、`favorites`、`downloads`、`localComics`、`network`、`updates` 等）。
- 平台契约：`compose/data/platform/`，15 份接口（`HttpEngine`、`KeyValueStore`、`SqlDatabase`、`PathProvider` 等）。

实现全部位于 `desktop/business`（端口适配器）与 `desktop/platform`（平台能力）。公共层因此没有可反向依赖的实现对象。

### 4.3 副作用单一出口

Toast、剪贴板、分享、显示模式读写、封面打码判据统一经 `LocalUxPlatformServices`，屏本体不出现平台 `import`。打码判据的分布由 `DesktopMaskJudgementSingleSourceTest` 固定。

### 4.4 编译面不含 Android 件

2026-11-17 起撤除了全部 `srcDir("../app/...")` 与 `exclude` 清单。Android 专有类型（`ViewModel`、`Activity`、`Intent`）在本仓不存在，引用会直接编译失败。

### 4.5 桌面侧禁用全局端口工厂

`BusinessPorts.of` 在桌面会抛异常。桌面一律直接构造 `DesktopBusinessPorts`。

### 4.6 追更数据流（core v4）

`comic_follow` 是追更的唯一事实源：详情页端口 `DesktopFollowDetailPort` 与巡检器 `DesktopFollowUpdateChecker` 都只经 `ComicFollowDao` 读写，不回写收藏表的 `has_update`。

- 判定：`FollowUpdateDiff` 纯函数，双因子（话数 / 末话 ID / 非空标题，无章节回退 `remoteUpdateTime`），首轮只建基线。
- 取数：`EngineFollowDetailSource` 按源 `forkEphemeral` 复用一个临时会话，一轮结束 `closeRound()` 释放，不抢窗口常驻会话的锁。
- 装配：巡检器在 `DesktopLargeScreenRouter`（唯一持 `engine` 的层）构造，经参数链下传 `DesktopTabHost`；TabHost 读账本流与进度，Hub 与追更屏只收成品。
- 导入：`DesktopNetworkFollowImporter` 分页取网站收藏 → 单事务整批写 `comic_follow` → 记 `comic_follow_sync_source`；单次上限 200 本、可随时取消。
- 备份：`.venera` 归档写入 `comic_follow.json` / `comic_follow_sync_source.json`，恢复走 `CoreTableBackup.importTable` 整表往返（列由 `PRAGMA table_info` 驱动）。

### 4.7 文件体积与三层静态护栏

由 `DesktopFileSizeAndLayerGuardTest` 强制执行：
- 新建及已完成拆分的生产源码文件字节数上限为 `<= 40 KB`（39 个尚未重构的历史超大文件登记于 `LEGACY_OVERSIZED_ALLOWLIST`，只减不增）。
- `com.venera.desktop` 根目录禁止保留散落 `.kt` 文件，必须归入 `app/`（入口与路由宿主）、`data/`（纯数据/读源/归档/错误总线）、`ui/`（桌面屏与公共组件）或对应功能子包。
- `com.venera.desktop.data`、`:core-data`、`:gallery-core` 禁止出现 `import androidx.compose` 或反向 `import com.venera.desktop.ui`。

## 5. 架构图

```mermaid
flowchart TB
    subgraph ENTRY["L0 进程外壳 · :desktop · JDK 25"]
        M["main 函数 · VeneraDesktop.kt"]
        W["nucleusApplication + DecoratedWindow"]
        S["VeneraDesktopLargeScreen · 路由与根屏"]
    end

    subgraph DESK["L1 桌面实现层 · com.venera.desktop · 345 kt"]
        DG["gallery 181 · 画廊 UI / 站点客户端 / 收藏下载"]
        DU["ui 28 + settings 21 · 公共组件 / 偏好"]
        DM["reader / detail / search / pixiv / downloads / favorites / follow"]
        DA["business 6 + platform 20 · 端口适配器 / 副作用实现"]
    end

    subgraph COMMON["L2 公共基建层 · com.venera.compose · 265 kt"]
        CP["data/api · BusinessPorts 17 族端口"]
        CC["data/platform · 15 份平台契约"]
        CD["data/db 16 · DAO / Schema"]
        CU["components 55 + ui/tokens 9"]
        CG["gallery 118 · data / domain / ui"]
        CF["feature 22 · source 9 · security 3 · stats 2"]
    end

    subgraph ENGINE["L3 引擎 · :engine-probe · JDK 21"]
        EP["engineprobe 18 · JS 宿主 / EngineSession / Cookie 罐"]
        EH["compose/engine 7 · JS Handler"]
    end

    EXT["外部资源 · 34 个 JS 漫画源 · 7 个图库站点客户端"]

    M --> W --> S
    S --> DG
    S --> DU
    S --> DM
    DG --> CG
    DM --> CG
    DU --> CU
    DG --> CD
    DA -.->|"实现端口"| CP
    DA -.->|"实现契约"| CC
    CG --> CU
    CG --> CP
    CU --> CP
    CF --> CP
    DA --> EP
    EP --> EH
    ENGINE --> EXT

    DESK -->|"936 处 import"| COMMON
    DESK -->|"47 处"| ENGINE
    COMMON -.->|"0 处 · 源码尺钉死"| DESK
    COMMON -.->|"0 处"| ENGINE
```

## 6. 运行时链路

### 6.1 启动

`main` → 安装崩溃处理器 → 创建数据目录与运行日志 → 解析命令行取证参数 → `nucleusApplication` → `DecoratedWindow` → `VeneraDesktopLargeScreen`。

### 6.2 漫画源

随包 34 个 JS 脚本位于 `desktop/src/main/resources/sources/`。`EngineAssets.listSourceKeys` 枚举键名 → `DesktopSourceCatalogPort` 包装为 `DesktopEngineComicSource` → `EngineSession.load` 在 JS 侧注册 `ComicSource.sources[key]`。

默认启用 4 个：`baozi`、`copy_manga`、`jm`、`manga_dex`。

### 6.3 图库源

与漫画源是两套机制。图库侧以 `GallerySite` 枚举（7 个站点）加各站客户端实现，无注册表类。来源轴由 `GallerySearchSource` 组合多条腿。

## 7. 目录速查

```
desktop/
├── core-data/src/main/kotlin/com/venera/compose/   L2 纯数据与安全层（零 Compose UI）
├── gallery-core/src/main/kotlin/com/venera/compose/ L2 画廊纯算法与站点客户端（零 Compose UI）
├── desktop/src/main/kotlin/com/venera/
│   ├── desktop/
│   │   ├── app/           L0 入口、窗口钩子、路由表、大屏状态与 Effects
│   │   ├── data/          L1 纯数据、读源、归档与错误总线（零 Compose UI）
│   │   ├── ui/            L1 桌面屏与公共 UI 组件
│   │   └── ...            L1 各业务功能子包（gallery / reader / settings / platform ...）
│   └── compose/           L2 公共 UI 组件与端口定义
├── desktop/src/probe/kotlin/com/venera/desktop/    调试探针源码集（不进安装包）
├── desktop/src/test/kotlin/com/venera/desktop/     L0-L2 单元测试、UI 语义测试与静态护栏测试
├── engine-probe/src/main/kotlin/com/venera/        L3 QuickJS 引擎与会话
├── docs/                                           本文档
└── gradle/libs.versions.toml                       版本目录
```

## 8. 已知不一致

`engine-probe` 命名易被理解为一次性探针，实际是生产依赖（`implementation(project(":engine-probe"))`）。

`DesktopSourceTree` 定义在 `src/test`，是测试用源码定位工具，生产代码引用为 0。

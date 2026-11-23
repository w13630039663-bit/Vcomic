# 构建、测试与发布

所有命令在仓库根 `desktop/` 下执行。Windows 使用 `gradlew.bat`。

## 1. 环境要求

| 项 | 要求 | 说明 |
|---|---|---|
| JDK | 25 | `:desktop` 的 `jvmToolchain(25)` |
| JDK | 21 | `:engine-probe` 与 Gradle daemon 使用 |
| Gradle | 9.7.1 | 由 wrapper 提供，无需单独安装 |
| Inno Setup | 6 或 7 | 仅打安装包时需要 |
| Android SDK | 非必需 | `local.properties` 中的 `sdk.dir` 是历史残留 |

toolchain 由 `foojay-resolver-convention` 按需下载。daemon 目标版本记录在 `gradle/gradle-daemon-jvm.properties`。

首次准备：

```bash
./gradlew --version      # 校验 wrapper 与 daemon JDK
```

## 2. 常用命令

| 场景 | 命令 |
|---|---|
| 编译 | `./gradlew :desktop:compileKotlin` |
| 编译探针源集 | `./gradlew :desktop:compileProbeKotlin` |
| 编译探针源集 | `./gradlew :desktop:compileProbeKotlin` |
| 运行（开发态） | `./gradlew :desktop:app` |
| 运行安装向导窗口 | `./gradlew :desktop:installer` |
| 全量测试 | `./gradlew :desktop:test` |
| 点名测试 | `./gradlew :desktop:test --tests A --tests B` |
| 清测试结果 | `./gradlew :desktop:cleanTest` |
| 出可运行目录 | `./gradlew :desktop:createDistributable` |
| 出 MSI | `./gradlew :desktop:packageMsi` |
| 出自研安装器 | `./gradlew :desktop:packageSetupInstaller` |
| 测试计数汇总 | `node _tools/count_desktop_tests.js` |
| 重复引入检查 | `./gradlew :desktop:checkDuplicateImports` |

开发态运行也可以双击 `run-venera.bat`，它提供菜单入口并透传取证参数。

### 2.1 常用构建参数

| 参数 | 作用 |
|---|---|
| `-PtestForks=N` | 设置测试最大并行 fork 数 |
| `-PskipEngineProbe` | 排除引擎并发测试类 |
| `-Pdesktop.dwmsbt=N` | 覆盖 DWM 材质参数，默认 3 |
| `-Pshot` | 运行期截图 |
| `-Popen=<页面>` | 启动直达指定页面 |
| `-PsevenZipSfx=<路径>` | 指定本地静默自解压模块 `7zSD.sfx` 路径（未指定时自动从 LZMA SDK 提取缓存） |
| `-PinstallerPage=<态名>` | 安装向导起始态：`SPLASH` / `OPTIONS` / `INSTALLING` / `COMPLETE` |
| `-PinstallerTheme=<light\|dark>` | 安装向导主题，缺省浅色 |

### 2.2 静态护栏

`:desktop:check` 已依赖 `:desktop:checkDuplicateImports`：同一颗 Kotlin 文件里出现两条完全相同的
`import` 会直接让构建失败。比较是逐字、大小写敏感的 —— `Spring` 与 `spring` 是两行，不算重复
（2026-11 的 P1-A 清理曾按大小写不敏感口径误删过唯一的小写引入）。

没有接 ktlint：本仓存量还有 1700+ 条 import 排序 / 未用引入类告警，而 ktlint 1.x 的
`ktlint_standard = disabled` 会把单条 `enabled` 压掉，做不到"只开一条规则"。全量格式化留作独立批次。

## 3. 运行时内存参数

由构建脚本注入，见 `desktop/build.gradle.kts` 的 `desktopRuntimeJvmArgs`：

| 参数 | 值 |
|---|---|
| 堆 | `-Xmx1024m -Xms64m` |
| Metaspace | `-XX:MaxMetaspaceSize=192m` |
| 代码缓存 | `-XX:ReservedCodeCacheSize=128m` |
| GC | `-XX:+UseG1GC` |
| Skia 资源缓存 | `-Dskiko.gpu.resourceCacheLimit=128M` |

## 4. 打包链路

三段式。第 1、2 步由任务依赖自动触发，日常只需执行第 3 步：

1. `createDistributable` 产出 app-image 到 `build/compose/binaries/main/app/venera-desktop/`。
2. `prepareSegmentHeapLauncher` 从 JDK 25 派生独立 `java.exe` 到 `build/segment-heap-jdk`，并给产物 exe 注入 SegmentHeap manifest。
3. `packageSetupInstaller` 用 `7z.exe` 压缩 app-image 并与 `7zSD.sfx` 拼接，生成调用自带 `runtime\bin\javaw.exe` 拉起 `InstallerWindowKt` 的单文件 `setup.exe`。

打包用的 jlink 运行时**必须是 JDK 25**：字节码按 `jvmToolchain(25)` 出（major 69），`desktopRuntimeJvmArgs` 里也带 `-XX:+UseCompactObjectHeaders`（JDK 24 起才认）。`compose.desktop.application` 的 `javaHome` 已钉到 25 的 toolchain；不钉会落到跑 Gradle 的 JDK（当前 21），装出来的应用点开即退、退出码 1、日志一个字都没有。

`packageMsi` 是另一条独立入口，产出 MSI。发布使用自研 `packageSetupInstaller`。

| 产物 | 位置 |
|---|---|
| app-image | `desktop/build/compose/binaries/main/app/venera-desktop/` |
| exe | 同上目录内 `venera-desktop.exe` |
| MSI | `desktop/build/compose/binaries/main/msi/`（jpackage 默认布局） |
| 自研安装器 | `desktop/build/compose/binaries/main/installer/` |

## 5. 版本号

`desktopPackageVersion` 定义在 `desktop/build.gradle.kts`，当前值 `2.1.1`，写入 `nativeDistributions.packageVersion`。

纪律：该值与 Android 侧 `versionName` 同串，由 `DesktopDeliveryPackagingTest` 逐字对账。每次发布前递增。

## 6. 排错

| 现象 | 原因 | 处理 |
|---|---|---|
| 打包版侧栏或顶栏材质不生效 | 打包 jlink 用了非预期 JDK | 确认 `createDistributable` 出的 `runtime/release` 是 25，重新打包 |
| 打包版看不到 `D_*` 读数 | `console = false`，标准输出不落终端 | 读 `<数据根>/logs/venera-<pid>-<时间>.log`，一次启动一份 |
| `packageSetupInstaller` 找不到 `7zSD.sfx` | 网络离线且未缓存 `lzma2501.7z` | 传 `-PsevenZipSfx=`，或设环境变量 `SEVENZIP_SFX` |
| 卸载后协议注册残留 | 早期 MSI 链路遗留 | 用自研安装器覆盖安装后再通过卸载入口卸载 |
| 测试计数与横幅数字不符 | `run-venera.bat` 横幅与构建脚本注释均为旧读数 | 以 `node _tools/count_desktop_tests.js` 的输出为准 |
| 引擎相关测试互相干扰 | 并发访问同一单例 | 加 `-PskipEngineProbe` 单独跑 |
| 源码树内出现 `.mimosa/` 目录 | 工具会话状态，未入库 | 可忽略，不要提交 |
| 标准 `application` 起窗报 `Method addObserver must be called on the main thread` | 类路径含 `nucleus.decorated-window-tao`，其注册的协程主调度器让 `Dispatchers.Main` 不是 EDT，lifecycle 把组合线程判成非主线程 | 该入口改用 Nucleus 宿主（`nucleusApplication` + `DecoratedWindow`）；仍用标准 `application` 的探针未处理 |
| 装好的应用点开即退、退出码 1、数据根下无新日志 | jlink 运行时版本低于 jvmArgs 要求（JDK 21 运行时 + `-XX:+UseCompactObjectHeaders` 会直接 `Unrecognized VM option`），字节码 major 69 同样加载不了 | 确认 `createDistributable` 出的 `runtime/release` 是 `JAVA_VERSION="25*"` |
| `venera-desktop.exe 注入 SegmentHeap 失败，退出码=1` | jpackage 产出的 launcher 带只读位，`BeginUpdateResourceW` 返回错误 19 | 注入前先摘只读位（构建脚本已自动处理） |

## 7. 发布前检查

以下项只有真机能验，编译与测试覆盖不到：

- [ ] 安装：perUser 安装、无管理员权限提示、开始菜单项存在
- [ ] 首次启动：窗口正常出现，无空白帧
- [ ] 窗口：拖动、双击最大化、最小化/最大化/关闭按钮、浅深色切换后按钮可见
- [ ] 高 DPI：125% 与 150% 缩放下文字与图标不模糊、不偏移
- [ ] 输入：中文输入法选字不误发送；`←`/`→` 翻页可用
- [ ] 读取：数据根下日志文件正常生成，含 `D_*` 读数
- [ ] 卸载：安装目录清理干净，数据根保留
- [ ] 覆盖升级：旧版本配置与数据全部保留

2026-11-22 已在 Windows 11 本机走通一遍（静默参数 `/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP-`）：安装 2.1.1 → 启动正常 → 覆盖升级 2.1.2（卸载项版本与显示名同步、数据根埋点存活）→ 卸载（安装目录/卸载项/`Software\Classes\pixiv`、`venera`/快捷方式全部清掉，数据根保留）。验证用的临时装机与备份已按原状还原。

## 8. 当前发布状态

| 项 | 状态 |
|---|---|
| 本机构建 | 可出 app-image、MSI、Inno 安装包 |
| 代码签名 | 未签名 |
| 对外发布 | 未发布，仓库无 release 资产 |
| 全量测试 | 当前非全绿，见 `reports/pre-release-audit-2026-11-21.md` |

发布前必须先清掉全量测试的红灯。

## 9. 验证纪律

- 单目标改动走增量：每改一处跑 `:desktop:compileKotlin`，收尾点名跑受影响测试类。
- 全量只在最后一个目标收尾时跑一次。
- 判据红当场修绿，不带着红提交。
- 不得把「跑了部分测试」表述为「全量绿」。

@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import java.io.File
import java.net.URI

/**
 * R1-F 桌面化 S0-1 探针模块：验证 `Compose Desktop + compose-fluent + WindowStyler`
 * 这套版本矩阵能否与本仓的 `AGP 9.3.2 / Kotlin 2.4.10 / material3 钉版` 同仓共存。
 *
 * 判据（写死在方案文档 `docs/rounds/windows-port-feasibility-2026-10.md` 第七节）：
 * 本模块的窗口能起来，**同时** `:app` 的 APK 与 material3 解析结果一字不变。
 *
 * 下面那份排除清单**为什么是这些颗**，从 2026-10-10 起有一把成文的尺子：
 * `docs/rounds/desktop-port-scope-verdicts-2026-10-10.md` §4（逐颗对账 + `[术]`/`[值]` 两类理由的口径）。
 * 摘行之前先在那张表里找到那一颗的裁决行；找不到就当场补一行，别只改这里。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

// 桌面端运行环境使用 JDK 25，支持紧凑对象头等 JVM 特性
kotlin { jvmToolchain(25) }

// 调试探针独立源集：不进生产包，但与 main 同一模块并关联编译，探针仍可读 internal 符号。
sourceSets.create("probe") {
    kotlin.srcDir("src/probe/kotlin")
    compileClasspath += sourceSets["main"].output
    runtimeClasspath += sourceSets["main"].output
}
configurations["probeImplementation"].extendsFrom(configurations["implementation"])
configurations["probeRuntimeOnly"].extendsFrom(configurations["runtimeOnly"])
kotlin.target.compilations.getByName("probe").associateWith(kotlin.target.compilations.getByName("main"))
// 测试要直接调探针里的纯函数：把 probe 输出放进测试类路径，并关联编译以读 internal。
sourceSets["test"].compileClasspath += sourceSets["probe"].output
sourceSets["test"].runtimeClasspath += sourceSets["probe"].output
kotlin.target.compilations.getByName("test").associateWith(kotlin.target.compilations.getByName("probe"))

/**
 * 护栏：同一文件里出现两条完全相同的 import 就报错。
 *
 * 为什么不是 ktlint：ktlint 1.x 的 `ktlint_standard = disabled` 会压掉单条规则的
 * `enabled`，而本仓存量还有 1700+ 条格式类告警 —— 整组开不全、整组关又剩不下东西。
 * 这条任务只做 P1-A 真正踩到的那件事：`Spring` 与 `spring` 是两行，逐字比较，
 * 不许按大小写不敏感的口径"去重"。
 */
val checkDuplicateImports by tasks.registering {
    group = "verification"
    description = "拦住同一文件内的重复 import（逐字、大小写敏感）"
    inputs.files(fileTree("src") { include("**/*.kt") })
    val projectDir = layout.projectDirectory.asFile
    doLast {
        val problems = mutableListOf<String>()
        listOf("src/main/kotlin", "src/test/kotlin", "src/probe/kotlin").forEach { root ->
            val dir = File(projectDir, root)
            if (!dir.isDirectory) return@forEach
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                val counts = mutableMapOf<String, Int>()
                file.readLines()
                    .map { it.trim() }
                    .filter { it.startsWith("import ") }
                    .forEach { line -> counts[line] = (counts[line] ?: 0) + 1 }
                counts.filterValues { it > 1 }.forEach { (line, count) ->
                    problems += "${file.relativeTo(projectDir).invariantSeparatorsPath}: $line（$count 次）"
                }
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("发现重复 import：\n" + problems.joinToString("\n"))
        }
    }
}
tasks.named("check") { dependsOn(checkDuplicateImports) }

// 包版本唯一记账处（nativeDistributions.packageVersion 与 Inno 安装器都从这取；
// 升级发布必须递增，MSI/Inno 才会当成覆盖升级而不是拒装）。
val desktopPackageVersion = "1.0.0"

// PathProvider 的门面接口住在 :app（Android 模块），纯 JVM 模块依赖不了它 ——
// 沿用 :engine-probe 的共享源目录手法：同一份文件、不复制第二份，
// android/ 子包引用 android.content.Context，桌面侧编不进也无需编进，排除即可。
// ── 共享面退役（2026-11-17）：安卓大屏 UI 基建层已整批自持进本模块源码树，
// 原 17 条 srcDir("../app/...") 与 12 段 exclude 一并撤除；被排除的 Android-only
// 文件不搬，编译面与撤除前逐颗一致（Gradle printFace 权威清单对过账）。
// PathProvider 等平台契约住在 data/platform（已自持），android/ 子包是 Android 接线面，不搬。

// Task 6：源脚本**随包分发**。桌面链路真用得上的只有这几类（逐个点名，不整个目录搬）：
//  - `sources/*.js` —— EngineSession.load 取的那份源脚本；
//  - `venera-init.js` / `venera-shim.js` —— DesktopJsHost 装载序列的前两颗。
//     （派单口径 R39 只列了 init，读数在案：DesktopJsHost.kt:102 `readText(venera-shim.js)`
//      是无条件执行的，不带它包内装载必崩，所以按"装载路径确实用到"这条判据把它一起带上。）
//  - `gallery_tags_*.sqlite` —— 批 40 起：画廊那三处译名消费点的数据源
//     （`DesktopGalleryTagDictionary` 复制成真实路径再只读打开；3,257,344 B 的包体增量就是它）。
// **不带** `opencc.txt`：它唯一的消费点是 `app/…/data/tags/ChineseVariantConverter.kt`
//   （`data/tags` 不在本模块 srcDir 的编译面里），桌面侧今天一行都不读它。
// 同理不带 `tags.json` / `tags_tw.json` / `licenses/` / `sources/index.json`
// （源清单是枚举 `sources/` 得来的，不是读这份索引）。
//
// ⚠️ **2026-10-21 更正**：`source_content_warning.json` 从「不带」改成**带上**。
// 原注释把它和 tags.json 归成一类（"桌面侧今天一行都不读它"），那句话当时是真的；
// 但安全评审 C 发现桌面因此**少了一整道内容判据** ——
// 安卓 `ContentGuardManager.maskHit:441-443` 靠这张表判 `nsfw`/`mixed` 源，
// 桌面没有它 ⇒ 历史记录那种**不带 tags** 的模型永远判不出成人向 ⇒ 封面恒裸。
// 现在 `DesktopSourceContentPresets` 真在读它（包内优先，走 classpath）。
// 批 40 起**带上** `gallery_tags_*.sqlite`：桌面那颗 `DesktopGalleryTagDictionary` 真在读它
// （标签墙译名 / 关注栏 / 补全那排三处消费点），取的是"包内优先"那条 classpath 路，
// 名字里的行数就是词典的版本号 ⇒ include 写成 `*` 而不是钉死 `79415`，换一批词典不必改构建脚本。
// include 的坑与上面 kotlin 源集同一条：过滤器对**整个 resources 源集**生效，本模块今天
// 没有自己的 `src/main/resources`，将来加了必须往这张清单里补一颗，否则会被**静默滤掉**。
// 源脚本 / 装载序列 / 标签词典 / 源级分级预设表已自持在本模块 `src/main/resources/`
// （sources/*.js、venera-init.js、venera-shim.js、gallery_tags_*.sqlite、source_content_warning.json），
// 与自有 fonts/、pet/、vcomic_icon.png 同源集，不再需要 srcDir + include 白名单——
// 旧白名单是"整个 resources 源集"的静默过滤器，撤掉后新资源不会被静默滤掉。

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.uiToolingPreview)

    // ── 写法 A（2026-10-20）：安卓大屏 UI 那一层的 material3 ──
    //
    // `components/` 与 `ui/tokens/` 有 60 余处 `androidx.compose.material3.*`
    // （`MiuixTheme` 的色板桥接、`MaterialTheme`、`DropdownMenu`、`Icon`、`Surface`…）。
    // 桌面侧原本只用 `compose.material`（M2），编不过。
    //
    // 坐标取 **JetBrains 的 `org.jetbrains.compose.material3`**（由
    // `compose.material3` 那枚 DSL 提供），不是 `androidx.compose.material3`：
    // 前者是对桌面/JS/iOS 打过包的 CMP 产物，后者是 Android-only 的 AAR。
    //
    // **版本共存的既有事实**（方案文档 §6.5 判据 4 的 A0 实测）：
    // `:app` 今天就已经经由 `com.materialkolor:material-kolor:5.0.1` 把 JetBrains 那颗
    // material3 带在 runtimeClasspath 上（`_qa/a0/baseline_app_deps.txt:887`），
    // 所以"两颗坐标族同挂而不撞同名类"不是本条新引入的风险。
    // 而 `:app` 自己仍解析为 `androidx.compose.material3:1.5.0-alpha22`，一个字节都没变 ——
    // 那正是"隔离安卓端"这条硬约束的兑现方式。
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.material3)

    // 色批 1+2（2026-11-04）：桌面主题桥按强调色种子跑 MD3 色调派生（`DesktopThemeBridge`），
    // 与 :app 用同一库同一点种子式子 —— 上面 A0 那条旧账已经量过这库带的是 JetBrains 那颗
    // material3 的产物（KMP 多平台），所以这是"既有事实的第一次正面使用"，不是新共存风险。
    implementation(libs.material.kolor)

    implementation(libs.compose.fluent.desktop)
    implementation(libs.window.styler)
    // 端到端最小闭环：真源脚本 + GraalJS 宿主 + 取图，全走 :engine-probe 那批共享 handler
    implementation(project(":core-data"))
    implementation(project(":gallery-core"))
    implementation(project(":engine-probe"))
    // Miuix 的 desktop 变体：同一版本目录里能不能与 fluent-desktop 并存，是 S0-1 要量的一格
    implementation(libs.miuix.ui.desktop)
    // 写法 A（2026-10-20）：`components/ComicTileLayout.kt` 用 Miuix 官方图标集那对
    // `MiuixIcons.ListView` / `MiuixIcons.GridView`（注释写明"线宽与其余 Miuix chrome 同源"）。
    // 那颗坐标的依赖面已核：只拉 miuix-core + compose foundation，**不引用 material3**
    // ⇒ 不会顶掉被 Miuix 钉住的那一版（见 libs.versions.toml 的登记）。
    implementation(libs.miuix.icons)
    // S0-7：现有 4 个玻璃消费点都先过 isRuntimeShaderSupported()，
    // 它在 desktop 返回什么决定"玻璃整段静默跳过"还是"真能画"
    implementation(libs.miuix.blur)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    // konachan.com（R18 库）过盾桥的 WebView2 Evergreen 宿主（2026-11-14 主人令「试试 WebView2」）：
    // 帧捕获式架构 —— 无原生可见 surface、不进任务栏，挑战框画进自家 Compose 面板里点。
    // Windows x64 + 桌面 JDK；运行时吃系统 WebView2 Evergreen，仓里零打包体积。
    implementation("li.songe.webview2:webview2-compose:0.1.1")
    // chrisbanes/haze：首页浅色实验皮（HOME_LIGHT）的玻璃轨/底部悬浮条模糊（2026-11-07）
    implementation(libs.haze)
    implementation(libs.haze.blur)
    // JsonKeyValueStore 的落盘格式（`data/platform` 的共享源里就用这一棵树，不再引第二套 JSON 库）。
    // 必须显式声明：`:engine-probe` 那份是 implementation，不透传到本模块的编译 classpath。
    implementation(libs.kotlinx.serialization.json)
    // Task 3：JdbcSqliteDatabase 的驱动。坐标版本照阶段 1 方案 D3 写死（已在 Maven Central 核到）。
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    // Task 4b：`data/db` 的持久层 JSON（JsonValues / CoreTableBackup）替代原来的 android `org.json`
    // ——后者桌面侧根本没有。生产调用方是真的：`sync/BackupManager` 导出与恢复归档的九个 member、
    // `sync/ForeignArchiveImport` 读 PicaComic 的 `sync_data`、`security/guard/ContentGuardManager`
    // 读 assets 的源级预设表，全走这一层。
    // （Task 4a 曾为已删的 `folderToJson` 加过它又撤掉；这次带进来的是有生产调用方的那份。）
    implementation(libs.gson)

    // Coil3 图片加载器（桌面侧自持，不走 VeneraImageFetcher）
    // libs.coil = coil3 (3.6.2)，已在 libs.versions.toml 定义
    // 桌面这一枚**不是可选项**：Coil 的 `ImageLoader` 作用域吃 `Dispatchers.Main.immediate`，
    // 而 Compose Desktop 不把那个 dispatcher 带上 classpath —— 少了这一行，第一次构造
    // `SingletonImageLoader` 就弹一枚 AWT Error 框「Module with the Main dispatcher is missing」
    // （2026-10-08 第四十七批的图证拍到的，判据 645 条当时全绿）。版本不写：靠图里已有的
    // `kotlinx-coroutines-bom` 供，写死一枚反而会与 core 不同版（那句报错后半段警告的正是这件事）。
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing")
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // 大图那一屏的缩放能力（第五十批）。选它的三条读数都写在
    // docs/rounds/desktop-image-viewer-design-2026-10-08.md §2：stable 2.16.0、
    // desktopRuntime 的 compose 三件与本仓逐字同版（1.12.1）、滚轮是一等公民。
    // ⚠️ 它的滚轮默认档是 EnabledWithCtrlKey，Task 5 那处必须显式选 Enabled。
    // ⚠️ 本仓 Android 侧在用的 me.saket.telephoto 那一族不取：`zoomable-image-coil3` 没有
    // desktop 变体（0.16.0 与 0.19.0 两次实测 desktop=NONE），且它的桌面件不含 Wheel。
    implementation("net.engawapg.lib:zoomable:2.16.0")

    // `data/platform/HttpEngine.kt`在上面的共享 srcDir 里，而它签名上的 `OkHttpClient` 是 okhttp 的类型
    // —— 不显式声明就编不过。必须显式：`:engine-probe` 那份是 implementation，不透传到本模块编译 classpath。
    // 注意这是"接口签名需要它的类型"，不等于桌面已经接好了一条真路（桌面侧实现件与它一起落在 S2）。
    implementation(libs.okhttp)

    // ── 2026-11-02 主人令「完善 booru 视频播放」：mp4 + webm 的统一解码器 ──
    // Gelbooru/Safebooru 给 mp4、yande.re 给 webm(VP8/VP9)。JavaFX Media 对 webm 不稳会漏掉 yande.re，
    // media3/ExoPlayer 是 AndroidX（桌面编不过）⇒ 取 **vlcj**（libvlc 绑定，全格式 + HTTP referrer/UA
    // 注入方便）。⚠️ 运行期依赖本机装了 VLC（vlcj 4.x 用内置 NativeDiscovery 找 libvlc，找不到时屏上
    // 如实念"没装 VLC"，不假装能播）。只在 `DesktopVideoLauncher` 那颗原生独立窗里用，不进 Compose 场景树。
    implementation("uk.co.caprica:vlcj:4.12.1")
    // 2026-11-03 主人令 2.4（下载目录）：原生「选择文件夹」走 JNA 的 Shell32（见 DesktopDirChooser）。
    // vlcj 已把 jna/jna-platform 带在编译类路径上（POM 是 compile 档），这里点名钉版是为了
    // "选择框不跟 vlcj 升级一起漂"——版本与本仓 Gradle 缓存里 vlcj 5.11.0 一致，不产生新下载。
    implementation("net.java.dev.jna:jna-platform:5.11.0")
    // ── 2026-11-03 主人令（方案稿 v4.1 定案）：接 Composables UI 的**无样式原语**层 ──
    // 只接 composeunstyled 聚合颗（checkbox/tri-state/disclosure/tab-group/toggle-switch/
    // progress/tooltip/modal/escape-handler/focus-ring/scrollbars 等逐包全在 api 面上）；
    // **不接** com.composables:ui 那层带官方皮肤的成品 —— 皮仍由 VeneraTokens 穿（AGENTS 禁第二套控件面）。
    // 用法一律经 ui/DesktopVeneraPrimitives.kt 的皮肤层转手，屏上不直接摸库。
    implementation("com.composables:composeunstyled:2.10.2")

    // 阶段 1 起 :desktop 有 JVM 单测；栈跟仓库钉版一致（JUnit4，libs.junit = junit:junit:4.13.2），不引新测试框架
    testImplementation(libs.junit)
    // 离屏语义树测试接入（2026-10-09）：runComposeUiTest 把界面渲成语义树，performClick 验屏上文案变没变。
    // 仍是 JUnit4 runner（compose 自带），未脱离既有测试栈；只补"像素层那一段"盲区（qoder 的纯函数判据盖不到）。
    testImplementation(compose.uiTest)

    // 窗口宿主：Nucleus 的 Tao 后端（进程内无 AWT；主包已内置 win32-x64/aarch64 ANGLE 原生库，
    // 不再外挂不同版本的 nucleus.angle-natives，避免模块与解压目录冲突导致双份 libGLESv2.dll）。
    implementation("dev.nucleusframework:nucleus.nucleus-application:2.5.18")
    implementation("dev.nucleusframework:nucleus.decorated-window-tao:2.5.18")
}

tasks.withType<Test> {
    useJUnit()
    // ── 测试跑法：并行 fork（-PtestForks=N，默认 1 = 老行为一字不变）──
    //   成因是读数而不是感觉：全量 1171 条的**逐颗耗时合计只有 105.7s**，而 `cleanTest` 后
    //   全量墙钟 95–117s ⇒ 时间几乎全花在「一个 JVM 里按顺序跑完 175 个类」上。
    //   同一份 XML 里最贵的一颗是 `DesktopEngineCallConcurrencyTest` = **53.9s**（真 GraalJS、
    //   24 笔串行样本，一颗就占掉全量的一半），第二贵 9.7s —— 所以砍不动它，只能让它和别的类并行。
    //   ⚠️ **这一枚今天不推荐用，代价实测过**（2026-10-22 本机，并行档共 11 跑红一次）——
    //   `DesktopFailureReadoutTest`「三站都答上时 failures 必须为空」报 `expected:<[]> but was:<[YANDERE]>`
    //   （那一次在 `testForks=8 -PskipEngineProbe` 那一格；账见 `docs/rounds/desktop-items-1-5-progress-2026-10-21.md` §117）。
    //   成因不在生产码而在**夹具**：`FakeHttpEngine.shared` 每 JVM 一颗，而
    //   `YandeReClient.getInstance` / `SafebooruClient.getInstance` 把**第一次见到的那颗 engine**
    //   锁进单例（`FakeHttpEngine.fresh` 的头注早就写了这一条）。forks>1 改了「哪颗类落到哪个 JVM」
    //   ⇒ 谁先锁单例成了运气 ⇒ 后来的用例打到别人那张空路由表上。
    //   要开这一枚得先让那三颗单例在测试里可复位（那要动 `app/src/main` 的 client，不在本批面里）。
    //   旋钮留着不删：形状由 `DesktopTestRunKnobsTest` 钉住，默认值不许漂。
    val testForks = providers.gradleProperty("testForks").orNull?.toIntOrNull() ?: 4
    if (testForks > 1) {
        maxParallelForks = testForks
    }
    // ── 测试跑法：按需摘掉那颗最贵的重锤（-PskipEngineProbe，默认关 = 全量一字不变）──
    //   `DesktopEngineCallConcurrencyTest` 一颗 53.9s，而全量逐颗耗时合计才 105.7s ⇒ 它占了一半。
    //   它真起 GraalJS、24 笔串行样本，是给"引擎抢锁"那一格作证的重锤：改屏的日常迭代用不上，
    //   收尾那一跑必须带上。所以这一枚**只能显式开**，且开了要在 stdout 出声 ——
    //   否则"摘了重锤的绿"会被读成"全量绿"（本仓假绿那一族的老账，别再添一笔）。
    //   ⚠️ 实测过的代价（2026-10-22 本机）：这一档 4 跑里 `DesktopAccentFillTest` 红过一次
    //   （`UncaughtExceptionsBeforeTest`，`DesktopAccentFillTest.kt:206`）。**但不是这一枚造的**：
    //   同一条红在**不带任何旋钮**的默认全量里也抓到过（逐跑计数与比例记在台账 §117，这里不抄数免得漂）。它属"全局未捕获处理器 +
    //   谁被撞全凭顺序运气"那一族（成因见 `DesktopEngineCallConcurrencyTest` 头注 §⚠️），
    //   摘重锤只是挪了时间轴、让撞上的机会变了。**所以这一枚只用于中间迭代，收尾仍要跑不带它的全量。**
    if (providers.gradleProperty("skipEngineProbe").isPresent) {
        filter.excludeTestsMatching("com.venera.desktop.architecture.DesktopEngineCallConcurrencyTest")
        doFirst {
            println("D_跑法警告 已摘掉 DesktopEngineCallConcurrencyTest（-PskipEngineProbe）⇒ 这一跑**不是全量**，收尾请复跑不带这一枚")
        }
    }
    // ⚠️ 把**本构建脚本**声明成测试输入（2026-10-04 桌面第十批实测才加）。
    //   有几条判据是"扫源码文本"的，其中一条扫的就是**这颗文件**（`-PstartSection=` 有没有真被
    //   拼进 `args`）。而 Gradle 不知道这件事：改这里它仍报 `:desktop:test UP-TO-DATE` ——
    //   扰动实验第一次就是这么"绿"过去的（读数：`Task :desktop:test UP-TO-DATE` + `GRADLE_EXIT=0`，
    //   加 `--rerun-tasks` 复跑才红）。**扫构建脚本的判据必须自己声明这个输入，否则它会拿着旧结果放行。**
    inputs.file(project.buildFile)
}

// 运行内存参数的**唯一出处**：`compose.desktop.application`（打包 launcher）与自定义 `app`
// JavaExec 任务（开发起窗）各挂一份 —— 只写在 application 那格里时，`gradlew :desktop:app`
// 完全吃不到，堆回落 8.5G 默认（实测读数：VM.flags MaxHeapSize=8547991552）。
// ⚠️ 堆不是 RSS 的全部 —— Skia 位图走原生内存，`-Xmx` 管不到它。
// 材质请求档对照开关（2026-11-17）：`./gradlew :desktop:app -Ddesktop.dwmsbt=2` 试 Mica、
// =4 试 MicaAlt，缺省 Acrylic。传进应用 JVM 才能被 DesktopDwmBackdrop 读到。
val dwmsbtArg = "-Ddesktop.dwmsbt=" + providers.gradleProperty("desktop.dwmsbt").orElse("3").get()

val desktopRuntimeJvmArgs = listOf(
    dwmsbtArg,
    // 桌面端含图库瀑布流、大图解码与切页双屏共存，上限放宽到 1024m 并使用低停顿 G1GC，
    // 避免 256m + SerialGC 在切页时频繁触发单线程 Full GC 导致界面冻屏。
    "-Xmx1024m",
    "-Xms64m",
    "-XX:MaxMetaspaceSize=192m",
    "-XX:ReservedCodeCacheSize=128m",
    "-XX:+UseG1GC",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+UseCompactObjectHeaders",
    "-Dskiko.gpu.resourceCacheLimit=128M",
)

compose.desktop {
    application {
        // 打包对象从"探针窗"换成真正的最小闭环 App：
        // 要验的是 GraalJS 能不能活过 jlink 出来的运行时（S0-9 那次打的是 SpikeWindow，没覆盖到）
        mainClass = "com.venera.desktop.app.VeneraDesktopKt"

        // 打包用的 jlink 运行时必须是 25：字节码按 jvmToolchain(25) 出（major 69），
        // 且 desktopRuntimeJvmArgs 带 `-XX:+UseCompactObjectHeaders`（JDK 24 起才有）。
        // 不钉就会落到跑 Gradle 的 JDK 21 上——JVM 起不来，launcher 静默退 1，应用一个字都不打。
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }.get().metadata.installationPath.asFile.absolutePath

        jvmArgs += desktopRuntimeJvmArgs

        // S0-9 打包分发探针：出可安装的 exe/msi，并显式带 jdk.accessibility
        // （Compose Desktop 的无障碍走 Java Access Bridge，缺这个模块则读屏软件全哑）
        //
        // 第一百三十九批 · 交付形态第一颗：包名/版本/描述脱离探针期那一套，并**关掉控制台**。
        // 那一枚开关不是独立决策 —— 它的前提是本批那条 `desktopRunLogFile`：读数第一次有盘上
        // 那一份（`<数据根>\logs\venera-<pid>-<时间>.log`，UTF-8），关 stdout 才不会把整族包内取证
        // 打哑。那三课（缺 jdk.unsupported / 缺 java.sql / 包里取不到 sqlite 原生库）全只有 stdout
        // 一条读数路 ⇒ **先有文件再去控制台**，次序反了就是拿证据换整洁。
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "venera-desktop"
            // 与 Android 侧 `app/build.gradle.kts` 的 versionName 同一档（2026-10-26 合主线后跟到 2.1.1 / versionCode 2110）：
            // 两端各留一份版本号，早晚会有一批只改一边，那时"这台机器装的是哪一版"就没有答案。
            // 唯一记账处在文件顶部的 desktopPackageVersion，Inno 安装器任务从这里取。
            packageVersion = desktopPackageVersion
            // 无障碍：Compose Desktop 走 Java Access Bridge，缺 jdk.accessibility 则读屏软件全哑。
            // jdk.unsupported：`sun.misc.Unsafe` 住在这个模块里，而 jlink 的自动模块推导看不出来
            // —— Truffle 的 NodeClassImpl 启动即 NoClassDefFoundError: sun/misc/Unsafe，
            // 打包跑第一次就把 GraalJS 打死（实测读数见 docs/rounds/windows-r1f-s0-spike-2026-10-02.md §七）。
            // java.sql：同一族的第二课，这次打死的是"读库"那一条路。sqlite-jdbc 是**自动模块**，
            // 它对 java.sql 的依赖 jlink 推不出来 ⇒ 交付包里 `java.sql.SQLException` 直接找不到，
            // 而开发跑用的是全 JDK，判据与 dev 起窗都抓不到 —— 只有打包跑看得见：
            // `D_异常 … 因=NoClassDefFoundError: java/sql/SQLException 栈=DesktopDatabasePorts.kt:200`（2026-10-10，批 82.9）。
            // java.net.http：ascii2d 搜图、CF 过盾、Edge/E 站网页登录的 CDP 客户端依赖，
            // jlink 默认不会从未显式声明的跨线程/反射链路推导它，不加则在打包版报 NoClassDefFoundError。
            modules("jdk.accessibility", "jdk.unsupported", "java.sql", "java.net.http")
            description = "VComic（漫画与图库）"
            vendor = "VComic"
            // 上面那两句"正式产物要改成写日志文件"本批做完了，所以这里从 true 改成 false：
            // 双击图标不再附带一扇黑窗。⚠️ 代价要说明白：包内 stdout 从此**只能从那份日志读**，
            // 批 90 那族直接抓 exe stdout 的量具不再适用（要改成读那份文件）。
            windows {
                console = false
                // 2026-11-06：默认不开快捷方式/菜单 ⇒ 装完桌面与开始菜单啥都没有。
                // 桌面图标 + 开始菜单 + 固定升级 UUID（重复安装不会装出多份）+ 当前用户安装（免管理员）。
                shortcut = true
                menu = true
                menuGroup = "VComic"
                perUserInstall = true
                upgradeUuid = "B7E4C1A2-3D5F-4A68-9C2B-6E1D0F8A3B52"
                // VComic 图标（docs/designs/app-icon-redesign-2026-11-20 定稿，vcomic_icon.png 转制七档）；
                // 不配的话 jpackage 落 Kotlin 默认 K 图标，exe/安装器/任务栏三处全是它。
                iconFile.set(file("packaging/vcomic-icon.ico"))
            }
        }
    }
}

// S0-2 的 8 组件组合探针（`:desktop:run` 现在指向真 App，探针窗改走这个任务）
tasks.register<JavaExec>("spike") {
    group = "probe"
    classpath = sourceSets["probe"].runtimeClasspath
    mainClass = "com.venera.desktop.SpikeWindowKt"
}

// S0-7：玻璃能力三层读数（纯 stdout，不需要窗口）
tasks.register<JavaExec>("glass") {
    group = "probe"
    classpath = sourceSets["probe"].runtimeClasspath
    mainClass = "com.venera.desktop.GlassProbeKt"
}

// 窗口宿主迁移试点：Nucleus（Tao 后端）起窗。`-Dprobe.backdrop=acrylic|mica` 选窗底材质档。
tasks.register<JavaExec>("nucleusprobe") {
    group = "probe"
    classpath = sourceSets["probe"].runtimeClasspath
    mainClass = "com.venera.desktop.NucleusProbeKt"
    systemProperty("nucleus.tao.debug", "true")
    providers.gradleProperty("probeBackdrop").orNull?.let { systemProperty("probe.backdrop", it) }
}

val isWindowsHost = System.getProperty("os.name").lowercase().contains("windows")
val segmentHeapScript = rootProject.file("scripts/enable-segment-heap.ps1")
val segmentHeapJdkDir = layout.buildDirectory.dir("segment-heap-jdk")
val segmentHeapJavaExe = segmentHeapJdkDir.map { it.file("bin/java.exe") }

// Windows 10/11 默认给 Win32 进程配老 NT Heap（16MB 段扣留空闲原生页不退还系统）。
// 从当前 JDK 派生轻量副本并给其 java.exe 注入 SegmentHeap 清单，不修改共享 JDK 缓存。
val prepareSegmentHeapLauncher = tasks.register("prepareSegmentHeapLauncher") {
    group = "build setup"
    description = "在 build/ 下生成启用 Windows SegmentHeap 的独立开发态 java.exe 启动器"
    onlyIf { isWindowsHost }
    val toolchainLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    doLast {
        val sourceJdk = toolchainLauncher.get().metadata.installationPath.asFile.absolutePath
        val targetJdk = segmentHeapJdkDir.get().asFile.absolutePath
        val proc = ProcessBuilder(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            segmentHeapScript.absolutePath,
            "-PrepareJdkLauncher",
            "-SourceJdk",
            sourceJdk,
            "-TargetJdk",
            targetJdk,
        ).inheritIO().start()
        val code = proc.waitFor()
        if (code != 0) throw GradleException("prepareSegmentHeapLauncher 失败，退出码=$code")
    }
}

// 为 Compose 打包输出的 venera-desktop.exe 注入 SegmentHeap manifest（含 Inno 安装器上游产物）。
tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }.configureEach {
    doLast {
        if (!isWindowsHost) return@doLast
        val appDirName = if (name == "createReleaseDistributable") "main-release" else "main"
        val exe = layout.buildDirectory.file("compose/binaries/$appDirName/app/venera-desktop/venera-desktop.exe").get().asFile
        if (!exe.isFile) return@doLast
        // jpackage 产出的 launcher 带只读位，BeginUpdateResourceW 会以错误 19 失败；先摘掉
        exe.setWritable(true)
        val proc = ProcessBuilder(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            segmentHeapScript.absolutePath,
            "-ExePath",
            exe.absolutePath,
        ).inheritIO().start()
        val code = proc.waitFor()
        if (code != 0) throw GradleException("venera-desktop.exe 注入 SegmentHeap 失败，退出码=$code")
    }
}

// 自研 7z SFX 安装器：把 createDistributable 的 app image 打成单文件 setup.exe，
// 解压后直接拉起内置 runtime 里的 Compose 云母安装向导（InstallerWindowKt）执行安装与卸载登记。
tasks.register("packageSetupInstaller") {
    group = "packaging"
    description = "用 7z SFX 把 app image 打成免管理员的自研云母向导安装器 setup.exe"
    dependsOn("createDistributable")

    val pkgVersion = desktopPackageVersion
    val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/venera-desktop")
    val outDir = layout.buildDirectory.dir("compose/binaries/main/installer")
    val sfxCacheDir = layout.buildDirectory.dir("tmp/sevenzip-extra")
    val customSfxProp = providers.gradleProperty("sevenZipSfx").orNull

    doLast {
        val sevenZipExe = listOfNotNull(
            File("C:\\Windows\\System32\\7z.exe"),
            File("C:\\Program Files\\7-Zip\\7z.exe"),
            File("C:\\Program Files (x86)\\7-Zip\\7z.exe"),
        ).firstOrNull { it.isFile }?.absolutePath ?: "7z"

        val cacheRoot = sfxCacheDir.get().asFile.apply { mkdirs() }
        val sfxFile = listOfNotNull(
            customSfxProp?.let(::File),
            System.getenv("SEVENZIP_SFX")?.let(::File),
            File("C:\\Program Files\\7-Zip\\7zSD.sfx"),
            File("C:\\Program Files (x86)\\7-Zip\\7zSD.sfx"),
            File(cacheRoot, "7zSD.sfx"),
        ).firstOrNull { it.isFile } ?: run {
            val sdkArchive = File(cacheRoot, "lzma2501.7z")
            if (!sdkArchive.isFile) {
                logger.lifecycle("正在下载 7-Zip LZMA SDK 25.01 以提取静默自解压模块 7zSD.sfx …")
                URI.create("https://www.7-zip.org/a/lzma2501.7z").toURL().openStream().use { input ->
                    sdkArchive.outputStream().use { output -> input.copyTo(output) }
                }
            }
            val extractProc = ProcessBuilder(sevenZipExe, "e", "-y", sdkArchive.absolutePath, "bin\\7zSD.sfx", "-o${cacheRoot.absolutePath}")
                .redirectErrorStream(true)
                .start()
            val log = extractProc.inputStream.bufferedReader().readText()
            if (extractProc.waitFor() != 0) {
                throw GradleException("提取 7zSD.sfx 失败：\n$log")
            }
            File(cacheRoot, "7zSD.sfx").also {
                if (!it.isFile) throw GradleException("未能在 lzma2501.7z 中找到 bin\\7zSD.sfx")
            }
        }

        val sourceRoot = appImageDir.get().asFile
        if (!sourceRoot.isDirectory) {
            throw GradleException("找不到打包目录：${sourceRoot.absolutePath}")
        }

        val payload7z = File(cacheRoot, "payload.7z").apply { if (exists()) delete() }
        val packProc = ProcessBuilder(sevenZipExe, "a", "-t7z", "-mx=7", "-mmt=on", payload7z.absolutePath, ".")
            .directory(sourceRoot)
            .redirectErrorStream(true)
            .start()
        val packLog = packProc.inputStream.bufferedReader().readText()
        if (packProc.waitFor() != 0) {
            throw GradleException("压缩安装负载失败：\n$packLog")
        }

        val sfxConfigText = """
            ;!@Install@!UTF-8!
            Title="VComic 安装程序"
            Progress="no"
            RunProgram="venera-desktop.exe --installer-mode --source=. --version=$pkgVersion"
            ;!@InstallEnd@!
        """.trimIndent().replace("\n", "\r\n") + "\r\n"

        val icoFile = file("packaging/vcomic-icon.ico")
        val iconScript = rootProject.file("scripts/set-exe-icon.ps1")
        val patchedSfx = File(cacheRoot, "7zSD-vcomic.sfx")
        sfxFile.copyTo(patchedSfx, overwrite = true)
        if (icoFile.isFile && iconScript.isFile) {
            val iconProc = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", iconScript.absolutePath,
                "-ExePath", patchedSfx.absolutePath,
                "-IcoPath", icoFile.absolutePath,
            ).redirectErrorStream(true).start()
            val iconLog = iconProc.inputStream.bufferedReader().readText()
            if (iconProc.waitFor() != 0) {
                logger.warn("替换安装器图标失败，保留默认图标：\n$iconLog")
            }
        }

        val outRoot = outDir.get().asFile.apply { mkdirs() }
        File(outRoot, "venera-desktop-$pkgVersion-setup.exe").delete()
        val setupExe = File(outRoot, "VComic-$pkgVersion-setup.exe")
        setupExe.outputStream().buffered().use { out ->
            patchedSfx.inputStream().use { it.copyTo(out) }
            out.write(sfxConfigText.toByteArray(Charsets.UTF_8))
            payload7z.inputStream().use { it.copyTo(out) }
        }
        logger.lifecycle("已生成自研云母安装器：${setupExe.absolutePath}")
    }
}

// 安装向导独立窗口：只串界面，进度走时间轴 12s 曲线，不接真实安装。
//   ./gradlew :desktop:installer -PinstallerPage=OPTIONS -PinstallerTheme=dark
//   宿主是 Nucleus（系统材质 Mica/亚克力只能在它的非分层窗上合成），走整包运行时类路径。
tasks.register<JavaExec>("installer") {
    group = "run"
    description = "起安装向导窗口（-PinstallerPage=SPLASH|OPTIONS|INSTALLING|COMPLETE，-PinstallerTheme=light|dark）"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.installer.InstallerWindowKt"
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    args(
        listOfNotNull(
            providers.gradleProperty("installerPage").orNull,
            providers.gradleProperty("installerTheme").orNull,
        )
    )
}

// 批 62：S0-7 §六 欠的那一步 —— 应用内模糊**画不画得出来**（要开一扇窗，靠 WGC 拍帧）
tasks.register<JavaExec>("blurprobe") {
    group = "probe"
    classpath = sourceSets["probe"].runtimeClasspath
    mainClass = "com.venera.desktop.BlurRenderProbeKt"
}

// 批 64：S0-9 —— 那一层糊**每帧花多少钱**（三相：不糊 → 糊 → 再不糊；读数全在 stdout，取完数自己走）。
//   ⚠️ 那三条 UTF-8 是必需的：这一颗念的每一行都带中文（`sh gradlew :desktop:blurcost`）。
// konachan.com WebView2 宿主的诊断探针：量 evaluateJavaScript 的回包编码、隐形挂载下的
//   挑战行为与 5s 库内超时（控制台读数，窗口自动退）。
tasks.register<JavaExec>("webview2Probe") {
    group = "probe"
    classpath = sourceSets["probe"].runtimeClasspath
    mainClass = "com.venera.desktop.gallery.data.Webview2ProbeKt"
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.register<JavaExec>("blurcost") {
    group = "probe"
    classpath = sourceSets["probe"].runtimeClasspath
    mainClass = "com.venera.desktop.BlurCostProbeKt"
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    // 堆上限写死（与 `reader` 同一颗纪律）：读数里那两枚 MB 才可复现。
    jvmArgs("-Xmx2g")
    // 加 -Phold=20000 让每一相起手真等 20 秒 —— 那一档是给 WGC 拍帧用的（毫秒数证明不了"那一相真糊了"）。
    // 加 -Pstep=400 把每帧推进调大：那是**正对照的把手**（三相读数一模一样时，得先证明这把尺子认账）。
    // 加 -Poverlay=anim 让**覆盖层**每帧换内容（生产那一形：墙静帧、动图在浮层里播）。默认静。
    // 加 -Prounds=3 把整副 ABBA 连跑几轮：每轮独立算地板、独立下判词（一轮的地板就是那一轮的散布，说不了话）。
    args(
        listOf(
            providers.gradleProperty("hold").orNull?.let { "hold=$it" },
            providers.gradleProperty("step").orNull?.let { "step=$it" },
            providers.gradleProperty("overlay").orNull?.let { "overlay=$it" },
            providers.gradleProperty("rounds").orNull?.let { "rounds=$it" },
        ).filterNotNull()
    )
}

// S0-8：条漫单列数千图的滚动帧时间与内存
tasks.register<JavaExec>("reader") {
    group = "probe"
    classpath = sourceSets["probe"].runtimeClasspath
    mainClass = "com.venera.desktop.ReaderProbeKt"
    // 堆上限写死，读数才可复现；-PdecodePerItem=true 跑"每项各自解码"的端到端档
    jvmArgs("-Xmx2g")
    args(providers.gradleProperty("decodePerItem").getOrElse("false"))
}

// 端到端最小闭环：真源 → GraalJS → 取图 → Fluent 窗口
//   ./gradlew :desktop:app -Pkey=jm -Pproxy=127.0.0.1:7890
//   加 -Pshot=_qa/desktop-jm.png 时它自截图后退出（无人值守取证）
//   加 -Pautofav=1 / -Pfavcheck=1 走收藏取证两跑（写 / 读回，只为读数存在，见 VeneraDesktop.kt）
//   加 -PstartDomain=SETTINGS 让无人值守取证落在**指定那一域**（认枚举名或中文题名；
//     认不出就退出码 1 且不开窗 —— 不退到默认域，理由在 ForensicFlags.startDomain 的注释里）。
//     ⚠️ 走命令行时**首选枚举名**：中文值要穿过 gradle 属性与 Windows 控制台编码那两层。
//   加 -PstartSection=LATEST 让取证落在**域内 pane 的那一行**（第十批 ⑤；`--start-domain=` 只到域）。
//     认的是行键或中文题名两种，对不上任何行时**不中止但出声**（那句在 ForensicFlags.sectionNote）。
//     例：`-PstartDomain=GALLERY -PstartSection=最新` 才拍得到刚点亮的「最新」那一屏。
//   加 -Pdetail=1 让**详情浮层自己摆出来**（那一层本来要点一张卡才出来，而鼠标自验是禁的）：
//     判读层认 `--detail=<值>`，0/false/off 是关；不给值只按开处理（见 ForensicFlags.state）。
//   加 -Partist=yandere/某标签 让取证**直接进那一位的单人页**（第二十批；那一屏本来要在画师列表上
//     点「看他的图」才出来，而鼠标自验是禁的 ⇒ 没有这一枚时那一屏只编译得过、拍不到图）。
//     值两截 `<站点>/<标签>`，站点段认键、枚举名、站方写法三种；读不出不中止但出声（ForensicFlags.artistNote）。
//   加 -Pranking=MONTH/2024-03 让取证**把排行榜拨到那一档、那一期**（第三十批；「上一期 / 下一期 / 回到本期」
//     那一排本来要把档拨到月/年之后才出现，而鼠标自验是禁的 ⇒ 没有这一枚时那一层只编译得过、拍不到图）。
//     档段认枚举名、下拉里那一整行的题、胶囊上那一小截三种；期段可省（省了就是本期）。
//     ⚠️ 走命令行时**首选枚举名**：中文值要穿过 gradle 属性与 Windows 控制台编码那两层。
// Task 6 第 4 件：这两个开关**把值一起传下去**（`--autofav=<值>`），由程序判读 ——
//   原来只传"给没给"，`-Pautofav=0` 也会点亮，是个假开关形状。现在 0/false/off 是关，且照样打读数。
// 手填凭据那枚取证种子（批 88·下半）。同一颗纪律第十一次：**判读层与消费点接好了也不算通**，
// 漏了这一跳 `-Pcookie=…` 就是"不报错也不做事"，而"罐里没那枚凭据"会被读成"手填那一路坏了"。
// 为什么在这一层摊成**多枚** `--cookie=`：一站要粘几枚字段（ehentai 四枚），而 `-P` 只能递一个串 ——
// 分隔符归 transport 这一层，`ForensicFlags.cookies` 始终只认那一枚参数的规范形状。
// 值形如 `-Pcookie=exhentai.org:ipb_a=1|exhentai.org:ipb_b=2`。
val cookieSeeds: List<String> = providers.gradleProperty("cookie").orNull
    ?.split("|")?.filter { it.isNotBlank() }?.map { "--cookie=$it" }
    .orEmpty()

tasks.register<JavaExec>("app") {
    if (isWindowsHost) {
        dependsOn(prepareSegmentHeapLauncher)
        executable(segmentHeapJavaExe.get().asFile.absolutePath)
    }
    doFirst { println("D_gradle app jvmArgs 含 dwmsbtArg=" + dwmsbtArg) }
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.app.VeneraDesktopKt"
    workingDir = rootDir
    // ⚠️ 强制 UTF-8 写 stdout（2026-10-04 实测才加）：JDK 18+ 的 `System.out` 跟的是
    //   `stdout.encoding`，中文 Windows 上默认落到 GBK，而 Gradle 按 UTF-8 解码子进程输出 ——
    //   结果是**每一条 `D_` 中文读数都变成 U+FFFD**，只有 ASCII 部分（枚举名、数字）活下来。
    //   这一颗任务的产物就是读数，读数码着等于没产；S0 那轮没暴露是因为探针窗只打 `S02_COMPOSED_OK`。
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    // 内存参数与打包 launcher 同源（见 `desktopRuntimeJvmArgs`）—— 只写 application 那格这里吃不到。
    jvmArgs(desktopRuntimeJvmArgs)
    args(
        listOf(
            providers.gradleProperty("key").getOrElse("jm"),
            providers.gradleProperty("proxy").orNull?.let { "--proxy=$it" },
            providers.gradleProperty("shot").orNull?.let { "--shot=$it" },
            providers.gradleProperty("startDomain").orNull?.let { "--start-domain=$it" },
            providers.gradleProperty("query").orNull?.let { "--query=$it" },
            providers.gradleProperty("startSection").orNull?.let { "--start-section=$it" },
            // 详情浮层那颗取证种子（第十一批 P1 判读层接好了，这一跳当时漏了 ⇒ `-Pdetail=1` 不报错也不做事，
            // 而"浮层拍不到图"会被读成"浮层起不来"。判据 `DesktopKeyNavigationTest` ⑨ 钉这一跳。）
            providers.gradleProperty("detail").orNull?.let { "--detail=$it" },
            // 画师单人页那颗种子（第二十批）。同一颗纪律：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Partist=…` 就是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
            // 值两截 `<站点>/<标签>`，站点写法与判读见 ForensicFlags.artist。
            providers.gradleProperty("artist").orNull?.let { "--artist=$it" },
            // 漫画详情那一屏那颗种子（第八十批）。同一颗纪律第七次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pcomic=…` 就是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
            // 值两截 `<漫画号>[/<题名>]`，判读见 ForensicFlags.comic。
            providers.gradleProperty("comic").orNull?.let { "--comic=$it" },
            // 阅读那一屏那颗种子（第八十一批）。同一颗纪律第八次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pread=…` 就是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
            // 值一到三截 `<漫画号>[/<话号>[/<章名>]]`，判读见 ForensicFlags.read。
            providers.gradleProperty("read").orNull?.let { "--read=$it" },
            // 阅读那一屏的**起点页**那颗种子（第八十二批半）。同一颗纪律第九次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-PreadPage=3` 就是"不报错也不做事"，而"页号没写进历史那一行"会被读成"写口坏了"。
            // 值是 0 起的页索引（不是第几页），判读见 ForensicFlags.readPage。
            providers.gradleProperty("readPage").orNull?.let { "--read-page=$it" },
            // 第八十七批：把阅读那一屏顶到某一档模式（`-PreadMode=HORIZONTAL_RTL`）。
            // 没有这一跳，横向那一面墙在帧上永远不存在 —— 它要"点模式那一排的一枚"才出来，而取证不许动鼠标。
            providers.gradleProperty("readMode").orNull?.let { "--read-mode=$it" },
            // 批 92：把浮层与大图那一屏**起在哪一档**种下来（`-PdetailTier=原图`，屏名与枚举名都认）。
            // 同一颗纪律第十一次：**判读层与消费点接好了也不算通**，漏了这一跳 `-PdetailTier=…` 就是
            // "不报错也不做事"，而"原图档那一格拍不到图"会被读成"那一层坏了"。判读见 ForensicFlags.detailTier。
            providers.gradleProperty("detailTier").orNull?.let { "--detail-tier=$it" },
            // 批 96：把某一话**整话写进本机**那颗种子（`-Pdownload=<漫画号>/<话号>`）。
            // 同一颗纪律第十二次：**判读层与消费点接好了也不算通**，漏了这一跳 `-Pdownload=…` 就是
            // "不报错也不做事"，而"那一格拍不到落盘"会被读成"整话下载坏了"。
            // 它没有别的入口可点：桌面那一格挂在**长按**上（`combinedClickable` 有 onLongClick 而没有 onRightClick），
            // 而取证不许动鼠标（手册 §8.1）⇒ 只能让种子去按同一颗 runner。判读见 ForensicFlags.download。
            providers.gradleProperty("download").orNull?.let { "--download=$it" },
            // 把某一颗随包源当成本台"停用着"起来（批 83.1）：只为拍到帧 —— 那一格今天只有离屏语义树量过。
            // 同一颗纪律第十次：**判读层与消费点接好了也不算通**，漏了这一跳 `-Pdisable=jm` 就是
            // "不报错也不做事"，而"源管理那一屏说一颗都没停用"会被读成"停用那枚开关坏了"。
            providers.gradleProperty("disable").orNull?.let { "--disable=$it" },
            // 排行榜那颗种子（第三十批）。同一颗纪律：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pranking=…` 就是"不报错也不做事"，而"那一排拍不到图"会被读成"那一层起不来"。
            // 值两截 `<档>[/<期>]`，写法与判读见 ForensicFlags.ranking。
            providers.gradleProperty("ranking").orNull?.let { "--ranking=$it" },
            // 反搜那颗种子（第三十六批）。同一颗纪律：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Preverse=1` 就是"不报错也不做事"，而"结果那一格拍不到图"会被读成"那一格起不来"。
            // 值只判开不开（`--reverse` / `--reverse=0`），它顺带把详情浮层摆出来（那一枚按钮在浮层里）。
            providers.gradleProperty("reverse").orNull?.let { "--reverse=$it" },
            // 反搜本机那一格那颗种子（第七十七批 下半）。同一颗纪律第六次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-PreverseFile=C:/x.png` 就是"不报错也不做事"，而"那一格拍不到图"会被读成"那一格起不来"。
            // 值是一条本机路径（它替用户跳过系统文件框那一步 —— 那一步今天没有鼠标走不到）。
            providers.gradleProperty("reverseFile").orNull?.let { "--reverse-file=$it" },
            // 大图那一层的两枚种子（第五十批）。同一颗纪律第三次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pviewer=1` 就是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
            // `viewer` 判开不开（`--viewer` / `--viewer=0`，默认是**进**）；`viewerZoom` 是一个倍数。
            providers.gradleProperty("viewer").orNull?.let { "--viewer=$it" },
            providers.gradleProperty("viewerZoom").orNull?.let { "--viewer-zoom=$it" },
            // 崩一枚那颗种子（第五十三批）。同一颗纪律第四次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pcrash=thread` 就是"不报错也不做事"，而"崩了没念出那一行"会被读成
            // "那颗 handler 没装上"。值只认 thread 与 edt（两档各走一条线程路，判读见 ForensicFlags.crash）。
            providers.gradleProperty("crash").orNull?.let { "--crash=$it" },
            // 「复制图片」那颗种子（第五十四批）。同一颗纪律第五次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Pcopy=1` 就是"不报错也不做事"，而"剪贴板是空的"会被读成"那一枚写口坏了"。
            // 值只判开不开，它顺带把详情浮层摆出来（那一枚按钮在浮层里）。
            providers.gradleProperty("copy").orNull?.let { "--copy=$it" },
            // 「存到本机」那颗种子（第八十五批）。同一颗纪律第六次：**判读层与消费点接好了也不算通**，
            // 这一跳漏了的话 `-Psave=1` 就是"不报错也不做事"，而"盘上没多那颗文件"会被读成"落盘那层坏了"。
            // 值只判开不开，它顺带把详情浮层摆出来（那一枚按钮也在浮层里，与 -Pcopy 同一格）。
            providers.gradleProperty("save").orNull?.let { "--save=$it" },
            providers.gradleProperty("autofav").orNull?.let { "--autofav=$it" },
            providers.gradleProperty("favcheck").orNull?.let { "--favcheck=$it" },
            // 起窗直达种子（第一百二十九批）。同一颗纪律：**判读层与消费点接好了也不算通**，
            // 漏了这一跳 `-Popen=HISTORY` 就是"不报错也不做事"，而"那一屏拍不到图"会被读成"那一屏起不来"。
            // 值认这条大屏线真落得下去的屏名（底栏 HOME/FAVORITES/SEARCH/GALLERY/EXPLORE，
            // 二级页 HISTORY/SOURCES/QUEUE/DOWNLOADS/GALLERYSEARCH/BOOKMARKSHUB），判读见 ForensicFlags.open。
            providers.gradleProperty("open").orNull?.let { "--open=$it" },
        ).filterNotNull() + cookieSeeds
    )
}

// S0-5：中文 IME 探针窗（判据只能由真 IME 会话给出，所以单独一个可启动入口）
tasks.register<JavaExec>("ime") {
    group = "probe"
    classpath = sourceSets["probe"].runtimeClasspath
    mainClass = "com.venera.desktop.ImeProbeKt"
}

// S0-6a：不引 window-styler，自己用 FFM 调 DWM 设 Mica（要拿 HWND ⇒ 两个 add-exports 是探针的一部分）
tasks.register<JavaExec>("mica") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.desktop.MicaProbeKt"
    workingDir = rootDir
    jvmArgs(
        "--add-exports", "java.desktop/sun.awt=ALL-UNNAMED",
        "--add-exports", "java.desktop/sun.awt.windows=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
}

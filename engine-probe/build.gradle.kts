/**
 * S0-3 探针：桌面侧（纯 JVM）用 GraalJS 跑**真源脚本**，判据见
 * `docs/rounds/windows-port-feasibility-2026-10.md` 第七节 S0-3。
 *
 * engine 的 7 颗去 Android 化 handler 已自持在本模块源码里；
 * `VeneraJsEngine.kt` 是 WebView 载体，不搬 —— 桌面侧由本模块的 `DesktopJsHost` 顶替。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    // QuickJS（quickjs-kt-jvm）：替换原 GraalJS/Truffle，冷启动与内存占用降至几 MB 级，支持 ES2023 + async/await
    implementation("io.github.dokar3:quickjs-kt-jvm:1.0.18")

    implementation(libs.okhttp)
    // jm 的页图实测是 WebP（RIFF....WEBP/VP8），JVM 的 ImageIO 原生不认 —— 桌面图片管线要外挂解码器
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.2")
    implementation(libs.gson)
    implementation(libs.jsoup)
    // CF 指纹伪装：TLS/JA3/JA4 + HTTP/2 指纹按 Chrome 复刻，ascii2d / safebooru 过盾实测
    implementation("com.github.zhkl0228:impersonator-okhttp:1.11.0")
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

// 代理从命令行传：-Pproxy=127.0.0.1:7890（本机直连这些域名全不通）
val probeArgs: (String) -> List<String> = { key ->
    listOf(key) + providers.gradleProperty("proxy").orNull?.let { listOf("--proxy=$it") }.orEmpty()
}

tasks.register<JavaExec>("nhentai") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.ProbeMainKt"
    args = probeArgs("nhentai")
    workingDir = rootDir
}

tasks.register<JavaExec>("jm") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.ProbeMainKt"
    args = probeArgs("jm")
    workingDir = rootDir
}

// S0-4 判据里点名的源：ehentai（exhentai 那半边正是 CF + 登录双挡）
tasks.register<JavaExec>("ehentai") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.ProbeMainKt"
    args = probeArgs("ehentai")
    workingDir = rootDir
}

// S0-4：JVM OkHttp 直撞已知 CF 站点，不过盾能不能出数据
tasks.register<JavaExec>("cf") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.CfProbeKt"
    args = listOfNotNull(providers.gradleProperty("proxy").orNull?.let { "--proxy=$it" })
    workingDir = rootDir
}

// 运行期 JDK 对 GraalJS 的差值：-Pjdk=21 / -Pjdk=25（25 由 gradle 自动下载）
val benchJdk = providers.gradleProperty("jdk").getOrElse("21").toInt()
tasks.register<JavaExec>("bench") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.JitBenchKt"
    workingDir = rootDir
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(benchJdk)
    }
    args = listOfNotNull(providers.gradleProperty("proxy").orNull?.let { "--proxy=$it" })
}

tasks.register<JavaExec>("weblogin") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.WebLoginProbeKt"
    workingDir = rootDir
}

tasks.register<JavaExec>("weblogin-search") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.WebLoginProbeKt"
    workingDir = rootDir
    args = listOf("--realdata", "--search=touhou", "--detailtest", "ehentai")
}

tasks.register<JavaExec>("impersonator") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.ImpersonatorProbeKt"
    workingDir = rootDir
}

tasks.register<JavaExec>("ascii2d") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.Ascii2dProbeKt"
    workingDir = rootDir
}

tasks.register<JavaExec>("ascii2d-cdp") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.Ascii2dCdpProbeKt"
    workingDir = rootDir
    // -Da2d.headless=1 时走 --headless=new（先试无感路径，被拦再 headed 弹窗）
    systemProperty("a2d.headless", providers.gradleProperty("a2d.headless").orElse(providers.systemProperty("a2d.headless")).orElse("0"))
}

tasks.register<JavaExec>("exauth") {
    group = "probe"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.venera.engineprobe.ExAuthProbeKt"
    workingDir = rootDir
    systemProperty("exauth.min", providers.systemProperty("exauth.min").orElse("0"))
}

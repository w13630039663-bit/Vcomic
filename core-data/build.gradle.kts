/**
 * 纯 JVM 数据与平台契约模块（零 Compose UI 依赖）。
 *
 * 承载 `data/platform`、`data/db`、`data/update` 与 `security/guard`，
 * 物理隔离持久层、SQLite 访问与安全守卫，杜绝反向依赖桌面 UI 层。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(25) }

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    implementation(libs.gson)
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(libs.junit)
}

tasks.withType<Test> {
    useJUnit()
    maxParallelForks = 4
}

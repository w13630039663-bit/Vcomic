/**
 * 纯 JVM 画廊领域算法与客户端数据层模块（零 Compose UI 依赖）。
 *
 * 承载 `gallery/data` 与 `gallery/domain`，仅依赖 `:core-data`，
 * 从编译边界上保证纯业务算法、网络客户端与筛选状态机不依赖 UI。
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(25) }

dependencies {
    api(project(":core-data"))
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(libs.junit)
}

tasks.withType<Test> {
    useJUnit()
    maxParallelForks = 4
}

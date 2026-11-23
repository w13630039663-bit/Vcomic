package com.venera.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.BasicTitleBar
import dev.nucleusframework.window.WindowScaffold
import dev.nucleusframework.window.WindowsBackdrop
import dev.nucleusframework.window.WindowsBackdropStyle

/**
 * 窗口宿主迁移试点：Nucleus（Tao 后端）最小起窗。
 *
 * 判定三件事：Compose 内容能否渲染、窗底材质能否合成、窗控是否可用。
 * 材质档由 `-Dprobe.backdrop=none|acrylic|mica` 选择，缺省不请求材质。
 */
fun main(args: Array<String>) {
    registerSkikoIfPresent()
    val backdrop = System.getProperty("probe.backdrop", "none")
    println("[probe] backdrop=$backdrop")

    nucleusApplication(args = args, backend = NucleusBackend.Tao) {
        DecoratedWindow(
            title = "Nucleus 探针",
            onCloseRequest = { exitApplication() },
            state = rememberWindowState(size = DpSize(900.dp, 620.dp)),
        ) {
            when (backdrop) {
                "acrylic" -> WindowsBackdrop(WindowsBackdropStyle.Acrylic)
                "mica" -> WindowsBackdrop(WindowsBackdropStyle.Mica)
            }
            WindowScaffold(
                modifier = Modifier.fillMaxSize(),
                titleBar = { BasicTitleBar() },
            ) { inner ->
                ProbeContent(alphaSurface = backdrop != "none", padding = inner)
            }
        }
    }
}

/**
 * CMP 1.13 把图形/文字实现改成注册表，非 AWT 宿主必须在建场景前自行注册；
 * CMP 1.12 无此机制，反射调用让同一份探针跑两个版本。
 */
private fun registerSkikoIfPresent() {
    runCatching {
        Class.forName("androidx.compose.ui.platform.ComposeUiSkikoRuntime_nonAndroidKt")
            .getMethod("registerSkikoComposeImplementation")
            .invoke(null)
    }.onSuccess { println("[probe] skiko implementation registered") }
        .onFailure { println("[probe] graphics registry absent, skip registration") }
}

@Composable
private fun ProbeContent(alphaSurface: Boolean, padding: PaddingValues) {
    // 请求材质时内容底色全透，由系统在窗后合成材质；不请求时用不透明底当渲染对照。
    val surface = if (alphaSurface) Color.Transparent else Color(0xFF1B1B1F)
    Box(
        modifier = Modifier.fillMaxSize().background(surface).padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text("Nucleus 探针", color = Color.White, fontSize = 28.sp)
            Text("Tao 宿主渲染正常", color = Color(0xFF9FE0A0), fontSize = 16.sp)
            Button(onClick = { println("[probe] 按钮被点击") }) {
                Text("点击读数")
            }
        }
    }
}

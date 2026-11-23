package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面源码树（`desktop/src/main` 全树）里**一次都不许点名 Android SDK**。
 *
 * ## 为什么不按 `^import android` 扫
 *
 * import 只是其中一种写法：`GalleryTagCategories.kt` 里那句日志是**内联 FQN**
 * `android.util.Log.i(…)`，整颗文件一句 `import android.*` 都没有。按形状扫就要把形状扫全。
 *
 * ## 出处
 *
 * 从前这条住在 `:app` 的测试里（app/src/test），为的是"`:desktop:compileKotlin` 炸了时
 * 它照样红"。共享面整批自持、`:app` 模块退役后，借出账不复存在，这条搬进桌面测试，
 * 扫描对象就是 `desktop/src/main` 全树。
 *
 * ⚠️ `androidx.compose.*` 不在禁引集合里：那是 Compose Multiplatform 在桌面上也用的包名，
 * 桌面 UI 代码合法。这里禁的是 `android.*`（Android SDK 本体）。
 */
class DesktopNoAndroidImportTest {

    @Test
    fun `桌面源码树不许出现 Android SDK 的点名`() {
        val hits = DesktopSourceTree.desktopMainSources().flatMap { file ->
            val rel = file.absolutePath.substringAfter("desktop/src/main").replace('\\', '/')
            file.readLines().mapIndexedNotNull { index, line ->
                // 注释行放过：文档里提一句"这颗从前吃 Context"是要的，不是越界
                if (line.isCommentLine()) return@mapIndexedNotNull null
                val m = ANDROID_SDK.find(line) ?: return@mapIndexedNotNull null
                "$rel:${index + 1}  ${line.trim()}  ← ${m.value}"
            }
        }
        assertTrue("桌面源码树里点名了 Android SDK：\n$hits", hits.isEmpty())
    }

    private companion object {
        /** `android.` 内联 FQN / import / `androidx.` 非 compose 的那几族（详见类头注）。 */
        val ANDROID_SDK = Regex("""(?<![\w.])(android\.\w+|androidx\.(?!compose)\w+(?:\.\w+)*)""")
    }
}

private fun String.isCommentLine(): Boolean = trimStart().startsWith("//") ||
    trimStart().startsWith("*") || trimStart().startsWith("/*")

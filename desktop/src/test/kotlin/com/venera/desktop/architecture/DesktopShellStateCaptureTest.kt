package com.venera.desktop.architecture

import com.venera.desktop.gallery.ui.DesktopSourceTree
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/**
 * 守卫桌面主壳顶层 Composable 的状态捕获面与字节码参数槽位上限。
 *
 * JVM 方法描述符参数槽位上限为 255（`invokedynamic` 链接时还会追加调用点参数，
 * 实际安全上限更低）；超限时编译期不报错，运行开窗会直接抛出 `BootstrapMethodError`。
 */
class DesktopShellStateCaptureTest {

    private val stateBudget = 176
    private val bytecodeSlotBudget = 240

    @Test
    fun 壳捕获的状态不超过阈值() {
        val source = DesktopSourceTree.codeText(
            DesktopSourceTree.desktopSourceOf("VeneraDesktopLargeScreen.kt"),
        )
        val captured = SHELL_STATE_PATTERN.findAll(source).count()
        assertTrue(
            "壳文件里声明了 $captured 枚会被捕获的状态（阈值 $stateBudget）——" +
                "每一枚都可能占一颗方法描述符的槽，越线时编译不报、起窗抛 BootstrapMethodError。" +
                "把新加的壳级状态合成一枚 holder，或挪进用它的那个分支的局部 remember。",
            captured <= stateBudget,
        )
    }

    @Test
    fun 壳字节码方法参数槽位不超过安全上限() {
        val bytes = DesktopShellStateCaptureTest::class.java.classLoader
            .getResourceAsStream("com/venera/desktop/VeneraDesktopLargeScreenKt.class")
            ?.use { it.readBytes() }
        assertNotNull("未在测试 classpath 中找到 VeneraDesktopLargeScreenKt.class", bytes)
        val methods = readMethodSlotCounts(bytes!!)
        val worst = methods.maxByOrNull { it.second }
        assertNotNull("未解析到任何方法描述符", worst)
        assertTrue(
            "VeneraDesktopLargeScreenKt 最大方法参数槽位为 ${worst!!.second}（方法 ${worst.first}），" +
                "已超过安全上限 $bytecodeSlotBudget（JVM 硬上限 255，invokedynamic 链接会再加槽导致启动崩溃）。" +
                "请将壳顶层散装状态或局部对象收拢进状态容器类。",
            worst.second <= bytecodeSlotBudget,
        )
    }

    private fun readMethodSlotCounts(classBytes: ByteArray): List<Pair<String, Int>> {
        val dis = DataInputStream(ByteArrayInputStream(classBytes))
        dis.readInt() // magic
        dis.readUnsignedShort() // minor
        dis.readUnsignedShort() // major
        val cpCount = dis.readUnsignedShort()
        val utf8s = arrayOfNulls<String>(cpCount)
        var i = 1
        while (i < cpCount) {
            when (val tag = dis.readUnsignedByte()) {
                1 -> {
                    val len = dis.readUnsignedShort()
                    val buf = ByteArray(len)
                    dis.readFully(buf)
                    utf8s[i] = String(buf, Charsets.UTF_8)
                }
                3, 4 -> dis.skipBytes(4)
                5, 6 -> {
                    dis.skipBytes(8)
                    i++
                }
                7, 8, 16, 19, 20 -> dis.skipBytes(2)
                9, 10, 11, 12, 17, 18 -> dis.skipBytes(4)
                15 -> dis.skipBytes(3)
                else -> error("未知常量池 tag: $tag")
            }
            i++
        }
        dis.readUnsignedShort() // access_flags
        dis.readUnsignedShort() // this_class
        dis.readUnsignedShort() // super_class
        val ifCount = dis.readUnsignedShort()
        dis.skipBytes(ifCount * 2)
        val fieldsCount = dis.readUnsignedShort()
        repeat(fieldsCount) {
            dis.skipBytes(6)
            val ac = dis.readUnsignedShort()
            repeat(ac) {
                dis.skipBytes(2)
                val al = dis.readInt()
                dis.skipBytes(al)
            }
        }
        val methodsCount = dis.readUnsignedShort()
        val out = ArrayList<Pair<String, Int>>(methodsCount)
        repeat(methodsCount) {
            val acc = dis.readUnsignedShort()
            val nameIdx = dis.readUnsignedShort()
            val descIdx = dis.readUnsignedShort()
            val ac = dis.readUnsignedShort()
            repeat(ac) {
                dis.skipBytes(2)
                val al = dis.readInt()
                dis.skipBytes(al)
            }
            val name = utf8s[nameIdx].orEmpty()
            val desc = utf8s[descIdx].orEmpty()
            val isStatic = (acc and 0x0008) != 0
            val slots = countDescriptorSlots(desc) + if (isStatic) 0 else 1
            out += name to slots
        }
        return out
    }

    private fun countDescriptorSlots(desc: String): Int {
        val end = desc.indexOf(')')
        if (!desc.startsWith("(") || end < 0) return 0
        var slots = 0
        var idx = 1
        while (idx < end) {
            when (desc[idx]) {
                'J', 'D' -> {
                    slots += 2
                    idx++
                }
                'B', 'C', 'F', 'I', 'S', 'Z' -> {
                    slots += 1
                    idx++
                }
                'L' -> {
                    idx = desc.indexOf(';', idx) + 1
                    slots += 1
                }
                '[' -> {
                    while (desc[idx] == '[') idx++
                    if (desc[idx] == 'L') {
                        idx = desc.indexOf(';', idx) + 1
                    } else {
                        idx++
                    }
                    slots += 1
                }
                else -> idx++
            }
        }
        return slots
    }

    private companion object {
        val SHELL_STATE_PATTERN =
            Regex("""by\s+remember\s*\{\s*(mutableStateOf|mutableIntStateOf|mutableLongStateOf|mutableFloatStateOf)""")
    }
}

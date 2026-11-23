package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import com.venera.engineprobe.EngineAssets
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 用户改写那几颗源脚本的落盘层（源编辑）。
 *
 * ## 为什么必须有「一层」而不是直接改文件
 *
 * 桌面的 34 颗源走 `EngineAssets.readText` 的**包内优先**：正式产物（jpackage）里那一份在 jar 内，
 * 只读。于是"编辑源脚本"如果照对岸那样直接写 `assetDir/sources/<key>.js`，会出现最坏的一种绿 ——
 * 盘上真改了、屏上念「已保存」、仓库目录跑测试时也确实生效，而**装成包之后装载读的仍是包内那一份**，
 * 改动一字都不生效。所以写侧必须有独立的一层，读侧（`EngineSession.loadText`）先问它。
 *
 * ## 目录形状与包内同构
 *
 * `<数据目录>/source_overrides/sources/<key>.js` —— 相对路径与 classpath 的 `/sources/…` 一字相同，
 * 这样 `EngineAssets.overrideFile(dir, relative)` 与 `readText(assetDir, relative)` 是同一个取用形状，
 * 不用为这一层另写第二套路径拼接（两份口径一定会漂，本仓记过很多遍）。
 *
 * ## 三处必须同一口径（这一颗存在的理由）
 *
 * 装载（引擎）、屏上那颗显示名、手填 cookie 那份字段名单 —— 全都从**同一篇原文**算出来。
 * 只让装载吃覆盖层的话，用户改完脚本，源管理屏上仍念出厂那一个名字，
 * 那就是"看着是旧的那一颗、跑的已经是改过的"，比不改更难查。
 *
 * ## 落盘判据照 [com.venera.compose.data.platform.JsonKeyValueStore] 抄
 *
 * 先写 `<name>.js.tmp` 再整体替换（`ATOMIC_MOVE` 优先、不支持时退普通覆盖）。
 * 半截脚本是最难回头查的一种损坏：装载报「未找到 extends ComicSource 的类声明」，
 * 而真因是那次写盘被打断。读侧那档配套口径是**空内容不算命中**（见 [textOf]）。
 */
object DesktopSourceOverrides {

    /** 数据目录下的那一格（`PathProvider.subDir` 的段校验口径：不许空、不许 `..`、不许带分隔符）。 */
    const val DIR_NAME = "source_overrides"

    /** 与 classpath `/sources` 同名的那一层。 */
    private const val SOURCES_SUBDIR = "sources"

    /** 键名的允许面：随包那 34 颗全是小写字母/数字/下划线/连字符（`jm`、`copy_manga`、`manhuagui`…）。 */
    private val SAFE_KEY = Regex("[A-Za-z0-9_-]{1,64}")

    /** 覆盖层根目录（不存在时建出来；建不出来就抛，与 `DesktopPaths` 那条「不许退回 tmp」同口径）。 */
    fun dirFor(paths: PathProvider): File = paths.subDir(DIR_NAME)

    /**
     * 键名合不合规矩：这是**进文件系统路径前的那道清洗**（评审 F1 那一族）。
     *
     * 屏上那个键来自清单，正常永远合法；但 `--open=` 种子、以及将来任何"从外部递一颗键进来"的
     * 调用点都会把用户给的字符串直接送到这里，`..` 或 `a/b` 就成了写到数据目录外面去。
     */
    fun isSafeKey(key: String): Boolean = SAFE_KEY.matches(key)

    /** 相对路径（与包内 `/sources/<key>.js` 同形）。不合法的键在这里就拒。 */
    fun relativeOf(key: String): String {
        require(isSafeKey(key)) { "源键名不合法：$key" }
        return "$SOURCES_SUBDIR/$key.js"
    }

    /** 那一颗在盘上的绝对位置（读数与报错都要用它，不能只念相对路径）。 */
    fun fileFor(dir: File, key: String): File = File(dir, relativeOf(key))

    /**
     * 读一篇改写过的原文；**没有改写层就交回 null**（这是完全正常的默认情形，不抛也不打印）。
     *
     * ⚠️ 空内容不算命中：见 [EngineAssets.overrideFile] 那条 —— 半截写入读回来是空串，
     * 认了它装载就会报「未找到类声明」，把写盘被打断谎报成脚本本身有问题。
     */
    fun textOf(dir: File, key: String): String? {
        if (!isSafeKey(key)) return null
        return EngineAssets.overrideFile(dir, relativeOf(key))
    }

    /**
     * 落盘一篇改写。调用方**必须先过装载校验**（`EngineSession.checkLoadable`）再来这里写 ——
     * 这一颗只管"写得干净"，不管"这篇脚本装不装得起来"。
     *
     * @return 盘上那个文件的绝对路径（给读数用；写失败直接抛，不返回假路径）
     */
    fun writeText(dir: File, key: String, content: String): String {
        val target = fileFor(dir, key)
        val parent = target.parentFile
        if (!parent.mkdirs() && !parent.isDirectory) {
            throw IllegalStateException("建不出改写层目录 ${parent.path}")
        }
        val tmp = File(parent, "${target.name}.tmp")
        try {
            tmp.writeText(content)
            try {
                Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            tmp.delete()
            throw IllegalStateException("写改写层 $target 失败：${e.message}", e)
        }
        return target.absolutePath
    }

    /**
     * 撤掉一颗的改写（回到出厂那一篇）。
     *
     * 这一格不是装饰：源脚本改坏是最容易发生的事故，而覆盖层一旦存在就永远压着包内那份 ——
     * 没有撤的办法，用户只能手去数据目录里删文件。
     *
     * ⚠️ 交回的是**结果**而不是"我这次删掉了什么"：`true` = 撤完之后这一颗没有改写层了
     * （本来就没有也算达成 ⇒ 幂等，重复撤不报错）。`false` 只发生在两种真失败上 ——
     * 键名不合法、或删除被系统拒。选这一档而不是"没东西可撤就 false"，为的是让调用方
     * 那句「撤不掉」只说真事故，而不是说"另一处已经替我撤过了"（那正是用户要的结局）。
     */
    fun clear(dir: File, key: String): Boolean {
        if (!isSafeKey(key)) return false
        val file = fileFor(dir, key)
        return !file.isFile || file.delete()
    }

    /**
     * 哪几颗有改写层（屏上那一句「已改写 N 颗」与「这一颗能不能撤」的口径来源）。
     *
     * ⚠️ 判"有没有"必须与 [textOf] 同一口径 —— 也就是**空文件不算**。
     * 只判文件在不在的话，半截写入那一颗会被标成「已改写」而装载实际读的还是包内那一份：
     * 那一枚「撤回」把手会摆在一颗其实没改过的源上，而屏上那句「已改写」是假话。
     */
    fun overriddenKeys(dir: File): Set<String> {
        val sources = File(dir, SOURCES_SUBDIR)
        if (!sources.isDirectory) return emptySet()
        return runCatching {
            sources.listFiles { f -> f.isFile && f.extension == "js" }
                ?.mapNotNull { f ->
                    val key = f.nameWithoutExtension
                    // 走 textOf 而不是自己判文件在不在：那一颗就是"同一口径"的那颗件。
                    key.takeIf { textOf(dir, it) != null }
                }
                ?.toSet()
        }.getOrNull() ?: emptySet()
    }

    /** stdout 那一行：改写了哪几颗。空表也要念，否则"一颗都没改"与"这一层没读过"分不开。 */
    fun reading(keys: Set<String>): String =
        "D_源改写 颗数=${keys.size} 键=${keys.sorted().joinToString(",")}"
}

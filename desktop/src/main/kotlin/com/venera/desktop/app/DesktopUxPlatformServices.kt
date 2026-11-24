package com.venera.desktop.app

import com.venera.desktop.data.DesktopSourceContentPresets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.NoOpUxPlatformServices
import com.venera.compose.components.UxPlatformServices
import com.venera.compose.gallery.domain.GalleryGuard
import coil3.PlatformContext

/**
 * 桌面侧的 [UxPlatformServices]。
 *
 * ## 为什么必须提供它（验收发现 R3）
 *
 * `LocalUxPlatformServices` 是 `staticCompositionLocalOf { NoOpUxPlatformServices }`，
 * 而桌面此前**从未 provide** ⇒ 一直吃 `NoOp`，它的 `coverMaskStateOf(...)` **恒返回 `"VISIBLE"`**。
 *
 * 后果：共享零件 `RecommendCarouselItem` / `HistoryCardHome` 里那句
 * `maskState == "VISIBLE"` 恒真 ⇒ **所有封面不打码**，且 `coverSharedElement(allowFly = …)`
 * 会允许打码命中项也做共享元素飞行（安卓侧明确禁止这种行为）。
 *
 * 这是一条**内容安全**缺口，不是样式问题。
 *
 * ## 判据从哪来（不自己发明一套）
 *
 * 桌面画廊墙已在用 `GalleryGuard.maskStateFor`（`gallery/domain/GalleryGuard.kt`，
 * 零平台耦合，已在 `:desktop` 编译面）。它的三档字面值与漫画侧
 * `ContentGuardManager.coverMaskStateFor` **是同一套** `VISIBLE`/`BLURRED`/`HIDDEN`
 * —— 那颗文件的类注释第 13-14 行明写了这件事。
 *
 * 所以这里搬**判据形状**，不另立门户：黑名单 > 成人标记 > 模式档。
 *
 * ## 判据的边界（如实记，不掩盖）
 *
 * 源级预设表 [DesktopSourceContentPresets] 是判据第一道，且**解析失败时 fail-closed** ——
 * 表读不到 / 解析 0 条时 [DesktopSourceContentPresets.isGuarded] 一律返回 true，
 * 封面默认打码，并把成因打到 `D_警告` stdout。不再存在「空表静默放通」。
 *
 * 标签这一道是桌面自持的**明确标记词表**（[ADULT_TAGS]，覆盖 R18 / NSFW / 成人向
 * 常见写法与中文标记），不依赖安卓 `Context`、也不假装有另一套正则引擎。
 * 词表只收**明确**的成人标记，不收含糊分区名（`mature` 之类在部分站点是非成人分区）。
 * 比起编一张「看起来和安卓一样」的假词表，这里宁可把真实边界写清楚。
 */
internal class DesktopUxPlatformServices(
    /** 分级档当前值。桌面已有 `DesktopGalleryContentGuard.nsfwMaskMode` 这条流。 */
    private val maskMode: () -> String,
    /** 用户黑名单判定：命中就给 `HIDDEN`。桌面已有 `DesktopGalleryContentGuard.rules`。 */
    private val blockedByUser: (sourceKey: String, title: String, author: String, tags: List<String>) -> Boolean,
) : UxPlatformServices by NoOpUxPlatformServices {

    /**
     * 与 `ContentGuardManager.coverMaskStateFor(sourceKey, …)` 同一套三档字面值。
     *
     * 复用 `GalleryGuard` 的**判据形状**：黑名单 > 成人标记 > 模式（`GalleryGuard.kt:29-35`）。
     * `GalleryGuard.maskStateFor` 吃 `GalleryPost`，漫画侧只有字段，所以照它的
     * `when (mode)` 写一遍 —— 档位字面值与早退顺序逐字相同，不另立一套。
     */
    override fun coverMaskStateOf(
        sourceKey: String,
        title: String,
        author: String,
        tags: List<String>,
        comicId: String,
        description: String,
    ): String {
        if (blockedByUser(sourceKey, title, author, tags)) return GalleryGuard.HIDDEN
        if (!looksAdult(sourceKey, tags)) return GalleryGuard.VISIBLE
        return when (maskMode()) {
            "OFF" -> GalleryGuard.VISIBLE
            "HIDE" -> GalleryGuard.HIDDEN
            else -> GalleryGuard.BLURRED
        }
    }

    /**
     * 「这条要不要按成人向处理」。
     *
     * **第一道：源级预设表**（[DesktopSourceContentPresets]）。按 `sourceKey` 归一命中
     * `nsfw`/`mixed`；历史卡不带 tags 时全靠这一道。解析失败时它 fail-closed（默认打码 + 告警）。
     *
     * **第二道：标签里的明确成人标记**（[ADULT_TAGS]）。桌面自持词表，不依赖安卓 Context。
     * 两道任一命中即按成人向处理，缺一不可 —— 历史记录调 `coverMaskStateOf` 时**不传 tags**
     * （`HomeHomeWidgets.kt:165` 只传 sourceKey/title/author/comicId），只看 tags 会永远判不出。
     */
    private fun looksAdult(sourceKey: String, tags: List<String>): Boolean =
        DesktopSourceContentPresets.isGuarded(sourceKey) || tags.any { it.isAdultTag() }

    private fun String.isAdultTag(): Boolean = lowercase().trim() in ADULT_TAGS

    private companion object {
        /**
         * 桌面自持的明确成人标记词表。只收**确定**的成人写法，不收含糊词
         * （`mature` 之类在部分站点是非成人分区名）。中英同收，不臆造正则引擎。
         */
        val ADULT_TAGS = setOf(
            "r-18", "r18", "r18g", "r-18g", "adult", "adults", "nsfw", "explicit",
            "hentai", "smut", "lewd", "ecchi", "erotica", "porn", "xxx", "18+", "18禁",
            "成人", "成人向", "限制级", "工口", "里番", "黄色",
        )
    }

    // JVM 版 Coil 进程级上下文为静态单例，供详情页预览构造 ImageRequest 使用。
    override fun platformContext(): PlatformContext = PlatformContext.INSTANCE

    /**
     * 桌面剪贴板：AWT 系统剪贴板（`DesktopCopyLink.kt` 已在用同一形状）。
     * 无头环境（CI 截图跑）写不进去 ⇒ 吞下异常，与 `toast` 那族"桌面静默降级"同一口径。
     */
    override fun copyToClipboard(text: String) {
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard
                .setContents(java.awt.datatransfer.StringSelection(text), null)
        }
    }

    /** 桌面吐司：没有系统通知条，UI 侧要出声走的是屏上的 `note` 那一路；这里静默（接口既定的桌面语义）。 */
    override fun toast(message: String) = Unit
    override fun toastLong(message: String) = Unit
}

/** 把桌面实现装进作用域。**缺了它，共享零件里的内容守卫就恒 `VISIBLE`。** */
@Composable
internal fun ProvideDesktopUxPlatformServices(
    maskMode: () -> String,
    blockedByUser: (String, String, String, List<String>) -> Boolean,
    content: @Composable () -> Unit,
) {
    val services = remember(maskMode, blockedByUser) {
        DesktopUxPlatformServices(maskMode, blockedByUser)
    }
    CompositionLocalProvider(LocalUxPlatformServices provides services, content = content)
}

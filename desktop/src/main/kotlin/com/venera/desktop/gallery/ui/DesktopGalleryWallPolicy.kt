package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryColumnMode
import com.venera.compose.gallery.domain.GalleryPreviewQuality
import com.venera.compose.gallery.domain.GallerySettingsModel
import com.venera.compose.gallery.ui.GalleryWall
import com.venera.compose.gallery.ui.buildGalleryWall

internal object DesktopGalleryWallPolicy {

    /**
     * "AI 生成"这条屏蔽项在读数里显示成的规则原文。
     *
     * 阶段二把画廊那面墙的落屏判据（`buildGalleryWall` + 这四件纯模型）从 Android-only 的
     * `gallery/ui/GalleryScreen.kt` 抽进了共享叶子件 `gallery/ui/GalleryWallModel.kt`，桌面因此
     * 拿得到对岸那颗真常量，不再需要"文本重述"。这一枚只是把那颗共享 `AI_BLOCKED_RULE`
     * 转交出来，给桌面读数与 `DesktopWallGuardPolicyTest` ③/⑥ 当别名用 —— 判据本身在
     * [of] 里逐字走共享那颗 `buildGalleryWall`，桌面这一侧一份都不重写。
     */
    val AI_BLOCKED_RULE: String = com.venera.compose.gallery.ui.AI_BLOCKED_RULE

    /**
     * 算出一面墙。纯函数（不 require @Composable），所以能被单测直接取值 ——
     * 与 `desktopDomainRows` 同一条理由：能真跑的判据永远比能扫的判据可靠。
     *
     * 阶段二收敛：这套"遮罩 / 屏蔽规则 / AI 过滤"的落屏判据从前在桌面被**逐字抄了一遍**
     * （本文件旧注释自陈是对岸的"文本重述"）。现在 `of` 只把共享那颗 [buildGalleryWall] 的产物
     * 适配成桌面的视图类型 [DesktopWall]，**三档判定与计数一份都不在桌面重写** —— 漂的那一侧永远是
     * 桌面，所以判据必须只有一个出处（`DesktopWallGuardPolicyTest` ⑤ 反向钉这条）。
     *
     * @param maskMode 直接取 [GalleryContentGuard.nsfwMaskMode] 的**原值**，不在这里翻译
     * @param blockAi 取 `prefs.galleryBlockAi`
     * @param guard 命中用户规则的那一问走它，与 Android 调用点同一颗方法、同一对入参
     */
    fun of(
        posts: List<GalleryPost>,
        maskMode: String,
        blockAi: Boolean,
        guard: GalleryContentGuard,
    ): DesktopWall {
        // 判据逐字走共享那颗 `buildGalleryWall`（遮罩三档 / 命中规则先判 / AI 屏蔽都住在它里面，
        // 桌面这边只交 `blockedRuleOf` 那一问，与 Android 调用点同一颗方法、同一对入参）。
        val wall: GalleryWall = sharedWall(posts, maskMode, blockAi, guard)
        // 只把视图类型换名（`GalleryCard → DesktopWallCard`），计数与规则原文一字不改。
        return DesktopWall(
            cards = wall.cards.map { DesktopWallCard(it.post, it.masked) },
            blockedCount = wall.blockedCount,
            blockedRules = wall.blockedRules,
            hiddenByRating = wall.hiddenByRating,
        )
    }

    /**
     * 同一颗判据的**不换位**输出（阶段三 · 首屏共享批 T3）：共享页 `GalleryForYouPage` 吃的是
     * `GalleryWall`，桌面不再经 `DesktopWall` 这一道视图换名拿计数 —— 转交仍是同一处
     * `buildGalleryWall` 调用（`of` 与本页共用，起口只有一颗，`DesktopWallPolicyWiredTest`
     * / `DesktopWallGuardPolicyTest` ⑤ 的账不因多一个出口而松动）。
     */
    fun sharedWall(
        posts: List<GalleryPost>,
        maskMode: String,
        blockAi: Boolean,
        guard: GalleryContentGuard,
    ): GalleryWall = buildGalleryWall(posts, maskMode, blockAi) { post ->
        guard.blockedGalleryRule(author = post.author, tags = post.tagList)?.pattern
    }

    /**
     * **没接守卫那一档的同一颗墙**（阶段三 · 首屏共享批 T3）：`desktopWallGuardedCards` 在 guard
     * 缺位时走 `desktopWallCardsOf`（只去重不判档），而共享页只认 `GalleryWall` 这一种形状 ——
     * 这里用 `OFF` 档过同一颗 `buildGalleryWall`（HIDDEN/BLUR 都不触发 ⇒ 全量过、不挡不遮），
     * 判据出处仍只有一颗；日志照 `desktopWallGuardedCards` 同一条出声（"这一屏没走守卫"必须看得见）。
     */
    fun unguardedWall(posts: List<GalleryPost>): GalleryWall =
        buildGalleryWall(posts, maskMode = "OFF", blockAi = false) { null }
}

/** 一张卡 + 它要不要被盖住。`masked` 为真时**不去取图**（见 [DesktopGalleryWallPolicy] 类注释）。 */
internal data class DesktopWallCard(val post: GalleryPost, val masked: Boolean)

/**
 * 点卡这件事的**完整载荷**：哪一批卡 + 当前第几张。
 *
 * ## 为什么不止交一张卡
 *
 * P0 交的是单张（`DesktopWallCard`），那时浮层只能"看这一张"。P1 要翻页（← / → 与页码），
 * 而"第 3 / 40 张"里的 40 **只有墙自己知道** —— 它是这一屏当下真上屏的那一批
 * （守卫挡掉的不算、续页追加的要算）。把它交给应用根去自己拼，等于让另一处去猜"这批有多大"，
 * 而那一猜一旦与墙不一致，页码就成了假读数。
 *
 * ⚠️ 与 ③ 那颗 [DesktopGalleryBatch] 不是一颗件，也不该合成一颗：
 * 那颗管"取回来的这一批"（含被挡下的、含去重键与到底名单），这颗管"屏上这一批的第几张"。
 * 合成一颗的后果是页码开始把被挡下的也数进去。
 */
internal data class DesktopWallSelection(val cards: List<DesktopWallCard>, val index: Int) {
    val size: Int get() = cards.size
    val card: DesktopWallCard? get() = cards.getOrNull(index)
}

/**
 * 一面墙的产物。四个字段各自是一句不同的话，别合并：
 * `blockedCount` 是"你的规则挡下的"，`hiddenByRating` 是"分级设置收起的"，
 * 让前者替后者说话就是把"你屏蔽的"念成"你不想看的"。
 */
internal data class DesktopWall(
    val cards: List<DesktopWallCard> = emptyList(),
    val blockedCount: Int = 0,
    val blockedRules: List<String> = emptyList(),
    val hiddenByRating: Int = 0,
)

/**
 * hero 那一大位要不要取图。遮挡档给 `null` = **根本不去取**。
 *
 * 为什么单独一颗函数：hero 原来直接拿 `daily.merged.posts.firstOrNull()`，
 * **绕过了分级这一整关**，而且它取的是 `backdropUrl`（中档大图，比墙上缩略档更清晰）——
 * 也就是"该挡的内容在最大最清楚的那一格上摆着"。判据走同一颗，只是把"要不要发起这一次取图"
 * 提成可单测的形状，而不是让 `masked` 那一步埋在 composable 的 `if` 里。
 */
internal fun heroBackdropOf(card: DesktopWallCard?): String? =
    card?.takeIf { !it.masked }?.post?.backdropUrl

/**
 * 卡上**发起哪一次取图**。`null` = 根本不发起。
 *
 * 遮挡档排在清晰度档前面：用户选了"清楚一点"那一档，也不该让一张注定要盖住的图先下下来。
 * 取哪一档本身不在这里判 —— 那是共享那颗 [GallerySettingsModel.wallUrl]（它管着
 * "LARGE 取不到就退回 preview，不给空地址"与"视频条目永远只吃静帧"两条，桌面抄第二份就会漂）。
 */
internal fun tileImageRequest(post: GalleryPost, masked: Boolean, quality: GalleryPreviewQuality): String? =
    if (masked) null else GallerySettingsModel.wallUrl(post, quality)

/**
 * 墙上摆几列。
 *
 * 两半颗各有各的主人：**AUTO** 那一档要吃"内容区实测宽"，那只有 composable 侧量得出来，
 * 所以自适应那半颗归桌面（[DesktopGalleryMetrics.imageWallColumnCount]）；
 * **TWO / THREE** 是用户的显式覆盖，语义归共享那颗 [GallerySettingsModel.gridColumns]
 * （"窗口再宽也不许跟着动"），桌面这边只许转交、不许自己写 `-> 2`。
 */
internal fun desktopWallColumnCount(contentWidthDp: Float, mode: GalleryColumnMode): Int =
    GallerySettingsModel.gridColumns(mode, DesktopGalleryMetrics.imageWallColumnCount(contentWidthDp))

/**
 * UI-2 画廊主墙的列数：AUTO 使用沉浸式 272dp 最低列宽，随内容区变宽逐步加列；
 * TWO / THREE 仍是用户显式覆盖，窗口再宽也不跟着动。
 */
internal fun desktopImmersiveWallColumnCount(contentWidthDp: Float, mode: GalleryColumnMode): Int =
    GallerySettingsModel.gridColumns(mode, DesktopGalleryMetrics.immersiveWallColumnCount(contentWidthDp))

/** 角标那两个字。与 Android 侧 `GalleryScreen.kt` 那颗 `GalleryAiPill` 同串，由用例逐字回核。 */
const val AI_BADGE_TEXT = "AI"

/**
 * 这一张摆不摆「AI」角标。
 *
 * 两枚"没有信号就不摆"叠在一起：开关关着不摆，站方没打 AI 标签也不摆 ——
 * 判据只用 [GalleryAi.isAiMarked]（站方标签），**不靠画风推断**。
 * 它与 `blockAi` 是两枚独立开关：屏蔽关着时总得有个读数，所以默认档是"摆"。
 */
internal fun aiBadgeFor(post: GalleryPost, badgeOn: Boolean): Boolean =
    // 同 [DesktopGalleryWallPolicy] 里那一处：`isAiMarked` 现在也吃站方自己给的 AI 位
    // （`post.siteAiFlag`）。这一颗是**角标**的判据，与上面那颗屏蔽判据共用同一个真值源 ——
    // 分成两套会让"角标不亮但屏蔽生效"这种自相矛盾的状态出现。
    badgeOn && GalleryAi.isAiMarked(post.siteAiFlag, post.tagList)

/**
 * 墙上那两笔缺席读数。返回空表 = 这一墙没有任何东西被挡，调用方一句都不该念。
 *
 * 两句的文案是**逐字照 Android**：`gallery/ui/GalleryFeedReadout.kt:40` 的
 * `ratingHiddenFragment`（「N 张按「成人内容处理」收起」）与 `:36` 的
 * `blockedByRulesFragment`（「N 张命中屏蔽规则 x、y」）。
 * 同一件事在两头念成两个词，用户就会以为桌面多了一套规则 —— 屏蔽那句里的**笔数**
 * 曾经被这里漏掉（写成"命中屏蔽规则 x、y"），漏掉的就是"一条规则挡下三张"与
 * "三条规则各挡一张"这两种情形的差别。
 */
internal fun DesktopWall.guardFragments(): List<String> = buildList {
    if (hiddenByRating > 0) {
        add("$hiddenByRating 张按「成人内容处理」收起")
    }
    if (blockedRules.isNotEmpty()) {
        add("$blockedCount 张命中屏蔽规则 ${blockedRules.joinToString("、")}")
    }
}

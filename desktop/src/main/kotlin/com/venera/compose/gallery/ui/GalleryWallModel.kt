package com.venera.compose.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.domain.GalleryAi
import com.venera.compose.gallery.domain.GalleryGuard

/**
 * 画廊那面墙的**纯落屏模型 + 过滤判据**（阶段二 · 画廊二级页适配共享）。
 *
 * 这四件从前住在 `GalleryScreen.kt`（一颗 3600+ 行、满屏 Android 宿主的大件），所以桌面拿不到，
 * 只能在 `DesktopGalleryWallPolicy.of` 里**逐字抄一遍**这套"遮罩 / 屏蔽规则 / AI 过滤"判据 ——
 * 那颗文件自己的注释就承认这是对岸的"文本重述"，而"重述就是会漂的那种重复"。
 * 这一颗把它抽成**零 Android import 的叶子件**（只依赖同包与 `gallery/{data,domain}` 那几片已在桌面编译面上的件），
 * 于是两端共编、判据只有一份。`GalleryScreen.kt` 里其余的 composable（`buildGalleryWall` 的调用方、
 * `GalleryCardsGrid`、`GalleryDailyPage` 等）**留在原处** —— 它们才是真正 Android-only 的那半边。
 *
 * `internal` 而非 public：与同包其余件一致（`buildGalleryWall` 给书签二级页共用本就是 internal）。
 */

/** 被画廊那把 AI 开关收起时，页尾念出来的"规则名"。它不是一条文本规则，是一枚开关。 */
const val AI_BLOCKED_RULE = "AI 生成"

/** 一张卡片：条目 + 要不要打码。打码判定在派生列表时一次算好，卡片本身不做判断。 */
internal class GalleryCard(val post: GalleryPost, val masked: Boolean)

/** 一屏的落屏结果：摆出来的卡片 + 两类"少了"的成因，页尾那一行全靠它报数。 */
internal class GalleryWall(
    val cards: List<GalleryCard>,
    /** 命中用户屏蔽规则而被剔掉的张数。 */
    val blockedCount: Int,
    /** 命中了哪几条规则（去重后的规则原文，用于"是哪条规则挡的"）。 */
    val blockedRules: List<String>,
    /** 按「成人内容处理 = 彻底隐藏」收起的张数。 */
    val hiddenByRating: Int,
)

/**
 * 落屏清单 + 两类"少了"的成因 —— 日榜与搜索结果**共用这一把判据**。
 *
 * 遮罩判定在滚动里是"每项每次重组"的密度，所以按输入记忆；HIDDEN 直接不落进列表。
 * 两个"少了"的成因**分开记**：命中黑名单是用户自己的规则，按分级收起是模式选择 ——
 * 混成一个数字就没法告诉用户该动哪一个（真机反馈的 某一站 整站空白，
 * 当时页尾还写着「那站 40 张」的假读数，正是这次定位真机反馈的唯一线索被抹掉的原因）。
 *
 * @param blockedRuleOf 这条被哪条用户规则挡了（返回规则原文，给页面念出来）。
 *   判据由调用方给：画廊侧是 GalleryPorts.contentGuard.blockedGalleryRule，
 *   日榜与搜索必须**同一个调用**，否则会出现"墙上被挡、搜索结果里全裸"的分叉。
 * @param blockAi 画廊那一把**独立的** AI 屏蔽开关（批次 C2，用户拍板"画廊和漫画分开"）。
 *   命中的条目按"命中一条屏蔽规则"记账，规则名写死成 `AI 生成` —— 页尾与空态那两处读数
 *   本来就会把 `blockedRules` 念出来，这样"AI 挡了几张、为什么挡"是同一句真话，
 *   不需要再造第三个计数器（第三个计数器意味着第三套文案，那才是会漂的地方）。
 */
internal fun buildGalleryWall(
    posts: List<GalleryPost>,
    maskMode: String,
    blockAi: Boolean = false,
    blockedRuleOf: (GalleryPost) -> String?,
): GalleryWall {
    val kept = ArrayList<GalleryCard>(posts.size)
    val blockedRules = LinkedHashSet<String>()
    var blockedCount = 0
    var hiddenByRating = 0
    posts.forEach { post ->
        val blocked = blockedRuleOf(post)
            ?: if (blockAi && GalleryAi.isAiMarked(post.siteAiFlag, post.tagList)) AI_BLOCKED_RULE else null
        when (GalleryGuard.maskStateFor(post, maskMode, blockedByUser = blocked != null)) {
            GalleryGuard.HIDDEN -> if (blocked != null) {
                blockedCount++
                blockedRules += blocked
            } else hiddenByRating++
            GalleryGuard.BLURRED -> kept += GalleryCard(post, masked = true)
            else -> kept += GalleryCard(post, masked = false)
        }
    }
    return GalleryWall(kept, blockedCount, blockedRules.toList(), hiddenByRating)
}

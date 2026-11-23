package com.venera.desktop.gallery.ui

import com.venera.compose.gallery.data.GalleryPost
import com.venera.compose.gallery.data.GalleryReverseSearch
import com.venera.compose.gallery.data.SauceNaoHit
import com.venera.compose.gallery.data.SauceNaoPage
import kotlinx.coroutines.CancellationException

/**
 * 桌面 批 35 · 「找同一张图」这一族的判据面（哪一档地址发车、缺席分几档、命中怎么念）。
 *
 * ## 为什么抽成一颗跑得动的件而不是写在浮层里
 *
 * 浮层那颗件是 composable，单测进不去；而这一族最要紧的四件事（发不发、发哪一档、
 * 回来的算答案还是算故障、取消算不算结果）**全是能真跑的判定**。
 * 本仓一贯：文本锚点不够就把那一格抽成跑得动的纯函数（批 22 定的那条，批 30 第二次用到）。
 *
 * ## 三条口径都不是这一批定的
 *
 * - **发哪一档**：转交浮层已有的 [desktopDetailTiers] 与 [desktopDetailTierUrl]，不新造第二份"取哪一枚地址"的算式；
 *   被挡住的那张在那两颗件里本来就是**空档表**，所以这里不写"挡住了不发"的第二份判据，
 *   只写它那句人话（`masked` 那枚形参是同一条事实的入口，不是第二把尺子）。
 * - **有没有键**：吃契约上那枚 `sauceNaoHasKey`（批 34 才有的写口 + 流），
 *   而"没键就不发"是对岸那颗 ViewModel 的裁决，理由写在它的注释里（实测匿名请求过盾成功而重试仍 403）。
 * 本机这一路也量过：裸连与走 7890 代理**都回 403**，body 是 Cloudflare 那张 HTML。
 * - **成人内容那一档**：与那面墙同一把判据（[desktopAllowNsfw] 逐字回核对岸 `GalleryScreen.kt` 那一行），
 *   这里只**收**一个布尔，不新造第二个读取点。
 *
 * ## 「没有相似图」与「没搜成」与「摆不出」是三档（不许塌成一句）
 *
 * 对岸把前两档分开的理由写在它自己的注释里：合成一句会让人去换图而不是去配键、或反过来。
 * 桌面这边多出第三档 —— 站方**回了 N 条**而解析那一步一条都没留下（`hidden` 那一道与形状不对的那些）。
 * 那一档说成"站方说没有相似图"就是替站方编了一句它没说过的话。
 */

/** 浮层上那枚入口的名字。判据与页脚那句共用这一颗常量，不留第二份字面量。 */
internal const val DESKTOP_REVERSE_ENTRY_LABEL: String = "找同一张图"

/**
 * 「没配 key 就不发」那一句。批 35 起它是 [desktopReverseGate] 里的一行，批 77 下半 起
 * 「用本机的一张图找」那一格也要说同一句 —— 两处各写一遍就会跟着其中一处的措辞漂，
 * 而那是同一件事（发不出去是因为没有键，跟图从哪来无关）。
 */
internal val DESKTOP_REVERSE_NO_KEY_REASON: String =
    "先去填一枚免费 key：设置那一屏的「$DESKTOP_SAUCE_NAO_KEY_SECTION_TITLE」那一区"

/** 该不该发、发哪一枚地址。两档各说一句，不发的那档**从不**带着空串发车。 */
internal sealed interface DesktopReverseGate {
    /** 可以发车。[note] 只在**没拿到原图那一档**时非空 —— 屏上要念得出这一笔拿的是什么。 */
    data class Send(val url: String, val note: String?) : DesktopReverseGate

    /** 不发。三档（被分级挡 / 没填键 / 站方没给地址）各带各的那一句。 */
    data class Blocked(val why: String) : DesktopReverseGate
}

/** 跑完一轮的四种落点。**没发车**与**发出去而失败**是两档，前者根本没碰网络。 */
internal sealed interface DesktopReverseOutcome {
    data class NotSent(val why: String) : DesktopReverseOutcome
    data class Answered(val hits: List<SauceNaoHit>, val cap: Int, val note: String?) : DesktopReverseOutcome
    /** 站方正常回答"没有相似图"。[returned] 是站方自己报的回条数 —— 它非零时这一档改口念"摆不出"。 */
    data class NoSimilar(val message: String?, val returned: Int) : DesktopReverseOutcome
    /** 发出去而失败。**原话留着**，本机那一半由 [desktopReverseFailedReading] 补。 */
    data class Failed(val reason: String) : DesktopReverseOutcome
}

/**
 * 这一张能不能发、发哪一枚地址。
 *
 * 三档的**次序**就是判据：被挡住的那张连"有没有键"都不该问（它根本不发出去，
 * 而那一句会把用户的注意力带去配一枚用不上的键）；地址那一档排最后，
 * 因为前两种情形下"站方给没给地址"都与这次不发车无关。
 */
internal fun desktopReverseGate(post: GalleryPost, masked: Boolean, hasKey: Boolean): DesktopReverseGate = when {
    masked -> DesktopReverseGate.Blocked("这一张被分级挡住，本机不把它交给站方比对")
    !hasKey -> DesktopReverseGate.Blocked(DESKTOP_REVERSE_NO_KEY_REASON)
    else -> {
        // 取**这一张该摆的最高一档**：原图有就发原图（站方要整幅特征），没有才退一档，
        // 并把退的这一步念出来 —— 「发出去的是哪一枚地址」是可核对的事实，而相似度高低**没量过**，屏上不说。
        val tier = desktopDetailTiers(post, masked).lastOrNull()
        val url = tier?.let { desktopDetailTierUrl(post, it) }.orEmpty()
        when {
            url.isBlank() -> DesktopReverseGate.Blocked("这一张站方没给出可用的图片地址，没什么可交给站方比对的")
            tier == DesktopDetailTier.ORIGINAL -> DesktopReverseGate.Send(url, null)
            else -> DesktopReverseGate.Send(url, "这一张没有原图那一档，发出去的是站方给的下一档地址")
        }
    }
}

/**
 * 发一笔并落成四档之一。
 *
 * ⚠️ 取消**原样抛出去**：这一笔挂在浮层的组合协程上，换一张卡或关掉浮层都会取消它，
 * 把它翻成「这一张没搜成」等于替用户的一次打断编出一个结果。
 * 其余异常按原话上交（[desktopReverseFailedReading] 负责补本机那一半）。
 */
internal suspend fun desktopReverseRun(
    reverse: GalleryReverseSearch,
    post: GalleryPost,
    masked: Boolean,
    hasKey: Boolean,
    allowNsfw: Boolean,
): DesktopReverseOutcome {
    val gate = desktopReverseGate(post, masked, hasKey)
    if (gate is DesktopReverseGate.Blocked) return DesktopReverseOutcome.NotSent(gate.why)
    val send = gate as DesktopReverseGate.Send
    val page: SauceNaoPage = try {
        reverse.searchByUrl(send.url, allowNsfw)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        return DesktopReverseOutcome.Failed(failure.message ?: failure.javaClass.simpleName)
    }
    if (page.hits.isEmpty()) return DesktopReverseOutcome.NoSimilar(page.message, page.resultsReturned)
    return DesktopReverseOutcome.Answered(page.hits, reverse.maxResults, send.note)
}

/** 第一句：相似度（这一屏唯一比标题更该先看见的数字）+ 命中的库 + 视频帧的段号。 */
internal fun desktopReverseHitReading(hit: SauceNaoHit): String = buildString {
    append("${hit.similarity}%")
    if (hit.indexName.isNotEmpty()) append(" · ").append(hit.indexName)
    hit.part?.let { append(" · ").append(it) }
}

/** 发出去而失败那句的本机一半：原话带着，人机验证（403 一族）与本机没有过盾那一步都点出 —— 换图没用，先试重试。 */
internal fun desktopReverseFailedReading(reason: String): String =
    "$reason —— 本机这一路没有过盾那一步：先看是不是人机验证（403 一族），再按一次重试。"

/** 「没有相似图」与「摆不出」两句分开：站方回了 N 条而一条都没留下时，不许替站方编一句它没说的。 */
internal fun desktopReverseNoSimilarReading(message: String?, returned: Int): String =
    if (returned > 0) {
        "站方回了 $returned 条，但一条都摆不出（解析那一步挡掉了）${message?.let { "：$it" } ?: ""}"
    } else {
        "站方说没有相似图${message?.let { "：$it" } ?: ""}"
    }

/** 没有作者也要念一句而不是留空行 —— 空行读起来像"还没加载完"。 */
internal fun desktopReverseArtistReading(hit: SauceNaoHit): String =
    hit.artistName?.let { "作者：$it" } ?: "作者未知"

/**
 * 对上了我们那两站时的那一句；对不上交回 `null` = 一句都不摆。
 *
 * ⚠️ 后半句是本机的边界而不是装饰：Android 那边这一行可以点开自己的大图页（站方编号是它的寻址方式），
 * 而桌面**没有按站方编号取回那一张**的那一层（浮层只从墙上那一批里拿卡）。
 * 只念编号而不念这一句，用户会去找一枚不存在的按钮。
 */
internal fun desktopReverseSiteRefReading(hit: SauceNaoHit): String? = hit.siteRef?.let {
    "这一张对上了 ${it.site.displayName} 的 ${it.id} —— 本机没有按站方编号开大图页的那一层，只能开来源"
}

/** 封顶那句吃契约上那枚数（写死 20 就等于替站方决定它一次回多少条）。 */
internal fun desktopReverseCapReading(cap: Int, taken: Int): String =
    "SauceNAO 一次只回前 $cap 条，这一轮摆 $taken 条 —— 到底了"


package com.venera.desktop.gallery.ui

import java.time.Instant
import java.time.ZoneId

/**
 * 收件箱的来源轴。全部 = 图库段在前、pixiv 段在后，两段各排各的不混排
 * （pixiv 无发布时间，混排即假排序）。
 */
internal enum class ArtistFeedSource(val label: String) {
    ALL("全部"),
    GALLERY("图库"),
    PIXIV("pixiv"),
}

/**
 * 顶栏形式切换（规格 §2「时间|画师」）：时间 = 分区流，画师 = [ArtistFeedGroup] 行列表。
 */
internal enum class InboxViewMode(val label: String) {
    TIME("时间"),
    ARTIST("画师"),
}

/**
 * 收件箱网格的一个条目（纯数据，零 @Composable —— 装配判据由单测直接取值）。
 *
 * [SectionHead] 同时是段内「全部已读」的挂点（[unreadCount] 是这一段的未读数）；
 * [Divider] 是新作/旧作的分界线，文案两源不同口径（图库带日期、pixiv 不带）。
 */
internal sealed interface ArtistInboxEntry {

    /** 分区头：段名 + 未读数。 */
    data class SectionHead(
        val source: ArtistFeedSource,
        val label: String,
        val unreadCount: Int,
    ) : ArtistInboxEntry

    /** 段中分界线（新作在上、旧作在下）。 */
    data class Divider(val label: String) : ArtistInboxEntry

    /**
     * 一张卡。图库卡带 [authorOverride]（关注标签名本身 —— moebooru/gelbooru 的
     * author 字段是上传者不是画师）；pixiv 卡带 [hideSourceBadge]（设计稿无站徽标）。
     */
    data class Card(
        val model: DesktopWallCardModel,
        val authorOverride: String? = null,
        val hideSourceBadge: Boolean = false,
    ) : ArtistInboxEntry
}

/** 一张收件箱卡在进装配前的形态：渲染模型 + 该卡的两枚差异传参。 */
internal data class ArtistInboxCardItem(
    val model: DesktopWallCardModel,
    val authorOverride: String? = null,
    val hideSourceBadge: Boolean = false,
)

/**
 * 一个来源段的装配输入：段名文案、段内未读数、分好侧的新作/旧作卡。
 * 分侧判据（水位）在装配前由 [artistInboxSplitByWatermark] 或 feed 层完成。
 */
internal data class ArtistInboxSegment(
    val source: ArtistFeedSource,
    val label: String,
    val unreadCount: Int,
    val newCards: List<ArtistInboxCardItem>,
    val oldCards: List<ArtistInboxCardItem>,
)

/**
 * 顶栏未读计数文案。量词分源：全部=项、图库=张、pixiv=篇。
 */
internal fun inboxUnreadLabel(source: ArtistFeedSource, count: Int): String {
    val unit = when (source) {
        ArtistFeedSource.ALL -> "项"
        ArtistFeedSource.GALLERY -> "张"
        ArtistFeedSource.PIXIV -> "篇"
    }
    return "未读 $count $unit"
}

/** 分区头文案：`图库 12 张` / `pixiv 8 篇` 形。 */
internal fun inboxSectionHeadLabel(name: String, count: Int, unit: String): String = "$name $count $unit"

/**
 * 副栏同步时刻文案：`HH:mm 同步`。从未同步过（0）交 `尚未同步`；
 * 水位倒挂（站方时刻晚于本机 now）按从未同步处理，不摆一个未来的钟。
 */
internal fun inboxSyncLabel(lastSyncAt: Long, nowMillis: Long): String {
    if (lastSyncAt <= 0L || lastSyncAt > nowMillis) return "尚未同步"
    val time = Instant.ofEpochMilli(lastSyncAt).atZone(ZoneId.systemDefault())
    return "%02d:%02d 同步".format(time.hour, time.minute)
}

/**
 * 分界线文案。图库带日期（上次查看时刻，与落盘 [lastReadTimestamp] 同源不脱节）；
 * pixiv 只写「上次查看」（该源无发布时间，日期无从对齐）。从未查看过不带日期。
 */
internal fun inboxDividerLabel(showDate: Boolean, lastReadTimestamp: Long): String {
    if (!showDate || lastReadTimestamp <= 0L) return "上次查看"
    val day = Instant.ofEpochMilli(lastReadTimestamp).atZone(ZoneId.systemDefault())
    return "上次查看 · ${day.monthValue}月${day.dayOfMonth}日"
}

/**
 * 按落盘水位把卡分到分界线两侧：`sourcePost.id > watermark` = 新作（上方），
 * 其余（含无出处、id 缺档的夹具卡）落旧作侧 —— 分不准的不进新作区。
 */
internal fun artistInboxSplitByWatermark(
    cards: List<ArtistInboxCardItem>,
    watermark: Long,
): Pair<List<ArtistInboxCardItem>, List<ArtistInboxCardItem>> =
    cards.partition { (it.model.sourcePost?.id ?: 0L) > watermark }

/**
 * 按画师水位把 pixiv 卡分侧（规格 §4「每画师 maxIllustId」）：卡上画师名查
 * [watermarks]，`id > 该画师水位` = 新作；无水位档（含 0）整张落旧作侧 ——
 * 段内已读只推已读画师的水位，没读的另一位新作不被误推成旧作。
 */
internal fun artistInboxSplitByArtistWatermark(
    cards: List<ArtistInboxCardItem>,
    watermarks: Map<String, Long>,
): Pair<List<ArtistInboxCardItem>, List<ArtistInboxCardItem>> =
    cards.partition { (it.model.sourcePost?.id ?: 0L) > (watermarks[it.model.author] ?: 0L) }

/**
 * pixiv 每画师候选水位：键 = 画师名（与 [artistInboxSplitByArtistWatermark] 同键口径），
 * 值 = 该画师本轮已见最大 illust id；无 id 档的画师不进表。
 */
internal fun artistInboxWatermarkCandidates(cards: List<ArtistInboxCardItem>): Map<String, Long> =
    cards.groupBy { it.model.author }.mapNotNull { (artist, items) ->
        items.maxOfOrNull { it.model.sourcePost?.id ?: 0L }?.takeIf { it > 0 }?.let { artist to it }
    }.toMap()

/**
 * 装配一个来源视图的条目序列（纯函数）。
 *
 * - 全部：图库段头 → 图库新作 → 图库分界线 → 图库旧作 → pixiv 段头 → pixiv 新作 →
 *   pixiv 分界线 → pixiv 旧作。两段各恰一条分界线，段头在前pixiv 在后。
 * - 单源：只有分界线无段头。
 * - 空段（该源本轮无产物或未接线）整段省略。
 */
internal fun artistInboxEntries(
    source: ArtistFeedSource,
    gallery: ArtistInboxSegment?,
    pixiv: ArtistInboxSegment?,
    lastReadTimestamp: Long,
): List<ArtistInboxEntry> {
    val galleryEntries = gallery?.let { segmentEntries(it, showDate = true, lastReadTimestamp, withHead = source == ArtistFeedSource.ALL) }.orEmpty()
    val pixivEntries = pixiv?.let { segmentEntries(it, showDate = false, lastReadTimestamp, withHead = source == ArtistFeedSource.ALL) }.orEmpty()
    return when (source) {
        ArtistFeedSource.GALLERY -> galleryEntries
        ArtistFeedSource.PIXIV -> pixivEntries
        ArtistFeedSource.ALL -> galleryEntries + pixivEntries
    }
}

/** 单段装配：单源视图不摆段头（withHead=false），分界线与卡照常。 */
private fun segmentEntries(
    segment: ArtistInboxSegment,
    showDate: Boolean,
    lastReadTimestamp: Long,
    withHead: Boolean,
): List<ArtistInboxEntry> {
    val items = segment.newCards.map { ArtistInboxEntry.Card(it.model, it.authorOverride, it.hideSourceBadge) } +
        ArtistInboxEntry.Divider(inboxDividerLabel(showDate, lastReadTimestamp)) +
        segment.oldCards.map { ArtistInboxEntry.Card(it.model, it.authorOverride, it.hideSourceBadge) }
    return if (withHead) {
        listOf(ArtistInboxEntry.SectionHead(segment.source, segment.label, segment.unreadCount)) + items
    } else {
        items
    }
}

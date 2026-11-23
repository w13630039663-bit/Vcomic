package com.venera.desktop.detail

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.venera.compose.feature.ComicDetailCore
import com.venera.desktop.gallery.data.DesktopComicBulkOffline
import com.venera.desktop.gallery.ui.desktopComicReadProgress
import com.venera.desktop.gallery.ui.desktopComicResumeReading
import com.venera.compose.data.db.HistoryRecord

// 视图数据：连接 ComicDetailCore 与纯展示组件 DesktopComicDetailContent 的唯一边界。

/** 相关推荐条目（`ComicDetails.recommend` 的桌面形；[sourceKey] 空时屏已用当前源顶位）。 */
internal data class DesktopDetailRecommendItem(
    val id: String,
    val title: String,
    val cover: String,
    val sourceKey: String,
)

/** 章节行数据：[marked] = 库里读到的那一话；[offline] = 本机档位（null = 下载口没接）。 */
internal data class DesktopDetailChapter(
    val id: String,
    val title: String,
    val marked: Boolean,
    val offline: DesktopComicBulkOffline?,
    /** 占位话档（源站要付费/登录的那类）；批量弹窗据此念「下载整本/整本」。 */
    val placeholder: Boolean = false,
)

/** 评论区一条。 */
internal data class DesktopDetailComment(
    val id: String,
    val userName: String,
    val content: String,
    val time: String?,
    /** 源给的头像地址；空/给不出 ⇒ 占位圆格（不是所有源都报，逐源各安天命）。 */
    val avatar: String? = null,
)

/**
 * 那一枚主胶囊的数据。
 *
 * [hasHistory] = 库里有这一本的记录（继续阅读） vs 没记录（开始阅读）；
 * [chapterTitle]/[pageIndex] 是文案承诺的那两个数；[progress] ∈ 0..1（≤0 不画线）。
 */
internal data class DesktopReadCapsuleData(
    val hasHistory: Boolean,
    val chapterTitle: String,
    val pageIndex: Int,
    val progress: Float,
)

/**
 * 有章节源的胶囊：续读那颗算式（`desktopComicResumeReading`）交回 null（零章节）⇒ 整颗不摆。
 */
internal fun desktopDetailReadCapsule(record: HistoryRecord?, chapterIds: List<String>): DesktopReadCapsuleData? =
    desktopComicResumeReading(record, chapterIds)?.let { (_, page) ->
        DesktopReadCapsuleData(
            hasHistory = record != null,
            chapterTitle = record?.lastChapterTitle.orEmpty(),
            pageIndex = page,
            progress = desktopComicReadProgress(record?.lastChapterIndex ?: 0, chapterIds.size),
        )
    }

/** 无章节源（EH 图库整本一章）：整本即一章，进度按一章算。 */
internal fun desktopDetailGalleryCapsule(record: HistoryRecord?): DesktopReadCapsuleData =
    DesktopReadCapsuleData(
        hasHistory = record != null,
        chapterTitle = record?.lastChapterTitle.orEmpty(),
        pageIndex = record?.lastPageIndex ?: 0,
        progress = if (record != null) 1f else 0f,
    )

/**
 * 「上次读到这里」的行号 = **续读真会打开的那一行**：`desktopComicResumeReading` 把越界
 * 行号夹进表尾 —— 源站删了那一话时，标记指着按下真会打开的最后一行，而不是空气。
 * 纯判断（屏与判据共用一颗）；`record = null` ⇒ 一行都不挂。
 */
internal fun desktopDetailMarkedIndex(record: HistoryRecord?, chapterIds: List<String>): Int? =
    record?.let { desktopComicResumeReading(it, chapterIds)?.first }

/** 详情页整份视图数据。各可空格的口径：**null = 没报/没接**，不与 0 混说。 */
internal data class DesktopDetailViewData(
    val title: String,
    val cover: String,
    val author: String,
    val uploader: String,
    /** 源报的评分；≤0 = 没报不摆。 */
    val rating: Float,
    /** 自己刚提交的评分（0 = 还没评过）；>0 时压过 [rating] 展示，避免"点了没反应"。 */
    val userRating: Float = 0f,
    /** 源声明能打分 ⇒ 评分行摆可点星；false 时只读展示，不摆假把手。 */
    val canRate: Boolean = false,
    /** 源声明能点赞 ⇒ 读数没报也摆那枚心形钮（读数由点赞后的状态补）。 */
    val canLike: Boolean = false,
    val updateTime: String,
    /** 点赞档位与读数（读数 null = 源没报，整格不摆）。 */
    val liked: Boolean = false,
    val likesCount: Int?,
    val commentCount: Int?,
    /** 带命名空间的标签（组名 → 词表）。 */
    val tagGroups: List<Pair<String, List<String>>>,
    val description: String,
    val thumbnails: List<String>,
    /** 源 `onThumbnailLoad` 换算后的取图地址（EH 换镜像域那一档），键 = 原始 url。 */
    val resolvedThumbnails: Map<String, String> = emptyMap(),
    val recommend: List<DesktopDetailRecommendItem>,
    val chapters: List<DesktopDetailChapter>,
    val readCapsule: DesktopReadCapsuleData?,
    /** 收藏三态：null = 还没读回来（syncLocalFav 没回），true/false = 真档位。 */
    val favorited: Boolean?,
    val favoriteBusy: Boolean,
    val favoriteFolders: List<String>,
    val favoriteAddedFolders: Set<String>,
    /** 网络收藏（源账号侧）：夹表 id→名 与已收 id 集；空表 = 源没接/没登录/还在取。 */
    val networkFolders: Map<String, String> = emptyMap(),
    val networkAdded: Set<String> = emptySet(),
    /** 网络收藏这一路**通没通**（核的 openFavoritePanel 过了"源声明 favorites 且已登录"那道门）。 */
    val networkAvailable: Boolean = false,
    /**
     * **单夹源**的网络收藏档（哔咔/JM：favorites 有、loadFolders 没有 → 没有夹表可拉，
     * 只有一枚整本开关）。null = 不是单夹形（多夹源或没通）。
     */
    val networkSingle: Boolean? = null,
    /** 网络收藏的取夹进度/失败原话（弹窗里如实说，别让"没有网络段"和"还在取"同形）。 */
    val networkLoading: Boolean = false,
    val networkError: String? = null,
    val comments: List<DesktopDetailComment>,
    val commentsLoading: Boolean,
    val commentsError: String? = null,
    /** 还有下一页评论（源报了 maxPage 且未到末页，或还能继续翻）。 */
    val commentsHasMore: Boolean = false,
    /** 续页加载中：列表已有内容时底部按钮转「正在取…」，不冒充首屏空白。 */
    val commentsLoadingMore: Boolean = false,
    /** 已加载到的页（至少 1）与源报的总页数（null = 源没报）。 */
    val commentsPage: Int = 1,
    val commentsMaxPage: Int? = null,
    /** 源声明能不能发评论（发评论框只看这一位；评论区本体恒摆）。 */
    val canSendComment: Boolean,
    /** 评论正在发送中（按钮转「发送中」并禁用，防重复提交）。 */
    val isSendingComment: Boolean = false,
    /** 动作那一路（点赞/收藏）的失败原话（核写进 `DetailUiState.error`，屏只搬）。 */
    val actionError: String? = null,
    val website: String,
    val sourceKey: String,
)

/** 评论区最近一次发送的结果（屏内本地状态；发送中由 [DesktopDetailViewData.isSendingComment] 表达）。 */
internal enum class CommentSendState { IDLE, SENT, FAILED }

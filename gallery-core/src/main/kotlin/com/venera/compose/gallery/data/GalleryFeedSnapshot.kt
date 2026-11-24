package com.venera.compose.gallery.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 画廊日榜那一屏的**离线快照**（冷启动第一帧就摆它，不转圈等网络）。
 *
 * 阶段二 · 域 2 从 `GalleryFeedCache.kt`（那颗落盘件绑 `android.content.Context`，整颗在桌面排除
 * 名单里）抽进这颗零 Android import 的纯模型叶子 —— 共享面 `gallery/ui/GalleryDailyScreenBody.kt`
 * 的 `GalleryDailyCore` 要读写这份档的形状，而落盘本身两端各自接：安卓是 `GalleryFeedCache`，
 * 桌面是 `gallery/data/DesktopGalleryDailySnapshotStore`。与 `GalleryTextLineHeight`（§127）同一条摘行先例。
 *
 * @param savedEpochDay 这份快照写于哪一天（`LocalDate.toEpochDay`）。跨天判据要读它 ——
 *   日榜的内容按天换，"永远是缓存"就是"永远看不到今天的热门"，那是另一种坏。
 * @param posts 直接复用 [GalleryFavorite] 那一套可序列化形状与它两条既有转换
 *   （[GalleryPost.toFavorite] / [GalleryFavorite.toPost]）。**不另写第三套 schema**：
 *   这一屏要的字段与收藏要的字段是同一批，重复一份只会两份漂。
 */
@Serializable
data class GalleryFeedSnapshot(
    @SerialName("saved_epoch_day") val savedEpochDay: Long = 0L,
    @SerialName("date") val date: String = "",
    @SerialName("notice") val notice: String? = null,
    /**
     * 「这一轮哪几站没给内容、各是什么原因」，**按站一份**。
     *
     * 为什么不能只留 [notice] 那个拼好的串：提示块要按"未配账号 / 出错 / 本来没货"三档分开上屏，
     * 还要给未配账号那一档挂一枚「去配置账号」—— 而从一个 `站：原因 · 站：原因` 的
     * 显示串里反解出结构，只能靠关键词猜。**猜错不报错，只在屏上换一个颜色、少一个按钮**，
     * 正是最难查的那一类。所以结构在这一层就留住，与 `GalleryForYouSnapshot.failures` 同一形状。
     *
     * [notice] 保留不删：空态那句整段说明仍在用它，且旧档里只有它（读回来是空表，
     * 屏上少一个提示块、不会错）—— 这条兼容路径**只退化成少一块**，不会退化成假读数。
     */
    @SerialName("failures") val failures: List<GalleryForYouSiteRow> = emptyList(),
    @SerialName("posts") val posts: List<GalleryFavorite> = emptyList(),
)

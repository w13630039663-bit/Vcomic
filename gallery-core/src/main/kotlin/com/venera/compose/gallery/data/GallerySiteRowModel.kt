package com.venera.compose.gallery.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 零平台依赖的纯模型叶子，供 GalleryFeedSnapshot 与共享 UI 层共用。

/**
 * 猜你喜欢那一屏里"按站一份"的读数（快照用的行式形状）。
 *
 * 为什么存成 `List` 而不是直接存 `Map<GallerySite, String>`：`GalleryFeedSnapshot` 那批
 * 已有形状就是"每行自带站点键"（`GalleryFavorite.site`），跟着它走有两个好处 ——
 * 不用押 kotlinx 对枚举键 Map 的编码口径，而且站点认不出来时可以**逐行摘掉**而不是整档作废
 * （与 [GalleryFeedCache] 那头 `mapNotNull { fav.site?.let {...} }` 同一条口径）。
 */
@Serializable
data class GalleryForYouSiteRow(
    @SerialName("site") val site: GallerySite? = null,
    @SerialName("value") val value: String = "",
)

/** 快照里那几列"按站一份"的读数还原成 Map；站点认不出的行**逐行摘掉**，不整档作废。 */
fun List<GalleryForYouSiteRow>.bySite(): Map<GallerySite, String> =
    mapNotNull { row -> row.site?.let { it to row.value } }.toMap()

/** 反向：Map → 行式（写快照用）。 */
fun Map<GallerySite, String>.toSiteRows(): List<GalleryForYouSiteRow> =
    entries.map { GalleryForYouSiteRow(site = it.key, value = it.value) }

/** 页尾那个"各站累计张数"专用：值是个位数，塞进字符串列再转回来，不另开一套形状。 */
fun List<GalleryForYouSiteRow>.countBySite(): Map<GallerySite, Int> =
    bySite().mapNotNull { (site, value) -> value.toIntOrNull()?.let { site to it } }.toMap()

fun Map<GallerySite, Int>.toCountRows(): List<GalleryForYouSiteRow> =
    entries.map { GalleryForYouSiteRow(site = it.key, value = it.value.toString()) }

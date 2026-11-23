package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.gallery.data.GallerySite
import com.venera.compose.gallery.domain.GalleryRatingTone
import com.venera.compose.gallery.domain.ratingToneOf
import com.venera.compose.ui.tokens.VeneraTokens

/**
 * 筛选架的评分档（图库站）：全部 / 全年龄 / 敏感 / 限制级。
 *
 * 档位与 [GalleryRatingTone] 对齐（`ratingToneOf` 那颗映射的单源）：
 * 「全年龄」= Safe、「敏感」= Caution、「限制级」= Explicit；Unknown 只在「全部」出现 ——
 * 站方给了个我们不认识的值时**不猜**（GalleryRating.kt 头注那条红线），筛它进任何一档都是替站方下判断。
 */
internal enum class DesktopGalleryRatingFilter(val label: String) {
    All("全部"),
    Safe("全年龄"),
    Caution("敏感"),
    Explicit("限制级"),
}

/**
 * 评分 + 子源过滤后的**墙页副本**（图库站筛选架的落点）。
 *
 * - 过滤发生在**投影之后**（cards 与 wall.cards 同步滤，两本账不分叉）；
 * - `null` 页原样透传；「全部 + 无子源」原样返回同一实例（零过滤成本）；
 * - `blockedCount` 等守卫读数**照抄不重算** —— 那是守卫那一段的账，与这层浏览过滤无关。
 */
internal fun desktopFilteredWallPage(
    page: DesktopWallPage?,
    rating: DesktopGalleryRatingFilter,
    site: GallerySite?,
): DesktopWallPage? {
    if (page == null) return null
    val noRatingFilter = rating == DesktopGalleryRatingFilter.All
    val noSiteFilter = site == null
    if (noRatingFilter && noSiteFilter) return page
    val cards = page.cards.filter { card ->
        val post = card.sourcePost ?: return@filter noRatingFilter && noSiteFilter
        val ratingOk = when (rating) {
            DesktopGalleryRatingFilter.All -> true
            DesktopGalleryRatingFilter.Safe -> ratingToneOf(post.rating) == GalleryRatingTone.Safe
            DesktopGalleryRatingFilter.Caution -> ratingToneOf(post.rating) == GalleryRatingTone.Caution
            DesktopGalleryRatingFilter.Explicit -> ratingToneOf(post.rating) == GalleryRatingTone.Explicit
        }
        val siteOk = site == null || post.site == site
        ratingOk && siteOk
    }
    val wallCards = page.wall.cards.filter { wallCard ->
        cards.any { it.sourcePost?.uid == wallCard.post.uid }
    }
    return page.copy(
        cards = cards,
        wall = com.venera.compose.gallery.ui.GalleryWall(
            cards = wallCards,
            blockedCount = page.wall.blockedCount,
            blockedRules = page.wall.blockedRules,
            hiddenByRating = page.wall.hiddenByRating,
        ),
    )
}

/**
 * 筛选架（图库站 `.filter-row`）：评分 / 排序 / 子源 / 总数读数。
 *
 * - 评分与子源是**新增的真过滤**（上面的 [desktopFilteredWallPage]），排序吃既有
 *   `DesktopGallerySort` 三档（推荐 / 最新 / 最热，真取数腿）；
 * - 子源胶囊按 [GallerySite] 里真接入的 booru 站动态生成（PIXIV 不在此域，站点枚举里没有的
 *   站一个不编 —— 稿面上的 Danbooru / Konachan 站方腿今天不存在，摆上去就是假开关）；
 * - 行高密度切换随行对齐墙那一批再上（它在那边才有真消费者），这里不摆。
 */

// 顶栏稿：`DesktopGalleryFilterRow` / `FilterSegment` / `SourceChip` 三颗已删 ——
// 评分 / 排序 / 子源收进工具条三个 pop-up，布局 / 密度进视图菜单。
// 本文件只留枚举与浏览层过滤（`desktopFilteredWallPage` 仍是壳里那道真过滤）。

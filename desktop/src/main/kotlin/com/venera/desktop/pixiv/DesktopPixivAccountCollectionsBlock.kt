package com.venera.desktop.pixiv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.gallery.data.GalleryPixivCollections
import com.venera.compose.gallery.data.PixivCollectionCard
import com.venera.compose.gallery.data.PixivCollectionsSearchPage
import com.venera.desktop.gallery.ui.CollectionMotion
import com.venera.desktop.gallery.ui.CollectionMiniCoverCard
import com.venera.desktop.gallery.ui.GalleryTokens

/**
 * 账号页「珍藏册」块（挂「我的收藏」段顶部）。
 *
 * 数据只吃**登录腿** `myBookmarked(userId)` —— 语义 = 该账号**收藏的**珍藏册
 * （创建的珍藏册没有可用端点，实测候选全 404，不摆「我的珍藏册」假列表）。
 * 匿名 / 无会话 failure 整块退空态（「还没有收藏 · 先去发现页看看」），不摆错误态。
 * 「新建珍藏册」虚线卡：创建端点未找到，画位照稿、按下念「功能待接」。
 */
@Composable
internal fun DesktopPixivAccountCollectionsBlock(
    collections: GalleryPixivCollections?,
    userId: Long?,
    onOpenCollection: (PixivCollectionCard) -> Unit,
) {
    val colors = GalleryTokens.Color
    val ux = LocalUxPlatformServices.current
    val state = produceState<Result<PixivCollectionsSearchPage>?>(null, userId, collections) {
        value = null
        value = if (collections == null || userId == null) {
            Result.success(PixivCollectionsSearchPage(emptyList(), 0, null))
        } else {
            collections.myBookmarked(userId).fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(it) },
            )
        }
    }
    val loaded = state.value
    val cards = loaded?.getOrNull()?.cards
    val failure = loaded?.exceptionOrNull()
    val isAnonOrNoSession = failure?.message?.contains("登录") == true

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "珍藏册",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = colors.TextPrimary,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        when {
            loaded == null -> {
                Text(
                    text = "正在读取…",
                    fontSize = 13.sp,
                    color = colors.TextTertiary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
            failure != null && !isAnonOrNoSession -> {
                Text(
                    text = "珍藏册加载失败：${failure.message?.take(60) ?: "网络异常"}",
                    fontSize = 13.sp,
                    color = colors.TextTertiary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
            cards.isNullOrEmpty() -> {
                Text(
                    text = "还没有收藏 · 先去发现页看看",
                    fontSize = 13.sp,
                    color = colors.TextTertiary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                )
            }
            else -> {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "new") {
                        NewCollectionCard(onClick = { ux.toast("功能待接") })
                    }
                    cards.take(12).forEachIndexed { index, card ->
                        item(key = card.id) {
                            CollectionMiniCoverCard(
                                card = card,
                                onClick = { onOpenCollection(card) },
                                modifier = CollectionMotion.railStagger(index),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 「新建珍藏册」虚线卡（176×104，加号 + 文案；hover 描边转 accent）。 */
@Composable
private fun NewCollectionCard(onClick: () -> Unit) {
    val colors = GalleryTokens.Color
    val dashColor = colors.TextTertiary.copy(alpha = 0.5f)
    Column(
        modifier = Modifier
            .width(176.dp)
            .height(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f)),
                    ),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                )
            }
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = colors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "新建珍藏册",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.TextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

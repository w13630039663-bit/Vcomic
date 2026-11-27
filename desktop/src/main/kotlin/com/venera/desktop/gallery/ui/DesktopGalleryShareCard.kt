package com.venera.desktop.gallery.ui

import coil3.PlatformContext
import com.venera.compose.gallery.data.GallerySite
import com.venera.desktop.data.DesktopErrorBus
import com.venera.desktop.data.desktopFailureFrame
import com.venera.desktop.data.desktopFailureType
import com.venera.desktop.data.desktopOneLineMessage
import com.venera.desktop.gallery.data.desktopCoilBytes
import com.venera.desktop.ui.DesktopFeedbackBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 分享卡片的纯渲染输入模型，与桌面墙卡视图字段一一对应。
 */
internal data class DesktopShareCardSpec(
    val title: String,
    val artist: String,
    val sourceLabel: String,
    val coverUrl: String,
    val avatarUrl: String?,
    val aspect: Float,
    val pageCount: Int,
    val masked: Boolean,
    val isDark: Boolean,
)

/**
 * 从墙卡模型与外部覆写参数构造分享卡片绘制规格。
 */
internal fun desktopShareCardSpecOf(
    card: DesktopWallCardModel,
    titleOverride: String? = null,
    avatarUrl: String? = null,
    authorOverride: String? = null,
    hideSourceBadge: Boolean = false,
    isDark: Boolean = true,
): DesktopShareCardSpec {
    val post = card.sourcePost
    val coverUrl = post?.let {
        when {
            it.isVideo -> it.videoPosterUrl.ifBlank { it.previewUrl }
            it.site == GallerySite.PIXIV -> it.previewUrl.ifBlank { it.largeUrl }.ifBlank { it.fileUrl }
            else -> it.previewUrl.ifBlank { it.largeUrl }.ifBlank { it.fastUrl }
        }
    }?.takeIf { it.isNotBlank() } ?: card.url

    val title = titleOverride?.takeIf { it.isNotBlank() }
        ?: post?.let { "${it.site.displayName} #${it.id}" }
        ?: card.title.ifBlank { "未命名作品" }

    val isPixiv = post?.site == GallerySite.PIXIV ||
        card.url.contains("pixiv.net") || (post != null && post.source.contains("pixiv.net"))

    val artist = authorOverride?.takeIf { it.isNotBlank() } ?: run {
        if (isPixiv) {
            card.author.ifBlank { post?.author.orEmpty() }.trim().ifBlank { "未知画师" }
        } else {
            desktopExtractRealArtistTag(post, card.author.ifBlank { post?.author.orEmpty() })?.trim().orEmpty()
                .ifBlank { post?.author?.trim().orEmpty() }
        }
    }

    val sourceLabel = if (hideSourceBadge || post == null) "" else post.site.displayName.uppercase()
    return DesktopShareCardSpec(
        title = title,
        artist = artist,
        sourceLabel = sourceLabel,
        coverUrl = coverUrl,
        avatarUrl = avatarUrl?.takeIf { it.isNotBlank() },
        aspect = card.aspect,
        pageCount = post?.pageCount ?: 1,
        masked = card.masked,
        isDark = isDark,
    )
}

/**
 * 取图并在内存中合成高清分享卡片写入系统剪贴板，全程不依赖屏幕截图。
 */
internal suspend fun desktopShareGalleryCard(
    context: PlatformContext,
    card: DesktopWallCardModel,
    titleOverride: String? = null,
    avatarUrl: String? = null,
    authorOverride: String? = null,
    hideSourceBadge: Boolean = false,
    isDark: Boolean = true,
) {
    val spec = desktopShareCardSpecOf(
        card = card,
        titleOverride = titleOverride,
        avatarUrl = avatarUrl,
        authorOverride = authorOverride,
        hideSourceBadge = hideSourceBadge,
        isDark = isDark,
    )
    if (spec.coverUrl.isBlank()) {
        reportShareFailure("当前作品没有可用封面地址", null)
        return
    }
    if (!desktopCanCopyImage()) {
        reportShareFailure("当前运行环境不支持写入剪贴板", null)
        return
    }
    val (coverBytes, avatarBytes) = coroutineScope {
        val coverDeferred = async { desktopCoilBytes(context, spec.coverUrl) }
        val avatarDeferred = async {
            spec.avatarUrl?.let { desktopCoilBytes(context, it) }
        }
        coverDeferred.await() to avatarDeferred.await()
    }
    if (coverBytes == null || coverBytes.isEmpty()) {
        reportShareFailure("未能获取卡片封面数据", null)
        return
    }

    val renderResult = withContext(Dispatchers.Default) {
        runCatching { renderDesktopShareCardImage(spec, coverBytes, avatarBytes) }
    }
    val bitmapImage = renderResult.getOrElse { err ->
        reportShareFailure("生成卡片图失败：${err::class.java.simpleName}", err)
        return
    }

    val clipErr = desktopShareAwtImageToClipboard(bitmapImage)
    if (clipErr != null) {
        reportShareFailure(clipErr, null)
        return
    }
    println("D_分享卡片 尺寸=${bitmapImage.width}x${bitmapImage.height} 字节=${coverBytes.size}")
    DesktopFeedbackBus.showToast("卡片图已复制到剪贴板，去聊天工具粘贴即可")
}

private fun reportShareFailure(userMessage: String, error: Throwable?) {
    val reading = if (error == null) {
        "D_分享卡片 未成 因=$userMessage"
    } else {
        "D_分享卡片 未成 型=${desktopFailureType(error)} " +
            "消息=${desktopOneLineMessage(error)} 栈=${desktopFailureFrame(error) ?: "没有"}"
    }
    println(reading)
    DesktopErrorBus.report(
        category = "图库",
        headline = "分享卡片失败",
        humanReason = userMessage,
        detail = reading,
        dedupKey = "share_card:${userMessage.take(32)}",
    )
    DesktopFeedbackBus.showToast("分享失败：$userMessage")
}

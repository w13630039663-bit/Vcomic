package com.venera.desktop.gallery.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.PixivUgoiraClip
import com.venera.compose.gallery.data.UGOIRA_MIN_DELAY_MILLIS
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/**
 * 桌面侧 pixiv **动图（ugoira）**就地逐帧播放。
 *
 * ## 与安卓那颗的关系
 *
 * 逐字照对岸 [com.venera.compose.gallery.ui.PixivUgoiraPlayer] 的三段设计纪律（状态机 / 翻帧时钟 / 叠静帧），
 * 只换两处平台事：
 * - **解码层**：安卓走 `android.graphics.BitmapFactory.decodeFile(...).asImageBitmap()`，桌面没有那颗 ——
 *   走 `org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()`（桌面既有的位图互转惯例，
 *   与 `DesktopComicPane` / `DesktopRegionStage` 同源）；
 * - **暂停判据**：安卓靠 `LocalLifecycleOwner` 出 RESUMED 停表，compose-desktop 没有那层 ——
 *   这里只在**打码态**暂停，离开组合由 `LaunchedEffect` 取消自动停（不留后台空转）。
 *
 * pixiv 端口作**形参注入**（桌面共享屏不许 `GalleryPorts.of(context)` 那种 Context 现取，
 * 与 [[venera-architecture-business-ports]] 那条"内核只吃 BusinessPorts/形参"同一口径）。
 *
 * ## 一屏只渲这一颗，取不到帧就交回 null 由调用点退回静帧
 *
 * 不留"按了没反应"的假把手：帧表为空、下载/解包失败都如实交回 null（或错误原因），
 * 调用点据此仍画静帧底座（`GalleryPost` 的第一帧就是普通 `image_urls`，本就铺得出画面）。
 */
internal sealed interface DesktopUgoiraState {
    /** 正在取元信息 / 解包。 */
    data object Pending : DesktopUgoiraState

    /** 解好了，帧目录在手。 */
    data class Ready(val clip: PixivUgoiraClip) : DesktopUgoiraState

    /** 没法播（非 ugoira / 站方没给帧 / 下载解包失败）。[reason] 是给取证念的成因，屏上不铺。 */
    data class Failed(val reason: String) : DesktopUgoiraState
}

/** 订阅并加载解压好的 ugoira 帧表与目录（照对岸 `rememberUgoiraPlayback`，端口改形参）。 */
@Composable
internal fun rememberDesktopUgoiraPlayback(
    pixiv: GalleryPixivWorks?,
    illustId: Long,
    original: Boolean,
    retryKey: Int,
): DesktopUgoiraState {
    var state by remember(illustId, original, retryKey) { mutableStateOf<DesktopUgoiraState>(DesktopUgoiraState.Pending) }
    LaunchedEffect(illustId, original, retryKey, pixiv) {
        state = DesktopUgoiraState.Pending
        if (pixiv == null) {
            state = DesktopUgoiraState.Failed("这一端不接 pixiv")
            return@LaunchedEffect
        }
        pixiv.ugoiraClip(illustId, original)
            .onSuccess { clip ->
                state = if (clip != null) DesktopUgoiraState.Ready(clip) else DesktopUgoiraState.Failed("没有动图元信息")
            }
            .onFailure { e ->
                state = DesktopUgoiraState.Failed(e.message ?: "动图下载或解析失败")
            }
    }
    return state
}

/**
 * ugoira 逐帧渲染那一格（不含取数）：叠在静帧之上由调用点负责，这里只画当前帧。
 *
 * @param paused 打码态传入 true 停表（画面仍画第 0 帧，与对岸「masked 时暂停」一致）。
 */
@Composable
internal fun DesktopUgoiraFrameRenderer(
    clip: PixivUgoiraClip,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val frames = clip.ugoira.frames
    var currentFrameIndex by remember(clip) { mutableIntStateOf(0) }
    val decodedFrames = remember(clip) { mutableStateMapOf<Int, ImageBitmap>() }

    // 顺序后台预解码所有帧（首帧先行铺画面），确保循环不卡帧。
    LaunchedEffect(clip) {
        withContext(Dispatchers.IO) {
            val total = frames.size
            if (total == 0) return@withContext
            fun decode(i: Int): ImageBitmap? {
                val f = File(clip.dir, frames[i].file)
                if (!f.isFile) return null
                return runCatching {
                    SkiaImage.makeFromEncoded(f.readBytes()).use { it.toComposeImageBitmap() }
                }.getOrNull()
            }
            decode(0)?.let { decodedFrames[0] = it }
            for (i in 0 until total) {
                if (!isActive) break
                if (!decodedFrames.containsKey(i)) decode(i)?.let { decodedFrames[i] = it }
            }
        }
    }

    // 翻帧时钟：withFrameNanos 累积毫秒→到该帧 delay 回卷循环（逐字照对岸 :230-245）。
    LaunchedEffect(clip, paused) {
        if (paused || frames.size < 2) return@LaunchedEffect
        var lastNano = withFrameNanos { it }
        var elapsedMillis = 0L
        while (isActive) {
            val currentNano = withFrameNanos { it }
            val deltaMillis = ((currentNano - lastNano) / 1_000_000L).coerceAtLeast(0L)
            lastNano = currentNano
            elapsedMillis += deltaMillis
            val frameDelay = frames[currentFrameIndex].delay.coerceAtLeast(UGOIRA_MIN_DELAY_MILLIS)
            if (elapsedMillis >= frameDelay) {
                elapsedMillis %= frameDelay
                currentFrameIndex = (currentFrameIndex + 1) % frames.size
            }
        }
    }

    val currentBitmap = decodedFrames[currentFrameIndex] ?: decodedFrames[0]
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        // 一帧都没解出来：不铺空 Box，交回调用点的静帧底座继续显。
        Box(modifier = modifier)
    }
}

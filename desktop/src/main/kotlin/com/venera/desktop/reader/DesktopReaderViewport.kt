package com.venera.desktop.reader

import androidx.compose.ui.geometry.Offset

/** 阅读器缩放区间：允许缩到一半，用来通览跨页版面。 */
internal const val READER_MIN_ZOOM = 0.5f
internal const val READER_MAX_ZOOM = 4f

/**
 * 视口缩放与平移计算结果。
 *
 * @property zoom 目标缩放倍率（[READER_MIN_ZOOM]..[READER_MAX_ZOOM]）。
 * @property pan 目标平移偏移量（像素）。
 */
internal data class ReaderViewportTransform(
    val zoom: Float,
    val pan: Offset,
)

/**
 * 以鼠标光标为锚点计算滚轮缩放后的新倍率与平移偏移。
 *
 * @param currentZoom 当前缩放倍率。
 * @param currentPan 当前平移偏移。
 * @param scrollDeltaY 滚轮纵向增量（负值放大，正值缩小）。
 * @param cursorPosition 光标在画布内的坐标。
 * @param viewportWidth 画布宽度（像素）。
 * @param viewportHeight 画布高度（像素）。
 * @param minZoom 最小缩放倍率。
 * @param maxZoom 最大缩放倍率。
 */
internal fun computeCursorAnchoredZoom(
    currentZoom: Float,
    currentPan: Offset,
    scrollDeltaY: Float,
    cursorPosition: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
    minZoom: Float = READER_MIN_ZOOM,
    maxZoom: Float = READER_MAX_ZOOM,
): ReaderViewportTransform {
    if (scrollDeltaY == 0f || viewportWidth <= 0f || viewportHeight <= 0f) {
        return ReaderViewportTransform(currentZoom, currentPan)
    }
    val factor = if (scrollDeltaY < 0f) 1.15f else 0.87f
    val nextZoom = (currentZoom * factor).coerceIn(minZoom, maxZoom)
    if (nextZoom <= minZoom + 0.001f) {
        return ReaderViewportTransform(minZoom, Offset.Zero)
    }
    val cursorRelCenter = cursorPosition - Offset(viewportWidth / 2f, viewportHeight / 2f)
    val ratio = nextZoom / currentZoom.coerceAtLeast(0.01f)
    val rawPan = cursorRelCenter - (cursorRelCenter - currentPan) * ratio
    return ReaderViewportTransform(
        zoom = nextZoom,
        pan = clampPanOffset(rawPan, nextZoom, viewportWidth, viewportHeight),
    )
}

/**
 * 双击画布时在 `1f` 与 `targetZoom`（以光标为锚点）之间切换。
 *
 * @param currentZoom 当前缩放倍率。
 * @param tapPosition 双击坐标。
 * @param viewportWidth 画布宽度（像素）。
 * @param viewportHeight 画布高度（像素）。
 * @param targetZoom 放大目标倍率。
 */
internal fun toggleDoubleTapZoom(
    currentZoom: Float,
    tapPosition: Offset,
    viewportWidth: Float,
    viewportHeight: Float,
    targetZoom: Float = 2f,
): ReaderViewportTransform {
    // 放大态或缩小态双击都是"回到原位"，只有 1x 才放大 —— 否则从 50% 直接跳到 200% 太突兀。
    if (currentZoom > 1.05f || currentZoom < 0.99f || viewportWidth <= 0f || viewportHeight <= 0f) {
        return ReaderViewportTransform(1f, Offset.Zero)
    }
    val cursorRelCenter = tapPosition - Offset(viewportWidth / 2f, viewportHeight / 2f)
    val rawPan = -cursorRelCenter
    return ReaderViewportTransform(
        zoom = targetZoom,
        pan = clampPanOffset(rawPan, targetZoom, viewportWidth, viewportHeight),
    )
}

/**
 * 将平移偏移量钳位在当前缩放倍率允许的边界内，防止画面被拖出可视区。
 *
 * @param pan 待钳位的平移偏移。
 * @param zoom 当前缩放倍率。
 * @param viewportWidth 画布宽度（像素）。
 * @param viewportHeight 画布高度（像素）。
 */
internal fun clampPanOffset(
    pan: Offset,
    zoom: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Offset {
    if (zoom <= 1.01f) return Offset.Zero
    // 容器围绕中心缩放：缩放后比视口多出的那一半，就是可平移的极限。
    val maxPanX = viewportWidth * (zoom - 1f) / 2f
    val maxPanY = viewportHeight * (zoom - 1f) / 2f
    return Offset(
        x = pan.x.coerceIn(-maxPanX, maxPanX),
        y = pan.y.coerceIn(-maxPanY, maxPanY),
    )
}

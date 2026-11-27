package com.venera.desktop.gallery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.venera.compose.gallery.data.GalleryContentGuard
import com.venera.compose.gallery.data.GalleryPorts
import com.venera.compose.gallery.domain.GalleryRanking
import com.venera.compose.gallery.domain.GallerySearchSource
import com.venera.compose.gallery.domain.PixivSearchFilter
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.ui.constFlow
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 画廊搜索分页控制器对外暴露的只读视图与操作回调。
 */
internal class DesktopGallerySearchPagerController(
    val mode: DesktopSearchPaginationMode,
    val onPickMode: (DesktopSearchPaginationMode) -> Unit,
    val currentPage: Int,
    val canNextPage: Boolean,
    val pageLoading: Boolean,
    val onPrevPage: () -> Unit,
    val onNextPage: () -> Unit,
    val onJumpPage: (Int) -> Unit,
)

/**
 * 画廊搜索分页协调器：管理模式读写、同搜索内页缓存、按页拉取与切模式重置。
 */
@Composable
internal fun rememberDesktopGallerySearchPagerController(
    ports: GalleryPorts,
    scope: CoroutineScope,
    galleryPrefs: DesktopGalleryPreferences?,
    maskModeOf: () -> String,
    wallGuardOf: () -> GalleryContentGuard?,
    wallBlockAiOf: () -> Boolean,
    gallerySearchMode: Int,
    gallerySearchQuery: String,
    gallerySearchSource: Int,
    gallerySearchSourceOptions: List<GallerySearchSource>,
    gallerySearchRanking: GalleryRanking,
    gallerySearchRankingLanding: GalleryRanking,
    gallerySearchRankingAnchor: LocalDate?,
    gallerySearchToday: LocalDate,
    gallerySearchR18: Boolean,
    gallerySearchPageState: MutableState<DesktopWallPage?>,
    gallerySearchTickState: MutableIntState,
    pixivSearchTerm: String,
    pixivSearchFilter: PixivSearchFilter,
    pixivSearchKind: Int,
    pixivSearchPageState: MutableState<DesktopWallPage?>,
    pixivSearchTickState: MutableIntState,
): DesktopGallerySearchPagerController {
    val fallbackState = remember { mutableStateOf(DesktopSearchPaginationMode.INFINITE) }
    val prefMode by (galleryPrefs?.searchPaginationMode ?: constFlow(fallbackState.value)).collectAsState()
    val effectiveMode = if (galleryPrefs != null) prefMode else fallbackState.value

    val booruCache = remember { DesktopSearchPageCache() }
    val pixivCache = remember { DesktopSearchPageCache() }
    var booruPageNo by remember { mutableIntStateOf(1) }
    var pixivPageNo by remember { mutableIntStateOf(1) }
    var pageLoading by remember { mutableStateOf(false) }

    val booruKey = remember(
        gallerySearchQuery, gallerySearchSource, gallerySearchRankingLanding,
        gallerySearchRankingAnchor, gallerySearchR18,
    ) {
        "${gallerySearchQuery.trim()}|$gallerySearchSource|$gallerySearchRankingLanding|$gallerySearchRankingAnchor|$gallerySearchR18"
    }
    val pixivKey = remember(pixivSearchTerm, pixivSearchFilter, pixivSearchKind) {
        "${pixivSearchTerm.trim()}|$pixivSearchFilter|$pixivSearchKind"
    }

    LaunchedEffect(booruKey) {
        booruCache.bindContext(booruKey)
        booruPageNo = 1
    }
    LaunchedEffect(pixivKey) {
        pixivCache.bindContext(pixivKey)
        pixivPageNo = 1
    }

    val gallerySearchPage by gallerySearchPageState
    val pixivSearchPage by pixivSearchPageState
    LaunchedEffect(gallerySearchPage, booruKey) {
        val loaded = gallerySearchPage
        if (loaded != null && loaded.batch.page <= 1 && booruPageNo == 1) booruCache.put(1, loaded)
    }
    LaunchedEffect(pixivSearchPage, pixivKey) {
        val loaded = pixivSearchPage
        if (loaded != null && loaded.batch.page <= 1 && pixivPageNo == 1) pixivCache.put(1, loaded)
    }

    var lastMode by remember { mutableStateOf(effectiveMode) }
    LaunchedEffect(effectiveMode) {
        if (lastMode != effectiveMode) {
            lastMode = effectiveMode
            booruCache.clear()
            pixivCache.clear()
            booruPageNo = 1
            pixivPageNo = 1
            if (gallerySearchMode == 1) {
                if (pixivSearchTerm.isNotBlank()) pixivSearchTickState.intValue++
            } else {
                if (gallerySearchQuery.isNotBlank()) gallerySearchTickState.intValue++
            }
        }
    }

    fun jumpToPage(target: Int) {
        val safePage = target.coerceAtLeast(1)
        if (pageLoading) return
        if (gallerySearchMode == 1) {
            val word = pixivSearchTerm.trim()
            if (word.isEmpty()) return
            pixivCache.bindContext(pixivKey)
            pixivCache.get(safePage)?.let { cached ->
                pixivSearchPageState.value = cached
                pixivPageNo = safePage
                return
            }
            pageLoading = true
            scope.launch {
                val loaded = runCatching {
                    desktopPixivSearchSpecificPage(
                        ports = ports,
                        word = word,
                        filter = pixivSearchFilter,
                        page = safePage,
                        guard = wallGuardOf(),
                        maskMode = maskModeOf(),
                        blockAi = wallBlockAiOf(),
                    )
                }.getOrNull()
                pageLoading = false
                if (loaded != null) {
                    pixivCache.put(safePage, loaded)
                    pixivSearchPageState.value = loaded
                    pixivPageNo = safePage
                }
            }
        } else {
            val word = gallerySearchQuery.trim()
            if (word.isEmpty()) return
            booruCache.bindContext(booruKey)
            booruCache.get(safePage)?.let { cached ->
                gallerySearchPageState.value = cached
                booruPageNo = safePage
                return
            }
            val source = gallerySearchSourceOptions[gallerySearchSource.coerceIn(0, gallerySearchSourceOptions.lastIndex)]
            val configured = desktopGelbooruConfigured(ports)
            val legs = source.availableLegs(configured)
            val missing = source.missingLegs(configured)
            val queries = legs.associateWith { site ->
                val suffix = desktopRankingQuery(
                    site,
                    desktopRankingLanding(legs, gallerySearchRanking),
                    gallerySearchRankingAnchor,
                    gallerySearchToday,
                )
                val ratingSuffix = desktopSearchRatingSuffix(site, gallerySearchR18)
                listOf(word, suffix, ratingSuffix).filter { it.isNotBlank() }.joinToString(" ")
            }
            pageLoading = true
            scope.launch {
                val loaded = runCatching {
                    desktopWallSearchSpecificPage(
                        ports = ports,
                        queries = queries,
                        silent = missing,
                        page = safePage,
                        guard = wallGuardOf(),
                        maskMode = maskModeOf(),
                        blockAi = wallBlockAiOf(),
                    )
                }.getOrNull()
                pageLoading = false
                if (loaded != null) {
                    booruCache.put(safePage, loaded)
                    gallerySearchPageState.value = loaded
                    booruPageNo = safePage
                }
            }
        }
    }

    val currentPage = if (gallerySearchMode == 1) pixivPageNo else booruPageNo
    val activeWall = if (gallerySearchMode == 1) pixivSearchPage else gallerySearchPage
    val canNextPage = activeWall != null && !activeWall.batch.atEnd

    return DesktopGallerySearchPagerController(
        mode = effectiveMode,
        onPickMode = { picked ->
            if (galleryPrefs != null) {
                galleryPrefs.setSearchPaginationMode(picked)
            } else {
                fallbackState.value = picked
            }
        },
        currentPage = currentPage,
        canNextPage = canNextPage,
        pageLoading = pageLoading,
        onPrevPage = { if (currentPage > 1) jumpToPage(currentPage - 1) },
        onNextPage = { if (canNextPage) jumpToPage(currentPage + 1) },
        onJumpPage = { target -> jumpToPage(target) },
    )
}

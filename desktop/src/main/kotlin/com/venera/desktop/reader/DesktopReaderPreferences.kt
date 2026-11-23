package com.venera.desktop.reader

import com.google.gson.Gson
import com.venera.compose.data.platform.JsonKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一套滤镜预设：名字 + 五项图像参数。
 *
 * 只存参数不存像素，套用时直接写回偏好那几格，预览与「重置」走同一条路。
 */
internal data class ReaderFilterPreset(
    val name: String = "",
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val filterHue: Float = 0f,
    val filterSepia: Float = 0f,
    val filterInvert: Boolean = false,
    val filterNegative: Boolean = false,
)

/**
 * 阅读器持久化偏好快照。
 *
 * @property fitIndex 画面适配档位索引（0=适应屏幕，1=适应宽度，2=原始）。
 * @property preloadCount 向后预加载页数（2 / 4 / 8）。
 * @property autoPlaySecs 自动翻页间隔（秒）。
 * @property arrowKeysOn 是否启用方向键翻页。
 * @property coverSinglePage 双页模式下封面（第 1 页）是否独占一页。
 * @property smoothTurning 翻页时是否走过渡动画（关 = 立刻切页）。
 * @property turnEffect 翻页动画/浏览模式档位：2=导览视图(分格聚焦)，3=淡出，4=翻页(3D硬翻)。
 * @property turnPerspective 3D 翻页透视度（5..50）。
 * @property guidedViewStyle 导览视图模式（0=聚焦，1=隐藏，2=沉浸式）。
 * @property guidedViewPadding 导览视图边距（0..200px）。
 * @property guidedViewMaxZoom 导览视图最大缩放倍数（1.5..4.0）。
 * @property guidedViewMaskStyle 导览视图隐藏遮罩效果（0=白色，1=黑色，2=模糊）。
 * @property guidedViewShowFullFirst 在首画板前先显示整页。
 * @property guidedViewShowFullLast 在尾画板后显示整页。
 * @property rotationIndex 页面旋转档：0=0°，1=90°，2=180°，3=270°。
 * @property pageSoundOn 翻页音效开关。
 * @property tapZonePercent 左右点击翻页区宽度（占内容宽百分比）。
 * @property brightness 亮度倍率（`0f..4f`，1=100%）。
 * @property contrast 对比度倍率（`0f..4f`，1=100%）。
 * @property saturation 饱和度倍率（`0f..4f`，1=100%）。
 * @property filterQualityIndex 缩放插值质量档位（0=平滑双线性，1=高质量双三次，2=像素近邻）。
 * @property dragTurnOn 是否支持鼠标边缘/书角拖拽跟随翻页。
 * @property turnAnimSeconds 翻页/补间动画统一时长（秒，0.1..4.0）。
 * @property shadowOn 双页中缝阴影效果开关。
 * @property shadowSize 中缝阴影宽度（占页宽百分比 0..100）。
 * @property shadowOpacity 中缝阴影黑度（百分比 0..100）。
 * @property shadowDisplace 中缝阴影偏移（百分比，正=往左页侧）。
 * @property alignNextHorizontal 双页分组时与下一张横图水平对齐（奇数距离插空白页）。
 * @property blankPage 双页分组时在首页前插空白页，使第 1 页落右侧。
 * @property noEnlarge 缩放上限钳原始尺寸（zoomTarget 上限 = 1）。
 * @property singleInScroll 垂直连续档一屏一页。
 * @property rotateH 横图旋转档：0=0°，1=90°左，2=90°右，3=180°。
 * @property marginH 水平边距（dp，paged 档每页外边距）。
 * @property marginV 垂直边距（dp，连续档页间隙）。
 * @property showPageNumber 页码文本显隐。
 * @property reverseReading 阅读方向倒序（横向翻页档取反，不改变档位本身）。
 * @property soundVolume 翻页音效音量（0..100）。
 * @property filterHue 色相旋转角度（0..360）。
 * @property filterSepia 色温（暖褐）强度（0..100）。
 * @property filterInvert 黑白（灰度）开关。
 * @property filterNegative 负片开关。
 * @property aiArtifact AI 痕迹去除开关。
 * @property aiArtifactModel 痕迹去除强度档（0=轻，1=中，2=重）。
 * @property aiArtifactTone 痕迹去除后保持色调平缓。
 * @property aiArtifactSize 痕迹尺寸基准（占页宽百分比 1..10）。
 * @property aiDescreen AI 去网纹开关。
 * @property aiDescreenModel 去网纹档（0=标准，1=硬核）。
 * @property aiDescreenKeep 保持大网点。
 * @property aiDescreenSize 网点尺寸基准（占页宽百分比 1..10）。
 * @property aiUpscale AI 增强开关。
 * @property aiUpscaleModel 增强档（0=快速，1=质量）。
 * @property aiUpscaleMaxmp 最大输入分辨率（Mpx）。
 * @property aiUpscaleAuto 自动按内存预算选比例。
 * @property aiUpscaleScale 上采样倍数（2 或 4）。
 * @property magnifierOn 悬浮放大镜是否常驻开启。
 * @property magnifierScale 悬浮放大镜放大倍率（1.5f..4.0f）。
 * @property isBookCustomized 当前漫画是否开启了独立自定义设置。
 */
internal data class DesktopReaderPrefsState(
    val fitIndex: Int = 0,
    val preloadCount: Int = 4,
    val autoPlaySecs: Int = 4,
    val arrowKeysOn: Boolean = true,
    val coverSinglePage: Boolean = true,
    val smoothTurning: Boolean = true,
    val turnEffect: Int = 4,
    val turnPerspective: Int = 20,
    val guidedViewStyle: Int = 0,
    val guidedViewPadding: Int = 100,
    val guidedViewMaxZoom: Float = 2.0f,
    val guidedViewMaskStyle: Int = 1,
    val guidedViewShowFullFirst: Boolean = false,
    val guidedViewShowFullLast: Boolean = false,
    val rotationIndex: Int = 0,
    val pageSoundOn: Boolean = true,
    val tapZonePercent: Int = 15,
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val filterQualityIndex: Int = 1,
    val dragTurnOn: Boolean = true,
    val turnAnimSeconds: Float = 0.30f,
    val shadowOn: Boolean = true,
    val shadowSize: Float = 7f,
    val shadowOpacity: Float = 50f,
    val shadowDisplace: Float = 5f,
    val alignNextHorizontal: Boolean = true,
    val blankPage: Boolean = false,
    val keepWideSingle: Boolean = true,
    val noEnlarge: Boolean = true,
    val singleInScroll: Boolean = false,
    val rotateH: Int = 0,
    val marginH: Int = 16,
    val marginV: Int = 16,
    val showPageNumber: Boolean = true,
    val reverseReading: Boolean = false,
    val doublePageOn: Boolean = true,
    val soundVolume: Int = 80,
    val filterHue: Float = 0f,
    val filterSepia: Float = 0f,
    val filterInvert: Boolean = false,
    val filterNegative: Boolean = false,
    val aiArtifact: Boolean = false,
    val aiArtifactModel: Int = 1,
    val aiArtifactTone: Boolean = true,
    val aiArtifactSize: Float = 3f,
    val aiDescreen: Boolean = false,
    val aiDescreenModel: Int = 1,
    val aiDescreenKeep: Boolean = true,
    val aiDescreenSize: Float = 3f,
    val aiUpscale: Boolean = false,
    val aiUpscaleModel: Int = 0,
    val aiUpscaleMaxmp: Float = 2f,
    val aiUpscaleAuto: Boolean = true,
    val aiUpscaleScale: Int = 4,
    val stripMaxWidthPercent: Int = 100,
    val stripPageGap: Int = 0,
    val magnifierOn: Boolean = false,
    val magnifierScale: Float = 2.0f,
    val isBookCustomized: Boolean = false,
)

/**
 * 阅读器设置与页内书签持久化管理器。
 */
internal object DesktopReaderPreferences {
    private val _state = MutableStateFlow(DesktopReaderPrefsState())
    val state: StateFlow<DesktopReaderPrefsState> = _state.asStateFlow()

    private val _bookmarksVersion = MutableStateFlow(0)
    val bookmarksVersion: StateFlow<Int> = _bookmarksVersion.asStateFlow()

    private val _filterPresets = MutableStateFlow<List<ReaderFilterPreset>>(emptyList())
    /** 已保存的滤镜预设；写入即持久化，UI 观察它刷新 chip 列表。 */
    val filterPresets: StateFlow<List<ReaderFilterPreset>> = _filterPresets.asStateFlow()

    private val gson = Gson()

    private var store: JsonKeyValueStore? = null
    private var activeBookKey: String? = null

    /**
     * 绑定持久化存储并恢复已保存的阅读器偏好。
     *
     * @param kvStore 键值存储实例。
     */
    fun bind(kvStore: JsonKeyValueStore) {
        store = kvStore
        _state.value = loadState(kvStore, activeBookKey)
        _filterPresets.value = readFilterPresets(kvStore)
    }

    /**
     * 绑定当前正在阅读的漫画；若该漫画已单独设置则载入覆盖配置，否则继承全局偏好。
     *
     * @param bookKey 漫画唯一标识（如 "sourceKey:comicId"），为 null 时切回全局。
     */
    fun bindBook(bookKey: String?) {
        activeBookKey = bookKey
        val kv = store ?: return
        _state.value = loadState(kv, bookKey)
    }

    /**
     * 切换当前漫画是否脱离全局偏好、采用独立专属配置。
     *
     * @param customized 是否为此漫画单独记住设置。
     */
    fun toggleBookCustomized(customized: Boolean) {
        val key = activeBookKey ?: return
        val kv = store ?: return
        val current = _state.value
        if (customized) {
            val json = gson.toJson(current.copy(isBookCustomized = true))
            kv.put("reader_book_override_$key", json)
            _state.value = current.copy(isBookCustomized = true)
        } else {
            kv.remove("reader_book_override_$key")
            _state.value = loadGlobalState(kv).copy(isBookCustomized = false)
        }
    }

    private fun loadState(kvStore: JsonKeyValueStore, bookKey: String?): DesktopReaderPrefsState {
        if (!bookKey.isNullOrBlank()) {
            val overrideJson = kvStore.getString("reader_book_override_$bookKey", null)
            if (!overrideJson.isNullOrBlank()) {
                runCatching {
                    gson.fromJson(overrideJson, DesktopReaderPrefsState::class.java)
                }.getOrNull()?.let { return it.copy(isBookCustomized = true) }
            }
        }
        return loadGlobalState(kvStore).copy(isBookCustomized = false)
    }

    private fun loadGlobalState(kvStore: JsonKeyValueStore): DesktopReaderPrefsState {
        return DesktopReaderPrefsState(
            // 旧「适应屏幕」总闸退场：关着的那批直接落原始档，之后适配只由档位说。
            fitIndex = kvStore.getInt("reader_fit_index", 0).coerceIn(0, 2)
                .let { if (kvStore.getBoolean("reader_fit_screen", true)) it else 2 },
            preloadCount = kvStore.getInt("reader_preload_count", 4).let { if (it in listOf(2, 4, 8)) it else 4 },
            autoPlaySecs = kvStore.getInt("reader_autoplay_secs", 4).coerceIn(2, 15),
            arrowKeysOn = kvStore.getBoolean("reader_arrow_keys", true),
            coverSinglePage = kvStore.getBoolean("reader_cover_single", true),
            smoothTurning = kvStore.getBoolean("reader_smooth_turning", true),
            turnEffect = kvStore.getInt("reader_turn_effect_v2", 4).let { if (it in 2..4) it else 4 },
            turnPerspective = kvStore.getInt("reader_turn_perspective", 20).coerceIn(5, 50),
            guidedViewStyle = kvStore.getInt("reader_gv_style", 0).coerceIn(0, 2),
            guidedViewPadding = kvStore.getInt("reader_gv_padding", 100).coerceIn(0, 200),
            guidedViewMaxZoom = kvStore.getString("reader_gv_max_zoom", "2.0")?.toFloatOrNull()?.coerceIn(1.5f, 4f) ?: 2f,
            guidedViewMaskStyle = kvStore.getInt("reader_gv_mask_style", 1).coerceIn(0, 2),
            guidedViewShowFullFirst = kvStore.getBoolean("reader_gv_full_first", false),
            guidedViewShowFullLast = kvStore.getBoolean("reader_gv_full_last", false),
            rotationIndex = kvStore.getInt("reader_rotation_index", 0).coerceIn(0, 3),
            pageSoundOn = kvStore.getBoolean("reader_page_sound", true),
            tapZonePercent = kvStore.getInt("reader_tap_zone", 15).coerceIn(15, 33),
            // 滤镜三键已改 0..4f 倍率口径（1=100%）；旧档 brightness 存的是 -0.5..0.5 偏移量，
            // 直接当倍率读会把 0（旧默认=不变）当 0 倍率（全黑），故命中旧偏移区间时 +1 迁移。
            brightness = kvStore.getString("reader_brightness", "1")
                ?.toFloatOrNull()
                ?.let { if (it in -0.5f..0.5f && it != 1f) it + 1f else it }
                ?.coerceIn(0f, 4f) ?: 1f,
            contrast = kvStore.getString("reader_contrast", "1")?.toFloatOrNull()?.coerceIn(0f, 4f) ?: 1f,
            saturation = kvStore.getString("reader_saturation", "1")?.toFloatOrNull()?.coerceIn(0f, 4f) ?: 1f,
            filterQualityIndex = kvStore.getInt("reader_filter_quality", 1).coerceIn(0, 2),
            dragTurnOn = kvStore.getBoolean("reader_drag_turn", true),
            turnAnimSeconds = kvStore.getString("reader_turn_anim_seconds", "0.3")?.toFloatOrNull()?.coerceIn(0.1f, 4f) ?: 0.3f,
            shadowOn = kvStore.getBoolean("reader_shadow_on", true),
            shadowSize = kvStore.getString("reader_shadow_size", "7")?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 7f,
            shadowOpacity = kvStore.getString("reader_shadow_opacity", "50")?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 50f,
            shadowDisplace = kvStore.getString("reader_shadow_displace", "5")?.toFloatOrNull()?.coerceIn(-100f, 100f) ?: 5f,
            alignNextHorizontal = kvStore.getBoolean("reader_align_next_horizontal", true),
            blankPage = kvStore.getBoolean("reader_blank_page", false),
            keepWideSingle = kvStore.getBoolean("reader_keep_wide_single", true),
            noEnlarge = kvStore.getBoolean("reader_no_enlarge", true),
            singleInScroll = kvStore.getBoolean("reader_single_in_scroll", false),
            rotateH = kvStore.getInt("reader_rotate_h", 0).coerceIn(0, 3),
            marginH = kvStore.getInt("reader_margin_h", 16).coerceIn(0, 200),
            marginV = kvStore.getInt("reader_margin_v", 16).coerceIn(0, 200),
            showPageNumber = kvStore.getBoolean("reader_show_page_number", true),
            reverseReading = kvStore.getBoolean("reader_reverse_reading", false),
            doublePageOn = kvStore.getBoolean("reader_double_page_on", true),
            soundVolume = kvStore.getInt("reader_sound_volume", 80).coerceIn(0, 100),
            filterHue = kvStore.getString("reader_filter_hue", "0")?.toFloatOrNull()?.coerceIn(0f, 360f) ?: 0f,
            filterSepia = kvStore.getString("reader_filter_sepia", "0")?.toFloatOrNull()?.coerceIn(0f, 100f) ?: 0f,
            filterInvert = kvStore.getBoolean("reader_filter_invert", false),
            filterNegative = kvStore.getBoolean("reader_filter_negative", false),
            aiArtifact = kvStore.getBoolean("reader_ai_artifact", false),
            aiArtifactModel = kvStore.getInt("reader_ai_artifact_model", 1).coerceIn(0, 2),
            aiArtifactTone = kvStore.getBoolean("reader_ai_artifact_tone", true),
            aiArtifactSize = kvStore.getString("reader_ai_artifact_size", "3")?.toFloatOrNull()?.coerceIn(1f, 10f) ?: 3f,
            aiDescreen = kvStore.getBoolean("reader_ai_descreen", false),
            aiDescreenModel = kvStore.getInt("reader_ai_descreen_model", 1).coerceIn(0, 1),
            aiDescreenKeep = kvStore.getBoolean("reader_ai_descreen_keep", true),
            aiDescreenSize = kvStore.getString("reader_ai_descreen_size", "3")?.toFloatOrNull()?.coerceIn(1f, 10f) ?: 3f,
            aiUpscale = kvStore.getBoolean("reader_ai_upscale", false),
            aiUpscaleModel = kvStore.getInt("reader_ai_upscale_model", 0).coerceIn(0, 1),
            aiUpscaleMaxmp = kvStore.getString("reader_ai_upscale_maxmp", "2")?.toFloatOrNull()?.coerceIn(0.2f, 50f) ?: 2f,
            aiUpscaleAuto = kvStore.getBoolean("reader_ai_upscale_auto", true),
            aiUpscaleScale = kvStore.getInt("reader_ai_upscale_scale", 4).let { if (it == 2 || it == 4) it else 4 },
            stripMaxWidthPercent = kvStore.getInt("reader_strip_max_width", 100).coerceIn(50, 100),
            stripPageGap = kvStore.getInt("reader_strip_page_gap", 0).coerceIn(0, 32),
            magnifierOn = kvStore.getBoolean("reader_magnifier_on", false),
            magnifierScale = kvStore.getString("reader_magnifier_scale", "2.0")?.toFloatOrNull()?.coerceIn(1.5f, 4f) ?: 2f,
            isBookCustomized = false,
        )
    }

    /**
     * 更新并持久化阅读器偏好。
     *
     * @param transform 基于当前状态的变换函数。
     */
    fun update(transform: (DesktopReaderPrefsState) -> DesktopReaderPrefsState) {
        val next = transform(_state.value)
        // 值没变就不落盘：开关类调用会从按键与手势的热路径进来，没必要每次都写全量键。
        if (next == _state.value) return
        _state.value = next
        val kv = store ?: return
        val bookKey = activeBookKey
        if (next.isBookCustomized && !bookKey.isNullOrBlank()) {
            val json = gson.toJson(next)
            kv.put("reader_book_override_$bookKey", json)
            return
        }
        kv.put("reader_fit_index", next.fitIndex)
        kv.put("reader_preload_count", next.preloadCount)
        kv.put("reader_autoplay_secs", next.autoPlaySecs)
        kv.put("reader_arrow_keys", next.arrowKeysOn)
        kv.put("reader_cover_single", next.coverSinglePage)
        kv.put("reader_smooth_turning", next.smoothTurning)
        kv.put("reader_turn_effect_v2", next.turnEffect)
        kv.put("reader_rotation_index", next.rotationIndex)
        kv.put("reader_page_sound", next.pageSoundOn)
        kv.put("reader_tap_zone", next.tapZonePercent)
        kv.put("reader_brightness", next.brightness.toString())
        kv.put("reader_contrast", next.contrast.toString())
        kv.put("reader_saturation", next.saturation.toString())
        kv.put("reader_filter_quality", next.filterQualityIndex)
        kv.put("reader_drag_turn", next.dragTurnOn)
        kv.put("reader_turn_anim_seconds", next.turnAnimSeconds.toString())
        kv.put("reader_shadow_on", next.shadowOn)
        kv.put("reader_shadow_size", next.shadowSize.toString())
        kv.put("reader_shadow_opacity", next.shadowOpacity.toString())
        kv.put("reader_shadow_displace", next.shadowDisplace.toString())
        kv.put("reader_align_next_horizontal", next.alignNextHorizontal)
        kv.put("reader_blank_page", next.blankPage)
        kv.put("reader_keep_wide_single", next.keepWideSingle)
        kv.put("reader_no_enlarge", next.noEnlarge)
        kv.put("reader_single_in_scroll", next.singleInScroll)
        kv.put("reader_rotate_h", next.rotateH)
        kv.put("reader_margin_h", next.marginH)
        kv.put("reader_margin_v", next.marginV)
        kv.put("reader_show_page_number", next.showPageNumber)
        kv.put("reader_reverse_reading", next.reverseReading)
        kv.put("reader_double_page_on", next.doublePageOn)
        kv.put("reader_sound_volume", next.soundVolume)
        kv.put("reader_filter_hue", next.filterHue.toString())
        kv.put("reader_filter_sepia", next.filterSepia.toString())
        kv.put("reader_filter_invert", next.filterInvert)
        kv.put("reader_filter_negative", next.filterNegative)
        kv.put("reader_ai_artifact", next.aiArtifact)
        kv.put("reader_ai_artifact_model", next.aiArtifactModel)
        kv.put("reader_ai_artifact_tone", next.aiArtifactTone)
        kv.put("reader_ai_artifact_size", next.aiArtifactSize.toString())
        kv.put("reader_ai_descreen", next.aiDescreen)
        kv.put("reader_ai_descreen_model", next.aiDescreenModel)
        kv.put("reader_ai_descreen_keep", next.aiDescreenKeep)
        kv.put("reader_ai_descreen_size", next.aiDescreenSize.toString())
        kv.put("reader_ai_upscale", next.aiUpscale)
        kv.put("reader_ai_upscale_model", next.aiUpscaleModel)
        kv.put("reader_ai_upscale_maxmp", next.aiUpscaleMaxmp.toString())
        kv.put("reader_ai_upscale_auto", next.aiUpscaleAuto)
        kv.put("reader_ai_upscale_scale", next.aiUpscaleScale)
        kv.put("reader_strip_max_width", next.stripMaxWidthPercent)
        kv.put("reader_strip_page_gap", next.stripPageGap)
        kv.put("reader_magnifier_on", next.magnifierOn)
        kv.put("reader_magnifier_scale", next.magnifierScale.toString())
        kv.put("reader_turn_perspective", next.turnPerspective)
        kv.put("reader_gv_style", next.guidedViewStyle)
        kv.put("reader_gv_padding", next.guidedViewPadding)
        kv.put("reader_gv_max_zoom", next.guidedViewMaxZoom.toString())
        kv.put("reader_gv_mask_style", next.guidedViewMaskStyle)
        kv.put("reader_gv_full_first", next.guidedViewShowFullFirst)
        kv.put("reader_gv_full_last", next.guidedViewShowFullLast)
    }

    /** 重置图像滤镜参数为默认值。 */
    fun resetFilters() {
        update {
            it.copy(
                brightness = 1f,
                contrast = 1f,
                saturation = 1f,
                filterHue = 0f,
                filterSepia = 0f,
                filterInvert = false,
                filterNegative = false,
            )
        }
    }

    /** 把当前滤镜参数存成一套预设；同名覆盖，最多留 8 套。 */
    fun saveFilterPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val s = _state.value
        val preset = ReaderFilterPreset(
            name = trimmed,
            brightness = s.brightness,
            contrast = s.contrast,
            saturation = s.saturation,
            filterHue = s.filterHue,
            filterSepia = s.filterSepia,
            filterInvert = s.filterInvert,
            filterNegative = s.filterNegative,
        )
        val next = (_filterPresets.value.filterNot { it.name == trimmed } + preset).takeLast(8)
        _filterPresets.value = next
        writeFilterPresets(next)
    }

    /** 套用第 [index] 套预设。 */
    fun applyFilterPreset(index: Int) {
        val preset = _filterPresets.value.getOrNull(index) ?: return
        update {
            it.copy(
                brightness = preset.brightness,
                contrast = preset.contrast,
                saturation = preset.saturation,
                filterHue = preset.filterHue,
                filterSepia = preset.filterSepia,
                filterInvert = preset.filterInvert,
                filterNegative = preset.filterNegative,
            )
        }
    }

    /** 删除第 [index] 套预设。 */
    fun deleteFilterPreset(index: Int) {
        val current = _filterPresets.value
        if (index !in current.indices) return
        val next = current.toMutableList().also { it.removeAt(index) }
        _filterPresets.value = next
        writeFilterPresets(next)
    }

    private fun readFilterPresets(kv: JsonKeyValueStore): List<ReaderFilterPreset> {
        val raw = kv.getString("reader_filter_presets", null) ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<ReaderFilterPreset>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    private fun writeFilterPresets(list: List<ReaderFilterPreset>) {
        store?.put("reader_filter_presets", gson.toJson(list))
    }

    /**
     * 读取指定章节的书签页码集合（0 基，升序）。
     *
     * @param episodeKey 章节唯一键。
     */
    fun bookmarksOf(episodeKey: String): List<Int> {
        if (episodeKey.isBlank()) return emptyList()
        val raw = store?.getString(bookmarkKey(episodeKey), null) ?: return emptyList()
        return raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it >= 0 }
            .distinct()
            .sorted()
    }

    /**
     * 切换指定章节某页的书签状态，返回切换后是否已收藏为书签。
     *
     * @param episodeKey 章节唯一键。
     * @param page 页码索引（0 基）。
     */
    fun toggleBookmark(episodeKey: String, page: Int): Boolean {
        if (episodeKey.isBlank() || page < 0) return false
        val current = bookmarksOf(episodeKey).toMutableSet()
        val added = if (page in current) {
            current.remove(page)
            false
        } else {
            current.add(page)
            true
        }
        store?.put(bookmarkKey(episodeKey), current.sorted().joinToString(","))
        _bookmarksVersion.value += 1
        return added
    }

    private fun bookmarkKey(episodeKey: String): String = "reader_bookmarks_${episodeKey.hashCode()}"
}

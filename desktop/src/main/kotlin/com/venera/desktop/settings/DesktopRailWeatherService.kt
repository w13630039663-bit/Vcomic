package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 侧栏星穹天气分类（由 WMO 标准气象码归纳为 5 档视觉表现）。
 */
enum class DesktopRailWeatherType(
    val dayLabelZh: String,
    val nightLabelZh: String,
    val dayLabelEn: String,
    val nightLabelEn: String,
) {
    CLEAR("晴空", "星夜", "Clear", "Starry"),
    CLOUDY("多云", "阴云", "Cloudy", "Overcast"),
    RAIN("细雨", "夜雨", "Rain", "Rainy"),
    STORM("雷雨", "雷暴", "Storm", "Thunder"),
    SNOW("飞雪", "雪夜", "Snow", "Snowy"),
}

/**
 * 侧栏实时天气快照。
 */
data class DesktopRailWeatherSnapshot(
    val type: DesktopRailWeatherType = DesktopRailWeatherType.CLEAR,
    val city: String = "",
    val tempC: Int? = null,
    val wmoCode: Int = 0,
    val updatedAtMs: Long = 0L,
    val syncing: Boolean = false,
)

/**
 * 侧栏天气联动模式偏好（关闭 / 自动 IP 定位 / 手动指定城市）。
 */
object DesktopRailWeatherMode {
    const val AUTO = "自动定位"
    const val CITY = "指定城市"
    const val OFF = "关闭"
    val OPTIONS = listOf(AUTO, CITY, OFF)
}

/**
 * 主题切换全窗转场特效偏好。
 */
object DesktopThemeRevealPrefs {
    const val STARBURST = "星穹冲击波"
    const val RIFT = "次元裂隙"
    const val VORTEX = "螺旋光阑"
    const val NONE = "无"
    val OPTIONS = listOf(STARBURST, RIFT, VORTEX, NONE)

    private const val PREF_KEY = "pref_desktop_theme_reveal_fx"
    private var boundStore: JsonKeyValueStore? = null

    private val _effect = MutableStateFlow(STARBURST)
    val effect: StateFlow<String> = _effect.asStateFlow()

    /** 冷启动绑定偏好存储并回填转场特效档位。 */
    fun bind(store: JsonKeyValueStore) {
        boundStore = store
        val saved = store.getString(PREF_KEY, null)
        if (saved != null && saved in OPTIONS) {
            _effect.value = saved
        }
    }

    /** 切换转场特效并落盘保存。 */
    fun write(store: JsonKeyValueStore, value: String) {
        if (value !in OPTIONS) return
        boundStore = store
        _effect.value = value
        store.put(PREF_KEY, value)
    }
}

/**
 * 桌面侧栏位置与实时天气拉取服务：
 * - 自动模式通过无权限弹窗的 IP 粗定位获取经纬度，再调免 Key 的 Open-Meteo 气象接口；
 * - 指定城市模式通过 Open-Meteo Geocoding API 解析城市坐标，彻底规避代理 IP 漂移；
 * - 结果与时间戳本地缓存 30 分钟，网络失败静默回退默认晴空/星夜，不阻塞 UI。
 */
object DesktopRailWeatherService {
    private const val KEY_MODE = "pref_desktop_rail_weather_mode"
    private const val KEY_CUSTOM_CITY = "pref_desktop_rail_weather_city"
    private const val KEY_CACHE_TYPE = "pref_desktop_rail_weather_cache_type"
    private const val KEY_CACHE_CITY = "pref_desktop_rail_weather_cache_city"
    private const val KEY_CACHE_TEMP = "pref_desktop_rail_weather_cache_temp"
    private const val KEY_CACHE_AT = "pref_desktop_rail_weather_cache_at"
    private const val CACHE_TTL_MS = 30L * 60L * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private var boundStore: JsonKeyValueStore? = null

    private val _mode = MutableStateFlow(DesktopRailWeatherMode.AUTO)
    val mode: StateFlow<String> = _mode.asStateFlow()

    private val _customCity = MutableStateFlow("")
    val customCity: StateFlow<String> = _customCity.asStateFlow()

    private val _snapshot = MutableStateFlow(DesktopRailWeatherSnapshot())
    val snapshot: StateFlow<DesktopRailWeatherSnapshot> = _snapshot.asStateFlow()

    /** 冷启动绑定偏好仓库，恢复上次缓存天气并在过期时静默刷新。 */
    fun bind(store: JsonKeyValueStore) {
        boundStore = store
        val savedMode = store.getString(KEY_MODE, null)
        if (savedMode != null && savedMode in DesktopRailWeatherMode.OPTIONS) {
            _mode.value = savedMode
        }
        _customCity.value = store.getString(KEY_CUSTOM_CITY, "") ?: ""

        val cachedType = runCatching {
            DesktopRailWeatherType.valueOf(store.getString(KEY_CACHE_TYPE, "") ?: "")
        }.getOrDefault(DesktopRailWeatherType.CLEAR)
        val cachedCity = store.getString(KEY_CACHE_CITY, "") ?: ""
        val cachedAt = store.getLong(KEY_CACHE_AT, 0L)
        val cachedTempRaw = store.getString(KEY_CACHE_TEMP, null)?.toIntOrNull()

        if (_mode.value != DesktopRailWeatherMode.OFF && cachedAt > 0L) {
            _snapshot.value = DesktopRailWeatherSnapshot(
                type = cachedType,
                city = cachedCity,
                tempC = cachedTempRaw,
                updatedAtMs = cachedAt,
            )
        }
        if (_mode.value != DesktopRailWeatherMode.OFF &&
            System.currentTimeMillis() - cachedAt >= CACHE_TTL_MS
        ) {
            refreshAsync()
        }
    }

    /** 切换天气联动模式（自动定位 / 指定城市 / 关闭）。 */
    fun setMode(store: JsonKeyValueStore, nextMode: String) {
        if (nextMode !in DesktopRailWeatherMode.OPTIONS) return
        boundStore = store
        _mode.value = nextMode
        store.put(KEY_MODE, nextMode)
        if (nextMode == DesktopRailWeatherMode.OFF) {
            _snapshot.value = DesktopRailWeatherSnapshot()
        } else {
            refreshAsync()
        }
    }

    /** 保存手动指定城市名称；若非空则自动切换到指定城市模式并立即拉取天气。 */
    fun setCustomCity(store: JsonKeyValueStore, city: String) {
        boundStore = store
        val clean = city.trim()
        _customCity.value = clean
        store.put(KEY_CUSTOM_CITY, clean)
        if (clean.isNotEmpty() && _mode.value != DesktopRailWeatherMode.CITY) {
            _mode.value = DesktopRailWeatherMode.CITY
            store.put(KEY_MODE, DesktopRailWeatherMode.CITY)
        }
        if (_mode.value != DesktopRailWeatherMode.OFF) {
            refreshAsync()
        }
    }

    /** 异步拉取一次最新天气，供设置页「立即刷新」按钮或缓存过期调用。 */
    fun refreshAsync() {
        val activeMode = _mode.value
        if (activeMode == DesktopRailWeatherMode.OFF) return
        if (_snapshot.value.syncing) return
        _snapshot.value = _snapshot.value.copy(syncing = true)
        scope.launch {
            val fetched = runCatching {
                when (activeMode) {
                    DesktopRailWeatherMode.CITY -> {
                        val target = _customCity.value.trim()
                        if (target.isEmpty()) fetchByAutoIp() else fetchByCityName(target)
                    }
                    DesktopRailWeatherMode.AUTO -> fetchByAutoIp()
                    else -> null
                }
            }.getOrNull()

            if (fetched != null) {
                _snapshot.value = fetched
                boundStore?.let { store ->
                    store.put(KEY_CACHE_TYPE, fetched.type.name)
                    store.put(KEY_CACHE_CITY, fetched.city)
                    store.put(KEY_CACHE_TEMP, fetched.tempC?.toString() ?: "")
                    store.put(KEY_CACHE_AT, fetched.updatedAtMs)
                }
            } else {
                _snapshot.value = _snapshot.value.copy(syncing = false)
            }
        }
    }

    /** 将 WMO 标准气象码映射为 5 种侧栏天象类别。 */
    internal fun mapWmoToWeatherType(code: Int): DesktopRailWeatherType = when {
        code == 0 || code == 1 -> DesktopRailWeatherType.CLEAR
        code == 2 || code == 3 || code == 45 || code == 48 -> DesktopRailWeatherType.CLOUDY
        code in 51..67 || code in 80..82 -> DesktopRailWeatherType.RAIN
        code in 71..77 || code == 85 || code == 86 -> DesktopRailWeatherType.SNOW
        code >= 95 -> DesktopRailWeatherType.STORM
        else -> DesktopRailWeatherType.CLEAR
    }

    private fun fetchByAutoIp(): DesktopRailWeatherSnapshot? {
        val ipObj = fetchJsonObject("https://ipapi.co/json/")
        val lat = ipObj?.get("latitude")?.jsonPrimitive?.doubleOrNull
        val lon = ipObj?.get("longitude")?.jsonPrimitive?.doubleOrNull
        val city = ipObj?.get("city")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: ipObj?.get("region")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        if (lat != null && lon != null) {
            return fetchOpenMeteoForecast(lat, lon, city ?: "当前位置")
        }

        // 备用 HTTPS IP 接口（ipinfo.io 返回 "lat,lon" 字段）
        val infoObj = fetchJsonObject("https://ipinfo.io/json") ?: return null
        val locParts = infoObj["loc"]?.jsonPrimitive?.content?.split(",") ?: return null
        val lat2 = locParts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon2 = locParts.getOrNull(1)?.toDoubleOrNull() ?: return null
        val city2 = infoObj["city"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "当前位置"
        return fetchOpenMeteoForecast(lat2, lon2, city2)
    }

    private fun fetchByCityName(city: String): DesktopRailWeatherSnapshot? {
        val encoded = URLEncoder.encode(city, Charsets.UTF_8)
        val geo = fetchJsonObject(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=zh&format=json",
        ) ?: return null
        val first = geo["results"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val lat = first["latitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = first["longitude"]?.jsonPrimitive?.doubleOrNull ?: return null
        val resolvedName = first["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: city
        return fetchOpenMeteoForecast(lat, lon, resolvedName)
    }

    private fun fetchOpenMeteoForecast(
        lat: Double,
        lon: Double,
        cityLabel: String,
    ): DesktopRailWeatherSnapshot? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
        val root = fetchJsonObject(url) ?: return null
        val current = root["current"]?.jsonObject ?: return null
        val code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: 0
        val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull?.let { kotlin.math.round(it).toInt() }
        return DesktopRailWeatherSnapshot(
            type = mapWmoToWeatherType(code),
            city = cityLabel,
            tempC = temp,
            wmoCode = code,
            updatedAtMs = System.currentTimeMillis(),
            syncing = false,
        )
    }

    private fun fetchJsonObject(url: String): JsonObject? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "VeneraDesktop/1.0")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return null
            json.parseToJsonElement(body).jsonObject
        }
    }.getOrNull()
}

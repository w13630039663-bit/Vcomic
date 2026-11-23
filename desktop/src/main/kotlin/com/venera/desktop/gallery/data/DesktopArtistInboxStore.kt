package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 画师收件箱持久化状态模型。
 *
 * 两枚水位键的键值口径：
 * - [galleryWatermarks] 键 = `GalleryArtistFollow.uid`（`站点routeKey:小写名`），
 *   值 = 该标签已见最大的 post id；
 * - [pixivWatermarks] 键 = 画师名（与卡面 `author` 同键口径），值 = 该画师已见最大
 *   illust id —— 按画师拆（规格 §4「每画师 maxIllustId」），段内已读不把没读的
 *   另一位画师的新作推成旧作。
 *
 * 水位只由 [DesktopArtistInboxStore.markAllViewed] 推进 —— 同步取数不落盘水位，
 * 否则本轮取回的作品全被推成旧作、新作区恒空。
 */
@Serializable
internal data class ArtistInboxPersistedState(
    val lastReadTimestamp: Long = 0L,
    val readUids: Set<String> = emptySet(),
    val starredArtists: Set<String> = emptySet(),
    val snoozedArtists: Set<String> = emptySet(),
    val galleryWatermarks: Map<String, Long> = emptyMap(),
    val pixivWatermarks: Map<String, Long> = emptyMap(),
    val lastSyncAt: Long = 0L,
)

/**
 * 关注画师收件箱的桌面本地状态存储。
 *
 * 管理已读、星标重点、静音暂缓与上次查看时间节点。
 */
internal class DesktopArtistInboxStore(private val file: File) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(readStateSync())
    val state: StateFlow<ArtistInboxPersistedState> = _state.asStateFlow()

    private fun readStateSync(): ArtistInboxPersistedState {
        if (!file.exists() || file.length() == 0L) {
            return ArtistInboxPersistedState()
        }
        return runCatching {
            json.decodeFromString<ArtistInboxPersistedState>(file.readText())
        }.getOrDefault(ArtistInboxPersistedState())
    }

    private suspend fun persist(newState: ArtistInboxPersistedState) = withContext(Dispatchers.IO) {
        _state.value = newState
        runCatching {
            file.parentFile?.mkdirs()
            val temp = File.createTempFile("artist_inbox_", ".tmp", file.parentFile)
            temp.writeText(json.encodeToString(ArtistInboxPersistedState.serializer(), newState))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    suspend fun markRead(uid: String) {
        val curr = _state.value
        if (uid in curr.readUids) return
        persist(curr.copy(readUids = curr.readUids + uid))
    }

    suspend fun toggleRead(uid: String) {
        val curr = _state.value
        val next = if (uid in curr.readUids) curr.readUids - uid else curr.readUids + uid
        persist(curr.copy(readUids = next))
    }

    suspend fun markAllRead(uids: Collection<String>) {
        val curr = _state.value
        val now = System.currentTimeMillis()
        persist(curr.copy(lastReadTimestamp = now, readUids = curr.readUids + uids))
    }

    /**
     * 每轮同步收尾写入的那一句：**只写 lastSyncAt**，不碰水位与已读。
     * 水位落盘只发生在 [markAllViewed]，两处规格不互斥。
     */
    suspend fun recordSync() {
        persist(_state.value.copy(lastSyncAt = System.currentTimeMillis()))
    }

    /**
     * 「全部已读」：已读并集 + 上次查看时刻 + 水位推进，一次 persist 合并写。
     *
     * @param uids 本轮视图的全部条目 uid（并进 readUids）。
     * @param galleryMax 图库侧候选水位（键 = 画师 uid，值 = 该标签已见最大 post id）；
     *   `null` = 本轮不推进图库侧（段内已读不把用户没读的另一源新作推成旧作，
     *   也不清退另一源已有水位）。
     * @param pixivMax pixiv 侧候选水位（键 = 画师名，值 = 该画师已见最大 illust id）；
     *   `null` 同上。
     */
    suspend fun markAllViewed(
        uids: Collection<String>,
        galleryMax: Map<String, Long>?,
        pixivMax: Map<String, Long>?,
    ) {
        val curr = _state.value
        val now = System.currentTimeMillis()
        val nextWatermarks = galleryMax?.let { incoming ->
            // 同标签取 max：候选值只前进不后退（旧值大说明别处已见过更新的）。
            val merged = curr.galleryWatermarks.toMutableMap()
            incoming.forEach { (uid, value) -> merged[uid] = maxOf(merged[uid] ?: 0L, value) }
            merged.toMap()
        } ?: curr.galleryWatermarks
        val nextPixiv = pixivMax?.let { incoming ->
            // 同画师取 max：候选值只前进不后退。
            val merged = curr.pixivWatermarks.toMutableMap()
            incoming.forEach { (artist, value) -> merged[artist] = maxOf(merged[artist] ?: 0L, value) }
            merged.toMap()
        } ?: curr.pixivWatermarks
        persist(
            curr.copy(
                lastReadTimestamp = now,
                readUids = curr.readUids + uids,
                galleryWatermarks = nextWatermarks,
                pixivWatermarks = nextPixiv,
            ),
        )
    }

    suspend fun toggleStar(artistName: String) {
        val curr = _state.value
        val next = if (artistName in curr.starredArtists) {
            curr.starredArtists - artistName
        } else {
            curr.starredArtists + artistName
        }
        persist(curr.copy(starredArtists = next))
    }

    suspend fun toggleSnooze(artistName: String) {
        val curr = _state.value
        val next = if (artistName in curr.snoozedArtists) {
            curr.snoozedArtists - artistName
        } else {
            curr.snoozedArtists + artistName
        }
        persist(curr.copy(snoozedArtists = next))
    }

    companion object {
        private const val FILE_NAME = "artist_inbox_state.json"

        @Volatile
        private var instance: DesktopArtistInboxStore? = null

        fun getInstance(paths: PathProvider?): DesktopArtistInboxStore {
            val root = paths?.dataRoot ?: File(System.getProperty("user.home"), ".venera/data")
            return instance ?: synchronized(this) {
                instance ?: DesktopArtistInboxStore(File(root, FILE_NAME)).also { instance = it }
            }
        }
    }
}

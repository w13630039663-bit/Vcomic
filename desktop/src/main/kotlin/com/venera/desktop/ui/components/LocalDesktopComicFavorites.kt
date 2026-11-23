package com.venera.desktop.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.venera.compose.data.db.FavoriteRecord

/**
 * 卡上直接落本地收藏的门（卡右键「加入收藏」直接落本地这一腿）。
 *
 * 只包装配点已有的两枚真口（[add] = 、[remove] = ），
 * 不另起第二套收藏写链。各在线卡屏从 [LocalDesktopComicFavorites] 取用，菜单项「加入收藏」直接落库；
 * 网络收藏这一腿本轮不接（引擎收藏方法未核准），动作件里 null 即不摆。
 *
 * 收藏态查询（isFavoriteOf）本轮也不接，屏直接摆「加入收藏」——FavoriteDao 主键 (comic_id, source_name)，
 * 重复 add 由库侧兜，不做加入/取消文案切换，避免拿不准的状态把「已收」念成「没收过」再叠一遍（§420 那条坑）。
 */
class DesktopComicFavorites(
    private val add: (suspend (FavoriteRecord) -> Unit)?,
    private val remove: (suspend (String) -> Unit)?,
) {
    /** 装配点给了 add 才摆「加入收藏」。null ⇒ 菜单不摆这一项（不摆落不到的假动作）。 */
    val canAdd: Boolean get() = add != null

    /** 装配点给了 remove 才摆「取消收藏」。 */
    val canRemove: Boolean get() = remove != null

    /** 收进本地默认夹。favorite 由屏递进来的原始字段拼，[sourceKey] = 这张卡自己的源。 */
    suspend fun addComic(
        sourceKey: String,
        comicId: String,
        title: String,
        cover: String,
        author: String = "",
        tags: String = "",
    ) {
        val write = add ?: return
        write(FavoriteRecord(comicId, title, author, cover, sourceKey, "默认", tags))
    }

    /** 从本地收藏移出（按漫画 id）。 */
    suspend fun removeComic(comicId: String) {
        remove?.invoke(comicId)
    }
}

val LocalDesktopComicFavorites = compositionLocalOf { DesktopComicFavorites(null, null) }

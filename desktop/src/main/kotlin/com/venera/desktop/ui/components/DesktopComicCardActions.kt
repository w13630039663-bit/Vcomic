package com.venera.desktop.ui.components

import com.venera.desktop.app.DESKTOP_EXPLORE_SOURCE
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.venera.desktop.ui.components.DesktopContextMenuArea
import com.venera.desktop.ui.components.DesktopMenuEntry

/**
 * 漫画卡 / 二级页对象的右键动作集。
 *
 * 硬口径：**搜索与收藏都按这张卡自己的源走**。[sourceKey] 由屏装配点递进来（卡模型
 * `EngineSession.ComicCard` 不带源字段，源在屏这一层，不往共享模型塞字段）；搜标签、搜画师、
 * 加入本地收藏、网络收藏全部吃它，绝不吃全局默认源 `DESKTOP_EXPLORE_SOURCE`。
 *
 * 只收**真能落到**的回调：某一路装配点没接上就传 null ⇒ 那一项 `enabled=false` 置灰不上屏，
 * 不摆「点了没反应」的空项（共享 `ComicCardContextMenu` 那两枚 NoOp 口在桌面不复用）。
 *
 * 收藏两条腿都允许（补令）：本地收藏走 [onAddLocalFavorite]，网络收藏走
 * [onAddNetworkFavorite]；两者可同时为 null（比如本地已下载书），菜单就不摆收藏项。
 */
data class DesktopComicCardActions(
    val sourceKey: String,
    /** 打开这本漫画的详情页 / 继续阅读。 */
    val onOpenDetail: (() -> Unit)? = null,
    /** 加入本地收藏（按 sourceKey 落库）。 */
    val onAddLocalFavorite: (() -> Unit)? = null,
    /** 加入网络收藏（按 sourceKey 对应源的账号）。null = 该源没登录 / 不支持。 */
    val onAddNetworkFavorite: (() -> Unit)? = null,
    /** 取消收藏（本地或网络，屏自己判）。 */
    val onRemoveFavorite: (() -> Unit)? = null,
    /** 加入追更（只登记账本，首轮巡检再建基线）；null = 装配点没接或这本来已追。 */
    val onAddFollow: (() -> Unit)? = null,
    /** 搜标签 / 任何搜索 —— 必须按 sourceKey 跳该源的搜索页。 */
    val onSearchInSource: ((String) -> Unit)? = null,
    /** 下载整本 / 单话。 */
    val onDownload: (() -> Unit)? = null,
    /** 复制标题。 */
    val onCopyTitle: (() -> Unit)? = null,
    /** 复制链接。 */
    val onCopyLink: (() -> Unit)? = null,
    /** 在浏览器打开源页。 */
    val onOpenWebsite: (() -> Unit)? = null,
    /** 打开所在文件夹（本地 / 下载对象）。 */
    val onOpenFolder: (() -> Unit)? = null,
    /** 从本地 / 队列删除（危险项，调用方自带确认）。 */
    val onDelete: (() -> Unit)? = null,
    /** 屏蔽本作（危险项，走 guard.addRule("COMIC_ID", id)，不摆假动作）。 */
    val onBlockComic: (() -> Unit)? = null,
)

/**
 * 统一构造在线漫画卡的右键菜单。
 *
 * 分组：打开 / 收藏 / 搜索与复制 / 危险项（一律用 Divider 隔开）。
 * 没有真落点的项用 `enabled=false` 置灰，不藏起来 —— 桌面右键不该「同一张卡有时有这项有时没有」，
 * 但点了没反应更坏，所以置灰而非隐藏（[DesktopMenuEntry.Item.enabled]）。
 */
internal fun desktopComicCardMenuEntries(
    actions: DesktopComicCardActions,
    /** 搜标签项要带的词；null = 这一对象没有可搜的标签（置灰）。 */
    tag: String? = null,
): List<DesktopMenuEntry> = buildList {
    // 口径：装配点没接上的动作**整项不摆**（不摆点了没反应的空项）；只有「有落点但这一张
    // 卡此刻没词/没网址」才置灰（enabled=false），例如没有标签时「搜索标签」。
    if (actions.onOpenDetail != null) {
        add(
            DesktopMenuEntry.Item(
                label = "查看详情",
                shortcut = "Enter",
                icon = Icons.Outlined.UnfoldMore,
                onClick = { actions.onOpenDetail.invoke() },
            ),
        )
    }
    // 收藏两条腿：本地 + 网络，都按 sourceKey 走；哪条没接就只摆接了的那条。
    if (actions.onAddLocalFavorite != null) {
        add(
            DesktopMenuEntry.Item(
                label = "本地收藏",
                icon = Icons.Outlined.StarBorder,
                onClick = { actions.onAddLocalFavorite.invoke() },
            ),
        )
    }
    if (actions.onAddNetworkFavorite != null) {
        add(
            DesktopMenuEntry.Item(
                label = "网络收藏",
                icon = Icons.Outlined.CloudUpload,
                onClick = { actions.onAddNetworkFavorite.invoke() },
            ),
        )
    }
    if (actions.onRemoveFavorite != null) {
        add(
            DesktopMenuEntry.Item(
                label = "取消收藏",
                icon = Icons.Outlined.Star,
                onClick = { actions.onRemoveFavorite.invoke() },
            ),
        )
    }
    if (actions.onAddFollow != null) {
        add(
            DesktopMenuEntry.Item(
                label = "加入追更",
                icon = Icons.Outlined.Refresh,
                onClick = { actions.onAddFollow.invoke() },
            ),
        )
    }
    if (actions.onDownload != null) {
        add(
            DesktopMenuEntry.Item(
                label = "下载",
                icon = Icons.Outlined.Download,
                onClick = { actions.onDownload.invoke() },
            ),
        )
    }
    val actionGroup = listOfNotNull(
        actions.onSearchInSource,
        actions.onCopyTitle,
        actions.onCopyLink,
        actions.onOpenWebsite,
    )
    if (actionGroup.isNotEmpty()) {
        add(DesktopMenuEntry.Divider)
        if (actions.onSearchInSource != null) {
            add(
                DesktopMenuEntry.Item(
                    label = "搜索标签: ${tag.orEmpty()}",
                    icon = Icons.Outlined.Search,
                    // 按源搜索：sourceKey 由 actions 带，回调自己认这个源，不吃全局默认源。
                    enabled = !tag.isNullOrBlank(),
                    onClick = { tag?.let { actions.onSearchInSource.invoke(it) } },
                ),
            )
        }
        if (actions.onCopyTitle != null) {
            add(
                DesktopMenuEntry.Item(
                    label = "复制标题",
                    shortcut = "Ctrl+C",
                    icon = Icons.Outlined.Description,
                    onClick = { actions.onCopyTitle.invoke() },
                ),
            )
        }
        if (actions.onCopyLink != null) {
            add(
                DesktopMenuEntry.Item(
                    label = "复制链接",
                    icon = Icons.Outlined.Link,
                    onClick = { actions.onCopyLink.invoke() },
                ),
            )
        }
        if (actions.onOpenWebsite != null) {
            add(
                DesktopMenuEntry.Item(
                    label = "在站方打开",
                    icon = Icons.Outlined.OpenInNew,
                    onClick = { actions.onOpenWebsite.invoke() },
                ),
            )
        }
    }
    // 危险项一律隔离在分隔线之后。
    if (actions.onDelete != null || actions.onBlockComic != null) {
        add(DesktopMenuEntry.Divider)
        if (actions.onBlockComic != null) {
            add(
                DesktopMenuEntry.Item(
                    label = "屏蔽本作",
                    icon = Icons.Outlined.Block,
                    isDanger = true,
                    onClick = { actions.onBlockComic.invoke() },
                ),
            )
        }
        if (actions.onDelete != null) {
            add(
                DesktopMenuEntry.Item(
                    label = "删除",
                    icon = Icons.Outlined.Delete,
                    isDanger = true,
                    onClick = { actions.onDelete.invoke() },
                ),
            )
        }
    }
}

/** 把动作集套到一块区域上，省得每屏各写一遍 `DesktopContextMenuArea(items = { ... })`。 */
@Composable
internal fun DesktopComicCardMenuArea(
    actions: DesktopComicCardActions,
    tag: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    DesktopContextMenuArea(
        items = { desktopComicCardMenuEntries(actions, tag) },
        modifier = modifier,
        content = content,
    )
}

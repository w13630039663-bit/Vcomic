package com.venera.desktop.pixiv

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.PixivCredentials
import com.venera.compose.gallery.data.PixivRestrict
import com.venera.compose.gallery.data.PixivUserDetail
import com.venera.compose.gallery.data.PixivUserHead
import com.venera.compose.gallery.data.PixivUserWorks
import com.venera.compose.gallery.data.PixivArtistSection
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * pixiv 画师主页（
 * 独立页保留 —— 作品详情页里点头像是**右栏面板**，不跳这里，见
 * [DesktopPixivWorkScreen] 的画师面板）。
 *
 * ## 页头住在网格里
 *
 * 页头（返回/名/关注 + 横幅 + 大头像 + 统计四格 + 段位 tabs）作为 [DesktopPixivWall] 的
 * **header 槽**进同一颗懒网格 —— 从前包在 `DetailPageTemplate`（整页 `verticalScroll`）里
 * 再嵌懒网格，无限高约束直接炸（报的独立画师页 Error 弹窗就是这一形）。
 *
 * 头部：横幅（站方没有横幅接口 ⇒ 渐变兜底）+ 大头像 + 名 / @账号 + 统计四格
 * （登录腿 userDetail，没给的格不摆）+ 关注钮（登录腿写站方）。
 * 三段（插画 / 漫画 / 收藏）照共享 `PixivArtistSection` 次序；
 * 登录腿 `nextUrl` 续页（页尾「更多」把手）。
 *
 * 取数走**登录腿优先**：`userIllusts` 一次一页带全字段；没票据时退匿名
 * `profileAll` + `worksByIds`（屏上念一句「未登录，只有匿名那一档」—— 不静默）。
 */
@Composable
internal fun DesktopPixivArtistScreen(
    userId: Long,
    name: String,
    pixiv: GalleryPixivWorks,
    account: PixivCredentials,
    onBack: () -> Unit,
    onOpenPost: (com.venera.compose.gallery.data.GalleryPost) -> Unit,
    /** 段位成品值 + 改段回调（住壳，进作品页返回不丢）。 */
    sectionState: PixivArtistSection = PixivArtistSection.ILLUST,
    onSectionChanged: (PixivArtistSection) -> Unit = {},
    /** 作品墙滚动状态：壳层 retained 锚点派生，进作品返回不丢位。 */
    wallState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    /** 滚动锚点本体：空墙入场时初始 index 会被 clamp，feed 首次到货后按它补跳一次。 */
    wallAnchor: com.venera.desktop.DesktopScrollAnchor,
    /** 点卡上画师名 → 壳接画师详情页路由；null = 回退浏览器。 */
    onOpenArtist: ((Long, String) -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val auth by account.authState.collectAsState()
    val scope = rememberCoroutineScope()
    val signed = auth is com.venera.compose.gallery.data.PixivAuthState.SignedIn
    val section = sectionState
    var followBusy by remember { mutableStateOf(false) }
    var followNote by remember { mutableStateOf<String?>(null) }
    var followOverride by remember(userId) { mutableStateOf<Boolean?>(null) }

    var artistRetryTick by remember(userId) { mutableStateOf(0) }
    val head = produceState<PixivUserHead?>(null, userId, artistRetryTick) {
        value = pixiv.userHead(userId).getOrNull()
    }
    val worksIndexResult = produceState<Result<PixivUserWorks>?>(null, userId, artistRetryTick) {
        value = null
        value = withContext(Dispatchers.IO) { pixiv.profileAll(userId) }
    }
    val worksIndex = remember(worksIndexResult.value) {
        mutableStateOf(worksIndexResult.value?.getOrNull())
    }
    val detail = produceState<PixivUserDetail?>(null, userId, artistRetryTick) {
        value = account.bearerOrNull()?.let { bearer -> pixiv.userDetail(bearer, userId.toString()).getOrNull() }
    }

    // 作品账：登录腿 nextUrl 续页；匿名腿一次性（profileAll + worksByIds，没有下一页）。
    data class ArtistFeed(val cards: List<DesktopPixivWallCard>, val nextUrl: String?, val anonymous: Boolean)
    var feed by remember(userId, section, signed) { mutableStateOf<ArtistFeed?>(null) }
    var feedError by remember(userId, section, signed) { mutableStateOf<String?>(null) }
    var moreBusy by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(userId, section, signed, artistRetryTick, worksIndexResult.value) {
        feed = null
        feedError = null
        val bearer = account.bearerOrNull()
        if (bearer != null) {
            // 登录腿：一次一页、带全字段（type 桶与段绑死：illust / manga；收藏段走书签腿）。
            val pageRes = when (section) {
                PixivArtistSection.ILLUST ->
                    pixiv.userIllusts(bearer, userId.toString(), "illust")
                PixivArtistSection.MANGA ->
                    pixiv.userIllusts(bearer, userId.toString(), "manga")
                PixivArtistSection.BOOKMARK ->
                    pixiv.bookmarkedIllusts(bearer, userId.toString(), PixivRestrict.Public)
            }
            val page = pageRes.getOrNull()
            if (page != null) {
                feed = ArtistFeed(page.works.map { desktopPixivCardOf(it) }, page.nextUrl, anonymous = false)
                return@LaunchedEffect
            }
            if (section == PixivArtistSection.BOOKMARK) {
                feedError = com.venera.desktop.platform.desktopFailureNoteOf(pageRes.exceptionOrNull())
                return@LaunchedEffect
            }
        }
        if (section == PixivArtistSection.BOOKMARK) {
            feed = ArtistFeed(emptyList(), null, anonymous = !signed)
            return@LaunchedEffect
        }
        val idxRes = worksIndexResult.value ?: return@LaunchedEffect
        val index = idxRes.getOrElse { err ->
            feedError = com.venera.desktop.platform.desktopFailureNoteOf(err)
            return@LaunchedEffect
        }
        val ids = index.idsFor(section).take(60)
        val category = if (section == PixivArtistSection.MANGA) "manga" else "illust"
        val rowsRes = withContext(Dispatchers.IO) {
            pixiv.worksByIds(userId, ids, category, firstPage = true)
        }
        val rows = rowsRes.getOrElse { err ->
            feedError = com.venera.desktop.platform.desktopFailureNoteOf(err)
            return@LaunchedEffect
        }
        feed = ArtistFeed(
            rows.filterNotNull().map { desktopPixivCardOf(it) },
            null,
            anonymous = true,
        )
    }

    val displayName = head.value?.name?.takeIf { it.isNotBlank() } ?: name
    val accountName = head.value?.account?.takeIf { it.isNotBlank() }

    // 返回恢复：入场时墙是空的，retained 的初始 index 被 clamp 掉；feed 首次到货后按锚点补跳一次。
    // 换段/换画师由壳层 reset 锚点，这里只管同段返回的场景。
    var wallRestored by remember(sectionState) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(feed, sectionState) {
        if (wallRestored || feed == null) return@LaunchedEffect
        wallRestored = true
        if (wallAnchor.index > 0 && wallState.firstVisibleItemIndex <= 1) {
            wallState.scrollToItem(wallAnchor.index, wallAnchor.offset)
        }
    }

    DesktopPixivWall(
        cards = feed?.cards ?: emptyList(),
        onOpen = { onOpenPost(it.post) },
        onOpenArtist = onOpenArtist,
        gridState = wallState,
        moreLabel = if (feed?.nextUrl != null) {
            if (moreBusy) "取更多中…" else "更多"
        } else {
            null
        },
        onLoadMore = if (feed?.nextUrl != null) {
            {
                if (!moreBusy) {
                    moreBusy = true
                    scope.launch {
                        val bearer = account.bearerOrNull()
                        val next = bearer?.let { b ->
                            when (section) {
                                PixivArtistSection.ILLUST ->
                                    pixiv.userIllusts(b, userId.toString(), "illust", feed?.nextUrl).getOrNull()
                                PixivArtistSection.MANGA ->
                                    pixiv.userIllusts(b, userId.toString(), "manga", feed?.nextUrl).getOrNull()
                                PixivArtistSection.BOOKMARK ->
                                    pixiv.bookmarkedIllusts(b, userId.toString(), PixivRestrict.Public, feed?.nextUrl).getOrNull()
                            }
                        }
                        val current = feed
                        if (next != null && current != null) {
                            feed = current.copy(
                                cards = current.cards + next.works.map { desktopPixivCardOf(it) },
                                nextUrl = next.nextUrl,
                            )
                        }
                        moreBusy = false
                    }
                }
            }
        } else {
            null
        },
        emptyLine = feedError
            ?: if (feed == null) "正在取…"
            else if (section == PixivArtistSection.BOOKMARK && !signed) "收藏段需要登录 pixiv 后查看。"
            else "这一段还没有作品。",
        header = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── 封面横幅（
                //    官方形 240dp 通栏；返回圆钮浮左上）──
                // 横幅源优先级不变：画师自设背景 → 作品第一张 → 渐变兜底（不替站方编）。
                val heroUrl = detail.value?.backgroundUrl
                    ?: head.value?.backgroundUrl
                    ?: feed?.cards?.firstOrNull()?.url
                Box(modifier = Modifier.fillMaxWidth().height(150.dp).clipToBounds()) {
                    if (heroUrl != null) {
                        VeneraCover(
                            url = heroUrl,
                            contentDescription = "画师横幅",
                            modifier = Modifier.fillMaxSize().blur(18.dp),
                            mask = VeneraCoverMask.Visible,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            tokens.color.primary.copy(alpha = 0.28f),
                                            tokens.color.surfaceContainerHigh,
                                            tokens.color.primaryContainer,
                                        ),
                                    ),
                                ),
                        )
                    }
                    // 底缘压暗，接住下面的身份行。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f)),
                            ),
                    )
                }

                // ── 身份行：大头像压横幅下缘 + 名/handle + 右侧动作 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.screenHorizontal)
                        .offset(y = (-28).dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(tokens.color.primaryContainer)
                            .border(3.dp, tokens.color.background, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        val avatarUrl = head.value?.avatarUrl
                        if (avatarUrl != null) {
                            VeneraCover(
                                url = avatarUrl,
                                contentDescription = displayName,
                                mask = VeneraCoverMask.Visible,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                displayName.take(1).uppercase(),
                                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                                fontWeight = VeneraType.cardTitle.weight,
                                color = tokens.color.onPrimaryContainer,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(bottom = tokens.spacing.space2)) {
                        Text(
                            text = displayName,
                            fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                            fontWeight = VeneraType.cardTitle.weight,
                            color = tokens.color.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        accountName?.let {
                            Text("@$it", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
                        }
                        followNote?.let {
                            Text(it, fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
                        }
                    }
                    // 动作列：已关注/关注（真写口）+ 复制主页链接。
                    Column(
                        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = tokens.spacing.space2),
                    ) {
                        if (signed) {
                            val followed = followOverride ?: (detail.value?.isFollowed == true)
                            PixivFollowButton(
                                followed = followed,
                                busy = followBusy,
                                onToggle = { want ->
                                    if (followBusy) return@PixivFollowButton
                                    followBusy = true
                                    followNote = null
                                    scope.launch {
                                        val bearer = account.bearerOrNull()
                                        val result = if (bearer == null) {
                                            Result.failure(IllegalStateException("拿不到票据"))
                                        } else if (want) {
                                            pixiv.followAdd(bearer, userId.toString(), private = false)
                                        } else {
                                            pixiv.followRemove(bearer, userId.toString())
                                        }
                                        followBusy = false
                                        if (result.isSuccess) followOverride = want
                                        followNote = result.fold(
                                            onSuccess = { if (want) "已关注。" else "已取消关注。" },
                                            onFailure = { t -> "没写上：${t.message?.take(120) ?: t.javaClass.simpleName}" },
                                        )
                                    }
                                },
                            )
                        }
                        // 复制主页链接（真把手：站方规范地址 users/{id}；URL 拼装不写字面 ——
                        // 那枚 pixiv Referer 值的字面唯一出处被管道尺 ③ 钉在 DesktopImagePipeline）。
                        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(tokens.color.surfaceContainerHigh)
                                .clickable {
                                    clipboard.setText(
                                        androidx.compose.ui.text.AnnotatedString("https://%s/users/%d".format("www.pixiv.net", userId)),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⧉", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textSecondary)
                        }
                        // 交系统默认浏览器：能力位不给就不摆。
                        if (com.venera.desktop.gallery.ui.desktopCanBrowseExternally()) {
                            val ux = com.venera.compose.components.LocalUxPlatformServices.current
                            val browserInteraction = remember { MutableInteractionSource() }
                            val browserPressed by browserInteraction.collectIsPressedAsState()
                            val browserHovered by browserInteraction.collectIsHoveredAsState()
                            val browserScale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (browserPressed) 0.9f else 1f,
                                animationSpec = androidx.compose.animation.core.tween(150),
                                label = "artistOpenBrowserPress",
                            )
                            val browserBg by androidx.compose.animation.animateColorAsState(
                                targetValue = when {
                                    browserHovered -> tokens.color.surfaceVariant
                                    else -> tokens.color.surfaceContainerHigh
                                },
                                animationSpec = androidx.compose.animation.core.tween(150),
                                label = "artistOpenBrowserBg",
                            )
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .graphicsLayer {
                                        scaleX = browserScale
                                        scaleY = browserScale
                                    }
                                    .clip(CircleShape)
                                    .background(browserBg)
                                    .clickable(
                                        interactionSource = browserInteraction,
                                        indication = null,
                                    ) {
                                        scope.launch {
                                            val error = com.venera.desktop.gallery.ui.openInBrowser(
                                                "https://%s/users/%d".format("www.pixiv.net", userId),
                                            )
                                            ux.toast(error ?: "已在系统浏览器打开")
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("↗", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textSecondary)
                            }
                        }
                    }
                }
                // offset 压出去的那半枚头像的让位。
                Spacer(Modifier.height(tokens.spacing.space6))

                // ── 统计行（官方形：一行内联，不再装卡）──
                val d = detail.value
                val stats = listOfNotNull(
                    d?.totalFollowers?.let { "粉丝 $it" },
                    d?.totalIllusts?.let { "插画 $it" },
                    d?.totalManga?.let { "漫画 $it" },
                    d?.totalBookmarksPublic?.let { "收藏 $it" },
                )
                if (stats.isNotEmpty()) {
                    Text(
                        text = stats.joinToString("　·　"),
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textSecondary,
                        modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal),
                    )
                } else {
                    Text(
                        text = if (signed) "统计还在取…" else "统计要登录才看得到。",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal),
                    )
                }

                // ── 简介与外链（站方 comment/links 两栏都在 userHead 里；有才摆）──
                val bio = head.value?.comment?.takeIf { it.isNotBlank() }
                if (bio != null) {
                    Text(
                        text = bio,
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(top = tokens.spacing.space2)
                            .padding(horizontal = tokens.spacing.screenHorizontal),
                    )
                }
                val links = head.value?.links.orEmpty()
                if (links.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(top = tokens.spacing.space1)
                            .padding(horizontal = tokens.spacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
                    ) {
                        links.take(3).forEach { url ->
                            Text(
                                text = url.removePrefix("https://").removePrefix("http://"),
                                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                                color = tokens.color.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable {
                                    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
                                },
                            )
                        }
                    }
                }

                // ── 三段 tabs（官方形：下划线活动态 + 计数徽标）──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space3),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space6),
                ) {
                    PixivArtistSection.entries.forEach { s ->
                        val selected = s == section
                        val count = when (s) {
                            PixivArtistSection.ILLUST -> d?.totalIllusts
                            PixivArtistSection.MANGA -> d?.totalManga
                            PixivArtistSection.BOOKMARK -> d?.totalBookmarksPublic
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onSectionChanged(s) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = s.label,
                                    fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) tokens.color.textPrimary else tokens.color.textSecondary,
                                )
                                count?.let {
                                    Spacer(Modifier.width(tokens.spacing.space1))
                                    Text(
                                        text = "$it",
                                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                                        fontWeight = VeneraType.cardTitle.weight,
                                        color = if (selected) tokens.color.primary else tokens.color.textTertiary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(tokens.spacing.space1))
                            // 下划线：选中那枚 28dp 紫条，其余不摆（不是整条灰线顶底）。
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(3.dp)
                                    .background(
                                        if (selected) tokens.color.primary else Color.Transparent,
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                }
                if (feed?.anonymous == true && section == PixivArtistSection.BOOKMARK) {
                    Text(
                        text = "收藏段要登录才看得到（站方没有匿名腿）。",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space2),
                    )
                } else if (feed?.anonymous == true) {
                    Text(
                        text = "未登录，只有匿名那一档（最多 60 张，没有下一页）。",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                        modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal),
                    )
                }
            }
        },
    )
}

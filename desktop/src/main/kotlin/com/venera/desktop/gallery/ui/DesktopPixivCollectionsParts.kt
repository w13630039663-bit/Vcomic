package com.venera.desktop.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraCover
import com.venera.compose.components.venera.VeneraCoverMask
import com.venera.compose.components.venera.hScrollEndFade
import com.venera.compose.gallery.data.PixivCollectionCard
import com.venera.compose.gallery.data.PixivWorkCard
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.desktop.ui.vShadow
import kotlinx.coroutines.delay

/**
 * 珍藏册 UI 部件（pixiv-collections-落地）。
 *
 * 色值一律 [GalleryTokens]（白卡面走 token 的浅档 `SurfaceLevel1 = Color.White`，
 * 不写 `Color(0xFFFFFFFF)` 字面量）；图上浮层（收藏钮 / 画师胶囊 / 页数徽）恒深玻璃
 * `SurfaceGlass`，深浅两档同口径。动效一律经 [CollectionMotion]（其内全部吃
 * `VeneraMotion` 令牌与 `reduceMotion` 读数）。
 */

/**
 * 珍藏册动效集（motionList 的唯一落点）。
 *
 * 全部读 [VeneraMotion.reduceMotion]：为真时位移 / 缩放 / 模糊置零，只留 opacity 过渡。
 */
internal object CollectionMotion {

    /**
     * 卡片悬停抬升：translateY(-2dp) + 阴影增强，150ms ease-out。
     * [interaction] 由调用点自给并与 clickable 共用同一颗，hover / 按压读数才联动。
     */
    @Composable
    fun hoverLift(
        shape: Shape,
        interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    ): Modifier {
        val hovered by interaction.collectIsHoveredAsState()
        val lift by animateDpAsState(
            targetValue = if (VeneraMotion.reduceMotion || !hovered) 0.dp else (-2).dp,
            animationSpec = tween(VeneraMotion.durationFast + 30, easing = VeneraMotion.easingStandard),
            label = "collectionHoverLift",
        )
        return Modifier
            .hoverable(interaction)
            .offset(y = lift)
            .vShadow(
                elevation = if (hovered && !VeneraMotion.reduceMotion) 10.dp else 0.dp,
                shape = shape,
            )
    }

    /** 按压缩放：scale 0.96，150ms ease-out（[interaction] 与 clickable 共用同一颗才有按压读数）。 */
    @Composable
    fun pressScale(interaction: MutableInteractionSource): Modifier {
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (VeneraMotion.reduceMotion || !pressed) 1f else 0.96f,
            animationSpec = tween(VeneraMotion.durationFast + 30, easing = VeneraMotion.easingStandard),
            label = "collectionPressScale",
        )
        return Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

    /** rail 首载 stagger：opacity 0→1 + blur 4→0 + 12dp 下移，300ms，相邻间隔 100ms×index。 */
    @Composable
    fun railStagger(index: Int): Modifier {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            if (VeneraMotion.reduceMotion) {
                progress.snapTo(1f)
                return@LaunchedEffect
            }
            delay(index.coerceAtMost(8) * 100L)
            progress.animateTo(1f, tween(VeneraMotion.durationPage, easing = VeneraMotion.easingDecelerate))
        }
        val v = progress.value
        return Modifier
            .graphicsLayer {
                alpha = v
                if (!VeneraMotion.reduceMotion) {
                    translationY = (1f - v) * 12.dp.toPx()
                }
            }
            .blur(VeneraMotion.staggerBlurStart * (1f - v))
    }

    /**
     * 心形收藏切换：crossfade 300ms cubic-bezier(0.2,0,0,1) + scale 0.25→1。
     * [content] 只画当前态的图标本体（色与描边/填充由调用点按 [liked] 定）。
     */
    @Composable
    fun heartSwap(
        liked: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        val easing = VeneraMotion.easingStandard
        Crossfade(
            targetState = liked,
            animationSpec = tween(300, easing = easing),
            modifier = modifier,
            label = "collectionHeartSwap",
        ) { _ ->
            val appear = remember { Animatable(if (VeneraMotion.reduceMotion) 1f else 0.25f) }
            LaunchedEffect(Unit) { appear.animateTo(1f, tween(300, easing = easing)) }
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = appear.value
                    scaleY = appear.value
                },
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }

    /** 浮层展开收起：150ms fade + 2dp 位移（目标下拉 / 弹出菜单一族）。 */
    @Composable
    fun menuExpand(
        visible: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable AnimatedVisibilityScope.() -> Unit,
    ) {
        val offsetPx = with(androidx.compose.ui.platform.LocalDensity.current) { 2.dp.roundToPx() }
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = fadeIn(tween(150, easing = VeneraMotion.easingStandard)) +
                slideInVertically(tween(150, easing = VeneraMotion.easingStandard)) { _ ->
                    if (VeneraMotion.reduceMotion) 0 else offsetPx
                },
            exit = fadeOut(tween(150, easing = VeneraMotion.easingStandard)) +
                slideOutVertically(tween(150, easing = VeneraMotion.easingStandard)) { _ ->
                    if (VeneraMotion.reduceMotion) 0 else offsetPx
                },
            label = "collectionMenuExpand",
            content = content,
        )
    }
}

/** 拼贴封面格：一格图（[url] 空串 = 浅面占位块）。 */
@Composable
private fun CollectionCoverCell(url: String, description: String, modifier: Modifier = Modifier) {
    val colors = GalleryTokens.Color
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.SurfaceLevel3),
    ) {
        if (url.isNotBlank()) {
            VeneraCover(
                url = url,
                contentDescription = description,
                mask = VeneraCoverMask.Visible,
                shimmerWhileLoading = true,
                preserveAspectRatio = false,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 拼贴行：多图时左大 62% 高满 + 右上下两格；
 * 站方只给 1 张已拼贴好的中图（或 0 张）时直接单格铺满，避免同一张图按三套尺寸重复解码。
 */
@Composable
private fun CollectionCollage(covers: List<String>, height: Dp, bigFraction: Float, gap: Dp, title: String) {
    if (covers.size <= 1) {
        CollectionCoverCell(
            url = covers.firstOrNull().orEmpty(),
            description = title,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(10.dp)),
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp)),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        val first = covers.firstOrNull().orEmpty()
        CollectionCoverCell(
            url = first,
            description = title,
            modifier = Modifier
                .fillMaxHeight()
                .weight(bigFraction),
        )
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f - bigFraction),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            CollectionCoverCell(
                url = covers.getOrElse(1) { first },
                description = title,
                modifier = Modifier.weight(1f),
            )
            CollectionCoverCell(
                url = covers.getOrElse(2) { first },
                description = title,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 小圆头像（站方地址空 = 底色圆座，首字母占位）。详情屏标题条 / 创建者卡也用。 */
@Composable
internal fun CollectionAvatar(url: String, name: String, size: Dp) {
    val colors = GalleryTokens.Color
    if (url.isNotBlank()) {
        VeneraCover(
            url = url,
            contentDescription = name,
            mask = VeneraCoverMask.Visible,
            modifier = Modifier
                .size(size)
                .clip(CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.SurfaceLevel3),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1),
                fontSize = (size.value / 2).sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.TextSecondary,
            )
        }
    }
}

/**
 * 心形收藏钮：28dp 圆钮恒深玻璃，未收藏白描线、已收藏 StatusFavorite 填充。
 * [count] 非空时在钮旁念数（详情页数据行用；卡面浮层不念）。
 */
@Composable
internal fun CollectionLikeButton(
    liked: Boolean,
    count: Int?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val colors = GalleryTokens.Color
    val interaction = remember { MutableInteractionSource() }
    val tint = if (liked) colors.StatusFavorite else colors.TextOnAccent
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = modifier
                .size(size)
                .then(CollectionMotion.pressScale(interaction))
                .clip(CircleShape)
                .background(colors.SurfaceGlass)
                .border(1.dp, colors.OutlineHairline.copy(alpha = 0.4f), CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            CollectionMotion.heartSwap(liked = liked) {
                Icon(
                    imageVector = if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (liked) "取消收藏" else "收藏",
                    tint = tint,
                    modifier = Modifier.size(size * 0.57f),
                )
            }
        }
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (liked) colors.StatusFavorite else colors.TextSecondary,
            )
        }
    }
}

/**
 * rail / 搜索卡带用的拼贴封面卡（底托 14 / 内衬 4 / 内图 10，拼贴左大 62%）。
 *
 * @param liked 本机已确认的收藏态（读 [com.venera.compose.gallery.data.GalleryPixivCollections.bookmarkedIds] 一帧）
 * @param onToggleLike 收藏 / 取消收藏；失败给可读错误是调用点（屏）的事，钮只管触发
 * @param onOpenCreator 点创建者行（卡面主体点击仍走 [onClick] 进详情）
 */
@Composable
internal fun CollectionCoverCard(
    card: PixivCollectionCard,
    liked: Boolean,
    onClick: () -> Unit,
    onToggleLike: (PixivCollectionCard) -> Unit,
    onOpenCreator: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 260.dp,
    mosaicHeight: Dp = 160.dp,
) {
    val colors = GalleryTokens.Color
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(CollectionMotion.hoverLift(shape, interaction))
                .then(CollectionMotion.pressScale(interaction))
                .clip(shape)
                .background(colors.SurfaceLevel1)
                .border(
                    1.dp,
                    if (hovered) colors.OutlineHover else colors.OutlineHairline,
                    shape,
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(4.dp),
        ) {
            Box {
                CollectionCollage(
                    covers = card.coverUrls,
                    height = mosaicHeight,
                    bigFraction = 0.62f,
                    gap = 4.dp,
                    title = card.title,
                )
                CollectionLikeButton(
                    liked = liked,
                    count = null,
                    onToggle = { onToggleLike(card) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 9.dp, bottom = 6.dp)) {
            Text(
                text = card.title,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenCreator),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CollectionAvatar(url = card.profileImageUrl.orEmpty(), name = card.userName, size = 22.dp)
                Text(
                    text = card.userName,
                    fontSize = 12.sp,
                    color = colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 迷你拼贴卡（账号页「珍藏册」块 / 详情页「TA的收藏」墙）：176dp 宽、拼贴高 104、无收藏钮。
 * 站方缩略卡不带作品数读数，第二行不编（只念题名）。
 */
@Composable
internal fun CollectionMiniCoverCard(
    card: PixivCollectionCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 176.dp,
) {
    val colors = GalleryTokens.Color
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(CollectionMotion.hoverLift(shape, interaction))
                .then(CollectionMotion.pressScale(interaction))
                .clip(shape)
                .background(colors.SurfaceLevel1)
                .border(
                    1.dp,
                    if (hovered) colors.OutlineHover else colors.OutlineHairline,
                    shape,
                )
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(4.dp),
        ) {
            CollectionCollage(
                covers = card.coverUrls,
                height = 104.dp,
                bigFraction = 0.58f,
                gap = 3.dp,
                title = card.title,
            )
        }
        Text(
            text = card.title,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 5.dp),
        )
    }
}

/**
 * 详情墙格：对齐官方样式 —— 竖卡 3 列（或首行横幅）、底部居中胶囊（作品标题 + 画师头像 + 画师名）、
 * 多页作品左上「N页」徽。悬停封面 1.03 缩放 150ms。
 */
@Composable
internal fun CollectionWallTile(
    work: PixivWorkCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 0.72f,
) {
    val colors = GalleryTokens.Color
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val zoom by animateFloatAsState(
        targetValue = if (hovered && !VeneraMotion.reduceMotion) 1.03f else 1f,
        animationSpec = tween(VeneraMotion.durationFast + 30, easing = VeneraMotion.easingStandard),
        label = "collectionTileZoom",
    )
    val imageUrl = work.largeUrl ?: work.mediumUrl ?: work.url
    Box(
        modifier = modifier
            .then(CollectionMotion.hoverLift(shape, interaction))
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(colors.SurfaceLevel3)
            .border(
                1.dp,
                if (hovered) colors.OutlineHover else colors.ImageHairline.takeIf { it != Color.Transparent }
                    ?: colors.OutlineHairline,
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
    ) {
        if (!imageUrl.isNullOrBlank()) {
            VeneraCover(
                url = imageUrl,
                contentDescription = work.title,
                mask = VeneraCoverMask.Visible,
                shimmerWhileLoading = true,
                preserveAspectRatio = false,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                    },
            )
        }
        if (work.pageCount > 1) {
            Text(
                text = "${work.pageCount}页",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.TextOnAccent,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(colors.SurfaceGlass)
                    .border(1.dp, colors.OutlineHairline.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        // 底部居中胶囊：作品标题 + 画师真头像 + 画师名（对齐官方样式，恒深玻璃保证亮暗图均清晰）。
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(CircleShape)
                .background(colors.SurfaceGlass)
                .border(1.dp, colors.OutlineHairline.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (work.title.isNotBlank()) {
                Text(
                    text = work.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.TextOnAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            CollectionAvatar(
                url = work.profileImageUrl.orEmpty(),
                name = work.userName,
                size = 16.dp,
            )
            Text(
                text = work.userName,
                fontSize = 11.sp,
                color = colors.TextOnAccent.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}

/**
 * 筛选标签行：左「筛选」胶囊 + 横向滚动标签带 + 右端 64dp 渐隐。
 * 索引 0 恒「全部」（发现页与标签搜索共用：0 = 不筛）。
 *
 * @param tags 标签表（不含「全部」头格，由本件自补）
 * @param selectedIndex 选中下标（0 = 全部）
 */
@Composable
internal fun CollectionFilterBar(
    tags: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GalleryTokens.Color
    val pillShape = CircleShape
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(colors.SurfaceLevel2)
                .border(1.dp, colors.OutlineHairline, pillShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = null,
                tint = colors.TextSecondary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "筛选",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.TextSecondary,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .hScrollEndFade(container = colors.Background, width = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (listOf<String?>(null) + tags).forEachIndexed { index, tag ->
                val selected = index == selectedIndex
                val label = tag ?: "全部"
                val labelColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (selected) colors.AccentPrimary else colors.TextSecondary,
                    animationSpec = tween(VeneraMotion.durationFast),
                    label = "collectionTagText",
                )
                // 动效清单第 3 条胶囊族：标签也吃按压 scale（同一颗 source 才有按压读数）。
                val interaction = remember { MutableInteractionSource() }
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                    maxLines = 1,
                    modifier = Modifier
                        .then(CollectionMotion.pressScale(interaction))
                        .clip(pillShape)
                        .background(if (selected) colors.AccentSoft else colors.SurfaceLevel2)
                        .border(1.dp, if (selected) colors.AccentPrimary else colors.OutlineHairline, pillShape)
                        .clickable(interactionSource = interaction, indication = null) { onPick(index) }
                        .padding(horizontal = 15.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** 页签行（画廊 pixiv 域「推荐 | 珍藏册」与发现页内页签共用）：当前页签描边态。 */
@Composable
internal fun CollectionPageTabs(
    titles: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GalleryTokens.Color
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        titles.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            // 动效清单第 3 条胶囊族：页签同吃按压 scale。
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) colors.TextPrimary else colors.TextSecondary,
                modifier = Modifier
                    .then(CollectionMotion.pressScale(interaction))
                    .clip(CircleShape)
                    .background(if (selected) colors.AccentSoft else Color.Transparent)
                    .border(1.dp, if (selected) colors.AccentPrimary else colors.OutlineHairline, CircleShape)
                    .clickable(interactionSource = interaction, indication = null) { if (!selected) onPick(index) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

/** 空态短句（账号页 / 详情墙共用；TextTertiary 灰字，居中）。 */
@Composable
internal fun CollectionEmptyLine(line: String, modifier: Modifier = Modifier) {
    val colors = GalleryTokens.Color
    Text(
        text = line,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = colors.TextTertiary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    )
}

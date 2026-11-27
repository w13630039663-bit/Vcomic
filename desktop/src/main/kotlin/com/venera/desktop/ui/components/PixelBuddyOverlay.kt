package com.venera.desktop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraMotion
import com.venera.desktop.settings.DesktopPixelBuddyBus
import com.venera.desktop.settings.DesktopPixelBuddyPrefs
import com.venera.desktop.settings.PixelBuddyCommand
import com.venera.desktop.settings.PixelBuddySkin
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 桌面端顶层像素互动伙伴覆盖层。
 *
 * 支持 1~3 只极简 2px 方块像素小家伙（32×20dp）在侧栏右边界竖爬、顶栏底边倒挂、
 * 叠罗汉与相遇贴贴、搜索框拼字与踩台阶光流、空态打盹/失败举锤、收藏/下载/退全屏探头联动、拖拽甩飞与铲屎。
 */
@Composable
fun PixelBuddyOverlay(
    sidebarWidth: Dp,
    topBarHeight: Dp = 44.dp,
    hidden: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val config by DesktopPixelBuddyPrefs.config.collectAsState()
    if (!config.enabled) return

    val searchState by DesktopPixelBuddyBus.searchState.collectAsState()
    val hasCards by DesktopPixelBuddyBus.hasCards.collectAsState()
    val commandPair by DesktopPixelBuddyBus.command.collectAsState()
    val scrollInterruptTick by DesktopPixelBuddyBus.scrollInterruptTick.collectAsState()
    val emptyAnchor by DesktopPixelBuddyBus.emptyAnchor.collectAsState()
    val retryAnchor by DesktopPixelBuddyBus.retryAnchor.collectAsState()
    val wandMode by DesktopPixelBuddyBus.wandMode.collectAsState()
    val hasCustomWandCursor = remember { createPixelWandPointerIcon() != null }
    val wandPosInWindow = if (wandMode && !hasCustomWandCursor) {
        DesktopPixelBuddyBus.wandPointerInWindow.collectAsState().value
    } else null
    val petCueEvent by DesktopPetCueBus.current.collectAsState()

    val density = LocalDensity.current
    val cellPx = with(density) { 2.dp.toPx() }
    val halfH = 10f * cellPx / 2f
    val edgeX = with(density) { sidebarWidth.toPx() }
    val topY = with(density) { topBarHeight.toPx() }
    var overlayOrigin by remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    var pendingBuildAtMs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var recentSearchMoodAtMs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var wasHidden by remember { androidx.compose.runtime.mutableStateOf(hidden) }
    var quickMenuState by remember { androidx.compose.runtime.mutableStateOf<Pair<BuddyAgent, Offset>?>(null) }
    var stageW by remember { androidx.compose.runtime.mutableFloatStateOf(960f) }
    var stageH by remember { androidx.compose.runtime.mutableFloatStateOf(720f) }
    var lateNightMode by remember { androidx.compose.runtime.mutableStateOf(false) }
    var lastLightCueAtMs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }

    fun Rect.toLocal(): Rect = translate(-overlayOrigin.x, -overlayOrigin.y)

    val agents = remember {
        listOf(
            BuddyAgent(0, PixelBuddySkin.CAT, BuddyWall.SIDE, edgeX, topY + 180f, 1f),
            BuddyAgent(1, PixelBuddySkin.ORANGE, BuddyWall.TOP, edgeX + 180f, topY, -1f),
            BuddyAgent(2, PixelBuddySkin.BLUE, BuddyWall.SIDE, edgeX, topY + 320f, -1f),
        )
    }
    val poops = remember { mutableStateListOf<BuddyPoop>() }
    val paintController = remember { PixelBuddyCardPaintController() }
    val petSpacingPx = with(density) { 38.dp.toPx() }
    val searchMarginPx = with(density) { 18.dp.toPx() }
    val petHitHalfPx = with(density) { 18.dp.toPx() }
    val poopHitHalfPx = with(density) { 12.dp.toPx() }
    var food by remember { androidx.compose.runtime.mutableStateOf<BuddyFood?>(null) }
    var frameTick by remember { mutableIntStateOf(0) }
    var animTick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { paintController.finishAndClear() }
    }

    // 同步主宠皮肤
    LaunchedEffect(config.primarySkin) {
        val others = PixelBuddySkin.entries.filter { it != config.primarySkin }
        agents[0].skin = config.primarySkin
        agents[1].skin = others.getOrElse(0) { PixelBuddySkin.ORANGE }
        agents[2].skin = others.getOrElse(1) { PixelBuddySkin.BLUE }
    }

    // 退出阅读器 / 全屏看图 / 设置回到主界面时，从侧栏外沿探头走出来
    LaunchedEffect(hidden, edgeX) {
        if (wasHidden && !hidden) {
            paintController.finishAndClear()
            agents.take(config.count).forEachIndexed { idx, p ->
                p.stackHostId = -1
                p.hammerTimerMs = 0f
                p.wall = BuddyWall.SIDE
                p.mode = BuddyMode.PEEK_IN
                p.x = edgeX - (18f + idx * 4f) * density.density
                p.y = (topY + 140f + idx * 54f).coerceAtMost((stageH - 70f).coerceAtLeast(topY + 60f))
                p.peekTargetX = edgeX
                p.dir = if (idx % 2 == 0) 1f else -1f
                p.icon = if (idx == 0) PixelIcon.STAR else null
                p.iconTimer = if (idx == 0) 12 else 0
            }
        }
        wasHidden = hidden
    }

    // 响应搜索框聚焦、搜索中与失焦（拉开 38dp 间距不重叠，搜索中稳稳留在搜索框上）
    LaunchedEffect(searchState.focused, searchState.searching, searchState.boundsInWindow, config.searchTypingSpell, overlayOrigin, hidden, pendingBuildAtMs <= 0L) {
        if (hidden) return@LaunchedEffect
        val active = agents.take(config.count)
        val rect = searchState.boundsInWindow?.toLocal()
        if (config.searchTypingSpell && (searchState.focused || searchState.searching) && rect != null) {
            active.forEachIndexed { i, p ->
                if (p.wall != BuddyWall.SEARCH) {
                    launchAgentJump(p, rect.right - searchMarginPx - i * petSpacingPx, rect.top - halfH, BuddyWall.SEARCH)
                }
            }
        } else if (!searchState.focused && !searchState.searching) {
            active.forEachIndexed { i, p ->
                if (p.wall == BuddyWall.SEARCH && pendingBuildAtMs <= 0L) {
                    launchAgentJump(p, edgeX + halfH, (topY + 150f + i * 56f).coerceAtMost((stageH - 64f).coerceAtLeast(topY + 60f)), BuddyWall.SIDE)
                }
            }
        }
    }

    // 响应打字蹦跶
    LaunchedEffect(searchState.typingTick) {
        if (hidden || searchState.typingTick <= 0 || !config.searchTypingSpell || VeneraMotion.reduceMotion) return@LaunchedEffect
        val active = agents.take(config.count)
        val p = active[searchState.typingTick % active.size]
        if (p.wall == BuddyWall.SEARCH) {
            p.mode = BuddyMode.HOP
            p.hopPhase = 0f
        }
    }

    // 记录搜索确认时刻：搜索中留在框上巡逻，等搜索完成 + 卡片就位后分列踩台阶点亮
    LaunchedEffect(searchState.confirmTick) {
        if (searchState.confirmTick <= 0 || VeneraMotion.reduceMotion) return@LaunchedEffect
        recentSearchMoodAtMs = System.currentTimeMillis()
        if (!config.searchCardBuild) return@LaunchedEffect
        paintController.finishAndClear()
        pendingBuildAtMs = System.currentTimeMillis()
        agents.take(config.count).forEachIndexed { idx, p ->
            p.paintTargetKey = null
            p.stackHostId = -1
            p.hammerTimerMs = 0f
            p.wall = BuddyWall.SEARCH
            p.mode = BuddyMode.WALK
            p.dir = if (idx % 2 == 0) -1f else 1f
            p.icon = PixelIcon.STAR
            p.iconTimer = 12
        }
    }

    // 监听滚动打断信号：用户开始浏览翻页时立刻收笔回墙、展开剩余卡片
    LaunchedEffect(scrollInterruptTick) {
        if (scrollInterruptTick <= 0) return@LaunchedEffect
        paintController.abortOnScroll(
            activeAgents = agents.take(config.count),
            edgeX = edgeX,
            topY = topY,
            stageH = stageH,
            halfH = halfH,
        )
    }

    // 动态监听搜索完成与卡片就位：任意宠数动态分列踩台阶 + 全列多米诺光流；无结果/失败与跨页旧卡防护
    LaunchedEffect(pendingBuildAtMs, searchState.searching, hasCards, hidden, searchState.ignoredCardKeys.size) {
        if (hidden) return@LaunchedEffect
        val startMs = pendingBuildAtMs
        if (startMs <= 0L || searchState.searching || !config.searchCardBuild || VeneraMotion.reduceMotion) return@LaunchedEffect
        val ready = DesktopPixelBuddyBus.hasUnignoredCards(searchState.ignoredCardKeys)
        if (!ready) {
            delay(260L)
            if (pendingBuildAtMs == startMs &&
                !searchState.searching &&
                !DesktopPixelBuddyBus.hasUnignoredCards(DesktopPixelBuddyBus.searchState.value.ignoredCardKeys)
            ) {
                pendingBuildAtMs = 0L
                DesktopPixelBuddyBus.clearIgnoredCards()
            }
            return@LaunchedEffect
        }
        val elapsedMs = System.currentTimeMillis() - startMs
        if (elapsedMs > 12000L) {
            pendingBuildAtMs = 0L
            DesktopPixelBuddyBus.clearIgnoredCards()
            return@LaunchedEffect
        }
        delay(95L)
        if (searchState.searching || pendingBuildAtMs != startMs) return@LaunchedEffect
        pendingBuildAtMs = 0L
        val localCards = DesktopPixelBuddyBus.snapshotCardRects(
            offsetX = overlayOrigin.x,
            offsetY = overlayOrigin.y,
            excludeKeys = DesktopPixelBuddyBus.searchState.value.ignoredCardKeys,
        )
        DesktopPixelBuddyBus.clearIgnoredCards()
        if (localCards.isEmpty()) return@LaunchedEffect
        paintController.startSession(
            localCards = localCards,
            activeAgents = agents.take(config.count),
            stageW = stageW,
            stageH = stageH,
            edgeX = edgeX,
            topY = topY,
            halfH = halfH,
            density = density.density,
        )
    }

    // 画廊/漫画搜索结束落入空态或失败重试锚点时触发小情绪
    LaunchedEffect(emptyAnchor, retryAnchor, searchState.searching, hidden) {
        if (hidden || searchState.searching || paintController.active || VeneraMotion.reduceMotion) return@LaunchedEffect
        if (System.currentTimeMillis() - recentSearchMoodAtMs > 15000L) return@LaunchedEffect
        val active = agents.take(config.count)
        when {
            retryAnchor != null -> {
                pendingBuildAtMs = 0L
                recentSearchMoodAtMs = 0L
                DesktopPixelBuddyBus.clearIgnoredCards()
                reactSearchMood(active, isFailure = true, retryAnchor?.toLocal(), stageW, stageH, edgeX, topY, halfH, density.density)
            }
            emptyAnchor != null -> {
                pendingBuildAtMs = 0L
                recentSearchMoodAtMs = 0L
                DesktopPixelBuddyBus.clearIgnoredCards()
                reactSearchMood(active, isFailure = false, emptyAnchor?.toLocal(), stageW, stageH, edgeX, topY, halfH, density.density)
            }
        }
    }

    // 联动业务总线事件（收藏/取消、切源、翻页、下载搬砖与奖励、列表底、深夜睡帽、回窗、搜空、失败）
    LaunchedEffect(petCueEvent?.serial) {
        val ev = petCueEvent ?: return@LaunchedEffect
        if (hidden || VeneraMotion.reduceMotion) return@LaunchedEffect
        if (ev.cue in setOf(DesktopPetCue.SEARCH_EMPTY, DesktopPetCue.COLLECTION_EMPTY, DesktopPetCue.LOAD_FAILED, DesktopPetCue.ERROR, DesktopPetCue.OFFLINE, DesktopPetCue.BLOCKED)) {
            pendingBuildAtMs = 0L
            DesktopPixelBuddyBus.clearIgnoredCards()
        }
        handleDesktopPetCue(
            cue = ev.cue,
            active = agents.take(config.count),
            paintActive = paintController.active,
            emptyAnchor = emptyAnchor?.toLocal(),
            retryAnchor = retryAnchor?.toLocal(),
            stageW = stageW,
            stageH = stageH,
            edgeX = edgeX,
            topY = topY,
            halfH = halfH,
            density = density.density,
            onSpawnRewardFood = { food = it },
            wandActive = wandMode,
            nowMs = System.currentTimeMillis(),
            lastLightCueAtMs = lastLightCueAtMs,
            onUpdateLightCueAtMs = { lastLightCueAtMs = it },
            onSetLateNight = { lateNightMode = it },
        )
        DesktopPixelBuddyBus.requestWake()
    }

    // 响应设置页快捷动作按钮（含补全的叠罗汉 STACK）
    LaunchedEffect(commandPair.first) {
        if (hidden || commandPair.first <= 0) return@LaunchedEffect
        val active = agents.take(config.count)
        when (commandPair.second) {
            PixelBuddyCommand.FEED -> {
                food = BuddyFood(edgeX + 210f * density.density, topY + 36f * density.density)
                active.forEach { it.stackHostId = -1; it.mode = BuddyMode.RUNBACK; it.wall = BuddyWall.FLOOR }
            }
            PixelBuddyCommand.POOP -> if (config.poopEnabled) {
                val p = active.random()
                p.mode = BuddyMode.HOP; p.hopPhase = 0f
                poops.add(BuddyPoop(p.x, p.y + 8f))
                if (poops.size > 10) poops.removeAt(0)
            }
            PixelBuddyCommand.GREET -> if (active.size >= 2) {
                active[0].icon = PixelIcon.HEART; active[0].iconTimer = 14
                active[1].icon = PixelIcon.HEART; active[1].iconTimer = 14
            }
            PixelBuddyCommand.STACK -> if (active.size >= 2) {
                active[0].icon = PixelIcon.STAR; active[0].iconTimer = 14
                active[1].stackHostId = active[0].id
                active[1].stackTimerMs = 5500f
                active[1].mode = BuddyMode.WALK
            }
            else -> Unit
        }
    }

    // 关闭逗猫棒模式时，正在追棒子的小家伙跳回侧栏
    LaunchedEffect(wandMode) {
        if (!wandMode) {
            agents.take(config.count).forEachIndexed { idx, p ->
                if (p.mode == BuddyMode.CHASE_WAND || p.wall == BuddyWall.FLOOR) {
                    launchAgentJump(p, edgeX + halfH, (topY + 140f + idx * 56f).coerceAtMost(stageH - 70f), BuddyWall.SIDE)
                }
            }
        }
    }

    if (hidden) return

    val wandPos = if (wandMode && !hasCustomWandCursor) wandPosInWindow?.let { it - overlayOrigin } else null

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() },
    ) {
        stageW = with(density) { maxWidth.toPx() }
        stageH = with(density) { maxHeight.toPx() }
        val floorY = (stageH - halfH - 6f).coerceAtLeast(topY + 40f)

        // 窗口失焦即挂起循环（0 CPU/GPU 唤醒）；聚焦时按互动/行走/静止分级节拍驱动，静止仅在帧相切换时重绘。
        val windowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(stageW, stageH, edgeX, topY, config.count, config.cardClimb, hidden, wandMode, windowFocused) {
            if (!windowFocused) return@LaunchedEffect
            var lastNs = System.nanoTime()
            var tickAccumMs = 0f
            var prevDelayMs = 20L
            val lookupLocalCard: (String) -> Rect? = { key ->
                DesktopPixelBuddyBus.cardRectOf(key)?.translate(-overlayOrigin.x, -overlayOrigin.y)
            }
            val visitLocalCards: ((String, Rect) -> Unit) -> Unit = { action ->
                DesktopPixelBuddyBus.forEachCardRect(overlayOrigin.x, overlayOrigin.y, action)
            }
            while (true) {
                val active = agents.take(config.count)
                val nextDelayMs = when {
                    paintController.active || wandMode || food != null || poops.any { !it.landed } || active.any {
                        it.mode in HIGH_DYNAMIC_BUDDY_MODES ||
                            it.squashMs > 0f || it.turnMs > 0f || it.hammerTimerMs > 0f || it.returnDelayMs > 0f
                    } -> 20L
                    active.any {
                        it.mode == BuddyMode.WALK ||
                            it.mode == BuddyMode.PETTING ||
                            it.stackHostId >= 0 ||
                            it.wall == BuddyWall.FLOOR
                    } -> 80L
                    else -> 250L
                }
                val woken = withTimeoutOrNull(nextDelayMs) {
                    DesktopPixelBuddyBus.wakeChannel.receive()
                    true
                } ?: false
                val nowNs = System.nanoTime()
                val rawDtMs = ((nowNs - lastNs) / 1_000_000f).coerceAtLeast(1f)
                lastNs = nowNs
                val dtMs = if (prevDelayMs >= 200L) 16.667f else rawDtMs.coerceIn(1f, 95f)
                prevDelayMs = nextDelayMs
                val dt = dtMs / 16.667f
                tickAccumMs += rawDtMs
                val isTick = tickAccumMs >= 100f
                val prevAnim = animTick
                if (isTick) {
                    tickAccumMs = (tickAccumMs - 100f).coerceAtMost(99f)
                    animTick++
                }
                val idleVisualChanged = isTick && (
                    active.any { it.iconTimer > 0 } ||
                        (animTick % 30 < 2) != (prevAnim % 30 < 2) ||
                        (animTick % 16 in 6..9) != (prevAnim % 16 in 6..9)
                    )
                if (nextDelayMs < 200L || idleVisualChanged || woken) {
                    frameTick++
                }

                food?.let { f ->
                    f.vy += 0.45f * dt; f.y += f.vy * dt
                    if (f.y >= floorY) { f.y = floorY; f.vy = 0f }
                }
                for (pp in poops) {
                    if (pp.landed) continue
                    pp.vy += 0.4f * dt; pp.y += pp.vy * dt
                    if (pp.y >= floorY + 4f) { pp.y = floorY + 4f; pp.vy = 0f; pp.landed = true }
                }

                paintController.stepFrame(
                    dtMs = dtMs,
                    activeAgents = active,
                    stageW = stageW,
                    stageH = stageH,
                    edgeX = edgeX,
                    topY = topY,
                    halfH = halfH,
                    density = density.density,
                    cardLookup = lookupLocalCard,
                )

                val searchRect = searchState.boundsInWindow?.toLocal()
                val livePointer = DesktopPixelBuddyBus.latestPointerInWindow?.let { it - overlayOrigin }
                for (pIdx in active.indices) {
                    if (wandMode && !paintController.active) {
                        stepWandChasePhysics(
                            p = active[pIdx],
                            pIdx = pIdx,
                            dtMs = dtMs,
                            isTick = isTick,
                            wandPos = livePointer,
                            edgeX = edgeX,
                            topY = topY,
                            stageW = stageW,
                            halfH = halfH,
                            floorY = floorY,
                            density = density.density,
                            cardLookup = lookupLocalCard,
                            visitCards = visitLocalCards,
                        )
                    } else {
                        stepRegularBuddyPhysics(
                            p = active[pIdx],
                            pIdx = pIdx,
                            allAgents = active,
                            dtMs = dtMs,
                            isTick = isTick,
                            stageW = stageW,
                            stageH = stageH,
                            edgeX = edgeX,
                            topY = topY,
                            halfH = halfH,
                            floorY = floorY,
                            density = density.density,
                            petSpacingPx = petSpacingPx,
                            cardClimb = config.cardClimb,
                            searching = searchState.searching,
                            searchRect = searchRect,
                            food = food,
                            onConsumeFood = { food = null },
                            lateNightMode = lateNightMode,
                            cardLookup = lookupLocalCard,
                            visitCards = visitLocalCards,
                        )
                    }
                }
                if (!wandMode && !paintController.active) {
                    stepCuriousLookAtCursor(active, livePointer, density.density)
                    if (isTick) stepBuddySocialInteractions(active, density.density)
                }
            }
        }

        val active = agents.take(config.count)

        // 顶层透明 Canvas 绘制所有像素元素
        Canvas(modifier = Modifier.fillMaxSize()) {
            val tickRead = frameTick
            val animRead = animTick
            if (tickRead < 0) return@Canvas
            drawPixelBuddyStage(
                active = active,
                poops = poops,
                food = food,
                paintController = paintController,
                showTypingSpell = config.searchTypingSpell && searchState.focused,
                query = searchState.query,
                searchRect = searchState.boundsInWindow?.toLocal(),
                wandActive = wandMode,
                wandPos = wandPos,
                edgeX = edgeX,
                topY = topY,
                halfH = halfH,
                cellPx = cellPx,
                density = density.density,
                animRead = animRead,
                lateNight = lateNightMode,
            )
        }

        // 宠物透明点击/长按顺毛/拖拽/右键热区（用 graphicsLayer 平移避免 60fps 触发布局重排）
        for (p in active) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer {
                        val tickRead = frameTick
                        if (tickRead < 0) return@graphicsLayer
                        val (hx, hy) = buddyVisualPose(p, edgeX, topY, halfH, density.density)
                        translationX = hx - petHitHalfPx
                        translationY = hy - petHitHalfPx
                    }
                    .pixelBuddyInteractiveGestures(
                        p = p,
                        edgeX = edgeX,
                        topY = topY,
                        stageWProvider = { stageW },
                        stageHProvider = { stageH },
                        onOpenQuickMenu = { agent, pos -> quickMenuState = agent to pos },
                    ),
            )
        }

        // 像素便便点击铲屎热区（graphicsLayer 跟随同步坐标，零 Layout 开销）
        for (pp in poops.toList()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        val tickRead = frameTick
                        if (tickRead < 0) return@graphicsLayer
                        translationX = pp.x - poopHitHalfPx
                        translationY = pp.y - poopHitHalfPx
                    }
                    .pointerInput(pp) {
                        detectTapGestures {
                            poops.remove(pp)
                            active.firstOrNull()?.let { it.icon = PixelIcon.HEART; it.iconTimer = 12 }
                            DesktopPixelBuddyBus.requestWake()
                        }
                    },
            )
        }
        // 右键快捷互动胶囊浮层
        quickMenuState?.let { (target, anchor) ->
            DesktopPixelBuddyQuickMenu(
                anchor = anchor,
                target = target,
                activeCount = config.count,
                hasPoop = poops.isNotEmpty(),
                wandActive = wandMode,
                stageW = stageW,
                stageH = stageH,
                onClearPoops = {
                    poops.clear()
                    active.firstOrNull()?.let { it.icon = PixelIcon.HEART; it.iconTimer = 12 }
                },
                onDismiss = { quickMenuState = null },
            )
        }
    }
}

private val HIGH_DYNAMIC_BUDDY_MODES = setOf(
    BuddyMode.DRAG,
    BuddyMode.FALL,
    BuddyMode.JUMP_TO,
    BuddyMode.LEAP,
    BuddyMode.SKIM,
    BuddyMode.RUNBACK,
    BuddyMode.HOP,
    BuddyMode.PEEK_IN,
    BuddyMode.CHASE_WAND,
)



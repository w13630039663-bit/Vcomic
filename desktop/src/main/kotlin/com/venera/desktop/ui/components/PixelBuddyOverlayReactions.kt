package com.venera.desktop.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.venera.desktop.settings.PixelBuddySkin
import com.venera.desktop.ui.components.PixelBuddySprites.drawPixelMatrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 发起指定目标与落定后态的抛物线跳跃（用于搜空打盹、失败举锤或归位侧栏）。
 */
internal fun launchAgentJump(
    p: BuddyAgent,
    tx: Float,
    ty: Float,
    targetWall: BuddyWall,
    targetCard: String? = null,
    postMode: BuddyMode? = null,
    postIcon: PixelIcon? = null,
    postHammerMs: Float = 0f,
) {
    p.stackHostId = -1
    p.returnDelayMs = 0f
    p.floorStayMs = 0f
    p.jumpSx = p.x
    p.jumpSy = p.y
    p.jumpEx = tx
    p.jumpEy = ty
    p.jumpT = 0f
    p.jumpTargetWall = targetWall
    p.jumpTargetCard = targetCard
    p.jumpPostMode = postMode
    p.jumpPostIcon = postIcon
    p.jumpPostHammerMs = postHammerMs
    p.dir = if (tx >= p.x) 1f else -1f
    p.mode = BuddyMode.JUMP_TO
}

/**
 * 让闲置宠物前往空结果区旁打盹（ZZZ），或前往重试按钮旁举小锤敲两下提醒。
 */
internal fun reactSearchMood(
    active: List<BuddyAgent>,
    isFailure: Boolean,
    anchor: Rect?,
    stageW: Float,
    stageH: Float,
    edgeX: Float,
    topY: Float,
    halfH: Float,
    density: Float,
) {
    val actor = active.firstOrNull { it.mode !in setOf(BuddyMode.LEAP, BuddyMode.SKIM, BuddyMode.DRAG) } ?: return
    val tx: Float
    val ty: Float
    if (anchor != null) {
        tx = if (isFailure) {
            (anchor.right + 16f * density).coerceIn(edgeX + 24f * density, stageW - 28f * density)
        } else {
            (anchor.left + (anchor.right - anchor.left) * 0.35f).coerceIn(edgeX + 24f * density, stageW - 28f * density)
        }
        ty = (anchor.top - halfH).coerceIn(topY + 24f * density, stageH - 36f * density)
    } else {
        tx = (edgeX + (stageW - edgeX) * (if (isFailure) 0.54f else 0.44f)).coerceIn(edgeX + 30f * density, stageW - 30f * density)
        ty = (topY + (stageH - topY) * 0.56f).coerceIn(topY + 30f * density, stageH - 40f * density)
    }
    if (isFailure) {
        launchAgentJump(
            p = actor,
            tx = tx,
            ty = ty,
            targetWall = BuddyWall.FLOOR,
            postMode = BuddyMode.HOP,
            postIcon = PixelIcon.HAMMER,
            postHammerMs = 1700f,
        )
    } else {
        launchAgentJump(
            p = actor,
            tx = tx,
            ty = ty,
            targetWall = BuddyWall.FLOOR,
            postMode = BuddyMode.SLEEP,
            postIcon = PixelIcon.ZZZ,
        )
    }
}

/**
 * 响应业务总线事件（收藏、取消收藏、切换站源、筛选、翻页/点开、下载搬砖与全完奖励、列表底、深夜睡帽、回窗迎接、搜空与加载失败）。
 */
internal fun handleDesktopPetCue(
    cue: DesktopPetCue,
    active: List<BuddyAgent>,
    paintActive: Boolean,
    emptyAnchor: Rect?,
    retryAnchor: Rect?,
    stageW: Float,
    stageH: Float,
    edgeX: Float,
    topY: Float,
    halfH: Float,
    density: Float,
    onSpawnRewardFood: (BuddyFood) -> Unit,
    wandActive: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
    lastLightCueAtMs: Long = 0L,
    onUpdateLightCueAtMs: (Long) -> Unit = {},
    onSetLateNight: (Boolean) -> Unit = {},
) {
    if (active.isEmpty()) return
    val busyProtect = paintActive || wandActive
    val busyModes = setOf(BuddyMode.LEAP, BuddyMode.SKIM, BuddyMode.DRAG, BuddyMode.CHASE_WAND)

    when (cue) {
        DesktopPetCue.TOGGLE_FAVORITE, DesktopPetCue.ADD_BOOKSHELF -> {
            if (busyProtect) return
            val p = active.firstOrNull { it.mode !in busyModes } ?: return
            p.mode = BuddyMode.HOP; p.hopPhase = 0f
            p.icon = PixelIcon.HEART; p.iconTimer = 16
        }
        DesktopPetCue.REMOVE_FAVORITE -> {
            if (busyProtect || nowMs - lastLightCueAtMs < 1500L) return
            onUpdateLightCueAtMs(nowMs)
            val p = active.firstOrNull { it.mode !in busyModes } ?: return
            p.icon = PixelIcon.SWEAT; p.iconTimer = 14
        }
        DesktopPetCue.SWITCH_SOURCE, DesktopPetCue.FILTER -> {
            if (busyProtect) return
            active.forEachIndexed { i, p ->
                if (p.mode !in busyModes) {
                    p.icon = PixelIcon.STAR; p.iconTimer = 12
                    if (i == 0) { p.mode = BuddyMode.HOP; p.hopPhase = 0f }
                }
            }
        }
        DesktopPetCue.DOWNLOADING -> {
            if (busyProtect) return
            val worker = active.lastOrNull { it.mode !in busyModes } ?: return
            worker.workBoxTimerMs = 20_000f
            worker.workBoxBeatMs = 420f
            worker.icon = PixelIcon.BOX; worker.iconTimer = 12
            if (worker.wall != BuddyWall.SIDE && worker.mode != BuddyMode.JUMP_TO) {
                launchAgentJump(worker, edgeX + halfH, (stageH - 120f * density).coerceAtLeast(topY + 80f), BuddyWall.SIDE, postMode = BuddyMode.IDLE, postIcon = PixelIcon.BOX)
            }
        }
        DesktopPetCue.DOWNLOAD_ALL_DONE -> {
            active.forEach { it.workBoxTimerMs = 0f; it.workBoxBeatMs = 0f }
            if (busyProtect) return
            onSpawnRewardFood(BuddyFood(edgeX + 210f * density, topY + 36f * density))
            active.forEach { p ->
                if (p.mode !in setOf(BuddyMode.LEAP, BuddyMode.SKIM, BuddyMode.DRAG)) {
                    p.stackHostId = -1
                    p.mode = BuddyMode.RUNBACK
                    p.wall = BuddyWall.FLOOR
                }
            }
        }
        DesktopPetCue.LATE_NIGHT -> {
            onSetLateNight(true)
            if (busyProtect) return
            val sleeper = active.firstOrNull { it.mode !in busyModes } ?: return
            sleeper.mode = BuddyMode.SLEEP; sleeper.timer = 60
            sleeper.icon = PixelIcon.ZZZ; sleeper.iconTimer = 14
        }
        DesktopPetCue.WINDOW_RETURN, DesktopPetCue.LONG_TIME_NO_SEE -> {
            if (busyProtect) return
            active.forEach { p ->
                if (p.mode !in busyModes && p.mode != BuddyMode.JUMP_TO) {
                    p.mode = BuddyMode.HOP; p.hopPhase = 0f
                    p.icon = PixelIcon.HEART; p.iconTimer = 16
                }
            }
        }
        DesktopPetCue.LIST_END -> {
            if (busyProtect || nowMs - lastLightCueAtMs < 1500L) return
            onUpdateLightCueAtMs(nowMs)
            val watcher = active.firstOrNull { it.mode !in busyModes && it.wall != BuddyWall.SEARCH } ?: return
            if (watcher.mode in setOf(BuddyMode.IDLE, BuddyMode.WALK)) {
                watcher.mode = BuddyMode.PEEK_EDGE; watcher.timer = 18
                watcher.icon = PixelIcon.STAR; watcher.iconTimer = 12
            }
        }
        DesktopPetCue.SWITCH_PAGE, DesktopPetCue.OPEN_WORK, DesktopPetCue.OPEN_IMAGE, DesktopPetCue.LOADING -> {
            if (busyProtect || nowMs - lastLightCueAtMs < 1500L) return
            onUpdateLightCueAtMs(nowMs)
            val cheer = active.firstOrNull { it.mode !in busyModes } ?: return
            cheer.icon = PixelIcon.STAR; cheer.iconTimer = 10
            if (cheer.mode == BuddyMode.IDLE) {
                cheer.mode = BuddyMode.STRETCH; cheer.timer = 8
            }
        }
        DesktopPetCue.SEARCH_EMPTY, DesktopPetCue.COLLECTION_EMPTY -> {
            if (!busyProtect) reactSearchMood(active, isFailure = false, emptyAnchor, stageW, stageH, edgeX, topY, halfH, density)
        }
        DesktopPetCue.LOAD_FAILED, DesktopPetCue.ERROR, DesktopPetCue.OFFLINE, DesktopPetCue.BLOCKED -> {
            if (!busyProtect) reactSearchMood(active, isFailure = true, retryAnchor, stageW, stageH, edgeX, topY, halfH, density)
        }
        else -> Unit
    }
}

/** 绘制像素伙伴画布上的拼字、道具、逗猫棒与全部角色本体（深夜模式附带极简像素小睡帽）。 */
internal fun DrawScope.drawPixelBuddyStage(
    active: List<BuddyAgent>,
    poops: List<BuddyPoop>,
    food: BuddyFood?,
    paintController: PixelBuddyCardPaintController,
    showTypingSpell: Boolean,
    query: String,
    searchRect: Rect?,
    wandActive: Boolean,
    wandPos: Offset?,
    edgeX: Float,
    topY: Float,
    halfH: Float,
    cellPx: Float,
    density: Float,
    animRead: Int,
    lateNight: Boolean = false,
) {
    with(paintController) { drawPaintEffects(cellPx) }

    if (showTypingSpell && query.isNotBlank() && searchRect != null) {
        val chars = query.takeLast(8).uppercase()
        var cx = searchRect.left + 8f * density
        val by = searchRect.top - 14f * density
        chars.forEachIndexed { idx, ch ->
            val glyph = PixelBuddySprites.pixelFont[ch]
            if (glyph != null) {
                val col = if (idx % 2 == 0) Color(0xFF947FD6) else Color(0xFFD97757)
                for (gy in glyph.indices) {
                    for (gx in glyph[gy].indices) {
                        if (glyph[gy][gx] == '#') drawRect(col, Offset(cx + gx * cellPx, by + gy * cellPx), Size(cellPx, cellPx))
                    }
                }
                cx += 4f * cellPx + 2f * density
            }
        }
    }

    val poopPal = PixelBuddySprites.paletteOf(PixelBuddySkin.CAT)
    for (pp in poops) drawPixelMatrix(PixelBuddySprites.iconRows(PixelIcon.POOP), poopPal, pp.x, pp.y, cellPx)
    food?.let { f -> drawPixelMatrix(PixelBuddySprites.iconRows(PixelIcon.FISH), poopPal, f.x, f.y, cellPx) }
    if (wandActive && wandPos != null) {
        drawPixelWand(wandPos, animRead.toFloat(), cellPx)
    }

    for (p in active) {
        val pal = PixelBuddySprites.paletteOf(p.skin)
        val pose = when (p.mode) {
            BuddyMode.SLEEP -> PixelPose.SLEEP
            BuddyMode.DRAG, BuddyMode.FALL -> PixelPose.DANGLE
            BuddyMode.DIZZY -> PixelPose.DIZZY
            BuddyMode.STRETCH -> PixelPose.STRETCH
            BuddyMode.PEEK_EDGE -> PixelPose.PEEK_DOWN
            BuddyMode.GREET, BuddyMode.HOP, BuddyMode.LEAP, BuddyMode.PETTING -> PixelPose.HAPPY
            BuddyMode.WALK, BuddyMode.RUNBACK, BuddyMode.SKIM, BuddyMode.PEEK_IN, BuddyMode.CHASE_WAND ->
                if (animRead % 4 < 2) PixelPose.WALK1 else PixelPose.WALK2
            else -> when {
                animRead % 30 < 2 -> PixelPose.BLINK
                animRead % 16 in 6..9 -> PixelPose.TAIL_WAG
                else -> PixelPose.IDLE
            }
        }
        val (cx, cy) = buddyVisualPose(p, edgeX, topY, halfH, density)
        val rot = buddyRotationDeg(p)
        val flip = if (p.wall == BuddyWall.TOP && rot >= 135f) p.dir > 0f else p.dir < 0f
        val (sx, sy) = buddySquashScale(p)
        drawPixelMatrix(PixelBuddySprites.framesOf(p.skin, pose), pal, cx, cy, cellPx, rot, flip, sx, sy)
        if (lateNight && pose !in setOf(PixelPose.DANGLE, PixelPose.DIZZY, PixelPose.PEEK_DOWN)) {
            val rad = rot * PI / 180.0
            val capLift = 5.6f * cellPx * sy
            val capOx = (capLift * sin(rad)).toFloat()
            val capOy = (-capLift * cos(rad)).toFloat()
            drawPixelMatrix(PixelBuddySprites.nightCapRows(), pal, cx + capOx, cy + capOy, cellPx, rot, flip, sx, sy)
        }
        if (p.iconTimer > 0 && p.icon != null) {
            val rise = (14 - p.iconTimer) * 0.85f * density
            drawPixelMatrix(PixelBuddySprites.iconRows(p.icon!!), pal, cx + 12f * density, cy - 13f * density - rise, cellPx)
        }
    }
}


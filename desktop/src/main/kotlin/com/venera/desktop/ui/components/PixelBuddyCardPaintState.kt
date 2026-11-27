package com.venera.desktop.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import com.venera.desktop.settings.DesktopPixelBuddyBus
import com.venera.desktop.settings.PixelBuddyCardPaintSnapshot
import com.venera.desktop.settings.PixelBuddyCardRevealPhase
import com.venera.desktop.settings.PixelBuddySkin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * 分列踩台阶与全列多米诺光流描绘控制器。
 *
 * 独立于 Overlay 维护按列聚类、不限宠物数量的动态抢单、真实毫秒跨跳与顶边实时擦亮、
 * 向下淌落的像素光流以及滚动即时避让。
 */
internal class PixelBuddyCardPaintController {

    var sessionId: Int = 0
        private set
    var active: Boolean = false
        private set

    private var sessionElapsedMs: Float = 0f
    private var idleElapsedMs: Float = 0f
    private var abortElapsedMs: Float = -1f
    private val columns = mutableListOf<PaintColumn>()
    private val seedsByKey = mutableMapOf<String, PaintCardSeed>()
    private val allSeeds = mutableListOf<PaintCardSeed>()
    private val phases = mutableMapOf<String, PixelBuddyCardRevealPhase>()
    private val beams = mutableListOf<ColumnLightBeam>()
    private val particles = PixelBuddyPaintParticles()

    /** 启动一次新的分列踩台阶与全列光流描绘会话。 */
    fun startSession(
        localCards: Map<String, Rect>,
        activeAgents: List<BuddyAgent>,
        stageW: Float,
        stageH: Float,
        edgeX: Float,
        topY: Float,
        halfH: Float,
        density: Float,
    ) {
        if (localCards.isEmpty() || activeAgents.isEmpty()) return
        sessionId++
        active = true
        sessionElapsedMs = 0f
        idleElapsedMs = 0f
        abortElapsedMs = -1f
        columns.clear()
        seedsByKey.clear()
        allSeeds.clear()
        phases.clear()
        beams.clear()

        val colClusterThreshold = 42f * density
        val sortedByTop = localCards.entries
            .filter { it.value.top >= topY - 24f }
            .sortedBy { it.value.top }
        if (sortedByTop.isEmpty()) {
            active = false
            return
        }

        val tempCols = mutableListOf<Pair<Float, MutableList<Pair<String, Rect>>>>()
        for ((key, rect) in sortedByTop) {
            val cx = (rect.left + rect.right) * 0.5f
            val hit = tempCols.firstOrNull { abs(it.first - cx) < colClusterThreshold }
            if (hit != null) hit.second.add(key to rect)
            else tempCols.add(cx to mutableListOf(key to rect))
        }
        tempCols.sortBy { it.first }

        val viewportLimit = topY + (stageH - topY) * 0.78f
        val defaultAccent = PixelBuddySprites.paletteOf(activeAgents.first().skin).body.toArgb().toLong() and 0xFFFFFFFFL
        tempCols.forEachIndexed { colIdx, (colCx, rawList) ->
            val col = PaintColumn(colCx)
            rawList.sortedBy { it.second.top }.forEachIndexed { idxInCol, (key, rect) ->
                val node = PaintCardSeed(key = key, colIdx = colIdx, rect = rect)
                allSeeds.add(node)
                seedsByKey[key] = node
                phases[key] = PixelBuddyCardRevealPhase.Waiting(defaultAccent)
                if (idxInCol == 0 || (idxInCol == 1 && rect.top < viewportLimit)) col.seeds.add(node)
                else col.tail.add(node)
            }
            columns.add(col)
        }

        publishSnapshot()
        activeAgents.forEachIndexed { idx, p ->
            p.wall = BuddyWall.CARD
            p.paintColIdx = -1
            val next = pickNextSeed(p, idx, activeAgents.size, edgeX, stageW)
            if (next != null) startLeap(p, next, halfH, density)
            else finishPet(p, idx, activeAgents, edgeX, topY, stageH, halfH)
        }
    }

    /** 推进单帧毫秒级粒子、列光流与踩台阶状态机。 */
    fun stepFrame(
        dtMs: Float,
        activeAgents: List<BuddyAgent>,
        stageW: Float,
        stageH: Float,
        edgeX: Float,
        topY: Float,
        halfH: Float,
        density: Float,
        localCardRects: Map<String, Rect> = emptyMap(),
        cardLookup: (String) -> Rect? = { localCardRects[it] },
    ) {
        val dt = dtMs / 16.667f
        particles.step(dt)
        var snapshotDirty = false

        // 1. 推进各列像素多米诺光流（恒定每秒 360dp）
        val beamSpeedPx = 360f * density * (dtMs / 1000f)
        for (i in beams.lastIndex downTo 0) {
            val bm = beams[i]
            bm.y += beamSpeedPx
            particles.addTrail(bm.colCx, bm.y, bm.color, life = 18f, vertical = true)
            while (bm.queue.isNotEmpty()) {
                val nextC = bm.queue.first()
                val liveRect = cardLookup(nextC.key) ?: nextC.rect
                if (bm.y >= liveRect.top + 12f * density) {
                    bm.queue.removeAt(0)
                    nextC.rect = liveRect
                    if (wakeNode(nextC, bm.color)) snapshotDirty = true
                } else break
            }
            if (bm.queue.isEmpty() && bm.y >= bm.targetY) beams.removeAt(i)
        }

        // 2. 推进宠物踩台阶（跳跃 -> 顶边向右实时擦除暗幕 -> 收尾碰头庆祝）
        if (active) {
            val paceMult = if (activeAgents.size == 1) 1.12f else 1f
            val padX = 12f * density
            val arcLift = 30f * density
            for (pIdx in activeAgents.indices) {
                val p = activeAgents[pIdx]
                if (p.returnDelayMs > 0f) {
                    p.returnDelayMs -= dtMs
                    if (p.returnDelayMs <= 0f) {
                        p.returnDelayMs = 0f
                        launchReturnJump(p, p.returnIdx, edgeX, topY, stageH, halfH)
                    }
                    continue
                }
                val seed = p.paintTargetKey?.let { seedsByKey[it] }
                val skinColor = PixelBuddySprites.paletteOf(p.skin).body
                when (p.mode) {
                    BuddyMode.LEAP -> {
                        if (seed == null) {
                            finishPet(p, pIdx, activeAgents, edgeX, topY, stageH, halfH)
                            continue
                        }
                        val liveRect = cardLookup(seed.key) ?: seed.rect
                        seed.rect = liveRect
                        p.jumpEx = liveRect.left + padX
                        p.jumpEy = liveRect.top - halfH
                        p.paintElapsedMs += dtMs * paceMult
                        val t = (p.paintElapsedMs / p.paintDurationMs).coerceIn(0f, 1f)
                        p.x = p.jumpSx + (p.jumpEx - p.jumpSx) * t
                        p.y = p.jumpSy + (p.jumpEy - p.jumpSy) * t - sin(t * PI).toFloat() * arcLift
                        p.dir = if (p.jumpEx >= p.jumpSx) 1f else -1f
                        if (Random.nextFloat() < 0.45f * dt) particles.addTrail(p.x, p.y + 6f * density, skinColor)
                        if (t >= 1f) {
                            val cardW = (liveRect.right - liveRect.left).coerceAtLeast(100f * density)
                            p.mode = BuddyMode.SKIM
                            p.paintElapsedMs = 0f
                            p.paintDurationMs = (220f + (cardW / density) * 0.42f).coerceIn(290f, 440f)
                            p.dir = 1f
                            p.lastSkimReported = -1f
                        }
                    }
                    BuddyMode.SKIM -> {
                        if (seed == null) {
                            finishPet(p, pIdx, activeAgents, edgeX, topY, stageH, halfH)
                            continue
                        }
                        val liveRect = cardLookup(seed.key) ?: seed.rect
                        seed.rect = liveRect
                        val sx = liveRect.left + padX
                        val ex = (liveRect.right - padX).coerceAtLeast(sx + 20f)
                        p.paintElapsedMs += dtMs * paceMult
                        val t = (p.paintElapsedMs / p.paintDurationMs).coerceIn(0f, 1f)
                        val e = 1f - (1f - t).pow(2)
                        p.x = sx + (ex - sx) * e
                        p.y = liveRect.top - halfH
                        p.dir = 1f
                        particles.addTrail(p.x, p.y + 8f * density, skinColor)
                        if (Random.nextFloat() < 0.28f * dt) particles.emitSparks(p.x, p.y + 8f * density, skinColor, 1, density)
                        if (abs(e - p.lastSkimReported) >= 0.015f || t >= 1f) {
                            p.lastSkimReported = e
                            val accentArgb = skinColor.toArgb().toLong() and 0xFFFFFFFFL
                            phases[seed.key] = PixelBuddyCardRevealPhase.Skimming(e, accentArgb)
                            snapshotDirty = true
                        }
                        if (t >= 1f) {
                            seed.done = true
                            if (wakeNode(seed, skinColor)) snapshotDirty = true
                            maybeLaunchColumnBeam(seed.colIdx, skinColor, cardLookup, density)
                            val next = pickNextSeed(p, pIdx, activeAgents.size, edgeX, stageW)
                            if (next != null) startLeap(p, next, halfH, density)
                            else finishPet(p, pIdx, activeAgents, edgeX, topY, stageH, halfH)
                        }
                    }
                    else -> Unit
                }
            }

            sessionElapsedMs += dtMs
            val anyPetWorking = activeAgents.any { it.mode == BuddyMode.LEAP || it.mode == BuddyMode.SKIM }
            if (!anyPetWorking && beams.isEmpty()) {
                idleElapsedMs += dtMs
                if (idleElapsedMs >= 700f) {
                    finishAndClear()
                    return
                }
            } else {
                idleElapsedMs = 0f
                if (sessionElapsedMs >= 7500f) {
                    finishAndClear()
                    return
                }
            }
        } else if (abortElapsedMs >= 0f) {
            abortElapsedMs += dtMs
            if (abortElapsedMs >= 650f) {
                abortElapsedMs = -1f
                finishAndClear()
                return
            }
        }

        if (snapshotDirty) publishSnapshot()
    }

    /** 检测到用户手动滚动时立即中断描绘，宠物跳回侧栏并立刻展开剩余卡片。 */
    fun abortOnScroll(
        activeAgents: List<BuddyAgent>,
        edgeX: Float,
        topY: Float,
        stageH: Float,
        halfH: Float,
    ) {
        if (!active) return
        active = false
        abortElapsedMs = 0f
        beams.clear()
        activeAgents.forEachIndexed { idx, p ->
            if (p.mode == BuddyMode.LEAP || p.mode == BuddyMode.SKIM) {
                p.paintTargetKey = null
                launchReturnJump(p, idx, edgeX, topY, stageH, halfH)
            }
        }
        val fallbackColor = PixelBuddySprites.paletteOf(activeAgents.firstOrNull()?.skin ?: PixelBuddySkin.CAT).body
        var changed = false
        for (node in allSeeds) {
            if (!node.done && phases[node.key] !is PixelBuddyCardRevealPhase.Awakened) {
                if (wakeNode(node, fallbackColor)) changed = true
            }
        }
        if (changed) publishSnapshot()
    }

    /** 清理当前描绘会话并还原总线零遮罩状态。 */
    fun finishAndClear() {
        active = false
        abortElapsedMs = -1f
        beams.clear()
        columns.clear()
        seedsByKey.clear()
        allSeeds.clear()
        phases.clear()
        DesktopPixelBuddyBus.clearCardPaint()
    }

    /** 在顶层画布绘制画笔轨迹、列光流束与像素火花。 */
    fun DrawScope.drawPaintEffects(cellPx: Float) {
        with(particles) { draw(cellPx, beams) }
    }

    private fun pickNextSeed(p: BuddyAgent, pIdx: Int, totalPets: Int, edgeX: Float, stageW: Float): PaintCardSeed? {
        val usableW = (stageW - edgeX).coerceAtLeast(210f)
        val prefX = edgeX + ((pIdx + 0.5f) / totalPets.coerceAtLeast(1)) * usableW
        var best: PaintCardSeed? = null
        var bestScore = Float.MAX_VALUE
        for (col in columns) {
            for (s in col.seeds) {
                if (s.claimed) continue
                val cx = (s.rect.left + s.rect.right) * 0.5f
                var score = s.rect.top * 1.15f + abs(cx - prefX) * 0.65f + hypot(cx - p.x, s.rect.top - p.y) * 0.22f
                if (p.paintColIdx == s.colIdx) score -= 45f
                if (score < bestScore) { bestScore = score; best = s }
            }
        }
        if (best != null) best.claimed = true
        return best
    }

    private fun startLeap(p: BuddyAgent, seed: PaintCardSeed, halfH: Float, density: Float) {
        val padX = 12f * density
        p.paintTargetKey = seed.key
        p.paintColIdx = seed.colIdx
        p.jumpSx = p.x
        p.jumpSy = p.y
        p.jumpEx = seed.rect.left + padX
        p.jumpEy = seed.rect.top - halfH
        val distDp = hypot(p.jumpEx - p.jumpSx, p.jumpEy - p.jumpSy) / density
        p.paintDurationMs = (230f + distDp * 0.42f).coerceIn(270f, 440f)
        p.paintElapsedMs = 0f
        p.dir = if (p.jumpEx >= p.jumpSx) 1f else -1f
        p.mode = BuddyMode.LEAP
    }

    private fun maybeLaunchColumnBeam(colIdx: Int, color: Color, cardLookup: (String) -> Rect?, density: Float) {
        val col = columns.getOrNull(colIdx) ?: return
        if (col.beamLaunched || col.seeds.any { !it.done }) return
        col.beamLaunched = true
        if (col.tail.isEmpty()) return
        val lastSeed = col.seeds.last()
        val startRect = cardLookup(lastSeed.key) ?: lastSeed.rect
        val endRect = cardLookup(col.tail.last().key) ?: col.tail.last().rect
        beams.add(
            ColumnLightBeam(
                colCx = (startRect.left + startRect.right) * 0.5f,
                y = startRect.bottom - 10f * density,
                targetY = endRect.top + 16f * density,
                color = color,
                queue = col.tail.toMutableList(),
            ),
        )
    }

    private fun wakeNode(node: PaintCardSeed, color: Color): Boolean {
        node.done = true
        if (phases[node.key] is PixelBuddyCardRevealPhase.Awakened) return false
        val accentArgb = color.toArgb().toLong() and 0xFFFFFFFFL
        phases[node.key] = PixelBuddyCardRevealPhase.Awakened(sessionId, accentArgb)
        return true
    }

    private fun finishPet(
        p: BuddyAgent,
        pIdx: Int,
        activeAgents: List<BuddyAgent>,
        edgeX: Float,
        topY: Float,
        stageH: Float,
        halfH: Float,
    ) {
        p.paintTargetKey = null
        p.icon = PixelIcon.STAR
        p.iconTimer = 14
        val othersBusy = activeAgents.any { it !== p && (it.mode == BuddyMode.LEAP || it.mode == BuddyMode.SKIM) }
        if (!othersBusy && activeAgents.size >= 2) {
            // 多宠收尾碰头庆祝：在末卡上原地朝伙伴冒星小跳 240ms 后再跳回侧栏
            activeAgents.forEach { mate ->
                mate.icon = PixelIcon.STAR; mate.iconTimer = 14
                if (mate !== p && mate.mode in setOf(BuddyMode.WALK, BuddyMode.IDLE)) {
                    mate.mode = BuddyMode.HOP; mate.hopPhase = 0f
                }
            }
            p.mode = BuddyMode.GREET; p.returnDelayMs = 240f; p.returnIdx = pIdx
            if (beams.isEmpty()) idleElapsedMs = 0f
        } else {
            launchReturnJump(p, pIdx, edgeX, topY, stageH, halfH)
            if (!othersBusy && beams.isEmpty()) idleElapsedMs = 0f
        }
    }

    private fun launchReturnJump(p: BuddyAgent, pIdx: Int, edgeX: Float, topY: Float, stageH: Float, halfH: Float) {
        p.jumpSx = p.x
        p.jumpSy = p.y
        p.jumpEx = edgeX + halfH
        p.jumpEy = (topY + 140f + pIdx * 58f).coerceAtMost(stageH - 70f)
        p.jumpT = 0f
        p.jumpTargetWall = BuddyWall.SIDE
        p.jumpTargetCard = null
        p.dir = if (p.jumpEx >= p.jumpSx) 1f else -1f
        p.mode = BuddyMode.JUMP_TO
    }

    private fun publishSnapshot() {
        DesktopPixelBuddyBus.updateCardPaint(
            PixelBuddyCardPaintSnapshot(sessionId = sessionId, active = true, phases = phases.toMap()),
        )
    }
}

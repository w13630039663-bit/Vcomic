package com.venera.desktop.ui.components

import androidx.compose.ui.geometry.Rect
import com.venera.desktop.settings.PixelBuddySkin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

internal enum class BuddyWall { SIDE, TOP, CARD, SEARCH, FLOOR }
internal enum class BuddyMode {
    IDLE, WALK, HOP, SLEEP, GREET, DRAG, FALL, DIZZY, RUNBACK, JUMP_TO, LEAP, SKIM, PEEK_IN,
    STRETCH, PEEK_EDGE, PETTING, CHASE_WAND,
}

internal class BuddyAgent(
    val id: Int,
    var skin: PixelBuddySkin,
    var wall: BuddyWall,
    var x: Float,
    var y: Float,
    var dir: Float = 1f,
) {
    var mode: BuddyMode = BuddyMode.WALK
    var vx: Float = 0f
    var vy: Float = 0f
    var timer: Int = 25 + Random.nextInt(25)
    var hopPhase: Float = 0f
    var bounceCount: Int = 0
    var icon: PixelIcon? = null
    var iconTimer: Int = 0
    var cardKey: String? = null
    var jumpSx: Float = 0f
    var jumpSy: Float = 0f
    var jumpEx: Float = 0f
    var jumpEy: Float = 0f
    var jumpT: Float = 0f
    var jumpTargetWall: BuddyWall = BuddyWall.SIDE
    var jumpTargetCard: String? = null
    var jumpPostMode: BuddyMode? = null
    var jumpPostIcon: PixelIcon? = null
    var jumpPostHammerMs: Float = 0f
    var paintTargetKey: String? = null
    var paintColIdx: Int = -1
    var paintElapsedMs: Float = 0f
    var paintDurationMs: Float = 320f
    var lastSkimReported: Float = -1f
    var stackHostId: Int = -1
    var stackTimerMs: Float = 0f
    var greetCooldownMs: Float = 0f
    var hammerTimerMs: Float = 0f
    var hammerBeatMs: Float = 0f
    var returnDelayMs: Float = 0f
    var returnIdx: Int = 0
    var peekTargetX: Float = 0f
    var squashMs: Float = 0f
    var turnMs: Float = 0f
    var turnFromDeg: Float = 90f
    var turnToDeg: Float = 180f
    var petHeartAccumMs: Float = 0f
    var floorStayMs: Float = 0f
    var wandPounceCooldownMs: Float = 0f
    var workBoxTimerMs: Float = 0f
    var workBoxBeatMs: Float = 0f
}

internal class BuddyPoop(var x: Float, var y: Float, var vy: Float = -1.5f, var landed: Boolean = false)
internal class BuddyFood(var x: Float, var y: Float, var vy: Float = 0f)

/**
 * 计算像素宠物当前渲染与点击热区的精确中心坐标及姿态角度（画布绘制与点击热区单源共用）。
 */
internal fun buddyVisualPose(
    p: BuddyAgent,
    edgeX: Float,
    topY: Float,
    halfH: Float,
    density: Float,
): Pair<Float, Float> {
    val hopOff = if (p.mode == BuddyMode.HOP) sin(p.hopPhase * PI).toFloat() * 7f * density else 0f
    return when {
        p.mode in setOf(
            BuddyMode.DRAG, BuddyMode.FALL, BuddyMode.DIZZY,
            BuddyMode.RUNBACK, BuddyMode.JUMP_TO, BuddyMode.LEAP, BuddyMode.SKIM, BuddyMode.PEEK_IN,
        ) -> p.x to (p.y - hopOff)
        p.wall == BuddyWall.SIDE -> (edgeX + halfH + hopOff) to p.y
        p.wall == BuddyWall.TOP -> p.x to (topY + halfH + hopOff)
        else -> p.x to (p.y - hopOff)
    }
}

/**
 * 推进单只像素伙伴的常规物理、叠罗汉跟随、探头出场、举锤敲击与自主状态机。
 */
internal fun stepRegularBuddyPhysics(
    p: BuddyAgent,
    pIdx: Int,
    allAgents: List<BuddyAgent>,
    dtMs: Float,
    isTick: Boolean,
    stageW: Float,
    stageH: Float,
    edgeX: Float,
    topY: Float,
    halfH: Float,
    floorY: Float,
    density: Float,
    petSpacingPx: Float,
    cardClimb: Boolean,
    searching: Boolean,
    searchRect: Rect?,
    localCardRects: Map<String, Rect> = emptyMap(),
    food: BuddyFood?,
    onConsumeFood: () -> Unit,
    lateNightMode: Boolean = false,
    cardLookup: (String) -> Rect? = { localCardRects[it] },
    visitCards: ((String, Rect) -> Unit) -> Unit = { action -> localCardRects.forEach { (k, r) -> action(k, r) } },
) {
    val dt = dtMs / 16.667f
    if (p.greetCooldownMs > 0f) p.greetCooldownMs = (p.greetCooldownMs - dtMs).coerceAtLeast(0f)
    if (p.squashMs > 0f) p.squashMs = (p.squashMs - dtMs).coerceAtLeast(0f)
    if (p.turnMs > 0f) p.turnMs = (p.turnMs - dtMs).coerceAtLeast(0f)
    if (isTick && p.iconTimer > 0) p.iconTimer--

    // 长按顺毛挠下巴：持续眯眼开心并定时冒出红心
    if (p.mode == BuddyMode.PETTING) {
        p.petHeartAccumMs += dtMs
        if (p.petHeartAccumMs >= 280f) {
            p.petHeartAccumMs = 0f
            p.icon = PixelIcon.HEART
            p.iconTimer = 10
        }
        return
    }

    // 举小锤子挥动提醒（加载失败时在重试按钮旁敲两下）
    if (p.hammerTimerMs > 0f) {
        p.hammerTimerMs = (p.hammerTimerMs - dtMs).coerceAtLeast(0f)
        p.hammerBeatMs += dtMs
        p.icon = PixelIcon.HAMMER
        p.iconTimer = 4
        if (p.hammerBeatMs >= 320f && p.mode != BuddyMode.JUMP_TO) {
            p.hammerBeatMs = 0f
            p.mode = BuddyMode.HOP
            p.hopPhase = 0f
        }
        if (p.hammerTimerMs <= 0f) {
            p.icon = null
            p.iconTimer = 0
            p.mode = BuddyMode.IDLE
            p.timer = 18
        }
    }

    // 下载队列进行中搬砖陪伴：定期闪烁包裹气泡提醒
    if (p.workBoxTimerMs > 0f) {
        p.workBoxTimerMs = (p.workBoxTimerMs - dtMs).coerceAtLeast(0f)
        p.workBoxBeatMs += dtMs
        if (p.hammerTimerMs <= 0f && p.workBoxBeatMs >= 480f) {
            p.workBoxBeatMs = 0f
            p.icon = PixelIcon.BOX
            p.iconTimer = 5
            if (p.mode == BuddyMode.IDLE && p.wall == BuddyWall.SIDE) {
                p.mode = BuddyMode.HOP
                p.hopPhase = 0f
            }
        }
    }

    if (p.mode == BuddyMode.HOP) {
        p.hopPhase += 0.08f * dt
        if (p.hopPhase >= 1f) {
            p.hopPhase = 0f
            p.mode = BuddyMode.IDLE
            p.timer = 18
        }
    }

    // 叠罗汉跟随：若宿主进入搜索或拖拽等离开态，自动落地解除
    if (p.stackHostId >= 0) {
        val host = allAgents.firstOrNull { it.id == p.stackHostId }
        p.stackTimerMs -= dtMs
        val hostBusy = host == null || host.mode in setOf(
            BuddyMode.DRAG, BuddyMode.FALL, BuddyMode.LEAP, BuddyMode.SKIM, BuddyMode.JUMP_TO,
        ) || host.wall == BuddyWall.SEARCH
        if (p.stackTimerMs <= 0f || hostBusy) {
            p.stackHostId = -1
            p.mode = BuddyMode.HOP
            p.hopPhase = 0f
            p.wall = host?.wall ?: BuddyWall.SIDE
            if (p.wall == BuddyWall.SIDE) p.x = edgeX else if (p.wall == BuddyWall.TOP) p.y = topY
        } else if (p.mode != BuddyMode.HOP && p.mode != BuddyMode.DRAG) {
            p.wall = host.wall
            p.dir = host.dir
            when (host.wall) {
                BuddyWall.SIDE -> { p.x = edgeX + halfH * 2.1f; p.y = host.y }
                BuddyWall.TOP -> { p.x = host.x; p.y = topY + halfH * 2.2f }
                else -> { p.x = host.x; p.y = host.y - halfH * 1.9f }
            }
            return
        }
    }

    // 若站立的卡片或搜索框已消失（包括正在发呆/打盹/小跳时），立刻脱离不卡半空
    if (p.mode !in setOf(BuddyMode.DRAG, BuddyMode.FALL, BuddyMode.JUMP_TO, BuddyMode.LEAP, BuddyMode.SKIM, BuddyMode.PEEK_IN, BuddyMode.RUNBACK)) {
        if (p.wall == BuddyWall.CARD && p.cardKey?.let { cardLookup(it) } == null) {
            p.mode = BuddyMode.FALL; p.vy = 0f; p.vx = 0f; p.cardKey = null
        } else if (p.wall == BuddyWall.SEARCH && searchRect == null) {
            val sideY = (topY + 140f * density + pIdx * petSpacingPx).coerceAtMost((stageH - 64f * density).coerceAtLeast(topY + 50f))
            launchAgentJump(p, edgeX + halfH, sideY, BuddyWall.SIDE, postMode = BuddyMode.WALK)
            return
        }
    }

    // 停在空白/失败临时落地位置（FLOOR）且无小鱼干时，播完情绪短驻后自动跳回侧栏
    if (p.wall == BuddyWall.FLOOR &&
        food == null &&
        p.hammerTimerMs <= 0f &&
        p.returnDelayMs <= 0f &&
        p.mode in setOf(BuddyMode.IDLE, BuddyMode.WALK, BuddyMode.SLEEP, BuddyMode.STRETCH, BuddyMode.PEEK_EDGE, BuddyMode.HOP)
    ) {
        p.floorStayMs += dtMs
        val stayLimitMs = if (p.mode == BuddyMode.SLEEP) 3210f else 1500f
        if (p.floorStayMs >= stayLimitMs && p.mode != BuddyMode.HOP) {
            p.floorStayMs = 0f
            val sideY = (topY + 140f * density + pIdx * petSpacingPx).coerceAtMost((stageH - 64f * density).coerceAtLeast(topY + 50f))
            launchAgentJump(p, edgeX + halfH, sideY, BuddyWall.SIDE, postMode = BuddyMode.WALK)
            return
        }
    } else if (p.wall != BuddyWall.FLOOR) {
        p.floorStayMs = 0f
    }

    when (p.mode) {
        BuddyMode.PEEK_IN -> {
            p.wall = BuddyWall.SIDE
            p.x += 0.9f * density * dt
            if (p.x >= p.peekTargetX) {
                p.x = p.peekTargetX
                p.mode = BuddyMode.WALK
                p.timer = 35
            }
        }
        BuddyMode.JUMP_TO -> {
            p.jumpT += 0.038f * dt
            val t = p.jumpT.coerceAtMost(1f)
            p.x = p.jumpSx + (p.jumpEx - p.jumpSx) * t
            p.y = p.jumpSy + (p.jumpEy - p.jumpSy) * t - sin(t * PI).toFloat() * 44f * density
            if (t >= 1f) {
                p.wall = p.jumpTargetWall
                p.cardKey = p.jumpTargetCard
                p.squashMs = 150f
                p.mode = p.jumpPostMode ?: if (p.wall == BuddyWall.SEARCH) BuddyMode.IDLE else BuddyMode.WALK
                p.jumpPostIcon?.let { p.icon = it; p.iconTimer = if (p.mode == BuddyMode.SLEEP) 22 else 14 }
                if (p.jumpPostHammerMs > 0f) {
                    p.hammerTimerMs = p.jumpPostHammerMs
                    p.hammerBeatMs = 260f
                }
                p.jumpPostMode = null
                p.jumpPostIcon = null
                p.jumpPostHammerMs = 0f
                p.timer = if (p.mode == BuddyMode.SLEEP) 45 else 40
            }
        }
        BuddyMode.FALL -> {
            val prevY = p.y
            p.vy += 0.55f * dt; p.x += p.vx * dt; p.y += p.vy * dt
            if (p.x < edgeX + halfH) { p.x = edgeX + halfH; p.vx = abs(p.vx) * 0.5f }
            if (p.x > stageW - 24f) { p.x = stageW - 24f; p.vx = -abs(p.vx) * 0.5f }
            var landedCard = false
            if (cardClimb && p.vy > 0f) {
                visitCards { k, r ->
                    if (landedCard) return@visitCards
                    val sy = r.top - halfH
                    if (p.x in (r.left + 12f)..(r.right - 12f) && prevY <= sy + 10f && p.y >= sy) {
                        p.y = sy; p.vy = 0f; p.vx = 0f; p.squashMs = 150f
                        p.wall = BuddyWall.CARD; p.cardKey = k; p.mode = BuddyMode.WALK
                        landedCard = true
                    }
                }
            }
            if (!landedCard && p.y >= floorY) {
                p.y = floorY; p.vx *= 0.7f; p.squashMs = 150f
                if (abs(p.vy) > 3.2f && p.bounceCount < 2) {
                    p.vy = -p.vy * 0.48f; p.bounceCount++
                } else {
                    p.vy = 0f; p.vx = 0f; p.mode = BuddyMode.DIZZY; p.timer = 14
                }
            }
        }
        BuddyMode.RUNBACK -> {
            val tx = food?.x ?: (edgeX + halfH)
            val dx = tx - p.x
            p.y = floorY
            if (abs(dx) > 6f) {
                p.x += sign(dx) * 2.8f * dt; p.dir = sign(dx)
            } else if (food != null) {
                onConsumeFood()
                p.icon = PixelIcon.HEART; p.iconTimer = 14
                p.mode = BuddyMode.HOP; p.hopPhase = 0f
            } else {
                p.wall = BuddyWall.SIDE; p.x = edgeX; p.y = floorY - 12f
                p.dir = -1f; p.mode = BuddyMode.WALK; p.timer = 45
            }
        }
        BuddyMode.WALK -> when (p.wall) {
            BuddyWall.SIDE -> {
                p.x = edgeX; p.y += p.dir * 1.1f * dt
                if (p.y <= topY + halfH + 6f && p.dir < 0f) {
                    p.wall = BuddyWall.TOP; p.x = edgeX + halfH + 14f; p.y = topY; p.dir = 1f
                    p.turnFromDeg = 90f; p.turnToDeg = 180f; p.turnMs = 130f
                } else if (p.y >= stageH - 56f && p.dir > 0f) p.dir = -1f
            }
            BuddyWall.TOP -> {
                p.y = topY; p.x += p.dir * 1.2f * dt
                if (p.x <= edgeX + halfH + 8f && p.dir < 0f) {
                    p.wall = BuddyWall.SIDE; p.x = edgeX; p.y = topY + halfH + 14f; p.dir = 1f
                    p.turnFromDeg = 180f; p.turnToDeg = 90f; p.turnMs = 130f
                } else if (p.x >= stageW - 48f && p.dir > 0f) p.dir = -1f
            }
            BuddyWall.CARD -> {
                val r = p.cardKey?.let { cardLookup(it) }
                if (r == null) { p.wall = BuddyWall.SIDE; p.x = edgeX }
                else {
                    val cardPad = 14f * density
                    p.y = r.top - halfH; p.x += p.dir * 1.0f * dt
                    if (p.x <= r.left + cardPad) p.dir = 1f
                    else if (p.x >= r.right - cardPad) {
                        p.dir = -1f
                        if (Random.nextFloat() < 0.45f) { p.mode = BuddyMode.PEEK_EDGE; p.timer = 14 }
                    }
                }
            }
            BuddyWall.SEARCH -> if (searchRect != null) {
                val searchPad = 18f * density
                p.y = searchRect.top - halfH
                if (searching) {
                    p.x += p.dir * 1.15f * dt
                    if (p.x <= searchRect.left + searchPad) { p.x = searchRect.left + searchPad; p.dir = 1f }
                    if (p.x >= searchRect.right - searchPad) { p.x = searchRect.right - searchPad; p.dir = -1f }
                } else {
                    p.x += (searchRect.right - searchPad - pIdx * petSpacingPx - p.x) * (0.22f * dt).coerceAtMost(1f)
                }
            }
            BuddyWall.FLOOR -> {
                p.x = (p.x + p.dir * 0.85f * dt).coerceIn(edgeX + 24f * density, (stageW - 28f * density).coerceAtLeast(edgeX + 30f * density))
            }
        }
        BuddyMode.IDLE, BuddyMode.SLEEP, BuddyMode.GREET, BuddyMode.STRETCH, BuddyMode.PEEK_EDGE -> {
            if (p.wall == BuddyWall.SIDE) p.x = edgeX
            if (p.wall == BuddyWall.TOP) p.y = topY
            if (p.wall == BuddyWall.SEARCH && searchRect != null) {
                val searchPad = 18f * density
                p.x += (searchRect.right - searchPad - pIdx * petSpacingPx - p.x) * (0.22f * dt).coerceAtMost(1f)
                p.y = searchRect.top - halfH
            }
        }
        else -> Unit
    }

    if (isTick && p.wall != BuddyWall.SEARCH && p.hammerTimerMs <= 0f && p.returnDelayMs <= 0f) {
        if (p.mode == BuddyMode.DIZZY) {
            p.timer--; if (p.timer <= 0) p.mode = BuddyMode.RUNBACK
        } else if (p.mode in setOf(BuddyMode.WALK, BuddyMode.IDLE, BuddyMode.SLEEP, BuddyMode.GREET, BuddyMode.STRETCH, BuddyMode.PEEK_EDGE)) {
            p.timer--
            if (p.timer <= 0) {
                val r = Random.nextFloat()
                val sleepStart = if (lateNightMode) 0.66f else 0.88f
                when {
                    r < 0.42f -> { p.mode = BuddyMode.WALK; p.dir = if (Random.nextBoolean()) 1f else -1f; p.timer = 30 + Random.nextInt(30) }
                    r < 0.60f -> { p.mode = BuddyMode.IDLE; p.timer = 20 + Random.nextInt(18) }
                    r < sleepStart - 0.08f -> { p.mode = BuddyMode.STRETCH; p.timer = 8 }
                    r < sleepStart -> {
                        if (p.wall == BuddyWall.CARD || p.wall == BuddyWall.TOP) { p.mode = BuddyMode.PEEK_EDGE; p.timer = 14 }
                        else { p.mode = BuddyMode.HOP; p.hopPhase = 0f; p.icon = PixelIcon.STAR; p.iconTimer = 8 }
                    }
                    else -> { p.mode = BuddyMode.SLEEP; p.timer = if (lateNightMode) 65 else 45; p.icon = PixelIcon.ZZZ; p.iconTimer = 10 }
                }
            }
        }
    }
}

/**
 * 侧栏与顶栏同轨迎面相遇互动：触发互相贴贴冒红心或主动叠罗汉。
 */
internal fun stepBuddySocialInteractions(
    active: List<BuddyAgent>,
    density: Float,
) {
    if (active.size < 2) return
    val meetDist = 24f * density
    for (i in 0 until active.lastIndex) {
        val a = active[i]
        if (a.mode != BuddyMode.WALK || a.stackHostId >= 0 || a.greetCooldownMs > 0f) continue
        if (a.wall != BuddyWall.SIDE && a.wall != BuddyWall.TOP) continue
        for (j in i + 1 until active.size) {
            val b = active[j]
            if (b.mode != BuddyMode.WALK || b.stackHostId >= 0 || b.greetCooldownMs > 0f || b.wall != a.wall) continue
            val dist = if (a.wall == BuddyWall.SIDE) abs(a.y - b.y) else abs(a.x - b.x)
            val facing = a.dir != b.dir
            if (dist <= meetDist && facing) {
                a.greetCooldownMs = 9500f
                b.greetCooldownMs = 9500f
                if (Random.nextFloat() < 0.38f) {
                    b.stackHostId = a.id
                    b.stackTimerMs = 4600f
                    a.icon = PixelIcon.HEART
                    a.iconTimer = 12
                } else {
                    a.dir = -a.dir
                    b.dir = -b.dir
                    a.mode = BuddyMode.GREET; a.timer = 8; a.icon = PixelIcon.HEART; a.iconTimer = 12
                    b.mode = BuddyMode.GREET; b.timer = 8; b.icon = PixelIcon.HEART; b.iconTimer = 12
                }
                return
            }
        }
    }
}

package com.venera.desktop.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerIcon
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin

/**
 * 计算落地压扁弹起（Squash & Stretch）的瞬间缩放比。
 */
internal fun buddySquashScale(p: BuddyAgent): Pair<Float, Float> {
    if (p.squashMs <= 0f) return 1f to 1f
    val ratio = (p.squashMs / 150f).coerceIn(0f, 1f)
    val bump = sin(ratio * PI).toFloat()
    return (1f + 0.24f * bump) to (1f - 0.22f * bump)
}

/**
 * 计算包含 45° 拐角翻墙过渡的当前旋转角（度）。
 */
internal fun buddyRotationDeg(p: BuddyAgent): Float {
    if (p.turnMs > 0f) {
        val progress = 1f - (p.turnMs / 130f).coerceIn(0f, 1f)
        return p.turnFromDeg + (p.turnToDeg - p.turnFromDeg) * progress
    }
    return when {
        p.mode in setOf(
            BuddyMode.DRAG, BuddyMode.FALL, BuddyMode.DIZZY, BuddyMode.RUNBACK,
            BuddyMode.JUMP_TO, BuddyMode.LEAP, BuddyMode.SKIM, BuddyMode.PEEK_IN, BuddyMode.CHASE_WAND,
        ) -> 0f
        p.wall == BuddyWall.SIDE -> 90f
        p.wall == BuddyWall.TOP -> 180f
        else -> 0f
    }
}

/**
 * 平时未开逗猫棒时，若鼠标从发呆或伏在卡片边沿的宠物附近 85dp 内经过，小家伙会好奇扭头朝向光标。
 */
internal fun stepCuriousLookAtCursor(
    active: List<BuddyAgent>,
    cursor: Offset?,
    density: Float,
) {
    if (cursor == null) return
    val radius = 85f * density
    val deadZone = 8f * density
    for (p in active) {
        if (p.wall == BuddyWall.SIDE || p.wall == BuddyWall.SEARCH) continue
        if (p.mode !in setOf(BuddyMode.IDLE, BuddyMode.STRETCH, BuddyMode.PEEK_EDGE)) continue
        if (hypot(cursor.x - p.x, cursor.y - p.y) <= radius && abs(cursor.x - p.x) > deadZone) {
            p.dir = if (cursor.x >= p.x) 1f else -1f
        }
    }
}

/**
 * 推进小家伙追逐像素逗猫棒的物理与扑腾跳跃：支持借画廊/漫画全部可见卡片当立体踏板层层追扑！
 */
internal fun stepWandChasePhysics(
    p: BuddyAgent,
    pIdx: Int,
    dtMs: Float,
    isTick: Boolean,
    wandPos: Offset?,
    edgeX: Float,
    topY: Float,
    stageW: Float,
    halfH: Float,
    floorY: Float,
    density: Float,
    localCardRects: Map<String, Rect> = emptyMap(),
    cardLookup: (String) -> Rect? = { localCardRects[it] },
    visitCards: ((String, Rect) -> Unit) -> Unit = { action -> localCardRects.forEach { (k, r) -> action(k, r) } },
) {
    if (p.squashMs > 0f) p.squashMs = (p.squashMs - dtMs).coerceAtLeast(0f)
    if (p.wandPounceCooldownMs > 0f) p.wandPounceCooldownMs = (p.wandPounceCooldownMs - dtMs).coerceAtLeast(0f)
    if (isTick && p.iconTimer > 0) p.iconTimer--
    if (p.mode in setOf(BuddyMode.LEAP, BuddyMode.SKIM, BuddyMode.DRAG)) return
    val dt = dtMs / 16.667f
    val target = wandPos ?: Offset(edgeX + 240f * density, floorY - 40f * density)
    val offsetX = (pIdx - 1) * 26f * density

    // 1. 正在跨卡跳跃前往踏板
    if (p.mode == BuddyMode.JUMP_TO) {
        p.jumpT += 0.046f * dt
        val t = p.jumpT.coerceAtMost(1f)
        p.x = p.jumpSx + (p.jumpEx - p.jumpSx) * t
        p.y = p.jumpSy + (p.jumpEy - p.jumpSy) * t - sin(t * PI).toFloat() * 42f * density
        if (t >= 1f) {
            p.wall = p.jumpTargetWall
            p.cardKey = p.jumpTargetCard
            p.squashMs = 150f
            p.mode = BuddyMode.CHASE_WAND
        }
        return
    }

    // 2. 扑棒后半空下落（优先落在下方卡片顶边，否则落回地面）
    if (p.mode == BuddyMode.FALL) {
        val prevY = p.y
        p.vy += 0.55f * dt
        p.x = (p.x + p.vx * dt).coerceIn(edgeX + 12f * density, stageW - 20f * density)
        p.y += p.vy * dt
        if (p.vy > 0f) {
            var landed = false
            visitCards { k, r ->
                if (landed) return@visitCards
                val sy = r.top - halfH
                if (p.x in (r.left + 10f * density)..(r.right - 10f * density) && prevY <= sy + 12f * density && p.y >= sy) {
                    p.y = sy; p.vy = 0f; p.vx = 0f
                    p.wall = BuddyWall.CARD; p.cardKey = k
                    p.squashMs = 150f
                    p.wandPounceCooldownMs = 460f
                    p.mode = BuddyMode.CHASE_WAND
                    landed = true
                }
            }
            if (landed) return
        }
        if (p.y >= floorY) {
            p.y = floorY; p.vy = 0f; p.vx = 0f
            p.wall = BuddyWall.FLOOR; p.cardKey = null
            p.squashMs = 150f
            p.wandPounceCooldownMs = 460f
            p.mode = BuddyMode.CHASE_WAND
        }
        return
    }

    p.stackHostId = -1
    p.mode = BuddyMode.CHASE_WAND

    // 3. 寻找最适合作为当前追逐踏板的可见卡片（覆盖 pixivision/推荐画师/关注/排行/瀑布流/漫画全卡片）
    val bestPlatform = findBestWandPlatform(p, target, topY, floorY, halfH, density, visitCards)
    if (bestPlatform != null) {
        val (bestKey, bestRect) = bestPlatform
        val isSameCard = p.wall == BuddyWall.CARD && p.cardKey == bestKey
        val tx = (target.x + offsetX).coerceIn(bestRect.left + 12f * density, bestRect.right - 12f * density)
        val ty = bestRect.top - halfH
        if (!isSameCard && hypot(tx - p.x, ty - p.y) > 46f * density) {
            launchAgentJump(p, tx, ty, BuddyWall.CARD, targetCard = bestKey, postMode = BuddyMode.CHASE_WAND)
            return
        }
    }

    // 4. 在当前踏板卡片或地面追着逗猫棒跑并在附近猛扑
    val activeCard = if (p.wall == BuddyWall.CARD) p.cardKey?.let { cardLookup(it) } else null
    if (activeCard != null) {
        val pad = 12f * density
        val goalX = (target.x + offsetX).coerceIn(activeCard.left + pad, (activeCard.right - pad).coerceAtLeast(activeCard.left + pad))
        p.y = activeCard.top - halfH
        val dx = goalX - p.x
        if (abs(dx) > 8f * density) {
            p.x += sign(dx) * 3.1f * density * dt
            p.dir = sign(dx)
        } else {
            p.dir = if (target.x >= p.x) 1f else -1f
            val distY = p.y - target.y
            if (p.wandPounceCooldownMs <= 0f && distY in (20f * density)..(160f * density) && abs(target.x - p.x) < 48f * density) {
                p.mode = BuddyMode.FALL
                p.vx = sign(target.x - p.x) * 2.1f * density
                p.vy = -7.8f * density
                p.icon = PixelIcon.STAR; p.iconTimer = 10
            }
        }
        return
    }

    // 若原本站在某卡片但该卡滑出视口，落回地面
    if (p.wall == BuddyWall.CARD) {
        p.mode = BuddyMode.FALL; p.vy = 0f; p.vx = 0f; p.cardKey = null
        return
    }

    p.wall = BuddyWall.FLOOR
    p.y = floorY
    val goalX = (target.x + offsetX).coerceIn(edgeX + 20f * density, stageW - 24f * density)
    val dx = goalX - p.x
    if (abs(dx) > 8f * density) {
        p.x += sign(dx) * 3.3f * density * dt
        p.dir = sign(dx)
    } else {
        p.dir = if (target.x >= p.x) 1f else -1f
        val distY = p.y - target.y
        if (p.wandPounceCooldownMs <= 0f && distY in (26f * density)..(180f * density)) {
            p.mode = BuddyMode.FALL
            p.vx = sign(target.x - p.x) * 2.2f * density
            p.vy = -8.5f * density
            p.icon = PixelIcon.STAR; p.iconTimer = 10
        }
    }
}

private fun findBestWandPlatform(
    p: BuddyAgent,
    target: Offset,
    topY: Float,
    floorY: Float,
    halfH: Float,
    density: Float,
    visitCards: ((String, Rect) -> Unit) -> Unit,
): Pair<String, Rect>? {
    val minTop = topY + 22f * density
    val maxTop = floorY - 26f * density
    var best: Pair<String, Rect>? = null
    var bestScore = Float.MAX_VALUE
    visitCards { key, r ->
        if (r.top < minTop || r.top > maxTop || r.right - r.left < 48f * density) return@visitCards
        val surfaceY = r.top - halfH
        val dyToWand = surfaceY - target.y
        if (dyToWand < -18f * density || dyToWand > 175f * density) return@visitCards
        val clampedX = target.x.coerceIn(r.left, r.right)
        val dxToWand = abs(target.x - clampedX)
        var score = dxToWand * 1.4f + abs(dyToWand)
        if (p.wall == BuddyWall.CARD && p.cardKey == key) score -= 28f * density
        if (score < bestScore) {
            bestScore = score
            best = key to r
        }
    }
    return best
}

/**
 * 返回 16x16 大号像素逗猫棒点阵（左上粉紫羽球带微描边，斜向右下延伸木杆）。
 */
internal fun pixelWandSpriteGrid(): List<String> = listOf(
    "..pp............",
    ".pFFp...........",
    "pFFPFp..........",
    "pFPFFvp.........",
    ".pvYYvvp........",
    "..pYYWWv........",
    "...pvbBWb.......",
    "....pbbWBb......",
    "......bbWBb.....",
    ".......bbWBb....",
    "........bbWBb...",
    ".........bbWBb..",
    "..........bbWBb.",
    "...........bbWBb",
    "............bbWB",
    ".............bbb",
)

private fun wandPixelArgb(ch: Char): Int = when (ch) {
    'F' -> 0xFFF472B6.toInt()
    'P' -> 0xFFFCE7F3.toInt()
    'v' -> 0xFF947FD6.toInt()
    'p' -> 0xFF4C3378.toInt()
    'Y' -> 0xFFFDE047.toInt()
    'W' -> 0xFFF3C492.toInt()
    'B' -> 0xFFD99B66.toInt()
    'b' -> 0xFF6E472B.toInt()
    else -> 0
}

/**
 * 构建用于软件窗口内系统指针替换的大号像素逗猫棒光标（无头环境或受限平台安全返回 null）。
 */
internal fun createPixelWandPointerIcon(): PointerIcon? = runCatching {
    val toolkit = Toolkit.getDefaultToolkit()
    val grid = pixelWandSpriteGrid()
    val best = toolkit.getBestCursorSize(48, 48)
    val targetW = if (best.width >= 32) best.width else 32
    val targetH = if (best.height >= 32) best.height else 32
    val scale = (minOf(targetW, targetH) / 16).coerceAtLeast(2)
    val imgW = targetW.coerceAtLeast(16 * scale)
    val imgH = targetH.coerceAtLeast(16 * scale)
    val img = BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB)
    for (gy in grid.indices) {
        val row = grid[gy]
        for (gx in row.indices) {
            val argb = wandPixelArgb(row[gx])
            if (argb == 0) continue
            val px = gx * scale
            val py = gy * scale
            for (dy in 0 until scale) {
                for (dx in 0 until scale) {
                    if (px + dx < imgW && py + dy < imgH) {
                        img.setRGB(px + dx, py + dy, argb)
                    }
                }
            }
        }
    }
    val hotX = (1 * scale).coerceIn(0, imgW - 1)
    val hotY = (1 * scale).coerceIn(0, imgH - 1)
    PointerIcon(toolkit.createCustomCursor(img, Point(hotX, hotY), "venera-pixel-wand"))
}.getOrNull()

/**
 * 无系统自定义光标支持时的兜底放大版画布像素逗猫棒。
 */
internal fun DrawScope.drawPixelWand(
    pos: Offset,
    swayPhase: Float,
    cellPx: Float,
) {
    val grid = pixelWandSpriteGrid()
    val bigCell = cellPx * 1.45f
    val sway = sin(swayPhase * 0.35f) * 1.2f * bigCell
    for (gy in grid.indices) {
        val row = grid[gy]
        for (gx in row.indices) {
            val argb = wandPixelArgb(row[gx])
            if (argb == 0) continue
            val featherFactor = (1f - (gy / 8f)).coerceIn(0f, 1f)
            drawRect(
                color = Color(argb),
                topLeft = Offset(pos.x + gx * bigCell + sway * featherFactor, pos.y + gy * bigCell),
                size = Size(bigCell + 0.5f, bigCell + 0.5f),
            )
        }
    }
}

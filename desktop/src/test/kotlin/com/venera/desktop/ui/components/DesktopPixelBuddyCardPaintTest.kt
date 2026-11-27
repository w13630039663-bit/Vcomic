package com.venera.desktop.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.venera.desktop.settings.DesktopPetVisibility
import com.venera.desktop.settings.DesktopPixelBuddyBus
import com.venera.desktop.settings.DesktopSettingRow
import com.venera.desktop.settings.PixelBuddyCardRevealPhase
import com.venera.desktop.settings.PixelBuddyConfig
import com.venera.desktop.settings.PixelBuddySkin
import com.venera.desktop.settings.buildPixelBuddySettingsCards
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证搜索与画廊瀑布流像素伙伴「不限宠数分列踩台阶 + 列多米诺光流 + 滚动避让」状态机。
 */
class DesktopPixelBuddyCardPaintTest {

    @After
    fun tearDown() {
        DesktopPixelBuddyBus.clearCardPaint()
    }

    private fun staggeredGalleryCards(): Map<String, Rect> {
        val rects = linkedMapOf<String, Rect>()
        val colLefts = listOf(80f, 300f, 520f, 740f)
        val heights = listOf(
            listOf(180f, 240f, 140f),
            listOf(230f, 150f, 210f),
            listOf(140f, 220f, 180f),
            listOf(210f, 180f, 230f),
        )
        for (c in 0 until 4) {
            var y = 120f
            for (r in 0 until 3) {
                val h = heights[c][r]
                rects["wall-$c-$r"] = Rect(colLefts[c], y, colLefts[c] + 190f, y + h)
                y += h + 14f
            }
        }
        return rects
    }

    private fun comicTwoColCards(): Map<String, Rect> {
        val rects = linkedMapOf<String, Rect>()
        for (r in 0 until 5) {
            val top = 120f + r * 170f
            rects["search-row-$r-0"] = Rect(80f, top, 440f, top + 156f)
            rects["search-row-$r-1"] = Rect(460f, top, 820f, top + 156f)
        }
        return rects
    }

    @Test
    fun `1到3只宠物在4列错落瀑布流与2列漫画下均能完整唤醒全部卡片`() {
        val layouts = listOf(staggeredGalleryCards(), comicTwoColCards())
        for (cards in layouts) {
            for (count in listOf(1, 2, 3)) {
                val controller = PixelBuddyCardPaintController()
                val agents = listOf(
                    BuddyAgent(0, PixelBuddySkin.CAT, BuddyWall.SEARCH, 700f, 44f),
                    BuddyAgent(1, PixelBuddySkin.ORANGE, BuddyWall.SEARCH, 640f, 44f),
                    BuddyAgent(2, PixelBuddySkin.BLUE, BuddyWall.SEARCH, 580f, 44f),
                ).take(count)

                controller.startSession(
                    localCards = cards,
                    activeAgents = agents,
                    stageW = 1000f,
                    stageH = 720f,
                    edgeX = 48f,
                    topY = 44f,
                    halfH = 15f,
                    density = 1f,
                )
                assertTrue("会话启动后应进入活跃态 (count=$count)", controller.active)

                val seenSkimOrAwakened = mutableSetOf<String>()
                for (step in 0 until 450) {
                    controller.stepFrame(
                        dtMs = 16f,
                        activeAgents = agents,
                        stageW = 1000f,
                        stageH = 720f,
                        edgeX = 48f,
                        topY = 44f,
                        halfH = 15f,
                        density = 1f,
                        localCardRects = cards,
                    )
                    val snap = DesktopPixelBuddyBus.cardPaint.value
                    cards.keys.forEach { k ->
                        val ph = snap.phaseOf(k)
                        if (ph is PixelBuddyCardRevealPhase.Skimming || ph is PixelBuddyCardRevealPhase.Awakened) {
                            seenSkimOrAwakened.add(k)
                        }
                    }
                    if (!controller.active && step > 10) break
                }

                assertEquals(
                    "全部 ${cards.size} 张卡片都应被踩过或光流唤醒 (count=$count)",
                    cards.keys,
                    seenSkimOrAwakened,
                )
                assertFalse("描绘收尾后会话应自动退出活跃态 (count=$count)", controller.active)
                assertFalse("描绘收尾后总线应恢复零遮罩 (count=$count)", DesktopPixelBuddyBus.cardPaint.value.active)
                agents.forEach { p ->
                    assertTrue(
                        "宠物完成后应跳回侧栏或正在回墙 (mode=${p.mode}, wall=${p.jumpTargetWall})",
                        p.mode == BuddyMode.JUMP_TO || p.wall == BuddyWall.SIDE,
                    )
                }
            }
        }
    }

    @Test
    fun `滚动中断时宠物立刻收笔回墙且剩余未点亮卡片即时唤醒`() {
        val cards = staggeredGalleryCards()
        val controller = PixelBuddyCardPaintController()
        val agents = listOf(
            BuddyAgent(0, PixelBuddySkin.CAT, BuddyWall.SEARCH, 700f, 44f),
            BuddyAgent(1, PixelBuddySkin.ORANGE, BuddyWall.SEARCH, 640f, 44f),
        )

        controller.startSession(
            localCards = cards,
            activeAgents = agents,
            stageW = 1000f,
            stageH = 720f,
            edgeX = 48f,
            topY = 44f,
            halfH = 15f,
            density = 1f,
        )
        // 先跑 2 帧后触发滚动打断
        repeat(2) {
            controller.stepFrame(16f, agents, 1000f, 720f, 48f, 44f, 15f, 1f, cards)
        }
        controller.abortOnScroll(agents, edgeX = 48f, topY = 44f, stageH = 720f, halfH = 15f)

        assertFalse("滚动打断后描绘会话应立即停用", controller.active)
        agents.forEach { p ->
            assertEquals("宠物应立刻起跳回侧栏", BuddyMode.JUMP_TO, p.mode)
            assertEquals(BuddyWall.SIDE, p.jumpTargetWall)
        }
        val snap = DesktopPixelBuddyBus.cardPaint.value
        val waitingLeft = cards.keys.count { snap.phaseOf(it) is PixelBuddyCardRevealPhase.Waiting }
        assertEquals("不应有任何卡片被锁死在 Waiting", 0, waitingLeft)
    }

    @Test
    fun `渲染坐标与热区坐标单源一致且叠罗汉与业务事件正常联动`() {
        val host = BuddyAgent(0, PixelBuddySkin.CAT, BuddyWall.SIDE, 48f, 200f, 1f)
        val rider = BuddyAgent(1, PixelBuddySkin.ORANGE, BuddyWall.SIDE, 48f, 210f, -1f)
        val agents = listOf(host, rider)

        // 1. 侧栏爬行时，视觉与热区中心应严格对齐到 edgeX + halfH
        val (hx0, hy0) = buddyVisualPose(host, edgeX = 48f, topY = 44f, halfH = 10f, density = 1f)
        assertEquals(58f, hx0, 0.01f)
        assertEquals(200f, hy0, 0.01f)

        // 2. 叠罗汉跟随宿主同步移动，时间归零自动脱离
        rider.stackHostId = host.id
        rider.stackTimerMs = 200f
        stepRegularBuddyPhysics(
            p = rider, pIdx = 1, allAgents = agents, dtMs = 16f, isTick = false,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f,
            floorY = 700f, density = 1f, petSpacingPx = 38f, cardClimb = true,
            searching = false, searchRect = null, localCardRects = emptyMap(),
            food = null, onConsumeFood = {},
        )
        assertEquals(69f, rider.x, 0.1f)
        assertEquals(host.y, rider.y, 0.1f)

        // 3. 收藏红心、下载完成掉落小鱼干、失败举锤提醒
        var spawnedFood: BuddyFood? = null
        handleDesktopPetCue(
            cue = DesktopPetCue.TOGGLE_FAVORITE, active = agents, paintActive = false,
            emptyAnchor = null, retryAnchor = null, stageW = 1000f, stageH = 720f,
            edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = { spawnedFood = it },
        )
        assertEquals(PixelIcon.HEART, host.icon)

        handleDesktopPetCue(
            cue = DesktopPetCue.DOWNLOAD_ALL_DONE, active = agents, paintActive = false,
            emptyAnchor = null, retryAnchor = null, stageW = 1000f, stageH = 720f,
            edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = { spawnedFood = it },
        )
        assertTrue("下载完成应触发奖励小鱼干", spawnedFood != null)

        val retryRect = Rect(400f, 300f, 520f, 340f)
        handleDesktopPetCue(
            cue = DesktopPetCue.LOAD_FAILED, active = agents, paintActive = false,
            emptyAnchor = null, retryAnchor = retryRect, stageW = 1000f, stageH = 720f,
            edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = {},
        )
        assertEquals(BuddyMode.JUMP_TO, host.mode)
        assertEquals(PixelIcon.HAMMER, host.jumpPostIcon)
        assertTrue(host.jumpPostHammerMs > 0f)
    }

    @Test
    fun `压扁弹起45度翻墙过渡与逗猫棒追逐物理及新增姿势尺寸均合法`() {
        // 1. 全皮肤全姿势严格保持 16x10 像素格，防止缩放/翻转歪斜
        for (skin in PixelBuddySkin.entries) {
            for (pose in PixelPose.entries) {
                val rows = PixelBuddySprites.framesOf(skin, pose)
                assertEquals("$skin $pose 行数应为10", 10, rows.size)
                rows.forEach { line -> assertEquals("$skin $pose 列宽应为16", 16, line.length) }
            }
        }

        // 2. 落地压扁弹起缩放比
        val p = BuddyAgent(0, PixelBuddySkin.CAT, BuddyWall.SIDE, 48f, 50f, -1f)
        p.squashMs = 75f
        val (sx, sy) = buddySquashScale(p)
        assertTrue("落地压扁阶段应横向微胀: $sx", sx > 1.1f)
        assertTrue("落地压扁阶段应纵向微扁: $sy", sy < 0.9f)

        // 3. 侧栏爬到顶栏拐角触发 45°（135°）翻墙过渡
        stepRegularBuddyPhysics(
            p = p, pIdx = 0, allAgents = listOf(p), dtMs = 16f, isTick = false,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f,
            floorY = 700f, density = 1f, petSpacingPx = 38f, cardClimb = true,
            searching = false, searchRect = null, localCardRects = emptyMap(),
            food = null, onConsumeFood = {},
        )
        assertEquals(BuddyWall.TOP, p.wall)
        assertTrue("拐角过渡计时应激活", p.turnMs > 0f)
        p.turnMs = 65f
        val midRot = buddyRotationDeg(p)
        assertTrue("转角中途应处于90与180度之间: $midRot", midRot in 120f..150f)

        // 4. 逗猫棒追逐借卡片当踏板跳跃，以及平时好奇转头盯光标
        val chaser = BuddyAgent(1, PixelBuddySkin.ORANGE, BuddyWall.FLOOR, 100f, 700f, 1f)
        val galleryHomeCards = mapOf(
            "pixivision-1" to Rect(120f, 220f, 480f, 400f),
            "user-2" to Rect(500f, 380f, 780f, 600f),
        )
        repeat(80) {
            stepWandChasePhysics(
                p = chaser, pIdx = 1, dtMs = 16f, isTick = false,
                wandPos = Offset(300f, 150f),
                edgeX = 48f, topY = 44f, stageW = 1000f, halfH = 10f, floorY = 700f, density = 1f,
                localCardRects = galleryHomeCards,
            )
        }
        assertTrue("逗猫棒在卡片上方时宠物应跳上或前往该卡片踏板", chaser.cardKey == "pixivision-1" || chaser.jumpTargetCard == "pixivision-1")

        val observer = BuddyAgent(2, PixelBuddySkin.BLUE, BuddyWall.CARD, 300f, 210f, 1f).apply {
            mode = BuddyMode.IDLE
        }
        stepCuriousLookAtCursor(listOf(observer), cursor = Offset(240f, 210f), density = 1f)
        assertEquals("附近光标在左侧时宠物应好奇转头向左看", -1f, observer.dir, 0.01f)
    }

    @Test
    fun `卡片注册忽略微小抖动且闲置时描光开关保持关闭`() {
        DesktopPixelBuddyBus.clearCardPaint()
        assertFalse("未开始描绘前 cardPaintActive 应为 false", DesktopPixelBuddyBus.cardPaintActive.value)

        val originRect = Rect(100f, 200f, 300f, 400f)
        DesktopPixelBuddyBus.registerCard("perf-test-card", originRect)
        assertTrue("注册卡片后 hasCards 应为 true", DesktopPixelBuddyBus.hasCards.value)

        // 小于 1.5px 的亚像素抖动不覆写已有位置
        DesktopPixelBuddyBus.registerCard("perf-test-card", Rect(100.8f, 200.6f, 300.4f, 400.9f))
        assertEquals(originRect, DesktopPixelBuddyBus.cardRectOf("perf-test-card"))

        // 超过阈值才更新并可正确注销
        val movedRect = Rect(110f, 200f, 310f, 400f)
        DesktopPixelBuddyBus.registerCard("perf-test-card", movedRect)
        assertEquals(movedRect, DesktopPixelBuddyBus.cardRectOf("perf-test-card"))

        DesktopPixelBuddyBus.unregisterCard("perf-test-card")
        assertEquals(null, DesktopPixelBuddyBus.cardRectOf("perf-test-card"))
    }

    @Test
    fun `设置页逗猫棒行采用标准Toggle开关且大号逗猫棒点阵结构合法`() {
        val grid = pixelWandSpriteGrid()
        assertEquals("逗猫棒应为 16 行高", 16, grid.size)
        grid.forEach { assertEquals("每行应为 16 像素宽", 16, it.length) }
        assertTrue("左上羽毛区应有有效像素", grid[1].contains('F'))
        assertTrue("右下木杆区应有有效像素", grid[13].contains('W'))

        DesktopPixelBuddyBus.setWandMode(false)
        val cards = buildPixelBuddySettingsCards(PixelBuddyConfig(), wandMode = true, store = null)
        val wandRow = cards.flatMap { it.rows }
            .filterIsInstance<DesktopSettingRow.Toggle>()
            .firstOrNull { it.title == "逗猫棒鼠标指针" || it.title == "Cat Wand Cursor" }
        assertTrue("设置页应提供标准的 Toggle 开关行控制逗猫棒指针", wandRow != null)
        assertTrue("传入 wandMode=true 时开关选中态应同步为 true", wandRow!!.checked)
        wandRow.onToggle(true)
        assertTrue("触发开关打开后总线应处于 wandMode=true", DesktopPixelBuddyBus.wandMode.value)
        wandRow.onToggle(false)
        assertFalse("触发开关关闭后总线应处于 wandMode=false", DesktopPixelBuddyBus.wandMode.value)
    }

    @Test
    fun `失败锤击或搜空落入FLOOR与卡片消失以及逗猫棒连跳均能正常脱困且不鬼畜`() {
        // 1. 失败跳到屏幕中段 FLOOR 锤击提醒结束后，自动跳回 SIDE 而不卡死半空
        val jumper = BuddyAgent(0, PixelBuddySkin.CAT, BuddyWall.FLOOR, 420f, 380f, 1f).apply {
            mode = BuddyMode.HOP
            hammerTimerMs = 120f
        }
        repeat(60) { stepIdx ->
            stepRegularBuddyPhysics(
                p = jumper, pIdx = 0, allAgents = listOf(jumper), dtMs = 45f, isTick = stepIdx % 2 == 0,
                stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f,
                floorY = 700f, density = 1f, petSpacingPx = 38f, cardClimb = true,
                searching = false, searchRect = null, localCardRects = emptyMap(),
                food = null, onConsumeFood = {},
            )
        }
        assertTrue(
            "失败锤击完成后应主动向 SIDE 跳回 (mode=${jumper.mode}, wall=${jumper.wall})",
            jumper.wall == BuddyWall.SIDE || (jumper.mode == BuddyMode.JUMP_TO && jumper.jumpTargetWall == BuddyWall.SIDE),
        )

        // 2. 站在卡片上打盹/小跳时卡片被移出视口，立刻触发下落脱离而非悬空原地蹦
        val onCard = BuddyAgent(1, PixelBuddySkin.ORANGE, BuddyWall.CARD, 300f, 220f, 1f).apply {
            cardKey = "gone-card"
            mode = BuddyMode.IDLE
        }
        stepRegularBuddyPhysics(
            p = onCard, pIdx = 1, allAgents = listOf(onCard), dtMs = 16f, isTick = false,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f,
            floorY = 700f, density = 1f, petSpacingPx = 38f, cardClimb = true,
            searching = false, searchRect = null, localCardRects = emptyMap(),
            food = null, onConsumeFood = {},
        )
        assertEquals("卡片消失时处于IDLE的宠物应立刻下落脱离", BuddyMode.FALL, onCard.mode)

        // 3. 逗猫棒静止悬在头顶时，落地后应有 460ms 冷却，不立即原地再次起扑
        val pouncer = BuddyAgent(2, PixelBuddySkin.BLUE, BuddyWall.FLOOR, 300f, 700f, 1f).apply {
            mode = BuddyMode.FALL
            vy = 5f
        }
        stepWandChasePhysics(
            p = pouncer, pIdx = 2, dtMs = 16f, isTick = false,
            wandPos = Offset(300f, 600f),
            edgeX = 48f, topY = 44f, stageW = 1000f, halfH = 10f, floorY = 700f, density = 1f,
            localCardRects = emptyMap(),
        )
        assertEquals(BuddyMode.CHASE_WAND, pouncer.mode)
        assertTrue("落地瞬间应进入猛扑冷却", pouncer.wandPounceCooldownMs > 400f)
        // 下一帧仍然不准立刻再变成 FALL 连跳
        stepWandChasePhysics(
            p = pouncer, pIdx = 2, dtMs = 16f, isTick = false,
            wandPos = Offset(300f, 600f),
            edgeX = 48f, topY = 44f, stageW = 1000f, halfH = 10f, floorY = 700f, density = 1f,
            localCardRects = emptyMap(),
        )
        assertEquals("冷却期间不应在同一坐标原地鬼畜起跳", BuddyMode.CHASE_WAND, pouncer.mode)
    }

    @Test
    fun `像素伙伴设置项全部携带15字以内适用范围说明确保弹窗可见`() {
        val cards = buildPixelBuddySettingsCards(PixelBuddyConfig(), wandMode = false, store = null)
        val rows = cards.flatMap { it.rows }
        assertTrue(rows.isNotEmpty())
        rows.forEach { r ->
            val cap = r.caption
            assertTrue("${r.title} 应有适用范围或说明 caption", !cap.isNullOrBlank())
            assertTrue("${r.title} caption 应 <= 15 字否则会被设置弹窗截断隐藏 (len=${cap?.length})", (cap?.length ?: 0) <= 15)
        }
    }

    @Test
    fun `顶栏跨页最近搜索确认屏蔽首页旧卡且搜框销毁自动清残留定位`() {
        val oldRect = Rect(100f, 120f, 300f, 360f)
        val topBoxRect = Rect(240f, 50f, 640f, 84f)
        DesktopPixelBuddyBus.registerCard("home-daily-old", oldRect)
        DesktopPixelBuddyBus.reportSearchFocus(true, bounds = topBoxRect, query = "")

        // 从顶栏最近搜索跳页确认
        DesktopPixelBuddyBus.reportSearchConfirm("touhou", crossPage = true)
        val st1 = DesktopPixelBuddyBus.searchState.value
        assertTrue(st1.crossPagePending)
        assertTrue("首页旧卡应在确认瞬间进入忽略名单", "home-daily-old" in st1.ignoredCardKeys)
        assertFalse("只有被忽略旧卡时不应误判已有新结果卡", DesktopPixelBuddyBus.hasUnignoredCards(st1.ignoredCardKeys))

        // 顶栏卸载销毁后应清空旧坐标
        DesktopPixelBuddyBus.clearSearchFocusAndBounds(topBoxRect)
        assertEquals(null, DesktopPixelBuddyBus.searchState.value.boundsInWindow)
        assertFalse(DesktopPixelBuddyBus.searchState.value.focused)

        // 旧首页卡 onDispose 注销 + 新结果页卡入列
        DesktopPixelBuddyBus.unregisterCard("home-daily-old")
        val newRect = Rect(90f, 160f, 290f, 400f)
        DesktopPixelBuddyBus.registerCard("wall-new-1", newRect)
        val ignoredAfter = DesktopPixelBuddyBus.searchState.value.ignoredCardKeys
        assertTrue("新结果卡注册后 hasUnignoredCards 应为 true", DesktopPixelBuddyBus.hasUnignoredCards(ignoredAfter))
        assertEquals(mapOf("wall-new-1" to newRect), DesktopPixelBuddyBus.snapshotCardRects(excludeKeys = ignoredAfter))

        DesktopPixelBuddyBus.unregisterCard("wall-new-1")
        DesktopPixelBuddyBus.clearIgnoredCards()
        DesktopPixelBuddyBus.reportSearching(false)
    }

    @Test
    fun `新增像素道具图标完整且剩余业务事件联动与节流专注保护正确`() {
        val boxRows = PixelBuddySprites.iconRows(PixelIcon.BOX)
        val sweatRows = PixelBuddySprites.iconRows(PixelIcon.SWEAT)
        val capRows = PixelBuddySprites.nightCapRows()
        assertEquals(4, boxRows.size)
        assertEquals(4, sweatRows.size)
        assertEquals(3, capRows.size)
        capRows.forEach { assertEquals(5, it.length) }

        val agents = listOf(
            BuddyAgent(0, PixelBuddySkin.CAT, BuddyWall.SIDE, 48f, 220f, 1f).apply { mode = BuddyMode.IDLE },
            BuddyAgent(1, PixelBuddySkin.ORANGE, BuddyWall.TOP, 260f, 44f, 1f).apply { mode = BuddyMode.IDLE },
        )

        // 下载中搬砖 -> 下载完停工掉小鱼干
        handleDesktopPetCue(
            cue = DesktopPetCue.DOWNLOADING,
            active = agents, paintActive = false, emptyAnchor = null, retryAnchor = null,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = {},
        )
        assertTrue(agents.last().workBoxTimerMs > 0f)
        assertEquals(PixelIcon.BOX, agents.last().icon)

        var spawnedFood: BuddyFood? = null
        handleDesktopPetCue(
            cue = DesktopPetCue.DOWNLOAD_ALL_DONE,
            active = agents, paintActive = false, emptyAnchor = null, retryAnchor = null,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = { spawnedFood = it },
        )
        assertEquals(0f, agents.last().workBoxTimerMs, 0.01f)
        assertTrue(spawnedFood != null)

        // 深夜睡帽
        var isLateNight = false
        agents[0].mode = BuddyMode.IDLE
        handleDesktopPetCue(
            cue = DesktopPetCue.LATE_NIGHT,
            active = agents, paintActive = false, emptyAnchor = null, retryAnchor = null,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = {}, onSetLateNight = { isLateNight = it },
        )
        assertTrue(isLateNight)
        assertEquals(BuddyMode.SLEEP, agents[0].mode)

        // 切回窗体迎接 & 取消收藏小汗滴 & 滚到底探头 & 节流 + 逗猫棒保护
        handleDesktopPetCue(
            cue = DesktopPetCue.WINDOW_RETURN,
            active = agents, paintActive = false, emptyAnchor = null, retryAnchor = null,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = {},
        )
        assertEquals(PixelIcon.HEART, agents[0].icon)

        var lastLightMs = 1000L
        handleDesktopPetCue(
            cue = DesktopPetCue.REMOVE_FAVORITE,
            active = agents, paintActive = false, emptyAnchor = null, retryAnchor = null,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = {}, nowMs = 3000L, lastLightCueAtMs = lastLightMs, onUpdateLightCueAtMs = { lastLightMs = it },
        )
        assertEquals(PixelIcon.SWEAT, agents[0].icon)
        assertEquals(3000L, lastLightMs)

        // 1.5s 内高频翻页被节流拦截，状态不变
        handleDesktopPetCue(
            cue = DesktopPetCue.LIST_END,
            active = agents, paintActive = false, emptyAnchor = null, retryAnchor = null,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = {}, nowMs = 3500L, lastLightCueAtMs = lastLightMs, onUpdateLightCueAtMs = { lastLightMs = it },
        )
        assertEquals(PixelIcon.SWEAT, agents[0].icon)

        // 超过 1.5s 正常触发 LIST_END
        agents[0].mode = BuddyMode.IDLE
        handleDesktopPetCue(
            cue = DesktopPetCue.LIST_END,
            active = agents, paintActive = false, emptyAnchor = null, retryAnchor = null,
            stageW = 1000f, stageH = 720f, edgeX = 48f, topY = 44f, halfH = 10f, density = 1f,
            onSpawnRewardFood = {}, nowMs = 5000L, lastLightCueAtMs = lastLightMs, onUpdateLightCueAtMs = { lastLightMs = it },
        )
        assertEquals(BuddyMode.PEEK_EDGE, agents[0].mode)
        assertEquals(PixelIcon.STAR, agents[0].icon)
    }

    @Test
    fun `侧栏小鱼桌宠默认关闭`() {
        assertFalse("侧栏桌宠默认应为关闭状态", DesktopPetVisibility.visible.value)
    }
}

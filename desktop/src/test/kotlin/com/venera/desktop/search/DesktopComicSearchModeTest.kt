package com.venera.desktop.search

import com.venera.engineprobe.EngineSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 覆盖漫画搜索目标编解码、启停校验、多选降级与聚合泳道四态分层排序。 */
class DesktopComicSearchModeTest {

    @Test
    fun `首次无偏好或非法串一律回落全网聚合`() {
        assertEquals(
            DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true),
            decodeSearchTarget(null),
        )
        assertEquals(
            DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true),
            decodeSearchTarget(""),
        )
        assertEquals(
            DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true),
            decodeSearchTarget("broken:value"),
        )
    }

    @Test
    fun `单源与自定义多源编解码往返一致`() {
        val single = DesktopSearchTarget.Single("picacg")
        assertEquals(single, decodeSearchTarget(encodeSearchTarget(single)))

        val multi = DesktopSearchTarget.Aggregate(linkedSetOf("jm", "copy_manga"), allEnabled = false)
        assertEquals(multi, decodeSearchTarget(encodeSearchTarget(multi)))

        val all = DesktopSearchTarget.Aggregate(setOf("jm", "picacg"), allEnabled = true)
        assertEquals("all", encodeSearchTarget(all))
        assertEquals(DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true), decodeSearchTarget("all"))
    }

    @Test
    fun `启停变更自动剔除已禁用源并按需降级单源`() {
        val savedMulti = DesktopSearchTarget.Aggregate(setOf("picacg", "jm", "wnacg"), allEnabled = false)
        // 停用 wnacg，仍剩 2 源保持自定义聚合
        assertEquals(
            DesktopSearchTarget.Aggregate(linkedSetOf("picacg", "jm"), allEnabled = false),
            resolveSearchTarget(savedMulti, listOf("picacg", "jm", "copy_manga")),
        )
        // 停用到只剩 1 源，自动降级单源
        assertEquals(
            DesktopSearchTarget.Single("jm"),
            resolveSearchTarget(savedMulti, listOf("jm", "copy_manga")),
        )
        // 全网聚合动态跟随全部已启用源
        val allSaved = DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true)
        assertEquals(
            DesktopSearchTarget.Aggregate(linkedSetOf("picacg", "jm", "baozi"), allEnabled = true),
            resolveSearchTarget(allSaved, listOf("picacg", "jm", "baozi")),
        )
    }

    @Test
    fun `多选提交规范化：空集拦下、单源降级、全选标全网`() {
        val enabled = listOf("picacg", "jm", "copy_manga")
        assertNull(normalizeMultiSelection(emptySet(), enabled))
        assertEquals(
            DesktopSearchTarget.Single("jm"),
            normalizeMultiSelection(setOf("jm"), enabled),
        )
        assertEquals(
            DesktopSearchTarget.Aggregate(linkedSetOf("picacg", "copy_manga"), allEnabled = false),
            normalizeMultiSelection(setOf("copy_manga", "picacg"), enabled),
        )
        val all = normalizeMultiSelection(setOf("jm", "picacg", "copy_manga"), enabled)
        assertTrue(all is DesktopSearchTarget.Aggregate && all.allEnabled && all.selectedKeys == linkedSetOf("picacg", "jm", "copy_manga"))
    }

    @Test
    fun `聚合泳道按有结果-检索中-失败-无结果分层且同层保原序`() {
        val order = listOf("picacg", "jm", "copy_manga", "ehentai", "wnacg", "baozi")
        val card = EngineSession.ComicCard("标题", "1", "https://example.invalid/1.jpg")
        val lanes = mapOf(
            "picacg" to DesktopAggregateLaneState("picacg", "Picacg", AggregateLaneStatus.Empty),
            "jm" to DesktopAggregateLaneState("jm", "禁漫", AggregateLaneStatus.Failed, error = "超时"),
            "copy_manga" to DesktopAggregateLaneState("copy_manga", "拷贝", AggregateLaneStatus.Ready, cards = listOf(card)),
            "ehentai" to DesktopAggregateLaneState("ehentai", "EH", AggregateLaneStatus.Loading),
            "wnacg" to DesktopAggregateLaneState("wnacg", "紳士", AggregateLaneStatus.Ready, cards = listOf(card)),
            "baozi" to DesktopAggregateLaneState("baozi", "包子", AggregateLaneStatus.Empty),
        )
        val sorted = sortAggregateLanes(lanes, order).map { it.sourceKey }
        assertEquals(
            listOf("copy_manga", "wnacg", "ehentai", "jm", "picacg", "baozi"),
            sorted,
        )
    }

    @Test
    fun `分级判定区分正常源与R18源并兼容多账号拷贝漫画`() {
        assertTrue(isR18SearchSource("picacg"))
        assertTrue(isR18SearchSource("jm"))
        assertTrue(isR18SearchSource("ehentai"))
        assertTrue(isR18SearchSource("wnacg"))
        assertEquals(false, isR18SearchSource("baozi"))
        assertEquals(false, isR18SearchSource("copy_manga"))
        assertEquals(false, isR18SearchSource("copy_manga_multi_accounts"))
        assertEquals(false, isR18SearchSource("comick"))
    }

    @Test
    fun `分级过滤与分组保持原序并正确命名聚合模式`() {
        val options = listOf(
            DesktopSearchSourceOption("picacg", "Picacg"),
            DesktopSearchSourceOption("baozi", "包子漫画"),
            DesktopSearchSourceOption("copy_manga", "拷贝漫画"),
            DesktopSearchSourceOption("jm", "禁漫天堂"),
            DesktopSearchSourceOption("ehentai", "ehentai"),
        )

        val allGrouped = filterAndGroupSearchSources(options, "", DesktopSearchSourceFilterTab.ALL)
        assertEquals(2, allGrouped.totalSafeCount)
        assertEquals(3, allGrouped.totalR18Count)
        assertEquals(listOf("baozi", "copy_manga"), allGrouped.safe.map { it.key })
        assertEquals(listOf("picacg", "jm", "ehentai"), allGrouped.r18.map { it.key })

        val safeOnly = filterAndGroupSearchSources(options, "", DesktopSearchSourceFilterTab.SAFE)
        assertEquals(listOf("baozi", "copy_manga"), safeOnly.flat.map { it.key })

        val r18Search = filterAndGroupSearchSources(options, "jm", DesktopSearchSourceFilterTab.R18)
        assertEquals(listOf("jm"), r18Search.flat.map { it.key })

        assertEquals("全网聚合", describeAggregateLabel(options.map { it.key }, options))
        assertEquals("正常源聚合", describeAggregateLabel(listOf("baozi", "copy_manga"), options))
        assertEquals("R18 聚合", describeAggregateLabel(listOf("picacg", "jm", "ehentai"), options))
        assertEquals("已选 2 源", describeAggregateLabel(listOf("picacg", "baozi"), options))
    }

    @Test
    fun `全网聚合自动跳过未配置私服且手动勾选仍保留`() {
        val rows = listOf("jm", "kavita", "komga", "lanraragi", "jcomic")
        val emptySettings: (String, String) -> String? = { _, _ -> null }

        // 全选模式：跳过未配置服务器地址的三颗私服
        val allPlan = planAggregateTargets(
            enabledRows = rows,
            aggTarget = DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true),
            keyOf = { it },
            isUnconfiguredSelfHosted = { isUnconfiguredSelfHostedSource(it, emptySettings) },
        )
        assertEquals(listOf("jm", "jcomic"), allPlan.active)
        assertEquals(listOf("kavita", "komga", "lanraragi"), allPlan.skippedSelfHosted)

        // 用户配置了自建 kavita 地址后，全网聚合应纳入 kavita
        val configuredPlan = planAggregateTargets(
            enabledRows = rows,
            aggTarget = DesktopSearchTarget.Aggregate(emptySet(), allEnabled = true),
            keyOf = { it },
            isUnconfiguredSelfHosted = { key ->
                isUnconfiguredSelfHostedSource(key) { sKey, settingKey ->
                    if (sKey == "kavita" && settingKey == "base_url") "https://my.kavita.local:5000" else null
                }
            },
        )
        assertEquals(listOf("jm", "kavita", "jcomic"), configuredPlan.active)
        assertEquals(listOf("komga", "lanraragi"), configuredPlan.skippedSelfHosted)

        // 手动指定子集勾选私服：尊重用户选择，不自动跳过
        val manualPlan = planAggregateTargets(
            enabledRows = rows,
            aggTarget = DesktopSearchTarget.Aggregate(setOf("jm", "kavita"), allEnabled = false),
            keyOf = { it },
            isUnconfiguredSelfHosted = { isUnconfiguredSelfHostedSource(it, emptySettings) },
        )
        assertEquals(listOf("jm", "kavita"), manualPlan.active)
        assertTrue(manualPlan.skippedSelfHosted.isEmpty())
    }

    @Test
    fun `防盗链头策略大小写归一合并并覆盖同名键`() {
        val policy = com.venera.compose.data.platform.ImageHeaderPolicy
        policy.clear()
        try {
            policy.publishForUrls(
                listOf("https://images.jcomic.net/cover/1.jpg"),
                mapOf("referer" to "https://jcomic.net/"),
            )
            val h1 = policy.headersFor("https://images.jcomic.net/cover/2.jpg")
            assertEquals("https://jcomic.net/", h1["referer"])

            // 运行时大小写不同的同类头应覆盖旧键而不是并发两份
            policy.publishForUrls(
                listOf("https://images.jcomic.net/cover/1.jpg"),
                mapOf("Referer" to "https://jcomic.net/new/"),
            )
            val h2 = policy.headersFor("https://images.jcomic.net/cover/2.jpg")
            assertEquals(1, h2.keys.count { it.equals("referer", ignoreCase = true) })
            assertEquals("https://jcomic.net/new/", h2["Referer"])
        } finally {
            policy.clear()
        }
    }
}

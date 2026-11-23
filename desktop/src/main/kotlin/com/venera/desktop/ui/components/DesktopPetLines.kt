package com.venera.desktop.ui.components

/**
 * 角色台词池：8 组 37 态 +批 9 态，每态 1~3 句轮换；A 档元信息模板也集中在这一颗文件。
 *
 * 角色位破例 —— 长句放行、口吻更个性化（黏人、嘴硬心软），
 * 不再为折叠窄轨自我审查成 6 字。折叠态按可用高度动态分列竖排，完整显示整句；
 * 矮到连分列都塞不下时，退到 [shortTable] 的 ≤6 字短版，再矮才不显示。
 * 短版是另写的俏皮话，不是长句截断，不冒出半截话。
 *
 * 不编造画面内容：本地只有封面 URL，没有像素，句子只对标题 / 作者 / 标签 / 章节 / 状态说话。
 */
internal object DesktopPetLines {

    private val table: Map<DesktopPetCue, List<String>> = mapOf(
        // P8 异常
        DesktopPetCue.ERROR to listOf(
            "这张加载失败，我再陪你等等，别急着走嘛",
            "哎呀出错了，才不是我干的，等下再试一次",
        ),
        DesktopPetCue.SCRIPT_ERROR to listOf("源脚本没起来，这一片先空着，我守着"),
        DesktopPetCue.PARSE_FAILED to listOf("这一页我没读懂，等下再陪你试"),
        DesktopPetCue.BLOCKED to listOf("路被挡住啦，先看看别的，我陪你绕"),
        DesktopPetCue.OFFLINE to listOf("这会儿连不上，我就在这儿等你，不走", "网断了呀，先翻翻本地那些，我陪着"),
        DesktopPetCue.LOAD_FAILED to listOf("加载失败，再试一次？我不急，你别急", "这一张慢了点，我继续帮你盯着"),
        // P7 等待 / 警示
        DesktopPetCue.WARNING to listOf("这个先留意一下，我才不是多事", "先确认一下比较好嘛，我等你"),
        DesktopPetCue.MASKED to listOf("这张打了码，我就不念内容啦，乖吧", "打了码的，先不念啦，别瞪我"),
        DesktopPetCue.WAITING_APPROVAL to listOf("等你点头呢，不急，我有的是耐心"),
        // P6 完成
        DesktopPetCue.LOAD_DONE to listOf("齐啦快看，我可是全程盯着呢", "都加载好了，慢慢翻，我不催"),
        DesktopPetCue.DOWNLOAD_ALL_DONE to listOf("全下好了，随时能看，夸我呀", "都下好了，慢慢翻，我陪你"),
        DesktopPetCue.CHAPTER_END to listOf("到最后一页啦，这本看完了，再看一本？"),
        // P5 进行中
        DesktopPetCue.LOADING to listOf("还在加载呢，急什么，我陪你一起等", "图还没好，等一小会儿嘛，不跑"),
        DesktopPetCue.DOWNLOADING to listOf("在下着呢，不急，我替你看着", "排在队里了，一会儿就好，别催"),
        DesktopPetCue.RETRYING to listOf("再试一次，我盯着呢，跑不掉的"),
        // P4 浏览 / 整理 / 互动
        DesktopPetCue.TAP_PET to listOf(
            "又戳我呀…在呢在呢，今天想翻点什么",
            "别戳啦，我又不跑，慢慢挑就是了",
            "哼，就只会戳我，想看什么自己说呀",
        ),
        DesktopPetCue.TOGGLE_FAVORITE to listOf("收好了，回头还能找着，我记性可好了", "记下啦，下次直接翻，不用谢"),
        DesktopPetCue.SWITCH_SOURCE to listOf("换了一边，重新翻翻，我跟着你走", "这边也翻翻嘛，慢慢来，不急"),
        DesktopPetCue.SWITCH_PAGE to listOf("切到这一栏了，我跟着过来了", "到这边啦，慢慢看，我哪儿也不去"),
        DesktopPetCue.SEARCH to listOf("开始找啦，我陪你一页页往下翻", "这一栏翻翻看，找到了我第一个喊你"),
        DesktopPetCue.FILTER to listOf("按你说的筛好了，从这几张看起", "重排完啦，别又说我多事嘛"),
        DesktopPetCue.OPEN_CHAPTER to listOf("接着上次，{chapter}，我没忘", "上次看到 {chapter}，接着来，我陪着"),
        DesktopPetCue.CONTINUE_READING to listOf("接着上次，慢慢看，我坐这儿"),
        DesktopPetCue.TURN_PAGE to listOf("翻过去啦，不着急，我等你", "这一页翻过去了，下一页我还在"),
        DesktopPetCue.ADD_BOOKSHELF to listOf("放书架上了，回头好找，我记着呢"),
        DesktopPetCue.MARK_READ to listOf("记下啦，这本算看过了，不赖嘛"),
        DesktopPetCue.REMOVE_FAVORITE to listOf("移出来啦，想加回来随时说，我不记仇", "先收起来了，想看再翻，我等你"),
        DesktopPetCue.LONG_PRESS to listOf("摸头啊…那我多待一会儿，才不是舍不得走"),
        DesktopPetCue.HOVER to listOf("嗯？想说什么，我听着呢"),
        // P1 待机
        DesktopPetCue.IDLE to listOf("闲着也闲着，我就待这儿陪你，不吵你"),
        DesktopPetCue.NO_TASK to listOf("好半天没动静，我先趴一会儿，你叫我一声我就醒", "闲着呢，你慢慢看，我不走"),
        DesktopPetCue.FIRST_LAUNCH to listOf("第一次见面呀，我待在侧边，不挡你，也不吵你"),
        DesktopPetCue.BACK_HOME to listOf("回来啦，慢慢挑，我一直在呢"),
        // P0 打盹
        DesktopPetCue.AWAY to listOf("安静好一会儿了，我就待着，不走开"),
        DesktopPetCue.DOZING to listOf("我打个盹啊…才不是困了，一叫就醒"),
        // ── 批：空 / 没结果 ──
        DesktopPetCue.SEARCH_EMPTY to listOf("没翻着呀，换个词试试，我陪你再找", "这边翻遍了也没找着，不急，慢慢换"),
        DesktopPetCue.COLLECTION_EMPTY to listOf("这边还空着呢，不急，慢慢攒", "还空空的呀，我先陪你待会儿"),
        DesktopPetCue.LIST_END to listOf("后面没啦，就这些了，我陪你往回翻", "到底了呀，翻到头啦，别划了"),
        DesktopPetCue.NETWORK_RESTORED to listOf("网回来了，接着翻，我哪也没去", "又能连上啦，继续吧，我等着呢"),
        // ── 批：陪伴 / 时间 ──
        DesktopPetCue.LONG_TIME_NO_SEE to listOf("有阵子没见啦，我还守在这儿呢", "回来啦，好久不见，慢慢挑"),
        DesktopPetCue.LATE_NIGHT to listOf("这么晚啦，我陪着，不催你睡", "还醒着呀，那我多陪一会儿"),
        DesktopPetCue.WINDOW_RETURN to listOf("回来啦，我没走开，一直在呢", "又回来啦，接着看吧，我等着"),
        // ── 批：小反应 ──
        DesktopPetCue.TAP_AGAIN to listOf("又戳、又戳…在呢在呢，我又不跑", "别戳啦，手不酸呀，我陪着就是了"),
        DesktopPetCue.QUIET_ON to listOf("那我安静会儿，想我了再叫我", "好啦好啦，我闭嘴，才不是闹别扭"),
    )

    /** 折叠矮窗短版：另写的整句，≤6 字，绝不截断长句。 */
    private val shortTable: Map<DesktopPetCue, String> = mapOf(
        DesktopPetCue.ERROR to "出错了",
        DesktopPetCue.SCRIPT_ERROR to "没起来",
        DesktopPetCue.PARSE_FAILED to "没读懂",
        DesktopPetCue.BLOCKED to "被挡啦",
        DesktopPetCue.OFFLINE to "连不上",
        DesktopPetCue.LOAD_FAILED to "加载失败",
        DesktopPetCue.WARNING to "留意下",
        DesktopPetCue.MASKED to "不念啦",
        DesktopPetCue.WAITING_APPROVAL to "等你呢",
        DesktopPetCue.LOAD_DONE to "齐啦",
        DesktopPetCue.DOWNLOAD_ALL_DONE to "下好了",
        DesktopPetCue.CHAPTER_END to "看完啦",
        DesktopPetCue.LOADING to "等下哈",
        DesktopPetCue.DOWNLOADING to "下着呢",
        DesktopPetCue.RETRYING to "再试试",
        DesktopPetCue.TAP_PET to "在呢",
        DesktopPetCue.TOGGLE_FAVORITE to "收好啦",
        DesktopPetCue.SWITCH_SOURCE to "换一边",
        DesktopPetCue.SWITCH_PAGE to "到这啦",
        DesktopPetCue.SEARCH to "找着呢",
        DesktopPetCue.FILTER to "筛好了",
        DesktopPetCue.OPEN_CHAPTER to "接着看",
        DesktopPetCue.CONTINUE_READING to "接着看",
        DesktopPetCue.TURN_PAGE to "翻过去",
        DesktopPetCue.ADD_BOOKSHELF to "放好了",
        DesktopPetCue.MARK_READ to "记下啦",
        DesktopPetCue.REMOVE_FAVORITE to "移出了",
        DesktopPetCue.LONG_PRESS to "多待会",
        DesktopPetCue.HOVER to "听着呢",
        DesktopPetCue.IDLE to "陪你呢",
        DesktopPetCue.NO_TASK to "趴会儿",
        DesktopPetCue.FIRST_LAUNCH to "初次见",
        DesktopPetCue.BACK_HOME to "回来啦",
        DesktopPetCue.AWAY to "我待着",
        DesktopPetCue.DOZING to "打个盹",
        DesktopPetCue.SEARCH_EMPTY to "没找到",
        DesktopPetCue.COLLECTION_EMPTY to "还空着",
        DesktopPetCue.LIST_END to "到底了",
        DesktopPetCue.NETWORK_RESTORED to "回来了",
        DesktopPetCue.LONG_TIME_NO_SEE to "好久不见",
        DesktopPetCue.LATE_NIGHT to "还醒着",
        DesktopPetCue.WINDOW_RETURN to "没走开",
        DesktopPetCue.TAP_AGAIN to "在呢呀",
        DesktopPetCue.QUIET_ON to "安静啦",
    )

    // A 档：元信息模板。每档模板只含自己那一个占位，避免空字段拼出半截话。
    private val workTitle = listOf(
        "《{title}》呀，这本我陪你慢慢翻，不许中途跑掉",
        "哦是《{title}》，坐下来看吧，我陪着",
    )
    private val workAuthor = listOf(
        "{author} 的作品呀，收着慢慢看，我记下了",
        "{author} 这本，我陪你翻翻，不急",
    )
    private val workTag = listOf(
        "{tag} 这一类，今天先翻这本，我陪你",
        "挑了 {tag} 呀，慢慢看，我不吵",
    )
    private val workFallback = listOf("打开啦，这本要看看吗，我陪你", "嗯，翻翻看，我在这儿")

    private val imageTitle = listOf(
        "《{title}》这张呀，慢慢看，我陪你多看两眼",
        "翻到《{title}》了，别急着划走嘛",
    )
    private val imageAuthor = listOf(
        "{author} 画的这张，收着慢慢看，我不催",
        "是 {author} 的图呀，我陪你多看两眼",
    )
    private val imageTag = listOf(
        "{tag} 这张，翻到了就多看两眼嘛",
        "{tag} 这一类，慢慢挑，我跟着",
    )
    private val imageFallback = listOf("看看这张，图打开了，我陪着", "图打开啦，慢慢看，不走")
    private val imageGroup = listOf("这是一组图呢，一张张慢慢往后翻", "不止一张呀，我陪你一张张来")
    private val masked = listOf("这张打了码，我就不念内容啦，乖吧", "打了码的，先不念啦")

    // B 档：带元信息的状态句。字段为空时回退到 table 里的中性句，不冒出「换到 这边了」。
    private val searchWithTag = listOf(
        "找的是 {tag}？我陪你一页页往下翻",
        "搜 {tag} 呢，我跟着，跑不掉的",
    )
    private val sourceWithName = listOf(
        "换到 {source} 这边了，重新翻翻，我跟着",
        "这边是 {source} 呀，慢慢翻，我陪着",
    )

    // 反搜专属（以图搜图一级页）：按 payload.source 区分，不动共享模板
    private val reverseGot = listOf("收到图了，这就去查", "图收到了，我来比对")
    private val reverseHit = listOf("找到啦，{tag}呢，慢慢比比看", "一共 {tag}，出处都在这儿了")
    private val reverseEmpty = listOf("一张都没找到，换张图试试？", "这图太冷门了，换一张看看")

    private var cursor = 0

    fun pick(cue: DesktopPetCue, payload: DesktopPetPayload?): String = when (cue) {
        DesktopPetCue.LOADING -> if (payload?.source == "以图搜图") render(rotate(reverseGot), payload)
        else rotate(table[cue].orEmpty())
        DesktopPetCue.LOAD_DONE -> if (payload?.source == "以图搜图") render(rotate(reverseHit), payload)
        else rotate(table[cue].orEmpty())
        DesktopPetCue.SEARCH_EMPTY -> if (payload?.source == "以图搜图") render(rotate(reverseEmpty), payload)
        else rotate(table[cue].orEmpty())
        DesktopPetCue.OPEN_WORK -> contentLine(
            payload, workTitle, workAuthor, workTag, workFallback, null,
        )
        DesktopPetCue.OPEN_IMAGE -> contentLine(
            payload, imageTitle, imageAuthor, imageTag, imageFallback, imageGroup,
        )
        DesktopPetCue.SEARCH -> {
            val term = payload?.tag?.trim().orEmpty()
            if (term.isNotEmpty()) render(rotate(searchWithTag), payload)
            else rotate(table[cue].orEmpty())
        }
        DesktopPetCue.SWITCH_SOURCE -> {
            val name = payload?.source?.trim().orEmpty()
            if (name.isNotEmpty()) render(rotate(sourceWithName), payload)
            else rotate(table[cue].orEmpty())
        }
        else -> render(rotate(table[cue].orEmpty()), payload)
    }

    /** 折叠矮窗短版：不消耗轮换游标，免得影响 [pick] 的下一句。 */
    fun short(cue: DesktopPetCue): String = shortTable[cue] ?: "在呢"

    /** 打码 / 组图 / 短标题 / 作者 / 标签 / 空值，按优先级回退；长标题不硬截。 */
    private fun contentLine(
        p: DesktopPetPayload?,
        titleLines: List<String>,
        authorLines: List<String>,
        tagLines: List<String>,
        fallback: List<String>,
        groupLines: List<String>?,
    ): String {
        if (p == null) return rotate(fallback)
        if (p.masked) return rotate(masked)
        if (groupLines != null && p.multiImage) return rotate(groupLines)
        val title = p.title.trim()
        if (title.isNotEmpty() && title.length <= 8) return render(rotate(titleLines), p)
        val author = p.author.trim()
        if (author.isNotEmpty() && author.length <= 8) return render(rotate(authorLines), p)
        val tag = p.tag.trim()
        if (tag.isNotEmpty() && tag.length <= 8) return render(rotate(tagLines), p)
        return rotate(fallback)
    }

    private fun render(template: String, p: DesktopPetPayload?): String {
        if (!template.contains('{')) return template
        val v = p ?: DesktopPetPayload()
        return template
            .replace("{title}", v.title.trim().ifEmpty { "这本" })
            .replace("{author}", v.author.trim().ifEmpty { "这位" })
            .replace("{tag}", v.tag.trim().ifEmpty { "这一类" })
            .replace("{source}", v.source.trim().ifEmpty { "这边" })
            .replace("{chapter}", v.chapter.trim().ifEmpty { "这一话" })
    }

    private fun rotate(list: List<String>): String {
        if (list.isEmpty()) return ""
        val value = list[cursor % list.size]
        cursor++
        return value
    }
}

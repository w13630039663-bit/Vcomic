package com.venera.desktop.gallery.data

import java.io.File

/**
 * 源管理那一屏的**数据层**（批 83）：不启引擎、不开库，只回答三件事 ——
 * 有哪几颗源、每颗叫什么、哪几颗被停用。
 *
 * ## 为什么名字要自己现取
 *
 * 装载那趟确实回一个显示名（`EngineSession.load`），可"列表"这一屏为了报名字去起一颗
 * GraalJS + 整源装载（30 张封面出网）正是批 82.7 那条 `desktopComicEngineNeeded` 反着来。
 * 而对岸那颗 `JsComicSource` 的 `name` 读的就是脚本头部那一行赋值 ⇒ 这里读同一行，
 * 不另造一套口径（同一份脚本两处读成两种形状，就是"桌面看到的与手机不同"那一族成因）。
 */
object DesktopSourceNames {

    /** 顶层那一行 `name = "显示名"`；**冒号**那种（`name: "每週必看"`）是分区名，不算。 */
    private val HEADER = Regex("""^\s*name\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)

    /**
     * 顶层那一行 `version = "1.4.1"`（源脚本头部的版本声明，与对岸 `ComicSourceParser` 读同一行）。
     *
     * 对岸新版源管理每一行念「v1.4.3」那一格吃的就是它；取不到交回**空串**，
     * 屏上那一格整颗不摆（对岸同样有版本缺失的行 —— 编一个版本号是替站方作伪证，与名字同一条纪律）。
     */
    private val VERSION = Regex("""^\s*version\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)

    /** 顶层那一行 `key = "Komiic"`（导入 .js 时从脚本里取唯一标识；取不到由调用点退文件名）。 */
    private val SOURCE_KEY = Regex("""^\s*key\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)

    /** 脚本声明的 key；没有 = `null`。 */
    fun keyOf(script: String): String? =
        SOURCE_KEY.find(script)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /** 取不到就交回 `null`，让调用点念 key —— 编一个站名等于替站方作伪证。 */
    fun displayNameOf(script: String): String? =
        HEADER.find(script)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    /** 版本那一行；没有 = 空串（屏上不摆那一格，不编版本号）。 */
    fun versionOf(script: String): String =
        VERSION.find(script)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }.orEmpty()

    /**
     * 这一颗源声明里的第一个站点主机（首页「源状态」真测速的探测目标）。
     *
     * 取**脚本里第一个 `https://主机` 形** —— 源的 API/站点域都写在脚本里，第一枚就是它的
     * 主域（镜像族也是从主域派生）。取不到交回空串：那一行测速腿不发车，屏上念「未测」，
     * 不编一个假主机去探（编主机与编版本号同罪）。
     */
    fun apiHostOf(script: String): String =
        HOST.find(script)?.groupValues?.get(1).orEmpty()

    private val HOST = Regex("""https://([A-Za-z0-9.-]+)""")

    /**
     * 按 key 现取那一颗的脚本原文（仓库目录与包内两种落点都试，**安静地**试；取不到交回 null）。
     *
     * ⚠️ 不许改走 `EngineAssets.readText`：那一颗每读一份就打一行 `D_资源 …`，
     * 列表要读 34 份 ⇒ stdout 会被 34 行资源读数糊满，而它一条都不是这一屏的账。
     *
     * 单独一颗而不并进 [displayNameOfKey]：屏上同一趟还要读第二样东西（[cookieFieldsOf] 那份名单），
     * 两处各读一遍文件就是两条轨 —— 读到一半被改的话，屏上会摆出名与名单不配套的格子。
     *
     * 起多了一枚 [overrideDir]：**用户改写过的那一颗，这里必须读到改写后的那一篇**。
     * 装载吃新脚本而清单念旧名字，就是"看着还是出厂那一颗、跑的已经不是它"——
     * 与引擎侧 `EngineSession.loadText` 同一个取用次序（覆盖层 → 包内 → 仓库），两处不许漂。
     */
    fun scriptOf(assetDir: File, key: String, overrideDir: File? = null): String? {
        overrideDir?.let { dir ->
            DesktopSourceOverrides.textOf(dir, key)?.let { return it }
        }
        val file = File(assetDir, "sources/$key.js")
        if (file.isFile) runCatching { return file.readText() }.getOrNull()?.let { return it }
        return runCatching {
            DesktopSourceNames::class.java.getResourceAsStream("/sources/$key.js")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    /** 按 key 现取那一颗的显示名（取不到交回 null，让调用点念 key）。 */
    fun displayNameOfKey(assetDir: File, key: String, overrideDir: File? = null): String? =
        scriptOf(assetDir, key, overrideDir)?.let { displayNameOf(it) }

    /**
     * 手填 cookie 那一形的字段名单（`account.loginWithCookies.fields`）。
     *
     * 没有那一形 ⇒ 交回**空表**，而空表在屏上的意思是"这一源不给那一格"（批 88 那条要求：
     * 不许摆一枚按不动的框）。名单从脚本文本现取，桌面一个字都不重述 —— 站方改了字段名，
     * 屏上那几格跟着变；写死一份名单的话，改的是脚本而屏上照旧摆老几格，答案永远过不了。
     *
     * `fields` 只在 `loginWithCookies` 之后那 **400 个字符**里找：窗口不给的话，
     * 同一颗源里后面别的 `fields:`（选项列表那一族）会被当成 cookie 名单。
     */
    /**
     * `tags`/`theme` 之外，源在详情答复里还会**按组**报标签
     * （jm 那张截图的「标签: / 原作: / 出演: / 浏览:」）。把
     * 详情页标签改成照组摆 —— 桌面此前把组名扔掉、只留摊平值，这里把那份账捡回来。
     *
     * 形状逐条对齐解析侧（`parseComicLoadInfo`）：List 摊平进「未分组」那一份；
     * Map 按组收（**组名原样**，不翻译不编名）。两形状并存时 Map 组在前、未分组殿后 ——
     * 摊平那串没有组名可念，挂在最前反而像漏了标题。
     */
    fun tagGroupsOf(rawTags: Any?): List<Pair<String, List<String>>> {
        return when (rawTags) {
            is Map<*, *> -> rawTags.entries.mapNotNull { (k, v) ->
                val group = k.toString().takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val items = when (v) {
                    is List<*> -> v.mapNotNull { it?.toString() }
                    else -> listOfNotNull(v?.toString())
                }.filter { it.isNotBlank() }
                if (items.isEmpty()) null else group to items
            }
            else -> emptyList()
        }
    }

    fun cookieFieldsOf(script: String): List<String> {
        val block = COOKIE_FIELDS.find(script)?.groupValues?.get(1) ?: return emptyList()
        return QUOTED.findAll(block).mapNotNull { it.groupValues[1].takeIf { name -> name.isNotBlank() } }.toList()
    }

    /**
     * 源是否声明了账号密码登录（官方 `account.login(account, pwd)`）。
     *
     * 这里只看脚本文本，不启动引擎：源管理列表本来就不该为了画一枚按钮去装载 34 份脚本。
     * 正则限定在 `account = {` 之后的一段窗口内，避免把分区里同名的 `login:` 当成账号能力。
     */
    fun supportsPasswordLoginOf(script: String): Boolean =
        PASSWORD_LOGIN.containsMatchIn(script)

    /** 源是否声明了应用内网页登录（官方 `account.loginWithWebview`）。 */
    fun supportsWebLoginOf(script: String): Boolean =
        WEB_LOGIN.containsMatchIn(script)

    private val COOKIE_FIELDS = Regex(
        """loginWithCookies\s*:\s*\{[\s\S]{0,400}?fields\s*:\s*\[([^\]]*)\]""",
    )

    private val PASSWORD_LOGIN = Regex(
        """\baccount\s*=\s*\{[\s\S]{0,5000}?\blogin\s*:\s*(?:async\s*)?\(""",
    )

    private val WEB_LOGIN = Regex(
        """\baccount\s*=\s*\{[\s\S]{0,5000}?\bloginWithWebview\s*:\s*\{""",
    )

    private val QUOTED = Regex(""""([^"]+)"""")

    /** 按 key 现取那份名单（脚本取不到 = 空表 = 屏上不给那一格）。 */
    fun cookieFieldsOfKey(assetDir: File, key: String, overrideDir: File? = null): List<String> =
        scriptOf(assetDir, key, overrideDir)?.let(::cookieFieldsOf) ?: emptyList()
}

/** 列表里的一行。[title] 取不到名字时就是 [key]（屏上永远有字可念，且那是实话）。 */
data class DesktopSourceRow(
    val key: String,
    val title: String,
    val enabled: Boolean,
    /** 这一颗在用户覆盖层里有改写后的那一篇（源编辑）。 */
    val overridden: Boolean = false,
    /**
     * 手填 cookie 那一形的字段名单（脚本自己声明的 `account.loginWithCookies.fields`）。
     *
     * 空表 = 这一源不给那一格 ⇒ 屏上**不摆**「登录」那枚把手（批 88 那条：摆一枚按不动的框比不摆更坏
     * —— 用户会以为填了就能登，而源根本没有那一路）。现量 34 颗里只有 3 颗有那一形
     * （ehentai 四枚 / lanraragi 一枚 / mycomic 一枚），所以那一扇门本来就该是少数几颗才有。
     */
    val cookieFields: List<String> = emptyList(),
    /** 源脚本声明了 `account.login(account, pwd)`。 */
    val supportsPasswordLogin: Boolean = false,
    /** 源脚本声明了 `account.loginWithWebview`（桌面当前没有内嵌浏览器宿主）。 */
    val supportsWebLogin: Boolean = false,
    /**
     * 这一颗源声明里的站点主机（首页「源状态」真测速的探测目标）。
     * 空串 = 脚本里提不出主机 ⇒ 测速腿不发车，屏上念「未测」。
     */
    val host: String = "",
)

object DesktopSourceCatalog {

    // 保持 public 以供 VeneraDesktop 签名引用；其余辅助对象保持 internal 避免扩大可见面。

    /**
     * 列表的一行。[title] 取不到名字时就是 [key]（屏上永远有字可念，且那是实话）。
     *
     * ⚠️ 参数翻了方向：`enabled` 吃的是**开着的那一份**（显式启用清单），
     * 而不是从前那份"停用的那些"。默认全开的旧模型已被主人的裁决作废（理由写在
     * `DesktopSourceState` 的类注释里：大部分源没登录、成人向站不该开箱即亮）。
     * 停用**不是删除**：那颗还在列表里，只是标着停用（否则用户会以为源被抹掉了）。
     */
    fun rows(
        keys: List<String>,
        names: Map<String, String>,
        enabled: Set<String>,
        overridden: Set<String> = emptySet(),
        /** 每颗源自己那份 cookie 字段名单（缺键 = 空表 = 不给那一格）。 */
        cookieFields: Map<String, List<String>> = emptyMap(),
        supportsPasswordLogin: Map<String, Boolean> = emptyMap(),
        supportsWebLogin: Map<String, Boolean> = emptyMap(),
        /** 每颗源声明里的站点主机（首页源状态真测速）。 */
        hosts: Map<String, String> = emptyMap(),
    ): List<DesktopSourceRow> = keys.map { key ->
        DesktopSourceRow(
            key = key,
            title = names[key] ?: key,
            enabled = key in enabled,
            overridden = key in overridden,
            cookieFields = cookieFields[key].orEmpty(),
            supportsPasswordLogin = supportsPasswordLogin[key] == true,
            supportsWebLogin = supportsWebLogin[key] == true,
            host = hosts[key].orEmpty(),
        )
    }

    /**
     * stdout 那一行：清单有几颗、开着几颗、在用哪一颗、而**在用的那颗是不是关着的**。
     *
     * 最后那一格必须有读数：关掉了正在用的那颗时屏上照旧摆着它，没有这一句就是"看着在用而它是关的"。
     */
    fun reading(sourceKey: String, rows: List<DesktopSourceRow>): String {
        val inUseDisabled = rows.firstOrNull { it.key == sourceKey }?.enabled == false
        return "D_源管理 清单=${rows.size} 开着=${rows.count { it.enabled }} 在用=$sourceKey 停用=${if (inUseDisabled) "在用" else "无"}"
    }
}

/** 选源那一下落到哪一档。四档分开，因为修法各不一样。 */
internal enum class DesktopSourcePick {
    /** 照它走（启用着的那颗）。 */
    Exact,

    /** 那一颗被停用了 ⇒ 落回第一颗启用着源。 */
    FellBack,

    /** 键压根不存在（拼错 / 撤了的那颗）⇒ 不是"停用"，念法必须不同。 */
    UnknownKey,

    /** 一颗启用着的都没有 ⇒ 不许硬挑一颗关着的当"在用"。 */
    NoneEnabled,
}

/** [key] = 接下来该装载的那一颗（`NoneEnabled` 时是空串：宁可不装，也不装一颗用户关掉的）。 */
internal data class DesktopSourceOutcome(
    val kind: DesktopSourcePick,
    val key: String,
    val reading: String,
)

/**
 * 选源的落点判断（纯函数，不碰引擎也不碰盘）。
 *
 * 这一颗是"启用"那枚开关**真生效**的地方：只在列表上标个亮而装载照旧，就是本仓那一族假开关。
 *
 * ⚠️ 参数从「停用的那些」翻成「开着的那些」（默认档的理由见 [DesktopSourceState]）。
 */
internal object DesktopSourceSelection {

    fun resolve(keys: List<String>, enabled: Set<String>, requested: String): DesktopSourceOutcome {
        val fallback = keys.firstOrNull { it in enabled }
        if (requested !in keys) {
            return if (fallback == null) {
                DesktopSourceOutcome(
                    DesktopSourcePick.NoneEnabled, "",
                    "D_源 没这颗 $requested 且清单里全关着 ⇒ 没有可用的源",
                )
            } else {
                DesktopSourceOutcome(
                    DesktopSourcePick.UnknownKey, fallback,
                    "D_源 没这颗 $requested ⇒ 用 $fallback",
                )
            }
        }
        if (requested in enabled) {
            return DesktopSourceOutcome(DesktopSourcePick.Exact, requested, "D_源 用=$requested")
        }
        return if (fallback == null) {
            DesktopSourceOutcome(
                DesktopSourcePick.NoneEnabled, "",
                "D_源 全关着（$requested 也在里面）⇒ 漫画域没有可用的源",
            )
        } else {
            DesktopSourceOutcome(
                DesktopSourcePick.FellBack, fallback,
                "D_源 回落 $requested→$fallback（$requested 没开着）",
            )
        }
    }

    /** 同一颗开关的两档：点一下开，再点一下关（叠成两份就永远关不掉）。 */
    fun toggle(enabled: Set<String>, key: String): Set<String> =
        if (key in enabled) enabled - key else enabled + key

    /**
     * 这一颗关不关得掉：已经关着的当然可以再点开，而**只剩一颗开着**的那一颗不许关。
     *
     * 那条不是装饰：清单全关之后 [resolve] 只能交回空手（不许硬挑一颗关着的当"在用"），
     * 于是漫画域没有任何一屏装得起来 —— 让用户一按就到那一步，等于给了一枚把自己锁在门外的开关。
     */
    fun canDisable(keys: List<String>, enabled: Set<String>, key: String): Boolean =
        key !in enabled || keys.count { it in enabled } > 1
}

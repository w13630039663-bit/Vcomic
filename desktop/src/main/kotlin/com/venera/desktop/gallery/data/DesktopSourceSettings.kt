package com.venera.desktop.gallery.data

/**
 * 源脚本 `settings` 块的**纯解析**（「源设置」段的读侧）。
 *
 * ## 为什么是正则扫描而不是起引擎
 *
 * 设置声明（jm 的 Refresh Domain List / Api Domain 那几行）就写在脚本文本里，
 * 与名字、版本、cookieFields 那几格同一层 —— 源管理这一屏刻意不起引擎
 * （批 82.7 那条），屏上要的只是"这一颗源声明了哪些设置项"，读一遍原文扫出来就够。
 * 起 GraalJS 装载只为读几行声明，是把"能改"变成"要等引擎那把锁"的那一族代价。
 *
 * 解析语义对齐对岸 `ComicSourceParser`（它同样从脚本文本里抠 `settings`）：
 * 认不出的档整颗不摆 —— 摆一枚不知道按了会跑什么代码的钮，比不摆更坏。
 *
 * ## 与 [DesktopSourceNames] 同一条纪律
 *
 * 输入是**脚本文本**，不是 key：读文件那一跳由调用点（壳）做，
 * 这一颗只管把文本扫成结构。屏自己拿 key 去读文件就是第二条轨
 * （读到一半被改的话，屏上会摆出与实际装载不配套的格子）。
 */
object DesktopSourceSettings {

    /**
     * 一枚声明项。
     *
     * type 只认四档（对岸 `SettingsItem` 那一族里桌面成立的那些）：
     * `switch`（开关）/ `select`（下拉）/ `callback`（按钮，跑了源脚本里那颗回调）/
     * `input`（文本框）。其余档（divider 之类纯装饰）整颗不摆。
     */
    data class Def(
        val settingKey: String,
        val title: String,
        val type: String,
        /** select 档的候选值（`options` 里每枚的 `value`；源没给 label，屏上就念值本身）。 */
        val options: List<String> = emptyList(),
        /** callback 档那枚按钮的字（源没写 buttonText 时屏上念「执行」）。 */
        val buttonText: String = "",
        /**
         * 出厂档（脚本里的 `default`）。**字符串化**：switch 是 "true"/"false"，
         * select/input 是原样字串。空串 = 没写（屏上以「没写」念，不编一个档出来）。
         */
        val default: String = "",
    )

    /** `settings = {` 那一行的起点（认不到 = 这颗源没有设置块）。 */
    private val SETTINGS_HEAD = Regex("""\bsettings\s*=\s*\{""")

    fun definitionsOf(script: String): List<Def> {
        val head = SETTINGS_HEAD.find(script) ?: return emptyList()
        val body = braceBodyOf(script, head.range.last) ?: return emptyList()
        return topLevelEntriesOf(body).mapNotNull { (entryKey, entryBody) ->
            val type = Regex("""type\s*:\s*"(\w+)"""").find(entryBody)?.groupValues?.get(1) ?: return@mapNotNull null
            val title = Regex("""title\s*:\s*"([^"]+)"""").find(entryBody)?.groupValues?.get(1)
                ?: return@mapNotNull null
            when (type) {
                "switch" -> Def(
                    settingKey = entryKey,
                    title = title,
                    type = type,
                    default = defaultValueOf(entryBody) ?: "false",
                )
                "select" -> Def(
                    settingKey = entryKey,
                    title = title,
                    type = type,
                    options = optionValuesOf(entryBody),
                    default = defaultValueOf(entryBody) ?: "",
                )
                "callback" -> Def(
                    settingKey = entryKey,
                    title = title,
                    type = type,
                    buttonText = Regex("""buttonText\s*:\s*"([^"]*)"""").find(entryBody)
                        ?.groupValues?.get(1).orEmpty(),
                )
                "input" -> Def(
                    settingKey = entryKey,
                    title = title,
                    type = type,
                    default = defaultValueOf(entryBody) ?: "",
                )
                // divider 之类纯装饰档：不是控件，摆出来就是一枚没有交互对象的东西。
                else -> null
            }
        }
    }

    /**
     * 从 `openBraceIndex`（指向 `{` 那个字符）起做一次**括号配平**扫描，
     * 交回块体（不含首尾两枚大括号）。字符串里的括号不计数（引号状态机跳过）；
     * 扫到串尾没配平 = 声明被截断，交回 null（整块不摆，而不是摆半截）。
     */
    private fun braceBodyOf(script: String, openBraceIndex: Int): String? {
        var depth = 0
        var inString: Char? = null
        var escaped = false
        for (i in openBraceIndex until script.length) {
            val c = script[i]
            if (inString != null) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == inString -> inString = null
                }
                continue
            }
            when (c) {
                '"', '\'' -> inString = c
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return script.substring(openBraceIndex + 1, i)
                }
            }
        }
        return null
    }

    /** 块体里 depth=1 的那几枚 `键: { … }`（键与各自的体）。 */
    private fun topLevelEntriesOf(body: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        var depth = 0
        var inString: Char? = null
        var escaped = false
        var keyStart = -1
        var entryOpen = -1
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (inString != null) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == inString -> inString = null
                }
                i++
                continue
            }
            when {
                c == '"' || c == '\'' -> inString = c
                c == '{' -> {
                    depth++
                    if (depth == 1) entryOpen = i
                }
                c == '}' -> {
                    depth--
                    if (depth == 0 && entryOpen >= 0) {
                        val header = body.substring(keyStart.coerceAtLeast(0), entryOpen)
                        val key = Regex("""(\w+)\s*:\s*$""").find(header)?.groupValues?.get(1)
                        if (key != null) out += key to body.substring(entryOpen + 1, i)
                        keyStart = -1
                        entryOpen = -1
                    }
                }
                depth == 0 && !c.isWhitespace() && keyStart < 0 -> keyStart = i
            }
            i++
        }
        return out
    }

    /** `default:` 右侧那一份，字符串化（剥引号；数字/布尔原样取到行尾或逗号）。 */
    private fun defaultValueOf(entryBody: String): String? {
        val m = Regex("""default\s*:\s*("(?:[^"\\]|\\.)*"|'[^']*'|[^,\n}]+)""").find(entryBody) ?: return null
        return m.groupValues[1].trim().trim('"', '\'').takeIf { it.isNotBlank() }
    }

    /** select 档 `options` 数组里的候选值：`{value: 'x'}` 与裸字符串两种形状都认。 */
    private fun optionValuesOf(entryBody: String): List<String> {
        val block = Regex("""options\s*:\s*\[([^\]]*)]""").find(entryBody)?.groupValues?.get(1)
            ?: return emptyList()
        return Regex("""value\s*:\s*['"]([^'"]+)['"]|^['"]([^'"]+)['"]""", RegexOption.MULTILINE)
            .findAll(block)
            .mapNotNull { it.groupValues[1] ?: it.groupValues[2] }
            .toList()
    }

    /**
     * 那颗罐里的设置值**字符串化**（屏只搬字符串；写侧照同一条约定还原）：
     * Boolean → "true"/"false"（源脚本里 `if (setting)` 吃的是真布尔，
     * 写回 "false" 字串会在 JS 里被当真值 —— 写侧必须还原成布尔，见装配点）；
     * 其余 `toString()`；null 不进表（屏上退回出厂档）。
     */
    fun stringifyValues(raw: Map<String, Any?>): Map<String, String> = raw.mapNotNull { (k, v) ->
        when (v) {
            null -> null
            is Boolean -> k to if (v) "true" else "false"
            else -> k to v.toString()
        }
    }.toMap()

    /** 写侧的还原：switch 那两串必须落回真布尔（理由见 [stringifyValues]）。 */
    fun parseValueForWrite(value: String): Any? = when (value) {
        "true" -> true
        "false" -> false
        else -> value
    }
}

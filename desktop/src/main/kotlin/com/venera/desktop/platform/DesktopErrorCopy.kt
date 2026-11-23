package com.venera.desktop.platform

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 错误态归档：**屏上只念归档后的短句与图标，原文只进日志**。
 *
 * 全桌面所有错误/超时/断网/未登录的上屏信息都从这两枚映射走——谁直接把
 * `t.javaClass.simpleName + t.message` 拼进屏，谁就是把日志当 UI 摆（清掉的那族）。
 * 分档只看成因类型与关键词，不透传原文：原文长短不可控，屏上那行必须短。
 */
enum class DesktopErrorKind(val label: String) {
    OFFLINE("连接失败"),
    TIMEOUT("网络超时"),
    /**
     * 网络层被阻断（实测登记）：pixiv / yande / gelbooru 这类域名的典型症状是
     * DNS 与 TCP 都通、卡在 **TLS 握手被重置**（`SSLException: handshake ...` / `handshake eof`）。
     * 与 [OFFLINE]（连不上）和 [RESTRICTED]（站点 403）都不同：这一档**只能靠代理**绕开，
     * 所以单独成档，屏上给的是"需配置代理"这句可执行提示，而不是笼统的"操作未完成"。
     */
    BLOCKED("网络受阻"),
    RESTRICTED("源访问受限"),
    RATE_LIMITED("请求过频"),
    AUTH_EXPIRED("登录已过期"),
    SERVER_DOWN("源服务异常"),
    NOT_LOGGED_IN("未登录"),
    FAILED("操作未完成"),
}

/** 按异常类型归档；认不出落 [DesktopErrorKind.FAILED]，不编成因。 */
fun desktopErrorKindOf(t: Throwable?): DesktopErrorKind = when (t) {
    is UnknownHostException, is ConnectException -> DesktopErrorKind.OFFLINE
    is SocketTimeoutException -> DesktopErrorKind.TIMEOUT
    // ⚠️ SSLException 必须排在关键词归档之前：它的消息里常带 "connection reset"，
    // 而下面那串关键词里 "connect" 会把 OFFLINE 抢走（那会让"需代理"被念成"连接失败"）。
    is javax.net.ssl.SSLException -> DesktopErrorKind.BLOCKED
    else -> desktopErrorKindOfText(t?.message.orEmpty() + " " + t?.javaClass?.simpleName.orEmpty())
}

/** 常规境内网络需走代理访问的海外内置源 key 集合，供站点提示与直连快速失败共用。 */
val PROXY_REQUIRED_SOURCE_KEYS: Set<String> = setOf(
    "comic_walker",
    "comick",
    "ehentai",
    "gelbooru",
    "hcomic",
    "hitomi",
    "jcomic",
    "manga_dex",
    "mh18",
    "nhentai",
    "shonen_jump_plus",
)

/** 按原文关键词归档（账本里只存了字符串的那一路用这枚）。 */
fun desktopErrorKindOfText(raw: String): DesktopErrorKind {
    val s = raw.lowercase()
    return when {
        // 明确标注需配置代理 / 直连拦截 —— 必须排在 timeout 之前。
        "需配置代理" in raw || "直连模式" in raw -> DesktopErrorKind.BLOCKED
        // TLS 握手被重置 / 证书链被中断 —— 这一族只能靠代理，先于下面所有档判。
        "handshake" in s || "ssl" in s || "tls" in s || "certificate" in s ||
            "connection reset" in s || "handshake eof" in s -> DesktopErrorKind.BLOCKED
        "timeout" in s || "timed out" in s || "没返回" in raw || "超时" in raw -> DesktopErrorKind.TIMEOUT
        "未登录" in raw || "not logged" in s || "need login" in s -> DesktopErrorKind.NOT_LOGGED_IN
        "401" in s || "unauthorized" in s -> DesktopErrorKind.AUTH_EXPIRED
        "403" in s || "forbidden" in s || "challenge" in s -> DesktopErrorKind.RESTRICTED
        "429" in s || "rate" in s -> DesktopErrorKind.RATE_LIMITED
        "500" in s || "502" in s || "503" in s || "server error" in s -> DesktopErrorKind.SERVER_DOWN
        "unknownhost" in s || "resolve" in s || "connect" in s || "网络" in raw -> DesktopErrorKind.OFFLINE
        else -> DesktopErrorKind.FAILED
    }
}

/**
 * 那一档的可执行提示（屏上副标题用；没有建议就给 `null`，不摆废话）。
 *
 * pixiv / booru 那几屏裸连时只会干等加载，看不出是网络被阻断。
 * 现在失败态带一句"要做什么"，用户不用猜。
 */
fun desktopErrorHintOf(kind: DesktopErrorKind): String? = when (kind) {
    DesktopErrorKind.BLOCKED -> "需配置代理后重试"
    DesktopErrorKind.OFFLINE -> "检查网络后重试"
    DesktopErrorKind.TIMEOUT -> "稍后重试"
    DesktopErrorKind.NOT_LOGGED_IN -> "先登录该源"
    DesktopErrorKind.AUTH_EXPIRED -> "重新登录后重试"
    DesktopErrorKind.RESTRICTED -> "检查该源权限"
    else -> null
}

/**
 * 上屏短句：归档得**档位短句**；认不出档（= 大概率是业务句而非异常串）交回原文截 60 字。
 * Toast / note 那族"账里只有一句话"的落点用这枚——业务话不吞，异常串不透。
 */
fun desktopErrorShortText(raw: String): String {
    val kind = desktopErrorKindOfText(raw)
    return if (kind == DesktopErrorKind.FAILED) raw.take(60) else kind.label
}

/**
 * 失败态上屏那一句：**归档短句 + 可执行提示**（没有建议就只给短句）。
 *
 * 原文不进屏（本文件头注那条口径），调用点自己 `println` 进日志。
 * pixiv / booru 裸连时屏上原本只有"取不到/加载中"，看不出是网络被阻断；
 * 统一走这一枚后，TLS 握手被重置会念成「网络受阻 · 需配置代理后重试」。
 */
fun desktopFailureNoteOf(t: Throwable?): String {
    val kind = desktopErrorKindOf(t)
    val short = desktopErrorShortText("${t?.javaClass?.simpleName.orEmpty()} ${t?.message.orEmpty()}")
    return desktopErrorHintOf(kind)?.let { "$short · $it" } ?: short
}

/** 带源 key 上下文的失败文案：需代理源若表现为超时或断连，统一提示需配置代理后重试。 */
fun desktopFailureNoteForSource(t: Throwable?, sourceKey: String?): String {
    val kind = desktopErrorKindOf(t)
    val effectiveKind = if (
        sourceKey != null &&
        sourceKey in PROXY_REQUIRED_SOURCE_KEYS &&
        (kind == DesktopErrorKind.TIMEOUT || kind == DesktopErrorKind.OFFLINE)
    ) {
        DesktopErrorKind.BLOCKED
    } else {
        kind
    }
    val short = effectiveKind.label
    return desktopErrorHintOf(effectiveKind)?.let { "$short · $it" } ?: short
}

/**
 * 只有字符串、没有异常对象的那一路（源站信封里的 `__err`、落盘读失败句）的上屏成品。
 *
 * 与 [desktopFailureNoteOf] 同一口径：认得出档位就念档位短句 + 提示，认不出（= 大概率是
 * 站方业务句，如"该源没有 search"）原样交回，不把业务话改写掉。
 *
 * @param raw 源站/落盘给出的那一段原文。
 */
fun desktopFailureNoteOfText(raw: String): String {
    val kind = desktopErrorKindOfText(raw)
    val short = if (kind == DesktopErrorKind.FAILED) raw.take(60) else kind.label
    return desktopErrorHintOf(kind)?.let { "$short · $it" } ?: short
}

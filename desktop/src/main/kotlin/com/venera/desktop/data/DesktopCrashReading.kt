package com.venera.desktop.data

/**
 * 崩了要念得出**是谁、为什么**（批 53 · 基础框架那一层）。
 *
 * ## 这一格为什么要单独做
 *
 * 本仓已经吃过两次"崩了但读不出来"：批 47 那颗 `ImageLoader` 缺 dispatcher 的崩溃，屏上是**一枚空白
 * Error 框**（Compose 内容不进 UIA 树 ⇒ 只有像素，而那个框里是空的），成因只在 stdout 那一大段裸
 * stack 里；手册 §0.1 也记着"崩溃的读数不在屏上"。取证那一跑是在几十行 `D_` 读数里挑，
 * 一段裸 stack 既没归属（哪颗线程、第几笔）、也没法 grep。
 *
 * ## 两条纪律
 *
 * 1. **一行**：换行与制表全压掉、长度有上限，且截断要说出来（不说就是读数在撒谎）。
 * 2. **凭据不进读数**：崩溃消息里最常出现的正是那条**带 query 的 URL**（反搜键与 Gelbooru 的
 *    `api_key`/`user_id` 都走 query）⇒ 剔除住在这颗件里，不留给调用方自觉。
 *    键名保留、只打值：要说得出"是哪一枚被挡了"，也不能把整条消息顺手抹平。
 */
internal fun desktopCrashReading(threadName: String, error: Throwable, sequence: Int): String {
    val oneLine = desktopOneLineMessage(error)
    val body = if (oneLine.length > CRASH_MESSAGE_CAP) {
        oneLine.take(CRASH_MESSAGE_CAP) + "…（截断）"
    } else {
        oneLine
    }
    val depth = runCatching { generateSequence(error.cause) { it.cause }.count() }.getOrDefault(0)
    val frame = desktopFailureFrame(error)
    return buildString {
        append("D_异常 线程=").append(threadName).append(" 次=").append(sequence)
        append(" 因=").append(desktopFailureType(error))
        if (body.isNotBlank()) append(": ").append(body)
        append(" 链=").append(depth).append(" 栈=").append(frame ?: "没有")
    }
}

/** 异常类型名（读不出也要给一个词，空着会被读成"读数件坏了"）。 */
internal fun desktopFailureType(error: Throwable): String =
    runCatching { error.javaClass.simpleName.ifBlank { "匿名异常" } }.getOrDefault("未知类型")

/**
 * 一笔失败的消息，压成一行并**先过一遍凭据剔除**（批 53 那条：最常出现在这里的正是带 query 的 URL）。
 *
 * 抽出来给两批共用：崩溃那一行（[desktopCrashReading]）与"动作没成"那一行（批 54 的复制读数）。
 * 两处各写一份折叠与脱敏，就是"只改了一处"那一族。
 */
internal fun desktopOneLineMessage(error: Throwable): String = runCatching {
    desktopScrubSecrets(error.message.orEmpty().replace(Regex("\\s+"), " ").trim())
}.getOrDefault("消息读不出")

/**
 * 一笔失败"发生在哪一帧"：栈顶那一帧（有文件名用文件名，没有用类名）。
 * 拿不到（合成栈、空栈）交回 `null`，由调用方念成"没有"。
 */
internal fun desktopFailureFrame(error: Throwable): String? = runCatching {
    error.stackTrace.firstOrNull()?.let { "${it.fileName ?: it.className}:${it.lineNumber}" }
}.getOrNull()

/** 一行读数的消息上限。超过就截，并在末尾明说"截断"。 */
private const val CRASH_MESSAGE_CAP = 200

/**
 * 把 query 里那几枚凭据参数的**值**打掉，键名留着。
 *
 * 为什么名单里有 `key` 这一枚光杆：站方两种写法都在用（`api_key=` 与 `key=`），
 * 而漏掉一种就是"读数里躺着一条能用的键"。
 */
internal fun desktopScrubSecrets(text: String): String =
    SECRET_PARAM.replace(text) { match -> "${match.groupValues[1]}=***" }

private val SECRET_PARAM =
    Regex("(?i)([?&]?(api[_-]?key|apikey|token|password|passwd|user[_-]?id|uid|key))=([^&\\s]+)")

/** 第几笔：EDT 上同一颗崩会连着来很多次，没有这枚计数就分不清"一次大崩"与"每分钟一崩"。 */
private val crashCount = java.util.concurrent.atomic.AtomicInteger()

/**
 * 装上那颗全局 handler。**必须在开窗之前**（装在 `application {}` 之后就管不到启动那一段，
 * 而批 47 那次崩的正是启动阶段）。
 *
 * 改道（三件事都照办）：
 * - **报错不自动关程序**：不再交还旧 handler —— AWT 那枚传统报错框撤掉，EDT 上的异常
 *   打完点照常返回，事件循环继续跑；非 EDT 线程的线程自然死亡与 JVM 默认同档。
 * - **落 log 在目录**：整段 stack 追加写 `logs/desktop-error.log`（见 [appendDesktopErrorLog]）。
 * - **唯一出口是桌宠**：往 [DesktopErrorBus] 塞一笔，桌宠订阅它发 ERROR 台词；
 *   [DesktopErrorToastOverlay] 只做流的自清，不渲染任何屏上元素，窗口期异常也不弹确认框。
 *
 * 照旧保留的两件事：
 * - **先念那一行**（归属：哪颗线程、第几笔、什么因、链多深、顶帧）；
 * - **再把整段 stack 照打** —— 替掉 JVM 那份 handler 之后，它自带的"打印整段 stack"就没了
 * （stdout 里只剩我们那一行，`Exception in thread` 那一段整个不见）。
 */
internal fun installDesktopCrashHandler() {
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        val reading = desktopCrashReading(thread.name, error, crashCount.incrementAndGet())
        println(reading)
        error.printStackTrace()
        val logPath = appendDesktopErrorLog(thread.name, error)
        DesktopErrorBus.push(thread.name, error, logPath)
    }
}

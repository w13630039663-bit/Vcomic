package com.venera.desktop.follow

import com.venera.engineprobe.EngineSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 生产取数口：按源复用一个临时引擎会话，一轮巡检结束统一释放。
 *
 * 为什么不复用窗口那颗常驻 [EngineSession]：它会随前台浏览切换源、且调用方共享同一把锁；
 * 巡检复用会把用户正在看的详情/阅读器挤掉。临时会话复用共享 Cookie 罐与连接池，
 * 只隔离 JS 上下文，成本远低于每本新建。
 *
 * @param sessionProvider 取窗口级宿主会话；返回 null 表示引擎没起来，本本如实报错。
 */
class EngineFollowDetailSource(
    private val sessionProvider: () -> EngineSession?,
) : FollowDetailSource {

    private var session: EngineSession? = null

    private var loadedKey: String? = null

    override suspend fun load(sourceName: String, comicId: String): FollowSnapshot = withContext(Dispatchers.IO) {
        val info = sessionFor(sourceName).details(comicId)
        // 占位章（整本一章）不计章节：id/title/count 一并归零，让判据走 remoteUpdateTime
        val realEpisodes = info.episodes.filter { !it.placeholder }
        val last = realEpisodes.lastOrNull()
        FollowSnapshot(
            latestChapterId = last?.id.orEmpty(),
            latestChapterTitle = last?.name.orEmpty(),
            chapterCount = realEpisodes.size,
            remoteUpdateTime = info.updateTime,
        )
    }

    private fun sessionFor(sourceName: String): EngineSession {
        val current = session
        if (current != null && loadedKey == sourceName) return current
        closeSession()
        val base = sessionProvider() ?: error("脚本引擎未启动，无法检查更新")
        val fork = base.forkEphemeral()
        try {
            fork.load(sourceName)
        } catch (e: Throwable) {
            runCatching { fork.close() }
            throw e
        }
        session = fork
        loadedKey = sourceName
        return fork
    }

    override fun closeRound() {
        closeSession()
    }

    private fun closeSession() {
        val current = session
        session = null
        loadedKey = null
        runCatching { current?.close() }
    }
}

package com.venera.engineprobe

import com.venera.engineprobe.EngineSession.Episode
import com.venera.engineprobe.EngineSession.ThumbPage
import com.venera.engineprobe.EngineSession.ThumbnailRewrite

/** 缩略图取字节（按需过源 `onThumbnailLoad` 钩子重写 URL 与请求头）。 */
internal fun EngineSession.execCoverBytes(cover: String): ByteArray {
    var url = cover
    var headers = emptyMap<String, String>()
    val env = runCatching {
        gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    if (!s.comic || !s.comic.onThumbnailLoad) return { __none: true };
                    return await s.comic.onThumbnailLoad(${gson.toJson(cover)}, "", "");
                })()
                """.trimIndent(),
            ),
            mapType,
        )
    }.getOrNull()
    if (env != null && env["__none"] != true && env["success"] != false) {
        val data = env["data"] as? Map<*, *>
        if (data != null) {
            url = data["url"]?.toString() ?: url
            headers = (data["headers"] as? Map<*, *>)
                ?.entries?.associate { it.key.toString() to it.value.toString() } ?: headers
        }
    }
    val (status, bytes) = host.fetchBytes(url, headers)
    if (status != 200 || bytes.isEmpty()) error("取缩略图失败 http=$status bytes=${bytes.size} url=$url")
    return bytes
}

/** 批量调用源 `comic.onThumbnailLoad` 获取封面 URL 改写与请求头。 */
internal fun EngineSession.execThumbnailRewrites(urls: List<String>): Map<String, ThumbnailRewrite> {
    if (urls.isEmpty()) return emptyMap()
    val envelope = runCatching {
        gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    if (!s || !s.comic || !s.comic.onThumbnailLoad) return null;
                    var urls = ${gson.toJson(urls)};
                    var out = [];
                    for (var i = 0; i < urls.length; i++) {
                        try {
                            var r = await s.comic.onThumbnailLoad(urls[i], '', '');
                            out.push({ url: (r && r.url) ? r.url : urls[i], headers: (r && r.headers) ? r.headers : {} });
                        } catch (e) {
                            out.push({ url: urls[i], headers: {} });
                        }
                    }
                    return out;
                })()
                """.trimIndent(),
            ),
            mapType,
        )
    }.getOrNull()
    if (envelope == null) {
        println("D_缩略钩子 key=$key 信封解析失败（evaluateAsync 没回 JSON）")
        return emptyMap()
    }
    if (envelope["success"] == false) {
        println("D_缩略钩子 key=$key 失败 ${envelope["error"]?.toString()?.take(140)}")
        return emptyMap()
    }
    val data = envelope["data"] ?: return emptyMap()
    val items = (data as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()
    if (items.isEmpty()) return emptyMap()
    val out = mutableMapOf<String, ThumbnailRewrite>()
    urls.forEachIndexed { i, original ->
        val item = items.getOrNull(i) ?: return@forEachIndexed
        val headers = (item["headers"] as? Map<*, *>)
            ?.entries?.associate { it.key.toString() to it.value.toString() } ?: emptyMap()
        out[original] = ThumbnailRewrite(
            url = item["url"]?.toString() ?: original,
            headers = headers,
        )
    }
    return out
}

/** 读取漫画详情页信息（标题、简介、章节表与推荐封面钩子改写）。 */
internal fun EngineSession.execDetails(comicId: String): ComicInfo {
    val env = gson.fromJson<Map<String, Any?>>(
        evalWithRetry(
            """
            return (async function(){
                var s = ComicSource.sources['$key'];
                return await s.comic.loadInfo(${gson.toJson(comicId)});
            })()
            """.trimIndent(),
        ),
        mapType,
    )
    val info = parseComicLoadInfo(comicId, env)
    val covers = buildList {
        if (info.cover.isNotBlank()) add(info.cover)
        info.recommend.forEach { if (it.cover.isNotBlank()) add(it.cover) }
    }
    val rewritten = applyThumbnailHook(covers)
    if (rewritten.isEmpty()) return info
    rewritten.values.forEach { rw ->
        if (rw.headers.isNotEmpty()) onThumbnailHeaders?.invoke(rw.url, rw.headers)
    }
    return info.copy(
        cover = rewritten[info.cover]?.url ?: info.cover,
        recommend = info.recommend.map { r -> r.copy(cover = rewritten[r.cover]?.url ?: r.cover) },
    )
}

/** 分页加载预览缩略图并过缩略图钩子。 */
internal fun EngineSession.execThumbnails(comicId: String, next: String?): ThumbPage {
    val env = gson.fromJson<Map<String, Any?>>(
        evalWithRetry(
            """
            return (async function(){
                var s = ComicSource.sources['$key'];
                if (!s.comic || !s.comic.loadThumbnails) return { thumbnails: [] };
                return await s.comic.loadThumbnails(${gson.toJson(comicId)}, ${gson.toJson(next)});
            })()
            """.trimIndent(),
        ),
        mapType,
    )
    val data = env["data"] as? Map<*, *> ?: return ThumbPage(emptyList(), null)
    val thumbs = (data["thumbnails"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
    val nextToken = data["next"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
    val rewritten = applyThumbnailHook(thumbs)
    rewritten.values.forEach { rw ->
        if (rw.headers.isNotEmpty()) onThumbnailHeaders?.invoke(rw.url, rw.headers)
    }
    return ThumbPage(
        if (rewritten.isEmpty()) thumbs else thumbs.map { rewritten[it]?.url ?: it },
        nextToken,
    )
}

/** 分页读取作品评论。 */
internal fun EngineSession.execComments(comicId: String, subId: String?, page: Int, replyTo: String?): DesktopCommentPage {
    val env = gson.fromJson<Map<String, Any?>>(
        host.evaluateAsync(
            """
            return (async function(){
                var s = ComicSource.sources['$key'];
                var args = [${gson.toJson(comicId)}, ${gson.toJson(subId)}, $page, ${gson.toJson(replyTo)}];
                if (typeof s?.comic?.loadComments === 'function') return await s.comic.loadComments.apply(s.comic, args);
                if (typeof s?.comments?.load === 'function') return await s.comments.load.apply(s.comments, args);
                if (typeof s?.comments === 'function') return await s.comments.apply(null, args);
                throw new Error("当前源暂不支持加载评论");
            })()
            """.trimIndent(),
        ),
        mapType,
    )
    return parseCommentPage(env)
}

/** 查询当前源是否支持加载与发送评论。 */
internal fun EngineSession.execCommentCapabilities(): DesktopCommentCapabilities {
    val env = runCatching {
        gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    return {
                        canLoad: !!s && (typeof s.comic?.loadComments === 'function' || typeof s.comments?.load === 'function' || typeof s.comments === 'function'),
                        canSend: !!s && (typeof s.comic?.sendComment === 'function' || typeof s.comments?.send === 'function')
                    };
                })()
                """.trimIndent(),
            ),
            mapType,
        )
    }.getOrNull()
    val data = env?.get("data") as? Map<*, *> ?: return DesktopCommentCapabilities()
    return DesktopCommentCapabilities(
        canLoad = data["canLoad"] == true,
        canSend = data["canSend"] == true,
    )
}

/** 发送一条作品评论。 */
internal fun EngineSession.execSendComment(comicId: String, subId: String?, content: String, replyTo: String?): Boolean {
    val script = """
        return (async function(){
            var s = ComicSource.sources['$key'];
            var args = [${gson.toJson(comicId)}, ${gson.toJson(subId)}, ${gson.toJson(content)}, ${gson.toJson(replyTo)}];
            var r;
            if (typeof s?.comic?.sendComment === 'function') r = await s.comic.sendComment.apply(s.comic, args);
            else if (typeof s?.comments?.send === 'function') r = await s.comments.send.apply(s.comments, args);
            else return false;
            return r !== false;
        })()
    """.trimIndent()
    val raw = evalWithRetry(script)
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
    if (env != null && env["success"] == false) {
        error(env["error"]?.toString() ?: "发送评论失败")
    }
    return env?.get("data") == true
}

/** 给作品打星（界面 0–5 星自动换算为源脚本 0–10 分）。 */
internal fun EngineSession.execStarRating(comicId: String, rating: Float): Boolean {
    val sourceRating = rating * 2f
    val script = """
        return (async function(){
            var s = ComicSource.sources['$key'];
            var c = s && s.comic;
            var fn = c && (typeof c.starRating === 'function' ? c.starRating : (typeof c.star === 'function' ? c.star : null));
            if (!fn) return false;
            await fn.call(c, ${gson.toJson(comicId)}, ${gson.toJson(sourceRating)});
            return true;
        })()
    """.trimIndent()
    val raw = evalWithRetry(script)
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
    if (env != null && env["success"] == false) {
        error(env["error"]?.toString() ?: "评分失败")
    }
    return env?.get("data") == true
}

/** 作品点赞或取消。 */
internal fun EngineSession.execLikeComic(comicId: String, isLike: Boolean): Boolean {
    val script = """
        return (async function(){
            var s = ComicSource.sources['$key'];
            var c = s && s.comic;
            if (!c) return false;
            if (typeof c.likeComic === 'function') { await c.likeComic(${gson.toJson(comicId)}, ${gson.toJson(isLike)}); return true; }
            if (typeof c.like === 'function') { await c.like(${gson.toJson(comicId)}, ${gson.toJson(isLike)}); return true; }
            return false;
        })()
    """.trimIndent()
    val raw = evalWithRetry(script)
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
    if (env != null && env["success"] == false) {
        error(env["error"]?.toString() ?: "点赞失败")
    }
    return env?.get("data") == true
}

/** 查询当前源是否支持作品评分与点赞。 */
internal fun EngineSession.execWriteCapabilities(): DesktopWriteCapabilities {
    val env = runCatching {
        gson.fromJson<Map<String, Any?>>(
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    var c = s && s.comic;
                    return {
                        canRate: !!c && (typeof c.starRating === 'function' || typeof c.star === 'function'),
                        canLike: !!c && (typeof c.likeComic === 'function' || typeof c.like === 'function')
                    };
                })()
                """.trimIndent(),
            ),
            mapType,
        )
    }.getOrNull()
    val data = env?.get("data") as? Map<*, *> ?: return DesktopWriteCapabilities()
    return DesktopWriteCapabilities(
        canRate = data["canRate"] == true,
        canLike = data["canLike"] == true,
    )
}

/** 获取指定章节的全部图片页键列表。 */
internal fun EngineSession.execPages(comicId: String, episode: Episode, sourceKey: String = key): List<String> {
    val targetKey = ensureSourceLoaded(sourceKey.ifBlank { key })
    val epArg = if (episode.placeholder && episode.id.isBlank()) "null" else gson.toJson(episode.id)
    val env = gson.fromJson<Map<String, Any?>>(
        evalWithRetry(
            """
            return (async function(){
                var s = ComicSource.sources[${gson.toJson(targetKey)}];
                if (!s || !s.comic) throw new Error('漫画源未加载或不支持章节读取: ' + ${gson.toJson(targetKey)});
                return await s.comic.loadEp(${gson.toJson(comicId)}, $epArg, "");
            })()
            """.trimIndent(),
        ),
        mapType,
    )
    if (env["success"] == false) error("loadEp 失败: ${env["error"]}")
    val data = env["data"] as? Map<*, *> ?: error("loadEp 没有 data")
    val list = ((data["images"] ?: data["pages"]) as? List<*> ?: emptyList<Any?>()).mapNotNull { it?.toString() }
    if (list.isEmpty()) {
        println("[session] loadEp 回了 0 页 ep=${if (episode.placeholder) "null" else episode.id} data键=${data.keys}")
    }
    return list
}

/** 拉取单页漫画图像字节并按源脚本 `onImageLoad` 切块数执行去混淆还原。 */
internal fun EngineSession.execPageImage(imageKey: String, comicId: String, episodeId: String, sourceKey: String = key): ByteArray {
    val targetKey = ensureSourceLoaded(sourceKey.ifBlank { key })
    val env = gson.fromJson<Map<String, Any?>>(
        host.evaluateAsync(
            """
            return (async function(){
                var s = ComicSource.sources[${gson.toJson(targetKey)}];
                if (!s || !s.comic) throw new Error('漫画源未加载或不支持图片读取: ' + ${gson.toJson(targetKey)});
                if (!s.comic.onImageLoad) return { __none: true };
                return await s.comic.onImageLoad(
                    ${gson.toJson(imageKey)}, ${gson.toJson(comicId)}, ${gson.toJson(episodeId)}, undefined);
            })()
            """.trimIndent(),
        ),
        mapType,
    )
    val request = desktopImageRequestOf(imageKey, env)
    val (status, bytes) = host.fetchBytes(request.url, request.headers)
    if (status != 200 || bytes.isEmpty()) error("取页失败 http=$status bytes=${bytes.size}")
    if (request.scrambleBlocks <= 1) return bytes
    val img = try {
        JvmImageOps.decode(bytes)
    } catch (t: Throwable) {
        throw IllegalStateException(
            "页图解不开 num=${request.scrambleBlocks} 头=${JvmImageOps.magic(bytes)} " +
                "解码器=[${JvmImageOps.readerFormats()}] <- ${t.message}",
            t,
        )
    }
    return JvmImageOps.descrambleBlocks(img, request.scrambleBlocks)
        ?: error("${img.height}px 不够切 ${request.scrambleBlocks} 块，去混淆无从谈起")
}

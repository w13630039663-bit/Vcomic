package com.venera.engineprobe

import java.io.File

/**
 * 网页登录那一形的诊断探针：装载真源脚本，量 `webLoginUrl()` 读不读得出
 * `account.loginWithWebview.url`。用法：`main ehentai nhentai …`（不传 = ehentai/nhentai）。
 */
fun main(args: Array<String>) {
    val keys = args.filter { !it.startsWith("--") }.takeIf { it.isNotEmpty() } ?: listOf("ehentai", "nhentai")
    val searchWord = args.firstOrNull { it.startsWith("--search=") }?.removePrefix("--search=")
    val useRealData = args.contains("--realdata")
    val root = File(System.getProperty("user.dir"))
    val assetDir = File(root, "app/src/main/assets")
    val dataDir = if (useRealData) {
        File(System.getenv("LOCALAPPDATA") ?: error("没有 LOCALAPPDATA"), "venera")
    } else {
        File(System.getProperty("java.io.tmpdir"), "venera-engine-probe")
    }
    println("[weblogin-probe] 数据罐=$dataDir")
    DesktopJsHost(assetDir, dataDir, null, emptyList()).use { host ->
        for (key in keys) {
            val session = EngineSession(host, assetDir)
            runCatching {
                val name = session.load(key)
                println("[weblogin-probe] $key 装载=$name url=${session.webLoginUrl()}")
                if (searchWord != null) {
                    // 诊断：让引擎自己发一趟 exhentai 首页，量它罐里的 Cookie 与响应形状，
                    // 跟 curl 对齐差异（排 exhentai 空响应）。
                    val raw = host.evaluateAsync(
                        """
                        return (async function() {
                            var cs = await Network.getCookies('https://exhentai.org');
                            var names = cs.map(function(c){return c.name;});
                            var res = await Network.get('https://exhentai.org/', {});
                            return JSON.stringify({
                                cookies: names,
                                status: res.status,
                                len: res.body ? res.body.length : -1,
                                head: res.body ? String(res.body).slice(0, 60) : 'EMPTY'
                            });
                        })()
                        """.trimIndent()
                    )
                    println("[weblogin-probe] 诊断原文=$raw")
                    val page = session.search(searchWord, 1)
                    println("[weblogin-probe] $key 搜索'$searchWord' 结果=${page.comics.size} maxPage=${page.maxPage} err=${page.error} 首封面=${page.comics.firstOrNull()?.cover?.take(60)}")
                    // 游标续页验证：第 1 页的 next 递回第 2 页，两页首条应不同（同一 25 条 = 游标没接上）。
                    // options 必须给真值表（脚本 loadNext 里 JSON.parse(options[0])），这里现取源的默认值表。
                    if (args.contains("--cursortest")) {
                        val raw2 = host.evaluateAsync(
                            """
                            return (async function() {
                                var s = ComicSource.sources['$key'];
                                var opts = s.search && s.search.optionList ? _veneraOptionValues(s.search.optionList) : [];
                                var kw = ${com.google.gson.Gson().toJson(searchWord)};
                                var r1 = await s.search.loadNext(kw, opts, null);
                                var r2 = await s.search.loadNext(kw, opts, r1.next);
                                return JSON.stringify({
                                    p1: r1.comics.length, next: r1.next,
                                    p2: r2.comics.length,
                                    t1: (r1.comics[0] || {}).title, t2: (r2.comics[0] || {}).title
                                });
                            })()
                            """.trimIndent()
                        )
                        println("[weblogin-probe] 游标原文=$raw2")
                    }
                    // 详情链验证：详情封面与预览缩略图都应被改写成 ehgt.org（免 Cookie 可取）。
                    if (args.contains("--detailtest")) {
                        val first = session.search(searchWord, 1).comics.firstOrNull()
                        if (first != null) {
                            val info = session.details(first.id)
                            println("[weblogin-probe] 详情封面=${info.cover.take(60)}")
                            val tp = session.thumbnails(first.id, null)
                            println("[weblogin-probe] 预览 ${tp.thumbnails.size} 张 next=${tp.next} 首张=${tp.thumbnails.firstOrNull()?.take(160)}")
                            // 用引擎的 OkHttp 取一张预览（对照 curl 的 404/000，量 URL 本身成不成立）
                            tp.thumbnails.firstOrNull()?.let { thumb ->
                                val (status, bytes) = host.fetchBytes(thumb, emptyMap())
                                println("[weblogin-probe] 引擎取图 http=$status bytes=${bytes.size}")
                            }
                        }
                    }
                    // 毒化→自愈验证：手工种回占位 igneous=mystery（源站对错值 igneous 一律回空页），
                    // 第一搜应失败（脚本 onLoadFailed 会 deleteCookies 清里站 Cookie），第二搜应自愈成功。
                    if (args.contains("--poisontest")) {
                        host.evaluateAsync(
                            """
                            return (async function() {
                                await Network.setCookies('https://exhentai.org', [
                                    { name: 'igneous', value: 'mystery', domain: '.exhentai.org', path: '/' }
                                ]);
                                return '毒化完成';
                            })()
                            """.trimIndent()
                        )
                        val p1 = session.search(searchWord, 1)
                        println("[weblogin-probe] 毒化后第一搜 结果=${p1.comics.size} err=${p1.error?.take(60)}")
                        val p2 = session.search(searchWord, 1)
                        println("[weblogin-probe] 毒化后第二搜 结果=${p2.comics.size} err=${p2.error?.take(60)}")
                    }
                }
            }.onFailure {
                println("[weblogin-probe] $key 失败：${it.message?.take(300)}")
            }
        }
    }
}

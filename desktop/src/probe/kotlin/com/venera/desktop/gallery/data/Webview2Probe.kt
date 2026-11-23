package com.venera.desktop.gallery.data

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import li.songe.compose.webview2.WebView
import li.songe.compose.webview2.WebView2Runtime
import li.songe.compose.webview2.WebViewLifecycle
import li.songe.compose.webview2.WebViewSettings
import li.songe.compose.webview2.rememberWebViewState
import java.io.File

/**
 * WebView2 宿主的**诊断探针 v3**：等「同源 + 不在加载 + 页题安定」后再量 fetch/XHR，
 * 错误带 message+stack 全量细节，区分「导航期上下文销毁」与「页内策略拦截」。
 * 跑法：`gradlew :desktop:webview2Probe`——量完**自动退窗**。
 */
private suspend fun run(label: String, st: li.songe.compose.webview2.WebViewState, expr: String) {
    val out = try {
        st.evaluateJavaScript(expr)
    } catch (t: Throwable) {
        "<THROW ${t.javaClass.simpleName}: ${t.message?.take(150)}>"
    }
    println("D_探针 $label => ${out.take(500)}")
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "webview2-probe") {
        val st = rememberWebViewState("https://konachan.com/")
        val settings = remember {
            WebViewSettings(
                userDataDirectory = File(
                    System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"),
                    "venera/konachan-webview2-probe",
                ).absolutePath,
                requestNativeFocus = false,
            )
        }
        LaunchedEffect(Unit) {
            println("D_探针 runtime=${WebView2Runtime.getAvailableVersion()}")
            while (st.lifecycle != WebViewLifecycle.Active) delay(200)
            // 等三件套：同源 + 不在加载 + 页题安定。每 2s 念一次现场，方便对着窗看。
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline) {
                val ready = st.url.startsWith("https://konachan.com") && !st.isLoading && st.title.isNotBlank()
                if (ready) break
                println(
                    "D_探针 等就绪… url=${st.url.take(40)} title=${st.title.take(20)} " +
                        "loading=${st.isLoading} life=${st.lifecycle}",
                )
                delay(2000)
            }
            delay(1500)
            println("D_探针 就绪 title=${st.title.take(40)} url=${st.url.take(60)}")
            run("readyState", st, "document.readyState")
            run(
                "fetch相对",
                st,
                "(async()=>{try{const r=await fetch('/post.json?limit=1',{credentials:'include'});" +
                    "const t=await r.text();return JSON.stringify({s:r.status,len:t.length})}" +
                    "catch(e){return 'ERR:'+(e&&e.message)+' | stack='+String(e&&e.stack||'').slice(0,150)}})()",
            )
            run(
                "fetch绝对",
                st,
                "(async()=>{try{const r=await fetch('https://konachan.com/post.json?limit=1',{credentials:'include'});" +
                    "const t=await r.text();return JSON.stringify({s:r.status,len:t.length})}" +
                    "catch(e){return 'ERR:'+(e&&e.message)+' | stack='+String(e&&e.stack||'').slice(0,150)}})()",
            )
            run(
                "xhr同步",
                st,
                "(()=>{try{const x=new XMLHttpRequest();x.open('GET','/post.json?limit=1',false);x.send();" +
                    "return JSON.stringify({s:x.status,len:x.responseText.length})}catch(e){return 'ERR:'+(e&&e.message)}})()",
            )
            run("完", st, "'done'")
        }
        WebView(st, Modifier.fillMaxSize(), settings = settings)
    }
}

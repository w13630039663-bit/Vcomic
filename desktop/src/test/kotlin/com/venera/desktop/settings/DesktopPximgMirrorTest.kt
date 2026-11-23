package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.desktop.gallery.data.DesktopPximgMirrorInterceptor
import com.venera.desktop.platform.FakePaths
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPximgMirrorTest {

    @After
    fun tearDown() {
        DesktopPximgMirror.restore("")
    }

    @Test
    fun `反代域名清洗与持久化读写正确`() {
        assertEquals("", DesktopPximgMirror.normalizeHost(""))
        assertEquals("", DesktopPximgMirror.normalizeHost("  https://i.pximg.net/ "))
        assertEquals("", DesktopPximgMirror.normalizeHost("直连"))
        assertEquals("i.pixiv.re", DesktopPximgMirror.normalizeHost("https://i.pixiv.re/img-master"))
        assertEquals("127.0.0.1:8080", DesktopPximgMirror.normalizeHost("http://127.0.0.1:8080/"))

        val dir = File(System.getProperty("java.io.tmpdir"), "venera-mirror-${System.nanoTime()}")
        val store = JsonKeyValueStore(PreferenceKeys.PREFS_NAME, FakePaths(dir))
        DesktopPximgMirror.write(store, "https://i.pixiv.re/")
        assertEquals("i.pixiv.re", DesktopPximgMirror.host.value)
        assertEquals("i.pixiv.re", store.getString(DesktopPximgMirror.KEY_PXIMG_MIRROR, null))
        dir.deleteRecursively()
    }

    @Test
    fun `地址重写仅命中i_pximg_net且不波及其他域名`() {
        val src = "https://i.pximg.net/img-original/img/2026/01/01/123_p0.jpg"
        assertEquals(
            "https://i.pixiv.re/img-original/img/2026/01/01/123_p0.jpg",
            DesktopPximgMirror.rewrite(src, "i.pixiv.re"),
        )
        assertEquals(
            "https://127.0.0.1:8443/img-original/img/2026/01/01/123_p0.jpg",
            DesktopPximgMirror.rewrite(src, "127.0.0.1:8443"),
        )
        val staticUrl = "https://s.pximg.net/common/images/emoji/101.png"
        assertEquals(staticUrl, DesktopPximgMirror.rewrite(staticUrl, "i.pixiv.re"))
        assertEquals(src, DesktopPximgMirror.rewrite(src, ""))
    }

    @Test
    fun `拦截器在反代成功时直返反代结果，反代失败时自动回退直连`() {
        DesktopPximgMirror.restore("mirror.example.com")
        val interceptor = DesktopPximgMirrorInterceptor()
        val origUrl = "https://i.pximg.net/img-master/img/123_p0.jpg"

        // 反代 200：仅请求 1 次反代。
        val visitedSuccess = mutableListOf<String>()
        val okResp = interceptor.intercept(stubChain(origUrl) { req ->
            visitedSuccess += req.url.toString()
            responseOf(req, 200)
        })
        assertEquals(200, okResp.code)
        assertEquals(listOf("https://mirror.example.com/img-master/img/123_p0.jpg"), visitedSuccess)

        // 反代返回 502：自动回退原地址直连。
        val visitedFallbackHttp = mutableListOf<String>()
        val fallbackResp = interceptor.intercept(stubChain(origUrl) { req ->
            val u = req.url.toString()
            visitedFallbackHttp += u
            if (u.contains("mirror.example.com")) responseOf(req, 502) else responseOf(req, 200)
        })
        assertEquals(200, fallbackResp.code)
        assertEquals(
            listOf(
                "https://mirror.example.com/img-master/img/123_p0.jpg",
                origUrl,
            ),
            visitedFallbackHttp,
        )

        // 反代抛 IOException：同样自动回退直连。
        val visitedFallbackIo = mutableListOf<String>()
        val ioResp = interceptor.intercept(stubChain(origUrl) { req ->
            val u = req.url.toString()
            visitedFallbackIo += u
            if (u.contains("mirror.example.com")) throw IOException("timeout") else responseOf(req, 200)
        })
        assertEquals(200, ioResp.code)
        assertEquals(
            listOf(
                "https://mirror.example.com/img-master/img/123_p0.jpg",
                origUrl,
            ),
            visitedFallbackIo,
        )
    }

    private fun responseOf(req: Request, code: Int): Response = Response.Builder()
        .request(req)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("status-$code")
        .body("ok".toResponseBody("text/plain".toMediaType()))
        .build()

    private fun stubChain(url: String, handler: (Request) -> Response): Interceptor.Chain {
        val initReq = Request.Builder().url(url).build()
        return object : Interceptor.Chain {
            override fun request(): Request = initReq
            override fun proceed(request: Request): Response = handler(request)
            override fun connection(): Connection? = null
            override fun call(): Call = error("unused")
            override fun connectTimeoutMillis(): Int = 5000
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun readTimeoutMillis(): Int = 5000
            override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun writeTimeoutMillis(): Int = 5000
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        }
    }
}

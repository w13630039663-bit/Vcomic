package com.venera.desktop.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错误态归档的可执行提示：鉴权两档必须在短句之外带一句"要做什么"。
 *
 * 失败态上屏统一走 [desktopFailureNoteOf]；401/403 过去只有档位短句，用户
 * 看不出该去登录还是该查权限。这里钉住这两档的提示不丢。
 */
class DesktopErrorCopyHintTest {

    @Test
    fun authExpired_hasExecutableHint() {
        val kind = desktopErrorKindOfText("401 Unauthorized")
        assertEquals(DesktopErrorKind.AUTH_EXPIRED, kind)
        val hint = desktopErrorHintOf(kind)
        assertTrue("401 必须带可执行提示", hint != null && hint.isNotBlank())
        assertTrue("401 提示必须点名登录", hint!!.contains("登录"))
    }

    @Test
    fun restricted_hasExecutableHint() {
        val kind = desktopErrorKindOfText("403 Forbidden")
        assertEquals(DesktopErrorKind.RESTRICTED, kind)
        val hint = desktopErrorHintOf(kind)
        assertTrue("403 必须带可执行提示", hint != null && hint.isNotBlank())
    }

    @Test
    fun failureNote_carriesHintForAuth() {
        val note = desktopFailureNoteOf(IllegalStateException("401 unauthorized"))
        assertTrue("失败态短句须带可执行提示", note.contains("·"))
        assertTrue("失败态须点名登录", note.contains("登录"))
    }
}

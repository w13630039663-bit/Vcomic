package com.venera.desktop.pixiv

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import com.venera.desktop.app.LocalShellBackHandled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.venera.compose.ui.tokens.VeneraTokens
import kotlinx.coroutines.launch

/**
 * pixiv 登录那一屏（桌面形）。
 *
 * ## 三条真路（对岸 `PixivCredentialState` 那套判据一条不重写）
 *
 * 1. **开浏览器**：`beginLogin()` 给 PKCE 授权地址 → 系统浏览器登录 → Windows 把
 *    `pixiv://account...` 交回本进程（`DesktopProtocolRegistrar` + `DesktopSingleInstance`）
 *    → `completeLogin`。浏览器回跳贴回第二格仍是备用路。
 * 2. **粘贴回跳链接**：浏览器登录完成后地址栏那条 `pixiv://account/login?code=...` 或带
 *    `code=` 的回调链接 → `completeLogin(redirectUrl)`。
 * 3. **粘贴凭据**：token JSON / 光秃秃的 refresh token → `signInWithPasted(text)`
 *    （浏览器那一路断在半路时的公共出口）。
 *
 * ## 为什么桌面没有内嵌 WebView
 *
 * 对岸的网页登录只有一形是内嵌 WebView（从 WebView 自己的 cookie 罐里抓票），桌面没有那一层
 * 宿主；而上面三条纯 JVM 全能做、接缝（`beginLogin` / `completeLogin` / `signInWithPasted`）
 * 都在 `DesktopPixivAccount` 上 —— 与源管理那路手填 cookie 同一条裁决。
 */
@Composable
internal fun DesktopPixivLoginScreen(
    account: com.venera.compose.gallery.data.PixivCredentials,
    onBack: () -> Unit,
) {
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    val auth by account.authState.collectAsState()
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var redirectDraft by remember { mutableStateOf("") }
    var pasteDraft by remember { mutableStateOf("") }
    var authorizeUrl by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    // 登录成了（状态跳到 SignedIn）就自动回上一屏：不打断人去看「已登录」那一行。
    LaunchedEffect(auth) {
        when (val state = auth) {
            is com.venera.compose.gallery.data.PixivAuthState.SignedIn -> onBack()
            is com.venera.compose.gallery.data.PixivAuthState.LoginRejected -> {
                note = "浏览器回跳没认上：${state.reason.take(180)}"
            }
            is com.venera.compose.gallery.data.PixivAuthState.NeedsRelogin -> {
                note = "pixiv 要求重新登录：${state.reason.take(180)}"
            }
            com.venera.compose.gallery.data.PixivAuthState.Anonymous -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = tokens.spacing.screenHorizontal,
                vertical = tokens.spacing.space6,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.space4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
        ) {
            // 一屏一返回：壳层已摆返回时屏内这枚隐去。
            if (!LocalShellBackHandled.current) {
                com.venera.desktop.ui.DesktopBackButton(onClick = onBack)
            }
            Text(
                text = "登录 pixiv",
                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                fontWeight = tokens.type.weightBold,
                color = tokens.color.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "登录走系统浏览器（PKCE）。登录完成后 Windows 会自动把 pixiv 回跳交回本窗口；" +
                "若系统没有弹回，再把地址栏那条回跳链接贴进下面第二格。",
            fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
            color = tokens.color.textSecondary,
            modifier = Modifier.widthIn(max = 560.dp),
            textAlign = TextAlign.Start,
        )

        // ── 第 1 步：拿授权链接并直接开浏览器 ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesktopPushButton(
                enabled = !busy,
                onClick = {
                    val challenge = account.beginLogin()
                    if (challenge == null) {
                        note = "起不了登录：这台机器没配 pixiv 的 client 凭据。"
                    } else {
                        authorizeUrl = challenge.authorizeUrl
                        note = "已打开系统浏览器。登录完成后会自动回到本窗口。"
                        clipboard.setText(AnnotatedString(challenge.authorizeUrl))
                        scope.launch {
                            val failure = openBrowser(challenge.authorizeUrl)
                            if (failure != null) {
                                note = "$failure。授权链接已复制，可手动粘到浏览器。"
                            }
                        }
                    }
                },
            ) { Text("① 在系统浏览器登录") }
            authorizeUrl?.let { url ->
                DesktopPushButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(url))
                        note = "授权链接已复制到剪贴板。"
                    },
                ) { Text("复制授权链接") }
            }
        }

        // ── 第 2 步：贴回跳链接 ──
        com.venera.desktop.ui.components.DesktopInputField(
            value = redirectDraft,
            onValueChange = { redirectDraft = it },
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            placeholder = "② 贴回跳链接（含 code= 的那一条）",
        )
        DesktopPushButton(
            enabled = !busy && redirectDraft.isNotBlank(),
            onClick = {
                busy = true
                note = null
                scope.launch {
                    val result = account.completeLogin(redirectDraft.trim())
                    busy = false
                    note = result.fold(
                        onSuccess = { "登录成功：${it.screenLine}" },
                        onFailure = { t -> "回跳没认上：${t.message?.take(160) ?: t.javaClass.simpleName}" },
                    )
                }
            },
        ) { Text("③ 用回跳链接完成登录") }

        // ── 备用路：直接贴凭据 ──
        Text(
            text = "备用：浏览器那一路断在半路时，直接贴 token JSON 或 refresh token。",
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textTertiary,
            modifier = Modifier.widthIn(max = 560.dp),
        )
        com.venera.desktop.ui.components.DesktopInputField(
            value = pasteDraft,
            onValueChange = { pasteDraft = it },
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            placeholder = "token JSON / refresh token",
            singleLine = false,
            minLines = 3,
            maxLines = 8,
        )
        DesktopPushButton(
            enabled = !busy && pasteDraft.isNotBlank(),
            onClick = {
                busy = true
                note = null
                scope.launch {
                    val result = account.signInWithPasted(pasteDraft.trim())
                    busy = false
                    note = result.fold(
                        onSuccess = { "登录成功：${it.screenLine}" },
                        onFailure = { t -> "没认上：${t.message?.take(160) ?: t.javaClass.simpleName}" },
                    )
                }
            },
        ) { Text("用粘贴的凭据登录") }

        // 已登录态下的出口：退出登录。
        if (auth is com.venera.compose.gallery.data.PixivAuthState.SignedIn) {
            DesktopPushButton(onClick = { account.signOut() }) { Text("退出登录") }
        }

        note?.let {
            Text(
                text = it,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
        if (busy) {
            Text("正在换票…", fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textTertiary)
        }
        Spacer(Modifier.height(tokens.spacing.space8))
    }
}

/** 系统浏览器（桌面判据复用 `openInBrowser` 那颗既有件；成不成那句成因原样交回）。 */
private suspend fun openBrowser(url: String): String? =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        com.venera.desktop.gallery.ui.openInBrowser(url)
    }

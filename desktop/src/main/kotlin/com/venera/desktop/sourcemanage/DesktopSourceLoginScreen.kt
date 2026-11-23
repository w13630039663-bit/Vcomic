package com.venera.desktop.sourcemanage

import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.components.contentMaxWidthFor
import com.venera.compose.components.venera.VeneraCard
import com.venera.compose.components.venera.VeneraTextField
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.ui.SettingsCardSpec
import com.venera.desktop.ui.SettingsControlPlacement
import com.venera.desktop.ui.SettingsPageTemplate
import com.venera.desktop.ui.SettingsRowSpec

/**
 * 手填凭据那一屏（goal「网页登录」里在桌面上**成立**的那一形）。
 *
 * ## 为什么这一屏只有"手工粘贴"而没有一枚「网页登录」
 *
 * §88 那份裁决现量过对岸：网页登录**只有一形**，是内嵌 WebView 登进去再从 WebView 自己的罐里抓
 * cookie（`WebLoginScreen.kt:130`），成功与否靠导航过程的 url/title 序列判 —— 桌面没有那一层宿主。
 * 而对岸**另外两形**里，手填 cookie 那一形是纯 JVM 的落点，接缝（桥那三档 cookie、`isLogged`、
 * 那颗盘上的罐）今天就在。所以这一屏摆的是那半件真做得成的事，而不是画一枚按下去开不出网页的把手。
 *
 * ## 屏只吃形参，一样都不自己去问
 *
 * 字段名单由调用方递进来。它住在脚本文本里，而源管理这一屏刻意**不起引擎**
 * （批 82.7 那条 `desktopComicEngineNeeded`）⇒ 屏再自己去读一遍就是第二条轨
 * （批 83 那颗 `DesktopSourceNames` 的注释原话：两处各读一遍文件，读到一半被改的话，
 * 屏上会摆出名与名单不配套的格子）。写那一趟同理只交回调：屏不认识引擎、不认识罐、不认识目录。
 *
 * ## 那句结果话为什么由外面递进来
 *
 * 三档（没登上 / 源认了但盘上一枚没落 / 真登上了）住在 `DesktopCookieLogin.screenNote`。
 * **中间那一档是批 88 整批存在的理由**：屏上自己拼一句"登录成功"就会把它盖掉，
 * 而那一档的意思正是"重启就带不上"。所以屏一个字都不判成没成。
 */
@Composable
internal fun DesktopSourceLoginScreen(
    mode: WideScreenLayoutMode,
    /** 那颗源的显示名（取不到时调用方交回**键**，屏不编站名 —— 编一个等于替站方作伪证）。 */
    sourceName: String,
    /** `account.loginWithCookies.fields`；空表 = 源没有手填 Cookie 这一形。 */
    fields: List<String>,
    /** 源声明了 `account.login(account, pwd)`。 */
    supportsPasswordLogin: Boolean,
    /** 源声明了 `account.loginWithWebview`（桌面当前没有 WebView 宿主）。 */
    supportsWebLogin: Boolean,
    /** 上一趟那句话（来自装配点的成品结果）。null = 还没跑过。 */
    note: String?,
    /** 那一趟在跑：提交那枚要 disable。 */
    busy: Boolean,
    /** 用户敲进去的 Cookie（键 = 字段名）。 */
    onSubmitCookies: (Map<String, String>) -> Unit,
    /** 用户敲进去的账号密码。 */
    onSubmitPassword: (account: String, password: String) -> Unit,
    /** 启动网页登录（弹出 Edge 独立窗口完成登录并捕获凭据）。 */
    onStartWebLogin: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    var cookieDraft by remember(fields) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var accountDraft by remember { mutableStateOf("") }
    var passwordDraft by remember { mutableStateOf("") }

    val cards = buildList {
        if (supportsPasswordLogin) {
            add(
                SettingsCardSpec(
                    title = com.venera.desktop.settings.DesktopI18n.t("账号密码", "Account & Password"),
                    caption = com.venera.desktop.settings.DesktopI18n.t("由源脚本自己的 account.login 执行登录；桌面不猜接口。", "Login via source script account.login."),
                    rows = listOf(
                        SettingsRowSpec(
                            title = com.venera.desktop.settings.DesktopI18n.t("账号", "Account"),
                            caption = null,
                            controlPlacement = SettingsControlPlacement.Below,
                            control = {
                                com.venera.desktop.ui.components.DesktopInputField(
                                    value = accountDraft,
                                    onValueChange = { accountDraft = it },
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                                    placeholder = com.venera.desktop.settings.DesktopI18n.t("账号", "Account"),
                                )
                            },
                        ),
                        SettingsRowSpec(
                            title = com.venera.desktop.settings.DesktopI18n.t("密码", "Password"),
                            caption = null,
                            controlPlacement = SettingsControlPlacement.Below,
                            control = {
                                com.venera.desktop.ui.components.DesktopInputField(
                                    value = passwordDraft,
                                    onValueChange = { passwordDraft = it },
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                                    placeholder = com.venera.desktop.settings.DesktopI18n.t("密码", "Password"),
                                )
                            },
                        ),
                        SettingsRowSpec(
                            title = com.venera.desktop.settings.DesktopI18n.t("登录", "Sign In"),
                            caption = if (busy) com.venera.desktop.settings.DesktopI18n.t("正在登录，重复提交会被挡住。", "Signing in…") else null,
                            control = {
                                DesktopPushButton(
                                    onClick = { onSubmitPassword(accountDraft.trim(), passwordDraft) },
                                    enabled = !busy && accountDraft.isNotBlank() && passwordDraft.isNotBlank(),
                                ) {
                                    Text(if (busy) com.venera.desktop.settings.DesktopI18n.t("登录中…", "Signing in…") else com.venera.desktop.settings.DesktopI18n.t("账号密码登录", "Sign In"))
                                }
                            },
                        ),
                    ),
                ),
            )
        }

        if (fields.isNotEmpty()) {
            add(
                SettingsCardSpec(
                    title = com.venera.desktop.settings.DesktopI18n.t("手填 Cookie", "Manual Cookies"),
                    caption = com.venera.desktop.settings.DesktopI18n.t("字段由源脚本自己声明，桌面只负责把值交回登录口。", "Fields declared by the source script."),
                    rows = fields.map { name ->
                        SettingsRowSpec(
                            title = name,
                            caption = null,
                            controlPlacement = SettingsControlPlacement.Below,
                            control = {
                                com.venera.desktop.ui.components.DesktopInputField(
                                    value = cookieDraft[name].orEmpty(),
                                    onValueChange = { cookieDraft = cookieDraft + (name to it) },
                                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                                    placeholder = name,
                                )
                            },
                        )
                    } + SettingsRowSpec(
                        title = com.venera.desktop.settings.DesktopI18n.t("提交 Cookie", "Submit Cookies"),
                        caption = if (busy) com.venera.desktop.settings.DesktopI18n.t("正在登录，重复提交会被挡住。", "Signing in…") else null,
                        control = {
                            DesktopPushButton(
                                onClick = { onSubmitCookies(cookieDraft) },
                                enabled = !busy && fields.any { cookieDraft[it]?.isNotBlank() == true },
                            ) {
                                Text(if (busy) com.venera.desktop.settings.DesktopI18n.t("登录中…", "Signing in…") else com.venera.desktop.settings.DesktopI18n.t("登录", "Sign In"))
                            }
                        },
                    ),
                ),
            )
        }

        if (supportsWebLogin) {
            add(
                SettingsCardSpec(
                    title = com.venera.desktop.settings.DesktopI18n.t("网页登录", "Web Login"),
                    caption = com.venera.desktop.settings.DesktopI18n.t("启动独立网页登录窗口，完成登录后将自动捕获凭据并同步至应用。", "Launch browser window to sign in and capture credentials automatically."),
                    rows = listOf(
                        SettingsRowSpec(
                            title = com.venera.desktop.settings.DesktopI18n.t("独立网页登录", "Browser Login"),
                            caption = if (busy) com.venera.desktop.settings.DesktopI18n.t("正在网页登录中，请在弹出的窗口中操作…", "Waiting for browser login…") else com.venera.desktop.settings.DesktopI18n.t("调用系统 Chromium/Edge 内核打开站方登录页面", "Open source login page using Edge/Chromium"),
                            control = {
                                DesktopPushButton(
                                    onClick = { onStartWebLogin?.invoke() },
                                    enabled = !busy && onStartWebLogin != null,
                                ) {
                                    Text(if (busy) com.venera.desktop.settings.DesktopI18n.t("登录中…", "Signing in…") else com.venera.desktop.settings.DesktopI18n.t("启动网页登录", "Start Web Login"))
                                }
                            },
                        ),
                    ),
                ),
            )
        }

        if (!supportsPasswordLogin && fields.isEmpty() && !supportsWebLogin) {
            add(
                SettingsCardSpec(
                    title = com.venera.desktop.settings.DesktopI18n.t("桌面暂不能登录这一颗源", "Login unavailable on desktop"),
                    caption = com.venera.desktop.settings.DesktopI18n.t("它没有声明账号密码、网页登录或手填 Cookie 登录。", "No supported login method declared."),
                    rows = listOf(
                        SettingsRowSpec(
                            title = com.venera.desktop.settings.DesktopI18n.t("没有可执行的登录接缝", "No login entry point"),
                            caption = com.venera.desktop.settings.DesktopI18n.t("这不是按钮没做，是源脚本没有给桌面可调用的登录入口。", "The source script does not expose a desktop login handler."),
                        ),
                    ),
                ),
            )
        }
    }

    SettingsPageTemplate(
        mode = mode,
        title = com.venera.desktop.settings.DesktopI18n.t("登录 $sourceName", "Sign in · $sourceName"),
        subtitle = when {
            supportsWebLogin -> com.venera.desktop.settings.DesktopI18n.t("这颗源支持网页登录，点击下方按钮启动登录窗口。", "Web login supported. Click below to open login window.")
            supportsPasswordLogin && fields.isNotEmpty() -> com.venera.desktop.settings.DesktopI18n.t("账号密码与手填 Cookie 两条真路都在。", "Supports both password and manual cookie login.")
            supportsPasswordLogin -> com.venera.desktop.settings.DesktopI18n.t("这颗源支持账号密码登录。", "Supports account & password login.")
            fields.isNotEmpty() -> com.venera.desktop.settings.DesktopI18n.t("这颗源支持手填 Cookie 登录。", "Supports manual cookie login.")
            else -> com.venera.desktop.settings.DesktopI18n.t("这颗源没有声明桌面可执行的登录方式。", "No desktop login method declared.")
        },
        cards = cards,
        onBack = onBack,
        note = note,
        cardColumns = 1,
        modifier = modifier,
    )
}

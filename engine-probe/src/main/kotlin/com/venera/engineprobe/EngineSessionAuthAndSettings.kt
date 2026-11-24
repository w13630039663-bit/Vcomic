package com.venera.engineprobe

import com.venera.engineprobe.EngineSession.CookieLogin
import com.venera.engineprobe.EngineSession.PasswordLogin

/** 手填 Cookie 登录当前源并交由脚本 `validate` 校验。 */
internal fun EngineSession.execLoginWithCookies(values: List<String>): CookieLogin {
    val script = """
        return (async function() {
            var s = ComicSource.sources['$key'];
            if (!s || !s.account || !s.account.loginWithCookies) {
                return { ok: false, why: "该源没有 loginWithCookies 那一形", fields: [] };
            }
            if (typeof s.account.loginWithCookies.validate !== 'function') {
                return { ok: false, why: "该源的 validate 不是函数", fields: [] };
            }
            var fields = Array.isArray(s.account.loginWithCookies.fields)
                ? s.account.loginWithCookies.fields : [];
            var r = await s.account.loginWithCookies.validate(${gson.toJson(values)});
            return { ok: r === true, fields: fields };
        })()
    """.trimIndent()
    val raw = host.evaluateAsync(script)
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
        ?: return CookieLogin(false, "源那句 validate 的答复解不开", emptyList())
    if (env["success"] == false) {
        return CookieLogin(false, env["error"]?.toString() ?: "validate 执行失败", emptyList())
    }
    val data = env["data"] as? Map<*, *>
        ?: return CookieLogin(false, "validate 回的形状不认识", emptyList())
    val fields = (data["fields"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    if (data["ok"] != true) {
        return CookieLogin(false, data["why"]?.toString() ?: "源说这批凭据不算通过", fields)
    }
    host.markLoggedIn(key)
    return CookieLogin(true, "源认了这批凭据", fields)
}

/** 读取源声明的网页登录入口地址。 */
internal fun EngineSession.execWebLoginUrl(): String? {
    val script = """
        (function() {
            var s = ComicSource.sources['$key'];
            if (!s || !s.account || !s.account.loginWithWebview) return null;
            var w = s.account.loginWithWebview;
            return (w && w.url) ? w.url : null;
        })()
    """.trimIndent()
    val raw = host.evaluate(script) ?: return null
    return raw.trim().takeIf { it.startsWith("http") && it != "null" }
}

/** 轮询检测独立网页登录窗当前页面是否已登录成功。 */
internal fun EngineSession.execCheckWebLoginStatus(url: String, title: String): Boolean {
    val script = """
        return (async function() {
            var s = ComicSource.sources['$key'];
            if (!s || !s.account || !s.account.loginWithWebview) return false;
            var fn = s.account.loginWithWebview.checkStatus;
            if (typeof fn !== 'function') return false;
            try {
                return (await fn(${gson.toJson(url)}, ${gson.toJson(title)})) === true;
            } catch(e) {
                return false;
            }
        })()
    """.trimIndent()
    val raw = host.evaluateAsync(script)
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
    return env?.get("data") == true || raw.contains("\"data\":true")
}

/** 执行网页登录成功钩子并标记会话为已登录状态。 */
internal fun EngineSession.execOnWebLoginSuccess(): Boolean {
    val script = """
        return (async function() {
            var s = ComicSource.sources['$key'];
            if (!s || !s.account || !s.account.loginWithWebview) return true;
            var fn = s.account.loginWithWebview.onLoginSuccess;
            if (typeof fn === 'function') {
                try {
                    await fn();
                } catch(e) {}
            }
            return true;
        })()
    """.trimIndent()
    host.evaluateAsync(script)
    host.markLoggedIn(key)
    return true
}

/** 使用账号密码登录当前源，并将凭据持久化至数据源存储供自动续期使用。 */
internal fun EngineSession.execLoginWithPassword(account: String, password: String): PasswordLogin {
    val script = """
        return (async function() {
            var s = ComicSource.sources['$key'];
            if (!s || !s.account || typeof s.account.login !== 'function') {
                return { ok: false, why: "该源没有账号密码登录那一形" };
            }
            var res = await s.account.login(
                ${gson.toJson(account)},
                ${gson.toJson(password)}
            );
            if (res && res.error) {
                return {
                    ok: false,
                    why: res.errorMessage || res.error || "登录失败"
                };
            }
            return { ok: true };
        })()
    """.trimIndent()
    val raw = host.evaluateAsync(script)
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull()
        ?: return PasswordLogin(false, "源那句 login 的答复解不开")
    if (env["success"] == false) {
        return PasswordLogin(false, env["error"]?.toString() ?: "login 执行失败")
    }
    val data = env["data"] as? Map<*, *>
        ?: return PasswordLogin(false, "login 回的形状不认识")
    if (data["ok"] != true) {
        return PasswordLogin(false, data["why"]?.toString() ?: "源说这组账号密码没登上")
    }
    host.dataStoreOf().saveAccount(key, listOf(account, password))
    return PasswordLogin(true, "源认了这组账号密码")
}

/** 从数据源读取已保存账号密码重新登录以刷新过期令牌。 */
internal fun EngineSession.execRelogin(): Boolean = runCatching {
    val accountData = host.dataStoreOf().getAccount(key)
    if (accountData is List<*> && accountData.size >= 2) {
        val user = accountData[0]?.toString() ?: ""
        val pwd = accountData[1]?.toString() ?: ""
        if (user.isNotBlank() && pwd.isNotBlank()) {
            return loginWithPassword(user, pwd).ok
        }
    }
    false
}.getOrDefault(false)

/** 读取当前源的全部配置值（按源脚本默认值兜底）。 */
internal fun EngineSession.execLoadSettingsAll(): Map<String, Any?> {
    val env = gson.fromJson<Map<String, Any?>>(
        host.evaluateAsync(
            """
            return (async function(){
                var s = ComicSource.sources['$key'];
                if (!s || !s.settings) return {};
                var out = {};
                for (var k in s.settings) {
                    if (typeof s.settings[k] === 'object' && s.settings[k] !== null &&
                        typeof s.settings[k].default !== 'undefined' &&
                        typeof s.settings[k].type === 'string') {
                        out[k] = { type: s.settings[k].type, default: s.settings[k].default };
                    }
                }
                return out;
            })()
            """.trimIndent(),
        ),
        mapType,
    )
    if (env["success"] == false) error("读设置失败: ${env["error"]}")
    val defs = (env["data"] as? Map<*, *>)?.entries?.associate {
        it.key.toString() to ((it.value as? Map<*, *>)?.get("default"))
    } ?: emptyMap()
    val stored = HashMap<String, Any?>()
    defs.forEach { (settingKey, fallback) ->
        stored[settingKey] = host.dataStoreOf().loadSetting(key, settingKey) ?: fallback
    }
    return stored
}

/** 写入单条源设置值。 */
internal fun EngineSession.execSaveSettingValue(settingKey: String, value: Any?) {
    host.dataStoreOf().saveSetting(key, settingKey, value)
}

/** 设置变更后按需同步运行期内存状态（例如 jm 图床域刷新）。 */
internal fun EngineSession.execApplySettingLive(settingKey: String) {
    if (key == "jm" && settingKey == "imageStream") {
        runCatching {
            host.evaluateAsync(
                """
                return (async function(){
                    var s = ComicSource.sources['$key'];
                    if (s && s.refreshImgUrl) {
                        await s.refreshImgUrl(false);
                    }
                    return null;
                })()
                """.trimIndent(),
                timeoutMs = 8_000L,
            )
        }
    }
}

/** 执行源脚本声明的设置动作回调。 */
internal fun EngineSession.execRunSettingCallback(settingKey: String): String? {
    val raw = host.evaluateAsync(
        """
        return (async function(){
            var s = ComicSource.sources['$key'];
            var item = s && s.settings ? s.settings[${gson.toJson(settingKey)}] : null;
            if (!item || typeof item.callback !== 'function') {
                return { ok: false, why: "这一颗源没有那枚回调" };
            }
            var r = await item.callback();
            return { ok: true, note: typeof r === 'string' ? r : null };
        })()
        """.trimIndent(),
    )
    val env = runCatching { gson.fromJson<Map<String, Any?>>(raw, mapType) }.getOrNull() ?: return null
    if (env["success"] == false) error(env["error"]?.toString() ?: "回调执行失败")
    val data = env["data"] as? Map<*, *> ?: return null
    if (data["ok"] != true) error(data["why"]?.toString() ?: "这一颗源没有那枚回调")
    return data["note"]?.toString()
}

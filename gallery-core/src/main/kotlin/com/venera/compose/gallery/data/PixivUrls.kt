package com.venera.compose.gallery.data

/**
 * pixiv 回包里的**协议相对地址**补全（桌面画师页顶图/关注头像破图）。
 *
 * 站方对 `background.url` / `profile_image_urls.medium` 这几格会回
 * `//p1-cdn-web.pximg.net/…` 这种**不带 scheme** 的形状（同一份回包里
 * `image_urls.*` 却带全 `https://` —— 键不同形状不同，不是我们抄错）。
 * 这种串直接交给取图器（Coil/okhttp）就是"有值、但永远取不动"：
 * 画师页顶图摆出破图占位（只判 isNotBlank），我的页横幅摆出渐变兜底
 * （被 `isHttpUrl()` 闸成 null）—— 同一件事的两种屏上形状。
 *
 * 归一规则只有一条：以 `//` 开头的补 `https:`，其余**原样**。
 * 空串与 null 也原样 —— 这一颗不兼任"合法性判据"，那是调用点闸的事。
 */
fun pixivAbsoluteUrl(raw: String?): String? =
    raw?.takeIf { it.startsWith("//") }?.let { "https:$it" } ?: raw

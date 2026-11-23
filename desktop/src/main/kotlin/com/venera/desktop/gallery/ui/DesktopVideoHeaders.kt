package com.venera.desktop.gallery.ui

import com.venera.compose.data.platform.ImageHeaderPolicy
import com.venera.compose.data.platform.UserAgentStrings

/**
 * 桌面 booru 视频取流的防盗链头（**桌面这一路唯一出处**）。
 *
 * 逐字照对岸 `GalleryVideoEngine.kt:50-52` 那颗 `videoRequestHeaders`，但**不 import 它**：
 * 对岸那颗 import `android.content.Context` / `androidx.media3` / `data.network`（`UserAgentPolicy` 也在
 * `data/network`，桌面 srcDir 不覆盖），整颗进不了桌面编译面。这里只搬它的**判据**：
 *  - 头按 host 取 [ImageHeaderPolicy.headersFor]（那颗在 `data/platform`，桌面编译面里有、`DesktopImagePipeline` 已在用）；
 *  - 表里**没有** UA 的站（yande.re 就是）补全局默认串 —— 只交表里那几点等于把能播的那路改成"不带 UA"，
 *    那是修一个坏一个（对岸 :47-48 实测）。UA 用同值的 [UserAgentStrings.DEFAULT_USER_AGENT]
 *    （`data/platform`，与 `data/network.UserAgentPolicy.DEFAULT_USER_AGENT` 同一串，见 ImageHeaderPolicy 注释）。
 *
 * 返回的这枚 Map 交给 [DesktopVideoLauncher] 翻成 libvlc 的 `--http-referrer` / `--http-user-agent` 选项。
 */
internal fun desktopVideoHeadersFor(url: String): Map<String, String> =
    LinkedHashMap<String, String>(ImageHeaderPolicy.headersFor(url)).apply {
        putIfAbsent("User-Agent", UserAgentStrings.DEFAULT_USER_AGENT)
    }

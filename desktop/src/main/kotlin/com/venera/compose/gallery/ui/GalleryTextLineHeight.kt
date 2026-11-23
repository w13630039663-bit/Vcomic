package com.venera.compose.gallery.ui

/**
 * 文本行高倍数（`em`，相对字号）——。
 *
 * **唯一出处是 miuix 的 `paragraph`**（`ui/tokens/MiuixTextStyles.kt:33` 写着
 * `TextStyle(fontSize = t.itemTitle, lineHeight = 1.2f.em)`，那颗注自己说明
 * "lineHeight 这一条库里是 1.2f.em，是唯一带行高的角色"）。这里照抄同一档，
 * 不另立新数：横滑一排要的是"行高可预测"，不是"排版更好看"。
 *
 * 为什么非要有它：`lineHeight` 不指定时，行高取**这一行实际用到的字形**的
 * ascent + descent —— 同一行字，纯拉丁 / CJK / 带 emoji 会差 1~2px。竖排文本
 * 差这点没人看得出来，但横滑一排的**报高**是"已测项的最大值"，于是"视口里换了谁"
 * 就成了报高，外面那张白卡跟着上下跳。详见 `GalleryPixivHome.kt` 的 [PixivArtistRail] 头注那条链条。
 *
 * ⚠️起**两站共用**：图库站的关注画师行也进了白卡
 * （用户「关注画师和每日热门 做成相同风格的ui」），同一个病灶在同一种容器里，
 * 倍数再各抄一档就会漂。所以它是 `internal` 而不是文件私有。
 *
 * ⚠️ 起从 `GalleryPixivHome.kt` **搬到此独立件**：那一颗是 pixiv 站内页
 * （69 处 Android 宿主调用、桌面编不过而整颗被 exclude），而这枚常量是**纯排版、零依赖**，
 * 却被六颗干净件（`GalleryCardCaption` / `GalleryHomeSections` / `GalleryBookmarksHubScreen` …）
 * 同包引用 —— 于是它们全被这枚住在脏件里的常量拖成"依赖被排除的兄弟"（B 类成因），
 * 上不了桌面编译面。搬过来之后同包解析不变（`:app` 一字不改其调用点），桌面则自动挂上这一颗。
 * 这正是「共享常量被困在平台专属件里」那一族（同 `RepoIndexEntry` / `UpdateDownload` /
 * `displayVersionOf` 那三笔搬家，见 `docs/rounds/desktop-reuse-android-ui-2026-10-18.md`）。
 */
internal const val TEXT_LINE_HEIGHT = 1.2f

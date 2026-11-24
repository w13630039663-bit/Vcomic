package com.venera.desktop.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.data.api.ComicPreferences
import com.venera.compose.data.api.NetworkPreferences
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.gallery.domain.GallerySaveNaming
import com.venera.compose.gallery.domain.PixivImageQuality
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopCacheMaintenance
import com.venera.desktop.gallery.data.DesktopDirChooser
import com.venera.desktop.gallery.data.DesktopGalleryBrowseHistory
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import com.venera.desktop.gallery.data.DesktopGalleryHandle
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopPixivAccount
import com.venera.desktop.gallery.data.desktopComicDownloadsRoot
import com.venera.desktop.gallery.data.desktopGallerySaveDir
import com.venera.desktop.gallery.data.formatBytes
import com.venera.desktop.gallery.ui.openInBrowser
import com.venera.desktop.platform.DesktopHttpEngine
import com.venera.desktop.platform.DesktopLineProbe
import com.venera.desktop.platform.DesktopProtocolRegistrar
import com.venera.desktop.platform.DesktopUpdateCheck
import com.venera.desktop.platform.DesktopWebDavSync
import com.venera.desktop.search.DesktopSearchViewMode
import com.venera.desktop.search.readSearchViewMode
import com.venera.desktop.search.writeSearchViewMode
import com.venera.desktop.ui.DesktopEffects
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** 构造「源与账号」分区设置卡。 */
@Composable
internal fun buildSourceSettingsCards(
    prefs: DesktopGalleryPreferences,
    gelbooruState: GelbooruCredentialState?,
    pixivAccount: DesktopPixivAccount?,
    galleryCreds: DesktopGalleryCredentials?,
    onOpenPixivAccount: (() -> Unit)?,
    onOpenPixivLogin: (() -> Unit)?,
    onShowGelbooruDialog: () -> Unit,
    onShowSauceNaoDialog: () -> Unit,
    openSourcesPage: () -> Unit,
): List<DesktopSettingsCard> {
    val ux = LocalUxPlatformServices.current
    val gelbooruIdentity by (gelbooruState?.identity ?: MutableStateFlow(null)).collectAsState()
    val isGelbooruSignedIn = gelbooruIdentity != null || gelbooruState?.isConfigured == true
    val pixivAuthState by (pixivAccount?.authState ?: MutableStateFlow(PixivAuthState.Anonymous)).collectAsState()
    val isPixivSignedIn = pixivAuthState is PixivAuthState.SignedIn
    val signedInUser = pixivAuthState as? PixivAuthState.SignedIn
    val sauceNaoHasKey = galleryCreds?.sauceNaoHasKey?.collectAsState()?.value
    val pixivIllustQ = prefs.pixivIllustQuality.collectAsState().value
    val pixivMangaQ = prefs.pixivMangaQuality.collectAsState().value
    val pixivZoomQ = prefs.pixivZoomQuality.collectAsState().value
    val pixivPreviewQ = prefs.pixivPreviewQuality.collectAsState().value

    val gelbooruCard = DesktopSettingsCard(
        title = "Gelbooru 账号",
        caption = "配置 User ID 与 API Key 解锁 Gelbooru 搜索与日榜（官方接口要求鉴权）",
        rows = listOfNotNull(
            DesktopSettingRow.Action(
                title = if (isGelbooruSignedIn) "已登录 Gelbooru" else "未配置凭据",
                caption = if (isGelbooruSignedIn) {
                    val uid = gelbooruIdentity?.userId ?: gelbooruState?.userId.orEmpty()
                    "当前凭据 · User ID: $uid (可正常访问与检索)"
                } else {
                    "匿名请求受站方限制；配置后可在画廊与搜索中正常使用"
                },
                buttonLabel = if (isGelbooruSignedIn) "修改凭据" else "登录账号",
                onAction = onShowGelbooruDialog,
            ),
            if (isGelbooruSignedIn) {
                DesktopSettingRow.Action(
                    title = "注销当前凭据",
                    caption = "清除保存在本机的 Gelbooru 鉴权信息",
                    buttonLabel = "退出登录",
                    buttonColor = VeneraTokens.color.error,
                    onAction = {
                        gelbooruState?.signOut()
                        ux.toast("已退出 Gelbooru 登录")
                    },
                )
            } else null,
        ),
    )

    val pixivCard = DesktopSettingsCard(
        title = "Pixiv 账号",
        caption = "登录 Pixiv 账号以使用云端书签、同步已关注画师及解除匿名限速",
        rows = listOfNotNull(
            if (isPixivSignedIn && signedInUser != null) {
                DesktopSettingRow.Action(
                    title = signedInUser.userName?.ifBlank { "Pixiv 用户" } ?: "Pixiv 用户",
                    caption = "已登录 · User ID: ${signedInUser.userId ?: "已认证"}",
                    buttonLabel = "管理我的 Pixiv",
                    onAction = { onOpenPixivAccount?.invoke() },
                )
            } else {
                DesktopSettingRow.Action(
                    title = "当前未登录",
                    caption = "未登录状态下画作检索受限，且无法使用云端书签",
                    buttonLabel = "登录 Pixiv",
                    onAction = { onOpenPixivLogin?.invoke() },
                )
            },
            if (isPixivSignedIn) {
                DesktopSettingRow.Action(
                    title = "退出 Pixiv 登录",
                    caption = "清除本地 Token 缓存并恢复为匿名访问",
                    buttonLabel = "退出登录",
                    buttonColor = VeneraTokens.color.error,
                    onAction = {
                        pixivAccount?.signOut()
                        ux.toast("已退出 Pixiv 登录")
                    },
                )
            } else null,
        ),
    )

    return listOfNotNull(
        gelbooruCard,
        pixivCard,
        galleryCreds?.let {
            DesktopSettingsCard(
                title = "SauceNAO",
                caption = "反搜功能的 API 凭据",
                rows = listOf(
                    DesktopSettingRow.Action(
                        title = if (sauceNaoHasKey == true) "已配置 API Key" else "未配置 API Key",
                        caption = "配置后反搜结果页可用（当前状态实时读凭据仓）",
                        buttonLabel = if (sauceNaoHasKey == true) "更换" else "设置",
                        onAction = onShowSauceNaoDialog,
                    ),
                ),
            )
        },
        DesktopSettingsCard(
            title = "Pixiv 画质",
            caption = "卡片与详情图四档",
            rows = listOf(
                DesktopSettingRow.Options(
                    title = "卡片预览画质",
                    caption = null,
                    options = listOf(
                        PixivImageQuality.SMALL.label,
                        PixivImageQuality.MEDIUM.label,
                        PixivImageQuality.LARGE.label,
                    ),
                    selectedIndex = when (pixivPreviewQ) {
                        PixivImageQuality.SMALL -> 0
                        PixivImageQuality.LARGE, PixivImageQuality.ORIGINAL -> 2
                        else -> 1
                    },
                    onSelect = { i ->
                        prefs.setPixivPreviewQuality(
                            when (i) {
                                0 -> PixivImageQuality.SMALL
                                2 -> PixivImageQuality.LARGE
                                else -> PixivImageQuality.MEDIUM
                            },
                        )
                    },
                ),
                DesktopSettingRow.Options(
                    title = "插画详情画质",
                    caption = null,
                    options = PixivImageQuality.entries.map { it.label },
                    selectedIndex = PixivImageQuality.entries.indexOf(pixivIllustQ).coerceAtLeast(0),
                    onSelect = { i -> prefs.setPixivIllustQuality(PixivImageQuality.entries[i]) },
                ),
                DesktopSettingRow.Options(
                    title = "漫画详情画质",
                    caption = null,
                    options = PixivImageQuality.entries.map { it.label },
                    selectedIndex = PixivImageQuality.entries.indexOf(pixivMangaQ).coerceAtLeast(0),
                    onSelect = { i -> prefs.setPixivMangaQuality(PixivImageQuality.entries[i]) },
                ),
                DesktopSettingRow.Options(
                    title = "大图缩放画质",
                    caption = null,
                    options = PixivImageQuality.entries.map { it.label },
                    selectedIndex = PixivImageQuality.entries.indexOf(pixivZoomQ).coerceAtLeast(0),
                    onSelect = { i -> prefs.setPixivZoomQuality(PixivImageQuality.entries[i]) },
                ),
            ),
        ),
        DesktopSettingsCard(
            title = "站点",
            caption = "漫画源启停、脚本编辑、源站登录",
            rows = listOf(
                DesktopSettingRow.Entry(
                    title = "站点",
                    caption = "34 颗内置站点的启停 / 编辑 / 登录",
                    value = null,
                    onOpen = openSourcesPage,
                ),
            ),
        ),
    )
}

/** 构造「数据与备份」分区设置卡。 */
@Composable
internal fun buildDataSettingsCards(
    prefs: DesktopGalleryPreferences,
    paths: PathProvider?,
    scope: CoroutineScope,
    cacheBytes: Long,
    clearingCache: Boolean,
    dirPickerError: String?,
    onDirPickerError: (String?) -> Unit,
    onCacheBytesChange: (Long) -> Unit,
    onClearingCacheChange: (Boolean) -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
): List<DesktopSettingsCard> {
    val ux = LocalUxPlatformServices.current
    val downloadDir by prefs.downloadDir.collectAsState()
    val downloadUseDefaultDir by prefs.downloadUseDefaultDir.collectAsState()
    val comicDownloadDir by prefs.comicDownloadDir.collectAsState()

    return buildList {
        add(
            DesktopSettingsCard(
                title = "数据",
                caption = "留底 / 历史 / 统计",
                rows = listOf(
                    DesktopSettingRow.Entry("备份与还原", "三张核心表 ↔ JSON", null, onOpen = onOpenBackup),
                    DesktopSettingRow.Entry("阅读历史", null, null, onOpen = onOpenHistory),
                    DesktopSettingRow.Entry("阅读统计", "14 天趋势 + 常读 Top 10", null, onOpen = onOpenStats),
                ),
            ),
        )
        if (paths != null) {
            val shownDir = if (downloadDir.isBlank()) desktopGallerySaveDir(paths).absolutePath else downloadDir
            val shownComicDir = if (comicDownloadDir.isBlank()) {
                desktopComicDownloadsRoot(paths.dataRoot).absolutePath
            } else {
                comicDownloadDir
            }
            add(
                DesktopSettingsCard(
                    title = "下载目录",
                    caption = "图库存图与漫画离线下载目录",
                    rows = listOf(
                        DesktopSettingRow.Toggle(
                            title = "默认目录直存",
                            caption = if (downloadUseDefaultDir) {
                                "开：存图不再问路，直接落到下面那一格目录"
                            } else {
                                "关：每次存图先弹目录选择，取消就不落盘"
                            },
                            checked = downloadUseDefaultDir,
                            onToggle = { prefs.setDownloadUseDefaultDir(it) },
                        ),
                        DesktopSettingRow.Entry(
                            title = "图库下载目录",
                            caption = dirPickerError ?: "点右侧换目录；留空回内置 数据根/downloads/图库",
                            value = if (downloadDir.isBlank()) "内置默认" else downloadDir,
                            onOpen = {
                                val picked = DesktopDirChooser.pickDirectory("选择图库下载目录", shownDir) {
                                    onDirPickerError(it)
                                }
                                if (picked != null) {
                                    onDirPickerError(null)
                                    prefs.setDownloadDir(picked)
                                }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "打开图库文件夹",
                            caption = "在资源管理器里看已下载的图",
                            buttonLabel = "打开",
                            onAction = {
                                scope.launch { runCatching { Desktop.getDesktop().open(File(shownDir)) } }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "恢复图库内置目录",
                            caption = "清掉自定义，回到数据根下的 downloads/图库",
                            buttonLabel = "恢复",
                            enabled = downloadDir.isNotBlank(),
                            onAction = { prefs.setDownloadDir("") },
                        ),
                        DesktopSettingRow.Entry(
                            title = "漫画下载目录",
                            caption = "新下载漫画章节存放目录；留空回内置 数据根/downloads",
                            value = if (comicDownloadDir.isBlank()) "内置默认" else comicDownloadDir,
                            onOpen = {
                                val picked = DesktopDirChooser.pickDirectory("选择漫画下载目录", shownComicDir) {
                                    onDirPickerError(it)
                                }
                                if (picked != null) {
                                    onDirPickerError(null)
                                    prefs.setComicDownloadDir(picked)
                                }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "打开漫画文件夹",
                            caption = "在资源管理器里看已下载的漫画章节",
                            buttonLabel = "打开",
                            onAction = {
                                scope.launch {
                                    runCatching {
                                        val dir = File(shownComicDir)
                                        dir.mkdirs()
                                        Desktop.getDesktop().open(dir)
                                    }
                                }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "恢复漫画内置目录",
                            caption = "清掉自定义，回到数据根下的 downloads（已下载的旧漫画仍保留）",
                            buttonLabel = "恢复",
                            enabled = comicDownloadDir.isNotBlank(),
                            onAction = { prefs.setComicDownloadDir("") },
                        ),
                    ),
                ),
            )
            val sizeLabel = if (cacheBytes < 0) "统计中…" else formatBytes(cacheBytes)
            add(
                DesktopSettingsCard(
                    title = "缓存",
                    caption = "占用 $sizeLabel（进入本页时统计）",
                    rows = listOf(
                        DesktopSettingRow.Action(
                            title = "清理接口与 HTTP 缓存",
                            caption = "回收 pixiv 接口 JSON、逐帧解包目录与 okhttp 活缓存（2026-11-04 起 HTTP 缓存也在清理口径内）",
                            buttonLabel = if (clearingCache) "清理中…" else "清理（现 $sizeLabel）",
                            enabled = !clearingCache,
                            onAction = {
                                onClearingCacheChange(true)
                                scope.launch {
                                    val freed = DesktopCacheMaintenance.clear(paths)
                                    DesktopHttpEngine.evictHttpCache()
                                    onCacheBytesChange(DesktopCacheMaintenance.measureBytes(paths))
                                    onClearingCacheChange(false)
                                    ux.toast("已释放 ${formatBytes(freed)}")
                                }
                            },
                        ),
                        DesktopSettingRow.Action(
                            title = "浏览历史",
                            caption = "清空本机画廊浏览记录（历史页同步清空）",
                            buttonLabel = "清空",
                            buttonColor = LocalSettingsGlass.current.danger,
                            onAction = {
                                scope.launch {
                                    DesktopGalleryBrowseHistory.getInstance(paths).clear()
                                    ux.toast("浏览历史已清空")
                                }
                            },
                        ),
                    ),
                ),
            )
        }
    }
}

/** 构造「网络」分区设置卡。 */
@Composable
internal fun buildNetworkSettingsCards(
    networkPrefs: NetworkPreferences?,
    mainThemeStore: JsonKeyValueStore?,
    paths: PathProvider?,
    galleryHandle: DesktopGalleryHandle?,
    scope: CoroutineScope,
): List<DesktopSettingsCard> {
    if (networkPrefs == null) {
        return listOf(
            DesktopSettingsCard(
                title = "网络",
                caption = "设置暂不可用",
                rows = listOf(DesktopSettingRow.Absent("网络设置", "网络偏好件未接入")),
            ),
        )
    }
    val proxyType = networkPrefs.proxyType.collectAsState().value
    val proxyHost = networkPrefs.proxyHost.collectAsState().value
    val proxyPort = networkPrefs.proxyPort.collectAsState().value
    val httpCacheMb = networkPrefs.httpCacheMaxMb.collectAsState().value
    val downloadThreads = networkPrefs.downloadThreads.collectAsState().value
    val pximgMirror by DesktopPximgMirror.host.collectAsState()
    val probeState by DesktopLineProbe.state.collectAsState()
    val davConfig = paths?.let { DesktopWebDavSync.readConfig(it) }
    val davState by DesktopWebDavSync.state.collectAsState()
    val davStore = paths?.let { JsonKeyValueStore(PreferenceKeys.PREFS_WEBDAV, it) }

    return buildList {
        add(
            DesktopSettingsCard(
                title = "网络代理",
                caption = "直连 / 系统跟随 / 手动 HTTP（改后重启生效）",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "代理类型",
                        caption = "「系统」档跟随 -Pproxy= 启动参数",
                        options = listOf("直连", "系统", "手动"),
                        selectedIndex = listOf("直连", "系统", "手动").indexOf(proxyType).coerceAtLeast(0),
                        onSelect = { i -> networkPrefs.setProxy(listOf("直连", "系统", "手动")[i], proxyHost, proxyPort) },
                    ),
                    DesktopSettingRow.Text(
                        title = "主机",
                        caption = "手动档生效",
                        value = proxyHost,
                        onCommit = { networkPrefs.setProxy(proxyType, it, proxyPort) },
                    ),
                    DesktopSettingRow.Text(
                        title = "端口",
                        caption = "手动档生效（1–65535）",
                        value = if (proxyPort > 0) "$proxyPort" else "",
                        onCommit = { networkPrefs.setProxy(proxyType, proxyHost, it.toIntOrNull() ?: 0) },
                    ),
                ),
            ),
        )
        if (mainThemeStore != null) {
            add(
                DesktopSettingsCard(
                    title = "图床反代",
                    caption = "自定义 Pixiv 图片加速域名",
                    rows = listOf(
                        DesktopSettingRow.Action(
                            title = "快捷节点",
                            caption = if (pximgMirror.isBlank()) "当前直连官方图床" else "反代失败时自动退回直连",
                            buttonLabel = if (pximgMirror == DesktopPximgMirror.PIXIV_RE) "恢复直连" else "设为 i.pixiv.re",
                            onAction = {
                                val next = if (pximgMirror == DesktopPximgMirror.PIXIV_RE) {
                                    DesktopPximgMirror.DIRECT
                                } else {
                                    DesktopPximgMirror.PIXIV_RE
                                }
                                DesktopPximgMirror.write(mainThemeStore, next)
                            },
                        ),
                        DesktopSettingRow.Text(
                            title = "反代域名",
                            caption = "填域名后自动生效，留空直连",
                            value = pximgMirror,
                            onCommit = { DesktopPximgMirror.write(mainThemeStore, it) },
                        ),
                    ),
                ),
            )
        }
        add(
            DesktopSettingsCard(
                title = "缓存上限",
                caption = "HTTP 响应缓存（改后重启生效）",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "HTTP 缓存上限",
                        caption = "此前写死 100MB，现已开放",
                        options = listOf("100 MB", "256 MB", "512 MB", "1024 MB"),
                        selectedIndex = listOf(100, 256, 512, 1024).indexOf(httpCacheMb).coerceAtLeast(0),
                        onSelect = { i -> networkPrefs.setHttpCacheMaxMb(listOf(100, 256, 512, 1024)[i]) },
                    ),
                ),
            ),
        )
        add(
            DesktopSettingsCard(
                title = "下载",
                caption = "同时下载的章节数（改后重启生效）",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "下载并发",
                        caption = "数值越大同时下载的章节越多；并发过高会挤占看图带宽",
                        options = listOf("1", "2", "4"),
                        selectedIndex = listOf(1, 2, 4).indexOf(downloadThreads).coerceAtLeast(0),
                        onSelect = { i -> networkPrefs.setDownloadThreads(listOf(1, 2, 4)[i]) },
                    ),
                ),
            ),
        )
        add(
            DesktopSettingsCard(
                title = "保存",
                caption = "存到本机的文件名档",
                rows = listOf(
                    DesktopSettingRow.Options(
                        title = "保存命名",
                        caption = "改后下次保存生效",
                        options = listOf("站名-编号", "原名", "画师-编号", "时间戳"),
                        selectedIndex = run {
                            val tier = mainThemeStore?.getString(
                                "pref_gallery_save_naming",
                                GallerySaveNaming.SITE_ID.name,
                            ) ?: GallerySaveNaming.SITE_ID.name
                            GallerySaveNaming.entries.indexOfFirst { it.name == tier }.coerceAtLeast(0)
                        },
                        onSelect = { i ->
                            mainThemeStore?.put("pref_gallery_save_naming", GallerySaveNaming.entries[i].name)
                        },
                    ),
                    DesktopSettingRow.Entry("保存格式", "桌面存原图字节，不转码", "原图", onOpen = {}, clickable = false),
                ),
            ),
        )
        add(
            DesktopSettingsCard(
                title = "线路测速",
                caption = "最近一次对 Gelbooru 的往返延迟",
                rows = listOf(
                    DesktopSettingRow.Action(
                        title = "测速",
                        caption = DesktopLineProbe.readout(probeState),
                        buttonLabel = if (probeState == DesktopLineProbe.State.Running) "测速中…" else "测一次",
                        enabled = probeState != DesktopLineProbe.State.Running,
                        onAction = {
                            if (galleryHandle != null && mainThemeStore != null) {
                                scope.launch {
                                    DesktopLineProbe.probe(galleryHandle.engine.okHttpClient, mainThemeStore)
                                }
                            }
                        },
                    ),
                ),
            ),
        )
        add(
            DesktopSettingsCard(
                title = "WebDAV 同步",
                caption = "归档上传与取回",
                rows = listOfNotNull(
                    davStore?.let { s ->
                        DesktopSettingRow.Text("服务器地址", "https://… 根地址", davConfig?.serverUrl.orEmpty()) {
                            s.put("server_url", it)
                        }
                    },
                    davStore?.let { s ->
                        DesktopSettingRow.Text("账号", null, davConfig?.username.orEmpty()) { s.put("username", it) }
                    },
                    davStore?.let { s ->
                        DesktopSettingRow.Text("密码", "盘上明文（与对岸同形）", davConfig?.password.orEmpty()) {
                            s.put("password", it)
                        }
                    },
                    davStore?.let { s ->
                        DesktopSettingRow.Text("远端目录", "默认 /Venera/", davConfig?.remotePath ?: "/Venera/") {
                            s.put("remote_path", it)
                        }
                    },
                    DesktopSettingRow.Action(
                        title = "上传归档",
                        caption = DesktopWebDavSync.stateLine(davState),
                        buttonLabel = if (davState == DesktopWebDavSync.State.Running) "同步中…" else "上传",
                        enabled = davState != DesktopWebDavSync.State.Running && paths != null,
                        onAction = {
                            val p = paths ?: return@Action
                            scope.launch { DesktopWebDavSync.upload(p) }
                        },
                    ),
                    DesktopSettingRow.Action(
                        title = "取回最新",
                        caption = "下到本机后在备份页选那一份还原",
                        buttonLabel = if (davState == DesktopWebDavSync.State.Running) "同步中…" else "下载",
                        enabled = davState != DesktopWebDavSync.State.Running && paths != null,
                        onAction = {
                            val p = paths ?: return@Action
                            scope.launch { DesktopWebDavSync.downloadLatest(p) }
                        },
                    ),
                ),
            ),
        )
    }
}

/** 构造「收藏」分区设置卡。 */
@Composable
internal fun buildFavoritesSettingsCards(
    comicPrefs: ComicPreferences?,
    prefs: DesktopGalleryPreferences,
): List<DesktopSettingsCard> {
    if (comicPrefs == null) {
        return listOf(
            DesktopSettingsCard(
                title = "收藏",
                caption = "设置暂不可用",
                rows = listOf(DesktopSettingRow.Absent("收藏设置", "漫画偏好件未接入")),
            ),
        )
    }
    val quickFavorite = comicPrefs.quickFavorite.collectAsState().value
    val newFavoriteAddTo = comicPrefs.newFavoriteAddTo.collectAsState().value
    val favoriteSortOrder = comicPrefs.favoriteSortOrder.collectAsState().value

    return listOf(
        DesktopSettingsCard(
            title = "收藏夹",
            caption = "新收藏去向与排序",
            rows = listOf(
                DesktopSettingRow.Options(
                    title = "新收藏位置",
                    caption = "收藏时进默认夹还是每次询问",
                    options = listOf("默认", "每次询问"),
                    selectedIndex = if (newFavoriteAddTo == "每次询问") 1 else 0,
                    onSelect = { i -> comicPrefs.setNewFavoriteAddTo(if (i == 1) "每次询问" else "默认") },
                ),
                DesktopSettingRow.Options(
                    title = "收藏夹排序",
                    caption = "收藏夹列表的排列档",
                    options = listOf("时间", "标题"),
                    selectedIndex = if (favoriteSortOrder == "title") 1 else 0,
                    onSelect = { i -> comicPrefs.setFavoriteSortOrder(if (i == 1) "title" else "time") },
                ),
                DesktopSettingRow.Text(
                    title = "快捷收藏夹",
                    caption = "填收藏夹名；留空则长按收藏打开面板",
                    value = quickFavorite.orEmpty(),
                    onCommit = { comicPrefs.setQuickFavorite(it.trim().ifBlank { null }) },
                ),
            ),
        ),
        DesktopSettingsCard(
            title = "追更",
            caption = "巡检时机与冷却间隔",
            rows = listOf(
                DesktopSettingRow.Toggle(
                    title = "启动时自动检查",
                    caption = "默认关闭；开启后开窗自动巡检一轮",
                    checked = prefs.followAutoCheck.collectAsState().value,
                    onToggle = { prefs.setFollowAutoCheck(it) },
                ),
                DesktopSettingRow.Options(
                    title = "巡检冷却",
                    caption = "同一本在这个间隔内不重复检查",
                    options = listOf("6 小时", "12 小时", "24 小时"),
                    selectedIndex = listOf(6, 12, 24)
                        .indexOf(prefs.followCooldownHours.collectAsState().value)
                        .coerceAtLeast(0),
                    onSelect = { i -> prefs.setFollowCooldownHours(listOf(6, 12, 24)[i]) },
                ),
            ),
        ),
    )
}

/** 构造「关于与更新」分区设置卡（用于搜索过滤与备用模板）。 */
@Composable
internal fun buildAboutSettingsCards(
    autoCheckUpdate: Boolean,
    onToggleAutoCheckUpdate: (Boolean) -> Unit,
    galleryHandle: DesktopGalleryHandle?,
    scope: CoroutineScope,
): List<DesktopSettingsCard> = listOf(
    DesktopSettingsCard(
        title = "关于 VComic",
        caption = "现代化跨平台漫画与画廊阅览中枢",
        rows = buildList {
            add(DesktopSettingRow.Entry("版本", "与 packageVersion 同源", DESKTOP_APP_VERSION, onOpen = {}, clickable = false))
            add(
                DesktopSettingRow.Toggle(
                    title = "应用启动时自动检查更新",
                    caption = "启动后静默检查新版本（每 24 小时最多一次）",
                    checked = autoCheckUpdate,
                    onToggle = onToggleAutoCheckUpdate,
                ),
            )
            if (galleryHandle != null) {
                val updateResult by DesktopUpdateCheck.state.collectAsState()
                add(
                    DesktopSettingRow.Action(
                        title = "检查更新",
                        caption = DesktopUpdateCheck.readoutOf(updateResult, DESKTOP_APP_VERSION),
                        buttonLabel = if (updateResult == DesktopUpdateCheck.Result.Checking) "检查中…" else "检查",
                        enabled = updateResult != DesktopUpdateCheck.Result.Checking,
                        onAction = {
                            scope.launch {
                                DesktopUpdateCheck.check(
                                    client = galleryHandle.engine.okHttpClient,
                                    localVersion = DESKTOP_APP_VERSION,
                                )
                            }
                        },
                    ),
                )
                if (updateResult is DesktopUpdateCheck.Result.Available) {
                    add(
                        DesktopSettingRow.Entry(
                            title = "去发布页下载",
                            caption = "桌面产物是 Exe/Msi，也可页内一键下载安装",
                            value = null,
                            onOpen = {
                                scope.launch {
                                    openInBrowser((updateResult as DesktopUpdateCheck.Result.Available).releaseUrl)
                                }
                            },
                        ),
                    )
                }
            }
            add(
                DesktopSettingRow.Entry(
                    title = "项目主页",
                    caption = "在系统浏览器打开",
                    value = null,
                    onOpen = { scope.launch { openInBrowser(DesktopUpdateCheck.HOMEPAGE_URL) } },
                ),
            )
            add(
                DesktopSettingRow.Entry(
                    title = "更新发布",
                    caption = "在系统浏览器打开发布页",
                    value = null,
                    onOpen = { scope.launch { openInBrowser(DesktopUpdateCheck.RELEASES_URL) } },
                ),
            )
        },
    ),
)

/** 构造「桌面能力」分区设置卡。 */
internal fun buildDesktopCapabilityCards(
    paths: PathProvider?,
    scope: CoroutineScope,
    onToast: (String) -> Unit,
): List<DesktopSettingsCard> {
    if (paths == null) return emptyList()
    val windowStore = JsonKeyValueStore("venera_desktop_window", paths)
    val rememberWindow = windowStore.getBoolean("pref_desktop_window_remember", true)
    val effectsReduce = windowStore.getBoolean("pref_desktop_effects_reduce", false)
    val searchViewMode = readSearchViewMode(paths)
    val protocolSentinel = File(paths.dataRoot, DesktopProtocolRegistrar.DISABLED_SENTINEL)

    return listOf(
        DesktopSettingsCard(
            title = "桌面件",
            caption = "窗口 · 协议 · 日志 · 键位",
            rows = listOf(
                DesktopSettingRow.Options(
                    title = "默认视图",
                    caption = "漫画搜索结果的排布",
                    options = DesktopSearchViewMode.entries.map { it.label },
                    selectedIndex = searchViewMode.ordinal,
                    onSelect = { i -> writeSearchViewMode(paths, DesktopSearchViewMode.entries[i]) },
                ),
                DesktopSettingRow.Toggle(
                    title = "记住窗口",
                    caption = "下次启动回到本次的尺寸与位置（最大化也记）",
                    checked = rememberWindow,
                    onToggle = { windowStore.put("pref_desktop_window_remember", it) },
                ),
                DesktopSettingRow.Toggle(
                    title = "精简特效",
                    caption = "关闭模糊与玻璃材质，低配设备更流畅",
                    checked = effectsReduce,
                    onToggle = { on ->
                        windowStore.put("pref_desktop_effects_reduce", on)
                        DesktopEffects.reduce = on
                    },
                ),
                DesktopSettingRow.Toggle(
                    title = "协议关联",
                    caption = "pixiv:// 与 venera:// 每次启动自动注册",
                    checked = !protocolSentinel.isFile,
                    onToggle = { on ->
                        runCatching {
                            if (on) protocolSentinel.delete() else protocolSentinel.createNewFile()
                        }
                    },
                ),
                DesktopSettingRow.Action(
                    title = "运行日志",
                    caption = "在资源管理器打开日志目录",
                    buttonLabel = "打开",
                    onAction = {
                        scope.launch {
                            runCatching { Desktop.getDesktop().open(File(paths.dataRoot, "logs")) }
                                .onFailure { onToast("打不开日志目录") }
                        }
                    },
                ),
                DesktopSettingRow.Entry("Esc", "返回总览 / 关闭浮层", value = null, onOpen = {}, clickable = false),
                DesktopSettingRow.Entry("← → 与 PageUp PageDown", "翻页（阅读器与查看器）", value = null, onOpen = {}, clickable = false),
                DesktopSettingRow.Entry("+ − 0", "缩放与复位（大图查看器）", value = null, onOpen = {}, clickable = false),
                DesktopSettingRow.Entry("Ctrl K", "搜索设置项（总览页）", value = null, onOpen = {}, clickable = false),
            ),
        ),
    )
}

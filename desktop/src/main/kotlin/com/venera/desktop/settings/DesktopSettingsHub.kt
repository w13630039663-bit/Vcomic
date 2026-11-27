package com.venera.desktop.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.components.WideScreenLayoutMode
import com.venera.compose.data.api.ComicPreferences
import com.venera.compose.data.api.NetworkPreferences
import com.venera.compose.data.api.ReaderPreferences
import com.venera.compose.data.platform.JsonKeyValueStore
import com.venera.compose.data.platform.PathProvider
import com.venera.compose.data.platform.PreferenceKeys
import com.venera.compose.data.update.UpdateDownload
import com.venera.compose.gallery.data.GalleryPixivWorks
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopCacheMaintenance
import com.venera.desktop.gallery.data.DesktopComicReaderPreferences
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import com.venera.desktop.gallery.data.DesktopGalleryHandle
import com.venera.desktop.gallery.data.DesktopGalleryPreferences
import com.venera.desktop.gallery.data.DesktopPixivAccount
import com.venera.desktop.gallery.ui.DesktopWallDensity
import com.venera.desktop.gallery.ui.openInBrowser
import com.venera.desktop.platform.DesktopUpdateCheck
import com.venera.desktop.ui.SettingsCardSpec
import com.venera.desktop.ui.SettingsControlPlacement
import com.venera.desktop.ui.SettingsPageTemplate
import com.venera.desktop.ui.SettingsRowSpec
import com.venera.desktop.ui.theme.VeneraType
import java.io.File
import kotlinx.coroutines.launch

/**
 * 桌面设置中枢入口：统筹分区导航、搜索过滤、弹窗外壳切换与凭证子弹窗。
 * 各分区卡片构造拆至 `DesktopSettingsCards*.kt`，模型在 `DesktopSettingsModels.kt`。
 */
@Composable
internal fun DesktopSettingsHub(
    mode: WideScreenLayoutMode,
    prefs: DesktopGalleryPreferences,
    guard: DesktopGalleryContentGuard,
    readerPrefs: DesktopComicReaderPreferences?,
    onOpenSourceManage: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    paths: PathProvider? = null,
    gelbooruState: GelbooruCredentialState? = null,
    pixivAccount: DesktopPixivAccount? = null,
    pixivWorks: GalleryPixivWorks? = null,
    onOpenPixivAccount: (() -> Unit)? = null,
    onOpenPixivLogin: (() -> Unit)? = null,
    onOpenBlockingRules: ((String) -> Unit)? = null,
    comicPrefs: ComicPreferences? = null,
    businessReaderPrefs: ReaderPreferences? = null,
    galleryCreds: DesktopGalleryCredentials? = null,
    networkPrefs: NetworkPreferences? = null,
    galleryHandle: DesktopGalleryHandle? = null,
    wallDensity: DesktopWallDensity = DesktopWallDensity.Loose,
    onPickWallDensity: (DesktopWallDensity) -> Unit = {},
    onClose: (() -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(DesktopSettingsSection.APPEARANCE) }
    var query by remember { mutableStateOf("") }
    var showGelbooruDialog by remember { mutableStateOf(false) }
    var showSauceNaoDialog by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableStateOf(-1L) }
    var clearingCache by remember { mutableStateOf(false) }
    var maskModeError by remember { mutableStateOf<String?>(null) }
    var dirPickerError by remember { mutableStateOf<String?>(null) }

    val themeMode by DesktopThemeMode.mode.collectAsState()
    val languageMode by DesktopLanguage.mode.collectAsState()
    val isEn = languageMode == DesktopLanguage.EN
    val mainThemeStore = remember(paths) {
        paths?.let { JsonKeyValueStore(PreferenceKeys.PREFS_NAME, it) }
    }
    val updateStore = remember(paths) {
        paths?.let { JsonKeyValueStore("venera_desktop_window", it) }
    }
    var autoCheckUpdate by remember(updateStore) {
        mutableStateOf(updateStore?.let { DesktopUpdateCheck.isAutoCheckEnabled(it) } ?: true)
    }

    LaunchedEffect(paths) {
        if (paths != null) {
            cacheBytes = DesktopCacheMaintenance.measureBytes(paths)
        }
    }

    val appearanceCards = buildAppearanceSettingsCards(mainThemeStore, isEn)
    val galleryCards = rememberGallerySettingsCards(prefs, mainThemeStore, wallDensity, onPickWallDensity, languageMode, isEn)
    val mediaCards = buildMediaSettingsCards(prefs)
    val exploreCards = buildExploreSettingsCards(guard, onOpenBlockingRules) { maskModeError = it }
    val readerCards = buildReaderSettingsCards(readerPrefs, businessReaderPrefs, mainThemeStore)
    val sourceCards = buildSourceSettingsCards(
        prefs = prefs,
        gelbooruState = gelbooruState,
        pixivAccount = pixivAccount,
        galleryCreds = galleryCreds,
        onOpenPixivAccount = onOpenPixivAccount,
        onOpenPixivLogin = onOpenPixivLogin,
        onShowGelbooruDialog = { showGelbooruDialog = true },
        onShowSauceNaoDialog = { showSauceNaoDialog = true },
        openSourcesPage = onOpenSourceManage,
    )
    val dataCards = buildDataSettingsCards(
        prefs = prefs,
        paths = paths,
        scope = scope,
        cacheBytes = cacheBytes,
        clearingCache = clearingCache,
        dirPickerError = dirPickerError,
        onDirPickerError = { dirPickerError = it },
        onCacheBytesChange = { cacheBytes = it },
        onClearingCacheChange = { clearingCache = it },
        onOpenBackup = onOpenBackup,
        onOpenHistory = onOpenHistory,
        onOpenStats = onOpenStats,
    )
    val networkCards = buildNetworkSettingsCards(networkPrefs, mainThemeStore, paths, galleryHandle, scope)
    val favoritesCards = buildFavoritesSettingsCards(comicPrefs, prefs)
    val aboutCards = buildAboutSettingsCards(
        autoCheckUpdate = autoCheckUpdate,
        onToggleAutoCheckUpdate = { enabled ->
            autoCheckUpdate = enabled
            updateStore?.let { DesktopUpdateCheck.setAutoCheckEnabled(it, enabled) }
        },
        galleryHandle = galleryHandle,
        scope = scope,
    )
    val desktopCapabilityExtra = buildDesktopCapabilityCards(paths, scope) { ux.toast(it) }
    val pixelBuddyConfig by DesktopPixelBuddyPrefs.config.collectAsState()
    val pixelBuddyWandMode by DesktopPixelBuddyBus.wandMode.collectAsState()
    val pixelBuddyCards = buildPixelBuddySettingsCards(pixelBuddyConfig, pixelBuddyWandMode, updateStore)

    val rawCardsOf: (DesktopSettingsSection) -> List<DesktopSettingsCard> = { s ->
        when (s) {
            DesktopSettingsSection.APPEARANCE -> appearanceCards
            DesktopSettingsSection.PIXEL_BUDDY -> pixelBuddyCards
            DesktopSettingsSection.GALLERY -> galleryCards
            DesktopSettingsSection.EXPLORE -> exploreCards
            DesktopSettingsSection.READER -> readerCards
            DesktopSettingsSection.SOURCES -> sourceCards
            DesktopSettingsSection.MEDIA -> mediaCards
            DesktopSettingsSection.FAVORITES -> favoritesCards
            DesktopSettingsSection.NETWORK -> networkCards
            DesktopSettingsSection.DATA -> dataCards
            DesktopSettingsSection.DESKTOP_CAPABILITIES -> desktopCapabilityExtra
            DesktopSettingsSection.ABOUT -> aboutCards
        }
    }

    val cardsOf: (DesktopSettingsSection) -> List<DesktopSettingsCard> = { s ->
        rawCardsOf(s).map { it.translate() }
    }

    val q = query.trim()
    val cards = cardsOf(section).filterByQuery(q)
    val templateCards = cards.map { it.toSpec() }
    val searchCards = if (q.isEmpty()) emptyList() else DesktopSettingsSection.entries.flatMap { sec ->
        cardsOf(sec).filterByQuery(q).map { card ->
            card.copy(title = "${sec.title} · ${card.title}").toSpec()
        }
    }
    val flatCards: List<DesktopSettingsCard> = if (q.isEmpty()) {
        cards
    } else {
        DesktopSettingsSection.entries.flatMap { sec ->
            cardsOf(sec).filterByQuery(q).map { card -> card.copy(title = "${sec.title} · ${card.title}") }
        }
    }
    val visibleCards = if (q.isEmpty()) templateCards else searchCards
    val visibleTitle = if (q.isEmpty()) section.title else if (isEn) "Search Results" else "搜索结果"
    val visibleSubtitle = if (q.isEmpty()) section.group else null

    val panelContent: @Composable () -> Unit = {
        SettingsPageTemplate(
            mode = mode,
            title = visibleTitle,
            subtitle = visibleSubtitle,
            note = maskModeError,
            cards = visibleCards,
            navigationWidth = 280.dp,
            cardColumns = 1,
            modifier = Modifier.fillMaxSize(),
            navigation = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                ) {
                    SettingsSearchField(value = query, onChange = { query = it })
                    Spacer(Modifier.height(tokens.spacing.space4))
                    SettingsUserCard(
                        pixivAccount = pixivAccount,
                        pixivWorks = pixivWorks,
                        onOpenPixivAccount = onOpenPixivAccount,
                        onOpenPixivLogin = onOpenPixivLogin,
                    )
                    Spacer(Modifier.height(tokens.spacing.space4))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        DesktopSettingsSection.entries.forEach { sec ->
                            SettingsNavItem(
                                section = sec,
                                selected = q.isEmpty() && section == sec,
                                onClick = { section = sec },
                            )
                        }
                    }
                    Spacer(Modifier.height(tokens.spacing.space3))
                    Text(
                        text = "VComic",
                        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
            },
            headerActions = {
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = tokens.color.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            emptyContent = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (q.isEmpty()) "这一组还没有设置项。" else "没有匹配「$q」的设置项。",
                        fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
            },
        )
    }

    if (onClose != null) {
        val dark = DesktopThemeMode.isDark(themeMode)
        val dialogPalette = settingsDialogPalette(dark)
        SettingsDialogShell(
            sections = DesktopSettingsSection.entries,
            selectedSection = if (q.isEmpty()) section else null,
            onSelectSection = { section = it },
            title = visibleTitle,
            query = query,
            onQueryChange = { query = it },
            cards = flatCards,
            sectionIcon = { settingsSectionIcon(it) },
            userCard = {
                SettingsUserCard(
                    pixivAccount = pixivAccount,
                    pixivWorks = pixivWorks,
                    onOpenPixivAccount = onOpenPixivAccount,
                    onOpenPixivLogin = onOpenPixivLogin,
                )
            },
            footer = "VComic\nVersion $DESKTOP_APP_VERSION",
            palette = dialogPalette,
            onClose = onClose,
            customContent = if (q.isEmpty() && section == DesktopSettingsSection.ABOUT) {
                {
                    val handle = galleryHandle
                    DesktopAboutUpdateContent(
                        palette = dialogPalette,
                        autoCheckEnabled = autoCheckUpdate,
                        onToggleAutoCheck = { enabled ->
                            autoCheckUpdate = enabled
                            updateStore?.let { DesktopUpdateCheck.setAutoCheckEnabled(it, enabled) }
                        },
                        canCheckUpdate = handle != null,
                        onCheckUpdate = {
                            if (handle != null) {
                                scope.launch {
                                    DesktopUpdateCheck.check(
                                        client = handle.engine.okHttpClient,
                                        localVersion = DESKTOP_APP_VERSION,
                                    )
                                }
                            }
                        },
                        onDownloadInstaller = { asset ->
                            if (handle != null) {
                                scope.launch {
                                    val dir = File(
                                        paths?.cacheRoot ?: File(System.getProperty("java.io.tmpdir")),
                                        "updates",
                                    )
                                    val outcome = DesktopUpdateCheck.downloadInstaller(
                                        client = handle.engine.okHttpClient,
                                        asset = asset,
                                        targetDir = dir,
                                    )
                                    if (outcome is UpdateDownload.Ready) {
                                        DesktopUpdateCheck.launchInstaller(outcome.file)
                                            .onFailure { err ->
                                                ux.toast("拉起安装程序失败：${err.message ?: err.javaClass.simpleName}")
                                            }
                                    }
                                }
                            }
                        },
                        onLaunchInstaller = { file ->
                            DesktopUpdateCheck.launchInstaller(file)
                                .onFailure { err ->
                                    ux.toast("拉起安装程序失败：${err.message ?: err.javaClass.simpleName}")
                                }
                        },
                        onOpenUrl = { url -> scope.launch { openInBrowser(url) } },
                    )
                }
            } else {
                null
            },
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            panelContent()
        }
    }

    if (showSauceNaoDialog && galleryCreds != null) {
        SauceNaoKeyDialog(galleryCreds = galleryCreds, onDismiss = { showSauceNaoDialog = false })
    }
    if (showGelbooruDialog && gelbooruState != null) {
        GelbooruLoginDialog(gelbooruState = gelbooruState, onDismiss = { showGelbooruDialog = false })
    }
}

private fun DesktopSettingsCard.translate(): DesktopSettingsCard = copy(
    title = DesktopI18n.tr(title),
    caption = caption?.let { DesktopI18n.tr(it) },
    rows = rows.map { row ->
        when (row) {
            is DesktopSettingRow.Toggle -> row.copy(
                title = DesktopI18n.tr(row.title),
                caption = row.caption?.let { DesktopI18n.tr(it) },
            )
            is DesktopSettingRow.Options -> row.copy(
                title = DesktopI18n.tr(row.title),
                caption = row.caption?.let { DesktopI18n.tr(it) },
                options = row.options.map { DesktopI18n.tr(it) },
            )
            is DesktopSettingRow.Entry -> row.copy(
                title = DesktopI18n.tr(row.title),
                caption = row.caption?.let { DesktopI18n.tr(it) },
                value = row.value?.let { DesktopI18n.tr(it) },
            )
            is DesktopSettingRow.Action -> row.copy(
                title = DesktopI18n.tr(row.title),
                caption = row.caption?.let { DesktopI18n.tr(it) },
                buttonLabel = DesktopI18n.tr(row.buttonLabel),
            )
            is DesktopSettingRow.Slider -> row.copy(
                title = DesktopI18n.tr(row.title),
                caption = row.caption?.let { DesktopI18n.tr(it) },
                display = { v -> DesktopI18n.tr(row.display(v)) },
            )
            is DesktopSettingRow.Text -> row.copy(
                title = DesktopI18n.tr(row.title),
                caption = row.caption?.let { DesktopI18n.tr(it) },
            )
            is DesktopSettingRow.Absent -> row.copy(
                title = DesktopI18n.tr(row.title),
                caption = row.caption?.let { DesktopI18n.tr(it) },
            )
        }
    },
)

private fun List<DesktopSettingsCard>.filterByQuery(q: String): List<DesktopSettingsCard> =
    map { card ->
        if (q.isEmpty()) {
            card
        } else {
            card.copy(
                rows = card.rows.filter { row ->
                    row.title.contains(q, ignoreCase = true) ||
                        row.caption?.contains(q, ignoreCase = true) == true
                },
            )
        }
    }.filter { it.rows.isNotEmpty() }

private fun DesktopSettingsCard.toSpec(): SettingsCardSpec = SettingsCardSpec(
    title = title,
    caption = caption,
    rows = rows.map { row ->
        SettingsRowSpec(
            title = row.title,
            caption = row.caption,
            onClick = (row as? DesktopSettingRow.Entry)?.takeIf { it.clickable }?.onOpen,
            control = { SettingControl(row) },
            controlPlacement = if (row is DesktopSettingRow.Options) {
                SettingsControlPlacement.Below
            } else {
                SettingsControlPlacement.Inline
            },
            counted = row !is DesktopSettingRow.Absent &&
                !(row is DesktopSettingRow.Entry && !row.clickable),
        )
    },
)

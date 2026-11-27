package com.venera.desktop.settings

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPopUpButton
import com.venera.desktop.ui.components.DesktopSearchField
import com.venera.desktop.ui.components.DesktopSearchFieldColors
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.compose.components.venera.VeneraSwitch
import com.venera.compose.gallery.data.PixivAuthState
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopPixivAccount
import com.venera.desktop.ui.SettingsPageTemplate


@Composable
internal fun SettingsSearchField(value: String, onChange: (String) -> Unit) {
    // 批4·搜索场归一：M3 OutlinedTextField 原生皮退役，删壳委托共享 DesktopSearchField
    // （settingsGlass 色档——形是全仓一颗，色随设置玻璃域）。
    DesktopSearchField(
        value = value,
        onValueChange = onChange,
        onSearch = {},
        placeholder = if (com.venera.desktop.settings.DesktopLanguage.isEnglish()) "Search settings" else "搜索设置项",
        shortcutHint = "Ctrl K",
        onClear = { onChange("") },
        colors = DesktopSearchFieldColors.settingsGlass(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 设置行右侧控件。行骨架、卡壳与分隔线统一由 `SettingsPageTemplate` 提供，
 * 这里只保留设置中枢自己的控件选择规则。
 */
@Composable
internal fun SettingControl(row: DesktopSettingRow) {
    val tokens = VeneraTokens
    when (row) {
        is DesktopSettingRow.Toggle -> VeneraSwitch(
            checked = row.checked,
            onCheckedChange = if (row.enabled) row.onToggle else null,
        )
        is DesktopSettingRow.Options -> {
            // 少选项（≤3）一律用紧凑切换器，不再用大圆角胶囊或下拉框 ——
            // 太不像 Windows。长标签在切换器里单行省略，说明由 caption 承载。
            // ≥4 项（缓存上限那类）才退回下拉，免得挤成一排看不清。
            if (row.options.size <= 3) {
                CardSegmented(
                    options = row.options,
                    selected = row.selectedIndex,
                    onSelect = row.onSelect,
                )
            } else {
                SettingsDropdown(
                    options = row.options,
                    selectedIndex = row.selectedIndex,
                    onSelect = row.onSelect,
                )
            }
        }
        is DesktopSettingRow.Entry -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (row.value != null) {
                Text(
                    text = row.value,
                    fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                    color = tokens.color.textSecondary,
                )
            }
            // 纯展示行（clickable=false）不摆 › —— 从前 onOpen={} 却带箭头，是假入口。
            if (row.clickable) {
                Text(
                    "›",
                    fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
                    color = tokens.color.textTertiary,
                )
            }
        }
        is DesktopSettingRow.Action -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (row.enabled) (row.buttonColor ?: tokens.color.primary) else tokens.color.surfaceContainerHigh)
                    .clickable(enabled = row.enabled, onClick = row.onAction)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = row.buttonLabel,
                    fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = if (row.enabled) Color.White else tokens.color.textTertiary,
                )
            }
        }
        is DesktopSettingRow.Text -> {
            var text by remember(row.value) { mutableStateOf(row.value) }
            // 700ms 防抖提交：停顿即存，不逼用户找保存钮（行上不摆第二个把手）。
            androidx.compose.runtime.LaunchedEffect(text) {
                kotlinx.coroutines.delay(700)
                if (text.trim() != row.value) row.onCommit(text.trim())
            }
            com.venera.desktop.ui.components.DesktopInputField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.width(180.dp),
            )
        }
        is DesktopSettingRow.Slider -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = row.display(row.value),
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textSecondary,
            )
        }
        is DesktopSettingRow.Absent -> Text(
            text = "不可用",
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textTertiary,
            modifier = Modifier
                .border(1.dp, tokens.color.textTertiary.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * 设置项下拉（薄壳，控件）：几何与展开体全在 [DesktopPopUpButton]。
 */
@Composable
internal fun SettingsDropdown(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    DesktopPopUpButton(
        label = null,
        value = options.getOrElse(selectedIndex) { "" },
        options = options,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
    )
}



/** 左导航每一分区的图标（材料图标，配文字标签，非纯图标——ui-ux-pro-max：图标按钮须有文字）。 */
internal fun settingsSectionIcon(section: DesktopSettingsSection): ImageVector = when (section) {
    DesktopSettingsSection.APPEARANCE -> Icons.Outlined.Palette
    DesktopSettingsSection.PIXEL_BUDDY -> Icons.Outlined.Pets
    DesktopSettingsSection.GALLERY -> Icons.Outlined.GridView
    DesktopSettingsSection.EXPLORE -> Icons.Outlined.PhotoLibrary
    DesktopSettingsSection.READER -> Icons.Outlined.MenuBook
    DesktopSettingsSection.FAVORITES -> Icons.Outlined.Star
    DesktopSettingsSection.NETWORK -> Icons.Outlined.Public
    DesktopSettingsSection.SOURCES -> Icons.Outlined.Tune
    DesktopSettingsSection.MEDIA -> Icons.Outlined.Movie
    DesktopSettingsSection.DATA -> Icons.Outlined.Storage
    DesktopSettingsSection.DESKTOP_CAPABILITIES -> Icons.Outlined.Laptop
    DesktopSettingsSection.ABOUT -> Icons.Outlined.Info
}

/** 卡片大小三档的屏上叫法（档义：偏小=同窗列更多卡更小，偏大相反）。 */
internal fun cardSizeLabel(size: com.venera.desktop.gallery.ui.DesktopCardSize): String = when (size) {
    com.venera.desktop.gallery.ui.DesktopCardSize.Small -> "偏小（列更多）"
    com.venera.desktop.gallery.ui.DesktopCardSize.Standard -> "标准"
    com.venera.desktop.gallery.ui.DesktopCardSize.Large -> "偏大（列更少）"
}

/** 详情右栏宽度三档的屏上叫法。 */
internal fun paneWidthLabel(width: com.venera.desktop.gallery.ui.DesktopDetailPaneWidth): String = when (width) {
    com.venera.desktop.gallery.ui.DesktopDetailPaneWidth.Narrow -> "窄"
    com.venera.desktop.gallery.ui.DesktopDetailPaneWidth.Standard -> "标准"
    com.venera.desktop.gallery.ui.DesktopDetailPaneWidth.Wide -> "宽"
}

/**
 * 左轨「占位 / 待接」小标签（③）。
 * 不可用项另点名，不混进徽标数字；文案极短、克制标准术语，不带隐喻。
 */
@Composable
internal fun SectionTag(text: String) {
    val tokens = VeneraTokens
    Text(
        text = text,
        fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
        color = tokens.color.textTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, tokens.color.textTertiary.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Windows 当前账户的头像图（系统账户图落盘目录里最新一张）；拿不到 = null，调用点退首字母块。 */
internal fun systemAccountPictureFile(): java.io.File? = runCatching {
    val dir = java.io.File(System.getenv("APPDATA") ?: return null, "Microsoft/Windows/AccountPictures")
    dir.listFiles()?.filter { it.isFile && it.length() > 0 }?.maxByOrNull { it.lastModified() }
}.getOrNull()

/**
 * 左栏用户卡：pixiv 已登录 ⇒ 显示站方账号名与头像（`userHead` 缓存管线），点击进「我的 pixiv」；
 * 未登录 ⇒ 显示电脑账号名与 Windows 账户图，点击进登录页。两边都拿不到头像才退首字母圆块。
 */
@Composable
internal fun SettingsUserCard(
    pixivAccount: DesktopPixivAccount?,
    pixivWorks: com.venera.compose.gallery.data.GalleryPixivWorks?,
    onOpenPixivAccount: (() -> Unit)?,
    onOpenPixivLogin: (() -> Unit)?,
) {
    val tokens = VeneraTokens
    val pixivState = pixivAccount?.authState?.collectAsState()?.value
    val signedInUser = pixivState as? com.venera.compose.gallery.data.PixivAuthState.SignedIn
    val signedIn = signedInUser != null
    val userId = signedInUser?.userId
    val systemUser = remember { System.getProperty("user.name")?.takeIf { it.isNotBlank() } ?: "用户" }
    // 登录态的头像/名字走 userHead 那条缓存管线（与「我的 pixiv」同源），异步到位，没到前先落登录名。
    val uid = userId?.toLongOrNull()
    val userHead = produceState<com.venera.compose.gallery.data.PixivUserHead?>(null, uid) {
        value = if (uid != null) withContext(Dispatchers.IO) { pixivWorks?.userHead(uid)?.getOrNull() } else null
    }
    val displayName = when {
        signedInUser != null -> signedInUser.userName?.takeIf { it.isNotBlank() }
            ?: userHead.value?.name?.takeIf { it.isNotBlank() }
            ?: "pixiv 用户"
        else -> systemUser
    }
    val subText = if (signedIn) "ID ${userId ?: "-"}" else "未登录 pixiv"
    val pixivAvatarUrl = userHead.value?.avatarUrl?.takeIf { it.isNotBlank() }
    val systemAvatarFile = remember { systemAccountPictureFile() }
    val systemAvatar = remember(systemAvatarFile) {
        runCatching {
            systemAvatarFile?.let {
                org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
            }
        }.getOrNull()
    }
    val avatarLetter = displayName.firstOrNull()?.uppercase() ?: "P"
    val onClick = if (signedIn) onOpenPixivAccount else onOpenPixivLogin

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .background(tokens.color.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                signedIn && pixivAvatarUrl != null -> AsyncImage(
                    model = pixivAvatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                !signedIn && systemAvatar != null -> androidx.compose.foundation.Image(
                    bitmap = systemAvatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                else -> Text(
                    text = avatarLetter,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.color.primary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subText,
                fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                color = tokens.color.textTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 左栏一枚导航项：图标 + 文字，选中态浅灰底。 */
@Composable
internal fun SettingsNavItem(
    section: DesktopSettingsSection,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = VeneraTokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) tokens.color.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = settingsSectionIcon(section),
            contentDescription = null,
            tint = if (selected) tokens.color.primary else tokens.color.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = section.title,
            fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
            color = if (selected) tokens.color.textPrimary else tokens.color.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

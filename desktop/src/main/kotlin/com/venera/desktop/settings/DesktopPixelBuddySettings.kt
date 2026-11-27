package com.venera.desktop.settings

import com.venera.compose.data.platform.JsonKeyValueStore

/**
 * 构造设置页「像素伙伴」独立选项卡的分组卡片。
 *
 * @param config 当前生效的像素伙伴配置
 * @param store 主偏好键值存储（可空，空时仅内存生效）
 */
internal fun buildPixelBuddySettingsCards(
    config: PixelBuddyConfig,
    wandMode: Boolean = false,
    store: JsonKeyValueStore?,
): List<DesktopSettingsCard> {
    val en = DesktopLanguage.isEnglish()
    fun t(zh: String, enText: String) = if (en) enText else zh

    val skinOptions = PixelBuddySkin.entries.map { it.label }
    val countOptions = listOf(t("1 只", "1 Pet"), t("2 只", "2 Pets"), t("3 只", "3 Pets"))

    return listOf(
        DesktopSettingsCard(
            title = t("像素伙伴总控", "Pixel Buddy"),
            caption = t("3px 极简方块像素小家伙，可贴侧栏竖爬、顶栏倒挂与跨卡片跳跃", "Minimalist 3px pixel pets on sidebar, top edge & cards"),
            rows = listOf(
                DesktopSettingRow.Toggle(
                    title = t("启用像素互动伙伴", "Enable Pixel Buddy"),
                    caption = t("阅读与看图页自动隐藏", "Auto-hides in reader"),
                    checked = config.enabled,
                    onToggle = { checked ->
                        DesktopPixelBuddyPrefs.update(store) { it.copy(enabled = checked) }
                    },
                ),
                DesktopSettingRow.Options(
                    title = t("在场宠物数量", "Active Pet Count"),
                    caption = t("多只可贴贴或叠罗汉", "Greet & stack up"),
                    options = countOptions,
                    selectedIndex = (config.count - 1).coerceIn(0, 2),
                    onSelect = { idx ->
                        DesktopPixelBuddyPrefs.update(store) { it.copy(count = idx + 1) }
                    },
                ),
                DesktopSettingRow.Options(
                    title = t("主宠形象", "Primary Skin"),
                    caption = t("3种像素风格随时换", "3 pixel skins"),
                    options = skinOptions,
                    selectedIndex = PixelBuddySkin.entries.indexOf(config.primarySkin).coerceAtLeast(0),
                    onSelect = { idx ->
                        val skin = PixelBuddySkin.entries.getOrElse(idx) { PixelBuddySkin.CAT }
                        DesktopPixelBuddyPrefs.update(store) { it.copy(primarySkin = skin) }
                    },
                ),
            ),
        ),
        DesktopSettingsCard(
            title = t("界面互动玩法", "Interactive Behaviors"),
            caption = t("控制像素伙伴与搜索框、漫画/画廊卡片的联动", "Control interactions with search bar and cards"),
            rows = listOf(
                DesktopSettingRow.Toggle(
                    title = t("搜索框聚焦拼字", "Search Bar Pixel Spelling"),
                    caption = t("适用：主界面各搜索框", "Scope: search bars"),
                    checked = config.searchTypingSpell,
                    onToggle = { checked ->
                        DesktopPixelBuddyPrefs.update(store) { it.copy(searchTypingSpell = checked) }
                    },
                ),
                DesktopSettingRow.Toggle(
                    title = t("搜索结果施工拼装", "Result Card Assembly"),
                    caption = t("适用：漫画/画廊搜索", "Comic/Gallery"),
                    checked = config.searchCardBuild,
                    onToggle = { checked ->
                        DesktopPixelBuddyPrefs.update(store) { it.copy(searchCardBuild = checked) }
                    },
                ),
                DesktopSettingRow.Toggle(
                    title = t("爬上漫画与画廊卡片", "Climb Comic & Gallery Cards"),
                    caption = t("适用：画廊与漫画搜索", "Gallery/Search"),
                    checked = config.cardClimb,
                    onToggle = { checked ->
                        DesktopPixelBuddyPrefs.update(store) { it.copy(cardClimb = checked) }
                    },
                ),
                DesktopSettingRow.Toggle(
                    title = t("拉屎与铲屎彩蛋", "Poop & Scoop Easter Egg"),
                    caption = t("适用：主界面日常页面", "Scope: main UI"),
                    checked = config.poopEnabled,
                    onToggle = { checked ->
                        DesktopPixelBuddyPrefs.update(store) { it.copy(poopEnabled = checked) }
                    },
                ),
                DesktopSettingRow.Toggle(
                    title = t("逗猫棒鼠标指针", "Cat Wand Cursor"),
                    caption = t("指针全窗·追逐限主界面", "Full-app cursor"),
                    checked = wandMode,
                    onToggle = { enabled ->
                        DesktopPixelBuddyBus.setWandMode(enabled)
                    },
                ),
            ),
        ),
        DesktopSettingsCard(
            title = t("快捷互动", "Quick Actions"),
            caption = t("立即让在场的小家伙表演一个小动作", "Trigger an instant animation"),
            rows = listOf(
                DesktopSettingRow.Action(
                    title = t("撒一把小鱼干", "Drop Fish Treat"),
                    caption = t("主界面全员下楼抢食", "Drop fish snack"),
                    buttonLabel = t("投喂", "Feed"),
                    onAction = { DesktopPixelBuddyBus.trigger(PixelBuddyCommand.FEED) },
                ),
                DesktopSettingRow.Action(
                    title = t("来一次叠罗汉", "Stack Together"),
                    caption = t("2只以上贴贴背起走", "Needs 2+ pets"),
                    buttonLabel = t("叠起", "Stack"),
                    onAction = { DesktopPixelBuddyBus.trigger(PixelBuddyCommand.STACK) },
                ),
                DesktopSettingRow.Action(
                    title = t("拉一坨像素便便", "Drop Pixel Poop"),
                    caption = t("点击便便即可清理", "Click to scoop"),
                    buttonLabel = t("触发", "Trigger"),
                    onAction = { DesktopPixelBuddyBus.trigger(PixelBuddyCommand.POOP) },
                ),
            ),
        ),
    )
}

package com.venera.desktop.settings

import com.venera.desktop.ui.theme.VeneraType
import com.venera.desktop.ui.DesktopPushButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.venera.compose.security.guard.GuardRulePattern
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopGalleryContentGuard
import com.venera.desktop.ui.DetailPageTemplate
import kotlinx.coroutines.launch

/**
 * 屏蔽规则管理子页（2.5）。按 [type]（KEYWORD/TAG/AUTHOR/COMIC_ID）增删守卫规则。
 *
 * **不是摆设**：这里的规则就是主墙 `desktopWallGuardedWall` 与封面遮罩判定**当下真在读**的那本账
 * （`DesktopGalleryContentGuard.rules`），加一条即挡、删一条即放，写侧走 `addRule/deleteRule`。
 * 校验也走真判据：正则先过 [GuardRulePattern.compiles] 才收；同类型同串（忽略大小写）去重。
 */
@Composable
internal fun DesktopBlockingRulesScreen(
    type: String,
    guard: DesktopGalleryContentGuard?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = VeneraTokens
    val scope = rememberCoroutineScope()
    val rules by (guard?.rules ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
    val typeRules = rules.filter { it.type == type }
    var input by remember(type) { mutableStateOf("") }
    var isRegex by remember(type) { mutableStateOf(false) }
    var error by remember(type) { mutableStateOf<String?>(null) }

    DetailPageTemplate(
        title = blockingTitle(type),
        onBack = onBack,
        modifier = modifier,
    ) {
        // 说明 + 输入行
        Text(
            text = blockingHint(type),
            fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
            color = tokens.color.textTertiary,
            modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space2),
        )
        if (guard == null) {
            Text(
                text = DesktopI18n.t("规则管理暂不可用", "Rule management is unavailable"),
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textSecondary,
                modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space3),
            )
            return@DetailPageTemplate
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
        ) {
            com.venera.desktop.ui.components.DesktopInputField(
                value = input,
                onValueChange = { input = it; error = null },
                modifier = Modifier.weight(1f),
                placeholder = blockingPlaceholder(type),
            )
            if (type != "COMIC_ID") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(DesktopI18n.t("正则", "Regex"), fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight, color = tokens.color.textSecondary)
                    com.venera.desktop.ui.DesktopSwitch(checked = isRegex, onCheckedChange = { isRegex = it })
                }
            }
            DesktopPushButton(
                onClick = {
                    val pattern = input.trim()
                    error = when {
                        pattern.isEmpty() -> DesktopI18n.tr("别填空。", "Cannot be empty.")
                        isRegex && !GuardRulePattern.compiles(pattern) -> DesktopI18n.tr("这串正则编译不过。", "Invalid regex pattern.")
                        typeRules.any { it.pattern.equals(pattern, ignoreCase = true) } -> DesktopI18n.tr("这条已经有了。", "Rule already exists.")
                        else -> null
                    }
                    if (error == null) {
                        scope.launch {
                            val id = guard.addRule(type, pattern, isRegex)
                            if (id >= 0) input = "" else error = DesktopI18n.tr("保存失败，再试一次。", "Save failed, please try again.")
                        }
                    }
                },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(DesktopI18n.t("添加", "Add"), fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight)
            }
        }
        error?.let {
            Text(
                text = it,
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.error,
                modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space1),
            )
        }
        Text(
            text = DesktopI18n.t("已保存 ${typeRules.size} 条", "${typeRules.size} saved"),
            fontWeight = VeneraType.cardTitle.weight,
            color = tokens.color.textSecondary,
            fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
            modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space2),
        )
        if (typeRules.isEmpty()) {
            Text(
                text = DesktopI18n.t("还没有规则。加一条即在该类命中处挡掉。", "No rules yet. Add one to filter matching entries."),
                fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                color = tokens.color.textTertiary,
                modifier = Modifier.padding(horizontal = tokens.spacing.screenHorizontal),
            )
        }
        typeRules.forEach { rule ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.screenHorizontal, vertical = tokens.spacing.space2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.space2),
            ) {
                Text(
                    text = rule.pattern,
                    fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                    color = tokens.color.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (rule.isRegex) {
                    Text(DesktopI18n.t("正则", "Regex"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary)
                }
                if (!rule.isEnabled) {
                    Text(DesktopI18n.t("已停用", "Disabled"), fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight, color = tokens.color.textTertiary)
                }
                IconButton(onClick = { scope.launch { guard.deleteRule(rule.id) } }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = DesktopI18n.t("删除该规则", "Delete rule"),
                        tint = tokens.color.textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun blockingTitle(type: String): String = when (type) {
    "TAG" -> DesktopI18n.t("屏蔽标签", "Blocked Tags")
    "AUTHOR" -> DesktopI18n.t("屏蔽画师", "Blocked Artists")
    "COMIC_ID" -> DesktopI18n.t("屏蔽作品", "Blocked Comics")
    else -> DesktopI18n.t("关键词屏蔽", "Blocked Keywords")
}

@Composable
private fun blockingHint(type: String): String = when (type) {
    "TAG" -> DesktopI18n.t("标签按包含匹配，不是精确匹配。", "Tags use substring matching, not exact match.")
    "AUTHOR" -> DesktopI18n.t("作者按包含匹配，只有提供作者信息的源才生效。", "Authors use substring matching; only applies to sources with author metadata.")
    "COMIC_ID" -> DesktopI18n.t("填写作品编号，按编号精确匹配，不区分源。", "Enter comic ID for exact match across all sources.")
    else -> DesktopI18n.t("关键词命中会挡住标题、作者、标签里含它的条目。", "Keywords filter items whose title, author, or tags contain them.")
}

@Composable
private fun blockingPlaceholder(type: String): String = when (type) {
    "COMIC_ID" -> DesktopI18n.t("作品编号", "Comic ID")
    else -> DesktopI18n.t("输入要屏蔽的内容", "Enter term to block")
}

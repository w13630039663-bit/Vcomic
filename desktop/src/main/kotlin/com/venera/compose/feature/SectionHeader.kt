/**
 * 分区标题（首页六分区与画廊首页共用）。
 *
 * 本轮 Token 化：字号 / 颜色 / 圆角 / 间距全部走 Token。
 * 「›」原为 20sp（明显过大，与 17sp 标题打架），已收敛到 type.chevron。
 */
package com.venera.compose.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import com.venera.compose.ui.tokens.VeneraTokens
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun MiuixSectionHeader(
    title: String,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * 行尾那一段（画廊首页用它摆「换一批 / 查看全部」）。
     *
     * 给了 [trailing] 就**不画那枚「›」，整行也不再可点**：那一颗箭头表达的是"整行是一个入口"，
     * 而行里已经有按钮了 —— 两个动作打架，点标题与点按钮做得不是同一件事，
     * 用户却没法从外观上知道哪个能点、点了会去哪。
     */
    trailing: (@Composable () -> Unit)? = null,
    /**
     * 节头左边那枚**小图标**（用户稿每个节头都带一枚）。
     *
     * 加性参数、默认 `null` ⇒ 其余调用点（漫画侧首页那几个分区）逐像素不变。
     * 尺寸与颜色**刻意写死在组件里**：图标是"这一节讲什么"的记号，不是按钮 ——
     * 各调用点自己挑大小的话，同一屏三节会长出三种粗细。
     */
    icon: ImageVector? = null,
    /**
     * 没有 [trailing] 时要不要画那枚「›」。给"这一节没有全量页"的地方关掉它：
     * 一枚画出来却点不动的箭头就是假把手（本仓反复修过的那一族）。
     */
    chevron: Boolean = true,
    /**
     * 标题**右侧**那一格。
     *
     * 加性参数、默认 `null` ⇒ 其余调用点逐像素不变。
     * 与 [trailing] 的分界：这一格是"**这一节看哪一半**"（属于标题自己），
     * [trailing] 是"**这一节能做什么**"（入口／动作）。两者贴在一起时，动作仍然贴右缘。
     */
    titleTrailing: (@Composable () -> Unit)? = null,
) {
    val tokens = VeneraTokens
    val clickable = onTap != null && trailing == null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.shape.small))
            .then(if (clickable) Modifier.clickable { onTap?.invoke() } else Modifier)
            .padding(
                horizontal = tokens.spacing.space2,
                // 纵向 4dp 配合卡片 12dp 内衬，使标题上下左右边距均等（16dp）。
                vertical = tokens.spacing.space2,
            ),
            // 行尾动作统一贴右缘（SpaceBetween）；本组件同时被漫画首页三个分区复用。
            horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tokens.color.textSecondary,
                    modifier = Modifier.size(tokens.spacing.sectionHeaderIcon),
                )
                Spacer(modifier = Modifier.width(tokens.spacing.space2))
            }
            Text(
                text = title,
                fontSize = tokens.type.itemTitle,
                fontWeight = tokens.type.weightSemibold,
                color = tokens.color.textPrimary,
            )
            if (titleTrailing != null) {
                Spacer(modifier = Modifier.width(tokens.spacing.space4))
                titleTrailing()
            }
        }
        if (trailing != null) {
            trailing()
        } else if (chevron) {
            Text(
                text = "›",
                fontSize = tokens.type.chevron,
                color = tokens.color.textTertiary,
            )
        }
    }
}

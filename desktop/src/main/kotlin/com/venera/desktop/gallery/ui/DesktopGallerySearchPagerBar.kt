package com.venera.desktop.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.theme.VeneraType

/**
 * 画廊搜索底部固定分页条：上一页 / 第 N 页（点击直跳）/ 下一页 + 键盘 `←` `→` 提示。
 *
 * 站方不提供总页数，因此不渲染虚假页码总数，仅在 [canNextPage] 为 false 时禁用下一页。
 */
@Composable
internal fun DesktopGallerySearchPagerBar(
    currentPage: Int,
    canNextPage: Boolean,
    loading: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpPage: (Int) -> Unit,
    onEditingPageChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GalleryTokens.Color
    val barShape = RoundedCornerShape(10.dp)
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember(editing) { mutableStateOf(false) }
    var draft by remember(currentPage) {
        val text = currentPage.toString()
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(0, text.length)))
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(editing) {
        onEditingPageChange(editing)
        if (editing) {
            val text = currentPage.toString()
            draft = TextFieldValue(text = text, selection = TextRange(0, text.length))
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun commitJump() {
        val target = parseSearchJumpPage(draft.text)
        editing = false
        if (target != null && target != currentPage) {
            onJumpPage(target)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(barShape)
            .background(colors.SurfaceLevel1)
            .border(1.dp, colors.OutlineHairline, barShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DesktopButton(
            text = "← 上一页",
            onClick = onPrevPage,
            size = DesktopButtonSize.Compact,
            variant = DesktopButtonVariant.Secondary,
            enabled = currentPage > 1 && !loading,
            horizontalPadding = 10.dp,
        )

        if (editing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "第",
                    fontSize = VeneraType.aux.size,
                    color = colors.TextSecondary,
                )
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.SurfaceLevel2)
                        .border(1.dp, colors.OutlineSelected, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { next ->
                            if (next.text.length <= 4 && next.text.all { it.isDigit() }) {
                                draft = next
                            }
                        },
                        singleLine = true,
                        cursorBrush = SolidColor(colors.AccentPrimary),
                        textStyle = TextStyle(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.TextPrimary,
                            textAlign = TextAlign.Center,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { commitJump() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    hadFocus = true
                                } else if (hadFocus && editing) {
                                    editing = false
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.Enter, Key.NumPadEnter -> {
                                            commitJump()
                                            true
                                        }
                                        Key.Escape -> {
                                            editing = false
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            },
                    )
                }
                Text(
                    text = "页",
                    fontSize = VeneraType.aux.size,
                    color = colors.TextSecondary,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.SurfaceLevel2)
                    .border(1.dp, colors.OutlineHairline, RoundedCornerShape(6.dp))
                    .then(if (!loading) Modifier.clickable { editing = true } else Modifier)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "第 $currentPage 页",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.TextPrimary,
                )
            }
        }

        DesktopButton(
            text = "下一页 →",
            onClick = onNextPage,
            size = DesktopButtonSize.Compact,
            variant = DesktopButtonVariant.Secondary,
            enabled = canNextPage && !loading,
            horizontalPadding = 10.dp,
        )

        if (loading) {
            Text(
                text = "加载中…",
                fontSize = VeneraType.aux.size,
                color = colors.TextTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        } else if (!canNextPage && currentPage >= 1) {
            Text(
                text = "已到末页",
                fontSize = VeneraType.aux.size,
                color = colors.TextTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SearchPagerKbd("←")
            SearchPagerKbd("→")
            Text(
                text = "翻页 · 点击页码直跳",
                fontSize = VeneraType.micro.size,
                color = colors.TextTertiary,
            )
        }
    }
}

@Composable
private fun SearchPagerKbd(label: String) {
    val colors = GalleryTokens.Color
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.SurfaceLevel2)
            .border(1.dp, colors.OutlineHairline, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = VeneraType.micro.size,
            fontFamily = FontFamily.Monospace,
            color = colors.TextSecondary,
        )
    }
}

package com.venera.desktop.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.venera.compose.components.LocalUxPlatformServices
import com.venera.compose.gallery.data.GelbooruCredentialState
import com.venera.compose.ui.tokens.VeneraTokens
import com.venera.desktop.gallery.data.DesktopGalleryCredentials
import com.venera.desktop.ui.DesktopButton
import com.venera.desktop.ui.DesktopButtonSize
import com.venera.desktop.ui.DesktopButtonVariant
import com.venera.desktop.ui.components.DesktopInputField
import com.venera.desktop.ui.theme.VeneraType
import kotlinx.coroutines.launch

/** SauceNAO API Key 配置弹窗。 */
@Composable
internal fun SauceNaoKeyDialog(
    galleryCreds: DesktopGalleryCredentials,
    onDismiss: () -> Unit,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    var inputKey by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.color.surface)
                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "SauceNAO API Key",
                fontSize = VeneraType.pageTitle.size, lineHeight = VeneraType.pageTitle.lineHeight,
                fontWeight = VeneraType.cardTitle.weight,
                color = tokens.color.textPrimary,
            )
            Text(
                text = "从 saucenao.com 账号页取 API Key 粘进下面输入框。",
                fontSize = VeneraType.body.size, lineHeight = VeneraType.body.lineHeight,
                color = tokens.color.textTertiary,
            )
            DesktopInputField(
                value = inputKey,
                onValueChange = { inputKey = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopButton(
                    text = "取消",
                    onClick = onDismiss,
                    size = DesktopButtonSize.Medium,
                    variant = DesktopButtonVariant.Ghost,
                )
                DesktopButton(
                    text = "保存",
                    onClick = {
                        galleryCreds.setSauceNaoKey(inputKey.trim())
                        onDismiss()
                        ux.toast("SauceNAO 凭据已保存")
                    },
                    size = DesktopButtonSize.Medium,
                    variant = DesktopButtonVariant.Primary,
                )
            }
        }
    }
}

/** Gelbooru 官方 API 凭据登录弹窗。 */
@Composable
internal fun GelbooruLoginDialog(
    gelbooruState: GelbooruCredentialState,
    onDismiss: () -> Unit,
) {
    val tokens = VeneraTokens
    val ux = LocalUxPlatformServices.current
    val scope = rememberCoroutineScope()
    var inputUserId by remember { mutableStateOf(gelbooruState.userId) }
    var inputApiKey by remember { mutableStateOf(gelbooruState.apiKey) }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(tokens.color.surface)
                .border(1.dp, tokens.color.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.color.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "G",
                        fontSize = VeneraType.sectionTitle.size, lineHeight = VeneraType.sectionTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.primary,
                    )
                }
                Column {
                    Text(
                        text = "登录 Gelbooru",
                        fontSize = VeneraType.cardTitle.size, lineHeight = VeneraType.cardTitle.lineHeight,
                        fontWeight = VeneraType.cardTitle.weight,
                        color = tokens.color.textPrimary,
                    )
                    Text(
                        text = "配置官方 API 鉴权凭据",
                        fontSize = VeneraType.aux.size, lineHeight = VeneraType.aux.lineHeight,
                        color = tokens.color.textTertiary,
                    )
                }
            }

            Text(
                text = "Gelbooru API 现已强制鉴权。请前往 gelbooru.com 登录后，在「My Account」页面中获取您的 User ID 与 API Key。",
                fontSize = VeneraType.aux.size,
                color = tokens.color.textSecondary,
                lineHeight = 16.sp,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "User ID (纯数字)",
                    fontSize = VeneraType.aux.size,
                    lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                )
                DesktopInputField(
                    value = inputUserId,
                    onValueChange = { inputUserId = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "例如：123456",
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "API Key",
                    fontSize = VeneraType.aux.size,
                    lineHeight = VeneraType.aux.lineHeight,
                    fontWeight = VeneraType.cardTitle.weight,
                    color = tokens.color.textPrimary,
                )
                DesktopInputField(
                    value = inputApiKey,
                    onValueChange = { inputApiKey = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "64位字符串",
                )
            }

            if (!errorMsg.isNullOrBlank()) {
                Text(
                    text = errorMsg.orEmpty(),
                    fontSize = VeneraType.micro.size, lineHeight = VeneraType.micro.lineHeight,
                    color = VeneraTokens.color.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopButton(
                    text = "取消",
                    onClick = onDismiss,
                    size = DesktopButtonSize.Medium,
                    variant = DesktopButtonVariant.Ghost,
                    enabled = !isVerifying,
                )
                DesktopButton(
                    text = if (isVerifying) "正在验证…" else "验证并保存",
                    onClick = {
                        scope.launch {
                            isVerifying = true
                            errorMsg = null
                            val result = gelbooruState.signIn(inputUserId, inputApiKey)
                            isVerifying = false
                            if (result.isSuccess) {
                                ux.toast("Gelbooru 登录成功")
                                onDismiss()
                            } else {
                                errorMsg = result.exceptionOrNull()?.message ?: "验证失败，请检查 User ID 与 Key"
                            }
                        }
                    },
                    size = DesktopButtonSize.Medium,
                    variant = DesktopButtonVariant.Primary,
                    enabled = inputUserId.isNotBlank() && inputApiKey.isNotBlank(),
                    loading = isVerifying,
                )
            }
        }
    }
}

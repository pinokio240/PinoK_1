// File: ui/components/CaptchaDialog.kt
package re.pinok.ui.components

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import re.pinok.SovaApp
import re.pinok.captcha.CaptchaChallenge
import re.pinok.util.AppLog

/**
 * Sprint 1, P0-3 (#76) + #CAPTCHA-V2 (2026-10-17):
 * Диалог ввода VK Captcha.
 *
 * Поддерживает две схемы:
 *  - CLASSIC (sid+img): картинка + поле ввода → captcha_key.
 *  - V2 (redirect_uri): WebView открывает страницу VK ID Captcha; после прохождения
 *    VK редиректит на blank.html#success_token=..., который перехватывается и
 *    возвращается через submitSuccessToken.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaDialog() {
    val app = SovaApp.getOrNull() ?: return
    val challengeState by app.captchaHandler.challenge.collectAsState()
    val challenge = challengeState

    if (challenge == null) return  // нет активной captcha — не показываем

    var input by remember(challenge.sid) { mutableStateOf("") }
    var submitting by remember(challenge.sid) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            // Запрещаем dismiss по тапу вне диалога — нужно явное решение.
        },
        title = {
            Text(
                text = if (challenge.isV2) "Подтвердите, что вы не робот" else "Введите код с картинки",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (challenge.isV2) {
                    CaptchaWebView(challenge, onSubmitted = { submitting = true })
                } else {
                    Text(
                        text = "VK требует подтверждения, что вы не робот. " +
                            "Введите символы с картинки ниже.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2.6f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = challenge.img,
                            contentDescription = "Капча",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim().take(10) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Код с картинки") },
                        singleLine = true,
                        isError = false,
                        enabled = !submitting,
                    )
                    if (submitting) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(16.dp).height(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Проверяем…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!challenge.isV2) {
                TextButton(
                    onClick = {
                        val key = input.trim()
                        if (key.isBlank()) return@TextButton
                        submitting = true
                        challenge.submit(key)
                    },
                    enabled = input.isNotBlank() && !submitting,
                ) {
                    Text("Отправить")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    submitting = false
                    challenge.cancel()
                },
                enabled = !submitting,
            ) {
                Text("Отмена")
            }
        },
    )
}

/**
 * WebView для VK ID Captcha. Загружает redirect_uri; перехватывает редирект на
 * blank.html#success_token=... и вызывает [CaptchaChallenge.submitSuccessToken].
 * Хост-переходы внутри id.vk.com/vk.com разрешены, всё остальное — нет.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptchaWebView(
    challenge: CaptchaChallenge,
    onSubmitted: () -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return super.shouldOverrideUrlLoading(view, request)
                        return handleOrLoad(challenge, url, onSubmitted, view)
                    }

                    @Deprecated("Deprecated in Java — оставлен для старых WebView, в которых не вызывается новая сигнатура")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                        return handleOrLoad(challenge, url, onSubmitted, view)
                    }
                }
                loadUrl(challenge.redirectUri ?: "")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(8.dp)),
    )
}

private fun handleOrLoad(
    challenge: CaptchaChallenge,
    url: String,
    onSubmitted: () -> Unit,
    view: WebView?,
): Boolean {
    // Перехватываем success_token после прохождения капчи.
    val token = extractSuccessToken(url)
    if (token != null) {
        AppLog.i("CaptchaDialog", "VK ID captcha solved — success_token acquired")
        onSubmitted()
        challenge.submitSuccessToken(token)
        // Не открываем в браузере и не грузим дальше — диалог закроется.
        return true
    }
    // Позволяем внутренние редиректы VK (капча многошаговая: id.vk.com → …).
    val allowedHost = url.contains("id.vk.com") || url.contains("id.vk.ru") ||
        url.contains("vk.com") || url.contains("vk.ru") ||
        url.contains("captcha") || url.contains("blank.html")
    if (allowedHost) {
        return false // грузить в этом же WebView
    }
    // Чужой внешний хост — не пускаем (безопасность).
    return true
}

/** Извлекает success_token из url вида ...blank.html#success_token=XXX или ?success_token=XXX. */
private fun extractSuccessToken(url: String): String? {
    val idx = url.indexOf("success_token=")
    if (idx < 0) return null
    var end = url.indexOf('&', idx)
    if (end < 0) end = url.indexOf('#', idx)
    if (end < 0) end = url.length
    val token = url.substring(idx + "success_token=".length, end)
    return if (token.isBlank() || token == "undefined") null else token
}
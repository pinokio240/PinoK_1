// File: ui/components/CaptchaDialog.kt
package re.pinok.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import re.pinok.SovaApp

/**
 * Sprint 1, P0-3 (#76): Диалог ввода VK Captcha.
 *
 * Подписывается на [re.pinok.captcha.UiCaptchaHandler.challenge] (StateFlow).
 * Когда challenge != null — показывает AlertDialog с:
 *  — картинкой captcha (captcha_img, ~130x50 px, aspect ratio ~2.6:1)
 *  — полем ввода (4-5 символов, обычно латиница/цифры)
 *  — кнопками «Отправить» и «Отмена»
 *
 * `Отправить` → `challenge.submit(key)` → `CompletableDeferred.complete(key)` →
 * `VKApiClient.callInternal` retries с captcha_sid+captcha_key.
 *
 * `Отмена` → `challenge.cancel()` → `solve()` возвращает null → запрос отменён
 * (VKApiClient возвращает null, caller видит ошибку).
 *
 * Диалог рендерится на верхнем уровне (в [re.pinok.ui.navigation.SovaNavHost]),
 * поверх всех экранов — captcha может потребоваться в любом запросе.
 *
 * Autocomplete disabled, keyboardType=None — VK captcha чувствительна к регистру
 * и регистру символов. Пользователь вводит как видит.
 */
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
            // Пользователь должен либо ввести код, либо отменить кнопкой.
        },
        title = {
            Text(
                text = "Введите код с картинки",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "VK требует подтверждения, что вы не робот. " +
                        "Введите символы с картинки ниже.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                // Картинка captcha. VK отдаёт ~130x50 px.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.6f)  // ~130/50
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val key = input.trim()
                    if (key.isBlank()) return@TextButton
                    submitting = true
                    // submit завершает CompletableDeferred → solve() возвращает key
                    // → VKApiClient делает retry. Диалог закроется когда challenge
                    // станет null (в finally блоке solve()).
                    challenge.submit(key)
                },
                enabled = input.isNotBlank() && !submitting,
            ) {
                Text("Отправить")
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

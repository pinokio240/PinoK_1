package re.pinok.auth

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import re.pinok.auth.exchange.AuthState

/**
 * Landing screen — экран входа с кнопками «Войти через VK» (WebView),
 * «Войти через Яндекс / Chrome» (внешний браузер, OAuth implicit)
 * и «Офлайн-режим».
 *
 * P2-12 #AUTH-AUDIT: вынесен из AuthActivity.kt в отдельный файл.
 * Функции [parseExternalBrowserToken], [ParsedToken], [parseParams],
 * [urlDecode], [ExternalBrowserShareHintSection], [ManualPasteBlock]
 * также перенесены сюда.
 */

// ═══════════════════════════════════════════════════════════════════
// #41: LANDING SCREEN — начальный экран с кнопкой «Войти через Яндекс / Chrome»
// ═══════════════════════════════════════════════════════════════════

@Composable
internal fun LandingScreen(
    state: AuthState,
    // #VKAUTH-V2 Primary CTA: запуск встроенного WebView (m.vk.ru).
    onStartWebView: () -> Unit = {},
    // Fix #187: вход через РЕАЛЬНЫЙ внешний браузер (Chrome/Яндекс) —
    // OAuth implicit flow с redirect_uri=https://oauth.vk.com/blank.html
    // (Fix #190: sova2://oauth не работает с client_id=6287487).
    // В отличие от onBrowserSessionLogin (который читает CookieManager и
    // не работает для настоящих браузеров) — этот вариант открывает
    // системный браузер, где пользователь уже может быть залогинен в VK.
    onLaunchExternalBrowser: () -> Unit = {},
    // Fix #190: показываем подсказку «Поделиться → PinoK» после запуска
    // внешнего браузера. Fix #191: Share — основной способ, ручная вставка —
    // свёрнута (раскрывается по кнопке «Вставить вручную»).
    showTokenPaste: Boolean = false,
    tokenPasteError: String? = null,
    onPasteToken: (String) -> Unit = {},
    onCancelTokenPaste: () -> Unit = {},
    showManualPaste: Boolean = false,
    onShowManualPaste: () -> Unit = {},
    // «Офлайн-режим» — вызывает OfflineManager (guest mode), оставлена по запросу.
    onOfflineMode: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isLoading = state is AuthState.Loading
    val errorMessage = (state as? AuthState.Error)?.message?.let(::humanizeError)

    // Fix #194b: диалог подтверждения перед запуском внешнего браузера.
    // Пользователь должен успеть прочитать инструкцию (выбрать браузер, где
    // залогинен; после входа — Поделиться → PinoK) ДО того, как браузер
    // перекроет экран приложения.
    var showBrowserConfirmDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp)
            .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(40.dp))

        // Логотип / иконка
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "VK",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "PinoK",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "Вход через VK — музыка, сообщения, друзья,\n" +
                   "группы, фото, стена. Все методы работают.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        // #VKAUTH-V2 #VKID-ONLY Primary CTA — «Войти через VK» (встроенный WebView,
        // VK ID flow). Грузит m.vk.ru/login?app_id=6287487 → VK ID SDK делает
        // silent exchange remixsid→web_token (или показывает форму входа VK ID).
        // Полный cookie capture (9 remix-ключей) → submitWebToken. Path 1.5
        // silentRefreshViaRemixsid удерживает сессию при смене IP (remixsid +
        // httoken + remixnsid + p cookie ... в storage). Авторизация ТОЛЬКО через VK ID.
        Button(
            onClick = onStartWebView,
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Войти через VK", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            text = "Вход через VK ID. Сессия удерживается при смене сети — " +
                   "remixsid, p cookie и все cookies сохраняются автоматически.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        // Secondary — «Войти через Яндекс / Chrome» (внешний браузер, OAuth implicit).
        // Fix #187 + Fix #188: вход через РЕАЛЬНЫЙ внешний браузер
        // (Chrome/Яндекс/Samsung). OAuth implicit flow.
        // Fix #190: redirect_uri=https://oauth.vk.com/blank.html. После входа
        // браузер покажет пустую страницу с access_token в URL — пользователь
        // «Поделиться» → PinoK.
        OutlinedButton(
            onClick = { showBrowserConfirmDialog = true },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Войти через Яндекс / Chrome", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            text = "Вход через браузер, где вы уже залогинены в VK. " +
                   "Перед запуском покажем инструкцию. " +
                   "Внимание: этот способ не удерживает сессию — при смене сети " +
                   "(Wi-Fi ↔ мобильные данные) потребуется повторный вход.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        // Fix #191: показываем «Поделиться»-инструкцию после запуска браузера.
        // Это основной способ — юзер жмёт Share в браузере → выбирает PinoK.
        if (showTokenPaste) {
            ExternalBrowserShareHintSection(
                isLoading = isLoading,
                onShowManualPaste = onShowManualPaste,
                showManualPaste = showManualPaste,
                manualPasteError = tokenPasteError,
                onManualPaste = onPasteToken,
                onCancel = onCancelTokenPaste,
            )
        }

        // #34: Офлайн-режим — доступ к скачанным аудио/видео без авторизации.
        // Заменяет бывшую кнопку «Отмена» (onCancel теперь не вызывается из
        // LandingScreen — отмена происходит только системной кнопкой Back).
        // onOfflineMode → setResult(RESULT_OFFLINE_MODE) → MainActivity
        // показывает OfflineManagerScreen в guest-режиме.
        //
        // Fix #113: кнопка ВСЕГДА доступна (раньше enabled = !isLoading).
        // При зависании входа (state==Loading) это единственный выход —
        // пользователь может сразу уйти в офлайн без ожидания. Для отмены
        // самого входа есть отдельная кнопка «Отмена входа» выше.
        //
        // Fix #198: приведён к единому стилю — FilledTonalButton. При loading
        // подкрашивается primary (чтобы привлечь внимание к escape-hatch).
        FilledTonalButton(
            onClick = onOfflineMode,
            enabled = true,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = if (isLoading)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isLoading)
                    MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Офлайн-режим", style = MaterialTheme.typography.titleMedium)
        }
    }

    // Fix #194b: диалог подтверждения перед запуском внешнего браузера.
    // Пользователь должен успеть прочитать инструкцию ДО того, как браузер
    // перекроет экран приложения — иначе он не поймёт, что делать дальше
    // (выбрать браузер → после входа нажать «Поделиться» → выбрать PinoK).
    if (showBrowserConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBrowserConfirmDialog = false },
            title = {
                Text(
                    text = "Вход через внешний браузер",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Сейчас откроется выбор браузера.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "1. Выберите браузер, где вы уже залогинились в VK (Яндекс, Chrome и т.д.).\n" +
                               "2. Если вы залогинены — ВК предложит «Продолжить как …», нажмите её.\n" +
                               "3. После входа нажмите «Поделиться» (⤴) в меню браузера.\n" +
                               "4. Выберите PinoK в списке — вход произойдёт автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Если «Поделиться» не работает — скопируйте адрес из адресной строки браузера и вернитесь в приложение: оно само распознает токен из буфера.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    // Fix #196: примечание про 405 — VK сервер нестабильно отдаёт
                    // 405 на /blank.html, хотя токен уже в URL. Юзер пугается,
                    // хотя всё работает. Объясняем что это нормально.
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "⚠ Если после входа браузер покажет «405 Method Not Allowed» или пустую страницу — это нормально. Токен уже получен, просто вернитесь в PinoK (системная кнопка Back или переключатель приложений) — вход произойдёт автоматически.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showBrowserConfirmDialog = false
                        onLaunchExternalBrowser()
                    },
                ) {
                    Text("Продолжить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showBrowserConfirmDialog = false },
                ) {
                    Text("Отмена")
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Fix #191: ExternalBrowserShareHintSection + parseExternalBrowserToken
// ═══════════════════════════════════════════════════════════════════

/**
 * Fix #191: секция-подсказка «Поделиться → PinoK» для external browser auth.
 *
 * После того как пользователь нажал «Войти через Яндекс/Chrome» и выбрал
 * браузер, ВК открывает страницу авторизации. Если сессия активна — ВК
 * сразу редиректит на https://oauth.vk.com/blank.html#access_token=...
 * Браузер показывает пустую страницу.
 *
 * ОСНОВНОЙ СПОСОБ (Fix #191): пользователь нажимает «Поделиться» в браузере
 * и выбирает PinoK — приложение получает URL через share intent, токен
 * сохраняется автоматически, вход происходит без копирования/вставки.
 *
 * АВТО-FALLBACK: если юзер просто скопировал URL (долгий тап → Копировать),
 * при возврате в PinoK AuthActivity.onResume проверит буфер и войдёт сам.
 *
 * РУЧНАЯ ВСТАВКА (deep fallback): если ни Share, ни буфер не сработали,
 * пользователь может раскрыть секцию «Вставить вручную» и вставить URL.
 */
@Composable
private fun ExternalBrowserShareHintSection(
    isLoading: Boolean,
    showManualPaste: Boolean,
    onShowManualPaste: () -> Unit,
    manualPasteError: String?,
    onManualPaste: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Шаг 2: Поделиться → PinoK",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "В браузере открылась страница входа VK.\n" +
                       "После авторизации (или сразу, если вы залогинены):\n\n" +
                       "1. Нажмите «Поделиться» (иконка ⤴ в меню браузера)\n" +
                       "2. Выберите PinoK в списке приложений\n" +
                       "3. Вход произойдёт автоматически",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Fix #196: примечание про 405 — VK сервер нестабильно отдаёт
            // 405 на /blank.html, хотя токен уже в URL fragment. Юзер видит
            // «405 Method Not Allowed» и пугается. Объясняем что это нормально
            // и что делать (вернуться в PinoK — токен уже в буфере).
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "⚠ Видите «405» или пустую страницу? Это нормально — токен уже получен.\n" +
                           "Вернитесь в PinoK (Back или переключатель приложений) — вход произойдёт сам.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }

            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "Входим…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (manualPasteError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = manualPasteError,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            // Deep fallback: ручная вставка (раскрывается по тапу).
            if (showManualPaste) {
                ManualPasteBlock(
                    isLoading = isLoading,
                    onSubmit = onManualPaste,
                )
            } else {
                TextButton(
                    onClick = onShowManualPaste,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Не сработало? Вставить ссылку вручную",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            TextButton(
                onClick = onCancel,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Отмена", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Fix #191: блок ручной вставки URL (deep fallback).
 * Показывается когда Share и clipboard не сработали.
 */
@Composable
private fun ManualPasteBlock(
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
) {
    var pastedText by rememberSaveable { mutableStateOf("") }
    // Fix #193: LocalClipboardManager deprecated в Compose 1.8+ (BOM 2025.06.00)
    // в пользу LocalClipboard с suspend-функциями. Чтобы не тянуть coroutine scope
    // в onClick и не рисковать с точным API новой abstraction — читаем буфер
    // через платформенный android.content.ClipboardManager (не deprecated,
    // работает синхронно, не зависит от Compose versioning).
    val context = LocalContext.current
    val clipboardManager = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = pastedText,
            onValueChange = { pastedText = it },
            label = { Text("Ссылка из адресной строки") },
            placeholder = {
                Text(
                    text = "https://oauth.vk.com/blank.html#access_token=...",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            minLines = 2,
            maxLines = 4,
            singleLine = false,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val clip = clipboardManager.primaryClip
                    val text = clip?.getItemAt(0)?.text?.toString()
                    if (!text.isNullOrEmpty()) pastedText = text
                },
                enabled = !isLoading,
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Text("Из буфера", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { onSubmit(pastedText) },
                enabled = !isLoading && pastedText.isNotBlank(),
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Text("Войти", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Fix #190: парсит access_token + user_id из вставленной строки.
 *
 * Принимает любой из форматов:
 *  - Полный URL: https://oauth.vk.com/blank.html#access_token=vk1.a.XXX&user_id=123&expires_in=0
 *  - Фрагмент: #access_token=vk1.a.XXX&user_id=123&expires_in=0
 *  - Только параметры: access_token=vk1.a.XXX&user_id=123&expires_in=0
 *  - URL-encoded варианты (%23 вместо # и т.д.)
 *
 * Возвращает null если не найдены access_token ИЛИ user_id.
 * (user_id нужен обязательно — без него токен бесполезен для VK API.)
 */
internal fun parseExternalBrowserToken(input: String): ParsedToken? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    // Собираем все key=value пары из строки. Ищем и в fragment (#), и в query (?),
    // и в просто тексте (если юзер вставил только параметры).
    val params = mutableMapOf<String, String>()

    // 1. Если есть "#" (fragment) — берём всё после него.
    //    Также обрабатываем URL-encoded "#": "%23".
    val fragmentCandidates = listOf("#", "%23")
    for (sep in fragmentCandidates) {
        val idx = trimmed.lastIndexOf(sep)
        if (idx >= 0) {
            val frag = trimmed.substring(idx + sep.length)
            parseParams(frag, params)
        }
    }

    // 2. Если есть "?" (query) — тоже парсим (на случай error-redirect).
    val qIdx = trimmed.indexOf("?")
    if (qIdx >= 0) {
        val query = trimmed.substring(qIdx + 1).substringBefore("#")
        parseParams(query, params)
    }

    // 3. Если params всё ещё пустой (нет ни # ни ?) — парсим всю строку
    //    как параметры (юзер мог вставить только "access_token=...&user_id=...").
    if (params.isEmpty()) {
        parseParams(trimmed, params)
    }

    val at = params["access_token"] ?: return null
    val uid = params["user_id"]?.toLongOrNull() ?: return null
    val expIn = params["expires_in"]?.toLongOrNull() ?: 0L
    if (at.isBlank() || at.length < 10) return null  // слишком короткий — явно не токен
    return ParsedToken(accessToken = at, userId = uid, expiresIn = expIn)
}

/**
 * Парсит "key1=val1&key2=val2" в map. Декодирует URL-encoding (%26 → &, + → space).
 */
internal fun parseParams(payload: String, out: MutableMap<String, String>) {
    // Сначала режем по "&" (но не по URL-encoded "&" = "%26").
    for (pair in payload.split("&")) {
        val eq = pair.indexOf("=")
        if (eq < 0) continue
        val key = urlDecode(pair.substring(0, eq)).lowercase()
        val value = urlDecode(pair.substring(eq + 1))
        if (key.isNotEmpty() && value.isNotEmpty()) {
            out[key] = value
        }
    }
}

internal fun urlDecode(s: String): String {
    return try {
        java.net.URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        s.replace("+", " ")
    }
}

/** Результат парсинга вставленного токена. */
internal data class ParsedToken(
    val accessToken: String,
    val userId: Long,
    val expiresIn: Long,
)


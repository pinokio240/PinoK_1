package re.pinok.ui.screens.calls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import re.pinok.feature.calls.CallsDependencies
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.media.ConversationParamsDecoder
import re.pinok.util.AppLog

/**
 * #CALLS-JOIN-BY-LINK (2026-09-06): Этап Г/Г4 плана «звонки.перенос.план.md» —
 * «Присоединиться по ссылке» (REV-UI §6.4, модалка calls_join_call_by_link_modal).
 *
 * Заменяет прежний JoinCallDialog CallsMainScreen (лог-only заглушка
 * «join by link» — no-stub нарушение): теперь реальная цепочка
 *   1. парс ссылки vk.ru/call/join/<token> (+ пароль из ?p=, §6.4);
 *   2. анонимный вход (заполнено имя): vchat.getAnonymTokenByLink → token;
 *   3. vchat.joinConversationByLink(joinLink, isVideo, sessionKey | anonymToken)
 *      — authed-вход по session_key, анонимный — по anonymToken (§6.4: два
 *      состояния — авторизованный и «войти без профиля»);
 *   4. ответ (conversation params) → [CallJoinByLinkHolder] → SovaNavHost
 *      открывает CallScreen(incoming=true, joinByLink=true): экран поднимает
 *      существующую цепочку params→signaling→engine, фаза RINGING —
 *      «Принять» шлёт accept-call (эквивалент web-превью calls_preview_*).
 *
 * Рендерятся ТОЛЬКО контролы, обеспеченные фасадом CallsApi (no-stub):
 * ссылка, пароль (?p=), имя анонима, тоггл isVideo. Контролы реверса БЕЗ
 * API в фасаде НЕ рендерятся: «Открыть приложение»/«всегда в браузере»
 * (мобильное приложение и есть нативный клиент), mic/cam-кнопки превью
 * (принять-с-видео — Этап Е), «Профиль/карточка превью» (нет метода
 * превью-данных звонка по ссылке).
 */

/** Результат разбора ссылки-приглашения: токен + пароль (REV-UI §6.4). */
internal data class CallJoinLinkParts(val joinToken: String, val password: String)

/** Сессия успешного joinConversationByLink — conversation params для сигналинга. */
data class CallJoinByLinkSession(
    val paramsJson: JsonObject,
    val isVideo: Boolean,
    val displayTitle: String,
)

/**
 * Holder join-сессии между модалкой и CallScreen. Compose-observable:
 * SovaNavHost перезапускает LaunchedEffect по смене session (pendingOutgoingCallPeerId
 * для этого непригоден — после consumeOutgoingCall он всегда 0, «0→0» эффект
 * не поднимает). CallScreen consume'ит сессию при композиции.
 */
object CallJoinByLinkHolder {
    var session: CallJoinByLinkSession? by mutableStateOf<CallJoinByLinkSession?>(null)
        private set

    /** Заложить сессию после успешного joinConversationByLink (модалка). */
    fun stash(s: CallJoinByLinkSession) {
        session = s
        AppLog.i("CallsJoinByLink", "stash: params keys=${s.paramsJson.keySet().size} isVideo=${s.isVideo}")
    }

    /** Забрать и очистить (CallScreen при композиции join-режима). */
    fun consume(): CallJoinByLinkSession? {
        val s = session
        session = null
        return s
    }
}

/**
 * Разбор ссылки-приглашения по реверсу §6.4 («нормализация ссылки»):
 *  - vk.ru/call/join/<token> — канонический вид (пример из среза §7.3:
 *    «https://vk.ru/call/join/UWkwjRisP8JYQD6WL3nJXr5Q-BDJlD2oGK6Sk6Zx5qg»);
 *  - https://<origin>/call/<s> — «если введено без call» (другие origin'ы);
 *  - https://sferum.ru/?call=<s> — Сферум;
 *  - пароль appended как ?p=<pass> / &p=<pass> — вытаскиваем отдельно;
 *  - сырой ввод без схемы/пути — считаем введённым сам токен.
 * @return null — распознать токен не удалось (честная ошибка ввода).
 */
internal fun parseCallJoinLink(raw: String): CallJoinLinkParts? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    // Пароль — query-параметр p= (реверс §6.4: «?p=<pass> / &p=<pass>»).
    var password = ""
    val qIdx = trimmed.indexOf('?')
    if (qIdx >= 0 && qIdx < trimmed.length - 1) {
        val query = trimmed.substring(qIdx + 1)
        for (pair in query.split('&')) {
            val kv = pair.split('=', limit = 2)
            if (kv.size == 2 && kv[0] == "p") {
                password = android.net.Uri.decode(kv[1])
            }
        }
    }

    // Токен — по приоритету шаблонов (последнее вхождение — самый глубокий сегмент).
    val token: String
    val joinIdx = trimmed.lastIndexOf("/call/join/")
    if (joinIdx >= 0) {
        token = trimmed.substring(joinIdx + "/call/join/".length)
    } else {
        val callIdx = trimmed.lastIndexOf("/call/")
        if (callIdx >= 0) {
            token = trimmed.substring(callIdx + "/call/".length)
        } else {
            val callEq = trimmed.lastIndexOf("call=")
            if (callEq >= 0) {
                token = trimmed.substring(callEq + "call=".length)
            } else {
                val slash = trimmed.lastIndexOf('/')
                token = if (slash >= 0) trimmed.substring(slash + 1) else trimmed
            }
        }
    }
    // Срезать хвосты пути/якоря/query/прочих параметров.
    var clean = token
    for (cut in charArrayOf('?', '&', '/', '#')) {
        val idx = clean.indexOf(cut)
        if (idx >= 0) clean = clean.substring(0, idx)
    }
    clean = clean.trim()
    if (clean.isEmpty()) return null
    return CallJoinLinkParts(joinToken = clean, password = password)
}

/**
 * Модалка «Присоединиться к звонку» (calls_join_call_by_link_modal):
 * ссылка + пароль + имя анонима + тоггл видео, кнопка «Продолжить»
 * (calls_join_call_by_link_modal_continue, disabled без ссылки).
 */
@Composable
fun CallsJoinByLinkDialog(onDismiss: () -> Unit) {
    val deps = LocalCallsDeps.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var linkText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var anonymName by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!joining) onDismiss() },
        title = { Text("Присоединиться к звонку") },
        text = {
            Column {
                Text(
                    "Вставьте ссылку-приглашение (vk.ru/call/join/…)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    placeholder = { Text("https://vk.ru/call/join/...") },
                    singleLine = true,
                    enabled = !joining,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calls_join_call_by_link_modal_input_link"),
                )
                Spacer(Modifier.height(8.dp))
                // Пароль — web показывает поле при пароль-ссылке (REV-UI §6.4);
                // здесь поле видимо всегда, заполнение опционально (?p= из ссылки
                // подхватывается парсером автоматически).
                TextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Пароль (если есть)") },
                    singleLine = true,
                    enabled = !joining,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calls_join_call_by_link_modal_input_password"),
                )
                Spacer(Modifier.height(8.dp))
                // Имя анонима (calls_join_screen_input_name_placeholder, §6.4):
                // заполнено → анонимный вход (getAnonymTokenByLink → anonymToken);
                // пусто → вход от authed-сессии (session_key).
                TextField(
                    value = anonymName,
                    onValueChange = { anonymName = it },
                    placeholder = { Text("Войти без профиля — введите имя") },
                    singleLine = true,
                    enabled = !joining,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calls_join_call_anonym_name"),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Видеозвонок",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = isVideo,
                        onCheckedChange = { isVideo = it },
                        enabled = !joining,
                        modifier = Modifier.testTag("calls_join_call_is_video"),
                    )
                }
                if (errorText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("calls_join_call_error"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !joining && linkText.isNotBlank(),
                onClick = {
                    val parts = parseCallJoinLink(linkText)
                    if (parts == null) {
                        errorText = "Не удалось распознать ссылку — нужен токен vk.ru/call/join/<токен>"
                        return@TextButton
                    }
                    joining = true
                    errorText = ""
                    scope.launch {
                        val result = performJoinByLink(
                            deps = deps,
                            parts = parts,
                            password = password.ifBlank { parts.password },
                            anonymName = anonymName.trim(),
                            isVideo = isVideo,
                        )
                        if (result.success) {
                            val session = result.session
                            if (session != null) {
                                AppLog.i("CallsJoinByLink", "join OK — передаю сессию на экран звонка")
                                CallJoinByLinkHolder.stash(session)
                                onDismiss()
                            } else {
                                joining = false
                                errorText = "Сервер не вернул параметры звонка"
                            }
                        } else {
                            joining = false
                            // Честное РЕАЛЬНОЕ сообщение: lastApiError фасада либо текст исключения.
                            errorText = result.errorMessage
                            Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            ) {
                Text(if (joining) "Подключение…" else "Продолжить")
            }
        },
        dismissButton = {
            TextButton(enabled = !joining, onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Итог join-цепочки: сессия при успехе / РЕАЛЬНОЕ сообщение об ошибке. */
private data class CallJoinResult(val success: Boolean, val session: CallJoinByLinkSession?, val errorMessage: String)

/**
 * Цепочка Г4 (по реверсу §6.4 + фасаду CallsApi):
 *  1. аноним (имя заполнено): vchat.getAnonymTokenByLink(joinLink, anonymName) → token;
 *  2. vchat.joinConversationByLink(joinLink, isVideo, sessionKey, anonymToken) —
 *     authed через session_key / аноним через anonymToken;
 *  3. пароль (если есть) — appended к joinLink как «?p=<pass>» (нормализация §6.4).
 * Тяжёлые вызовы — withContext(Dispatchers.Default) (#ANR-MAIN-IO).
 */
private suspend fun performJoinByLink(
    deps: CallsDependencies,
    parts: CallJoinLinkParts,
    password: String,
    anonymName: String,
    isVideo: Boolean,
): CallJoinResult {
    // joinLink для API = токен из ссылки (KDoc VKApiClient vchatGetAnonymTokenByLink:
    // «joinLink токен из ссылки vk.ru/call/join/<join_link>, base64url»); пароль —
    // appended по реверсу (нормализация §6.4 «пароль appended как ?p=<pass>»).
    val joinLinkParam = if (password.isBlank()) parts.joinToken else parts.joinToken + "?p=" + password
    AppLog.i(
        "CallsJoinByLink",
        "join: token.len=${parts.joinToken.length} password=${password.isNotBlank()} anonym=${anonymName.isNotBlank()} isVideo=$isVideo",
    )
    return try {
        withContext(Dispatchers.Default) {
            // Анонимный вход: токен участника по ссылке (имя — опционально).
            var anonymToken: String? = null
            if (anonymName.isNotBlank()) {
                anonymToken = deps.apiClient.vchatGetAnonymTokenByLink(joinLinkParam, anonymName)
                if (anonymToken == null) {
                    val apiErr = deps.apiClient.lastApiError
                    val msg = if (apiErr.isNullOrBlank()) {
                        "Анонимный токен не получен (ссылка недействительна или звонок завершён)"
                    } else {
                        "Ошибка анонимного входа: $apiErr"
                    }
                    AppLog.w("CallsJoinByLink", "getAnonymTokenByLink failed: $msg")
                    return@withContext CallJoinResult(false, null, msg)
                }
            }
            // Authed-вход: session_key (свежий, фолбэк — кэш prefs).
            var sessionKey: String? = null
            if (anonymToken == null) {
                val sk = deps.ensureCallsSessionKey(force = false)
                val cached = deps.prefs.data.first().callsSessionKey
                sessionKey = when {
                    sk != null && sk.isNotBlank() -> sk
                    cached.isNotBlank() -> cached
                    else -> null
                }
                if (sessionKey == null) {
                    val msg = "Нет сессии звонков — введите имя для входа без профиля"
                    AppLog.w("CallsJoinByLink", "session_key недоступен: $msg")
                    return@withContext CallJoinResult(false, null, msg)
                }
            }
            val resp = deps.apiClient.vchatJoinConversationByLink(
                joinLink = joinLinkParam,
                isVideo = isVideo,
                sessionKey = sessionKey,
                anonymToken = anonymToken,
            )
            if (resp == null) {
                val apiErr = deps.apiClient.lastApiError
                val msg = if (apiErr.isNullOrBlank()) {
                    "Не удалось присоединиться (звонок завершён или ссылка недействительна — calls_join_error_not_found_conversation)"
                } else {
                    "Ошибка присоединения: $apiErr"
                }
                AppLog.w("CallsJoinByLink", "joinConversationByLink failed: $msg")
                return@withContext CallJoinResult(false, null, msg)
            }
            // Валидация ответа: params должны распознаться декодером conversation params.
            val decoded = ConversationParamsDecoder.decodeParamsJson(resp)
            if (decoded == null) {
                AppLog.w(
                    "CallsJoinByLink",
                    "joinConversationByLink: ответ без ожидаемых полей params (${resp.keySet()}) — присоединение невозможно",
                )
                return@withContext CallJoinResult(
                    false,
                    null,
                    "Сервер вернул неожиданные параметры звонка (см. лог)",
                )
            }
            AppLog.i("CallsJoinByLink", "join OK: endpoint=${decoded.endpoint.take(32)}… token=${decoded.token.take(6)}…")
            CallJoinResult(true, CallJoinByLinkSession(resp, isVideo, "Звонок по ссылке"), "")
        }
    } catch (e: Exception) {
        AppLog.e("CallsJoinByLink", "join error", e)
        CallJoinResult(false, null, "Ошибка: ${e.message}")
    }
}

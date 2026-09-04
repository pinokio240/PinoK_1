package re.pinok.feature.calls

import androidx.compose.runtime.staticCompositionLocalOf
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import re.pinok.data.local.SovaPrefs
import re.pinok.data.model.QueueCredential
import re.pinok.data.model.QueueEvent
import re.pinok.data.model.UserProfile

/**
 * Task 20 (2026-09-03): DI-контракт экранов звонков, перенесённых в
 * :feature:calls (6742de6c). Провайдер — SovaApp (:app), CompositionLocal
 * ставится в MainActivity.setContent. Состав членов НЕ сужался — все члены
 * из 6742de6c сохранены (включая longPollClient/getOkUid/getVkUid/
 * getAnonymUid/callsSession*).
 *
 * ТИПОВАЯ ПОЛИТИКА (по фактам лога сборки 2026-09-03): :feature:calls не
 * может видеть :app-типы — цикл зависимостей :app -> :feature:calls ->
 * :app запрещён Gradle. Поэтому:
 *  - SovaPrefs — РЕАЛЬНЫЙ тип: класс перенесён в :core:data (пакет сохранён,
 *    Task 20; компилятор подтвердил — :core:data/:feature:calls собрались);
 *  - ExchangeAuthRepository остался в :app: пакет re.pinok.auth.exchange —
 *    кластер из 14 файлов, same-package ссылки импорта не требуют (перенос
 *    одного файла = 330 unresolved в логе 2026-09-03); фасад CallsAuth;
 *  - VKApiClient (14k строк, импортирует re.pinok.SovaApp — перенос
 *    невозможен за один шаг), Queuev4Client и LongPollClient (конструкторы
 *    принимают VKApiClient) — объявлены фасад-интерфейсами CallsApi /
 *    CallsQueue / CallsLongPoll ниже; рантайм-объекты ТЕ ЖЕ (:app-классы
 *    реализуют фасады без правки сигнатур), поведение звонков не менялось;
 *  - возвращаемый тип getCallConversationParams — Pair<String?, JsonObject?>
 *    (сигнатура SovaApp:1615, под которую написаны вызовы CallScreen:
 *    vchatResp уходит в ConversationParamsDecoder.decodeParamsJson(JsonObject)).
 */
interface CallsDependencies {
    val apiClient: CallsApi
    val prefs: SovaPrefs
    val httpClient: OkHttpClient
    val exchangeAuthRepository: CallsAuth
    val queuev4Client: CallsQueue
    val longPollClient: CallsLongPoll
    val callsSessionKey: String
    val callsSessionUid: Long

    suspend fun ensureCallsSessionKey(force: Boolean): String?
    suspend fun getCallConversationParams(conversationId: String): Pair<String?, JsonObject?>
    fun getOkUid(): Long
    fun getVkUid(): Long
    fun getAnonymUid(): Long
}

/**
 * Фасад VKApiClient для экранов звонков: 17 членов — census вызовов экранов
 * (Task 20 + Task 22: messagesGetInboundCalls — CallsHistoryScreen:76, был
 * пропущен в census Task 20: в перенесённом файле стояло унаследованное
 * `app.apiClient` вместо `deps.apiClient`, поэтому вызов не попал в grep).
 * Возвращаемые типы — gson/примитивы/UserProfile (:core:data)/CallModels.
 * Дефолтных аргументов в интерфейсе НЕТ (override не может переобъявлять
 * дефолты): VKApiClient реализует своими сигнатурами как есть, вызовы из
 * экранов — с явными аргументами (правка Task 20/22, значения те же дефолты).
 *
 * #CALLS-SNAP (2026-09-04), Этап А1 плана «звонки.перенос.план.md»: фасад
 * РАСШИРЕН волнами 1+2 (§2.1–§2.4) на 31 новый член — 48 суммарно. Только
 * добавление: прежние 17 членов не тронуты; состав 8 членов CallsDependencies
 * не сужен (§3.1 плана). Реализации — VKApiClient (KDoc-маркеры #CALLS-SNAP).
 */
interface CallsApi {
    val lastApiError: String?
    val lastApiErrorCode: Int
    fun lastAnonymUid(): Long
    suspend fun usersGetByIds(userIds: List<Long>): Map<Long, UserProfile>
    suspend fun queueSubscribe(userId: Long, queueIdSuffix: String?): QueueCredential?
    suspend fun vchatSystemGetInfo(sessionKey: String): JsonObject?
    suspend fun vchatCreateJoinLink(conversationId: String, sessionKey: String): String?
    suspend fun vchatJoinConversation(
        conversationId: String,
        sessionKey: String,
        isVideo: Boolean,
    ): JsonObject?

    suspend fun vchatHangupConversation(
        conversationId: String,
        sessionKey: String,
        reason: String,
    ): Boolean

    suspend fun vchatStartConversation(
        conversationId: String,
        sessionKey: String?,
        peerUid: Long,
        callerAppId: Long,
    ): JsonObject?

    suspend fun messagesStartCall(peerId: Long, video: Boolean): String?
    suspend fun messagesGetCurrentCalls(): List<JsonObject>
    suspend fun messagesGetInboundCalls(count: Int): List<JsonObject>
    suspend fun callsGetHistory(count: Int, offset: Int): List<JsonObject>
    suspend fun messagesGetCallRecordings(count: Int): List<JsonObject>
    suspend fun messagesGetCallTranscriptions(count: Int): List<JsonObject>
    suspend fun friendsGetOnline(userId: Long?): List<UserProfile>

    // ─── #CALLS-SNAP (2026-09-04): Этап А1 плана «звонки.перенос.план.md», волна 1 ───
    // История/записи (§2.1) и расшифровки ASR (§2.2). Состав 8 членов
    // CallsDependencies не меняется (§3.1 плана: фасад расширяется добавлением).
    // Правило: дефолтных аргументов в фасаде НЕТ (урок K2: «an overriding
    // function is not allowed to specify default values for its parameters» —
    // проверено компилятором 2.0.21; вызовы экранов — с явными аргументами).

    /** История звонков с фильтром и пагинацией маркером —
     *  VK API calls.getHistory{count, offset, fields, filter:"all"|"missed", pagination_marker} (§2.1). */
    suspend fun callsGetHistory(count: Int, offset: Int, filter: String, paginationMarker: String?): List<JsonObject>

    /** История групповых звонков —
     *  VK API calls.getGroupHistory{group_id, count, fields, filter, pagination_marker} (§2.1). */
    suspend fun callsGetGroupHistory(groupId: Long, count: Int, filter: String, paginationMarker: String?): List<JsonObject>

    /** Убрать записи из списка истории —
     *  VK API calls.deleteHistoryRecords{record_ids:"1,2"} (§2.1). */
    suspend fun callsDeleteHistoryRecords(recordIds: List<Long>): Boolean

    /** Очистить личную историю звонков —
     *  VK API calls.clearHistory{} (§2.1). */
    suspend fun callsClearHistory(): Boolean

    /** Убрать записи групповых звонков —
     *  VK API calls.deleteGroupHistoryRecords{record_ids:"1,2", group_id} (§2.1). */
    suspend fun callsDeleteGroupHistoryRecords(recordIds: List<Long>, groupId: Long): Boolean

    /** Очистить историю групповых звонков —
     *  VK API calls.clearGroupHistory{group_id} (§2.1). */
    suspend fun callsClearGroupHistory(groupId: Long): Boolean

    /** Пропущенные звонки —
     *  VK API calls.getMissedCalls{count} (§2.1; был в VKApiClient, вводится в фасад). */
    suspend fun callsGetMissedCalls(count: Int): List<JsonObject>

    /** Список/тексты расшифровок —
     *  VK API calls.getAsrTranscriptions{count} (§2.2). */
    suspend fun callsGetAsrTranscriptions(count: Int): List<JsonObject>

    /** Правка текста расшифровки —
     *  VK API calls.editAsrTranscription{transcription_id, text} (§2.2; имена полей
     *  восстановления не поддаются из снапшотов — уточняются на Этапе В2). */
    suspend fun callsEditAsrTranscription(transcriptionId: String, text: String): Boolean

    /** Удаление расшифровок —
     *  VK API calls.deleteAsrTranscriptions{transcription_ids:"1,2"} (§2.2; имена
     *  полей — как у deleteHistoryRecords, уточняются на Этапе В2). */
    suspend fun callsDeleteAsrTranscriptions(transcriptionIds: List<Long>): Boolean

    // ─── #CALLS-SNAP (2026-09-04): Этап А1, волна 2 — планирование/join/участники/настройки/чат звонка (§2.3, §2.4) ───

    /** Запланированные звонки —
     *  VK API messages.getScheduledCalls{count} (§2.3; был в VKApiClient, вводится в фасад). */
    suspend fun messagesGetScheduledCalls(count: Int): List<JsonObject>

    /** Создать/править запланированный звонок —
     *  VK API messages.editCall{call_id, name?, scheduled_date?} (§2.3). */
    suspend fun messagesEditCall(callId: String, name: String?, scheduledDate: Long?): Boolean

    /** Удалить запланированный звонок —
     *  VK API messages.deleteScheduledCall{call_id} (§2.3). */
    suspend fun messagesDeleteScheduledCall(callId: String): Boolean

    /** Принудительно завершить звонок («Начать сейчас» для запланированных) —
     *  VK API messages.forceCallFinish{call_id} (§2.3). */
    suspend fun messagesForceCallFinish(callId: String): Boolean

    /** Анонимный токен по ссылке-приглашению vk.ru/call/join/<id> —
     *  vchat.getAnonymTokenByLink{joinLink, anonymName?} через fb.do (§2.3).
     *  Возвращает token; okcdn-uid из ответа попадает в lastAnonymUid(). */
    suspend fun vchatGetAnonymTokenByLink(joinLink: String, anonymName: String?): String?

    /** Присоединиться к звонку по ссылке (authed через session_key / anon через anonymToken) —
     *  vchat.joinConversationByLink{joinLink, isVideo, protocolVersion, ...} через fb.do (§2.3). */
    suspend fun vchatJoinConversationByLink(
        joinLink: String,
        isVideo: Boolean,
        sessionKey: String?,
        anonymToken: String?,
    ): JsonObject?

    /** Инвалидировать ссылку-приглашение звонка —
     *  vchat.removeJoinLink{conversationId} через fb.do (§2.3). */
    suspend fun vchatRemoveJoinLink(conversationId: String, sessionKey: String): Boolean

    /** Поиск адресата для «Создать звонок» —
     *  VK API messages.search{q, count:20, extended:1, fields} (§2.3);
     *  возвращает сырые items[] (диалоги/контакты). */
    suspend fun messagesSearchForCallTargets(query: String, count: Int): List<JsonObject>

    /** Чат, связанный со звонком, —
     *  VK API calls.getConversationByCall{call_id, hall_id?} (§2.4). */
    suspend fun callsGetConversationByCall(callId: String, hallId: Long?): JsonObject?

    /** Глобальные настройки/тумблеры звонков —
     *  VK API calls.getSettings{} → {settings:{public_key,is_dev,calls_ip,ip_setting_enabled}, toggles:[{name,enabled}]} (§2.4). */
    suspend fun callsGetSettings(): JsonObject?

    /** Персональные настройки звонков (чтение) —
     *  VK API calls.getUserSettings{} (§2.4). */
    suspend fun callsGetUserSettings(): JsonObject?

    /** Персональные настройки звонков (запись) —
     *  VK API calls.setUserSettings{settings:<JSON>} (§2.4; точная форма settings
     *  уточняется на Этапе З1 — метод проводит реальный вызов уже сейчас). */
    suspend fun callsSetUserSettings(settingsJson: String): Boolean

    /** Настройки конкретного звонка (чтение) —
     *  VK API calls.getCallSettings{call_id} (§2.4). */
    suspend fun callsGetCallSettings(callId: String): JsonObject?

    /** Настройки конкретного звонка (запись; параметры из бандла) —
     *  VK API calls.updateCallSettings{call_id, show_chat_history:0|1} (§2.4). */
    suspend fun callsUpdateCallSettings(callId: String, showChatHistory: Boolean): Boolean

    /** Участники звонка (постранично) —
     *  VK API calls.getParticipants{call_id, offset, count, fields} (§2.4);
     *  response = {count, secret, profiles, anonyms, groups}. */
    suspend fun callsGetParticipants(callId: String, offset: Int, count: Int, fields: String?): JsonObject?

    /** Участники звонка по id —
     *  VK API calls.getParticipantsByIds{call_id, participant_ids:"1,2", fields} (§2.4). */
    suspend fun callsGetParticipantsByIds(callId: String, participantIds: List<Long>, fields: String?): JsonObject?

    /** Реакции в звонке —
     *  VK API calls.getReactions{call_id} (§2.4; параметры из бандла не восстановлены —
     *  уточняются на Ж-0, вызов реальный). */
    suspend fun callsGetReactions(callId: String): JsonObject?

    /** Переименовать участника (custom name) —
     *  VK API calls.editParticipantName{call_id, user_id, name} (§2.4; имена полей —
     *  по аналогии с getParticipantsByIds, уточняются на Ж1). */
    suspend fun callsEditParticipantName(callId: String, userId: Long, name: String): Boolean

    /** Сбросить имя участника —
     *  VK API calls.deleteParticipantName{call_id, user_id} (§2.4; уточняется на Ж1). */
    suspend fun callsDeleteParticipantName(callId: String, userId: Long): Boolean

    /** Проверить допустимость имени участника —
     *  VK API calls.checkParticipantName{call_id, name} (§2.4; ответ сырой —
     *  поле доступности уточняется на Ж1). */
    suspend fun callsCheckParticipantName(callId: String, name: String): JsonObject?

    /** Слушатели/залы (брейкаут) —
     *  VK API voicerooms.getParticipants{call_id, filter:"listeners", offset, count, fields}
     *  (§2.4; в бандле экспортируется как voiceRoomsGetParticipants, wire-метод —
     *  voicerooms.getParticipants через api-шлюз, НЕ fb.do); response = {profiles, anonyms, groups, count, secret}. */
    suspend fun voiceRoomsGetParticipants(callId: String, offset: Int, count: Int, filter: String?): JsonObject?
}

/** Фасад Queuev4Client: setCredential/start/events — вызовы экранов (census). */
interface CallsQueue {
    fun setCredential(cred: QueueCredential)
    fun start()
    val events: SharedFlow<QueueEvent>
}

/**
 * Фасад ExchangeAuthRepository: userId()/remixsid()/buildVkCookieHeader() —
 * вызовы экранов (census Task 20/21/22). buildVkCookieHeader добавлен в Task 22:
 * CallsWebViewScreen синхронизирует куки через RemixsidCapturer (:app, пакет
 * re.pinok.auth.exchange — кластер из 14 файлов, перенос невозможен — тот же
 * корень, что у Task 21); делегируем через уже инжектируемый объект
 * ExchangeAuthRepository, рантайм — тот же RemixsidCapturer.buildVkCookieHeader().
 */
interface CallsAuth {
    fun userId(): Long
    fun remixsid(): String?
    fun buildVkCookieHeader(): String
}

/** Фасад LongPollClient: член без вызовов экранов (состав 6742de6c). */
interface CallsLongPoll

val LocalCallsDeps = staticCompositionLocalOf<CallsDependencies> {
    error("CallsDependencies not provided")
}

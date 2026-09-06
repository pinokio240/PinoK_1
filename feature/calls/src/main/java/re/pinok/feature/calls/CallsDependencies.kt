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

    // ─── #CALLS-SNAP (2026-09-06): РЕВИЗИЯ-2 (REV-DEEP-1/2/3, волна-7) —
    // запланированные (calls.start + editCall full-wire), чат звонка
    // (getHistory/send/getConversationsById), записи (video.edit). Только
    // добавление: прежние 48 членов не тронуты, состав 8 членов
    // CallsDependencies не сужен. Дефолтов в фасаде нет (урок K2).

    /**
     * Создать запланированный звонок — VK API calls.start.
     *
     * РЕВИЗИЯ-2 (REV-DEEP-2, bridge@777039/725601): web создаёт запланированный
     * звонок РОВНО этим методом (обёртка callsStart, сабмит jM→handleOnSubmit
     * `(0,y.callsStart)(PO(e))` без call-аргумента). ПРЕЖНЯЯ гипотеза
     * «messages.editCall с call_id="0"» ОПРОВЕРГНУТА реверсом: в коде web
     * call_id:"0" нигде не встречается, messages.editCall используется ТОЛЬКО
     * для правки/переноса существующего (PO с t=call).
     *
     * Wire-объект wO (bridge@725601, точные имена): {mute_audio, mute_video,
     * name, only_auth_users, duration (СЕКУНДЫ, Math.round(ms/1e3)),
     * time (unix СЕКУНДЫ, пересчитанные в выбранную TZ), recurrence_rule,
     * waiting_hall, feedback, only_admin_can_share_movie,
     * only_admin_can_record, mute_screen_sharing} + условные [group_id],
     * [skip_notification], [show_chat_history], [recurrence_until_time].
     * Calendar/displayName — КЛИЕНТСКИЕ поля web, в API не уходят.
     *
     * MuteState (bridge@720715): "unmute"|"mute"|"mute_permanent";
     * mute_screen_sharing при создании — только "unmute"|"mute_permanent".
     * recurrence_rule (модуль 912069): never|daily|weekly|weekdays|weekend|
     * monthly|yearly (same_week_day UI → weekdays/weekend wire, RO@725500).
     *
     * Ответ (FM@773057): {call_id, join_link,
     * short_credentials:{link_with_password, link_without_password, password}}.
     *
     * @param name               название (web шлёт всегда; пустое — клиентский
     *                           дефолт calls_default_meeting_name, решает UI)
     * @param timeSec            начало, unix СЕКУНДЫ (в TZ пользователя)
     * @param durationSec        длительность в СЕКУНДАХ
     * @param muteAudio          wire-строка MuteState для микрофонов
     * @param muteVideo          wire-строка MuteState для камер
     * @param onlyAuthUsers      анонимная ссылка: false = anonymous ON (wire 0)
     * @param recurrenceRule     повтор (7 wire-значений выше)
     * @param waitingHall        зал ожидания (wire 0/1)
     * @param feedback           реакции (wire 0/1; web-дефолт ON → 1)
     * @param onlyAdminCanShareMovie совместный просмотр: false = ALL (wire 0)
     * @param onlyAdminCanRecord запись: только админ (web шлёт 1/0 всегда)
     * @param muteScreenSharing  wire-строка MuteState демонстрации
     * @param groupId            «от имени группы» (wire group_id; null — omit)
     * @param skipNotification   напоминание ВЫКЛ (null — omit; wire 0/1)
     * @param showChatHistory    история чата (null — omit; wire 0/1)
     * @param recurrenceUntilSec конец повтора (null — omit; wire СЕКУНДЫ)
     * @return response-объект или null (ошибка — lastApiError).
     */
    suspend fun callsStartScheduledCall(
        name: String,
        timeSec: Long,
        durationSec: Long,
        muteAudio: String,
        muteVideo: String,
        onlyAuthUsers: Boolean,
        recurrenceRule: String,
        waitingHall: Boolean,
        feedback: Boolean,
        onlyAdminCanShareMovie: Boolean,
        onlyAdminCanRecord: Boolean,
        muteScreenSharing: String,
        groupId: Long?,
        skipNotification: Boolean?,
        showChatHistory: Boolean?,
        recurrenceUntilSec: Long?,
    ): JsonObject?

    /**
     * Править/перенести существующий запланированный звонок —
     * VK API messages.editCall (ПОЛНЫЙ wire-объект).
     *
     * РЕВИЗИЯ-2 (REV-DEEP-2, bridge@726449 PO с t=call / @899680 Uk-перенос):
     * payload = wO(e) + call_id + marker_time (из item.schedule.marker_time);
     * web использует editCall ТОЛЬКО для существующих (правка BM / перенос Uk).
     * Прежний узкий член messagesEditCall(callId, name, scheduledDate)
     * СОХРАНЁН (правило «фасад расширяется добавлением»); новый член — полный
     * wire, старый остаётся для совместимости сигнатур волны-1.
     *
     * @param markerTime marker_time из schedule айтема (null — omit; web
     *                   шлёт при правке/переносе всегда из айтема)
     * @return true при успехе (response-объект получен).
     */
    suspend fun messagesEditCallScheduled(
        callId: String,
        name: String,
        timeSec: Long,
        durationSec: Long,
        muteAudio: String,
        muteVideo: String,
        onlyAuthUsers: Boolean,
        recurrenceRule: String,
        waitingHall: Boolean,
        feedback: Boolean,
        onlyAdminCanShareMovie: Boolean,
        onlyAdminCanRecord: Boolean,
        muteScreenSharing: String,
        markerTime: Long?,
        groupId: Long?,
        skipNotification: Boolean?,
        showChatHistory: Boolean?,
        recurrenceUntilSec: Long?,
    ): Boolean

    /**
     * Список запланированных звонков (ПОЛНАЯ форма с пагинацией) —
     * VK API messages.getScheduledCalls.
     *
     * РЕВИЗИЯ-2 (REV-DEEP-2, 97907@90169): web шлёт {grouped:1, count:50,
     * [caller_id], [start_from]}; ответ {items, next_from, groups, profiles}.
     * Прежний узкий член messagesGetScheduledCalls(count) СОХРАНЁН.
     *
     * @param grouped   группировка web (1)
     * @param count     страница (web 50)
     * @param callerId  фильтр владельца (full-вкладка, null — omit)
     * @param startFrom пагинация (next_from прошлого ответа, null — первая)
     * @return СЫРОЙ response {items, next_from, groups, profiles} или null.
     */
    suspend fun messagesGetScheduledCallsPage(grouped: Boolean, count: Int, callerId: Long?, startFrom: String?): JsonObject?

    /**
     * История сообщений чата — VK API messages.getHistory.
     *
     * РЕВИЗИЯ-2 (REV-DEEP-1, IM-SPA 83836@1769954): {peer_id, [start_cmid],
     * count, offset, extended:1, fwd_extended:1} (+retries:5 — клиентский
     * ретрай web, у нас общий call()). Направления web: назад offset=-1;
     * вперёд offset=1-p; вокруг offset=-floor(0.8·p). peer_id чата звонка —
     * ГОТОВЫЙ из ответа calls.getConversationByCall → conversation.peer.id
     * (bridge@196400 setRoomChatId; арифметики +2e9 НЕ требуется — приходит
     * готовым; арифметика 2e9 существует только в IM-чанках для chat_id).
     *
     * @param peerId    peer_id чата (conversation.peer.id)
     * @param count     размер страницы
     * @param offset    направление пагинации (см. выше)
     * @param startCmid стартовый conversation_message_id (null — omit)
     * @return items[] (каждый содержит conversation_message_id/date/text/
     *         from_id/action? — сервисные сообщения звонка).
     */
    suspend fun messagesGetHistory(peerId: Long, count: Int, offset: Int, startCmid: Long?): List<JsonObject>

    /**
     * Отправить сообщение в чат — VK API messages.send.
     *
     * РЕВИЗИЯ-2 (REV-DEEP-1): обёртка звонкового бридга — passthrough
     * (bridge@968528 `ue=(e={})=>xP("messages.send")(e)`); builder IM-SPA
     * (83836@1541400) для текста шлёт {peer_id, random_id, message}; живой
     * пример random_id — `Math.round(2e9*Math.random())` (252761de@13996).
     * Вложения/forward/sticker — НЕ в этом члене (загрузчиков в фасаде нет,
     * честное отклонение остаётся).
     *
     * @param peerId   peer_id чата звонка
     * @param message  текст сообщения
     * @param randomId идемпотент отправки (UI генерит round(2e9·random))
     * @return true при успехе (response получен).
     */
    suspend fun messagesSendToPeer(peerId: Long, message: String, randomId: Long): Boolean

    /**
     * Реестр чатов по peer_id — VK API messages.getConversationsById.
     *
     * РЕВИЗИЯ-2 (REV-DEEP-1, bridge@960662): {peer_ids:"1,2", extended:1,
     * fields:"name,photo_100,photo_200,can_upload_video,
     * custom_names_for_calls"} — заголовок/фото чата звонка.
     *
     * @return СЫРОЙ response ({conversations|count, profiles...}) или null.
     */
    suspend fun messagesGetConversationsById(peerIds: List<Long>, fields: String?): JsonObject?

    /**
     * Переименовать запись звонка (заголовок VK-видео) — VK API video.edit.
     *
     * РЕВИЗИЯ-2 (REV-DEEP-3): «метода messages.getCallRecordings не
     * существует; запись — ОБЫЧНЫЙ VK-видео» (DOM страницы записей:
     * video_card_layout + video_card_edit_button-карандаш); calls.*-мутатора
     * переименования в бандлах НЕТ; wire-ГИПОТЕЗА video.edit {owner_id,
     * video_id, name} (обёртка есть: bridge@964211/972213) — подтверждается
     * живым сервером (Этап И), помечено в KDoc реализации.
     *
     * @param ownerId owner_id видео (recordExternalOwnerId / ссылка vkvideo)
     * @param videoId video_id видео (recordExternalMovieId)
     * @param name    новый заголовок
     * @return true при успехе.
     */
    suspend fun videoEditTitle(ownerId: Long, videoId: Long, name: String): Boolean

    /**
     * Группы, от имени которых можно запланировать/создать звонок —
     * VK API messages.getGroupsForCall.
     *
     * РЕВИЗИЯ-2 (REV-DEEP-2, bridge@776476): опции селекта «От имени»
     * (calls_schedule_call_modal_user_select) = я + getGroupsForCall
     * (value = -id); item.wO group_id уходит ТОЛЬКО при создании/правке
     * «от имени группы». Реализация существовала в VKApiClient (метод :app
     * без члена фасада) — введён в фасад для модалки расписания.
     *
     * @return items[] сырых групп ({id, name, photo_50, ...}).
     */
    suspend fun messagesGetGroupsForCallForSchedule(): List<JsonObject>
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

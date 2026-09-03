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

    suspend fun likesAdd(type: String, ownerId: Long, itemId: Long, reactionId: Int? = null, accessKey: String? = null, trackCode: String? = null): Int
    suspend fun likesDelete(type: String, ownerId: Long, itemId: Long, accessKey: String? = null, trackCode: String? = null): Int
    suspend fun likesIsLiked(type: String, ownerId: Long, itemId: Long): Boolean?
    suspend fun wallRepost(object_: String, message: String = ""): Pair<Long, Int>
    suspend fun faveAdd(type: String = "post", ownerId: Long, itemId: Long): Boolean
    suspend fun faveRemove(type: String = "post", ownerId: Long, itemId: Long): Boolean
    suspend fun videoAdd(videoId: Long, ownerId: Long, accessKey: String? = null): Boolean
    suspend fun videoCreateComment(ownerId: Long, videoId: Long, message: String, replyToComment: Long? = null): Long
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

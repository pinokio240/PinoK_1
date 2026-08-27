package re.pinok.data.model

/**
 * #CALLS: модели голосовых/видео-звонков VK (WebRTC).
 *
 * VK-звонки организованы так:
 *  1. Инициатор шлёт messages.startCall → получает call_id.
 *  2. Обе стороны подписываются на queuev4.vk.ru/im1180 через SAT-токен
 *     (queue.credential = {base_url, key, ts}) и лонг-поллят события сигналинга.
 *  3. WebRTC: PeerConnection через STUN/TURN relay calls.okcdn.ru.
 *
 * Это модели состояния звонка — НЕ транспорт. Транспорт в Queuev4Client/WebRtcEngine.
 */

/** Направление звонка. */
enum class CallDirection { INCOMING, OUTGOING }

/** Медиа-тип звонка. */
enum class CallMediaType { AUDIO, VIDEO }

/** Фаза звонка (state machine). */
enum class CallPhase {
    IDLE,          // нет активного звонка
    RINGING,       // звенит (входящий) / дозвон (исходящий)
    CONNECTING,    // установка WebRTC-соединения
    ACTIVE,        // разговор
    ENDED,         // завершён
    FAILED,        // ошибка
}

/** Участник звонка. */
data class CallParticipant(
    val peerId: Long,
    val name: String,
    val photo100: String?,
)

/**
 * Состояние активного/входящего/исходящего звонка.
 * Единственный source of truth для UI (CallScreen).
 */
data class VkCall(
    val callId: String,
    val peer: CallParticipant,
    val direction: CallDirection,
    val mediaType: CallMediaType,
    val phase: CallPhase,
    val isVideoEnabled: Boolean = mediaType == CallMediaType.VIDEO,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val startTimeMs: Long = 0L,
) {
    companion object {
        val EMPTY = VkCall(
            callId = "",
            peer = CallParticipant(0L, "", null),
            direction = CallDirection.OUTGOING,
            mediaType = CallMediaType.AUDIO,
            phase = CallPhase.IDLE,
        )
    }
}

/**
 * Queue-credential для queuev4.vk.ru (сигналинг звонков).
 * Формат из localStorage: queue_credential_calls_cache_<uid>_<app_id> →
 *   {"data":{"key":"<sha256>","ts":<long>,"url":"https://queuev4.vk.ru/im1180",
 *            "id":<uid>},"lastUpdate":<ms>}
 */
data class QueueCredential(
    val key: String,
    val ts: Long,
    val url: String,
    val userId: Long,
)

/** Событие из очереди queuev4 (сигналинг звонков). */
data class QueueEvent(
    val queueId: String,
    val ts: Long,
    val payload: Map<String, Any?>,
)

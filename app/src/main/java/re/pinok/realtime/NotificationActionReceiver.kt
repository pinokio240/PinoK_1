package re.pinok.realtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import re.pinok.SovaApp
import re.pinok.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * §42.2 #PUSH-ENHANCED — приёмник действий из notification (кнопка «Прочитать»).
 *
 * Когда пользователь нажимает «Прочитать» в push-уведомлении, NotificationCompat
 * отправляет PendingIntent на этот BroadcastReceiver. Мы:
 *   1. Помечаем уведомление как прочитанное на сервере (notifications.markAsRead).
 *   2. Отменяем system notification (убираем из шторки).
 *
 * §46 #REMOTE-INPUT — кнопка «Ответить» с RemoteInput (прямой ответ из шторки).
 * Пользователь разворачивает уведомление, нажимает «Ответить», вводит текст в
 * системном диалоге, и текст отправляется как комментарий через VK API
 * (wall.createComment / photos.createComment). Не нужно открывать приложение.
 *
 * Зарегистрирован в AndroidManifest.xml.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_READ = "re.pinok.action.MARK_READ"
        const val ACTION_REPLY = "re.pinok.action.REPLY"
        // Fix #135b: срабатывает когда пользователь свайпает message notification
        // (или делает "Clear all"). MessageNotifier.showNotification ставит
        // этот action в setDeleteIntent — нужно сбросить activeNotifications кеш,
        // иначе следующее сообщение в этом диалоге будет инкрементировать
        // старый unreadCount.
        // Константа дублирована из MessageNotifier.ACTION_DISMISS для удобства
        // использования внутри receiver — но источник истины = MessageNotifier.
        const val ACTION_DISMISS = MessageNotifier.ACTION_DISMISS

        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_NOTIF_DATE = "notif_date"

        // §46 #REMOTE-INPUT: extras для кнопки «Ответить».
        /** Key для RemoteInput — текст ответа пользователя. */
        const val EXTRA_REPLY_TEXT = "reply_text_key"
        /** Тип родительского объекта: "wall" | "photo" | "video". Определяет API. */
        const val EXTRA_REPLY_TARGET_TYPE = "reply_target_type"
        /** parentOwnerId — владелец поста/фото/видео (может быть отрицательным = группа). */
        const val EXTRA_REPLY_OWNER_ID = "reply_owner_id"
        /** parentItemId — post_id / photo_id / video_id. */
        const val EXTRA_REPLY_ITEM_ID = "reply_item_id"
        /** parentCommentId — если отвечаем на комментарий (reply_to_comment). 0 = новый комментарий. */
        const val EXTRA_REPLY_COMMENT_ID = "reply_comment_id"
    }

    private val TAG = "NotifActionReceiver"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            ACTION_MARK_READ -> handleMarkRead(context, intent)
            ACTION_REPLY -> handleReply(context, intent)
            ACTION_DISMISS -> handleDismiss(context, intent)
        }
    }

    /**
     * Fix #135b: пользователь свайпнул message notification (или "Clear all").
     *
     * MessageNotifier.showNotification кеширует NotificationData для каждого
     * peerId (activeNotifications) — чтобы при следующем сообщении в этом же
     * диалоге инкрементировать unreadCount и переиспользовать title (без
     * повторного API lookup). Без этого обработчика кеш бы оставался «активным»
     * даже после того как пользователь убрал уведомление → следующее сообщение
     * показало бы «title + (N+1 непрочитанных)» вместо «title + 1 непрочитанное».
     *
     * Здесь НЕ вызываем messages.markAsRead — пользователь не открывал чат,
     * просто смахнул уведомление. Просто очищаем кеш.
     */
    private fun handleDismiss(context: Context, intent: Intent) {
        val peerId = intent.getLongExtra(MessageNotifier.EXTRA_PEER_ID, -1L)
        if (peerId <= 0) {
            AppLog.w(TAG, "DISMISS: peerId missing in intent")
            return
        }
        AppLog.d(TAG, "DISMISS: peerId=$peerId — clearing activeNotifications cache")
        // Нельзя напрямую удалить из activeNotifications (он private в
        // MessageNotifier) — используем существующий метод cancelNotification,
        // который и кеш чистит, и отменяет notification (на случай если
        // dismiss пришёл не от swipe, а от programmatically cancel).
        MessageNotifier.cancelNotification(context, peerId)
    }

    /**
     * §42.2: «Прочитать» — отметить как прочитанное + cancel notification.
     */
    private fun handleMarkRead(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val notifDate = intent.getLongExtra(EXTRA_NOTIF_DATE, 0L)

        AppLog.d(TAG, "MARK_READ: notifId=$notifId date=$notifDate")

        // 1. Отменяем notification сразу (быстрый отклик).
        if (notifId > 0) {
            try {
                NotificationManagerCompat.from(context.applicationContext).cancel(notifId)
            } catch (e: Exception) {
                AppLog.w(TAG, "cancel failed: ${e.message}")
            }
        }

        // 2. Помечаем как прочитанное на сервере (async, не блокируем receiver).
        // notificationsMarkAsRead() — без параметров (отмечает все прочитанными).
        scope.launch {
            try {
                val app = SovaApp.get(context.applicationContext)
                app.apiClient.notificationsMarkAsRead()
                AppLog.d(TAG, "markAsRead OK")
            } catch (e: Exception) {
                AppLog.w(TAG, "markAsRead failed: ${e.message}")
            }
        }
    }

    /**
     * §46 #REMOTE-INPUT: «Ответить» — извлечь текст из RemoteInput, отправить
     * комментарий через VK API, обновить notification с результатом.
     *
     * RemoteInput доставляет текст внутри results-Intent, который система
     * встраивает в наш intent перед доставкой. Извлекаем через
     * [RemoteInput.getResultsFromIntent].
     *
     * После отправки: обновляем notification — убираем прогресс, показываем
     * «Отправлено» или ошибку. Не cancel сразу (пользователь видит подтверждение).
     */
    private fun handleReply(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val targetType = intent.getStringExtra(EXTRA_REPLY_TARGET_TYPE) ?: "wall"
        val ownerId = intent.getLongExtra(EXTRA_REPLY_OWNER_ID, 0L)
        val itemId = intent.getLongExtra(EXTRA_REPLY_ITEM_ID, 0L)
        val commentId = intent.getLongExtra(EXTRA_REPLY_COMMENT_ID, 0L)

        // Извлекаем текст ответа из RemoteInput.
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(EXTRA_REPLY_TEXT)
            ?.toString()
            ?.trim()

        if (replyText.isNullOrBlank()) {
            AppLog.w(TAG, "REPLY: empty text — skip notifId=$notifId")
            return
        }

        AppLog.i(TAG, "REPLY: notifId=$notifId type=$targetType owner=$ownerId item=$itemId comment=$commentId text='${replyText.take(60)}'")

        if (ownerId == 0L || itemId == 0L) {
            AppLog.w(TAG, "REPLY: invalid target (owner=$ownerId item=$itemId) — skip")
            return
        }

        // Отправляем комментарий (async, не блокируем receiver — у него ~10с window).
        scope.launch {
            val app = SovaApp.get(context.applicationContext)
            val api = app.apiClient
            var success = false
            var errorMsg: String? = null

            try {
                success = when (targetType) {
                    "photo" -> {
                        api.photosCreateComment(ownerId, itemId, replyText)
                    }
                    "video" -> {
                        // §48 #VIDEO-BOARD-COMMENT: video.createComment с
                        // reply_to_comment для threaded replies.
                        val commentIdResult = api.videoCreateComment(
                            ownerId = ownerId,
                            videoId = itemId,
                            message = replyText,
                            replyToComment = if (commentId > 0L) commentId else null,
                        )
                        commentIdResult > 0L
                    }
                    "topic" -> {
                        // §48 #VIDEO-BOARD-COMMENT: board.createComment для
                        // обсуждений групп. ownerId отрицательный = группа,
                        // itemId = topic_id. reply_to_comment не поддерживается
                        // board API (нет threaded replies в discussions).
                        api.boardCreateComment(ownerId, itemId, replyText)
                    }
                    else -> {
                        // "wall" — wall.createComment с reply_to_comment если есть.
                        val commentIdResult = api.wallCreateComment(
                            ownerId = ownerId,
                            postId = itemId,
                            message = replyText,
                            replyToComment = if (commentId > 0L) commentId else null,
                        )
                        commentIdResult > 0L
                    }
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "REPLY: send failed", e)
                errorMsg = e.message
            }

            AppLog.i(TAG, "REPLY: result=$success errorMsg=$errorMsg")

            // Обновляем notification: показываем результат вместо спиннера.
            // §46: используем updateReplyResultNotification чтобы показать
            // «Отправлено» / «Ошибка» и через 3с cancel.
            try {
                val ctx = context.applicationContext
                if (success) {
                    ReplyResultNotifier.showSuccess(ctx, notifId, replyText)
                } else {
                    ReplyResultNotifier.showError(ctx, notifId, errorMsg)
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "REPLY: result notification failed: ${e.message}")
            }
        }
    }
}

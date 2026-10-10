package re.pinok.realtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import re.pinok.R
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.local.SovaPrefs
import re.pinok.data.model.Post
import re.pinok.ui.MainActivity
import re.pinok.util.AppLog
import java.util.concurrent.TimeUnit

/**
 * #COMMUNITY-POSTS — периодический опрос newsfeed.get + показ system notifications
 * для НОВЫХ постов из подписанных сообществ.
 *
 * Клиентская логика PinoK (НЕ зависит от настроек VK). В отличие от
 * [NotificationsPoller] (который опрашивает notifications.getRedesign — события
 * «лайк/комментарий/репост»), этот поллер опрашивает newsfeed.get(filters="post")
 * и постит ОТДЕЛЬНОЕ уведомление на каждый новый пост сообщества, участником
 * которого является пользователь.
 *
 * Важные решения:
 *   - Пушим ТОЛЬКО посты сообществ (`fromId < 0`). Посты от пользователей
 *     (`fromId > 0`) не пушим — фича называется «посты сообществ».
 *   - Уведомления идут в ОТДЕЛЬНЫЙ канал `community_posts` с IMPORTANCE_DEFAULT —
 *     НИЖЕ канала `messages` (IMPORTANCE_HIGH) механизма [MessageNotifier],
 *     чтобы НИКОГДА не перекрывать сообщения из диалогов.
 *   - Уважает [SovaPrefs.Snapshot.notifyMode]: показываем посты сообществ только
 *     в режимах ALL и COMMUNITIES_ONLY (в MESSAGES_ONLY и SILENT — не показываем).
 *   - Дедупликация по ключу "ownerId_id" через CSV seenKeys в SovaPrefs
 *     (communityPostsSeenKeys, max [MAX_SEEN_KEYS]).
 *
 * Архитектура (без FCM, через периодический REST poll):
 *
 *   1. start() запускает SupervisorJob + CoroutineScope(Dispatchers.IO) с
 *      периодическим циклом: первый poll через 5с, далее каждые
 *      [POLL_INTERVAL_SEC] секунд.
 *   2. pollOnce() под pollMutex.withLock: snapshot prefs → проверки
 *      (pushEnabled, токен, communityPostPushEnabled, notifyMode) →
 *      newsfeedGet(count=30, filters="post") → фильтр постов сообществ →
 *      diff с seenKeys → showPostNotification для каждого нового → обновление seen.
 *   3. stop() отменяет periodicJob (вызывается при logout).
 *
 * Потокобезопасность: pollMutex гарантирует, что только один pollOnce выполняется
 * одновременно (перезапуск / повторный вызов из таймера не конкурируют).
 */
class CommunityPostsPoller(
    private val context: Context,
    private val api: VKApiClient,
    private val prefs: SovaPrefs,
) {

    companion object {
        const val TAG = "CommunityPostsPoller"
        const val CHANNEL_COMMUNITY_POSTS = "community_posts"
        const val POLL_INTERVAL_SEC = 120
        const val MAX_SEEN_KEYS = 100

        /**
         * #COMMUNITY-POSTS: создаёт notification channel `community_posts`
         * (IMPORTANCE_DEFAULT — тише, чем `messages`/`communities` диалоговых).
         * Вызывается из SovaApp.onCreate. Идемпотентно (если канал уже есть — skip).
         */
        fun initChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nmService = context.getSystemService(Context.NOTIFICATION_SERVICE)
            if (nmService == null) {
                AppLog.w(TAG, "initChannel: NotificationManager is null — skip")
                return
            }
            val nm = nmService as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_COMMUNITY_POSTS) != null) {
                return
            }
            val channel = NotificationChannel(
                CHANNEL_COMMUNITY_POSTS,
                "Новые посты сообществ",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Уведомления о новых постах из подписанных сообществ"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                // #COMMUNITY-POSTS: задаём ОТДЕЛЬНЫЙ тон для канала постов сообществ —
                // конкретизированный дефолтный notification-тон с эксплицитными
                // AudioAttributes(USAGE_NOTIFICATION_EVENT). Вместе с IMPORTANCE_DEFAULT
                // это даёт более тихий/менее навязчивый тон, чем HIGH-канал `messages`
                // (сообщения из диалогов), где тон не переопределён явно. Звук/вибрацию
                // на уровне уведомления дополнительно гасим setSilent/setVibrate в
                // showPostNotification при выключенных тумблерах.
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build(),
                )
            }
            nm.createNotificationChannel(channel)
            AppLog.i(TAG, "Channel '$CHANNEL_COMMUNITY_POSTS' created (IMPORTANCE_DEFAULT)")
        }

        /**
         * #COMMUNITY-POSTS: нормализует int до opaque ARGB для setColor (ожидает
         * байт альфы в старшем разряде; 0x9E9E9E без 0xFF дал бы прозрачный цвет).
         * Если альфа-байт == 0 — подставляем 0xFF.
         */
        private fun opaqueArgb(value: Int): Int {
            return if (((value ushr 24) and 0xFF) == 0) (value and 0xFFFFFF) or 0xFF000000.toInt() else value
        }
    }

    private val supervisorJob = SupervisorJob()
    private val pollScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    private var periodicJob: Job? = null

    private val pollMutex = Mutex()

    private var isRunning = false

    // OkHttpClient для best-effort загрузки аватара сообщества (lazy).
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Запускает периодический опрос. Безопасно вызывать многократно — если уже
     * запущен, no-op. Первый poll — через 5с после старта (не блокируем boot).
     */
    fun start() {
        if (isRunning) {
            AppLog.d(TAG, "start: already running — skip")
            return
        }
        isRunning = true
        periodicJob = pollScope.launch {
            AppLog.i(TAG, "Community posts poller started")
            delay(5_000L)
            while (isActive) {
                try {
                    pollOnce()
                } catch (e: Exception) {
                    AppLog.w(TAG, "periodic poll failed: ${e.message}")
                }
                delay(POLL_INTERVAL_SEC * 1000L)
            }
        }
    }

    /** Останавливает poller. Безопасно вызывать если не запущен. */
    fun stop() {
        if (!isRunning) {
            return
        }
        isRunning = false
        val job = periodicJob
        if (job != null) {
            job.cancel()
        }
        periodicJob = null
        AppLog.i(TAG, "Community posts poller stopped")
    }

    /**
     * Один цикл опроса: snapshot → проверки → fetch → diff → show notifications.
     *
     * Потокобезопасность: pollMutex гарантирует, что только один pollOnce
     * выполняется одновременно.
     */
    private suspend fun pollOnce() {
        pollMutex.withLock {
            try {
                val snap = prefs.data.first()

                // Глобальный master toggle push.
                if (!snap.pushEnabled) return@withLock

                // Валидный токен (как в NotificationsPoller.triggerImmediatePoll).
                if (!SovaApp.get(context).tokenStorage.hasValidToken()) {
                    AppLog.d(TAG, "pollOnce: no valid token — skip")
                    return@withLock
                }

                // Тумблер именно этой фичи.
                if (!snap.communityPostPushEnabled) {
                    AppLog.d(TAG, "pollOnce: communityPostPushEnabled=false — skip")
                    return@withLock
                }

                // Режим уведомлений: посты сообществ показываем только в ALL
                // и COMMUNITIES_ONLY. В MESSAGES_ONLY (0) и SILENT (3) — не показываем.
                val mode = snap.notifyMode
                if (mode != SovaPrefs.NOTIFY_MODE_ALL && mode != SovaPrefs.NOTIFY_MODE_COMMUNITIES_ONLY) {
                    AppLog.d(TAG, "pollOnce: notifyMode=$mode — community posts not shown")
                    return@withLock
                }

                AppLog.d(TAG, "pollOnce: fetching newsfeed.get (filters=post)...")
                val result = api.newsfeedGet(count = 30, filters = "post")
                val posts = result.posts
                if (posts.isEmpty()) {
                    AppLog.d(TAG, "pollOnce: no posts returned")
                    return@withLock
                }

                // Только посты сообществ (fromId < 0), валидные id/ownerId.
                val communityPosts = posts.filter { post ->
                    post.fromId < 0L && post.id > 0L && post.ownerId != 0L
                }
                if (communityPosts.isEmpty()) {
                    AppLog.d(TAG, "pollOnce: ${posts.size} posts, none from communities")
                    return@withLock
                }

                // Diff с seenKeys.
                val seen = parseSeenKeys(snap.communityPostsSeenKeys)
                val newPosts = communityPosts.filter { post ->
                    postKey(post) !in seen
                }

                if (newPosts.isEmpty()) {
                    AppLog.d(TAG, "pollOnce: ${communityPosts.size} community posts, all already seen")
                    updateSeenKeys(snap.communityPostsSeenKeys, communityPosts)
                    return@withLock
                }

                AppLog.i(TAG, "pollOnce: ${communityPosts.size} community posts, ${newPosts.size} NEW — showing notifications")

                // Показываем по одному уведомлению на каждый новый пост.
                for (post in newPosts) {
                    showPostNotification(snap, post, result.groups)
                }

                // Обновляем seen: новые + текущие (distinct, take MAX_SEEN_KEYS).
                val merged = mergeSeenKeys(snap.communityPostsSeenKeys, newPosts)
                prefs.setCommunityPostsSeenKeys(merged)
            } catch (e: Exception) {
                AppLog.w(TAG, "pollOnce failed: ${e.message}")
            }
        }
    }

    /**
     * #COMMUNITY-POSTS: показывает одно системное уведомление о новом посте
     * сообщества в канале `community_posts`.
     *
     * Заголовок = имя сообщества; текст = «Новый пост: <превью>» (обрезано до
     * pushPreviewLength). Аватар сообщества (photo100, fallback photo200) —
     * best-effort: при ошибке загрузки — без аватара.
     */
    private fun showPostNotification(
        snap: SovaPrefs.Snapshot,
        post: Post,
        groups: Map<Long, VKApiClient.GroupInfo>,
    ) {
        try {
            // Гейт Android 13+: permission уведомлений.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    AppLog.w(TAG, "Notifications not permitted — skipping")
                    return
                }
            }

            val key = postKey(post)
            val notifId = 2000 + key.hashCode()

            // Название сообщества: groups[-fromId] → GroupInfo. fromId<0 ⇒ -fromId>0.
            val group = groups[-post.fromId]
            val groupName = if (group != null && group.name.isNotBlank()) group.name else "Новое в сообществе"

            // Текст: «Новый пост: <превью>», либо «Новый пост» если текст пуст.
            val rawText = post.text
            val preview = if (rawText.isBlank()) "" else {
                val maxLen = snap.pushPreviewLength.coerceIn(0, 200)
                if (maxLen == 0) {
                    ""
                } else {
                    val p = rawText.replace("\n", " ").take(maxLen)
                    if (rawText.length > maxLen) "$p…" else p
                }
            }
            val contentText = if (preview.isBlank()) "Новый пост" else "Новый пост: $preview"

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                // #COMMUNITY-POSTS: тап по уведомлению ведёт К ПОСТУ (deep-link),
                // а не просто на MainActivity. MainActivity.handleDeepLinkIntent
                // обрабатывает ACTION_OPEN_POST → pendingDeepLink → SovaNavHost
                // навигирует на PostDetailScreen (ownerId/postId).
                action = re.pinok.realtime.VkUrlDeepLinker.ACTION_OPEN_POST
                putExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_OWNER_ID, post.ownerId)
                putExtra(re.pinok.realtime.VkUrlDeepLinker.EXTRA_ITEM_ID, post.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                key.hashCode() and 0x7FFFFFFF,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_COMMUNITY_POSTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(groupName)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setSortKey("1")
                .setWhen(post.date * 1000L)
                .setShowWhen(true)
                .setAutoCancel(true)
                // #COMMUNITY-POSTS: цвет уведомления поста сообщества — пользовательский
                // (Настройки → Уведомления → Цвет уведомлений сообществ). Default серый
                // 0x9E9E9E. Если 0 (не задан) — фолбэк на серый. setColor ожидает ARGB —
                // нормализуем альфу (0x9E9E9E без 0xFF дал бы прозрачный).
                .setColor(if (snap.communityPostColor != 0) opaqueArgb(snap.communityPostColor) else 0xFF9E9E9E.toInt())
                .setContentIntent(pendingIntent)

            // Аватар сообщества (photo100, fallback photo200) — best-effort.
            if (snap.pushShowAvatar && group != null) {
                val avatarUrl = if (!group.photo100.isNullOrBlank()) group.photo100 else group.photo200
                if (!avatarUrl.isNullOrBlank()) {
                    val bmp = loadBitmap(avatarUrl, targetPx = 192)
                    if (bmp != null) {
                        builder.setLargeIcon(bmp)
                    }
                }
            }

            // #COMMUNITY-POSTS: применяем пользовательские тумблеры звука/вибрации
            // пуша (аналог VkNotificationsNotifier.applyDisplayPrefs — она private).
            // Канал community_posts = IMPORTANCE_DEFAULT + enableVibration(true),
            // поэтому по умолчанию звук и вибрация есть; здесь только уважаем
            // настройки «Звук уведомлений» / «Вибрация уведомлений» (Настройки →
            // Уведомления → Отображение и звук).
            if (snap.pushSoundEnabled == false) {
                builder.setSilent(true)
            }
            if (snap.pushVibrationEnabled == false) {
                builder.setVibrate(longArrayOf(0))
            }

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
            AppLog.d(TAG, "Notification shown: community='$groupName' id=$notifId text='$contentText'")
        } catch (e: Exception) {
            AppLog.e(TAG, "showPostNotification failed", e)
        }
    }

    /**
     * #COMMUNITY-POSTS: возвращает stable-ключ поста для дедупликации.
     */
    private fun postKey(post: Post): String {
        return "${post.ownerId}_${post.id}"
    }

    /**
     * #COMMUNITY-POSTS: парсит CSV seenKeys из prefs в Set<String>.
     * Пустая строка → пустой set.
     */
    private fun parseSeenKeys(csv: String): Set<String> {
        if (csv.isBlank()) return emptySet()
        return csv.split(",").filter { it.isNotBlank() }.toSet()
    }

    /**
     * #COMMUNITY-POSTS: обновляет seenKeys когда новых постов нет — сохраняем
     * актуальный топ communityPosts (на случай, если VK вернул их в др. порядке).
     */
    private suspend fun updateSeenKeys(currentCsv: String, posts: List<Post>) {
        val keys = posts.map { postKey(it) }
        val merged = mergeKeyStrings(currentCsv, keys)
        if (merged != currentCsv) {
            prefs.setCommunityPostsSeenKeys(merged)
        }
    }

    /**
     * #COMMUNITY-POSTS: объединяет текущий CSV с новыми ключами постов —
     * distinct + take(MAX_SEEN_KEYS).
     */
    private fun mergeSeenKeys(currentCsv: String, newPosts: List<Post>): String {
        val newKeys = newPosts.map { postKey(it) }
        return mergeKeyStrings(currentCsv, newKeys)
    }

    /**
     * #COMMUNITY-POSTS: объединяет CSV-строку с ключами — distinct, take(MAX_SEEN_KEYS).
     */
    private fun mergeKeyStrings(currentCsv: String, additionalKeys: List<String>): String {
        val base = parseSeenKeys(currentCsv)
        val merged = (additionalKeys + base.toList()).distinct().take(MAX_SEEN_KEYS)
        return merged.joinToString(",")
    }

    /**
     * #COMMUNITY-POSTS: загружает Bitmap из URL (best-effort, timeout 5с).
     * Вызывается из IO-потока (pollOnce уже на Dispatchers.IO). При ошибке — null.
     *
     * @param url URL картинки
     * @param targetPx целевой max размер по большей стороне
     */
    private fun loadBitmap(url: String, targetPx: Int = 192): Bitmap? {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLog.w(TAG, "loadBitmap: HTTP ${response.code} for $url")
                    return null
                }
                val body = response.body
                if (body == null) {
                    AppLog.w(TAG, "loadBitmap: empty body for $url")
                    return null
                }
                val bytes = body.bytes()
                if (bytes.isEmpty()) {
                    AppLog.w(TAG, "loadBitmap: zero bytes for $url")
                    return null
                }
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                val sampleSize = calculateSampleSize(opts.outWidth, opts.outHeight, targetPx)
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "loadBitmap failed for $url: ${e.message}")
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while ((width / sample) > target || (height / sample) > target) {
            sample *= 2
        }
        return sample
    }
}
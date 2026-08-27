package re.pinok.ui.screens.clips

import re.pinok.api.VKApiClient
import re.pinok.data.model.Video
import re.pinok.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * §37.12 Phase 2: ClipsRepository — тонкая обёртка над VKApiClient для clips.
 *
 * Назначение:
 *  - инкапсулировать выбранную "section" (popular / subscriptions / trends / search)
 *  - держать курсор пагинации (nextFrom) между запросами
 *  - предоставлять единый API для ClipsViewModel
 *
 * Все методы делегируют в VKApiClient, ловят ошибки и возвращают null/empty
 * вместо проброса исключений наверх — ViewModel получает чистые данные.
 *
 * @param api готовый VKApiClient из SovaApp
 */
class ClipsRepository(private val api: VKApiClient) {

    /** Текущая секция ленты clips. */
    enum class Section(val apiValue: String, val display: String) {
        POPULAR("clips", "Популярное"),
        SUBSCRIPTIONS("clips_subscriptions", "Подписки"),
        TRENDS("clips_trends", "Тренды"),
    }
    // §37.12 #324: shortVideo.getRecom использует ref-значения (НЕ section).
    // "clips" / "subscriptions" / "trends" — конвертация Section → ref.
    private fun Section.toRecomRef(): String = when (this) {
        Section.POPULAR -> "clips"
        Section.SUBSCRIPTIONS -> "subscriptions"
        Section.TRENDS -> "trends"
    }

    /** Результат загрузки страницы ленты. */
    data class FeedPage(
        val items: List<Video>,
        val nextFrom: String?,
        val profiles: Map<Long, re.pinok.data.model.UserProfile>,
        val groups: Map<Long, re.pinok.api.VKApiClient.GroupInfo>,
    )

    /**
     * Загрузить первую страницу ленты (сбрасывает курсор).
     *
     * §37.12 #324: использует shortVideo.getRecom (canonical VK web endpoint),
     * который возвращает clips с inline files[] (CDN URLs). Fallback на
     * newsfeed.getFeed(section=clips) если shortVideo.getRecom недоступен
     * (например, метод скрыт для определённых токенов).
     */
    suspend fun loadFirst(
        section: Section,
        count: Int = 10,
    ): FeedPage = withContext(Dispatchers.IO) {
        try {
            // §37.12 #324: пробуем shortVideo.getRecom (canonical path)
            var r = api.shortVideoGetRecom(section = section.toRecomRef(), count = count)
            if (r.items.isEmpty()) {
                AppLog.w("ClipsRepository", "shortVideo.getRecom empty, fallback → newsfeed.getFeed(section=${section.apiValue})")
                r = api.newsfeedGetClipsFeed(section = section.apiValue, count = count)
            }
            // §37.12 #326: диагностика — сколько clips имеют files[] (готовы к воспроизведению)
            val withFiles = r.items.count { it.bestPlayUrl != null }
            AppLog.i("ClipsRepository", "loadFirst(${section.apiValue}): ${r.items.size} clips, " +
                "$withFiles with files[] (bestPlayUrl!=null), ${r.items.size - withFiles} need fetch")
            FeedPage(r.items, r.nextFrom, r.profiles, r.groups)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "loadFirst(${section.apiValue}) error", e)
            FeedPage(emptyList(), null, emptyMap(), emptyMap())
        }
    }

    /**
     * Загрузить следующую страницу (использует курсор nextFrom).
     * Возвращает null если nextFrom == null (больше нет данных).
     *
     * §37.12 #324: shortVideo.getRecom использует page_anchor (НЕ next_from).
     * nextFrom здесь — это page_anchor из предыдущего ответа shortVideo.getRecom,
     * ИЛИ next_from из newsfeed.getFeed fallback. Метод сам разберётся.
     */
    suspend fun loadNext(
        section: Section,
        nextFrom: String,
        count: Int = 10,
    ): FeedPage? = withContext(Dispatchers.IO) {
        try {
            // §37.12 #324: пробуем shortVideo.getRecom с page_anchor
            var r = api.shortVideoGetRecom(
                section = section.toRecomRef(),
                count = count,
                pageAnchor = nextFrom,
            )
            if (r.items.isEmpty()) {
                AppLog.w("ClipsRepository", "shortVideo.getRecom next-page empty, fallback → newsfeed.getFeed(start_from=$nextFrom)")
                r = api.newsfeedGetClipsFeed(
                    section = section.apiValue,
                    count = count,
                    startFrom = nextFrom,
                )
            }
            if (r.items.isEmpty() && r.nextFrom == null) return@withContext null
            FeedPage(r.items, r.nextFrom, r.profiles, r.groups)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "loadNext(${section.apiValue}, from=$nextFrom) error", e)
            null
        }
    }

    /**
     * Загрузить один clip по ownerId+videoId (для deep-link или обновления одного clip).
     *
     * §37.12 #326: shortVideo.get — CANONICAL метод VK web для fetch одного клипа
     * с files[] (CDN URLs). Пробуем ПЕРВЫМ, т.к. video.get НЕ возвращает files[]
     * для clips (подтверждено логами). Fallback на videoGetClipById только если
     * shortVideo.get недоступен или вернул null.
     */
    suspend fun getClip(ownerId: Long, videoId: Long, accessKey: String? = null): Video? =
        withContext(Dispatchers.IO) {
            try {
                // §37.12 #326: shortVideo.get возвращает files[] с прямыми CDN URLs.
                val clip = api.shortVideoGet(ownerId, videoId)
                if (clip != null && clip.bestPlayUrl != null) {
                    AppLog.i("ClipsRepository", "getClip: shortVideo.get ok for ${ownerId}_$videoId, files=${clip.files?.size ?: 0}")
                    return@withContext clip
                }
                // Fallback: video.get (может вернуть files[] для обычных видео, но не для clips)
                AppLog.w("ClipsRepository", "getClip: shortVideo.get returned null/no-files for ${ownerId}_$videoId, fallback → video.get")
                api.videoGetClipById(ownerId, videoId, accessKey)
            } catch (e: Exception) {
                AppLog.e("ClipsRepository", "getClip($ownerId, $videoId) error", e)
                null
            }
        }

    /**
     * Поиск clips по строке/хештегу.
     */
    suspend fun search(query: String, offset: Int = 0, count: Int = 20): FeedPage =
        withContext(Dispatchers.IO) {
            try {
                val r = api.searchClips(query, count = count, offset = offset)
                FeedPage(r.items, r.nextFrom, r.profiles, r.groups)
            } catch (e: Exception) {
                AppLog.e("ClipsRepository", "search('$query') error", e)
                FeedPage(emptyList(), null, emptyMap(), emptyMap())
            }
        }

    // ── Clip interactions (делегируют в apiClient) ────────────────────────

    /** Лайкнуть clip. type="video".
     *  §37.12 #322: accessKey для приватных клипов.
     *  §37.12 #325: trackCode — VK web передаёт track_code+ref в likes.add для clips. */
    suspend fun like(ownerId: Long, videoId: Long, accessKey: String? = null, trackCode: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val r = api.likesAdd("video", ownerId, videoId, accessKey = accessKey, trackCode = trackCode)
            r >= 0
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "like($ownerId, $videoId) error", e)
            false
        }
    }

    /** Снять лайк. §37.12 #322: accessKey. §37.12 #325: trackCode. */
    suspend fun unlike(ownerId: Long, videoId: Long, accessKey: String? = null, trackCode: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val r = api.likesDelete("video", ownerId, videoId, accessKey = accessKey, trackCode = trackCode)
            r >= 0
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "unlike($ownerId, $videoId) error", e)
            false
        }
    }

    /**
     * Подписаться на автора clip.
     * Если ownerId < 0 → это group, вызываем groups.join({group_id: -ownerId}).
     * Если ownerId > 0 → user, для user-clips VK API не имеет прямого
     * "subscribe" — используется users.subscribe / followUser через execute
     * (в данной фазе возвращаем false, UI показывает "недоступно для пользователей").
     */
    suspend fun subscribeAuthor(ownerId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            if (ownerId > 0) {
                // §37.9: user-clip — подписка через friends.add (VK сам делает публичную подписку).
                api.usersSubscribe(ownerId)
            } else {
                val groupId = -ownerId
                api.groupsJoin(groupId)
            }
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "subscribeAuthor($ownerId) error", e)
            false
        }
    }

    /** Отписаться от автора (group или user-clip). */
    suspend fun unsubscribeAuthor(ownerId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            if (ownerId > 0) {
                api.usersUnsubscribe(ownerId)
            } else {
                val groupId = -ownerId
                api.groupsLeave(groupId)
            }
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "unsubscribeAuthor($ownerId) error", e)
            false
        }
    }

    /** §37.9: toggle уведомлений о новых клипах автора (wall.subscribe/unsubscribe). */
    suspend fun toggleNotifications(ownerId: Long, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (enable) api.wallSubscribe(ownerId) else api.wallUnsubscribe(ownerId)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "toggleNotifications($ownerId, $enable) error", e)
            false
        }
    }

    /** §37.9: скрыть автора из рекомендаций (newsfeed.ban). */
    suspend fun hideAuthor(ownerId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            api.newsfeedBanUser(ownerId)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "hideAuthor($ownerId) error", e)
            false
        }
    }

    /** §37.9: добавить clip-автора в закладки. */
    suspend fun favoriteAuthor(ownerId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            if (ownerId > 0) api.faveAddPage(userId = ownerId)
            else api.faveAddPage(groupId = -ownerId)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "favoriteAuthor($ownerId) error", e)
            false
        }
    }

    /** §37.9: убрать clip-автора из закладок. */
    suspend fun unfavoriteAuthor(ownerId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            if (ownerId > 0) api.faveRemovePage(userId = ownerId)
            else api.faveRemovePage(groupId = -ownerId)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "unfavoriteAuthor($ownerId) error", e)
            false
        }
    }

    /** §37.9: пожаловаться на клип (execute video.report). */
    suspend fun reportClip(ownerId: Long, videoId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            api.reportVideo(ownerId, videoId)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "reportClip($ownerId, $videoId) error", e)
            false
        }
    }

    /** §37.9: удалить свой клип (video.delete). */
    suspend fun deleteClip(ownerId: Long, videoId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            api.videoDeleteClip(videoId, ownerId)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "deleteClip($ownerId, $videoId) error", e)
            false
        }
    }

    /** §37.9: редактировать свой клип (video.edit). */
    suspend fun editClip(ownerId: Long, videoId: Long, name: String?, description: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            api.videoEdit(videoId, ownerId, name = name, description = description)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "editClip($ownerId, $videoId) error", e)
            false
        }
    }

    /**
     * Записать просмотр в историю (для рекомендаций).
     *
     * §37.12 #322: video.addViewingHistoryRecord — BFF-only метод VK web,
     * через прямой vk1.a.* токен стабильно возвращает error 100. Поэтому:
     *  - НЕ логируем ошибку как E (чтобы не засорять logcat каждый swipe),
     *    только D-уровень для отладки.
     *  - Возвращаем false тихо — UI это не волнует (просмотр трекается
     *    best-effort, не блокирует ничего).
     */
    suspend fun trackView(ownerId: Long, videoId: Long, durationWatched: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                api.videoAddViewingHistoryRecord(ownerId, videoId, durationWatched)
            } catch (e: Exception) {
                AppLog.d("ClipsRepository", "trackView (BFF-only, expected to fail on direct token): ${e.message}")
                false
            }
        }

    /** Заблокировать автора. */
    suspend fun banAuthor(ownerId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            api.accountBan(ownerId)
        } catch (e: Exception) {
            AppLog.e("ClipsRepository", "banAuthor($ownerId) error", e)
            false
        }
    }
}

package re.pinok.ui.screens.clips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import re.pinok.api.VKApiClient
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.util.AppLog

/**
 * §37.12 Phase 2: ClipsViewModel — состояние clips-экрана.
 *
 * Хранит:
 *  - текущую ленту clips (List<Video>)
 *  - курсор пагинации nextFrom
 *  - profiles/groups для рендера авторов
 *  - currentIndex (какой clip сейчас показывает VerticalPager)
 *  - loading/error флаги
 *  - текущую секцию (POPULAR/SUBSCRIPTIONS/TRENDS)
 *
 * Intent-методы (вызывает UI):
 *  - loadFirst(section) — первая загрузка / смена секции
 *  - loadNext() — подгрузить следующую страницу (вызывается при приближении к концу)
 *  - toggleLike(clip) — лайк/анлайн с optimistic update
 *  - toggleSubscribe(clip) — подписка на автора
 *  - setCurrentIndex(i) — обновить индекс (для трекинга просмотров)
 *  - refresh() — полный reload
 */
class ClipsViewModel(
    private val repo: ClipsRepository,
) : ViewModel() {

    /** UI-состояние clips-экрана. */
    data class UiState(
        val section: ClipsRepository.Section = ClipsRepository.Section.POPULAR,
        val clips: List<Video> = emptyList(),
        val profiles: Map<Long, UserProfile> = emptyMap(),
        val groups: Map<Long, VKApiClient.GroupInfo> = emptyMap(),
        val nextFrom: String? = null,
        val currentIndex: Int = 0,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        /** Идентификаторы clip'ов в процессе лайка — для показа loading на сердечке. */
        val likingClipIds: Set<Long> = emptySet(),
        /** Идентификаторы clip'ов в процессе подписки. */
        val subscribingClipIds: Set<Long> = emptySet(),
        val endReached: Boolean = false,
    ) {
        /** Текущий clip (для overlay). */
        val currentClip: Video? get() = clips.getOrNull(currentIndex)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Загруженные ID'ы clip'ов (для дедупликации при пагинации). */
    private val loadedIds: MutableSet<Long> = mutableSetOf()

    fun loadFirst(section: ClipsRepository.Section = _state.value.section) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, section = section) }
            loadedIds.clear()
            val page = repo.loadFirst(section)
            page.items.forEach { loadedIds.add(it.id) }
            _state.update {
                it.copy(
                    loading = false,
                    clips = page.items,
                    profiles = page.profiles,
                    groups = page.groups,
                    nextFrom = page.nextFrom,
                    currentIndex = 0,
                    endReached = page.items.isEmpty() || page.nextFrom == null,
                )
            }
        }
    }

    fun loadNext() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        val nf = s.nextFrom ?: return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val page = repo.loadNext(s.section, nf) ?: run {
                _state.update { it.copy(loadingMore = false, endReached = true) }
                return@launch
            }
            // Дедупликация: добавляем только новые clip'ы.
            val newClips = page.items.filter { it.id !in loadedIds }
            newClips.forEach { loadedIds.add(it.id) }
            _state.update {
                it.copy(
                    loadingMore = false,
                    clips = it.clips + newClips,
                    profiles = it.profiles + page.profiles,
                    groups = it.groups + page.groups,
                    nextFrom = page.nextFrom,
                    endReached = newClips.isEmpty() || page.nextFrom == null,
                )
            }
        }
    }

    fun setCurrentIndex(i: Int) {
        val s = _state.value
        if (i == s.currentIndex || i !in s.clips.indices) return
        _state.update { it.copy(currentIndex = i) }
        // Авто-подгрузка при приближении к концу (за 3 clip'а до конца).
        if (i >= s.clips.size - 3) loadNext()
        // Трекинг просмотра для предыдущего clip'а (асинхронно, без блокировки UI).
        val prevClip = s.clips.getOrNull(s.currentIndex)
        if (prevClip != null) {
            viewModelScope.launch {
                repo.trackView(prevClip.ownerId, prevClip.id, prevClip.duration)
            }
        }
    }

    /**
     * §37.12 Phase 3.1 (#322): lazy-fetch полных данных клипа через video.get.
     *
     * newsfeed.getFeed(section=clips) НЕ возвращает files[] (CDN-URL'ы для
     * ExoPlayer) и часто не возвращает access_key (нужен для likes.add /
     * wall.post на приватных клипах). Этот метод вызывается из ClipPlayerItem
     * через LaunchedEffect, когда clip.bestPlayUrl == null OR clip.accessKey == null.
     *
     * После успешного fetch — обновляем clip в state (optimistic merge: новые
     * files[] + access_key + likes/reposts/comments счётчики).
     *
     * @return true если clip был обновлён (для логгирования/UI).
     */
    fun fetchClipDetails(clip: Video) {
        // §37.12 #324: fetch только когда bestPlayUrl==null (нет CDN URL для воспроизведения).
        // accessKey НЕ требуется для публичных clips (VK web передаёт bare videoId в likes.add).
        if (clip.id <= 0 || clip.ownerId == 0L) return
        if (clip.bestPlayUrl != null) return
        // Защита от повторных fetch'ей: проверим, что clip ещё в state и не обновлён.
        val current = _state.value.clips.firstOrNull { it.id == clip.id && it.ownerId == clip.ownerId } ?: return
        if (current.bestPlayUrl != null) return
        AppLog.i("ClipsViewModel", "fetchClipDetails: start for ${clip.ownerId}_${clip.id} " +
            "(files=${clip.files?.size ?: 0}, trackCode=${if (clip.trackCode != null) "yes" else "no"})")
        viewModelScope.launch {
            val fresh = repo.getClip(clip.ownerId, clip.id, clip.accessKey) ?: run {
                AppLog.w("ClipsViewModel", "fetchClipDetails: repo.getClip returned null for ${clip.ownerId}_${clip.id}")
                return@launch
            }
            if (fresh.bestPlayUrl == null) {
                AppLog.w("ClipsViewModel", "fetchClipDetails: fresh.bestPlayUrl still null for ${clip.ownerId}_${clip.id} " +
                    "(files=${fresh.files?.size ?: 0}, player=${if (fresh.player != null) "yes" else "no"})")
            }
            // Merge: сохраняем optimistic-поля (isSubscribed/isFavorite/userLikes),
            // т.к. user мог нажать like/subscribe между feed-load и этим fetch.
            val merged = fresh.copy(
                isSubscribed = clip.isSubscribed ?: fresh.isSubscribed,
                isFavorite = clip.isFavorite ?: fresh.isFavorite,
                likes = clip.likes?.let { cur ->
                    // #ARCH-CONTAINERS 3.7-1: likes в :core:data — захват + явная проверка
                    // (внутри аргументов copy безопасный вызов receiver не смарт-кастит).
                    val freshLikes = fresh.likes
                    if (freshLikes != null) {
                        freshLikes.copy(
                            count = cur.count.coerceAtLeast(freshLikes.count),
                            userLikes = cur.userLikes,
                        )
                    } else cur
                } ?: fresh.likes,
            )
            _state.update { st ->
                st.copy(
                    clips = st.clips.map { c ->
                        if (c.id == clip.id && c.ownerId == clip.ownerId) merged else c
                    }
                )
            }
            AppLog.d("ClipsViewModel", "fetchClipDetails ok: ${clip.ownerId}_${clip.id} " +
                "files=${merged.files?.size ?: 0} accessKey=${if (merged.accessKey != null) "yes" else "no"} " +
                "url=${if (merged.bestPlayUrl != null) "yes" else "no"}")
        }
    }

    fun toggleLike(clip: Video) {
        val s = _state.value
        if (clip.id in s.likingClipIds) return
        val isCurrentlyLiked = clip.isLiked
        // Optimistic update: обновляем clip в списке сразу.
        _state.update { st ->
            st.copy(
                clips = st.clips.map { c ->
                    if (c.id == clip.id && c.ownerId == clip.ownerId) {
                        val newLikes = (c.likes ?: re.pinok.data.model.Post.Likes(count = 0, userLikes = 0)).copy(
                            count = (c.likesCount + if (isCurrentlyLiked) -1 else 1).coerceAtLeast(0),
                            userLikes = if (isCurrentlyLiked) 0 else 1,
                        )
                        c.copy(likes = newLikes)
                    } else c
                },
                likingClipIds = st.likingClipIds + clip.id,
            )
        }
        viewModelScope.launch {
            // §37.12 #322: передаём accessKey — без него VK возвращает error 100
            // "object not found" для приватных клипов из newsfeed.getFeed(section=clips).
            // §37.12 #325: передаём trackCode — VK web передаёт track_code+ref в
            // likes.add для clips (нужно для валидации clips-объектов).
            val ok = if (isCurrentlyLiked) repo.unlike(clip.ownerId, clip.id, clip.accessKey, clip.trackCode)
                     else repo.like(clip.ownerId, clip.id, clip.accessKey, clip.trackCode)
            if (!ok) {
                // Revert: возвращаем как было.
                _state.update { st ->
                    st.copy(
                        clips = st.clips.map { c ->
                            if (c.id == clip.id && c.ownerId == clip.ownerId) {
                                val newLikes = (c.likes ?: re.pinok.data.model.Post.Likes(count = 0, userLikes = 0)).copy(
                                    count = (c.likesCount + if (isCurrentlyLiked) 1 else -1).coerceAtLeast(0),
                                    userLikes = if (isCurrentlyLiked) 1 else 0,
                                )
                                c.copy(likes = newLikes)
                            } else c
                        },
                    )
                }
                AppLog.w("ClipsViewModel", "toggleLike revert (ok=false) for clip ${clip.ownerId}_${clip.id}" +
                    " accessKey=${if (clip.accessKey != null) "yes" else "null"} trackCode=${if (clip.trackCode != null) "yes" else "null"}")
            }
            _state.update { it.copy(likingClipIds = it.likingClipIds - clip.id) }
        }
    }

    fun toggleSubscribe(clip: Video, onResult: ((Boolean) -> Unit)? = null) {
        val s = _state.value
        if (clip.id in s.subscribingClipIds) {
            onResult?.invoke(false)
            return
        }
        val isSubscribed = clip.isSubscribedToAuthor
        _state.update { st ->
            st.copy(
                clips = st.clips.map { c ->
                    if (c.ownerId == clip.ownerId) {
                        c.copy(isSubscribed = if (isSubscribed) 0 else 1)
                    } else c
                },
                subscribingClipIds = st.subscribingClipIds + clip.id,
            )
        }
        viewModelScope.launch {
            val ok = if (isSubscribed) repo.unsubscribeAuthor(clip.ownerId)
                     else repo.subscribeAuthor(clip.ownerId)
            if (!ok) {
                _state.update { st ->
                    st.copy(
                        clips = st.clips.map { c ->
                            if (c.ownerId == clip.ownerId) {
                                c.copy(isSubscribed = if (isSubscribed) 1 else 0)
                            } else c
                        },
                    )
                }
                AppLog.w("ClipsViewModel", "toggleSubscribe revert (ok=false) for ownerId=${clip.ownerId}")
            }
            _state.update { it.copy(subscribingClipIds = it.subscribingClipIds - clip.id) }
            onResult?.invoke(ok)
        }
    }

    fun refresh() {
        loadFirst(_state.value.section)
    }

    // ── §37.9: доп. операции контекстного меню ────────────────────────────

    /** §37.9: toggle favorite clip-автора (закладки). Optimistic, с revert. */
    fun toggleFavorite(clip: Video, onResult: ((Boolean) -> Unit)? = null) {
        val isFav = clip.isFavorited
        _state.update { st ->
            st.copy(
                clips = st.clips.map { c ->
                    if (c.ownerId == clip.ownerId) c.copy(isFavorite = if (isFav) 0 else 1) else c
                },
            )
        }
        viewModelScope.launch {
            val ok = if (isFav) repo.unfavoriteAuthor(clip.ownerId)
                     else repo.favoriteAuthor(clip.ownerId)
            if (!ok) {
                _state.update { st ->
                    st.copy(
                        clips = st.clips.map { c ->
                            if (c.ownerId == clip.ownerId) c.copy(isFavorite = if (isFav) 1 else 0) else c
                        },
                    )
                }
                AppLog.w("ClipsViewModel", "toggleFavorite revert (ok=false) for ownerId=${clip.ownerId}")
            }
            onResult?.invoke(ok)
        }
    }

    /** §37.9: toggle уведомлений о новых клипах автора. */
    fun toggleNotifications(clip: Video) {
        val isSubscribed = clip.isSubscribedToAuthor
        viewModelScope.launch {
            val ok = repo.toggleNotifications(clip.ownerId, enable = !isSubscribed)
            AppLog.d("ClipsViewModel", "toggleNotifications ok=$ok for ownerId=${clip.ownerId}")
        }
    }

    /** §37.9: скрыть автора из рекомендаций + убрать его clip'ы из ленты. */
    fun hideAuthor(clip: Video) {
        viewModelScope.launch {
            val ok = repo.hideAuthor(clip.ownerId)
            if (ok) {
                _state.update { st ->
                    st.copy(clips = st.clips.filter { it.ownerId != clip.ownerId })
                }
            }
        }
    }

    /** §37.9: пожаловаться на клип. */
    fun reportClip(clip: Video) {
        viewModelScope.launch {
            val ok = repo.reportClip(clip.ownerId, clip.id)
            AppLog.d("ClipsViewModel", "reportClip ok=$ok for ${clip.ownerId}_${clip.id}")
        }
    }

    /** §37.9: удалить свой клип + убрать из ленты. */
    fun deleteClip(clip: Video) {
        viewModelScope.launch {
            val ok = repo.deleteClip(clip.ownerId, clip.id)
            if (ok) {
                _state.update { st ->
                    st.copy(clips = st.clips.filter { it.id != clip.id || it.ownerId != clip.ownerId })
                }
            }
        }
    }

    /** §37.9: редактировать свой клип (name/desc) — без reload, VK сам обновит при следующем get. */
    fun editClip(clip: Video, name: String?, description: String?) {
        viewModelScope.launch {
            val ok = repo.editClip(clip.ownerId, clip.id, name, description)
            if (ok) {
                _state.update { st ->
                    st.copy(
                        clips = st.clips.map { c ->
                            if (c.id == clip.id && c.ownerId == clip.ownerId) {
                                c.copy(
                                    title = name ?: c.title,
                                    description = description ?: c.description,
                                )
                            } else c
                        },
                    )
                }
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            loadFirst(_state.value.section)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            loadedIds.clear()
            val page = repo.search(query)
            page.items.forEach { loadedIds.add(it.id) }
            _state.update {
                it.copy(
                    loading = false,
                    clips = page.items,
                    profiles = page.profiles,
                    groups = page.groups,
                    nextFrom = page.nextFrom,
                    currentIndex = 0,
                    endReached = page.items.isEmpty() || page.nextFrom == null,
                )
            }
        }
    }
}

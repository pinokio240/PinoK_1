package re.pinok.ui.components

import androidx.compose.runtime.Composable
import re.pinok.data.model.Attachment
import re.pinok.data.model.Post
import re.pinok.data.model.Video
import re.pinok.ui.screens.feed.DocAttachmentCard
import re.pinok.ui.screens.feed.LinkCard
import re.pinok.ui.screens.feed.PollCard

/**
 * POSTCARD-REFACTOR-PLAN (этап 2): ЕДИНЫЙ рендер вложений поста для всех
 * поверхностей — лента (FeedScreen), сообщество (CommunityScreen), профиль
 * (ProfileScreen), открытый пост (PostDetailScreen), закладки (BookmarksScreen).
 *
 * Консолидирует 5 дублирующих реализаций рендера вложений. Порядок рендера
 * фиксирован (как VK web группирует вложения поста):
 *
 *  1. Фото  — одним блоком [PostPhotoGrid] (карусель при включённой настройке).
 *  2. Видео — [PostVideoCarousel] (если carouselEnabled и >1) ИЛИ стопкой, где
 *     клип (isClip) при [clipThumbnailEnabled] идёт через [ClipThumbnail],
 *     обычное видео — через [VideoThumbnail].
 *  3. Аудио — [AudioAttachmentList].
 *  4. Плейлисты — [PlaylistAttachmentCard] (type="audio_playlist").
 *  5. Ссылки — [LinkCard] (type="link"/"page") — только при [renderLinkPollDoc].
 *  6. Опрос — [PollCard] — только при [renderLinkPollDoc].
 *  7. Документ — [DocAttachmentCard] (type="doc") — только при [renderLinkPollDoc].
 *
 * Поведение каждого экрана сохраняется через флаги конфигурации (все с
 * дефолтами, чтобы вызовы старых мест не ломались):
 *
 *  - [showVideoTitle]      — false для Profile/Bookmarks (не показывать блок
 *    «название + счётчики» под превью видео; было showTitle=false).
 *  - [titleExpandable]     — true для Community (разворачиваемое название
 *    видео, #POST-TEXT-EXPAND).
 *  - [renderLinkPollDoc]   — false для Community/Bookmarks (не рендерить
 *    link/page/poll/doc вложения).
 *  - [consumeClicks]       — true для Bookmarks: клик по фото/видео-вложению
 *    ведёт к [onPostClick] (открыть пост), а не к просмотрщику/плееру.
 *  - [clipThumbnailEnabled]— true для Community: клипы (isClip) показывать
 *    вертикальной карточкой [ClipThumbnail] (как в группе).
 *
 * Использует общие ui/components-компоненты (тот же пакет — импорт не нужен) и
 * internal-карточки [LinkCard], [PollCard], [DocAttachmentCard] из
 * re.pinok.ui.screens.feed (паттерн ProfileScreen).
 *
 * NULL-ПОЛИТИКА: новые вложения опциональны по схеме Gson — проверки только
 * «if (x != null)» (БЕЗ ?./?:/!!); поля модели захватываются в локальные val
 * до проверки (#ARCH-CONTAINERS 3.7-1, смарт-каст чужого модуля).
 */
@Composable
fun PostAttachmentRenderer(
    attachments: List<Attachment>?,
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    onVideoClick: (Video) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    carouselEnabled: Boolean = true,
    // флаги конфигурации (для совместимости с разными экранами)
    showVideoTitle: Boolean = true,   // false для Profile/Bookmarks (было showTitle=false)
    titleExpandable: Boolean = false, // true для Community
    renderLinkPollDoc: Boolean = true, // false для Community/Bookmarks (не рендерить link/poll/doc)
    consumeClicks: Boolean = false,   // true для Bookmarks (клик по вложению ведёт к onPostClick)
    clipThumbnailEnabled: Boolean = false, // true для Community — показывать клипы как ClipThumbnail
    // для consumeClicks: пост, который открывает onPostClick (null-безопасно — глушится).
    post: Post? = null,
) {
    val list = attachments
    if (list == null || list.isEmpty()) return

    // 1. Фото: один блок [PostPhotoGrid] со всеми фото поста.
    val photos = list.filter { it.type == "photo" && it.photo != null }.mapNotNull { it.photo }
    if (photos.isNotEmpty()) {
        PostPhotoGrid(
            photos = photos,
            onPhotoClick = if (consumeClicks && post != null) { _, _ -> onPostClick(post) } else onPhotoClick,
            carouselEnabled = carouselEnabled,
        )
    }

    // 2. Видео: карусель (если включена и >1) или стопка клип/видео.
    val videos = list.filter { it.type == "video" && it.video != null }.mapNotNull { it.video }
    if (videos.isNotEmpty()) {
        val onVideo: (Video) -> Unit =
            if (consumeClicks && post != null) { _ -> onPostClick(post) } else onVideoClick
        if (carouselEnabled && videos.size > 1) {
            PostVideoCarousel(
                videos = videos,
                carouselEnabled = carouselEnabled,
                onVideoClick = onVideo,
            )
        } else {
            videos.forEach { v ->
                if (v.isClip && clipThumbnailEnabled) {
                    ClipThumbnail(video = v, onClick = onVideo)
                } else {
                    VideoThumbnail(
                        video = v,
                        onClick = onVideo,
                        showTitle = showVideoTitle,
                        titleExpandable = titleExpandable,
                    )
                }
            }
        }
    }

    // 3. Аудио-вложения.
    val audioTracks = list.filter { it.type == "audio" && it.audio != null }.mapNotNull { it.audio }
    if (audioTracks.isNotEmpty()) {
        AudioAttachmentList(tracks = audioTracks)
    }

    // 4. Плейлисты (type="audio_playlist").
    list.forEach { att ->
        // #ARCH-CONTAINERS 3.7-1: audioPlaylist в :core:data — захват в val.
        val playlist = att.audioPlaylist
        if (att.type == "audio_playlist" && playlist != null) {
            PlaylistAttachmentCard(playlist = playlist)
        }
    }

    if (renderLinkPollDoc) {
        // 5. Ссылки (link/page) — как alias-ссылки (VK wiki).
        list.forEach { att ->
            val link = att.link
            if ((att.type == "link" || att.type == "page") && link != null) {
                // FIX-LINK-CLICK: передаём реальный onClick — открывает URL
                // во внешнем браузере. Раньше был onClick = {} → ссылки не открывались.
                val ctx = androidx.compose.ui.platform.LocalContext.current
                LinkCard(link = link, onClick = {
                    re.pinok.util.openUrlExternal(ctx, link.url)
                })
            }
        }
        // 6. Опрос (первый из вложений).
        val pollAtt = list.firstOrNull { it.type == "poll" && it.poll != null }
        if (pollAtt != null) {
            val poll = pollAtt.poll
            if (poll != null) {
                PollCard(poll = poll, onVote = {})
            }
        }
        // 7. Документ-вложения.
        list.forEach { att ->
            val doc = att.doc
            if (att.type == "doc" && doc != null) {
                DocAttachmentCard(doc = doc, onOpen = {})
            }
        }
    }
}
package re.pinok.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation routes for SOVA 2.0.
 *
 * Dock (bottom nav) — 5 tabs, mirrors VK layout:
 *   Feed, Messages, Music, Video, Profile
 *
 * Drawer — extra destinations accessible from the app drawer:
 *   Services (SuperApp), Notifications, Settings, About
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector?) {
    // Dock
    object Feed          : Screen("feed",          "Лента",        Icons.Default.Dashboard)
    object Messages      : Screen("messages",      "Сообщения",    Icons.Default.Email)
    object Music         : Screen("music",         "Музыка",       Icons.Default.LibraryMusic)
    object Video         : Screen("video",         "Видео",        Icons.Default.PlayCircle)
    object Profile       : Screen("profile",       "Профиль",      Icons.Default.Person)

    /**
     * Полноэкранный видеоплеер. Принимает ownerId/id как path-параметры:
     * например, "video_player/123/456".
     */
    object VideoPlayer : Screen("video_player/{ownerId}/{videoId}", "Видео", null) {
        const val ARG_OWNER_ID = "ownerId"
        const val ARG_VIDEO_ID = "videoId"
        fun buildRoute(ownerId: Long, videoId: Long): String = "video_player/$ownerId/$videoId"
    }

    /**
     * Fix #62: Полноэкранный аудиоплеер. Без параметров — читает состояние
     * из PlayerConnection (currentTrack, queue, position). Открывается тапом
     * по мини-плееру внизу MusicScreen.
     */
    object AudioPlayer : Screen("audio_player", "Плеер", null)

    /**
     * Fix #62: Экран очереди воспроизведения (список «Далее»). Без параметров —
     * читает очередь из PlayerConnection. Кнопка «Сохранить как плейлист» (TODO).
     */
    object AudioQueue : Screen("audio_queue", "Очередь", null)

    /**
     * #MUSIC-PORT: экран «Плейлисты» — список плейлистов текущего пользователя
     * (audio.getPlaylists). Заменяет прежний AlertDialog из меню «Моя музыка».
     */
    object MusicPlaylists : Screen("music_playlists", "Плейлисты", null)

    /**
     * #MUSIC-PORT: экран деталей плейлиста — обложка + название + описание +
     * треки + «Играть все» + «Скачать плейлист». Принимает ownerId/playlistId/accessKey.
     */
    object PlaylistDetail : Screen("playlist_detail/{ownerId}/{playlistId}?accessKey={accessKey}", "Плейлист", null) {
        const val ARG_OWNER_ID = "ownerId"
        const val ARG_PLAYLIST_ID = "playlistId"
        const val ARG_ACCESS_KEY = "accessKey"
        fun buildRoute(ownerId: Long, playlistId: Long, accessKey: String?): String {
            val ak = accessKey?.let { android.net.Uri.encode(it) } ?: ""
            return "playlist_detail/$ownerId/$playlistId?accessKey=$ak"
        }
    }

    /**
     * #MUSIC-PORT: экран «Альбомы» — альбомы из каталога (catalog.getAudio).
     */
    object MusicAlbums : Screen("music_albums", "Альбомы", null)

    /**
     * #MUSIC-PORT: экран «Артисты и кураторы» — список артистов.
     */
    object MusicArtists : Screen("music_artists", "Артисты", null)

    /**
     * #MUSIC-PORT: экран артиста — треки + related artists.
     */
    object ArtistDetail : Screen("artist_detail/{slug}?name={name}", "Артист", null) {
        const val ARG_SLUG = "slug"
        const val ARG_NAME = "name"
        // #MUSIC-PORT: slug — идентификатор артиста; name — имя для поиска треков.
        fun buildRoute(slug: String, name: String): String =
            "artist_detail/${android.net.Uri.encode(slug)}?name=${android.net.Uri.encode(name)}"
    }

    /**
     * #MUSIC-CATALOG-SHOW-ALL: экран «Показать все» для блока каталога.
     * Грузит catalog.getSection(sectionId) и показывает полный список
     * (все треки/плейлисты блока). sectionId приходит из actions header-блока.
     */
    object CatalogSection : Screen("catalog_section/{sectionId}?title={title}", "Показать все", null) {
        const val ARG_SECTION_ID = "sectionId"
        const val ARG_TITLE = "title"
        fun buildRoute(sectionId: String, title: String): String =
            "catalog_section/${android.net.Uri.encode(sectionId)}?title=${android.net.Uri.encode(title)}"
    }

    /**
     * Fix #67: Экран сообщества — стена + инфо. Принимает groupId как path-параметр.
     * Положительный ID (как в groups[].id). В wallGet передаётся как -groupId.
     */
    object Community : Screen("community/{groupId}", "Сообщество", null) {
        const val ARG_GROUP_ID = "groupId"
        fun buildRoute(groupId: Long): String = "community/$groupId"
    }

    /**
     * Шаг 4 (#32d): Экран темы обсуждения сообщества.
     * Принимает groupId/topicId как path-параметры, title — через query
     * (для TopAppBar). Загружает board.getComments с пагинацией.
     */
    object BoardTopic : Screen("board_topic/{groupId}/{topicId}?title={title}", "Обсуждение", null) {
        const val ARG_GROUP_ID = "groupId"
        const val ARG_TOPIC_ID = "topicId"
        const val ARG_TITLE = "title"
        fun buildRoute(groupId: Long, topicId: Long, title: String): String =
            "board_topic/$groupId/$topicId?title=${android.net.Uri.encode(title)}"
    }

    /**
     * Sprint 1, P0-2 (#74): Экран чужого профиля — стена + инфо + действия.
     * Принимает userId как path-параметр (положительный, как в users[].id).
     * Открывается тапом по автору поста (fromId > 0), другу, собеседнику в чате.
     */
    object UserProfile : Screen("user_profile/{userId}", "Профиль", null) {
        const val ARG_USER_ID = "userId"
        fun buildRoute(userId: Long): String = "user_profile/$userId"
    }

    /**
     * Fix #71: Экран детального просмотра поста.
     * Принимает ownerId/postId как path-параметры. Сам объект Post передаётся
     * через in-memory holder [re.pinok.ui.navigation.PostHolder].
     */
    object PostDetail : Screen("post_detail/{ownerId}/{postId}", "Пост", null) {
        const val ARG_OWNER_ID = "ownerId"
        const val ARG_POST_ID = "postId"
        fun buildRoute(ownerId: Long, postId: Long): String = "post_detail/$ownerId/$postId"
    }

    /**
     * Экран диалога — история сообщений + отправка (#43).
     * Принимает peerId как path-параметр, title/photo — через query-параметры
     * (URL-encoded). Например: "chat_detail/123?title=Иван&photo=https://…"
     */
    object ChatDetail : Screen("chat_detail/{peerId}?title={title}&photo={photo}", "Диалог", null) {
        const val ARG_PEER_ID = "peerId"
        const val ARG_TITLE = "title"
        const val ARG_PHOTO = "photo"
        // #CALLS-NAME-FIX (2026-08-29): java.net.URLEncoder делает FORM-кодирование —
        // пробел → «+», а Navigation декодирует только %XX (Uri.decode): заголовки
        // диалогов отображались как «Имя+Фамилия». android.net.Uri.encode даёт %20,
        // который Navigation корректно разворачивает обратно в пробел.
        fun buildRoute(peerId: Long, title: String, photo: String?): String {
            val t = android.net.Uri.encode(title.ifBlank { "Диалог" })
            val p = photo?.let { android.net.Uri.encode(it) } ?: ""
            return "chat_detail/$peerId?title=$t&photo=$p"
        }
    }

    /**
     * P3.1: Экран информации о чате — members / shared media / actions
     * (mute, clear history, block, report spam, leave).
     * Принимает peerId как path-параметр. Метаданные (title/photo/members)
     * грузятся через messages.getConversationsById при открытии.
     */
    object ChatInfo : Screen("chat_info/{peerId}", "Информация", null) {
        const val ARG_PEER_ID = "peerId"
        fun buildRoute(peerId: Long): String = "chat_info/$peerId"
    }

    /**
     * P5.1: Встроенный браузер (WebView) для открытия ссылок из чата.
     * Принимает url как query-параметр (URL-encoded). Используется когда
     * [re.pinok.data.local.SovaPrefs.Snapshot.openLinksInInternalBrowser] = true.
     * Иначе ссылки открываются внешним браузером (ACTION_VIEW).
     */
    object InternalBrowser : Screen("internal_browser?url={url}", "Браузер", null) {
        const val ARG_URL = "url"
        fun buildRoute(url: String): String = "internal_browser?url=${android.net.Uri.encode(url)}"
    }

    /**
     * P3.3: Экран управления папками диалогов — список / добавить / редактировать / удалить.
     * Аналог m.vk.ru: «Папки с чатами» (tid="me_folder_settings_item_*").
     * Без параметров — папки грузятся из FoldersRepository (SovaPrefs.msgFoldersData).
     */
    object FoldersSettings : Screen("folders_settings", "Папки с чатами", null)

    /**
     * Полноэкранный просмотр Stories (vkitStoriesGallery).
     * Данные передаются через in-memory holder [StoryHolder].
     */
    object StoryViewer : Screen("story_viewer", "Истории", null)

    /**
     * Fix #50: Экран офлайн аудиоплеера — минималистичный, без сетевых запросов.
     *
     * Показывает только скачанные треки из [re.pinok.media.TrackDownloadManager]:
     * прогресс, controls (play/pause/prev/next), seek bar, очередь, shuffle/repeat.
     * Никаких обложек/лайков/репостов — только локальные файлы.
     */
    object OfflineAudioPlayer : Screen("offline_audio_player", "Офлайн плеер", null)

    /**
     * Fix #111: Экран офлайн-просмотра скачанной видео-истории.
     *
     * Принимает ownerId + storyId как nav args, читает локальный файл через
     * [re.pinok.media.StoryVideoDownloadManager.getLocalFile] и meta через
     * [re.pinok.media.StoryVideoDownloadManager.getStoryMeta]. Воспроизводит
     * через ExoPlayer с file:// URI — без сети, без CDN URL refresh.
     *
     * Отдельный экран от [StoryViewer] (который работает с live API stories
     * через StoryHolder) и от [VideoPlayer] (работает с Video model, не Story).
     */
    object StoryOfflinePlayer : Screen("story_offline_player/{ownerId}/{storyId}", "История", null) {
        const val ARG_OWNER_ID = "ownerId"
        const val ARG_STORY_ID = "storyId"
        fun buildRoute(ownerId: Long, storyId: Int): String = "story_offline_player/$ownerId/$storyId"
    }

    /**
     * §37.12 #330: Экран офлайн-просмотра скачанного клипа.
     *
     * Принимает ownerId + videoId (Long) как nav args, читает локальный файл
     * через [re.pinok.media.ClipVideoDownloadManager.getLocalFile] и meta через
     * [re.pinok.media.ClipVideoDownloadManager.getClipMeta]. Воспроизводит через
     * ExoPlayer с file:// URI и RESIZE_MODE_ZOOM (TikTok-стиль 9:16 vertical).
     *
     * Отдельный экран от [StoryOfflinePlayer]:
     *  - clips используют Long videoId (у stories — Int storyId);
     *  - clips хранятся в ClipVideoDownloadManager (clip_downloads/, ключ "c_*");
     *  - clips имеют TikTok-стиль ZOOM-resize + ContentScale.Crop.
     */
    object ClipOfflinePlayer : Screen("clip_offline_player/{ownerId}/{videoId}", "Клип", null) {
        const val ARG_OWNER_ID = "ownerId"
        const val ARG_VIDEO_ID = "videoId"
        fun buildRoute(ownerId: Long, videoId: Long): String = "clip_offline_player/$ownerId/$videoId"
    }

    // Drawer — основные разделы социальной сети
    object Friends       : Screen("friends",       "Друзья",       Icons.Default.Group)
    object Groups        : Screen("groups",        "Сообщества",   Icons.Outlined.Groups)
    object Photos        : Screen("photos",        "Фото",         Icons.Outlined.Image)
    object Search        : Screen("search",        "Поиск",        Icons.Default.Search)
    object Bookmarks     : Screen("bookmarks",     "Закладки",     Icons.Outlined.Bookmark)
    object Documents     : Screen("documents",     "Документы",    Icons.Outlined.Description)
    /** §37.12 Phase 6: VK Clips — короткие вертикальные видео. */
    object Clips         : Screen("clips",         "Клипы",        Icons.Outlined.VideoLibrary)
    /** §37.12 Phase 5: запись и публикация нового клипа. */
    object ClipCreate    : Screen("clip_create",   "Новый клип",   Icons.Filled.Videocam)

    // Drawer — системные разделы
    object Services      : Screen("services",      "Сервисы",      Icons.Default.Apps)
    object Notifications : Screen("notifications", "Уведомления",  Icons.Default.AlternateEmail)
    object Settings      : Screen("settings",      "Настройки",    Icons.Default.Settings)
    object NotificationSettings : Screen("notification_settings", "Настройки уведомлений", Icons.Default.Notifications)
    /** #CALLS: история звонков (пропущенные/входящие/исходящие). */
    object CallsHistory  : Screen("calls_history", "Звонки",       Icons.Filled.Call)
    object About         : Screen("about",         "О приложении", null)

    object Logs          : Screen("logs",          "Логи",         Icons.Default.BugReport)

    /**
     * Экран офлайн-менеджера — просмотр скачанных аудио и видео
     * при отсутствии сети. Доступен из ошибки ленты или настроек.
     */
    object OfflineManager : Screen("offline_manager", "Офлайн", Icons.Outlined.CloudOff)

    /**
     * Этап 2 (#Equalizer): Полноэкранный эквалайзер — 5 вкладок:
     * Пресеты / Полосы / Bass+Virt / Reverb / Loudness.
     *
     * Открывается из кнопки в боковом drawer. В аудиоплеере остаётся
     * упрощённый BottomSheet (только пресеты + master switch + 5 полос).
     * Полный экран — для тонкой настройки всех 6 эффектов.
     *
     * Видимость отдельных вкладок регулируется через [re.pinok.media.EqualizerFeatureFlags]
     * (настройки → вкладка «Эквалайзер»).
     */
    object Equalizer : Screen("equalizer", "Эквалайзер", Icons.Filled.Equalizer)

    /**
     * §49.6 Sprint VK-ID-1.2: Экран «Устройства и сессии» — список активных
     * сессий аккаунта VK с возможностью завершить любую/all.
     * Источник: анализ VK ID_веб.zip (VK_IMPORT_API.MD §49.6).
     *
     * Доступ: Настройки → Защита → «Устройства и сессии».
     * Deep-link: из security-alert notification (SecurityAlertNotifier).
     */
    object Devices : Screen("devices", "Устройства", Icons.Default.DevicesOther)

    /**
     * #CALLS: экран активного звонка (входящий/исходящий/разговор).
     * Принимает peerId как path-параметр, title/photo — query-параметры.
     * Открывается из ChatDetailScreen (кнопка звонка) или из уведомления
     * о входящем звонке (CallForegroundService).
     */
    object Call : Screen("call/{peerId}?title={title}&photo={photo}&incoming={incoming}&payload={payload}", "Звонок", null) {
        const val ARG_PEER_ID = "peerId"
        const val ARG_TITLE = "title"
        const val ARG_PHOTO = "photo"
        const val ARG_INCOMING = "incoming"
        const val ARG_PAYLOAD = "payload"
        fun buildRoute(peerId: Long, title: String, photo: String?, incoming: Boolean, payload: String? = null): String {
            // #CALLS-NAME-FIX (2026-08-29): URLEncoder (FORM) превращал пробел в «+»:
            // «Входящий звонок» приходил на экран как «Входящий+звонок» (Navigation
            // декодирует %XX, но не «+»). Uri.encode → %20 → корректный пробел.
            val t = android.net.Uri.encode(title.ifBlank { "Звонок" })
            val p = photo?.let { android.net.Uri.encode(it) } ?: ""
            val pl = payload?.let { android.net.Uri.encode(it) } ?: ""
            return "call/$peerId?title=$t&photo=$p&incoming=$incoming&payload=$pl"
        }
    }

    /** #CALLS-WEBVIEW (2026-08-25): звонки через WebView vk.ru/calls. */
    object CallsWebView : Screen("calls_webview", "Звонки", null)

    companion object {
        val dock = listOf(Feed, Messages, Music, Video, Profile)
        // Сначала социальные разделы, потом системные.
        val drawer = listOf(
            Friends, Groups, Photos, Search, Bookmarks, Documents,
            CallsHistory, Services, Notifications, Settings, Logs,
        )
    }
}

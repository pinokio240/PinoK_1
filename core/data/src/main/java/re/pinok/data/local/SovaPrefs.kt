package re.pinok.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import re.pinok.util.AppLog

/**
 * #OFFLINE-TAB: формат сохранения скачанных аудио.
 *
 * - M4A (default) — AAC в MP4-контейнере. Android MediaMuxer собирает нативно,
 *   играет везде (Android/iOS/Windows/macOS), ноль внешних зависимостей.
 *   Метаданные — через MP4 atoms (ilst). Используется для ВСЕХ треков сейчас.
 * - MP3 — opt-in. Универсальнее для экспорта во внешние приложения (рингтоны,
 *   плееры), ID3v2.4 теги. Требует ffmpeg-kit (не подключён в этом билде) +
 *   Siren-транскодер (P0 #2 из VK_IMPORT_API.MD §42). Сейчас при выборе MP3
 *   TrackDownloadManager сохраняет .m4a с предупреждением (fallback), пока
 *   кодек не интегрирован. См. [re.pinok.media.TrackDownloadManager.downloadHlsTrack].
 */
enum class AudioFormat(val prefValue: String, val ext: String, val label: String) {
    M4A("m4a", "m4a", "M4A (AAC)"),
    MP3("mp3", "mp3", "MP3");

    companion object {
        fun fromPref(v: String?): AudioFormat {
            if (v == null) return MP3
            for (fmt in entries) {
                if (fmt.prefValue == v) return fmt
            }
            return M4A
        }
    }
}

/** Качество выходного MP3-файла (битрейт). */
enum class AudioQuality(val prefValue: String, val bitrate: String, val label: String) {
    Q128("128", "128k", "128 kbps"),
    Q192("192", "192k", "192 kbps"),
    Q320("320", "320k", "320 kbps");

    companion object {
        fun fromPref(v: String?): AudioQuality {
            if (v == null) return Q192
            for (q in entries) {
                if (q.prefValue == v) return q
            }
            return Q192
        }
    }
}

private val Context.sovaDataStore by preferencesDataStore(name = "sova_settings")

/**
 * DataStore-backed settings for SOVA 2.0.
 *
 * Covers all 7 categories of the original SOVA V RE preferences:
 *  - news, interface, privacy, messages, music, network, locker
 *
 * Plus a few SOVA_2.0-only extras (selected accent color index).
 */
// #ARCH-DATA (Task 20): дефолт showLogFab раньше читался из BuildConfig.DEBUG
// (:app) — библиотечному модулю :core:data BuildConfig не нужен, значение
// подаёт хост (SovaApp: SovaPrefs(this, BuildConfig.DEBUG)).
class SovaPrefs(context: Context, debugDefault: Boolean = false) {

    private val ds = context.applicationContext.sovaDataStore

    val data: Flow<Snapshot> = ds.data.map { p ->
        Snapshot(
            // News
            newsAdsBlocked     = p[Keys.NEWS_ADS_BLOCKED]     ?: true,
            newsRepostsHidden  = p[Keys.NEWS_REPOSTS_HIDDEN]  ?: false,
            newsPromoHidden    = p[Keys.NEWS_PROMO_HIDDEN]    ?: true,

            // Interface
            themeDark          = p[Keys.THEME_DARK]            ?: true,
            themeAccentIndex   = p[Keys.THEME_ACCENT_INDEX]    ?: 6,
            themeDynamic       = p[Keys.THEME_DYNAMIC]         ?: false,
            // #MONET-HYBRID: гибридный режим — при включённом Material You
            //   primary/secondary/tertiary = accent (пользовательский),
            //   а surface/background/surfaceVariant = из обоев (dynamic).
            //   Без этого Monet полностью перекрашивает accent-роли под обои.
            themeMonetHybrid   = p[Keys.THEME_MONET_HYBRID]     ?: true,
            fontScale          = p[Keys.FONT_SCALE]            ?: 100,
            // Fix #224: скорость анимаций интерфейса (0..100%). 100 — норма,
            // 50 — вдвое быстрее, 0 — анимации выключены (мгновенные переходы).
            // Применяется к NavHost-переходам, swipe-reply spring, AnimatedVisibility.
            interfaceAnimSpeed = p[Keys.INTERFACE_ANIM_SPEED]  ?: 100,

            // Fix #228: масштаб стикер-фото в чате (0..40, в процентах увеличения
            // относительно исходного размера). 0 — исходный размер, 40 — +40% к
            // оригиналу. Применяется в ChatDetailScreen к стикерам, отправленным
            // как картинка (isStickerLike).
            stickerPhotoScale  = p[Keys.STICKER_PHOTO_SCALE]  ?: 0,

            // Fix #237: показ плавающего значка логирования (DraggableLogFab).
            // Default = BuildConfig.DEBUG — в debug-сборке виден разработчику,
            // в release-сборке скрыт по умолчанию. Пользователь может включить
            // в настройках (SettingsScreen → Логирование → «Показывать значок»). 
            showLogFab         = p[Keys.SHOW_LOG_FAB]           ?: debugDefault,
            // #LOG-CATEGORIES: множество имён отключенных категорий логов.
            // #LOG-CATEGORIES-DEFAULT-CRITICAL (2026-08-05): default =
            // NON_CRITICAL_CATEGORY_NAMES — включены только AUTH+SYSTEM+NETWORK,
            // остальные 8 категорий выключены (шум). Пользователь: «по умолчанию
            // только критические источники». Читается в SovaApp.onCreate и
            // применяется через AppLog.applyDisabledCategories().
            logCategoriesDisabled = p[Keys.LOG_CATEGORIES_DISABLED]
                ?: AppLog.NON_CRITICAL_CATEGORY_NAMES,
            // #238: показ FAB «подняться в верх ленты» при прокрутке вниз.
            // Default = true — FAB виден по умолчанию, пользователь может скрыть
            // в настройках (SettingsScreen → Интерфейс → «Кнопка наверх в ленте»).
            feedShowScrollFab  = p[Keys.FEED_SHOW_SCROLL_FAB]   ?: true,
            // #FEED-FILTER-TOGGLE: показывать панель разделов ленты (FeedFilterBar).
            feedShowFilter      = p[Keys.FEED_SHOW_FILTER]       ?: false,
            // #NET-SWITCH-POPUP (2026-08-04): popup при переключении сети.
            // Default = false — popup СКРЫТ по умолчанию (пользователь просил
            // «по умолчанию выключено»). Переключение сети и silent refresh
            // продолжают работать в фоне (без UI). Юзер может включить в Настройках.
            netSwitchPopupEnabled = p[Keys.NET_SWITCH_POPUP_ENABLED] ?: false,

            // Privacy
            privacyOfflineMode  = p[Keys.PRIVACY_OFFLINE]       ?: false,
            // Fix #DEFAULTS-OFF (2026-08-04): default true → false.
            // Пользователь хочет чтобы маскировка устройства по умолчанию
            // была выключена. Сама функция рабочая (VKApiClient подменяет
            // device_model/os_version/build/manufacturer на Pixel 9 Pro).
            privacyDeviceMask   = p[Keys.PRIVACY_DEVICE_MASK]  ?: false,
            privacyAntiTelemetry= p[Keys.PRIVACY_ANTI_TELEMETRY] ?: true,
            // Fix #HIDE-LAST-SEEN-DEAD (2026-08-04): default true → false.
            // ВАЖНО: настройка НЕ РАБОТАЕТ — accountSetOnline() не вызывается
            // нигде в проекте (0 callers), filterUsersFields() удалён в #30i.
            // Toggle сохраняется в DataStore, но эффекта 0 (placebo).
            // Default=false чтобы не вводить пользователя в заблуждение.
            // Починка — отдельная задача (нужен periodic account.setOnline
            // ping через WorkManager, либо возврат filterUsersFields).
            privacyHideLastSeen = p[Keys.PRIVACY_HIDE_LAST_SEEN] ?: false,

            // Messages
            msgDnr             = p[Keys.MSG_DNR]                ?: false,
            msgDnt             = p[Keys.MSG_DNT]                ?: false,
            msgUndelete        = p[Keys.MSG_UNDELETE]           ?: true,
            msgUnedit          = p[Keys.MSG_UNEDIT]             ?: true,
            // P0.1: typing indicator in ChatDetailScreen (LongPoll codes 61/62).
            msgTypingIndicator = p[Keys.MSG_TYPING_INDICATOR]   ?: true,
            // P0.3: pinned message bar + pin/unpin in context menu.
            msgPinBar          = p[Keys.MSG_PIN_BAR]              ?: true,
            msgShowFavorites   = p[Keys.MSG_SHOW_FAVORITES]     ?: false,
            // P1.3: message grouping — объединение последовательных сообщений
            // от одного отправителя в пределах 5 минут (hide avatar/name, плоские углы).
            msgGrouping        = p[Keys.MSG_GROUPING]              ?: true,
            // P1.1: date separators («Сегодня», «Вчера», «12 июля») между сообщениями
            // разных дней. Sticky header в LazyColumn.
            msgDateSeparators  = p[Keys.MSG_DATE_SEPARATORS]      ?: true,
            // P1.1: unread divider («Непрочитанные сообщения») перед первым непрочитанным.
            msgUnreadDivider   = p[Keys.MSG_UNREAD_DIVIDER]       ?: true,
            // P1.1: scroll-to-bottom FAB — появляется при прокрутке вверх, тап → вниз.
            msgScrollFab       = p[Keys.MSG_SCROLL_FAB]            ?: true,
            // P1.2: reply via swipe — свайп вправо на входящем / влево на исходящем
            // активирует reply mode (тот же callback что в context menu).
            msgSwipeReply      = p[Keys.MSG_SWIPE_REPLY]           ?: true,
            // P2.6: read receipts (✓/✓✓) — статус прочтения исходящих сообщений.
            msgReadReceipts    = p[Keys.MSG_READ_RECEIPTS]         ?: true,
            // P1.4: search bar + tabs (Все/Каналы/Непрочитанные) в MessagesScreen.
            msgSearch          = p[Keys.MSG_SEARCH]                ?: true,
            // P2.5: multi-select mode — long-press → «Выбрать» → выделение нескольких
            // сообщений для массового Delete/Forward. Opt-in (default false).
            msgMultiSelect     = p[Keys.MSG_MULTI_SELECT]          ?: false,
            // P3.5: multi-file upload — выбор до 10 фото за раз (PickMultipleVisualMedia).
            msgMultiFile       = p[Keys.MSG_MULTI_FILE]            ?: true,
            // P3.6: dual send/mic button — state machine (EDIT/LOADING/LIMIT/MIC/SUBMIT).
            // Opt-in (default false).
            msgDualButton      = p[Keys.MSG_DUAL_BUTTON]           ?: false,
            // P3.2: mute/unmute chat — toggle уведомлений (default true).
            msgMute            = p[Keys.MSG_MUTE]                 ?: true,
            // P3.1: ChatInfo screen — отдельный экран с members/media/actions (default true).
            msgChatInfo        = p[Keys.MSG_CHAT_INFO]            ?: true,
            // P3.4: channel mode — отдельный UX для каналов (broadcast-сообщества):
            // скрытие composer, footer «Вы подписаны», mute/leave в одно действие.
            // Default true — каналы определяются автоматически по can_write.allowed=false.
            msgChannelMode     = p[Keys.MSG_CHANNEL_MODE]         ?: true,
            // P3.3: folders system — пользовательские папки диалогов.
            // Default false (opt-in, экспериментально) — API messages.getChatFolders
            // недокументирован, поэтому папки хранятся клиентски в msgFoldersData (JSON).
            msgFolders         = p[Keys.MSG_FOLDERS]              ?: false,
            // P3.3: JSON-сериализованный список ChatFolder (source of truth для UI).
            // Пустая строка = нет папок. Десериализация в FoldersRepository.
            msgFoldersData     = p[Keys.MSG_FOLDERS_DATA]         ?: "",
            // Fix #276: JSON-массив peer_id закреплённых диалогов (в порядке закрепления,
            // 0-й элемент — самый верхний). Source of truth для UI списка диалогов.
            // VK API messages.markAsImportantConversation требует special-scope
            // token (выдаётся только по запросу в support), недоступен нашему web-token,
            // поэтому закрепление хранится локально + sync-попытка API best-effort.
            pinnedConvsData    = p[Keys.PINNED_CONVS_DATA]        ?: "",
            // P3.7: bubble-less дизайн — flat layout сообщений (без Card/bubble).
            // Аналог m.vk.ru: ConvoMessageWithoutBubble. Default false (opt-in, экспериментально).
            msgBubbleless      = p[Keys.MSG_BUBBLELESS]           ?: false,
            // P4.2: LongPoll backfill — восстановление пропущенных между сессиями
            // событий через messages.getLongPollHistory(pts, ts). Fix #339: default true —
            // без backfill'а накопленные за время Doze сообщения теряются и юзер не видит
            // пропущенные push-уведомления (FCM нет, LongPoll — единственный канал).
            msgLpBackfill      = p[Keys.MSG_LP_BACKFILL]          ?: true,
            // P4.1: LongPoll v14 — lp_version=14, mode=1226 (расширенные поля в ответе:
            // attachments, random_id, peer_id, message_id, platform). Default false (opt-in,
            // экспериментально — парсер v3 совместим с v14 обратно, но v14 возвращает
            // больше данных, потенциально больше трафика).
            msgLpV14           = p[Keys.MSG_LP_V14]              ?: false,
            // §52.5 Sprint A (P0): Modern Sync API — messages.getDiff (lp_version=21).
            // LongPoll-credentials (key/ts/server_lp), folders и counters одним запросом
            // вместо getLongPollServer. Default false (opt-in) — legacy getLongPollServer
            // остаётся fallback'ом. Парсер LP-событий v21 совместим с базовыми кодами v3.
            msgModernSync      = p[Keys.MSG_MODERN_SYNC]          ?: false,
            // P4.4: execute batching — группировка нескольких API-вызовов в один
            // HTTP round-trip через execute.VKscript. Default false (opt-in, экспериментально).
            // Ограничения: photos.saveMessagesPhoto, docs.save и другие из
            // executeUnsupportedMethods НЕ могут быть batch'ены — только get/send/... .
            msgExecuteBatch    = p[Keys.MSG_EXECUTE_BATCH]        ?: false,
            // P4.3: WebSocket transport для каналов — VK переводит каналы на WS
            // (frontend.vkm_new_channels_ws_engine:1). Default false (opt-in).
            // ⚠️ Protocol недокументирован, может измениться без notice. Заготовка
            // [re.pinok.realtime.ChannelWebSocketClient] — stub для будущего использования.
            // Не активен в основном flow пока VK не форсирует отказ от LongPoll.
            msgWsChannels      = p[Keys.MSG_WS_CHANNELS]          ?: false,
            // P4.2: последний сохранённый ts (для backfill при следующем старте).
            lpLastTs           = p[Keys.LP_LAST_TS]              ?: 0L,
            // P4.2: последний сохранённый pts (для backfill при следующем старте).
            lpLastPts          = p[Keys.LP_LAST_PTS]             ?: 0L,
            // P5.1: открытие ссылок из чата во внутреннем браузере (WebView).
            // Default: false — внешний браузер (текущее поведение, Fix #51-A).
            openLinksInInternalBrowser = p[Keys.OPEN_LINKS_INTERNAL] ?: false,

            // Cache
            cacheSizeMb        = p[Keys.CACHE_SIZE_MB]         ?: 0L,  // 0 = без ограничений
            cacheCustomPath    = p[Keys.CACHE_CUSTOM_PATH]     ?: "",

            // Music
            musicDownloadPath  = p[Keys.MUSIC_DOWNLOAD_PATH]   ?: "/Music/PinoK/",
            videoDownloadPath  = p[Keys.VIDEO_DOWNLOAD_PATH]   ?: "",
            musicHighQuality   = p[Keys.MUSIC_HQ]              ?: true,
            musicBackgroundPlay= p[Keys.MUSIC_BG_PLAY]         ?: true,
            // #OFFLINE-TAB: формат сохранения скачанных аудио. Default: M4A.
            audioFormat        = AudioFormat.fromPref(p[Keys.AUDIO_FORMAT]),
            audioQuality       = AudioQuality.fromPref(p[Keys.AUDIO_QUALITY]),
            // §42.12 P1 #3: писать MP4 metadata теги (©nam/©ART/©alb/©too). Default true.
            writeId3Tags       = p[Keys.WRITE_ID3_TAGS]       ?: true,
            // §42.12 P2 #8: добавлять тексты из Genius в ©lyr. Default false (opt-in,
            // требует доп. сетевой запрос + парсинг HTML).
            writeGeniusLyrics  = p[Keys.WRITE_GENIUS_LYRICS]  ?: false,
            // §42.12 P2 #9: промо-комментарий «Downloaded by PinoK» в cmt. Default false.
            writePromoComment  = p[Keys.WRITE_PROMO_COMMENT]  ?: false,
            // §42.12 P3 #11: метод конвертации siren-треков. "siren_transcoder"
            // (ffmpeg-kit, default) или "hls_native" (только AAC, siren→.ts fallback).
            audioConvertMethod = p[Keys.AUDIO_CONVERT_METHOD] ?: "siren_transcoder",
            // §42.12 P1 #5: добавлять "NN. " префикс к имени файла (номер трека
            // в плейлисте). Default true — как в VKNext. Для одиночных треков
            // не используется (index=null).
            numTracksInPlaylist = p[Keys.NUM_TRACKS_IN_PLAYLIST] ?: true,
            // Fix #334: предпочтительное качество видео для плеера и клипов.
            // Значения: "auto" (макс доступное) | "2160" | "1440" | "1080" |
            // "720" | "480" | "360" | "240" | "144". Видео-плеер выбирает ближайшее
            // доступное ≤ предпочтённого (fallback на макс если preferred выше всех).
            videoPreferredQuality = p[Keys.VIDEO_PREFERRED_QUALITY] ?: "auto",
            // #VIDEO-AUTOPLAY: автовоспроизведение видео при открытии VideoPlayerScreen.
            // Default true — пользователь запросил «по умолчанию включено».
            // При выключении ExoPlayer создаётся с playWhenReady=false и LifecycleStartEffect
            // не форсирует play — пользователь жмёт кнопку play сам.
            videoAutoplay = p[Keys.VIDEO_AUTOPLAY] ?: true,
            // OK-IMPL-1 (Stage 7) + FEED-FIX-4 (#349): включение встраивания
            // внешних видео (YouTube, OK.ru iframe, иные iframe-источники) И
            // нативного OK-воспроизведения в VideoPlatformRouter.
            // Default true — пользователь явно запросил «Включить тумблер
            // Воспроизводить из OK / YouTube в Настройках по умолчанию должен
            // быть включён». VK-видео играет нативно всегда (ExoPlayer) —
            // этот флаг на VK НЕ влияет. При выключении OK/YouTube/external
            // показывается placeholder «Внешние видео отключены» с кнопкой
            // «Открыть в браузере».
            // (FEED-FIX-1 #346 временно ставил default=false из-за подозрения
            // что OkWebViewPlayer ломает авторизацию; но OkVideoRepository
            // нативный path НЕ использует WebView вообще → не влияет на auth.
            // FEED-FIX-2 #347 пофиксил detectPlatform → OK теперь правильно
            // определяется и идёт в нативный path. WebView включается только
            // для YouTube/EXTERNAL_IFRAME.)
            externalVideosEnabled = p[Keys.EXTERNAL_VIDEOS_ENABLED] ?: true,
            // Fix #337: редактор панелей. JSON-массивы маршрутов (Screen.route)
            // в порядке отображения. Скрытые пункты хранятся в *_hidden
            // (JSON-массив route). Боковая панель: 3 фикс. кнопки в низу
            // (Офлайн, Настройки, Выйти) — они НЕ входят в sidebarItemsOrder
            // и не редактируются. Только "dynamic" пункты (Friends/Groups/...).
            sidebarItemsOrder = p[Keys.SIDEBAR_ITEMS_ORDER] ?: SIDEBAR_DEFAULT_ORDER,
            sidebarItemsHidden = p[Keys.SIDEBAR_ITEMS_HIDDEN] ?: "[]",
            // #BOTTOM-DEFAULT-4 (2026-08-01): пользователь просил, чтобы по умолчанию
            // на нижней панели было 4 кнопки: Профиль, Сообщения, Музыка, Видео.
            // Раньше дефолт был 5 (feed + 4) и hidden=[] → после #SIDEBAR-BOTTOM-UNION
            // visible раздувался до всех 17 пунктов (normalizeRouteOrder добавлял
            // недостающие из canonical). Теперь hidden по умолчанию скрывает все,
            // кроме 4 дефолтных. Миграция PANEL_DEFAULTS_V2 в SovaApp.onCreate
            // перезаписывает order/hidden для существующих пользователей.
            bottomBarItemsOrder = p[Keys.BOTTOMBAR_ITEMS_ORDER] ?: BOTTOMBAR_DEFAULT_ORDER,
            bottomBarItemsHidden = p[Keys.BOTTOMBAR_ITEMS_HIDDEN] ?: BOTTOMBAR_DEFAULT_HIDDEN,

            // Stories (Fix #100)
            // Fix #AUTOCACHE-STORIES-OFF (2026-08-04): default = false.
            // Пользователь решил что авто-кэш историй по умолчанию выключен
            // (тратит трафик + место; вручную включается через Настройки→Видео).
            autoCacheStories   = p[Keys.AUTO_CACHE_STORIES]    ?: false,
            storyCacheLimitMb  = p[Keys.STORY_CACHE_LIMIT_MB]  ?: 200,

            // Audio auto-cache (Fix #110)
            // Fix #AUTOCACHE-AUDIO-OFF (2026-08-05): default = false.
            // Пользователь решил что авто-загрузка аудио по умолчанию выключена
            // (тратит трафик + место; вручную включается через Настройки→Авто-загрузка).
            // Аналогично Fix #AUTOCACHE-STORIES-OFF для stories.
            autoCacheAudio     = p[Keys.AUTO_CACHE_AUDIO]      ?: false,

            // Network
            netSslPinning      = p[Keys.NET_SSL_PINNING]       ?: false,
            netAwayBypass      = p[Keys.NET_AWAY_BYPASS]       ?: true,
            netAdBlock         = p[Keys.NET_AD_BLOCK]          ?: true,
            // m.vk.ru mobile-web API gateway (web.api.vk.ru). See VKEndpoints.WEB_API_HOST.
            // Default: false — current api.vk.com Android gateway is the default.
            netUseWebApiGateway= p[Keys.NET_USE_WEB_API_GATEWAY] ?: false,
            netProxyEnabled    = p[Keys.NET_PROXY_ENABLED]     ?: false,
            netProxyHost       = p[Keys.NET_PROXY_HOST]        ?: "",
            netProxyPort       = p[Keys.NET_PROXY_PORT]        ?: 8080,

            // Locker
            // Fix #DEFAULTS-OFF (2026-08-04): lockerOnBackground default true → false.
            // ВАЖНО: подсистема locker ЧАСТИЧНО МЁРТВАЯ — LockerActivity существует
            // (полноценный PIN-screen + биометрия), но setLockerPinHash() НИКЕМ
            // не вызывается → lockerPinHash всегда "" → LockerActivity никогда
            // не запустится. lockerOnBackground триггерится только при
            // lockerPinHash.isNotBlank(), поэтому по умолчанию бесполезен.
            // Default=false чтобы не вводить в заблуждение.
            // Починка PIN-setup диалога — отдельная задача.
            lockerEnabled      = p[Keys.LOCKER_ENABLED]        ?: false,
            lockerPinHash      = p[Keys.LOCKER_PIN_HASH]       ?: "",
            lockerBiometric    = p[Keys.LOCKER_BIOMETRIC]      ?: false,
            lockerOnBackground = p[Keys.LOCKER_ON_BACKGROUND]  ?: false,

            // Navigation
            lastRoute          = p[Keys.LAST_ROUTE]            ?: "feed",

            // Fix #189: Auth Domains Config — настраиваемые VK домены (.com/.ru)
            // Доступны ДО авторизации через шестерёнку на LandingScreen.
            authOauthHost      = p[Keys.AUTH_OAUTH_HOST]        ?: AUTH_OAUTH_HOST_DEFAULT,
            authIdHost         = p[Keys.AUTH_ID_HOST]          ?: AUTH_ID_HOST_DEFAULT,
            authLoginHost      = p[Keys.AUTH_LOGIN_HOST]       ?: AUTH_LOGIN_HOST_DEFAULT,
            authMobileWebHost  = p[Keys.AUTH_MOBILE_WEB_HOST]  ?: AUTH_MOBILE_WEB_HOST_DEFAULT,
            authApiHost        = p[Keys.AUTH_API_HOST]         ?: AUTH_API_HOST_DEFAULT,
            authWebClientId    = p[Keys.AUTH_WEB_CLIENT_ID]    ?: AUTH_WEB_CLIENT_ID_DEFAULT,
            authForceRevoke    = p[Keys.AUTH_FORCE_REVOKE]     ?: false,

            // Fix #302 (Task 2-b): JSON-кэш состояний sn_* toggles из
            // settingsGeneral.getNotifySettings(page="notify"). Структура:
            // {"sn_messages":true,"sn_chats":false,...}. Используется для
            // мгновенного отображения состояния при повторном входе на вкладку
            // «Уведомления», без ожидания API-ответа. Пустая строка = нет кэша.
            notifyCacheJson    = p[Keys.NOTIFY_CACHE_JSON]      ?: "",
            // §1-NOTIF-ARCHIVE: частота email-уведомлений из архива m.vk.ru/settings?act=notify.
            emailNotifyFreq    = p[Keys.EMAIL_NOTIFY_FREQ]      ?: 0,

            // §42 #PUSH-NOTIFICATIONS: локальные push-уведомления.
            pushEnabled            = p[Keys.PUSH_ENABLED]            ?: true,
            pushLikes              = p[Keys.PUSH_LIKES]              ?: true,
            pushComments           = p[Keys.PUSH_COMMENTS]           ?: true,
            pushReplies            = p[Keys.PUSH_REPLIES]            ?: true,
            pushFollows            = p[Keys.PUSH_FOLLOWS]            ?: true,
            pushMentions           = p[Keys.PUSH_MENTIONS]           ?: true,
            pushReposts            = p[Keys.PUSH_REPOSTS]            ?: true,
            pushWall               = p[Keys.PUSH_WALL]               ?: true,
            pushGifts              = p[Keys.PUSH_GIFTS]              ?: true,
            pushOther              = p[Keys.PUSH_OTHER]              ?: true,
            pushPollingIntervalSec = p[Keys.PUSH_POLLING_INTERVAL]   ?: 120,
            pushLastSeenKeys       = p[Keys.PUSH_LAST_SEEN_KEYS]     ?: "",

            // §42.2 #PUSH-ENHANCED: расширенные настройки отображения/группировки.
            // Авто-скрытие (0 = никогда, иначе ms до auto-cancel).
            pushAutoDismissMs      = p[Keys.PUSH_AUTO_DISMISS_MS]     ?: 0L,
            // Режим превью: "full" / "sender_only" / "hidden".
            pushPreviewMode        = p[Keys.PUSH_PREVIEW_MODE]        ?: "full",
            // Длина превью текста (0/40/80/160 символов).
            pushPreviewLength      = p[Keys.PUSH_PREVIEW_LENGTH]      ?: 80,
            // Quiet hours (не беспокоить).
            pushQuietHoursEnabled  = p[Keys.PUSH_QUIET_HOURS_ENABLED] ?: false,
            pushQuietHoursStart    = p[Keys.PUSH_QUIET_HOURS_START]   ?: 1320,  // 22:00
            pushQuietHoursEnd      = p[Keys.PUSH_QUIET_HOURS_END]     ?: 480,   // 08:00
            // Визуальные опции.
            pushShowAvatar         = p[Keys.PUSH_SHOW_AVATAR]         ?: true,
            pushShowBigPicture     = p[Keys.PUSH_SHOW_BIG_PICTURE]    ?: true,
            pushSoundEnabled       = p[Keys.PUSH_SOUND_ENABLED]       ?: true,
            pushVibrationEnabled   = p[Keys.PUSH_VIBRATION_ENABLED]   ?: true,
            pushLedColor           = p[Keys.PUSH_LED_COLOR]           ?: 0,     // 0=system
            // Группировка.
            // §42.6 #PUSH-NO-GROUP-DEFAULT: default изменён с "category" на "none".
            // При "category" 3+ уведомления сворачиваются в стопку "N новых лайков" —
            // чтобы увидеть отдельные посты, нужен pinch-out (жест двумя пальцами),
            // который почти никто не знает. Тап по стопке открывает экран уведомлений,
            // а не отдельные посты. Default "none" = каждое уведомление отдельно,
            // каждое напрямую тапаемое со своим deep-link.
            pushGroupingMode       = p[Keys.PUSH_GROUPING_MODE]       ?: "none",
            pushGroupThreshold     = p[Keys.PUSH_GROUP_THRESHOLD]     ?: 3,
            // Кнопки действий в уведомлении.
            pushActionButtons      = p[Keys.PUSH_ACTION_BUTTONS]      ?: true,
            // §46 #REMOTE-INPUT: кнопка «Ответить» с RemoteInput (прямой ответ
            // из шторки для comment/reply уведомлений). Default true.
            pushReplyButton        = p[Keys.PUSH_REPLY_BUTTON]        ?: true,
            // Список заглушённых пользователей (CSV).
            pushPerUserMuted       = p[Keys.PUSH_PER_USER_MUTED]      ?: "",
            // Задержка перед показом (grace period, 0 = сразу).
            pushShowDelayMs        = p[Keys.PUSH_SHOW_DELAY_MS]       ?: 0L,
            // §42.3 #PUSH-SOURCE-FILTER: фильтр по источнику (кто/что).
            pushFromCommunities    = p[Keys.PUSH_FROM_COMMUNITIES]    ?: true,
            pushFromUsers          = p[Keys.PUSH_FROM_USERS]          ?: true,
            // §49.5.1 #SAFETY-NET-ALERTS (2026-08-04): alerts о подозрительных
            // входах (новое устройство/город). Poller дергает accountPersonal.
            // getSecurityAlerts каждые 10 мин. Default true — proactive security.
            // Связано с Fix #340 LongPollKeepAliveService (poller работает в фоне).
            pushSafetyNetAlerts    = p[Keys.PUSH_SAFETY_NET_ALERTS]   ?: true,
            // §49.5.1: интервал polling SecurityAlerts (минуты, default 10).
            safetyNetPollIntervalMin = p[Keys.SAFETY_NET_POLL_INTERVAL] ?: 10,
            // #CALLS: queuev4 credential (ввод вручную из localStorage).
            callsQueueKey = p[Keys.CALLS_QUEUE_KEY] ?: "",
            callsQueueTs = p[Keys.CALLS_QUEUE_TS] ?: 0L,
            callsSessionKey = p[Keys.CALLS_SESSION_KEY] ?: "",
            callsSessionUid = p[Keys.CALLS_SESSION_UID] ?: 0L,
            callsCallToken = p[Keys.CALLS_CALL_TOKEN] ?: "",
            callsVideoRx = p[Keys.CALLS_VIDEO_RX] ?: true,
            // #CALLS-SYMMETRIC (01.09): видеозаглушка наружу — симметричный звонок.
            // Default true: следующий Wi-Fi-тест решает гипотезу пользователя.
            callsVideoTx = p[Keys.CALLS_VIDEO_TX] ?: true,
            // #CALLS-SWDECODE (01.09): принудительный SW-декодер — диагностика
            // чёрного экрана при доказанном рендере (TextureView, 1354 кадра).
            callsVideoSwDecode = p[Keys.CALLS_VIDEO_SW_DECODE] ?: false,
        )
    }

    suspend fun setNewsAdsBlocked(v: Boolean)            = put(Keys.NEWS_ADS_BLOCKED, v)
    suspend fun setNewsRepostsHidden(v: Boolean)         = put(Keys.NEWS_REPOSTS_HIDDEN, v)
    suspend fun setNewsPromoHidden(v: Boolean)           = put(Keys.NEWS_PROMO_HIDDEN, v)

    suspend fun setThemeDark(v: Boolean)                 = put(Keys.THEME_DARK, v)
    suspend fun setThemeAccentIndex(v: Int)              = put(Keys.THEME_ACCENT_INDEX, v)
    suspend fun setThemeDynamic(v: Boolean)              = put(Keys.THEME_DYNAMIC, v)
    /** #MONET-HYBRID: гибридный режим Material You (dynamic surface + accent primary). */
    suspend fun setThemeMonetHybrid(v: Boolean)          = put(Keys.THEME_MONET_HYBRID, v)
    suspend fun setFontScale(v: Int)                     = put(Keys.FONT_SCALE, v)
    /** Fix #224: скорость анимаций интерфейса (0..100). 0 = выключены. */
    suspend fun setInterfaceAnimSpeed(v: Int)            = put(Keys.INTERFACE_ANIM_SPEED, v)
    /** Fix #228: масштаб стикер-фото (0..40, % увеличения от оригинала). */
    suspend fun setStickerPhotoScale(v: Int)             = put(Keys.STICKER_PHOTO_SCALE, v)
    /** Fix #237: показ плавающего значка логирования. */
    suspend fun setShowLogFab(v: Boolean)                = put(Keys.SHOW_LOG_FAB, v)
    /**
     * #LOG-CATEGORIES (2026-08-04): множество имён ОТКЛЮЧЕННЫХ категорий логов.
     * Каждое имя — это LogCategory.name (enum).
     * #LOG-CATEGORIES-DEFAULT-CRITICAL (2026-08-05): default =
     * AppLog.NON_CRITICAL_CATEGORY_NAMES — включены только AUTH+SYSTEM+NETWORK.
     * Сохраняется в DataStore, загружается в SovaApp.onCreate и применяется
     * через AppLog.applyDisabledCategories().
     *
     * Внимание: заменяет множество целиком (не merge). UI передаёт полный
     * список отключенных категорий на каждое изменение тумблера.
     */
    suspend fun setLogCategoriesDisabled(v: Set<String>) = put(Keys.LOG_CATEGORIES_DISABLED, v)
    /** #238: показ FAB «подняться в верх ленты» в FeedScreen. */
    suspend fun setFeedShowScrollFab(v: Boolean)         = put(Keys.FEED_SHOW_SCROLL_FAB, v)
    /** #FEED-FILTER-TOGGLE: показывать панель разделов ленты. */
    suspend fun setFeedShowFilter(v: Boolean)             = put(Keys.FEED_SHOW_FILTER, v)
    /** #NET-SWITCH-POPUP: включить/выключить popup переключения сети. */
    suspend fun setNetSwitchPopupEnabled(v: Boolean)    = put(Keys.NET_SWITCH_POPUP_ENABLED, v)

    suspend fun setPrivacyOfflineMode(v: Boolean)        = put(Keys.PRIVACY_OFFLINE, v)
    suspend fun setPrivacyDeviceMask(v: Boolean)         = put(Keys.PRIVACY_DEVICE_MASK, v)
    suspend fun setPrivacyAntiTelemetry(v: Boolean)      = put(Keys.PRIVACY_ANTI_TELEMETRY, v)
    suspend fun setPrivacyHideLastSeen(v: Boolean)       = put(Keys.PRIVACY_HIDE_LAST_SEEN, v)

    suspend fun setMsgDnr(v: Boolean)                    = put(Keys.MSG_DNR, v)
    suspend fun setMsgDnt(v: Boolean)                    = put(Keys.MSG_DNT, v)
    suspend fun setMsgUndelete(v: Boolean)               = put(Keys.MSG_UNDELETE, v)
    suspend fun setMsgUnedit(v: Boolean)                 = put(Keys.MSG_UNEDIT, v)
    /** P0.1: show «N печатает…» in ChatDetailScreen TopAppBar subtitle. */
    suspend fun setMsgTypingIndicator(v: Boolean)        = put(Keys.MSG_TYPING_INDICATOR, v)
    /** P0.3: pinned message bar + pin/unpin in context menu. */
    suspend fun setMsgPinBar(v: Boolean)                 = put(Keys.MSG_PIN_BAR, v)
    suspend fun setMsgShowFavorites(v: Boolean)          = put(Keys.MSG_SHOW_FAVORITES, v)
    /** P1.3: message grouping — объединение последовательных сообщений. */
    suspend fun setMsgGrouping(v: Boolean)               = put(Keys.MSG_GROUPING, v)
    /** P1.1: date separators между сообщениями разных дней. */
    suspend fun setMsgDateSeparators(v: Boolean)         = put(Keys.MSG_DATE_SEPARATORS, v)
    /** P1.1: unread divider перед первым непрочитанным сообщением. */
    suspend fun setMsgUnreadDivider(v: Boolean)          = put(Keys.MSG_UNREAD_DIVIDER, v)
    /** P1.1: scroll-to-bottom FAB при прокрутке вверх. */
    suspend fun setMsgScrollFab(v: Boolean)              = put(Keys.MSG_SCROLL_FAB, v)
    /** P1.2: reply via swipe — свайп для ответа на сообщение. */
    suspend fun setMsgSwipeReply(v: Boolean)             = put(Keys.MSG_SWIPE_REPLY, v)
    /** P2.6: read receipts (✓/✓✓) — статус прочтения. */
    suspend fun setMsgReadReceipts(v: Boolean)           = put(Keys.MSG_READ_RECEIPTS, v)
    /** P1.4: search + tabs в MessagesScreen. */
    suspend fun setMsgSearch(v: Boolean)                 = put(Keys.MSG_SEARCH, v)
    /** P2.5: multi-select mode — выделение нескольких сообщений. Opt-in. */
    suspend fun setMsgMultiSelect(v: Boolean)            = put(Keys.MSG_MULTI_SELECT, v)
    /** P3.5: multi-file upload — до 10 фото за раз. */
    suspend fun setMsgMultiFile(v: Boolean)              = put(Keys.MSG_MULTI_FILE, v)
    /** P3.6: dual send/mic button — state machine. Opt-in. */
    suspend fun setMsgDualButton(v: Boolean)             = put(Keys.MSG_DUAL_BUTTON, v)
    /** P3.2: mute/unmute chat — toggle уведомлений. */
    suspend fun setMsgMute(v: Boolean)                   = put(Keys.MSG_MUTE, v)
    /** P3.1: ChatInfo screen — отдельный экран информации о чате. */
    suspend fun setMsgChatInfo(v: Boolean)               = put(Keys.MSG_CHAT_INFO, v)
    /** P3.4: channel mode — отдельный UX для каналов (broadcast). */
    suspend fun setMsgChannelMode(v: Boolean)            = put(Keys.MSG_CHANNEL_MODE, v)
    /** P3.3: folders system — включение папок диалогов. */
    suspend fun setMsgFolders(v: Boolean)                = put(Keys.MSG_FOLDERS, v)
    /** P3.3: JSON-сериализованный список ChatFolder (source of truth). */
    suspend fun setMsgFoldersData(v: String)             = put(Keys.MSG_FOLDERS_DATA, v)
    /** Fix #276: JSON-массив peer_id закреплённых диалогов (в порядке). */
    suspend fun setPinnedConvsData(v: String)            = put(Keys.PINNED_CONVS_DATA, v)

    // ─── #CALLS-SNAP (2026-09-05): Этап А3 плана «звонки.перенос.план.md» ───
    // Конфигурация сайдбара раздела «Звонки» («Настройка пунктов меню»):
    // CSV "TAB:1,TAB:0" в порядке отображения (TAB — имя CallsTab из
    // :feature:calls); пустая строка = конфигурация по умолчанию.
    // Отдельный ключ ВНЕ Snapshot — чтобы не расширять большой data-класс
    // (FeedScreen передаёт initial-копию целиком, урок themeMonetHybrid):
    // читается отдельным флоу, пишется сеттером.

    /** #CALLS-SNAP: конфигурация сайдбара «Звонков» (CSV, "" = дефолт). */
    val callsSidebarCfg: Flow<String> = ds.data.map { p ->
        // NULL-ЯВНО: отсутствие ключа — тривиальный фолбэк на дефолт
        val raw = p[Keys.CALLS_SIDEBAR_CFG]
        if (raw == null) "" else raw
    }

    /** #CALLS-SNAP: сохранить конфигурацию сайдбара «Звонков». */
    suspend fun setCallsSidebarCfg(v: String)            = put(Keys.CALLS_SIDEBAR_CFG, v)
    /** P3.7: bubble-less дизайн — flat layout сообщений. */
    suspend fun setMsgBubbleless(v: Boolean)              = put(Keys.MSG_BUBBLELESS, v)
    /** P4.2: LongPoll backfill — восстановление пропущенных между сессиями событий. */
    suspend fun setMsgLpBackfill(v: Boolean)              = put(Keys.MSG_LP_BACKFILL, v)
    /** P4.1: LongPoll v14 — lp_version=14, mode=1226 (расширенные поля). */
    suspend fun setMsgLpV14(v: Boolean)                   = put(Keys.MSG_LP_V14, v)
    /** §52.5 Sprint A (P0): Modern Sync API — messages.getDiff (lp_version=21). */
    suspend fun setMsgModernSync(v: Boolean)              = put(Keys.MSG_MODERN_SYNC, v)
    /** P4.4: execute batching — группировка API-вызовов через VKScript. */
    suspend fun setMsgExecuteBatch(v: Boolean)            = put(Keys.MSG_EXECUTE_BATCH, v)
    /** P4.3: WebSocket transport для каналов (stub, недокументировано). */
    suspend fun setMsgWsChannels(v: Boolean)              = put(Keys.MSG_WS_CHANNELS, v)
    /** P4.2: сохранить последний ts LongPoll (для backfill). */
    suspend fun setLpLastTs(v: Long)                      = put(Keys.LP_LAST_TS, v)
    /** P4.2: сохранить последний pts LongPoll (для backfill). */
    suspend fun setLpLastPts(v: Long)                     = put(Keys.LP_LAST_PTS, v)
    /** P5.1: открывать ссылки из чата во внутреннем браузере (WebView). */
    suspend fun setOpenLinksInInternalBrowser(v: Boolean) = put(Keys.OPEN_LINKS_INTERNAL, v)

    suspend fun setCacheSizeMb(v: Long)                     = put(Keys.CACHE_SIZE_MB, v)
    suspend fun setCacheCustomPath(v: String)                = put(Keys.CACHE_CUSTOM_PATH, v)

    suspend fun setMusicDownloadPath(v: String)          = put(Keys.MUSIC_DOWNLOAD_PATH, v)
    suspend fun setVideoDownloadPath(v: String)          = put(Keys.VIDEO_DOWNLOAD_PATH, v)
    suspend fun setMusicHighQuality(v: Boolean)          = put(Keys.MUSIC_HQ, v)
    suspend fun setMusicBackgroundPlay(v: Boolean)       = put(Keys.MUSIC_BG_PLAY, v)
    /** #OFFLINE-TAB: установить формат сохранения аудио (M4A/MP3). */
    suspend fun setAudioFormat(v: AudioFormat)           = put(Keys.AUDIO_FORMAT, v.prefValue)
    suspend fun setAudioQuality(v: AudioQuality)          = put(Keys.AUDIO_QUALITY, v.prefValue)
    /** §42.12 P1 #3: писать MP4 metadata теги. */
    suspend fun setWriteId3Tags(v: Boolean)             = put(Keys.WRITE_ID3_TAGS, v)
    /** §42.12 P2 #8: добавлять тексты из Genius в ©lyr. */
    suspend fun setWriteGeniusLyrics(v: Boolean)        = put(Keys.WRITE_GENIUS_LYRICS, v)
    /** §42.12 P2 #9: промо-комментарий в cmt. */
    suspend fun setWritePromoComment(v: Boolean)        = put(Keys.WRITE_PROMO_COMMENT, v)
    /** §42.12 P3 #11: метод конвертации siren (siren_transcoder/hls_native). */
    suspend fun setAudioConvertMethod(v: String)        = put(Keys.AUDIO_CONVERT_METHOD, v)
    /** §42.12 P1 #5: добавлять "NN. " префикс к имени файла в плейлисте. */
    suspend fun setNumTracksInPlaylist(v: Boolean)      = put(Keys.NUM_TRACKS_IN_PLAYLIST, v)
    suspend fun setVideoPreferredQuality(v: String)      = put(Keys.VIDEO_PREFERRED_QUALITY, v)
    /** #VIDEO-AUTOPLAY: вкл/выкл автовоспроизведения видео при открытии. */
    suspend fun setVideoAutoplay(v: Boolean)            = put(Keys.VIDEO_AUTOPLAY, v)
    /** OK-IMPL-1 (Stage 7): включить/выключить встраивание внешних видео (YouTube/iframe). */
    suspend fun setExternalVideosEnabled(v: Boolean)     = put(Keys.EXTERNAL_VIDEOS_ENABLED, v)
    // Fix #337: редактор панелей — порядок и видимость кнопок.
    suspend fun setSidebarItemsOrder(v: String)           = put(Keys.SIDEBAR_ITEMS_ORDER, v)
    suspend fun setSidebarItemsHidden(v: String)          = put(Keys.SIDEBAR_ITEMS_HIDDEN, v)
    suspend fun setBottomBarItemsOrder(v: String)         = put(Keys.BOTTOMBAR_ITEMS_ORDER, v)
    suspend fun setBottomBarItemsHidden(v: String)        = put(Keys.BOTTOMBAR_ITEMS_HIDDEN, v)
    // #BOTTOM-DEFAULT-4: миграция на новый дефолт нижней панели.
    suspend fun setPanelDefaultsV2(v: Int)                = put(Keys.PANEL_DEFAULTS_V2, v)

    /**
     * #BOTTOM-DEFAULT-4: одноразовая миграция дефолта нижней панели.
     *
     * Старый дефолт: order=["feed","messages","music","video","profile"], hidden=[]
     * → после #SIDEBAR-BOTTOM-UNION visible раздувался до 17 кнопок (normalize
     * добавлял недостающие из canonical).
     *
     * Новый дефолт: order=["profile","messages","music","video"], hidden=[все
     * остальные 13 route].
     *
     * Миграция применяется ТОЛЬКО если пользователь НЕ настраивал панель
     * (ключ BOTTOMBAR_ITEMS_ORDER отсутствует в storage). Если пользователь
     * уже менял порядок/видимость — его настройки сохраняются, просто
     * помечаем PANEL_DEFAULTS_V2=1 чтобы не проверять снова.
     *
     * @return true если миграция применила новые дефолты, false если уже применена
     *         или пользователь настраивал панель сам.
     */
    suspend fun migratePanelDefaultsV2(): Boolean {
        val snap = ds.data.first()
        val cur = snap[Keys.PANEL_DEFAULTS_V2] ?: 0
        if (cur >= 1) return false
        val userOrder = snap[Keys.BOTTOMBAR_ITEMS_ORDER]
        val userHidden = snap[Keys.BOTTOMBAR_ITEMS_HIDDEN]
        // null = ключа нет = пользователь не открывал редактор панелей.
        // Если пользователь менял order ИЛИ hidden — не трогаем.
        val userTouched = userOrder != null || (userHidden != null && userHidden != "[]")
        ds.edit { p ->
            if (!userTouched) {
                p[Keys.BOTTOMBAR_ITEMS_ORDER] = BOTTOMBAR_DEFAULT_ORDER
                p[Keys.BOTTOMBAR_ITEMS_HIDDEN] = BOTTOMBAR_DEFAULT_HIDDEN
            }
            p[Keys.PANEL_DEFAULTS_V2] = 1
        }
        return !userTouched
    }

    /**
     * §42.6 #PUSH-NO-GROUP-DEFAULT: миграция default-режима группировки пушей.
     *
     * До §42.6 default был "category" — 3+ уведомления сворачивались в стопку
     * "N новых лайков". Чтобы увидеть отдельные посты, нужен pinch-out (жест
     * двумя пальцами), который почти никто не знает. Тап по стопке открывал
     * экран уведомлений, а не отдельные посты → пользователь не мог выбрать
     * конкретный пост из группы.
     *
     * Теперь default = "none" — каждое уведомление отдельно, каждое напрямую
     * тапаемое со своим deep-link.
     *
     * Миграция сбрасывает pushGroupingMode на "none" для существующих пользователей,
     * у которых значение было установлено старым default'ом ("category") и они
     * явно не меняли его через UI. Если пользователь сам выбрал "community" или
     * "user" — не трогаем (осознанный выбор).
     *
     * Вызывается из SovaApp.onCreate (как migratePanelDefaultsV2).
     */
    suspend fun migratePushGroupingDefault(): Boolean {
        val snap = ds.data.first()
        val cur = snap[Keys.PUSH_GROUPING_MIGRATED] ?: 0
        if (cur >= 1) return false
        val currentMode = snap[Keys.PUSH_GROUPING_MODE]
        // null = ключа нет = новый пользователь (получит "none" из default).
        // "category" = старый default → сбрасываем на "none".
        // "community"/"user" = явный выбор пользователя → не трогаем.
        val shouldReset = currentMode == null || currentMode == "category"
        ds.edit { p ->
            if (shouldReset) {
                p[Keys.PUSH_GROUPING_MODE] = "none"
            }
            p[Keys.PUSH_GROUPING_MIGRATED] = 1
        }
        return shouldReset
    }

    // Fix #100: Stories settings
    suspend fun setAutoCacheStories(v: Boolean)          = put(Keys.AUTO_CACHE_STORIES, v)
    suspend fun setStoryCacheLimitMb(v: Int)             = put(Keys.STORY_CACHE_LIMIT_MB, v)

    // Fix #110: Audio auto-cache setting
    suspend fun setAutoCacheAudio(v: Boolean)            = put(Keys.AUTO_CACHE_AUDIO, v)

    suspend fun setNetSslPinning(v: Boolean)             = put(Keys.NET_SSL_PINNING, v)
    suspend fun setNetAwayBypass(v: Boolean)             = put(Keys.NET_AWAY_BYPASS, v)
    suspend fun setNetAdBlock(v: Boolean)                = put(Keys.NET_AD_BLOCK, v)
    /** Mobile-web API gateway toggle (web.api.vk.ru). Default: false (api.vk.com). */
    suspend fun setNetUseWebApiGateway(v: Boolean)       = put(Keys.NET_USE_WEB_API_GATEWAY, v)
    suspend fun setNetProxyEnabled(v: Boolean)           = put(Keys.NET_PROXY_ENABLED, v)
    suspend fun setNetProxyHost(v: String)               = put(Keys.NET_PROXY_HOST, v)
    suspend fun setNetProxyPort(v: Int)                  = put(Keys.NET_PROXY_PORT, v)

    suspend fun setLockerEnabled(v: Boolean)             = put(Keys.LOCKER_ENABLED, v)
    suspend fun setLockerPinHash(v: String)              = put(Keys.LOCKER_PIN_HASH, v)
    suspend fun setLockerBiometric(v: Boolean)           = put(Keys.LOCKER_BIOMETRIC, v)
    suspend fun setLockerOnBackground(v: Boolean)        = put(Keys.LOCKER_ON_BACKGROUND, v)

    suspend fun setLastRoute(v: String)                     = put(Keys.LAST_ROUTE, v)

    // Fix #189: Auth Domains Config setters — настраиваемые VK домены.
    suspend fun setAuthOauthHost(v: String)            = put(Keys.AUTH_OAUTH_HOST, v.trim())
    suspend fun setAuthIdHost(v: String)               = put(Keys.AUTH_ID_HOST, v.trim())
    suspend fun setAuthLoginHost(v: String)            = put(Keys.AUTH_LOGIN_HOST, v.trim())
    suspend fun setAuthMobileWebHost(v: String)        = put(Keys.AUTH_MOBILE_WEB_HOST, v.trim())
    suspend fun setAuthApiHost(v: String)              = put(Keys.AUTH_API_HOST, v.trim())
    suspend fun setAuthWebClientId(v: String)          = put(Keys.AUTH_WEB_CLIENT_ID, v.trim())
    suspend fun setAuthForceRevoke(v: Boolean)         = put(Keys.AUTH_FORCE_REVOKE, v)

    /**
     * Fix #302 (Task 2-b): persist cached notification toggle states
     * (JSON map of sn_* keys → booleans). Stored after a successful
     * settingsGeneral.getNotifySettings fetch or after a successful
     * per-key toggle. Read on NotificationsTab re-entry for instant UI.
     */
    suspend fun setNotifyCacheJson(v: String)           = put(Keys.NOTIFY_CACHE_JSON, v)

    // §42 #PUSH-NOTIFICATIONS: setters для push-prefs.
    suspend fun setPushEnabled(v: Boolean)              = put(Keys.PUSH_ENABLED, v)
    suspend fun setPushLikes(v: Boolean)                = put(Keys.PUSH_LIKES, v)
    suspend fun setPushComments(v: Boolean)             = put(Keys.PUSH_COMMENTS, v)
    suspend fun setPushReplies(v: Boolean)              = put(Keys.PUSH_REPLIES, v)
    suspend fun setPushFollows(v: Boolean)              = put(Keys.PUSH_FOLLOWS, v)
    suspend fun setPushMentions(v: Boolean)             = put(Keys.PUSH_MENTIONS, v)
    suspend fun setPushReposts(v: Boolean)              = put(Keys.PUSH_REPOSTS, v)
    suspend fun setPushWall(v: Boolean)                 = put(Keys.PUSH_WALL, v)
    suspend fun setPushGifts(v: Boolean)                = put(Keys.PUSH_GIFTS, v)
    suspend fun setPushOther(v: Boolean)                = put(Keys.PUSH_OTHER, v)
    suspend fun setPushPollingIntervalSec(v: Int)       = put(Keys.PUSH_POLLING_INTERVAL, v)
    suspend fun setPushLastSeenKeys(v: String)          = put(Keys.PUSH_LAST_SEEN_KEYS, v)

    // §42.2 #PUSH-ENHANCED: setters для расширенных настроек.
    suspend fun setPushAutoDismissMs(v: Long)           = put(Keys.PUSH_AUTO_DISMISS_MS, v)
    suspend fun setPushPreviewMode(v: String)           = put(Keys.PUSH_PREVIEW_MODE, v)
    suspend fun setPushPreviewLength(v: Int)            = put(Keys.PUSH_PREVIEW_LENGTH, v)
    suspend fun setPushQuietHoursEnabled(v: Boolean)    = put(Keys.PUSH_QUIET_HOURS_ENABLED, v)
    suspend fun setPushQuietHoursStart(v: Int)          = put(Keys.PUSH_QUIET_HOURS_START, v)
    suspend fun setPushQuietHoursEnd(v: Int)            = put(Keys.PUSH_QUIET_HOURS_END, v)
    suspend fun setPushShowAvatar(v: Boolean)           = put(Keys.PUSH_SHOW_AVATAR, v)
    suspend fun setPushShowBigPicture(v: Boolean)       = put(Keys.PUSH_SHOW_BIG_PICTURE, v)
    suspend fun setPushSoundEnabled(v: Boolean)         = put(Keys.PUSH_SOUND_ENABLED, v)
    suspend fun setPushVibrationEnabled(v: Boolean)     = put(Keys.PUSH_VIBRATION_ENABLED, v)
    suspend fun setPushLedColor(v: Int)                = put(Keys.PUSH_LED_COLOR, v)
    suspend fun setPushGroupingMode(v: String)          = put(Keys.PUSH_GROUPING_MODE, v)
    suspend fun setPushGroupThreshold(v: Int)           = put(Keys.PUSH_GROUP_THRESHOLD, v)
    suspend fun setPushActionButtons(v: Boolean)        = put(Keys.PUSH_ACTION_BUTTONS, v)
    /** §46 #REMOTE-INPUT: setter для кнопки «Ответить». */
    suspend fun setPushReplyButton(v: Boolean)          = put(Keys.PUSH_REPLY_BUTTON, v)
    suspend fun setPushPerUserMuted(v: String)          = put(Keys.PUSH_PER_USER_MUTED, v)
    suspend fun setPushShowDelayMs(v: Long)             = put(Keys.PUSH_SHOW_DELAY_MS, v)
    // §42.3 #PUSH-SOURCE-FILTER: setters для source-фильтра.
    suspend fun setPushFromCommunities(v: Boolean)      = put(Keys.PUSH_FROM_COMMUNITIES, v)
    suspend fun setPushFromUsers(v: Boolean)            = put(Keys.PUSH_FROM_USERS, v)
    // §49.5.1 #SAFETY-NET-ALERTS
    suspend fun setPushSafetyNetAlerts(v: Boolean)      = put(Keys.PUSH_SAFETY_NET_ALERTS, v)
    suspend fun setSafetyNetPollIntervalMin(v: Int)    = put(Keys.SAFETY_NET_POLL_INTERVAL, v)
    // #CALLS: queuev4 credential для звонков (ввод вручную из localStorage).
    suspend fun setCallsQueueKey(v: String)            = put(Keys.CALLS_QUEUE_KEY, v)
    suspend fun setCallsQueueTs(v: Long)               = put(Keys.CALLS_QUEUE_TS, v)
    suspend fun setCallsSessionKey(v: String)          = put(Keys.CALLS_SESSION_KEY, v)
    /** #CALLS: okcdn uid из _okcls_anonymLogin (для userId в WS-URL сигналинга). */
    suspend fun setCallsSessionUid(v: Long)            = put(Keys.CALLS_SESSION_UID, v)
    /** #CALLS: $Ksd-токен (calls_token_with_url) — auth_token для auth.anonymLogin v3. */
    suspend fun setCallsCallToken(v: String)           = put(Keys.CALLS_CALL_TOKEN, v)
    /** #CALLS-VIDEO-RX (Этап 1): kill-switch приёма видео собеседника (default true). */
    suspend fun setCallsVideoRx(v: Boolean)            = put(Keys.CALLS_VIDEO_RX, v)
    // #CALLS-SYMMETRIC: видеозаглушка наружу (sendrecv без камеры).
    suspend fun setCallsVideoTx(v: Boolean)            = put(Keys.CALLS_VIDEO_TX, v)
    // #CALLS-SWDECODE: принудительный программный декодер видео.
    suspend fun setCallsVideoSwDecode(v: Boolean)      = put(Keys.CALLS_VIDEO_SW_DECODE, v)
    /** §1-NOTIF-ARCHIVE: частота email-уведомлений (0=всегда, 1=не чаще раза в день, 2=никогда). */
    suspend fun setEmailNotifyFreq(v: Int)              = put(Keys.EMAIL_NOTIFY_FREQ, v)

    private suspend fun <T> put(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        ds.edit { it[key] = value }
    }

    /** Immutable snapshot of all settings. Collected once on UI start. */
    data class Snapshot(
        // News
        val newsAdsBlocked: Boolean,
        val newsRepostsHidden: Boolean,
        val newsPromoHidden: Boolean,
        // Interface
        val themeDark: Boolean,
        val themeAccentIndex: Int,
        val themeDynamic: Boolean,
        /** #MONET-HYBRID: при true и включённом Material You — accent перекрывает primary/secondary/tertiary. */
        val themeMonetHybrid: Boolean,
        val fontScale: Int,
        /** Fix #224: скорость анимаций интерфейса (0..100%). 0 = выключены (snap). */
        val interfaceAnimSpeed: Int,
        /** Fix #228: масштаб стикер-фото (0..40, % увеличения от оригинала). */
        val stickerPhotoScale: Int,
        /** Fix #237: показ плавающего значка логирования (DraggableLogFab). */
        val showLogFab: Boolean,
        /**
         * #LOG-CATEGORIES (2026-08-04): множество имён ОТКЛЮЧЕННЫХ категорий
         * логов (LogCategory.name).
         * #LOG-CATEGORIES-DEFAULT-CRITICAL (2026-08-05): default =
         * AppLog.NON_CRITICAL_CATEGORY_NAMES (только AUTH+SYSTEM+NETWORK включены).
         * Читается в SovaApp.onCreate → AppLog.applyDisabledCategories().
         */
        val logCategoriesDisabled: Set<String>,
        /** #238: показ FAB «подняться в верх ленты» в FeedScreen (default true). */
        val feedShowScrollFab: Boolean,
        /** #FEED-FILTER-TOGGLE: показывать панель разделов ленты (default true). */
        val feedShowFilter: Boolean,
        /** #NET-SWITCH-POPUP: popup при переключении сети (default true). */
        val netSwitchPopupEnabled: Boolean,
        // Privacy
        val privacyOfflineMode: Boolean,
        val privacyDeviceMask: Boolean,
        val privacyAntiTelemetry: Boolean,
        val privacyHideLastSeen: Boolean,
        // Messages
        val msgDnr: Boolean,
        val msgDnt: Boolean,
        val msgUndelete: Boolean,
        val msgUnedit: Boolean,
        /** P0.1: typing indicator in ChatDetailScreen (LongPoll codes 61/62). */
        val msgTypingIndicator: Boolean,
        /** P0.3: pinned message bar + pin/unpin in context menu. */
        val msgPinBar: Boolean,
        /** #MSG-FAVORITES-TOGGLE: показывать «Избранное» в чатах и шеринг. */
        val msgShowFavorites: Boolean,
        /** P1.3: message grouping — объединение последовательных сообщений. */
        val msgGrouping: Boolean,
        /** P1.1: date separators между сообщениями разных дней. */
        val msgDateSeparators: Boolean,
        /** P1.1: unread divider перед первым непрочитанным. */
        val msgUnreadDivider: Boolean,
        /** P1.1: scroll-to-bottom FAB при прокрутке вверх. */
        val msgScrollFab: Boolean,
        /** P1.2: reply via swipe — свайп для ответа. */
        val msgSwipeReply: Boolean,
        /** P2.6: read receipts (✓/✓✓) — статус прочтения. */
        val msgReadReceipts: Boolean,
        /** P1.4: search + tabs в MessagesScreen. */
        val msgSearch: Boolean,
        /** P2.5: multi-select mode (long-press → «Выбрать»). Opt-in (default false). */
        val msgMultiSelect: Boolean,
        /** P3.5: multi-file upload — до 10 фото за раз (default true). */
        val msgMultiFile: Boolean,
        /** P3.6: dual send/mic button — state machine (EDIT/LOADING/LIMIT/MIC/SUBMIT). Opt-in. */
        val msgDualButton: Boolean,
        /** P3.2: mute/unmute chat — toggle уведомлений (default true). */
        val msgMute: Boolean,
        /** P3.1: ChatInfo screen — отдельный экран с members/media/actions (default true). */
        val msgChatInfo: Boolean,
        /** P3.4: channel mode — отдельный UX для каналов (default true). */
        val msgChannelMode: Boolean,
        /** P3.3: folders system — включение папок диалогов (default false, opt-in). */
        val msgFolders: Boolean,
        /** P3.3: JSON-сериализованный список ChatFolder (source of truth для UI). */
        val msgFoldersData: String,
        /** Fix #276: JSON-массив peer_id закреплённых диалогов (в порядке, source of truth). */
        val pinnedConvsData: String,
        /** P3.7: bubble-less дизайн — flat layout (без Card/bubble), default false. */
        val msgBubbleless: Boolean,
        /** P4.2: LongPoll backfill — восстановление пропущенных между сессиями событий (default false). */
        val msgLpBackfill: Boolean,
        /** P4.1: LongPoll v14 — lp_version=14, mode=1226 (default false, opt-in). */
        val msgLpV14: Boolean,
        /** §52.5 Sprint A (P0): Modern Sync API — messages.getDiff lp_version=21 (default false). */
        val msgModernSync: Boolean,
        /** P4.4: execute batching — группировка API-вызовов через VKScript (default false). */
        val msgExecuteBatch: Boolean,
        /** P4.3: WebSocket transport для каналов (stub, недокументировано, default false). */
        val msgWsChannels: Boolean,
        /** P4.2: последний сохранённый ts LongPoll (для backfill при старте). */
        val lpLastTs: Long,
        /** P4.2: последний сохранённый pts LongPoll (для backfill при старте). */
        val lpLastPts: Long,
        /** P5.1: открывать ссылки из чата во внутреннем браузере (WebView). Default: false. */
        val openLinksInInternalBrowser: Boolean,
        // Cache
        val cacheSizeMb: Long,
        val cacheCustomPath: String,
        // Music
        val musicDownloadPath: String,
        val videoDownloadPath: String,
        val musicHighQuality: Boolean,
        val musicBackgroundPlay: Boolean,
        /** #OFFLINE-TAB: формат сохранения (M4A по умолчанию, MP3 opt-in через ffmpeg-kit). */
        val audioFormat: AudioFormat,
        val audioQuality: AudioQuality,
        /** §42.12 P1 #3: писать MP4 metadata теги (default true). */
        val writeId3Tags: Boolean,
        /** §42.12 P2 #8: добавлять тексты из Genius в ©lyr (default false, opt-in). */
        val writeGeniusLyrics: Boolean,
        /** §42.12 P2 #9: промо-комментарий «Downloaded by PinoK» в cmt (default false). */
        val writePromoComment: Boolean,
        /** §42.12 P3 #11: метод конвертации siren (siren_transcoder/hls_native). */
        val audioConvertMethod: String,
        /** §42.12 P1 #5: добавлять "NN. " префикс к имени файла в плейлисте (default true). */
        val numTracksInPlaylist: Boolean,
        /** Fix #334: предпочтительное качество видео ("auto"/"1080"/"720"/...). */
        val videoPreferredQuality: String,
        /** #VIDEO-AUTOPLAY: автовоспроизведение при открытии (default true). */
        val videoAutoplay: Boolean,
        /**
         * OK-IMPL-1 (Stage 7) + FEED-FIX-4 (#349): включение внешних видео
         * (YouTube/OK iframe/...) И нативного OK-воспроизведения.
         * Default true — пользователь запросил «тумблер включён по умолчанию».
         * VK-видео играет нативно всегда — этот флаг на VK НЕ влияет.
         * При false VideoPlatformRouter показывает placeholder
         * «Внешние видео отключены» вместо OkWebViewPlayer/нативного OK.
         */
        val externalVideosEnabled: Boolean,
        /**
         * Fix #337: редактор панелей. JSON-массив route-строк в порядке отображения.
         *  - sidebarItemsOrder: dynamic-пункты боковой панели (без фикс. Офлайн/Настройки/Выйти).
         *  - bottomBarItemsOrder: все пункты нижней панели.
         * sidebarItemsHidden / bottomBarItemsHidden — JSON-массив скрытых route.
         */
        val sidebarItemsOrder: String,
        val sidebarItemsHidden: String,
        val bottomBarItemsOrder: String,
        val bottomBarItemsHidden: String,
        // Stories (Fix #100)
        val autoCacheStories: Boolean,
        val storyCacheLimitMb: Int,
        // Audio auto-cache (Fix #110)
        val autoCacheAudio: Boolean,
        // Network
        val netSslPinning: Boolean,
        val netAwayBypass: Boolean,
        val netAdBlock: Boolean,
        /** Mobile-web API gateway (web.api.vk.ru). See VKEndpoints.WEB_API_HOST. */
        val netUseWebApiGateway: Boolean,
        val netProxyEnabled: Boolean,
        val netProxyHost: String,
        val netProxyPort: Int,
        // Locker
        val lockerEnabled: Boolean,
        val lockerPinHash: String,
        val lockerBiometric: Boolean,
        val lockerOnBackground: Boolean,
        // Navigation
        val lastRoute: String,
        // Fix #189: Auth Domains Config — настраиваемые VK домены.
        // Хосты без scheme (например "oauth.vk.com" или "oauth.vk.ru").
        // Scheme добавляется в AuthDomainsConfig при формировании URL.
        val authOauthHost: String,
        val authIdHost: String,
        val authLoginHost: String,
        val authMobileWebHost: String,
        val authApiHost: String,
        val authWebClientId: String,
        val authForceRevoke: Boolean,
        /**
         * Fix #302 (Task 2-b): JSON-кэш состояний sn_* notification toggles
         * из settingsGeneral.getNotifySettings(page="notify"). Формат:
         * `{"sn_messages":true,"sn_chats":false,...}`. Пустая строка = нет
         * кэша (используются defaults из NOTIFY_DEFAULTS в SettingsScreen).
         */
        val notifyCacheJson: String,
        /** §1-NOTIF-ARCHIVE: частота email-уведомлений (0=всегда, 1=не чаще раза в день, 2=никогда). */
        val emailNotifyFreq: Int,

        /**
         * §42 #PUSH-NOTIFICATIONS: локальные push-уведомления для VK-событий
         * (лайки/комментарии/репосты/ответы/подписки/упоминания/подарки/стена).
         *
         * В отличие от sn_* toggles (server-side, управляют что VK шлёт через FCM),
         * эти — client-side: управляют показывает ли PinoK system notification
         * когда NotificationsPoller находит новые элементы в notifications.getRedesign.
         *
         * pushEnabled — глобальный master toggle. Если false — poller не запускается.
         * Per-category toggles — проверяются при показе каждого уведомления.
         * pushPollingIntervalSec — интервал fallback-опроса (60-600с, default 120).
         * pushLastSeenKeys — CSV последних увиденных uniqueKey (для diff, max 100).
         */
        val pushEnabled: Boolean,
        val pushLikes: Boolean,
        val pushComments: Boolean,
        val pushReplies: Boolean,
        val pushFollows: Boolean,
        val pushMentions: Boolean,
        val pushReposts: Boolean,
        val pushWall: Boolean,
        val pushGifts: Boolean,
        val pushOther: Boolean,
        val pushPollingIntervalSec: Int,
        val pushLastSeenKeys: String,

        /**
         * §42.2 #PUSH-ENHANCED: расширенные настройки отображения и группировки.
         *
         * Проблема (скриншот 20260802_221731): 25 уведомлений «Новое уведомление (N)»
         * заливают шторку — нет группировки, нет summary, не сворачиваются.
         *
         * Решение: configurable grouping + group summary (InboxStyle) + privacy
         * preview modes + auto-dismiss + quiet hours + avatar + action buttons.
         */
        /** Авто-скрытие: 0 = никогда, иначе ms до auto-cancel (5000/10000/30000/60000/1800000). */
        val pushAutoDismissMs: Long,
        /** Режим превью: "full" (отправитель+текст) / "sender_only" (только отправитель) / "hidden" (скрыть всё). */
        val pushPreviewMode: String,
        /** Длина превью текста (0/40/80/160 символов). */
        val pushPreviewLength: Int,
        /** Quiet hours: не показывать push в заданное окно (default false). */
        val pushQuietHoursEnabled: Boolean,
        /** Начало quiet hours в минутах от полуночи (default 1320 = 22:00). */
        val pushQuietHoursStart: Int,
        /** Конец quiet hours в минутах от полуночи (default 480 = 08:00). */
        val pushQuietHoursEnd: Int,
        /** Показывать аватар отправителя как large icon (default true). */
        val pushShowAvatar: Boolean,
        /** Показывать BigPicture для фото-уведомлений (default true). */
        val pushShowBigPicture: Boolean,
        /** Звук уведомления (default true; false = silent channel). */
        val pushSoundEnabled: Boolean,
        /** Вибрация (default true). */
        val pushVibrationEnabled: Boolean,
        /** Цвет LED: 0 = системный, иначе ARGB int. */
        val pushLedColor: Int,
        /**
         * Режим группировки:
         * - "none": каждое уведомление отдельно (старое поведение).
         * - "category": по типу (лайки вместе, комментарии вместе) — default.
         * - "community": по сообществу/владельцу родительского объекта.
         * - "user": по отправителю (feedbackIds.first()).
         */
        val pushGroupingMode: String,
        /** Порог сворачивания: при >= N уведомлений в группе — показываем summary (InboxStyle). */
        val pushGroupThreshold: Int,
        /** Показывать кнопку «Прочитать» в уведомлении (default true). */
        val pushActionButtons: Boolean,
        /** §46 #REMOTE-INPUT: показывать кнопку «Ответить» с RemoteInput (default true). */
        val pushReplyButton: Boolean,
        /** CSV заглушённых user IDs (push от них не показываются). */
        val pushPerUserMuted: String,
        /** Задержка перед показом (grace period ms, 0 = сразу). */
        val pushShowDelayMs: Long,
        /**
         * §42.3 #PUSH-SOURCE-FILTER: фильтр по источнику уведомления.
         *
         * pushFromCommunities — показывать уведомления где parentOwnerId < 0
         *   (события на контенте сообществ: посты групп, фото групп, ...).
         * pushFromUsers — показывать где parentOwnerId > 0 (контент пользователей).
         *
         * Оба default true. Если выключить pushFromCommunities — не будут
         * показываться лайки/комментарии на постах сообществ и т.п.
         */
        val pushFromCommunities: Boolean,
        val pushFromUsers: Boolean,
        /** §49.5.1 #SAFETY-NET-ALERTS: показывать уведомления о подозрительных входах. */
        val pushSafetyNetAlerts: Boolean,
        /** §49.5.1: интервал polling SecurityAlerts (минуты, default 10). */
        val safetyNetPollIntervalMin: Int,
        // #CALLS: queuev4 credential для звонков (можно ввести вручную из localStorage).
        val callsQueueKey: String,
        val callsQueueTs: Long,
        /** #CALLS: vchat session_key (из localStorage _okcls_anonymLogin). Fallback для vchat API. */
        val callsSessionKey: String,
        /** #CALLS: okcdn uid (из _okcls_anonymLogin) — userId в WS URL сигналинга. */
        val callsSessionUid: Long,
        /** #CALLS: $Ksd-токен (calls_token_with_url) — auth_token для auth.anonymLogin v3. */
        val callsCallToken: String,
        /** #CALLS-VIDEO-RX (Этап 1, CALLS_MAP §11.2.5): приём видео собеседника
         *  во входящем видео-звонке (recvonly). Kill-switch: при нативном краше
         *  декодера на конкретном устройстве выключается в Настройки → Звонки
         *  БЕЗ пересборки (fallback — прежнее поведение a=inactive). Default true. */
        val callsVideoRx: Boolean,
        /** #CALLS-SYMMETRIC (01.09, Этап 2-заготовка): отправлять чёрную видеозаглушку
         *  (320×180@10fps, БЕЗ камеры и разрешения CAMERA) — answer m=video становится
         *  sendrecv, звонок симметричен как у офиц. клиента. Гипотеза: официальный
         *  клиент в same-NAT Wi-Fi не начинает ICE-проверки против recvonly-ответа.
         *  Default true (первый шаг Этапа 2; при включении камеры заменится на неё). */
        val callsVideoTx: Boolean,
        /** #CALLS-SWDECODE (01.09): принудительный SoftwareVideoDecoderFactory вместо
         *  DefaultVideoDecoderFactory — решающая диагностика чёрного экрана при
         *  декодирующемся видео (HW-текстуры MTK vs композиция). Вступает после
         *  перезапуска приложения (фабрика создаётся один раз на процесс).
         *  Default false. */
        val callsVideoSwDecode: Boolean,
    )

    private object Keys {
        // News
        val NEWS_ADS_BLOCKED    = booleanPreferencesKey("news_ads_blocked")
        val NEWS_REPOSTS_HIDDEN = booleanPreferencesKey("news_reposts_hidden")
        val NEWS_PROMO_HIDDEN   = booleanPreferencesKey("news_promo_hidden")
        // Interface
        val THEME_DARK          = booleanPreferencesKey("theme_dark")
        val THEME_ACCENT_INDEX  = intPreferencesKey("theme_accent_index")
        val THEME_DYNAMIC       = booleanPreferencesKey("theme_dynamic")
        val THEME_MONET_HYBRID  = booleanPreferencesKey("theme_monet_hybrid")
        val FONT_SCALE          = intPreferencesKey("font_scale")
        val INTERFACE_ANIM_SPEED= intPreferencesKey("interface_anim_speed")
        val STICKER_PHOTO_SCALE = intPreferencesKey("sticker_photo_scale")
        // Fix #237: показ плавающего значка логирования.
        val SHOW_LOG_FAB        = booleanPreferencesKey("show_log_fab")
        // #LOG-CATEGORIES (2026-08-04): множество имён категорий логов
        // (LogCategory.name), которые пользователь ОТКЛЮЧИЛ в Settings → Log.
        // #LOG-CATEGORIES-DEFAULT-CRITICAL (2026-08-05): default =
        // AppLog.NON_CRITICAL_CATEGORY_NAMES — включены только AUTH+SYSTEM+NETWORK.
        val LOG_CATEGORIES_DISABLED = stringSetPreferencesKey("log_categories_disabled")
        // #238: показ FAB «подняться в верх ленты» в FeedScreen.
        val FEED_SHOW_SCROLL_FAB = booleanPreferencesKey("feed_show_scroll_fab")
        // #FEED-FILTER-TOGGLE: показывать панель разделов ленты.
        val FEED_SHOW_FILTER = booleanPreferencesKey("feed_show_filter")
        // #NET-SWITCH-POPUP: popup при переключении сети (default true).
        val NET_SWITCH_POPUP_ENABLED = booleanPreferencesKey("net_switch_popup_enabled")
        // Privacy
        val PRIVACY_OFFLINE       = booleanPreferencesKey("privacy_offline")
        val PRIVACY_DEVICE_MASK   = booleanPreferencesKey("privacy_device_mask")
        val PRIVACY_ANTI_TELEMETRY= booleanPreferencesKey("privacy_anti_telemetry")
        val PRIVACY_HIDE_LAST_SEEN= booleanPreferencesKey("privacy_hide_last_seen")
        // Messages
        val MSG_DNR          = booleanPreferencesKey("msg_dnr")
        val MSG_DNT          = booleanPreferencesKey("msg_dnt")
        val MSG_UNDELETE     = booleanPreferencesKey("msg_undelete")
        val MSG_UNEDIT       = booleanPreferencesKey("msg_unedit")
        val MSG_TYPING_INDICATOR = booleanPreferencesKey("msg_typing_indicator")
        val MSG_PIN_BAR          = booleanPreferencesKey("msg_pin_bar")
        val MSG_SHOW_FAVORITES  = booleanPreferencesKey("msg_show_favorites")
        val MSG_GROUPING         = booleanPreferencesKey("msg_grouping")
        val MSG_DATE_SEPARATORS  = booleanPreferencesKey("msg_date_separators")
        val MSG_UNREAD_DIVIDER   = booleanPreferencesKey("msg_unread_divider")
        val MSG_SCROLL_FAB       = booleanPreferencesKey("msg_scroll_fab")
        val MSG_SWIPE_REPLY      = booleanPreferencesKey("msg_swipe_reply")
        val MSG_READ_RECEIPTS    = booleanPreferencesKey("msg_read_receipts")
        val MSG_SEARCH            = booleanPreferencesKey("msg_search")
        val MSG_MULTI_SELECT      = booleanPreferencesKey("msg_multi_select")
        val MSG_MULTI_FILE        = booleanPreferencesKey("msg_multi_file")
        val MSG_DUAL_BUTTON       = booleanPreferencesKey("msg_dual_button")
        val MSG_MUTE              = booleanPreferencesKey("msg_mute")
        val MSG_CHAT_INFO         = booleanPreferencesKey("msg_chat_info")
        val MSG_CHANNEL_MODE      = booleanPreferencesKey("msg_channel_mode")
        val MSG_FOLDERS           = booleanPreferencesKey("msg_folders")
        val MSG_FOLDERS_DATA      = stringPreferencesKey("msg_folders_data")
        // Fix #276: локальное хранилище закреплённых диалогов (JSON array of peer_id).
        val PINNED_CONVS_DATA     = stringPreferencesKey("pinned_convs_data")
        // #CALLS-SNAP (2026-09-05): конфигурация сайдбара «Звонков» (Этап А3)
        val CALLS_SIDEBAR_CFG     = stringPreferencesKey("calls_sidebar_cfg")
        val MSG_BUBBLELESS        = booleanPreferencesKey("msg_bubbleless")
        // P4.2: LongPoll backfill — persistence ts/pts между сессиями
        val MSG_LP_BACKFILL        = booleanPreferencesKey("msg_lp_backfill")
        // P4.1: LongPoll v14 — lp_version=14, mode=1226
        val MSG_LP_V14             = booleanPreferencesKey("msg_lp_v14")
        // §52.5 Sprint A (P0): Modern Sync API — messages.getDiff (lp_version=21)
        val MSG_MODERN_SYNC        = booleanPreferencesKey("msg_modern_sync")
        // P4.4: execute batching — группировка API-вызовов
        val MSG_EXECUTE_BATCH      = booleanPreferencesKey("msg_execute_batch")
        // P4.3: WebSocket transport для каналов (stub)
        val MSG_WS_CHANNELS        = booleanPreferencesKey("msg_ws_channels")
        val LP_LAST_TS             = longPreferencesKey("lp_last_ts")
        val LP_LAST_PTS            = longPreferencesKey("lp_last_pts")
        // P5.1: открытие ссылок из чата во внутреннем браузере (WebView).
        val OPEN_LINKS_INTERNAL    = booleanPreferencesKey("open_links_internal_browser")
        // Cache
        val CACHE_SIZE_MB     = longPreferencesKey("cache_size_mb")
        val CACHE_CUSTOM_PATH = stringPreferencesKey("cache_custom_path")
        // Music
        val MUSIC_DOWNLOAD_PATH = stringPreferencesKey("music_download_path")
        val VIDEO_DOWNLOAD_PATH = stringPreferencesKey("video_download_path")
        val MUSIC_HQ            = booleanPreferencesKey("music_hq")
        val MUSIC_BG_PLAY       = booleanPreferencesKey("music_bg_play")
        // #OFFLINE-TAB: формат сохранения аудио ("m4a" | "mp3"). Default: "m4a".
        val AUDIO_FORMAT        = stringPreferencesKey("audio_format")
        val AUDIO_QUALITY       = stringPreferencesKey("audio_quality")
        // §42.12 P1 #3: писать MP4 metadata теги (©nam/©ART/©alb/©too). Default: true.
        val WRITE_ID3_TAGS      = booleanPreferencesKey("write_id3_tags")
        // §42.12 P2 #8: добавлять тексты из Genius в ©lyr. Default: false (opt-in).
        val WRITE_GENIUS_LYRICS = booleanPreferencesKey("write_genius_lyrics")
        // §42.12 P2 #9: промо-комментарий в cmt. Default: false.
        val WRITE_PROMO_COMMENT = booleanPreferencesKey("write_promo_comment")
        // §42.12 P3 #11: метод конвертации siren ("siren_transcoder" | "hls_native").
        val AUDIO_CONVERT_METHOD = stringPreferencesKey("audio_convert_method")
        // §42.12 P1 #5: добавлять "NN. " префикс к имени файла в плейлисте. Default: true.
        val NUM_TRACKS_IN_PLAYLIST = booleanPreferencesKey("num_tracks_in_playlist")
        val VIDEO_PREFERRED_QUALITY = stringPreferencesKey("video_preferred_quality")
        // #VIDEO-AUTOPLAY: автовоспроизведение видео при открытии плеера.
        val VIDEO_AUTOPLAY         = booleanPreferencesKey("video_autoplay")
        // OK-IMPL-1 (Stage 7): включение внешних видео (YouTube/OK iframe).
        val EXTERNAL_VIDEOS_ENABLED = booleanPreferencesKey("external_videos_enabled")
        // Fix #337: редактор панелей — порядок и видимость кнопок.
        val SIDEBAR_ITEMS_ORDER   = stringPreferencesKey("sidebar_items_order")
        val SIDEBAR_ITEMS_HIDDEN  = stringPreferencesKey("sidebar_items_hidden")
        val BOTTOMBAR_ITEMS_ORDER = stringPreferencesKey("bottombar_items_order")
        val BOTTOMBAR_ITEMS_HIDDEN = stringPreferencesKey("bottombar_items_hidden")
        // #BOTTOM-DEFAULT-4: миграция на новый дефолт (4 кнопки: Профиль,
        // Сообщения, Музыка, Видео). 0 = не применена, 1 = применена.
        val PANEL_DEFAULTS_V2 = intPreferencesKey("panel_defaults_v2")

        // Stories (Fix #100)
        val AUTO_CACHE_STORIES   = booleanPreferencesKey("auto_cache_stories")
        val STORY_CACHE_LIMIT_MB = intPreferencesKey("story_cache_limit_mb")
        // Audio auto-cache (Fix #110)
        val AUTO_CACHE_AUDIO     = booleanPreferencesKey("auto_cache_audio")
        // Network
        val NET_SSL_PINNING   = booleanPreferencesKey("net_ssl_pinning")
        val NET_AWAY_BYPASS   = booleanPreferencesKey("net_away_bypass")
        val NET_AD_BLOCK      = booleanPreferencesKey("net_ad_block")
        val NET_USE_WEB_API_GATEWAY = booleanPreferencesKey("net_use_web_api_gateway")
        val NET_PROXY_ENABLED = booleanPreferencesKey("net_proxy_enabled")
        val NET_PROXY_HOST    = stringPreferencesKey("net_proxy_host")
        val NET_PROXY_PORT    = intPreferencesKey("net_proxy_port")
        // Locker
        val LOCKER_ENABLED      = booleanPreferencesKey("locker_enabled")
        val LOCKER_PIN_HASH     = stringPreferencesKey("locker_pin_hash")
        val LOCKER_BIOMETRIC    = booleanPreferencesKey("locker_biometric")
        val LOCKER_ON_BACKGROUND= booleanPreferencesKey("locker_on_background")
        // Navigation
        val LAST_ROUTE           = stringPreferencesKey("last_route")
        // Fix #189: Auth Domains Config — настраиваемые VK домены.
        val AUTH_OAUTH_HOST      = stringPreferencesKey("auth_oauth_host")
        val AUTH_ID_HOST         = stringPreferencesKey("auth_id_host")
        val AUTH_LOGIN_HOST      = stringPreferencesKey("auth_login_host")
        val AUTH_MOBILE_WEB_HOST = stringPreferencesKey("auth_mobile_web_host")
        val AUTH_API_HOST        = stringPreferencesKey("auth_api_host")
        val AUTH_WEB_CLIENT_ID   = stringPreferencesKey("auth_web_client_id")
        val AUTH_FORCE_REVOKE    = booleanPreferencesKey("auth_force_revoke")
        // Fix #302 (Task 2-b): кэш sn_* notification toggles (JSON map).
        val NOTIFY_CACHE_JSON    = stringPreferencesKey("notify_cache_json")
        // §1-NOTIF-ARCHIVE: частота email-уведомлений (0=всегда, 1=не чаще раза в день, 2=никогда).
        val EMAIL_NOTIFY_FREQ    = intPreferencesKey("email_notify_freq")

        // §42 #PUSH-NOTIFICATIONS: локальные push-уведомления.
        val PUSH_ENABLED            = booleanPreferencesKey("push_enabled")
        val PUSH_LIKES              = booleanPreferencesKey("push_likes")
        val PUSH_COMMENTS           = booleanPreferencesKey("push_comments")
        val PUSH_REPLIES            = booleanPreferencesKey("push_replies")
        val PUSH_FOLLOWS            = booleanPreferencesKey("push_follows")
        val PUSH_MENTIONS           = booleanPreferencesKey("push_mentions")
        val PUSH_REPOSTS            = booleanPreferencesKey("push_reposts")
        val PUSH_WALL               = booleanPreferencesKey("push_wall")
        val PUSH_GIFTS              = booleanPreferencesKey("push_gifts")
        val PUSH_OTHER              = booleanPreferencesKey("push_other")
        val PUSH_POLLING_INTERVAL   = intPreferencesKey("push_polling_interval_sec")
        val PUSH_LAST_SEEN_KEYS     = stringPreferencesKey("push_last_seen_keys")

        // §42.2 #PUSH-ENHANCED: расширенные настройки.
        val PUSH_AUTO_DISMISS_MS     = longPreferencesKey("push_auto_dismiss_ms")
        val PUSH_PREVIEW_MODE       = stringPreferencesKey("push_preview_mode")
        val PUSH_PREVIEW_LENGTH     = intPreferencesKey("push_preview_length")
        val PUSH_QUIET_HOURS_ENABLED= booleanPreferencesKey("push_quiet_hours_enabled")
        val PUSH_QUIET_HOURS_START  = intPreferencesKey("push_quiet_hours_start")
        val PUSH_QUIET_HOURS_END    = intPreferencesKey("push_quiet_hours_end")
        val PUSH_SHOW_AVATAR        = booleanPreferencesKey("push_show_avatar")
        val PUSH_SHOW_BIG_PICTURE   = booleanPreferencesKey("push_show_big_picture")
        val PUSH_SOUND_ENABLED      = booleanPreferencesKey("push_sound_enabled")
        val PUSH_VIBRATION_ENABLED  = booleanPreferencesKey("push_vibration_enabled")
        val PUSH_LED_COLOR          = intPreferencesKey("push_led_color")
        val PUSH_GROUPING_MODE      = stringPreferencesKey("push_grouping_mode")
        val PUSH_GROUP_THRESHOLD    = intPreferencesKey("push_group_threshold")
        // §42.6 #PUSH-NO-GROUP-DEFAULT: флаг one-time миграции (0→1).
        val PUSH_GROUPING_MIGRATED  = intPreferencesKey("push_grouping_migrated")
        val PUSH_ACTION_BUTTONS     = booleanPreferencesKey("push_action_buttons")
        // §46 #REMOTE-INPUT: кнопка «Ответить» с RemoteInput.
        val PUSH_REPLY_BUTTON       = booleanPreferencesKey("push_reply_button")
        val PUSH_PER_USER_MUTED     = stringPreferencesKey("push_per_user_muted")
        val PUSH_SHOW_DELAY_MS      = longPreferencesKey("push_show_delay_ms")
        // §42.3 #PUSH-SOURCE-FILTER: фильтр по источнику.
        val PUSH_FROM_COMMUNITIES   = booleanPreferencesKey("push_from_communities")
        // §49.5.1 #SAFETY-NET-ALERTS (2026-08-04)
        val PUSH_SAFETY_NET_ALERTS  = booleanPreferencesKey("push_safety_net_alerts")
        val SAFETY_NET_POLL_INTERVAL = intPreferencesKey("safety_net_poll_interval_min")
        val PUSH_FROM_USERS         = booleanPreferencesKey("push_from_users")
        // #CALLS: queuev4 credential для звонков.
        val CALLS_QUEUE_KEY         = stringPreferencesKey("calls_queue_key")
        val CALLS_QUEUE_TS          = longPreferencesKey("calls_queue_ts")
        val CALLS_SESSION_KEY       = stringPreferencesKey("calls_session_key")
        val CALLS_SESSION_UID       = longPreferencesKey("calls_session_uid")
        val CALLS_CALL_TOKEN        = stringPreferencesKey("calls_call_token")
        /** #CALLS-VIDEO-RX (Этап 1): приём видео собеседника (kill-switch краша декодера). */
        val CALLS_VIDEO_RX          = booleanPreferencesKey("calls_video_rx")
        val CALLS_VIDEO_TX          = booleanPreferencesKey("calls_video_tx")
        val CALLS_VIDEO_SW_DECODE   = booleanPreferencesKey("calls_video_sw_decode")
    }

    // Fix #189: defaults для Auth Domains Config.
    // Вынесены в companion чтобы быть доступными из AuthDomainsConfig без ссылки на Snapshot.
    companion object {
        const val AUTH_OAUTH_HOST_DEFAULT      = "oauth.vk.com"
        const val AUTH_ID_HOST_DEFAULT         = "id.vk.com"
        const val AUTH_LOGIN_HOST_DEFAULT      = "login.vk.com"
        const val AUTH_MOBILE_WEB_HOST_DEFAULT = "m.vk.ru"
        const val AUTH_API_HOST_DEFAULT        = "api.vk.com"
        const val AUTH_WEB_CLIENT_ID_DEFAULT   = "6287487"  // vk.com desktop web

        // Fix #337: дефолтный порядок пунктов панелей (JSON-массивы route).
        // Совпадает с текущими dockScreens / drawerScreens в SovaNavHost —
        // пользователь не видит изменений до первого входа в «Редактор панелей».
        // Sidebar: только dynamic-пункты (без фикс. Офлайн/Настройки/Выйти).
        const val SIDEBAR_DEFAULT_ORDER =
            """["friends","groups","photos","search","bookmarks","documents","calls_history","clips","services","notifications","logs"]"""
        // #BOTTOM-DEFAULT-4: по умолчанию на нижней панели 4 кнопки в порядке:
        // Профиль → Сообщения → Музыка → Видео (как просил пользователь).
        const val BOTTOMBAR_DEFAULT_ORDER =
            """["profile","messages","music","video"]"""
        // #BOTTOM-DEFAULT-4: все остальные пункты скрыты по умолчанию.
        // Пользователь может включить их через «Редактор панелей» — тогда
        // нижняя панель станет прокручиваемой (см. #BOTTOM-SCROLL в SovaNavHost).
        const val BOTTOMBAR_DEFAULT_HIDDEN =
            """["feed","friends","groups","photos","search","bookmarks","documents","clips","services","notifications","logs","offline_manager","equalizer"]"""
    }
}

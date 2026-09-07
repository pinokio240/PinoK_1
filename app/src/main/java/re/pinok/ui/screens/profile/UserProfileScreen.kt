// File: ui/screens/profile/UserProfileScreen.kt
package re.pinok.ui.screens.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Call
// П-7-AB: Flag — пункт «Пожаловаться» в «⋯»-меню записи чужой стены.
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Post
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.components.ShareSheet
import re.pinok.util.AppLog

/**
 * Sprint 1, P0-2 (#74): Экран чужого профиля.
 *
 * Открывается тапом по автору поста в ленте (если `fromId > 0`), по другу в
 * списке друзей, по собеседнику в чате. Показывает:
 *  — Шапку профиля (аватар, имя, статус, online-индикатор) — [ProfileHeader]
 *  — Счётчики (друзья, подписчики, фото, видео) — [CountersRow]
 *  — Действия: «Написать» (открывает ChatDetailScreen) и «Добавить в друзья»
 *    (вызывает `friends.add` с Toast-фидбеком)
 *  — Стену пользователя (`wall.get(ownerId=userId, count=20)`) — [WallPostCard]
 *
 * Источник данных: `users.get(user_ids=userId, fields=photo_100,photo_200,
 * photo_400,online,last_seen,status,verified,counters,bdate,city,country,
 * followers_count,common_count)` через [VKApiClient.usersGetFull].
 *
 * В отличие от [ProfileScreen] (свой профиль), здесь НЕТ кнопки «Выйти» и
 * настроек аккаунта — только просмотр + 2 действия.
 *
 * Переиспользует public composables из ProfileScreen.kt: [ProfileHeader],
 * [CountersRow], [WallPostCard] — чтобы не дублировать UI-логику.
 *
 * Этап П-2 (PROFILE-P2-3b): поверх «Написать»/«В друзья» добавлены:
 *  — «Подписаться»/«Отписаться» (usersSubscribe/usersUnsubscribe; начальное
 *    состояние из UserProfile.isSubscribed; для друга не показывается —
 *    не дублирует «В друзья»);
 *  — «Позвонить» в TopAppBar — nullable [onCallClick] от хоста (CallStarter-
 *    паттерн FriendsScreen: null → кнопка не рендерится);
 *  — «⋯»-меню TopAppBar: пожаловаться (users.report), скопировать ссылку
 *    (domain/screen_name), закладки (fave.addUser/removeUser, состояние из
 *    is_favorite), скрыть/вернуть в ленту (newsfeed.addBan/deleteBan —
 *    состояние локальное: is_hidden_from_feed модель не парсит);
 *  — секция «Подарки» над лентой (gifts.get, count=9).
 *
 *  П-7-AB (остаток правой колонки веба — профиль.этап-П5.решение.md §3):
 *  — чип «Подписчики» в [CountersRow] кликабелен → список подписчиков
 *    СОБЕСЕДНИКА (FollowersSubscriptionsScreen, mode=followers, userId=p.id);
 *  — строка «Подписки» ([SubscriptionsEntryRow]) → подписки собеседника
 *    (mode=subscriptions); счётчик — параллельный добор
 *    users.getSubscriptions (как gifts); чипа «Подписки» в CountersRow нет —
 *    поля subscriptions в модели UserProfile.Counters нет (модель править
 *    запрещено), строка — как в своём профиле;
 *  — ЧЕСТНОЕ ОТКЛОНЕНИЕ: чип «Друзья» на чужом профиле НЕ кликабелен —
 *    существующий Screen.Friends глобальный (без userId) и показывает СВОИХ
 *    друзей: переход вёл бы к подмене списка собеседника своим; экрана
 *    «друзья пользователя X» в репо нет;
 *  — «Пожаловаться» на записи чужой стены ([WallPostCard] showReport=!isSelf):
 *    wall.markAsSpam с AlertDialog-подтверждением; manage-пункты П-6а по-прежнему
 *    не активируются на чужой стене (showActions=false — меню только с жалобой).
 *
 *  #OPVK-EXTRACT (Task 3-c, идея OpenVK AboutProfileLayout — AGPL, только
 *  семантика «собирать label/value ТОЛЬКО из непустых значений», не код):
 *  секция «Информация» ([ProfileInfoSection]) над «Подарками» — карточка со
 *  строками «О себе»/«Деятельность»/«Интересы»/«Любимая музыка»/
 *  «Любимые фильмы»/«Любимые книги» ([profileInfoRows]). Никаких новых
 *  VKA-вызовов: поля уже приходят в usersGetFull→usersGetFullExtended
 *  (fields профиля) и парсятся в UserProfile. bdate/city НЕ дублируются —
 *  их уже рисует [ProfileHeader] («День рождения»/«Город»); строка
 *  «Любимые цитаты» НЕ рисуется — поле quotes запрашивается в fields, но в
 *  модель UserProfile не парсится (модель вне зоны правок) — no-stub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: Long,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit = {},
    onMessageClick: (peerId: Long, title: String, photo: String?) -> Unit = { _, _, _ -> },
    onPostClick: (Post) -> Unit = {},
    // Шаг 5 (#32e): тап по комментарию поста → PostDetailScreen.
    onCommentClick: (Post) -> Unit = {},
    // Этап П-2: «Позвонить» — #ARCH-CONTAINERS (Этап 1.4): хост передаёт
    // колбэк ТОЛЬКО если в реестре есть CallStarter (SovaNavHost.callClick);
    // null → кнопка НЕ рендерится (тот же контракт, что FriendsScreen/FeedScreen).
    onCallClick: ((peerId: Long, title: String, photo: String?) -> Unit)? = null,
    // П-7-AB: тапы правой колонки веба с userId собеседника: чип «Подписчики»
    // → список подписчиков; строка «Подписки» → подписки. «Друзья» не проводим
    // (глобальный Screen.Friends показывает СВОИХ друзей — отклонение в KDoc).
    onFollowersClick: (Long) -> Unit = {},
    onSubscriptionsClick: (Long) -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var friendActionInProgress by remember { mutableStateOf(false) }
    var isFriend by remember { mutableStateOf(false) }
    // Fix #86: pull-to-refresh + infinite scroll стены пользователя.
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val photoViewerState = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // Sprint 2, P1-3 (#90): диалог репоста.
    val repostPost = remember { mutableStateOf<Post?>(null) }

    // Этап П-2 (PROFILE-P2-3b): состояние действий чужого профиля.
    var profileMenuExpanded by remember { mutableStateOf(false) }
    // Начальные значения выставляются из профиля (is_subscribed / is_favorite).
    var subscribed by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    // is_hidden_from_feed модель UserProfile не парсит — состояние локальное
    // (сессионное), сбрасывается при перезагрузке экрана.
    var hiddenFromFeed by remember { mutableStateOf(false) }
    var subscribeInProgress by remember { mutableStateOf(false) }
    var gifts by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    // П-7-AB: счётчик «Подписок» собеседника (users.getSubscriptions →
    // followSubscriptionsTotal, параллельный добор как gifts); не распознан →
    // строка «Подписки» без числа (счётчик не имитируется).
    var subscriptionsCount by remember { mutableStateOf<Int?>(null) }
    // П-7-AB: «Пожаловаться» на запись чужой стены (wall.markAsSpam).
    val reportingPost = remember { mutableStateOf<Post?>(null) }
    var reportInFlight by remember { mutableStateOf(false) }

    // Свой профиль? Если да — не показываем «Добавить в друзья».
    val currentUserId = app.exchangeAuthRepository.userId()
    val isSelf = userId == currentUserId

    LaunchedEffect(userId) {
        scope.launch {
            loading = true
            errorText = null
            try {
                val p = app.apiClient.usersGetFull(userId)
                profile = p
                if (p != null) {
                    // #FRIEND-COLOR: инициализируем isFriend из profile.friendStatus.
                    // VK friend_status: 0=не друг, 1=заявка отправлена мной,
                    // 2=входящая заявка, 3=друг (взаимная дружба).
                    // Кнопка "Удалить" показывается только если status==3.
                    // Раньше isFriend всегда=false при загрузке — кнопка показывала
                    // "В друзья" даже для действительных друзей.
                    isFriend = (p.friendStatus == 3)
                    // Этап П-2: начальные состояния подписки/закладок из users.get
                    // (is_subscribed / is_favorite парсятся в UserProfile с П-0).
                    subscribed = (p.isSubscribed == 1)
                    isBookmarked = (p.isFavorite == 1)
                    val wall = app.apiClient.wallGet(ownerId = userId, count = 20)
                    posts = wall
                        .filter { it.id > 0 && it.ownerId != 0L }
                        .distinctBy { "${it.ownerId}_${it.id}" }
                    endReached = wall.size < 20
                    AppLog.i("UserProfileScreen", "Loaded profile uid=$userId + ${wall.size} posts")
                } else {
                    errorText = app.apiClient.lastApiError ?: "Профиль не найден"
                }
            } catch (e: Exception) {
                AppLog.e("UserProfileScreen", "Failed to load profile uid=$userId", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Этап П-2: подарки чужого профиля (gifts.get, count=9 — инвентарь
    // §1.1.6 «Подарки (N)»). Для своего профиля не грузим; ошибка/пусто →
    // секции просто нет (no-stub).
    LaunchedEffect(userId, isSelf) {
        if (isSelf) return@LaunchedEffect
        try {
            gifts = app.apiClient.giftsGet(userId, count = 9)
        } catch (e: Exception) {
            AppLog.w("UserProfileScreen", "giftsGet failed: ${e.message}")
        }
    }

    // П-7-AB: счётчик «Подписок» собеседника (для строки «Подписки» —
    // параллельный добор, как gifts). Для своего профиля не грузим — там
    // свой счётчик считает ProfileScreen. Ошибка → строка без числа.
    LaunchedEffect(userId, isSelf) {
        if (isSelf) return@LaunchedEffect
        try {
            subscriptionsCount = followSubscriptionsTotal(
                app.apiClient.usersGetSubscriptions(userId = userId, count = 1),
            )
        } catch (e: Exception) {
            AppLog.w("UserProfileScreen", "subscriptions count failed: ${e.message}")
        }
    }

    // Fix #86: pull-to-refresh стены + профиля.
    fun refreshWall() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            try {
                val fresh = app.apiClient.usersGetFull(userId)
                if (fresh != null) profile = fresh
                // Этап П-2: освежаем состояния подписки/закладок из свежего профиля.
                subscribed = (fresh?.isSubscribed == 1)
                isBookmarked = (fresh?.isFavorite == 1)
                val wall = app.apiClient.wallGet(ownerId = userId, count = 20)
                posts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                endReached = wall.size < 20
                AppLog.i("UserProfileScreen", "refreshed: ${wall.size} posts")
            } catch (e: Exception) {
                AppLog.e("UserProfileScreen", "refreshWall failed", e)
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #86: infinite scroll стены.
    fun loadMoreWall() {
        if (loadingMore || endReached || posts.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val wall = app.apiClient.wallGet(ownerId = userId, count = 20, offset = posts.size)
                val newPosts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .filter { np -> posts.none { it.ownerId == np.ownerId && it.id == np.id } }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                if (newPosts.isEmpty()) {
                    endReached = true
                } else {
                    posts = (posts + newPosts).distinctBy { "${it.ownerId}_${it.id}" }
                    if (wall.size < 20) endReached = true
                }
            } catch (e: Exception) {
                AppLog.e("UserProfileScreen", "loadMoreWall failed", e)
            } finally {
                loadingMore = false
            }
        }
    }

    // Fix #86: триггер пагинации.
    LaunchedEffect(listState, posts.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMoreWall() }
    }

    // П-7-AB: жалоба на запись чужой стены (wall.markAsSpam) после подтверждения
    // в AlertDialog. Успех → тост «Жалоба отправлена»; ошибка → тост lastApiError.
    // Во время полёта кнопки диалога disabled, повторный вызов игнорируется.
    fun reportWallPostConfirmed(target: Post) {
        if (reportInFlight) return
        reportInFlight = true
        scope.launch {
            val ok = try {
                app.apiClient.wallMarkAsSpam(ownerId = target.ownerId, postId = target.id)
            } catch (e: Exception) {
                AppLog.e("UserProfileScreen", "wallMarkAsSpam failed", e)
                false
            }
            reportInFlight = false
            reportingPost.value = null
            Toast.makeText(
                context,
                if (ok) "Жалоба отправлена"
                else (app.apiClient.lastApiError ?: "Не удалось отправить жалобу"),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    if (loading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Профиль", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val p = profile
    if (p == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Профиль", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = errorText ?: "Профиль не загружен",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        return
    }

    // #OPVK-EXTRACT: строки «Информация» — только непустые значения; пусто →
    // секция не рисуется вовсе (no-stub, семантика AboutProfileLayout OpenVK).
    val infoRows = profileInfoRows(p)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = p.fullName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // Этап П-2: «Позвонить» — только при живом CallStarter
                    // (иконка как в FriendsScreen:457; null → не рендерится).
                    if (!isSelf && onCallClick != null) {
                        IconButton(
                            onClick = { onCallClick(p.id, p.fullName, p.photo200 ?: p.photo100) },
                        ) {
                            Icon(
                                Icons.Filled.Call,
                                contentDescription = "Позвонить",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (!isSelf) {
                        Box {
                            IconButton(onClick = { profileMenuExpanded = true }) {
                                Icon(Icons.Outlined.MoreHoriz, contentDescription = "Ещё")
                            }
                            DropdownMenu(
                                expanded = profileMenuExpanded,
                                onDismissRequest = { profileMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Пожаловаться") },
                                    onClick = {
                                        profileMenuExpanded = false
                                        scope.launch {
                                            try {
                                                // VKA usersReport KDoc: допустимые type —
                                                // porn|spam|fraud|insult|... («user» в
                                                // списке нет) — шлём spam как общий тип.
                                                val ok = app.apiClient.usersReport(p.id, "spam")
                                                Toast.makeText(
                                                    context,
                                                    if (ok) "Жалоба отправлена"
                                                    else "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            } catch (e: Exception) {
                                                AppLog.e("UserProfileScreen", "usersReport failed", e)
                                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Скопировать ссылку") },
                                    onClick = {
                                        profileMenuExpanded = false
                                        // Fix #193-паттерн: платформенный
                                        // ClipboardManager (LocalClipboardManager
                                        // deprecated в Compose 1.8+).
                                        val url = "https://vk.com/${p.domain ?: p.screenName ?: "id${p.id}"}"
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("vk_profile_link", url))
                                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isBookmarked) "Убрать из закладок" else "Добавить в закладки") },
                                    onClick = {
                                        profileMenuExpanded = false
                                        scope.launch {
                                            try {
                                                val ok =
                                                    if (isBookmarked) app.apiClient.faveRemoveUser(p.id)
                                                    else app.apiClient.faveAddUser(p.id)
                                                if (ok) {
                                                    isBookmarked = !isBookmarked
                                                    Toast.makeText(
                                                        context,
                                                        if (isBookmarked) "Добавлено в закладки" else "Убрано из закладок",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                AppLog.e("UserProfileScreen", "fave user action failed", e)
                                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (hiddenFromFeed) "Вернуть в ленту" else "Скрыть из ленты") },
                                    onClick = {
                                        profileMenuExpanded = false
                                        scope.launch {
                                            try {
                                                val ok =
                                                    if (hiddenFromFeed) app.apiClient.newsfeedDeleteBan(userIds = listOf(p.id))
                                                    else app.apiClient.newsfeedAddBan(userIds = listOf(p.id))
                                                if (ok) {
                                                    hiddenFromFeed = !hiddenFromFeed
                                                    Toast.makeText(
                                                        context,
                                                        if (hiddenFromFeed) "Записи скрыты из ленты" else "Записи вернутся в ленту",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                AppLog.e("UserProfileScreen", "newsfeed ban action failed", e)
                                                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refreshWall() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item { ProfileHeader(profile = p) }
            item {
                CountersRow(
                    profile = p,
                    // П-7-AB: тап по чипу «Подписчики» → подписчики СОБЕСЕДНИКА.
                    // «Друзья» НЕ проводим: глобальный Screen.Friends показывает
                    // СВОИХ друзей (отклонение — KDoc экрана выше).
                    onFollowersClick = { onFollowersClick(p.id) },
                )
            }
            // П-7-AB: строка «Подписки» собеседника (чипа в CountersRow нет —
            // поля subscriptions в модели Counters нет; строка — как в своём
            // профиле, см. KDoc SubscriptionsEntryRow).
            item {
                SubscriptionsEntryRow(
                    count = subscriptionsCount,
                    onClick = { onSubscriptionsClick(p.id) },
                )
            }

            // Действия: «Написать» + «Добавить в друзья» (кроме себя).
            if (!isSelf) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                onMessageClick(
                                    p.id,
                                    p.fullName,
                                    p.photo200 ?: p.photo100,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Написать")
                        }
                        OutlinedButton(
                            onClick = {
                                if (friendActionInProgress) return@OutlinedButton
                                scope.launch {
                                    friendActionInProgress = true
                                    try {
                                        if (isFriend) {
                                            val ok = app.apiClient.friendsDelete(p.id)
                                            isFriend = !ok
                                            Toast.makeText(
                                                context,
                                                if (ok) "Удалён из друзей" else "Не удалось удалить",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            val result = app.apiClient.friendsAdd(p.id)
                                            // friendsAdd возвращает: 1 — заявка отправлена/принята,
                                            // 2 — одобрена, 0 — ошибка.
                                            if (result > 0) {
                                                isFriend = (result == 2)
                                                Toast.makeText(
                                                    context,
                                                    if (result == 2) "Добавлен в друзья"
                                                    else "Заявка отправлена",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Не удалось добавить: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        AppLog.e("UserProfileScreen", "friend action failed", e)
                                        Toast.makeText(
                                            context,
                                            "Ошибка: ${e.message}",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } finally {
                                        friendActionInProgress = false
                                    }
                                }
                            },
                            enabled = !friendActionInProgress,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.width(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isFriend) "Удалить" else "В друзья")
                        }
                        // Этап П-2: «Подписаться»/«Отписаться» — usersSubscribe/
                        // usersUnsubscribe (VKA П-0; web-семантика «подписаться на
                        // открытой странице»). Состояние из is_subscribed; для
                        // друга (friend_status==3) не показываем — не дублируем
                        // кнопку «В друзья»/«Удалить».
                        if (!isFriend) {
                            OutlinedButton(
                                onClick = {
                                    if (subscribeInProgress) return@OutlinedButton
                                    scope.launch {
                                        subscribeInProgress = true
                                        try {
                                            val ok =
                                                if (subscribed) app.apiClient.usersUnsubscribe(p.id)
                                                else app.apiClient.usersSubscribe(p.id)
                                            if (ok) {
                                                subscribed = !subscribed
                                                Toast.makeText(
                                                    context,
                                                    if (subscribed) "Вы подписались" else "Вы отписались",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Не удалось: ${app.apiClient.lastApiError ?: "ошибка"}",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            AppLog.e("UserProfileScreen", "subscribe action failed", e)
                                            Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            subscribeInProgress = false
                                        }
                                    }
                                },
                                enabled = !subscribeInProgress,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(
                                    if (subscribed) "Отписаться" else "Подписаться",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // #OPVK-EXTRACT: секция «Информация» (label/value из уже загруженного
            // UserProfile) — между действиями и «Подарками», как инфо-блок веба.
            if (infoRows.isNotEmpty()) {
                item(key = "profile_info") {
                    ProfileInfoSection(rows = infoRows)
                }
            }

            // Этап П-2: секция «Подарки» над лентой записей (gifts.get, count=9,
            // инвентарь §1.1.6). Рисуется только при непустом ответе — no-stub.
            if (gifts.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            text = "Подарки (${p.counters?.gifts ?: gifts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            items(gifts) { g ->
                                val url = giftImageUrl(g)
                                if (url != null) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Подарок",
                                        modifier = Modifier.size(72.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Записи (${posts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            items(posts, key = { "${it.ownerId}_${it.id}" }) { post ->
                WallPostCard(
                    post = post,
                    authorName = p.fullName,
                    authorPhoto = p.photo200 ?: p.photo100,
                    onVideoClick = onVideoClick,
                    onPostClick = onPostClick,
                    onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                    onRepostClick = { repostPost.value = it },
                    // Шаг 5 (#32e): тап по комментарию → onCommentClick → PostDetailScreen.
                    onCommentClick = onCommentClick,
                    // П-7-AB: «Пожаловаться» на записях ЧУЖОЙ стены (wall.markAsSpam).
                    // showActions по-прежнему false (П-6а: manage-пункты только на
                    // своём профиле) → меню открывается с одним «Пожаловаться».
                    showReport = !isSelf,
                    onReportSpam = { reportingPost.value = it },
                )
            }
            // Fix #86: футер пагинации.
            item {
                when {
                    loadingMore -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    endReached && posts.isNotEmpty() -> {
                        Text(
                            text = "Это все записи",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }

            if (posts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "На стене нет записей",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        } // PullToRefreshBox (Fix #86)

        // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
        val viewer = photoViewerState.value
        if (viewer != null) {
            PhotoViewer(
                photos = viewer.first,
                initial = viewer.second,
                onDismiss = { photoViewerState.value = null },
            )
        }

        // ShareSheet: расширенный диалог «Поделиться».
        val sharing = repostPost.value
        if (sharing != null) {
            ShareSheet(
                post = sharing,
                onDismiss = { repostPost.value = null },
            )
        }

        // П-7-AB: подтверждение «Пожаловаться» на запись (wall.markAsSpam).
        val reportTarget = reportingPost.value
        if (reportTarget != null) {
            AlertDialog(
                onDismissRequest = { if (!reportInFlight) reportingPost.value = null },
                title = { Text("Пожаловаться на запись?") },
                text = { Text("Запись будет отправлена на проверку администрации VK.") },
                confirmButton = {
                    TextButton(
                        onClick = { reportWallPostConfirmed(reportTarget) },
                        enabled = !reportInFlight,
                    ) {
                        Text(
                            if (reportInFlight) "Отправка…" else "Пожаловаться",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { reportingPost.value = null }, enabled = !reportInFlight) {
                        Text("Отмена")
                    }
                },
            )
        }
    }
}

/**
 * Этап П-2 (PROFILE-P2-3b): URL картинки подарка из item ответа `gifts.get`.
 * VK отдаёт два формата: legacy — gift_75/gift_96/gift_256 строками на уровне
 * item; новый — вложенный объект gift { thumb_75/thumb_96/thumb_256 }.
 * Поддержаны оба; null → item без картинки пропускается (no-stub).
 */
private fun giftImageUrl(item: JsonObject): String? {
    for (key in listOf("gift_256", "gift_96", "gift_75")) {
        val v = item.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        if (!v.isNullOrBlank()) return v
    }
    val gift = item.getAsJsonObject("gift") ?: return null
    for (key in listOf("thumb_256", "thumb_96", "thumb_75")) {
        val v = gift.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        if (!v.isNullOrBlank()) return v
    }
    return null
}

/**
 * #OPVK-EXTRACT (Task 3-c): строки секции «Информация» из полей UserProfile,
 * которые УЖЕ приходят в загрузочном флоу экрана (usersGetFull →
 * usersGetFullExtended, fields about/activities/interests/music/movies/books;
 * парсинг VKA:13457-13463) — НИКАКИХ новых VKA-вызовов. Только непустые
 * значения (семантика AboutProfileLayout OpenVK: item добавляется при
 * непустой строке — идея, не код; AGPL-копирование запрещено).
 *
 * ЧЕСТНЫЕ ОГРАНИЧЕНИЯ (no-stub):
 *  — «Любимые цитаты» (quotes) не рисуется: поле запрашивается в fields
 *    usersGetFullExtended, но в модель UserProfile НЕ парсится (модель —
 *    core/data, вне зоны правок), имитировать значение нечем;
 *  — bdate/city НЕ дублируются: их уже показывает [ProfileHeader]
 *    («День рождения: …» / «Город: …», ProfileScreen.kt:1526-1541);
 *  — sex/tv/games/relation/career/education и personal.* приходят не для всех
 *    страниц/токенов (UserProfile.sex — 0-дефолт без «не задано»-маркера),
 *    в заданный список строк не входят — не рисуются.
 */
private fun profileInfoRows(p: UserProfile): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    p.about?.takeIf { it.isNotBlank() }?.let { rows.add("О себе" to it) }
    p.activities?.takeIf { it.isNotBlank() }?.let { rows.add("Деятельность" to it) }
    p.interests?.takeIf { it.isNotBlank() }?.let { rows.add("Интересы" to it) }
    p.music?.takeIf { it.isNotBlank() }?.let { rows.add("Любимая музыка" to it) }
    p.movies?.takeIf { it.isNotBlank() }?.let { rows.add("Любимые фильмы" to it) }
    p.books?.takeIf { it.isNotBlank() }?.let { rows.add("Любимые книги" to it) }
    return rows
}

/**
 * #OPVK-EXTRACT (Task 3-c): карточка «Информация» чужого профиля — заголовок
 * в стиле секций экрана («Подарки»/«Записи»: titleMedium) + Card (паттерн
 * [SubscriptionsEntryRow]: elevation 0) со строками label/value. Рисуется
 * только при наличии хотя бы одной непустой строки (вызывает только с
 * непустым [rows] — см. вызов в UserProfileScreen).
 */
@Composable
private fun ProfileInfoSection(rows: List<Pair<String, String>>) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Информация",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(8.dp))
        Card(elevation = CardDefaults.cardElevation(0.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                rows.forEachIndexed { index, row ->
                    val (label, value) = row
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

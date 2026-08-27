// File: ui/screens/profile/UserProfileScreen.kt
package re.pinok.ui.screens.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    // Fix #86: pull-to-refresh стены + профиля.
    fun refreshWall() {
        if (isRefreshing) return
        scope.launch {
            isRefreshing = true
            try {
                val fresh = app.apiClient.usersGetFull(userId)
                if (fresh != null) profile = fresh
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
            item { CountersRow(profile = p) }

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
    }
}

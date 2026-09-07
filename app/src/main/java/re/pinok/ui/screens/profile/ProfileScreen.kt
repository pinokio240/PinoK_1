package re.pinok.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.google.gson.JsonObject
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Attachment
import re.pinok.data.model.Post
import re.pinok.data.model.PhotoSizes
import re.pinok.data.model.Track
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.media.PlayerConnection
import re.pinok.ui.components.AudioAttachmentList
import re.pinok.ui.components.CreatePostDialog
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.components.PlaylistAttachmentCard
import re.pinok.ui.components.RepostDialog
import re.pinok.util.AppLog
import re.pinok.util.toCountString
import re.pinok.util.toRelativeTime

// П-1 (#PROFILE-SNAP): вкладки контента профиля. Статический набор — web-набор
// users.getContentTabs известен (инвентарь §1.1.3: music,videos,photos,…),
// вызывать usersGetContentTabs для этого не обязательно.
private const val PROFILE_TAB_WALL = "wall"
private const val PROFILE_TAB_MUSIC = "music"
private const val PROFILE_TAB_VIDEO = "video"
private const val PROFILE_TAB_PHOTO = "photo"

private val PROFILE_CONTENT_TABS: List<Pair<String, String>> = listOf(
    PROFILE_TAB_WALL to "Стена",
    PROFILE_TAB_MUSIC to "Музыка",
    PROFILE_TAB_VIDEO to "Видео",
    PROFILE_TAB_PHOTO to "Фото",
)

// П-1: подвкладки стены — значения wall.get(filter) по канонике users.getWallTabs
// (all | owner | archived). «Архив» показывается только если users.getWallTabs
// вернул archived с count>0 (иначе серверная поддержка фильтра не гарантирована).
private const val WALL_FILTER_ALL = "all"
private const val WALL_FILTER_OWNER = "owner"
private const val WALL_FILTER_ARCHIVED = "archived"

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onVideoClick: (Video) -> Unit = {},
    // Шаг 5 (#32e): тап по комментарию поста → PostDetailScreen.
    onCommentClick: (Post) -> Unit = {},
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var posts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val photoViewerState = remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // Sprint 2, P1-3 (#90): диалог репоста.
    val repostPost = remember { mutableStateOf<Post?>(null) }
    // User request 2026-07-12: кнопка «Создать пост» перенесена из ленты в профиль.
    val creatingPost = remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> selectedPhotoUri = uri }
    // Флаг: нужно перезагрузить стену после создания нового поста.
    var reloadWallTrigger by remember { mutableStateOf(0) }

    // ── П-1 (#PROFILE-SNAP): свой профиль — статус, подвкладки стены, вкладки контента ──
    // Активная подвкладка стены (wall.get filter).
    var wallFilter by remember { mutableStateOf(WALL_FILTER_ALL) }
    // Индикатор перезагрузки ленты (смена фильтра).
    var wallLoading by remember { mutableStateOf(false) }
    // «Архив» доступен, если users.getWallTabs вернул archived с count>0.
    var hasArchiveWallTab by remember { mutableStateOf(false) }
    // Подарки (gifts.get — сырые JsonObject, парсинг в GiftsSection).
    var gifts by remember { mutableStateOf<List<JsonObject>?>(null) }
    // Активная вкладка контента.
    var selectedContentTab by remember { mutableStateOf(PROFILE_TAB_WALL) }
    // Счётчик ретраев вкладок контента (кнопка «Повторить»).
    var contentRetryTick by remember { mutableStateOf(0) }
    // Музыка (audio.get) — ленивая загрузка при первом выборе вкладки.
    var musicLoaded by remember { mutableStateOf(false) }
    var musicLoading by remember { mutableStateOf(false) }
    var musicError by remember { mutableStateOf<String?>(null) }
    var musicTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    // Видео (video.get).
    var videoLoaded by remember { mutableStateOf(false) }
    var videoLoading by remember { mutableStateOf(false) }
    var videoError by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    // Фото (photos.getAll).
    var photoLoaded by remember { mutableStateOf(false) }
    var photoLoading by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    // Диалог правки статуса (status.set).
    var showStatusDialog by remember { mutableStateOf(false) }
    var statusSaving by remember { mutableStateOf(false) }
    var statusError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            errorText = null
            try {
                // П-1 (#PROFILE-SNAP): usersGetFullExtended вместо usersGet —
                // тянет field=cover (обложка профиля) и веб-набор полей
                // (PROFILE-P0-2); всё, что рисует экран (status/counters/
                // verified/bdate/city), парсится так же.
                val prof = app.apiClient.usersGetFullExtended(null)
                profile = prof
                if (prof != null) {
                    // П-1: доступность «Архива» (users.getWallTabs) и подарки
                    // (gifts.get) — параллельные доборы, стену не задерживают.
                    scope.launch {
                        val wallTabs = app.apiClient.usersGetWallTabs(prof.id)
                        hasArchiveWallTab = wallTabs.any { it.type == "archived" && it.count > 0 }
                    }
                    scope.launch {
                        try {
                            gifts = app.apiClient.giftsGet(prof.id, count = 10)
                        } catch (e: Exception) {
                            AppLog.e("ProfileScreen", "Gifts load failed", e)
                        }
                    }
                    val wall = app.apiClient.wallGet(ownerId = prof.id, count = 20)
                    // Fix #53: защитная дедупликация на уровне UI.
                    posts = wall
                        .filter { it.id > 0 && it.ownerId != 0L }
                        .distinctBy { "${it.ownerId}_${it.id}" }
                    AppLog.i("ProfileScreen", "Loaded profile + ${wall.size} wall posts")
                } else {
                    errorText = app.apiClient.lastApiError ?: "Не удалось загрузить профиль"
                }
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "Failed to load profile", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Перезагрузка стены после создания нового поста (reloadWallTrigger меняется).
    LaunchedEffect(reloadWallTrigger) {
        if (reloadWallTrigger == 0) return@LaunchedEffect // пропускаем первичную загрузку
        scope.launch {
            val p = profile ?: return@launch
            try {
                // П-1: перезагрузка уважает активную подвкладку стены (wall.get filter).
                val wall = app.apiClient.wallGetWithFilter(ownerId = p.id, filter = wallFilter, count = 20)
                posts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                AppLog.i("ProfileScreen", "Reloaded wall after new post: ${wall.size} posts")
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "Reload wall failed", e)
            }
        }
    }

    // П-1 (#PROFILE-SNAP): подвкладки стены. Смена фильтра перезагружает ленту
    // через wall.get(filter=all|owner|archived). Первое значение пропускаем —
    // начальная загрузка идёт в LaunchedEffect(Unit) выше.
    var wallFilterStarted by remember { mutableStateOf(false) }
    LaunchedEffect(wallFilter) {
        if (!wallFilterStarted) {
            wallFilterStarted = true
            return@LaunchedEffect
        }
        scope.launch {
            val prof = profile ?: return@launch
            wallLoading = true
            try {
                val wall = app.apiClient.wallGetWithFilter(ownerId = prof.id, filter = wallFilter, count = 20)
                posts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                AppLog.i("ProfileScreen", "Wall filter=$wallFilter loaded: ${wall.size} posts")
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "Wall filter load failed", e)
            } finally {
                wallLoading = false
            }
        }
    }

    // П-1: вкладки контента грузятся ЛЕНИВО при первом выборе
    // (ключ-флаг loaded + LaunchedEffect по активной вкладке).
    LaunchedEffect(selectedContentTab, contentRetryTick) {
        val ownerId = profile?.id ?: return@LaunchedEffect
        when (selectedContentTab) {
            PROFILE_TAB_MUSIC -> if (!musicLoaded && !musicLoading) {
                musicLoading = true
                musicError = null
                try {
                    musicTracks = app.apiClient.audioGet(count = 10, ownerId = ownerId)
                        .filter { it.id > 0L && !it.url.isNullOrBlank() }
                    musicLoaded = true
                    AppLog.i("ProfileScreen", "Music tab loaded: ${musicTracks.size} tracks")
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Music tab load failed", e)
                    musicError = "Ошибка: ${e.message}"
                } finally {
                    musicLoading = false
                }
            }
            PROFILE_TAB_VIDEO -> if (!videoLoaded && !videoLoading) {
                videoLoading = true
                videoError = null
                try {
                    videos = app.apiClient.videoGet(ownerId = ownerId, count = 9)
                        .filter { it.id > 0L }
                    videoLoaded = true
                    AppLog.i("ProfileScreen", "Video tab loaded: ${videos.size} videos")
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Video tab load failed", e)
                    videoError = "Ошибка: ${e.message}"
                } finally {
                    videoLoading = false
                }
            }
            PROFILE_TAB_PHOTO -> if (!photoLoaded && !photoLoading) {
                photoLoading = true
                photoError = null
                try {
                    photos = app.apiClient.photosGetAll(ownerId = ownerId, count = 12)
                    photoLoaded = true
                    AppLog.i("ProfileScreen", "Photo tab loaded: ${photos.size} photos")
                } catch (e: Exception) {
                    AppLog.e("ProfileScreen", "Photo tab load failed", e)
                    photoError = "Ошибка: ${e.message}"
                } finally {
                    photoLoading = false
                }
            }
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val p = profile
    if (p == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = errorText ?: "Профиль не загружен",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("  Выйти")
            }
        }
        return
    }

    // Fix #43: statusBarsPadding — контент не уходит под системную панель.
    // ProfileScreen в hasOwnTopBar списке SovaNavHost, но своего Scaffold нет
    // (глобальный TopAppBar не рисуется) → insets применяем сами.
    LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // П-1: тап по статусу → диалог правки (status.set).
        item {
            ProfileHeader(
                profile = p,
                onStatusClick = {
                    statusError = null
                    showStatusDialog = true
                },
            )
        }
        item { CountersRow(profile = p) }
        // П-1 (#PROFILE-SNAP): вкладки контента «Стена / Музыка / Видео / Фото» —
        // шапка и счётчики сохраняются, меняется только контент ниже.
        item {
            ProfileChipsRow(
                options = PROFILE_CONTENT_TABS,
                selected = selectedContentTab,
                onSelect = { selectedContentTab = it },
            )
        }
        if (selectedContentTab == PROFILE_TAB_WALL) {
            // User request 2026-07-12: кнопка «Создать пост» в профиле
            // (перенесена из ленты). VK API: wall.post — owner_id = текущий пользователь.
            // См. VK_IMPORT_API.MD §1.1.
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { creatingPost.value = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Создать пост",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            // П-1 (#PROFILE-SNAP): подвкладки стены «Все / Свои / Архив»
            // (wall.get filter=all|owner|archived, каноника users.getWallTabs).
            item {
                WallFilterRow(
                    selected = wallFilter,
                    showArchive = hasArchiveWallTab,
                    onSelect = { wallFilter = it },
                )
            }
            if (wallLoading) {
                item { TabProgressRow() }
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
                    onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                    onRepostClick = { repostPost.value = it },
                    // Шаг 5 (#32e): тап по комментарию → onCommentClick → PostDetailScreen.
                    onCommentClick = onCommentClick,
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                )
            }
            if (posts.isEmpty() && !wallLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when (wallFilter) {
                                WALL_FILTER_OWNER -> "Своих записей на стене нет"
                                WALL_FILTER_ARCHIVED -> "В архиве нет записей"
                                else -> "На стене пока нет записей"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // П-1: подарки профиля (gifts.get) — ряд открыток под лентой.
            val giftsList = gifts
            if (!giftsList.isNullOrEmpty()) {
                item {
                    GiftsSection(gifts = giftsList, totalCount = p.counters?.gifts ?: 0)
                }
            }
        } else if (selectedContentTab == PROFILE_TAB_MUSIC) {
            item {
                MusicTabSection(
                    tracks = musicTracks,
                    loading = musicLoading,
                    error = musicError,
                    onRetry = { contentRetryTick++ },
                )
            }
        } else if (selectedContentTab == PROFILE_TAB_VIDEO) {
            item {
                VideoTabSection(
                    videos = videos,
                    loading = videoLoading,
                    error = videoError,
                    onRetry = { contentRetryTick++ },
                    onVideoClick = onVideoClick,
                )
            }
        } else if (selectedContentTab == PROFILE_TAB_PHOTO) {
            item {
                PhotoTabSection(
                    photos = photos,
                    loading = photoLoading,
                    error = photoError,
                    onRetry = { contentRetryTick++ },
                    onPhotoClick = { urls, idx -> photoViewerState.value = urls to idx },
                )
            }
        }
        // Кнопка выхода — видна на любой вкладке (единственный вход в логаут).
        item {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("  Выйти из аккаунта")
            }
        }
    }

    // Sprint 2, P1-1 (#88): полноэкранный просмотр фото.
    val viewer = photoViewerState.value
    if (viewer != null) {
        PhotoViewer(
            photos = viewer.first,
            initial = viewer.second,
            onDismiss = { photoViewerState.value = null },
        )
    }

    // Sprint 2, P1-3 (#90): диалог репоста.
    val reposting = repostPost.value
    if (reposting != null) {
        RepostDialog(
            post = reposting,
            onDismiss = { repostPost.value = null },
        )
    }

    // User request 2026-07-12: диалог создания нового поста (перенесён из ленты).
    // VK API: wall.post(message, owner_id, friends_only) или
    // uploadPhotoAndPost для поста с фото. После успеха — перезагружаем стену.
    if (creatingPost.value) {
        CreatePostDialog(
            selectedPhotoUri = selectedPhotoUri,
            onPickPhoto = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemovePhoto = { selectedPhotoUri = null },
            onDismiss = {
                creatingPost.value = false
                selectedPhotoUri = null
            },
            onSubmit = { message, friendsOnly ->
                val photoUri = selectedPhotoUri
                val ownerId = p.id
                scope.launch {
                    val id = if (photoUri != null) {
                        // Пост с фото — upload flow на стену текущего пользователя.
                        AppLog.i("ProfileScreen", "Creating post with photo on wall $ownerId")
                        app.apiClient.uploadPhotoAndPost(message, photoUri, friendsOnly = friendsOnly)
                    } else {
                        // Текстовый пост на свою стену.
                        app.apiClient.wallPost(message, ownerId = ownerId, friendsOnly = friendsOnly)
                    }
                    if (id > 0) {
                        AppLog.i("ProfileScreen", "Post created: id=$id")
                        // Перезагружаем стену, чтобы новый пост появился сверху.
                        reloadWallTrigger++
                    } else {
                        AppLog.w("ProfileScreen", "wallPost failed")
                    }
                }
                creatingPost.value = false
                selectedPhotoUri = null
            },
        )
    }

    // П-1 (#PROFILE-SNAP): диалог правки статуса (status.set). Пустой текст
    // снимает статус (VK: пустой text удаляет статус).
    if (showStatusDialog) {
        var statusDraft by remember { mutableStateOf(p.status ?: "") }
        AlertDialog(
            onDismissRequest = { if (!statusSaving) showStatusDialog = false },
            title = { Text("Изменить статус") },
            text = {
                Column {
                    OutlinedTextField(
                        value = statusDraft,
                        onValueChange = { statusDraft = it.take(140) },
                        placeholder = { Text("Что у вас нового?") },
                        maxLines = 3,
                        enabled = !statusSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val sErr = statusError
                    if (sErr != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = sErr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            statusSaving = true
                            statusError = null
                            val newText = statusDraft.trim()
                            val ok = try {
                                app.apiClient.statusSet(newText)
                            } catch (e: Exception) {
                                AppLog.e("ProfileScreen", "statusSet failed", e)
                                false
                            }
                            statusSaving = false
                            if (ok) {
                                // Обновляем состояние экрана без перезагрузки профиля.
                                profile = p.copy(status = newText.takeIf { it.isNotEmpty() })
                                showStatusDialog = false
                                AppLog.i("ProfileScreen", "Status updated")
                            } else {
                                statusError = app.apiClient.lastApiError ?: "Не удалось сохранить статус"
                            }
                        }
                    },
                    enabled = !statusSaving,
                ) {
                    Text(if (statusSaving) "Сохранение…" else "Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStatusDialog = false }, enabled = !statusSaving) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
fun ProfileHeader(
    profile: UserProfile,
    // П-1 (#PROFILE-SNAP): тап по статусу → диалог правки (status.set).
    // Nullable с дефолтом null: UserProfileScreen (чужой профиль) зовёт без
    // параметра — там правка статуса не показывается.
    onStatusClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // П-1: обложка профиля (users.get field=cover → usersGetFullExtended
        // парсит → UserProfile.cover.images; enabled=1 без images — не рисуем).
        val coverUrl = profile.cover?.takeIf { it.enabled }?.images?.lastOrNull()
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Обложка профиля",
                modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(12.dp))
        }
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.BottomEnd) {
            if (profile.photo200 != null) {
                AsyncImage(
                    model = profile.photo200,
                    contentDescription = profile.fullName,
                    modifier = Modifier.size(120.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.firstName.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (profile.isOnline) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(3.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = profile.fullName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (profile.verified == 1) {
                Spacer(Modifier.width(6.dp))
                Text("\u2713", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
            }
        }
        // П-1: статус кликабелен на своём профиле (правка через status.set);
        // пустой статус → подсказка «Установить статус» (как в вебе VK).
        val statusText = profile.status
        val statusClick = onStatusClick
        if (statusText != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = if (statusClick != null) {
                    Modifier.clickable { statusClick() }
                } else {
                    Modifier
                },
            )
        } else if (statusClick != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Установить статус",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { statusClick() },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ID: ${profile.id}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        profile.bdate?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "День рождения: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profile.city?.title?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Город: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CountersRow(profile: UserProfile) {
    val counters = profile.counters
    val items = mutableListOf<Pair<String, Int>>()
    // Основные счётчики — из counters или верхнеуровневых полей.
    counters?.friends?.let { items.add("Друзья" to it) }
    profile.followersCount.takeIf { it > 0 }?.let { items.add("Подписчики" to it) }
        ?: counters?.followers?.let { items.add("Подписчики" to it) }
    counters?.photos?.let { items.add("Фото" to it) }
    counters?.videos?.let { items.add("Видео" to it) }
    counters?.audios?.let { items.add("Аудио" to it) }
    counters?.gifts?.takeIf { it > 0 }?.let { items.add("Подарки" to it) }
    if (items.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        items.forEach { (label, count) ->
            Card(
                modifier = Modifier.weight(1f).padding(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = count.toCountString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallPostCard(
    post: Post,
    authorName: String,
    authorPhoto: String?,
    onVideoClick: (Video) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    // Sprint 2, P1-1 (#88): тап по фото → полноэкранный просмотр.
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    // Sprint 2, P1-3 (#90): тап по репосту → диалог.
    onRepostClick: (Post) -> Unit = {},
    // Шаг 5 (#32e): тап по иконке комментария → PostDetailScreen.
    onCommentClick: (Post) -> Unit = {},
) {
    val photoAttachments = post.attachments?.filter { it.type == "photo" && it.photo != null }.orEmpty()
    // Fix #70: ранее video-вложения вообще не отображались на стене профиля.
    val videoAttachments = post.attachments?.filter { it.type == "video" && it.video != null }.orEmpty()
    // #30 (audio attachments): рендер audio-вложений на стене профиля.
    val audioAttachments = post.attachments?.filter { it.type == "audio" && it.audio != null }.orEmpty()
    val timeStr = post.date.toRelativeTime()
    val likeCount = post.likes?.count ?: 0
    val isLiked = post.likes?.userLikes == 1
    val commentCount = post.comments?.count ?: 0
    val repostCount = post.reposts?.count ?: 0
    val viewCount = post.views?.count ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (authorPhoto != null) {
                    AsyncImage(
                        model = authorPhoto,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = authorName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        .clickable { onPostClick(post) },
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (photoAttachments.isNotEmpty()) {
                PhotoGrid(
                    photos = photoAttachments.mapNotNull { it.photo },
                    onPhotoClick = onPhotoClick,
                )
            }
            // Fix #70: рендерим video-вложения (как в FeedScreen).
            videoAttachments.forEach { attach -> attach.video?.let { VideoThumbnail(video = it, onClick = onVideoClick) } }
            // #30 (audio attachments): рендерим audio-вложения (как в FeedScreen).
            if (audioAttachments.isNotEmpty()) {
                AudioAttachmentList(tracks = audioAttachments.mapNotNull { it.audio })
            }
            // #30 (playlists): audio_playlist вложения.
            val playlistAttachments = post.attachments?.filter { it.type == "audio_playlist" && it.audioPlaylist != null }.orEmpty()
            playlistAttachments.forEach { att -> att.audioPlaylist?.let { PlaylistAttachmentCard(playlist = it) } }
            // Fix #70: рендерим репост (copy_history) — первый элемент.
            post.copyHistory?.firstOrNull()?.let { repost ->
                RepostBlock(repost = repost, onPhotoClick = onPhotoClick, onVideoClick = onVideoClick)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ActionIcon(
                    icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    count = likeCount,
                    tint = if (isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                ActionIcon(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    count = commentCount,
                    // Шаг 5 (#32e): тап по комментарию → PostDetailScreen (через onCommentClick).
                    onClick = { onCommentClick(post) },
                )
                Spacer(Modifier.width(8.dp))
                ActionIcon(
                    icon = Icons.Outlined.Repeat,
                    count = repostCount,
                    onClick = { onRepostClick(post) },
                )
                Spacer(Modifier.weight(1f))
                if (viewCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Visibility, null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(viewCount.toCountString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoGrid(
    photos: List<Attachment.Photo>,
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
) {
    val photosWithUrl = photos.mapNotNull { photo ->
        val size = PhotoSizes.best(photo.sizes)
        val url = size?.url ?: return@mapNotNull null
        val ratio = if (size.height > 0) size.width.toFloat() / size.height.toFloat() else 1f
        Triple(photo, url, ratio)
    }
    if (photosWithUrl.isEmpty()) return
    val allUrls = photosWithUrl.map { it.second }
    val colCount = when { photosWithUrl.size == 1 -> 1; photosWithUrl.size <= 4 -> 2; else -> 3 }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = colCount,
    ) {
        photosWithUrl.forEachIndexed { index, (_, url, ratio) ->
            Card(
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 2f)).clip(RoundedCornerShape(8.dp))
                    .clickable { onPhotoClick(allUrls, index) },
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                AsyncImage(model = url, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

// Fix #70: VideoThumbnail для video-вложений на стене (аналог FeedScreen.VideoThumbnail).
@Composable
fun VideoThumbnail(video: Video, onClick: (Video) -> Unit) {
    val thumbUrl = video.thumbUrl
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick(video) },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbUrl != null) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                modifier = Modifier.size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            if (video.duration > 0) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "${video.duration / 60}:${"%02d".format(video.duration % 60)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// Fix #70: RepostBlock — отображение copy_history[0] как вложенной карточки.
// Показывает заголовок «Запись <ownerId>» + текст + вложения оригинального поста
// (фото/видео/аудио/плейлисты — #PROFILE-REPOST-ATTACH).
// Sprint 2, P1-1 (#88): фото кликабельны → PhotoViewer.
@Composable
fun RepostBlock(
    repost: Post,
    onPhotoClick: (List<String>, Int) -> Unit = { _, _ -> },
    onVideoClick: (Video) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    // Audit #40: для user-posts (ownerId>0) не показываем "клуба" —
                    // иначе получается двойной пробел "Запись  123". buildString аккуратнее.
                    text = buildString {
                        append("Запись")
                        if (repost.ownerId < 0) append(" клуба")
                        append(' ')
                        append(repost.ownerId)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (repost.text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = repost.text,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Фото из репоста
            val repostPhotos = repost.attachments
                ?.filter { it.type == "photo" && it.photo != null }
                .orEmpty()
            if (repostPhotos.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                PhotoGrid(
                    photos = repostPhotos.mapNotNull { it.photo },
                    onPhotoClick = onPhotoClick,
                )
            }
            // #PROFILE-REPOST-ATTACH: видео в репосте (как в WallPostCard).
            val repostVideos = repost.attachments
                ?.filter { it.type == "video" && it.video != null }
                .orEmpty()
            repostVideos.forEach { att ->
                att.video?.let {
                    Spacer(Modifier.height(6.dp))
                    VideoThumbnail(video = it, onClick = onVideoClick)
                }
            }
            // #PROFILE-REPOST-ATTACH: аудио в репосте.
            val repostAudios = repost.attachments
                ?.filter { it.type == "audio" && it.audio != null }
                .orEmpty()
            if (repostAudios.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                AudioAttachmentList(tracks = repostAudios.mapNotNull { it.audio })
            }
            // #PROFILE-REPOST-ATTACH: плейлисты в репосте.
            repost.attachments
                ?.filter { it.type == "audio_playlist" && it.audioPlaylist != null }
                ?.forEach { att ->
                    att.audioPlaylist?.let {
                        Spacer(Modifier.height(6.dp))
                        PlaylistAttachmentCard(playlist = it)
                    }
                }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = tint)
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(count.toCountString(), style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// П-1 (#PROFILE-SNAP): вкладки контента и секции своего профиля.
// ═══════════════════════════════════════════════════════════════════════════

/** Единый ряд чипов (вкладки контента + подвкладки стены). */
@Composable
private fun ProfileChipsRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Card(
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable { onSelect(value) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Подвкладки стены: «Все / Свои / Архив» («Архив» — только если доступен). */
@Composable
private fun WallFilterRow(selected: String, showArchive: Boolean, onSelect: (String) -> Unit) {
    val filters = buildList {
        add(WALL_FILTER_ALL to "Все")
        add(WALL_FILTER_OWNER to "Свои")
        if (showArchive) add(WALL_FILTER_ARCHIVED to "Архив")
    }
    ProfileChipsRow(options = filters, selected = selected, onSelect = onSelect)
}

@Composable
private fun TabProgressRow() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun TabErrorRow(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
private fun TabEmptyRow(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Вкладка «Музыка»: audio.get → ряд карточек треков; тап — playTrackList. */
@Composable
private fun MusicTabSection(
    tracks: List<Track>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    when {
        loading -> TabProgressRow()
        error != null -> TabErrorRow(message = error, onRetry = onRetry)
        tracks.isEmpty() -> TabEmptyRow("В вашей музыке пока нет треков")
        else -> {
            val playerState = PlayerConnection.playerState.collectAsState().value
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    tracks,
                    // Ключ с индексом — crash-proof к дублям (прецедент Fix #281).
                    key = { idx, track -> "music_${idx}_${track.ownerId}_${track.id}" },
                ) { idx, track ->
                    val current = playerState.currentTrack
                    val isCurrent = current != null &&
                        track.id == current.id &&
                        track.ownerId == current.ownerId
                    ProfileTrackCard(
                        track = track,
                        isPlaying = isCurrent && playerState.isPlaying,
                        onClick = {
                            if (isCurrent) {
                                PlayerConnection.togglePlayPause()
                            } else {
                                PlayerConnection.playTrackList(tracks, idx)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTrackCard(track: Track, isPlaying: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(140.dp).clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier.size(140.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val thumb = track.albumThumb
                if (thumb != null) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .padding(4.dp),
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Вкладка «Видео»: video.get → ряд карточек с превью; тап — плеер видео. */
@Composable
private fun VideoTabSection(
    videos: List<Video>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onVideoClick: (Video) -> Unit,
) {
    when {
        loading -> TabProgressRow()
        error != null -> TabErrorRow(message = error, onRetry = onRetry)
        videos.isEmpty() -> TabEmptyRow("У вас пока нет видео")
        else -> {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    videos,
                    key = { idx, video -> "video_${idx}_${video.ownerId}_${video.id}" },
                ) { _, video ->
                    ProfileVideoCard(video = video, onClick = { onVideoClick(video) })
                }
            }
        }
    }
}

@Composable
private fun ProfileVideoCard(video: Video, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(200.dp).clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val thumbUrl = video.thumbUrl
                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                if (video.duration > 0) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${video.duration / 60}:${"%02d".format(video.duration % 60)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Text(
                text = video.title,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Вкладка «Фото»: photos.getAll → сетка 3 колонки; тап — PhotoViewer. */
@Composable
private fun PhotoTabSection(
    photos: List<JsonObject>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPhotoClick: (List<String>, Int) -> Unit,
) {
    // П-1: photosGetAll возвращает сырые JsonObject — парсим sizes[] здесь
    // (VKApiClient не правим), как решено для «сырых» ответов #PROFILE-SNAP.
    val urls = remember(photos) { photos.mapNotNull { extractPhotoAllUrl(it) } }
    when {
        loading -> TabProgressRow()
        error != null -> TabErrorRow(message = error, onRetry = onRetry)
        urls.isEmpty() -> TabEmptyRow("У вас пока нет фотографий")
        else -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                urls.chunked(3).forEachIndexed { rowIdx, rowUrls ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowUrls.forEachIndexed { colIdx, url ->
                            Box(
                                modifier = Modifier.weight(1f).aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onPhotoClick(urls, rowIdx * 3 + colIdx) },
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                        // Добиваем последнюю строку пустыми ячейками до 3 колонок.
                        repeat(3 - rowUrls.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** URL лучшего размера из item photos.getAll (sizes[] — max по площади). */
private fun extractPhotoAllUrl(photo: JsonObject): String? {
    return try {
        var bestUrl: String? = null
        var bestArea = 0L
        val sizes = photo.getAsJsonArray("sizes")
        if (sizes != null) {
            for (el in sizes) {
                if (!el.isJsonObject) continue
                val o = el.asJsonObject
                val urlEl = o.get("url") ?: continue
                if (!urlEl.isJsonPrimitive) continue
                val wEl = o.get("width")
                val hEl = o.get("height")
                val w = if (wEl != null && wEl.isJsonPrimitive) wEl.asInt else 0
                val h = if (hEl != null && hEl.isJsonPrimitive) hEl.asInt else 0
                val area = w.toLong() * h.toLong()
                if (area > bestArea) {
                    bestArea = area
                    bestUrl = urlEl.asString
                }
            }
        }
        if (bestUrl != null) return bestUrl
        // Fallback: статические photo_* поля, если sizes пуст.
        listOf("photo_807", "photo_604", "photo_130").firstNotNullOfOrNull { key ->
            val el = photo.get(key)
            if (el != null && el.isJsonPrimitive) el.asString else null
        }
    } catch (e: Exception) {
        AppLog.e("ProfileScreen", "extractPhotoAllUrl parse error", e)
        null
    }
}

/** Секция «Подарки»: gifts.get → LazyRow открыток. */
@Composable
private fun GiftsSection(gifts: List<JsonObject>, totalCount: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = if (totalCount > 0) "Подарки ($totalCount)" else "Подарки",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                gifts,
                key = { idx, _ -> "gift_$idx" },
            ) { _, giftItem ->
                val thumbUrl = extractGiftThumbUrl(giftItem)
                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = "Подарок",
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

/**
 * URL открытки из «сырого» item gifts.get. Реальная структура (снапшот
 * «Профиль», apiPrefetchCache): {id, from_id, message, date,
 * gift: {id, thumb_512, thumb_256, thumb_96, thumb_48, ...}, gift_hash} —
 * поля photo_270/photo_200 в ответе отсутствуют, берём thumb_*.
 */
private fun extractGiftThumbUrl(giftItem: JsonObject): String? {
    return try {
        val giftObj = giftItem.getAsJsonObject("gift") ?: giftItem
        listOf("thumb_512", "thumb_256", "thumb_96", "thumb_48").firstNotNullOfOrNull { key ->
            val el = giftObj.get(key)
            if (el != null && el.isJsonPrimitive && el.asString.isNotBlank()) el.asString else null
        }
    } catch (e: Exception) {
        null
    }
}


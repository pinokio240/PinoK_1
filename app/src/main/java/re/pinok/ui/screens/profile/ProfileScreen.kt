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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.Attachment
import re.pinok.data.model.Post
import re.pinok.data.model.PhotoSizes
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.ui.components.AudioAttachmentList
import re.pinok.ui.components.CreatePostDialog
import re.pinok.ui.components.PhotoViewer
import re.pinok.ui.components.PlaylistAttachmentCard
import re.pinok.ui.components.RepostDialog
import re.pinok.util.AppLog
import re.pinok.util.toCountString
import re.pinok.util.toRelativeTime

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

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            errorText = null
            try {
                val p = app.apiClient.usersGet(null)
                profile = p
                if (p != null) {
                    val wall = app.apiClient.wallGet(ownerId = p.id, count = 20)
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
                val wall = app.apiClient.wallGet(ownerId = p.id, count = 20)
                posts = wall
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                AppLog.i("ProfileScreen", "Reloaded wall after new post: ${wall.size} posts")
            } catch (e: Exception) {
                AppLog.e("ProfileScreen", "Reload wall failed", e)
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
        item { ProfileHeader(profile = p) }
        item { CountersRow(profile = p) }
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
        item {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("  Выйти из аккаунта")
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
        if (posts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "На стене пока нет записей",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
}

@Composable
fun ProfileHeader(profile: UserProfile) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
        profile.status?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
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


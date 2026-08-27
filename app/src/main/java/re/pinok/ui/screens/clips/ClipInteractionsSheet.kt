package re.pinok.ui.screens.clips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.Comment
import re.pinok.data.model.UserProfile
import re.pinok.data.model.Video
import re.pinok.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "ClipInteractionsSheet"

/**
 * §37.12 Phase 4: Контекстное меню клипа (кнопка «...» / more-actions).
 *
 * Элементы меню (см. §37.9):
 *  - Подписаться/Отписаться от автора
 *  - Добавить в закладки / Убрать из закладок
 *  - Уведомления о новых клипах (toggle)
 *  - Пожаловаться
 *  - Скрыть автора
 *  - Заблокировать автора
 *  - Скопировать ссылку
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipMoreActionsSheet(
    clip: Video,
    onDismiss: () -> Unit,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit,
    onFavorite: () -> Unit,
    onReport: () -> Unit,
    onBanAuthor: () -> Unit,
    onCopyLink: () -> Unit,
    onToggleNotifications: () -> Unit = {},
    onHideAuthor: () -> Unit = {},
    onEditClip: () -> Unit = {},
    onDeleteClip: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Действия с клипом",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            // §37.9: Subscribe/Unsubscribe — для ВСЕХ clip'ов (раньше только group).
            MoreItem(
                icon = if (clip.isSubscribedToAuthor) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                title = if (clip.isSubscribedToAuthor) "Отписаться от автора" else "Подписаться на автора",
                onClick = {
                    if (clip.isSubscribedToAuthor) onUnsubscribe() else onSubscribe()
                    onDismiss()
                },
            )
            // §37.9: Уведомления о новых клипах (wall.subscribe/unsubscribe).
            MoreItem(
                icon = if (clip.isSubscribedToAuthor) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications,
                title = if (clip.isSubscribedToAuthor) "Выключить уведомления" else "Включить уведомления",
                onClick = {
                    onToggleNotifications()
                    onDismiss()
                },
            )
            MoreItem(
                icon = if (clip.isFavorited) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                title = if (clip.isFavorited) "Убрать из закладок" else "Добавить в закладки",
                onClick = {
                    onFavorite()
                    onDismiss()
                },
            )
            if (clip.canReportClip) {
                MoreItem(
                    icon = Icons.Filled.Flag,
                    title = "Пожаловаться",
                    onClick = {
                        onReport()
                        onDismiss()
                    },
                )
            }
            if (clip.ownerId != 0L) {
                MoreItem(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Скрыть автора",
                    onClick = {
                        onHideAuthor()
                        onDismiss()
                    },
                )
                MoreItem(
                    icon = Icons.Filled.Block,
                    title = "Заблокировать автора",
                    onClick = {
                        onBanAuthor()
                        onDismiss()
                    },
                )
            }
            MoreItem(
                icon = Icons.Filled.ContentCopy,
                title = "Скопировать ссылку",
                onClick = {
                    onCopyLink()
                    onDismiss()
                },
            )
            // §37.9: Редактировать (только для своего клипа).
            if (clip.canEditClip) {
                MoreItem(
                    icon = Icons.Outlined.Edit,
                    title = "Редактировать клип",
                    onClick = {
                        onEditClip()
                        onDismiss()
                    },
                )
            }
            // §37.9: Удалить (только для своего клипа).
            if (clip.canDeleteClip) {
                MoreItem(
                    icon = Icons.Outlined.Delete,
                    title = "Удалить клип",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {
                        onDeleteClip()
                        onDismiss()
                    },
                )
            }
        }
    }
}

/**
 * Лист «Поделиться клипом» — список чатов для отправки + кнопки «на стену» / «в story» / «копировать ссылку».
 * §37.9 меню «Поделиться».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipShareSheet(
    clip: Video,
    onDismiss: () -> Unit,
    onShareToChat: (peerId: Long, peerName: String) -> Unit,
    onShareToWall: () -> Unit,
    onCopyLink: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val app = remember { SovaApp.get(context) }
    val scope = rememberCoroutineScope()

    var chats by remember { mutableStateOf<List<re.pinok.data.model.Chat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(clip.id) {
        loading = true
        try {
            chats = app.apiClient.messagesGetConversations(count = 20)
        } catch (e: Exception) {
            AppLog.e(TAG, "messagesGetConversations error", e)
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Поделиться клипом",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            // Быстрые действия сверху.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickShareButton(
                    icon = Icons.Filled.Link,
                    label = "На стену",
                    onClick = {
                        onShareToWall()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                QuickShareButton(
                    icon = Icons.Filled.ContentCopy,
                    label = "Ссылка",
                    onClick = {
                        onCopyLink()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider()
            Text(
                text = "Отправить в чат",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (loading) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (chats.isEmpty()) {
                Text(
                    text = "Нет чатов",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    items(chats, key = { c -> c.peer.id }) { chat ->
                        val title = chat.peer.title?.takeIf { it.isNotBlank() } ?: "id${chat.peer.id}"
                        val avatar = chat.peer.photo
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onShareToChat(chat.peer.id, title)
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                if (avatar != null) {
                                    AsyncImage(
                                        model = avatar,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Лист «Комментарии к клипу» — список комментариев + поле ввода.
 * §37.9 кнопка Comment → wall.getComments + wall.createComment.
 *
 * Для clip VK использует тот же wall.getComments API, где
 * owner_id = clip.ownerId, post_id = clip.id (clip = video-attachable).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipCommentsSheet(
    clip: Video,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val app = remember { SovaApp.get(context) }
    val scope = rememberCoroutineScope()

    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var profiles by remember { mutableStateOf<Map<Long, UserProfile>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var newComment by remember { mutableStateOf("") }

    fun loadComments() {
        scope.launch {
            loading = true
            try {
                val r = app.apiClient.wallGetComments(
                    ownerId = clip.ownerId,
                    postId = clip.id,
                    count = 30,
                )
                comments = r.comments
                profiles = r.profiles
            } catch (e: Exception) {
                AppLog.e(TAG, "wallGetComments for clip ${clip.ownerId}_${clip.id} error", e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(clip.id, clip.ownerId) { loadComments() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Forum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Комментарии · ${clip.commentsCount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
            HorizontalDivider()
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Пока нет комментариев. Будьте первым!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    items(comments, key = { c -> c.id }) { comment ->
                        CommentItem(comment = comment, profiles = profiles)
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
            HorizontalDivider()
            // Поле ввода нового комментария.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newComment,
                    onValueChange = { newComment = it },
                    placeholder = { Text("Ваш комментарий…", fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                    maxLines = 3,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = newComment.trim()
                        if (text.isBlank() || sending) return@IconButton
                        scope.launch {
                            sending = true
                            try {
                                val cid = app.apiClient.wallCreateComment(
                                    ownerId = clip.ownerId,
                                    postId = clip.id,
                                    message = text,
                                )
                                if (cid > 0) {
                                    newComment = ""
                                    loadComments() // перезагружаем список
                                    android.widget.Toast.makeText(
                                        context, "Комментарий добавлен", android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        context, "Не удалось добавить комментарий", android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            } catch (e: Exception) {
                                AppLog.e(TAG, "wallCreateComment error", e)
                                android.widget.Toast.makeText(
                                    context, "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                sending = false
                            }
                        }
                    },
                    enabled = newComment.isNotBlank() && !sending,
                ) {
                    if (sending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = "Отправить",
                            tint = if (newComment.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────

@Composable
private fun MoreItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

@Composable
private fun QuickShareButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    profiles: Map<Long, UserProfile>,
) {
    val p = profiles[comment.fromId]
    val name = if (comment.fromId > 0) {
        p?.let { "${it.firstName} ${it.lastName}".trim() } ?: "id${comment.fromId}"
    } else if (comment.fromId < 0) {
        "Сообщество"
    } else "Гость"
    val timeText = if (comment.date > 0) {
        try {
            SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
                .format(Date(comment.date * 1000L))
        } catch (_: Exception) { null }
    } else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            p?.photo100?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (timeText != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

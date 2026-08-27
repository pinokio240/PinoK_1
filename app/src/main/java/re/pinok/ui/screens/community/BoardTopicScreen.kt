package re.pinok.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.BoardComment
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime

/**
 * Шаг 4 (#32d): Экран темы обсуждения сообщества.
 *
 * Загружает [boardGetComments] с пагинацией (30 за раз, по образцу CommunityScreen.loadMoreWall).
 * Открывается тапом по теме на вкладке «Обсуждения» CommunityScreen.
 *
 * ВАЖНО: BoardComment содержит только creatorId (from_id), без имени/аватара.
 * VK API board.getComments с extended=1 возвращает profiles/groups, но текущий
 * boardGetComments их не парсит. Для MVP показываем creatorId + текст + дату.
 * Полноценная реализация (имя + аватар) — отдельный шаг: распарсить profiles
 * в boardGetComments или подгружать через usersGet батчем.
 *
 * @param groupId  ID группы (положительный, как в groups[].id)
 * @param topicId  ID темы
 * @param title    Заголовок темы (для TopAppBar)
 * @param onBack   Callback возврата
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardTopicScreen(
    groupId: Long,
    topicId: Long,
    title: String,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<BoardComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Первая загрузка.
    LaunchedEffect(groupId, topicId) {
        scope.launch {
            loading = true
            errorText = null
            try {
                comments = app.apiClient.boardGetComments(groupId = groupId, topicId = topicId, count = 30, offset = 0)
                endReached = comments.size < 30
                AppLog.i("BoardTopicScreen", "Loaded ${comments.size} comments for topic $topicId")
            } catch (e: Exception) {
                AppLog.e("BoardTopicScreen", "boardGetComments failed", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Пагинация.
    fun loadMore() {
        if (loadingMore || endReached || comments.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val more = app.apiClient.boardGetComments(
                    groupId = groupId, topicId = topicId, count = 30, offset = comments.size,
                )
                val newOnes = more.filter { nc -> comments.none { it.id == nc.id } }
                if (newOnes.isEmpty()) {
                    endReached = true
                } else {
                    comments = (comments + newOnes).distinctBy { it.id }
                    if (more.size < 30) endReached = true
                }
            } catch (e: Exception) {
                AppLog.e("BoardTopicScreen", "loadMore failed", e)
            } finally {
                loadingMore = false
            }
        }
    }

    // Триггер пагинации — за 3 элемента до конца.
    LaunchedEffect(listState, comments.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
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
    ) { innerPadding ->
        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            errorText != null && comments.isEmpty() -> {
                val msg = errorText
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = msg ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            comments.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "В теме нет комментариев",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, top = 8.dp, bottom = 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(comments, key = { it.id }) { comment ->
                        BoardCommentCard(comment = comment)
                    }
                    // Футер пагинации.
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
                            endReached -> {
                                Text(
                                    text = "Это все комментарии",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Карточка комментария темы обсуждения.
 *
 * Пока без аватара/имени автора (BoardComment содержит только creatorId).
 * Показываем: круг-плейсхолдер с ID, текст, дату. Полноценная реализация —
 * после распарсивания profiles в boardGetComments.
 */
@Composable
private fun BoardCommentCard(comment: BoardComment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Плейсхолдер аватара — круг с ID автора.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (comment.creatorId > 0) "U" else "G",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ID автора (U = user, G = group); до распарсивания profiles.
                    Text(
                        text = if (comment.creatorId > 0) "Пользователь #${comment.creatorId}"
                        else "Сообщество #${-comment.creatorId}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = comment.created.toAbsoluteTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = comment.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}


// File: ui/screens/community/AdminCommentsScreen.kt
package re.pinok.ui.screens.community

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.data.model.Comment
import re.pinok.ui.components.ErrorView
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime

// ═══════════════════════════════════════════════════════════════════════════
// C8 (#ADMIN-COMMENTS): «Комментарии» сообщества — фильтры модерации и лента
// последних комментариев.
// Паттерн экрана: C6/C7 (AdminChatsScreen/AdminSectionsScreen) — Scaffold +
// TopAppBar + ErrorView + lastApiError + Toast.
//   Фильтры:
//     READ  — groupsGetCommentFilters (groups.getSettings, legacy settings);
//     WRITE — официальный путь groupsSetCommentFilters (groups.edit, без hash);
//             при неудаче — legacy-фолбэк groupsSaveCommentsLegacy
//             (POST groupsedit.php?act=save_comments) — ТОЛЬКО если hash
//             получен через groupsGetLegacySettingsHash (иначе запрос не шлём,
//             показываем честную ошибку).
//   Лента: wall.get по последним постам + wall.getComments (desc) — честная
//     аппроксимация (официальной ленты комментариев сообщества в API нет).
//   Действия: удалить (wallDeleteComment), ответить (wallCreateComment,
//     reply_to_comment), бан автора (groupsBanUser, только from_id > 0).
// ═══════════════════════════════════════════════════════════════════════════

/** C8: комментарий из ленты с контекстом поста и именем автора. */
private data class RecentComment(
    val postId: Long,
    val postText: String,
    val comment: Comment,
    val authorName: String,
    val authorPhoto: String?,
)

/** C8: короткий Toast-хелпер (чтобы не дублировать makeText в лямбдах). */
private fun toastShort(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminCommentsScreen(
    groupId: Long,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Фильтры комментариев ──
    var filters by remember { mutableStateOf<VKApiClient.GroupCommentFilters?>(null) }
    var filtersLoading by remember { mutableStateOf(true) }
    var filtersError by remember { mutableStateOf<String?>(null) }
    var savingFilters by remember { mutableStateOf(false) }
    var fEnableReplies by remember { mutableStateOf(false) }
    var fDisableRepliesFromGroups by remember { mutableStateOf(false) }
    var fObsceneFilter by remember { mutableStateOf(false) }
    var fObsceneStopwords by remember { mutableStateOf(false) }
    var fToxicFilter by remember { mutableStateOf(false) }
    var fWords by remember { mutableStateOf("") }
    var fRecognizePhoto by remember { mutableStateOf(false) }

    // ── Лента последних комментариев ──
    var comments by remember { mutableStateOf<List<RecentComment>>(emptyList()) }
    var commentsLoading by remember { mutableStateOf(true) }
    var commentsError by remember { mutableStateOf<String?>(null) }
    var commentsInfo by remember { mutableStateOf<String?>(null) }

    // ── Действия над комментарием ──
    var replyEntry by remember { mutableStateOf<RecentComment?>(null) }
    var banEntry by remember { mutableStateOf<RecentComment?>(null) }
    var busyCommentId by remember { mutableStateOf<Long?>(null) }

    fun loadCommentsAll() {
        scope.launch {
            commentsLoading = true
            commentsError = null
            commentsInfo = null
            try {
                // Аппроксимация: последние 10 постов стены, до 5 свежих
                // комментариев на пост (sort=desc), сортировка по дате.
                val posts = app.apiClient.wallGet(ownerId = -groupId, count = 10)
                if (posts.isEmpty()) {
                    val err = app.apiClient.lastApiError
                    if (err.isNullOrBlank()) {
                        commentsInfo = "Постов на стене нет"
                    } else {
                        commentsError = err
                    }
                } else {
                    val result = mutableListOf<RecentComment>()
                    for (post in posts.take(10)) {
                        if (post.id <= 0L) continue
                        val res = app.apiClient.wallGetComments(
                            ownerId = -groupId,
                            postId = post.id,
                            count = 5,
                            sort = "desc",
                            threadItemsCount = 0,
                        )
                        val profiles = res.profiles
                        for (c in res.comments) {
                            val author = profiles[c.fromId]
                            val name = if (author != null) {
                                "${author.firstName} ${author.lastName}".trim()
                            } else {
                                ""
                            }
                            val photo = if (author != null) author.photo100 else null
                            result.add(
                                RecentComment(
                                    postId = post.id,
                                    postText = post.text,
                                    comment = c,
                                    authorName = if (name.isBlank()) "id${c.fromId}" else name,
                                    authorPhoto = photo,
                                ),
                            )
                        }
                    }
                    result.sortByDescending { it.comment.date }
                    comments = result.take(60)
                    if (result.isEmpty()) {
                        commentsInfo = "Свежих комментариев нет (выборка по последним постам)"
                    }
                }
            } catch (e: Exception) {
                AppLog.e("AdminComments", "load comments failed", e)
                // NULL-ЯВНО: текст ошибки для ErrorView, null-ветка тривиальна (UI-дефолт).
                commentsError = e.message ?: "Ошибка загрузки комментариев"
            } finally {
                commentsLoading = false
            }
        }
    }

    fun loadFilters() {
        scope.launch {
            filtersLoading = true
            filtersError = null
            try {
                val st = app.apiClient.groupsGetCommentFilters(groupId)
                if (st == null) {
                    // NULL-ЯВНО: lastApiError может быть пустым — подставляем фолбэк для ErrorView.
                    filtersError = app.apiClient.lastApiError?.takeIf { it.isNotBlank() }
                        ?: "Не удалось загрузить фильтры комментариев"
                } else {
                    filters = st
                    fEnableReplies = st.enableReplies == true
                    fDisableRepliesFromGroups = st.disableRepliesFromGroups == true
                    fObsceneFilter = st.obsceneFilter == true
                    fObsceneStopwords = st.obsceneStopwords == true
                    fToxicFilter = st.toxicFilter == true
                    fWords = st.obsceneWords.orEmpty()
                    fRecognizePhoto = st.recognizePhoto == true
                }
            } catch (e: Exception) {
                AppLog.e("AdminComments", "load filters failed", e)
                // NULL-ЯВНО: текст ошибки для ErrorView, null-ветка тривиальна (UI-дефолт).
                filtersError = e.message ?: "Ошибка загрузки фильтров"
            } finally {
                filtersLoading = false
            }
        }
    }

    fun loadAll() {
        loadCommentsAll()
        loadFilters()
    }

    /** Сохранение фильтров: официальный groups.edit → legacy-фолбэк с hash. */
    fun saveFilters() {
        if (savingFilters) return
        if (filters == null) return
        savingFilters = true
        scope.launch {
            try {
                val payload = VKApiClient.GroupCommentFilters(
                    enableReplies = fEnableReplies,
                    disableRepliesFromGroups = fDisableRepliesFromGroups,
                    obsceneFilter = fObsceneFilter,
                    obsceneStopwords = fObsceneStopwords,
                    toxicFilter = fToxicFilter,
                    obsceneWords = fWords.trim(),
                    recognizePhoto = null,
                )
                var ok = app.apiClient.groupsSetCommentFilters(groupId, payload)
                var note = ""
                if (!ok) {
                    // Гейт hash: legacy-запрос шлём ТОЛЬКО если hash получен.
                    val hash = app.apiClient.groupsGetLegacySettingsHash(groupId)
                    if (hash.isNullOrBlank()) {
                        AppLog.w("AdminComments", "official failed and no legacy hash")
                    } else {
                        ok = app.apiClient.groupsSaveCommentsLegacy(groupId, hash, payload)
                        if (ok) note = " (через веб-legacy save_comments)"
                    }
                }
                if (ok) {
                    toastShort(context, "Сохранено$note")
                    loadFilters()
                } else {
                    val err = app.apiClient.lastApiError
                    toastShort(
                        context,
                        if (err.isNullOrBlank()) {
                            "Не удалось сохранить: официальный путь не сработал и legacy-хэш не получен"
                        } else {
                            "Не удалось сохранить: $err"
                        },
                    )
                }
            } catch (e: Exception) {
                AppLog.e("AdminComments", "save filters failed", e)
                toastShort(context, "Ошибка: ${e.message}")
            } finally {
                savingFilters = false
            }
        }
    }

    fun deleteComment(entry: RecentComment) {
        if (busyCommentId != null) return
        busyCommentId = entry.comment.id
        scope.launch {
            try {
                val ok = app.apiClient.wallDeleteComment(ownerId = -groupId, commentId = entry.comment.id)
                // NULL-ЯВНО: lastApiError может быть null — подставляем фолбэк текста в тост.
                toastShort(
                    context,
                    if (ok) {
                        "Комментарий удалён"
                    } else {
                        "Ошибка: ${app.apiClient.lastApiError ?: "не удалось"}"
                    },
                )
                if (ok) loadCommentsAll()
            } catch (e: Exception) {
                AppLog.e("AdminComments", "delete failed", e)
                toastShort(context, "Ошибка: ${e.message}")
            } finally {
                busyCommentId = null
            }
        }
    }

    fun replyTo(entry: RecentComment, message: String) {
        if (message.isBlank()) return
        if (busyCommentId != null) return
        busyCommentId = entry.comment.id
        scope.launch {
            try {
                val newId = app.apiClient.wallCreateComment(
                    ownerId = -groupId,
                    postId = entry.postId,
                    message = message.trim(),
                    replyToComment = entry.comment.id,
                )
                if (newId > 0L) {
                    toastShort(context, "Ответ опубликован")
                    replyEntry = null
                    loadCommentsAll()
                } else {
                    // NULL-ЯВНО: lastApiError может быть null — подставляем фолбэк текста в тост.
                    toastShort(context, "Ошибка: ${app.apiClient.lastApiError ?: "не удалось ответить"}")
                }
            } catch (e: Exception) {
                AppLog.e("AdminComments", "reply failed", e)
                toastShort(context, "Ошибка: ${e.message}")
            } finally {
                busyCommentId = null
            }
        }
    }

    fun banAuthor(entry: RecentComment) {
        if (entry.comment.fromId <= 0L) {
            toastShort(context, "Автор — сообщество, бан неприменим")
            banEntry = null
            return
        }
        if (busyCommentId != null) return
        busyCommentId = entry.comment.id
        scope.launch {
            try {
                val ok = app.apiClient.groupsBanUser(
                    groupId = groupId,
                    userId = entry.comment.fromId,
                    reason = 0,
                    endDate = 0L,
                )
                toastShort(
                    context,
                    if (ok) {
                        "Автор добавлен в чёрный список"
                    } else {
                        // NULL-ЯВНО: lastApiError может быть null — подставляем фолбэк текста в тост.
                        "Ошибка: ${app.apiClient.lastApiError ?: "не удалось"}"
                    },
                )
                if (ok) banEntry = null
            } catch (e: Exception) {
                AppLog.e("AdminComments", "ban failed", e)
                toastShort(context, "Ошибка: ${e.message}")
            } finally {
                busyCommentId = null
            }
        }
    }

    LaunchedEffect(groupId) { loadAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Комментарии") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { pad ->
        when {
            filtersLoading && commentsLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            filtersError != null && filters == null -> ErrorView(
                message = filtersError,
                onRetry = { loadAll() },
                modifier = Modifier.padding(pad),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                // ── Фильтры комментариев ──
                Text("Фильтры комментариев", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Официальная запись — groups.edit; при его отказе — веб-форма save_comments (только с полученным hash)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                val st = filters
                if (st != null) {
                    CommentFilterSwitch(
                        label = "Комментарии включены",
                        checked = fEnableReplies,
                        enabled = !savingFilters,
                        onChange = { fEnableReplies = it },
                    )
                    CommentFilterSwitch(
                        label = "Запретить ответы от сообществ",
                        checked = fDisableRepliesFromGroups,
                        enabled = !savingFilters,
                        onChange = { fDisableRepliesFromGroups = it },
                    )
                    CommentFilterSwitch(
                        label = "Фильтр мата",
                        checked = fObsceneFilter,
                        enabled = !savingFilters,
                        onChange = { fObsceneFilter = it },
                    )
                    CommentFilterSwitch(
                        label = "Токсичные комментарии",
                        checked = fToxicFilter,
                        enabled = !savingFilters,
                        onChange = { fToxicFilter = it },
                    )
                    CommentFilterSwitch(
                        label = "Стоп-слова",
                        checked = fObsceneStopwords,
                        enabled = !savingFilters,
                        onChange = { fObsceneStopwords = it },
                    )
                    if (fObsceneStopwords) {
                        OutlinedTextField(
                            value = fWords,
                            onValueChange = { fWords = it },
                            label = { Text("Стоп-слова") },
                            supportingText = { Text("Ключевые слова, комментарии с ними отклоняются") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            enabled = !savingFilters,
                        )
                    }
                    Text(
                        "Распознавание фото на комментариях: ${if (fRecognizePhoto) "включено" else "выключено"} (read-only)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    // NULL-ЯВНО: filtersError может быть null/пуст — подставляем фолбэк для ErrorView.
                    ErrorView(
                        message = filtersError?.takeIf { it.isNotBlank() } ?: "Фильтры не загружены",
                        onRetry = { loadFilters() },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { saveFilters() },
                    enabled = !savingFilters && st != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (savingFilters) "Сохранение…" else "Сохранить фильтры")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // ── Лента последних комментариев ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Последние комментарии",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { loadCommentsAll() }, enabled = !commentsLoading) {
                        Text(if (commentsLoading) "Загрузка…" else "Обновить")
                    }
                }
                Text(
                    "Выборка по последним постам стены (официальной ленты комментариев нет — аппроксимация)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val commErr = commentsError
                if (commErr != null) {
                    ErrorView(
                        message = commErr,
                        onRetry = { loadCommentsAll() },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                val commInfo = commentsInfo
                if (commInfo != null) {
                    Text(
                        commInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                comments.forEach { entry ->
                    AdminCommentCard(
                        entry = entry,
                        busy = busyCommentId == entry.comment.id,
                        onDelete = { deleteComment(entry) },
                        onReply = { replyEntry = entry },
                        onBan = { banEntry = entry },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    val reply = replyEntry
    if (reply != null) {
        var message by remember(reply.comment.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (busyCommentId == null) replyEntry = null },
            title = { Text("Ответить на комментарий") },
            text = {
                Column {
                    Text(
                        reply.authorName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        reply.comment.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Текст ответа") },
                        enabled = busyCommentId == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = message.isNotBlank() && busyCommentId == null,
                    onClick = { replyTo(reply, message) },
                ) { Text("Ответить") }
            },
            dismissButton = {
                TextButton(enabled = busyCommentId == null, onClick = { replyEntry = null }) { Text("Отмена") }
            },
        )
    }

    val ban = banEntry
    if (ban != null) {
        AlertDialog(
            onDismissRequest = { if (busyCommentId == null) banEntry = null },
            title = { Text("Бан автора?") },
            text = {
                Text(
                    "${ban.authorName} (id${ban.comment.fromId}) будет добавлен в чёрный список сообщества навсегда. " +
                        "Комментарии автора не удаляются.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = busyCommentId == null,
                    onClick = { banAuthor(ban) },
                ) { Text("Забанить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = busyCommentId == null, onClick = { banEntry = null }) { Text("Отмена") }
            },
        )
    }
}

/** C8: строка тумблера фильтра (паттерн SwitchRow из AdminSectionsScreen). */
@Composable
private fun CommentFilterSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** C8: карточка комментария из ленты. */
@Composable
private fun AdminCommentCard(
    entry: RecentComment,
    busy: Boolean,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onBan: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val photo = entry.authorPhoto
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                Text(
                    entry.comment.date.toAbsoluteTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            entry.comment.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
        val postPreview = entry.postText.ifBlank { "запись без текста" }
        Text(
            "Пост ${entry.postId}: ${postPreview.take(80)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(modifier = Modifier.padding(top = 2.dp)) {
            TextButton(onClick = onDelete, enabled = !busy) { Text("Удалить") }
            TextButton(onClick = onReply, enabled = !busy) { Text("Ответить") }
            TextButton(onClick = onBan, enabled = !busy) { Text("Бан автора") }
        }
    }
}
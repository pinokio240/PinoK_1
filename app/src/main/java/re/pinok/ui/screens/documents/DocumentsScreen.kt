package re.pinok.ui.screens.documents

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.data.model.DocFile
import re.pinok.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen() {
    val app = SovaApp.get()
    val scope = rememberCoroutineScope()
    var docs by remember { mutableStateOf<List<DocFile>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // Fix #83: пагинация документов + pull-to-refresh.
    val pageSize = 50
    var isRefreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        scope.launch {
            loading = true
            endReached = false
            errorText = null
            try {
                val list = app.apiClient.docsGet(count = pageSize)
                // Fix #53: защитная дедупликация — LazyColumn keys должны быть уникальны.
                docs = list
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                if (list.size < pageSize) endReached = true
                AppLog.i("DocumentsScreen", "Loaded ${list.size} docs")
                if (list.isEmpty()) {
                    errorText = app.apiClient.lastApiError ?: "Нет документов"
                }
            } catch (e: Exception) {
                AppLog.e("DocumentsScreen", "Failed to load docs", e)
                errorText = "Ошибка: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    // Fix #83: pull-to-refresh — перезагрузка первой страницы.
    fun refreshDocs() {
        scope.launch {
            isRefreshing = true
            try {
                val list = app.apiClient.docsGet(count = pageSize)
                docs = list
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .distinctBy { "${it.ownerId}_${it.id}" }
                endReached = (list.size < pageSize)
                errorText = null
            } catch (e: Exception) {
                AppLog.w("DocumentsScreen", "refreshDocs failed: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // Fix #83: пагинация — подгрузка следующих документов через offset.
    fun loadMoreDocs() {
        if (loadingMore || endReached || docs.isEmpty()) return
        scope.launch {
            loadingMore = true
            try {
                val offset = docs.size
                val page = app.apiClient.docsGet(count = pageSize, offset = offset)
                    .filter { it.id > 0 && it.ownerId != 0L }
                    .filter { np -> docs.none { it.id == np.id && it.ownerId == np.ownerId } }
                if (page.isNotEmpty()) {
                    docs = (docs + page).distinctBy { "${it.ownerId}_${it.id}" }
                }
                if (page.size < pageSize) endReached = true
            } catch (e: Exception) {
                AppLog.w("DocumentsScreen", "loadMoreDocs failed: ${e.message}")
            } finally {
                loadingMore = false
            }
        }
    }

    // Fix #83: бесконечная пагинация — триггер при скролле к концу.
    LaunchedEffect(listState, docs.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= docs.size - 3 && docs.isNotEmpty()
        }
        .distinctUntilChanged()
        .filter { it }
        .collect { loadMoreDocs() }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (docs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = errorText ?: "Нет документов",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Fix #83: PullToRefreshBox — pull-to-refresh документов.
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { refreshDocs() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            items(docs, key = { "${it.ownerId}_${it.id}" }) { doc ->
                DocRow(doc = doc)
                Box(
                    modifier = Modifier.fillMaxWidth().height(1.dp)
                        .padding(horizontal = 76.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                )
            }
            // Fix #83: футер пагинации.
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
                            text = "Это все документы",
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocRow(doc: DocFile) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            // audit Medium #5: Toast-фидбек вместо пустого TODO.
            Toast.makeText(context, "Документ «${doc.title}.${doc.ext}» — в разработке", Toast.LENGTH_SHORT).show()
        }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = iconForDoc(doc),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${doc.ext.uppercase()} • ${doc.sizeLabel} • ${doc.typeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (doc.date > 0) {
                Text(
                    text = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))
                        .format(Date(doc.date * 1000)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        IconButton(onClick = {
            // TODO: Trigger download via TrackDownloadManager / browser intent
            AppLog.i("DocumentsScreen", "Download requested: ${doc.title}.${doc.ext}")
        }) {
            Icon(Icons.Outlined.Download, contentDescription = "Скачать")
        }
    }
}

private fun iconForDoc(doc: DocFile): ImageVector = when {
    doc.isImage -> Icons.Outlined.Image
    doc.isGif -> Icons.Outlined.Image
    doc.ext.lowercase() == "pdf" -> Icons.Outlined.PictureAsPdf
    doc.type == 6 -> Icons.Outlined.Movie
    else -> Icons.Outlined.Description
}

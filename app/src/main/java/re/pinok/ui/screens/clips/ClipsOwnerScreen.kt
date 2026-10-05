package re.pinok.ui.screens.clips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import re.pinok.SovaApp
import re.pinok.data.model.Video
import re.pinok.util.AppLog

private const val TAG = "ClipsOwnerScreen"
private const val CLIPS_COUNT = 30

/**
 * Лента клипов автора (пользователя или сообщества).
 *
 * Грузит вертикальные клипы через [re.pinok.api.VKApiClient.shortVideoGetOwnerVideos]
 * (метод `shortVideo.getOwnerVideos`, которым VK web открывает вкладку «Клипы»
 * на странице профиля/сообщества). Тап по карточке → [onVideoClick] (по эталону
 * открывается оверлей-плеер VideoHolder).
 *
 * Метод без курсора → грузим count=30 одним разом; пагинации нет.
 * При ошибке — текст + кнопка «Повторить»; если клипов нет — «Клипов нет».
 *
 * @param ownerId владелец клипов (положительный — пользователь, отрицательный — сообщество)
 * @param onBack закрыть экран
 * @param onVideoClick открыть клип на просмотр (overlay VideoPlayer)
 * @param onAuthorClick открыть профиль автора клипа
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipsOwnerScreen(
    ownerId: Long,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit = {},
    onAuthorClick: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    var clips by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    // Счётчик перезагрузок — кнопка «Повторить» просто инкрементирует его.
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(ownerId, reloadKey) {
        loading = true
        error = false
        val app = SovaApp.get(context)
        try {
            val list = app.apiClient.shortVideoGetOwnerVideos(ownerId = ownerId, count = CLIPS_COUNT)
            clips = list
        } catch (e: Exception) {
            AppLog.e(TAG, "Ошибка загрузки клипов автора $ownerId: ${e.message}", e)
            error = true
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ownerId < 0) "Клипы сообщества" else "Клипы пользователя") },
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
        ) {
            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "Не удалось загрузить клипы",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { reloadKey++ }) {
                            Text("Повторить")
                        }
                    }
                }
                clips.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Клипов нет",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(clips) { clip ->
                            ClipsOwnerCard(
                                video = clip,
                                onClick = { onVideoClick(clip) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Карточка клипа автора — вертикальное превью (портрет), внизу название и просмотры. */
@Composable
private fun ClipsOwnerCard(
    video: Video,
    onClick: () -> Unit,
) {
    val thumbUrl = video.thumbUrl
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                modifier = Modifier.align(Alignment.Center).size(46.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (video.title.isNotBlank()) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (video.views > 0) {
                Text(
                    "${video.views} просмотров",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
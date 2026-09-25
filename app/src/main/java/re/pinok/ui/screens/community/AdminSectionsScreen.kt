// File: ui/screens/community/AdminSectionsScreen.kt
package re.pinok.ui.screens.community

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.SovaApp
import re.pinok.api.VKApiClient
import re.pinok.ui.components.ErrorView
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════════════════════
// C7 (#ADMIN-SECTIONS): «Разделы» сообщества (админ-блок).
// READ  — groups.getSettings {group_id} (официальный метод; коды значений
//         сверены с официальным vk-java-sdk: GetSettingsResponse + enum'ы
//         GroupPhotos/GroupVideo/GroupAudio/GroupDocs/GroupTopics/GroupWiki).
// WRITE — groups.edit {group_id, wall, topics, photos, video, audio, links,
//         events, places, contacts, docs, wiki, main_section,
//         secondary_section} — единый метод записи настроек (W35-b).
// Значения разделов: 0 — выключен, 1 — открыт, 2 — ограничен
// (стена: 3 — закрытая). main/secondary_section — коды GroupFullSection.
// market-полей в groups.getSettings нет — «Товары» этим экраном не пишутся.
// ═══════════════════════════════════════════════════════════════════════════

/** Трёхпозиционные разделы: ключ поля → подпись (порядок как в web-админке). */
private val TRI_STATE_SECTIONS = listOf(
    "topics" to "Обсуждения",
    "photos" to "Фотографии",
    "video" to "Видеозаписи",
    "audio" to "Аудиозаписи",
    "docs" to "Документы",
    "wiki" to "Материалы",
)

/** Значения трёхпозиционного раздела (0/1/2). */
private val TRI_STATE_VALUES = listOf(
    0 to "Выключен",
    1 to "Открытый",
    2 to "Ограниченный",
)

/** Значения стены (0..3). */
private val WALL_VALUES = listOf(
    0 to "Выключена",
    1 to "Открытая",
    2 to "Ограниченная",
    3 to "Закрытая",
)

/** Коды основного/дополнительного раздела (GroupFullSection, vk-java-sdk). */
private val SECTION_CODES = listOf(
    0 to "Нет",
    1 to "Фотографии",
    2 to "Обсуждения",
    3 to "Аудиозаписи",
    4 to "Видеозаписи",
    5 to "Товары",
    14 to "Документы",
)

/** Чтение значения трёхпозиционного поля по ключу. */
private fun sectionsGet(s: VKApiClient.GroupSections, key: String): Int = when (key) {
    "topics" -> s.topics
    "photos" -> s.photos
    "video" -> s.video
    "audio" -> s.audio
    "docs" -> s.docs
    "wiki" -> s.wiki
    else -> 0
}

/** Запись значения трёхпозиционного поля по ключу (copy). */
private fun sectionsSet(s: VKApiClient.GroupSections, key: String, value: Int): VKApiClient.GroupSections =
    when (key) {
        "topics" -> s.copy(topics = value)
        "photos" -> s.copy(photos = value)
        "video" -> s.copy(video = value)
        "audio" -> s.copy(audio = value)
        "docs" -> s.copy(docs = value)
        "wiki" -> s.copy(wiki = value)
        else -> s
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSectionsScreen(
    groupId: Long,
    onBack: () -> Unit,
) {
    val app = SovaApp.get()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Прочитанный снимок (для кнопки «Сохранить») и текущее состояние формы.
    var loaded by remember { mutableStateOf<VKApiClient.GroupSections?>(null) }
    var cur by remember { mutableStateOf<VKApiClient.GroupSections?>(null) }

    // Диалоги выбора основного/дополнительного раздела.
    var pickMain by remember { mutableStateOf(false) }
    var pickSecondary by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val st = app.apiClient.groupsGetSettings(groupId)
                if (st == null) {
                    error = app.apiClient.lastApiError?.takeIf { it.isNotBlank() }
                        ?: "Не удалось загрузить настройки"
                } else {
                    loaded = st
                    cur = st
                }
            } catch (e: Exception) {
                AppLog.e("AdminSections", "load failed", e)
                error = e.message ?: "Ошибка загрузки"
            } finally {
                loading = false
            }
        }
    }

    fun save() {
        val s = cur ?: return
        if (saving) return
        saving = true
        scope.launch {
            try {
                val ok = app.apiClient.groupsEditSections(groupId, s)
                if (ok) {
                    Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()
                    load()
                } else {
                    val err = app.apiClient.lastApiError
                    Toast.makeText(
                        context,
                        if (err.isNullOrBlank()) "Не удалось сохранить" else "Ошибка: $err",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                AppLog.e("AdminSections", "save failed", e)
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(groupId) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Разделы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { pad ->
        when {
            loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            error != null -> ErrorView(
                message = error,
                onRetry = { load() },
                modifier = Modifier.padding(pad),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                val s = cur ?: return@Column

                // ── Стена (0..3) ──
                Text("Стена", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ChipRow(values = WALL_VALUES, selected = s.wall) { v -> cur = s.copy(wall = v) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // ── Трёхпозиционные разделы ──
                TRI_STATE_SECTIONS.forEachIndexed { index, (key, label) ->
                    if (index > 0) Spacer(Modifier.height(14.dp))
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        values = TRI_STATE_VALUES,
                        selected = sectionsGet(s, key),
                    ) { v -> cur = sectionsSet(s, key, v) }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // ── Двухпозиционные разделы ──
                SwitchRow("Ссылки", s.links != 0) { on -> cur = s.copy(links = if (on) 1 else 0) }
                SwitchRow("События", s.events != 0) { on -> cur = s.copy(events = if (on) 1 else 0) }
                SwitchRow("Места", s.places != 0) { on -> cur = s.copy(places = if (on) 1 else 0) }
                SwitchRow("Контакты", s.contacts != 0) { on -> cur = s.copy(contacts = if (on) 1 else 0) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // ── Основной / дополнительный раздел ──
                PickerRow(
                    label = "Основной раздел",
                    value = s.mainSection,
                    onOpen = { pickMain = true },
                )
                Spacer(Modifier.height(8.dp))
                PickerRow(
                    label = "Дополнительный раздел",
                    value = s.secondarySection,
                    onOpen = { pickSecondary = true },
                )

                Text(
                    "«Товары» этим экраном не переключаются: у магазина свои настройки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { save() },
                    enabled = !saving && cur != loaded,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (saving) "Сохранение…" else "Сохранить")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (pickMain) {
        SectionPickDialog(
            title = "Основной раздел",
            current = cur?.mainSection,
            onPick = { v ->
                cur = cur?.copy(mainSection = v)
                pickMain = false
            },
            onDismiss = { pickMain = false },
        )
    }
    if (pickSecondary) {
        SectionPickDialog(
            title = "Дополнительный раздел",
            current = cur?.secondarySection,
            onPick = { v ->
                cur = cur?.copy(secondarySection = v)
                pickSecondary = false
            },
            onDismiss = { pickSecondary = false },
        )
    }
}

/** Ряд чипов для выбора значения (0..3). */
@Composable
private fun ChipRow(
    values: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        values.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

/** Двухпозиционный раздел: подпись + Switch. */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
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
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Строка выбора раздела: подпись + текущее значение, тап → диалог. */
@Composable
private fun PickerRow(
    label: String,
    value: Int?,
    onOpen: () -> Unit,
) {
    val text = value
        ?.let { v -> SECTION_CODES.firstOrNull { it.first == v }?.second ?: "Другое ($v)" }
        ?: "Не удалось прочитать"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** Диалог выбора кода раздела (RadioButton по SECTION_CODES). */
@Composable
private fun SectionPickDialog(
    title: String,
    current: Int?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SECTION_CODES.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == value, onClick = { onPick(value) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

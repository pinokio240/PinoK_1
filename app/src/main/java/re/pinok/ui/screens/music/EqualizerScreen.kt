// File: ui/screens/music/EqualizerScreen.kt
package re.pinok.ui.screens.music

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import re.pinok.data.model.EqualizerPreset
import re.pinok.media.CustomPreset
import re.pinok.media.CustomPresetStore
import re.pinok.media.EqualizerFeatureFlags
import re.pinok.media.EqualizerHelper
import re.pinok.media.PlayerConnection
import re.pinok.ui.components.SpectrumVisualizer

// ══════════════════════════════════════════════════════════════════════
//  Этап 2 (#Equalizer): Полноэкранный эквалайзер.
//
//  5 вкладок (видимость регулируется [EqualizerFeatureFlags]):
//    1. Пресеты      — список встроенных, тап = применить
//    2. Полосы EQ    — 9 вертикальных слайдеров ±15 dB
//    3. Bass + Virt  — 2 горизонтальных слайдера 0..1000 + switches
//    4. Reverb       — 6 пресетов (radio) + switch
//    5. Loudness     — slider 0..15 dB + switch
//
//  Упрощённый EQ остаётся в AudioPlayerScreen (BottomSheet с пресетами +
//  master switch + 5 полос). Этот экран — для тонкой настройки.
//
//  Источник паттерна: декомпиляция Equalizer v6.3.5.7
//  (см. reference/equalizer/, EQUALIZER_INTEGRATION_PLAN.md).
// ══════════════════════════════════════════════════════════════════════

/** Подписи частот для 9 полос EQ (как в AudioPlayerScreen). */
private val eqFrequencyLabels = listOf(
    "60Hz", "170Hz", "310Hz", "600Hz", "1kHz", "3kHz", "6kHz", "12kHz", "14kHz",
)

/** Описание вкладки эквалайзера (id + подпись). Вынесено на уровень файла,
 *  чтобы не пересоздавалось при рекомпозиции (local class ломает remember). */
private data class EqTabDef(val id: String, val label: String)

/**
 * @param onBack закрыть экран (назад в предыдущий маршрут).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Читаем feature-флаги ОДИН раз при открытии экрана. Если пользователь
    // поменяет флаги в Настройках и вернётся — экран пересоздастся (NavHost
    // recompose), и snapshot перечитается. Внутри сессии флаги не меняются.
    val flags = remember { EqualizerFeatureFlags.snapshot() }

    // ── Динамический список вкладок (только включённые эффекты) ──────────
    val tabs = remember(flags) {
        buildList {
            // Пресеты всегда видны (это базовая функция).
            add(EqTabDef("presets", "Пресеты"))
            if (flags.eqEnabled) add(EqTabDef("bands", "Полосы"))
            if (flags.bassEnabled || flags.virtualizerEnabled) add(EqTabDef("bassvirt", "Bass/Virt"))
            if (flags.reverbEnabled) add(EqTabDef("reverb", "Reverb"))
            if (flags.loudnessEnabled) add(EqTabDef("loud", "Loudness"))
        }
    }
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // ── Общее состояние: master switch + текущий пресет ──────────────────
    var masterEnabled by remember { mutableStateOf(false) }
    var currentPreset by remember { mutableStateOf<String?>(null) }
    // #EQ-SCO: индикатор «звонок» — если активен Bluetooth SCO (моно 8kHz),
    // Virtualizer+Reverb приостановлены. Обновляем при входе на экран и при
    // возврате фокуса (пользователь мог подключить гарнитуру вне экрана).
    var scoSuspended by remember { mutableStateOf(false) }

    // audioSessionId для SpectrumVisualizer (0 = не привязан).
    var audioSessionId by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        masterEnabled = EqualizerHelper.isEnabled() || EqualizerHelper.isSavedEnabled()
        currentPreset = EqualizerHelper.currentPresetName ?: EqualizerHelper.getSavedPresetName()
        scoSuspended = EqualizerHelper.engine()?.isScoSuspended() ?: false
        audioSessionId = EqualizerHelper.engine()?.attachedSessionId ?: 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── TopAppBar: ← Эквалайзер + master switch ──────────────────────
        TopAppBar(
            title = {
                Column {
                    Text("Эквалайзер", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = currentPreset ?: "Пользовательский",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {
                Switch(
                    checked = masterEnabled,
                    onCheckedChange = { on ->
                        masterEnabled = on
                        PlayerConnection.setEqualizerEnabled(on)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(Modifier.width(12.dp))
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // #EQ-SCO: баннер-предупреждение при активной звонковой гарнитуре.
        // Virtualizer+Reverb приостановлены до возврата на нормальный output.
        if (scoSuspended) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Активна Bluetooth-гарнитура (звонок)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            "Virtualizer и Reverb временно отключены (моно 8kHz). " +
                            "Вернутся автоматически после переключения на динамики/A2DP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

        // ─── Spectrum Visualizer (Этап 3 EQUALIZER_INTEGRATION_PLAN.md) ───
        // Real-time FFT-визуализатор: 32 полосы с градиентом. Активен только
        // когда эквалайзер включён и audio сессия привязана. Visualizer
        // берёт данные напрямую из AudioFlinger — НЕ нагружает ExoPlayer.
        SpectrumVisualizer(
            sessionId = audioSessionId,
            active = masterEnabled && audioSessionId != 0 && !scoSuspended,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )

        // ─── TabRow ───────────────────────────────────────────────────────
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 0.dp,
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.label, maxLines = 1) },
                )
            }
        }

        // ─── Контент вкладки ──────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val tab = tabs.getOrNull(page) ?: return@HorizontalPager
            when (tab.id) {
                "presets"  -> PresetsTab(
                    enabled = masterEnabled,
                    currentPreset = currentPreset,
                    onApplyPreset = { preset ->
                        PlayerConnection.setEqualizerPreset(preset)
                        currentPreset = preset.name
                    },
                    onApplyCustomPreset = { custom ->
                        EqualizerHelper.engine()?.applyCustomPreset(custom)
                        currentPreset = custom.name
                    },
                    onPresetSaved = { newName ->
                        // Сохраняем текущее состояние как новый пресет.
                        val engine = EqualizerHelper.engine()
                        if (engine != null) {
                            val snapshot = engine.snapshotCustomPreset(newName)
                            CustomPresetStore.upsert(
                                name = snapshot.name,
                                eqBands = snapshot.eqBands,
                                eqEnabled = snapshot.eqEnabled,
                                bassEnabled = snapshot.bassEnabled,
                                bassStrength = snapshot.bassStrength,
                                virtEnabled = snapshot.virtEnabled,
                                virtStrength = snapshot.virtStrength,
                                loudEnabled = snapshot.loudEnabled,
                                loudGainmB = snapshot.loudGainmB,
                                reverbEnabled = snapshot.reverbEnabled,
                                reverbPreset = snapshot.reverbPreset,
                            )
                            currentPreset = newName
                        }
                    },
                    onPresetDeleted = { id ->
                        CustomPresetStore.delete(id)
                    },
                )
                "bands"    -> BandsTab(enabled = masterEnabled)
                "bassvirt" -> BassVirtTab(
                    bassVisible = flags.bassEnabled,
                    virtVisible = flags.virtualizerEnabled,
                )
                "reverb"   -> ReverbTab()
                "loud"     -> LoudnessTab()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Пресеты (встроенные + пользовательские)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun PresetsTab(
    enabled: Boolean,
    currentPreset: String?,
    onApplyPreset: (EqualizerPreset) -> Unit,
    onApplyCustomPreset: (CustomPreset) -> Unit,
    onPresetSaved: (String) -> Unit,
    onPresetDeleted: (Long) -> Unit,
) {
    // Загружаем custom пресеты один раз при входе + храним в mutableStateListOf
    // чтобы UI обновлялся при добавлении/удалении без полной перезагрузки.
    val customPresets = remember { mutableStateListOf<CustomPreset>() }
    LaunchedEffect(Unit) {
        customPresets.clear()
        customPresets.addAll(CustomPresetStore.list())
    }

    // Диалог сохранения нового пресета.
    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    // Диалог подтверждения удаления.
    var pendingDelete by remember { mutableStateOf<CustomPreset?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = if (enabled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (enabled) "Эквалайзер включён" else "Эквалайзер выключен",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Тап по пресету применит его и включит EQ. " +
                                "Настрой полосы и нажми «+» чтобы сохранить свой.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Встроенные пресеты",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            items(EqualizerPreset.ALL) { preset ->
                val isActive = currentPreset == preset.name
                PresetCard(
                    preset = preset,
                    isActive = isActive,
                    onClick = { onApplyPreset(preset) },
                )
            }
            // ─── Пользовательские пресеты (Этап 4) ──────────────────────
            if (customPresets.isNotEmpty()) {
                item {
                    Text(
                        "Мои пресеты",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(customPresets) { custom ->
                    val isActive = currentPreset == custom.name
                    CustomPresetCard(
                        preset = custom,
                        isActive = isActive,
                        onClick = { onApplyCustomPreset(custom) },
                        onDelete = { pendingDelete = custom },
                    )
                }
            }
            // Нижний отступ чтобы FAB не перекрывал последний элемент.
            item { Spacer(Modifier.height(80.dp)) }
        }

        // FAB «Сохранить текущий как пресет».
        ExtendedFloatingActionButton(
            onClick = {
                newPresetName = ""
                showSaveDialog = true
            },
            icon = { Icon(Icons.Filled.Save, contentDescription = null) },
            text = { Text("Сохранить") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    // ─── Диалог: ввод имени нового пресета ─────────────────────────────
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Сохранить пресет") },
            text = {
                Column {
                    Text(
                        "Текущие настройки всех эффектов (полосы EQ, Bass, Virtualizer, " +
                        "Loudness, Reverb) будут сохранены как новый пресет.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPresetName.trim().ifBlank { "Мой пресет" }
                        onPresetSaved(name)
                        // Обновляем список custom пресетов в UI.
                        customPresets.clear()
                        customPresets.addAll(CustomPresetStore.list())
                        showSaveDialog = false
                    },
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Отмена") }
            },
        )
    }

    // ─── Диалог: подтверждение удаления ────────────────────────────────
    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить пресет") },
            text = {
                Text("Удалить пресет «${toDelete.name}»? Действие нельзя отменить.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPresetDeleted(toDelete.id)
                        customPresets.remove(toDelete)
                        pendingDelete = null
                    },
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }
}

/**
 * Карточка пользовательского пресета — показывает мини-визуализацию полос +
 * название + активный маркер + кнопку удаления (правая иконка-корзина).
 */
@Composable
private fun CustomPresetCard(
    preset: CustomPreset,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Мини-визуализация полос (mB → dB для отображения).
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(70.dp),
            ) {
                preset.eqBands.take(9).forEach { gainmB ->
                    val gainDb = gainmB.toInt() / 100
                    val h = (kotlin.math.abs(gainDb).coerceIn(0, 15) * 1.6f + 4f).dp
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(h)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (gainDb >= 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            ),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val effectsOn = mutableListOf<String>()
                if (preset.bassEnabled) effectsOn.add("Bass")
                if (preset.virtEnabled) effectsOn.add("Virt")
                if (preset.loudEnabled) effectsOn.add("Loud")
                if (preset.reverbEnabled) effectsOn.add("Reverb")
                val effectsLine = if (effectsOn.isEmpty()) "Только EQ" else effectsOn.joinToString(", ")
                Text(
                    effectsLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isActive) {
                Text(
                    "✓",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Удалить пресет",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatDb(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else v.toString()

@Composable
private fun PresetCard(
    preset: EqualizerPreset,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Мини-визуализация полос пресета
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.width(70.dp),
            ) {
                preset.bands.take(9).forEach { gain ->
                    val h = (kotlin.math.abs(gain).coerceIn(0f, 15f) * 1.6f + 4f).dp
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(h)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (gain >= 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            ),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "Полосы: ${preset.bands.joinToString(", ") { formatDb(it) + " dB" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isActive) {
                Text(
                    "✓",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Полосы EQ (9 вертикальных слайдеров ±15 dB)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun BandsTab(enabled: Boolean) {
    // 9 UI-слотов (как в AudioPlayerScreen). Мапим в реальное число полос
    // устройства через getEqualizerBands().
    val slotCount = eqFrequencyLabels.size
    var bands by remember { mutableStateOf(List(slotCount) { 0f }) }
    var presetName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        presetName = EqualizerHelper.currentPresetName ?: EqualizerHelper.getSavedPresetName()
        val live = PlayerConnection.getEqualizerBands()       // mB, размер = numberOfBands устройства
        val saved = EqualizerHelper.getSavedBands()           // mB, полный список (9 слотов)
        // #EQ-BANDS-PERSIST: приоритет сохранённому списку, а не live-полосам
        // устройства. Устройство может иметь 5 полос (HOTWAV), а UI — 9 слотов:
        // live содержит только 5 значений → слоты 5-8 «сбрасывались» в 0 при
        // возврате на вкладку. saved хранит все 9 слотов.
        val source = if (saved.isNotEmpty()) saved else live
        val mapped = MutableList(slotCount) { 0f }
        if (source.isNotEmpty()) {
            val upper = minOf(source.lastIndex, slotCount - 1)
            for (i in 0..upper) mapped[i] = source[i].toInt() / 100f
        }
        bands = mapped
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Подсказка сверху
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Тяни ползунки вверх/вниз для усиления/ослабления частоты. " +
                       "Сдвиг любой полосы сбрасывает пресет в «Пользовательский».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        // 9 вертикальных слайдеров в горизонтальном скролле
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            eqFrequencyLabels.forEachIndexed { index, freq ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp),
                ) {
                    Text(
                        text = "${bands.getOrElse(index) { 0f }.toInt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bands.getOrElse(index) { 0f } != 0f)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    EqVerticalSliderThemed(
                        value = bands.getOrElse(index) { 0f },
                        onValueChange = { newValue ->
                            val updated = bands.toMutableList()
                            updated[index] = newValue
                            bands = updated
                            presetName = null
                            PlayerConnection.setEqualizerBand(
                                index,
                                (newValue * 100).toInt().toShort(),
                            )
                        },
                        enabled = enabled,
                        modifier = Modifier.height(220.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = freq,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Вертикальный слайдер для эквалайзера — поворот горизонтального [Slider]
 * на 270° через [Modifier.layout]. Тематизированный (Material3 colorScheme).
 */
@Composable
private fun EqVerticalSliderThemed(
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        valueRange = -15f..15f,
        modifier = modifier.layout { measurable, constraints ->
            val swapped = constraints.copy(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
            val placeable = measurable.measure(swapped)
            layout(placeable.height, placeable.width) {
                placeable.placeWithLayer(
                    x = -(placeable.width - placeable.height) / 2,
                    y = -(placeable.height - placeable.width) / 2,
                ) {
                    rotationZ = 270f
                }
            }
        },
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        ),
    )
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Bass + Virtualizer
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun BassVirtTab(
    bassVisible: Boolean,
    virtVisible: Boolean,
) {
    val engine = remember { EqualizerHelper.engine() }

    var bassOn by remember { mutableStateOf(false) }
    var bassStr by remember { mutableStateOf(0) }
    var virtOn by remember { mutableStateOf(false) }
    var virtStr by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        engine?.let {
            bassOn = it.isBassBoostEnabled() || it.isBassBoostSavedEnabled()
            bassStr = it.getBassBoostStrength()
            virtOn = it.isVirtualizerEnabled() || it.isVirtualizerSavedEnabled()
            virtStr = it.getVirtualizerStrength()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (bassVisible) {
            EffectCard(
                title = "BassBoost",
                description = "Усиление низких частот (0..1000). " +
                              "Даёт «глубокий бас» на наушниках/динамике.",
                checked = bassOn,
                onToggle = { on ->
                    bassOn = on
                    engine?.setBassBoostEnabled(on)
                },
                value = bassStr.toFloat(),
                valueRange = 0f..1000f,
                valueLabel = { "${it.toInt()}" },
                onValueChange = { v ->
                    bassStr = v.toInt()
                    engine?.setBassBoostStrength(v.toInt())
                },
            )
        }
        if (virtVisible) {
            EffectCard(
                title = "Virtualizer",
                description = "Пространственный эффект (0..1000). " +
                              "Расширяет стереобазу. Лучше работает в наушниках.",
                checked = virtOn,
                onToggle = { on ->
                    virtOn = on
                    engine?.setVirtualizerEnabled(on)
                },
                value = virtStr.toFloat(),
                valueRange = 0f..1000f,
                valueLabel = { "${it.toInt()}" },
                onValueChange = { v ->
                    virtStr = v.toInt()
                    engine?.setVirtualizerStrength(v.toInt())
                },
            )
        }
        if (!bassVisible && !virtVisible) {
            Text(
                "Оба эффекта отключены в настройках.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Reverb
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun ReverbTab() {
    val engine = remember { EqualizerHelper.engine() }
    val presetNames = remember { engine?.reverbPresetNames ?: emptyList() }

    var reverbOn by remember { mutableStateOf(false) }
    var selectedPreset by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        engine?.let {
            reverbOn = it.isReverbEnabled() || it.isReverbSavedEnabled()
            selectedPreset = it.getReverbPreset()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Master switch для reverb
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PresetReverb", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "Реверберация: эмуляция акустики помещения. " +
                        "На некоторых устройствах может искажать звук.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = reverbOn,
                    onCheckedChange = { on ->
                        reverbOn = on
                        engine?.setReverbEnabled(on)
                    },
                )
            }
        }

        Text(
            "Выбор пресета",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )

        presetNames.forEachIndexed { index, name ->
            val isSelected = selectedPreset == index
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedPreset = index
                        engine?.setReverbPreset(index)
                        if (!reverbOn) {
                            reverbOn = true
                            engine?.setReverbEnabled(true)
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        if (index == 0) {
                            Text(
                                "Без эффекта",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isSelected) {
                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Loudness
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun LoudnessTab() {
    val engine = remember { EqualizerHelper.engine() }

    var loudOn by remember { mutableStateOf(false) }
    var gainMB by remember { mutableStateOf(0) }      // millibels 0..1500

    LaunchedEffect(Unit) {
        engine?.let {
            loudOn = it.isLoudnessEnabled() || it.isLoudnessSavedEnabled()
            gainMB = it.getLoudnessTargetGain()
        }
    }

    // LoudnessEnhancer доступен только на API 19+.
    val available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!available) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    "LoudnessEnhancer требует Android 4.4+ (API 19). " +
                    "На вашей версии недоступно.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        } else {
            EffectCard(
                title = "LoudnessEnhancer",
                description = "Нормализация громкости: поднимает тихие " +
                              "участки трека. Target gain 0..+15 dB. " +
                              "Полезно в шумной обстановке.",
                checked = loudOn,
                onToggle = { on ->
                    loudOn = on
                    engine?.setLoudnessEnabled(on)
                },
                value = gainMB.toFloat(),
                valueRange = 0f..1500f,
                valueLabel = { mb -> String.format("%.1f dB", mb / 100f) },
                onValueChange = { v ->
                    gainMB = v.toInt()
                    engine?.setLoudnessTargetGain(v.toInt())
                },
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Shared: карточка эффекта с switch + slider
// ══════════════════════════════════════════════════════════════════════

/**
 * Универсальная карточка эффекта: заголовок + описание + switch + слайдер.
 * Используется для BassBoost / Virtualizer / LoudnessEnhancer.
 */
@Composable
private fun EffectCard(
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                valueLabel(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                enabled = checked,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                ),
            )
        }
    }
}

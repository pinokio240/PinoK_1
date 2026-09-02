package re.pinok.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import re.pinok.BuildConfig
import re.pinok.SovaApp
import re.pinok.contracts.ContainerRegistry
import re.pinok.contracts.SettingsSection
import re.pinok.data.local.SovaPrefs
import re.pinok.data.local.AudioFormat
import re.pinok.data.local.AudioQuality
import re.pinok.data.model.DownloadState
import re.pinok.data.model.DownloadStatus
import re.pinok.data.model.Track
import re.pinok.ui.theme.SovaColors
import re.pinok.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Delete
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.text.DecimalFormat

// ══════════════════════════════════════════════════════════════════════
//  #245 (settings tabs): разбиение длинного списка настроек на вкладки.
//
//  Раньше SettingsScreen был единым LazyColumn с 9 секциями (~50 строк
//  ToggleRow) — пользователю приходилось скроллить экраны, чтобы найти
//  нужный переключатель. Теперь каждая группа вынесена в отдельную вкладку
//  PrimaryScrollableTabRow + HorizontalPager (swipe между вкладками).
//
//  Вкладки:
//    1. Интерфейс  — тема/акцент/шрифт/анимация/стикеры
//    2. Новости    — реклама/репосты/промо
//    3. Сообщения  — все msg* настройки (DNF, multi-select, swipe reply…)
//    4. Музыка     — качество/фон/авто-кэш аудио/скачанные аудио
//    5. Видео      — скачанные видео + путь
//    6. Сеть       — SSL/away/adblock/web-api-шлюз
//    7. Приватность — device mask/anti-telemetry/last seen
//    8. Защита     — PIN/биометрия/on-background
//    9. Логирование — toggle показа плавающего значка логов (кнопка ВКЛ/ВЫКЛ)
// ══════════════════════════════════════════════════════════════════════

/**
 * Группа настроек. Порядок значений определяет порядок вкладок в TabRow.
 * Иконки — Material Outlined (для консистентности с остальным UI).
 */
private enum class SettingsTab(
    val label: String,
    val icon: ImageVector,
) {
    INTERFACE("Интерфейс", Icons.Outlined.Palette),
    NEWS("Новости", Icons.Outlined.Newspaper),
    MESSAGES("Сообщения", Icons.AutoMirrored.Outlined.Chat),
    MUSIC("Музыка", Icons.Outlined.MusicNote),
    // #OFFLINE-TAB: управление офлайн-кэшем аудио — путь, формат (M4A/MP3),
    // «Очистить всё», «Загрузить всё» (sequential queue, Fix #265).
    OFFLINE("Офлайн", Icons.Outlined.CloudOff),
    // Этап 2 (#Equalizer): вкл/выкл отдельных эффектов эквалайзера.
    // Скрывает соответствующие вкладки в полноэкранном EqualizerScreen.
    EQUALIZER("Эквалайзер", Icons.Outlined.Equalizer),
    VIDEO("Видео", Icons.Outlined.VideoLibrary),
    NETWORK("Сеть", Icons.Outlined.Cloud),
    // Fix #298: вкладка «Уведомления» — быстрый доступ к push-настройкам
    // + переход к полному экрану NotificationSettingsScreen.
    NOTIFICATIONS("Уведомления", Icons.Outlined.Notifications),
    // Fix #337: «Редактор панелей» — настройка боковой и нижней панелей
    // (включение/выключение кнопок + смена порядка).
    PANELS("Редактор панелей", Icons.Outlined.DashboardCustomize),
    PRIVACY("Приватность", Icons.Outlined.Shield),
    SECURITY("Защита", Icons.Outlined.Lock),
    LOGGING("Логирование", Icons.Outlined.BugReport),
    AUTHOR("Автор", Icons.Filled.Person),
    // #ARCH-CONTAINERS (Этап 1.4): ядерная вкладка CALLS («Звонки») убрана из
    // enum — вкладка настроек звонков приходит из реестра (SettingsSection
    // контейнера :feature:calls, route "settings_calls", order 90). Контент
    // рендерит ХОСТ: CallsTab остаётся в :app (SovaPrefs недоступен
    // фиче-модулю) — см. hostSettingsContentFor(). Без контейнера вкладки нет,
    // ядерные вкладки не меняются. Дублей быть не должно: одна секция — одна
    // вкладка (ядро в enum больше не рисует).
}

@Composable
private fun AuthorTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Ссылки") }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val uri = Uri.parse("https://vk.ru/pluton_tut")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Автор",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "vk.ru/pluton_tut",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val uri = Uri.parse("https://vk.ru/pluton240")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Группа",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "vk.ru/pluton240",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  #ARCH-CONTAINERS (Этап 1.4): объединённый список страниц настроек
//
//  Страница = ядерная (enum SettingsTab — собственность хоста, НЕИЗМЕННО)
//  либо контейнерная (SettingsSection из реестра, появляется ТОЛЬКО пока
//  контейнер зарегистрирован). Ключ — сумма типов: enum-имя для ядерных,
//  route-строка для контейнерных. PrimaryScrollableTabRow и HorizontalPager
//  строятся из ОДНОГО списка pages → индексы pager'а всегда согласованы.

/**
 * Дескриптор страницы настроек. Контент НЕ захватывается сюда (иначе
 * remember-страницы закэшировали бы устаревший Snapshot s) — контент
 * резолвится при рендере: Core → when(tab), Container → hostSettingsContentFor.
 */
private sealed class SettingsPage(val key: String, val label: String, val icon: ImageVector?) {
    /** Ядерная вкладка (enum SettingsTab). */
    class Core(val tab: SettingsTab) : SettingsPage("core:${tab.name}", tab.label, tab.icon)

    /** Вкладка контейнера (SettingsSection из реестра). */
    class Container(val section: SettingsSection) : SettingsPage(
        "container:${section.route}", section.title, hostSettingsIconFor(section.route),
    )
}

/**
 * route SettingsSection → иконка (контракты без compose — иконку мапит хост,
 * как NavEntry.iconKey в drawer). "settings_calls" → иконка прежней ядерной
 * вкладки «Звонки» (Icons.Filled.Call) — UI выглядит как раньше.
 * Неизвестный route → нейтральная иконка (расширение) — НЕ падаем.
 */
private fun hostSettingsIconFor(route: String): ImageVector = when (route) {
    "settings_calls" -> Icons.Filled.Call
    else -> Icons.Outlined.Extension
}

/**
 * route SettingsSection → контент хоста. Компосаблы настроек остаются в :app
 * (SovaPrefs/SovaApp недоступны фиче-модулю) — хост-маппинг route → компосабл,
 * тот же паттерн, что NavEntry.route → destination в SovaNavHost.
 * Неизвестный route → null (страница-заглушка + предупреждение в лог).
 */
private fun hostSettingsContentFor(route: String): (@Composable (SovaPrefs.Snapshot, SovaApp, CoroutineScope) -> Unit)? =
    when (route) {
        "settings_calls" -> { s, app, scope -> CallsTab(s, app, scope) }
        else -> null
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onOpenNotificationSettings: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
) {
    val app = SovaApp.get()
    val snap by app.prefs.data.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val s = snap ?: return

    // #ARCH-CONTAINERS (Этап 1.4): ядерные вкладки (enum, порядок прежний) +
    // контейнерные секции после них (по order). Реестр не реактивен — читаем
    // при построении composition (контракт ContainerRegistry).
    val containerSections = remember {
        ContainerRegistry.find<SettingsSection>().sortedBy { it.order }
    }
    val tabs = SettingsTab.entries
    val pages = remember(containerSections) {
        buildList {
            tabs.forEach { add(SettingsPage.Core(it)) }
            containerSections.forEach { add(SettingsPage.Container(it)) }
        }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 0.dp,
        ) {
            pages.forEachIndexed { index, page ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(page.label, maxLines = 1) },
                    icon = {
                        // Контейнерные секции могут быть без иконки — не падаем.
                        if (page.icon != null) Icon(page.icon, contentDescription = null)
                    },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (val p = pages[page]) {
                is SettingsPage.Core -> when (p.tab) {
                    SettingsTab.INTERFACE -> InterfaceTab(s, app, scope)
                    SettingsTab.NEWS -> NewsTab(s, app, scope)
                    SettingsTab.MESSAGES -> MessagesTab(s, app, scope)
                    SettingsTab.MUSIC -> MusicTab(s, app, scope, context)
                    SettingsTab.OFFLINE -> OfflineTab(s, app, scope, context)
                    SettingsTab.EQUALIZER -> EqualizerTab()
                    SettingsTab.VIDEO -> VideoTab(s, app, scope, context)
                    SettingsTab.NETWORK -> NetworkTab(s, app, scope)
                    SettingsTab.NOTIFICATIONS -> NotificationsTab(s, app, scope, onOpenNotificationSettings)
                    SettingsTab.PANELS -> PanelEditorTab(s, app, scope)
                    SettingsTab.PRIVACY -> PrivacyTab(s, app, scope)
                    SettingsTab.SECURITY -> SecurityTab(s, app, scope, onOpenDevices)
                    SettingsTab.LOGGING -> LoggingTab(s, app, scope)
                    SettingsTab.AUTHOR -> AuthorTab(s, app, scope)
                }
                is SettingsPage.Container -> {
                    // #ARCH-CONTAINERS (Этап 1.4): контент контейнерной вкладки —
                    // по host-маппингу route → компосабл (CallsTab остаётся в :app).
                    val content = hostSettingsContentFor(p.section.route)
                    if (content == null) {
                        // Graceful: секция есть, хост-маппинга нет — заглушка, не падаем.
                        LaunchedEffect(p.section.route) {
                            AppLog.w(
                                "SettingsScreen",
                                "CONTAINERS: SettingsSection route '${p.section.route}' не поддержан хостом — показана заглушка",
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item { SectionHeader(p.section.title) }
                            item {
                                Text(
                                    "Раздел предоставлен контейнером и будет доступен после обновления хоста.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        content(s, app, scope)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Звонки
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun CallsTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Разрешения") }
        item {
            val micGranted = re.pinok.util.PermissionManager.isGranted(context, android.Manifest.permission.RECORD_AUDIO)
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            ToggleRow(
                title = "Микрофон",
                subtitle = "Доступ к микрофону для звонков",
                checked = micGranted,
            ) {
                if (it) launcher.launch(android.Manifest.permission.RECORD_AUDIO)
                else {
                    android.widget.Toast.makeText(context, "Отключите микрофон в настройках системы", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        item {
            val notifGranted = re.pinok.util.PermissionManager.isGranted(context, android.Manifest.permission.POST_NOTIFICATIONS)
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            ToggleRow(
                title = "Уведомления о звонках",
                subtitle = "Всплывающее уведомление при входящем звонке",
                checked = notifGranted,
            ) {
                if (it) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else {
                    android.widget.Toast.makeText(context, "Отключите уведомления в настройках системы", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        // #CALLS-VIDEO-RX (Этап 1, CALLS_MAP §11.2.5): kill-switch приёма видео.
        // При краше видео-декодера на конкретном устройстве пользователь выключает
        // приём БЕЗ пересборки — движок вернётся к прежнему поведению (a=inactive).
        item { SectionHeader("Видео") }
        item {
            ToggleRow(
                title = "Приём видео собеседника",
                subtitle = "Показывать видео во входящих видео-звонках. Выключите, если звонок крашится",
                checked = s.callsVideoRx,
            ) { v ->
                scope.launch { app.prefs.setCallsVideoRx(v) }
            }
        }
        // #CALLS-SYMMETRIC (01.09): чёрная видеозаглушка наружу — симметричный звонок
        // (Этап 2-заготовка, БЕЗ камеры и разрешения CAMERA). Гипотеза: офиц. клиент
        // в Wi-Fi same-NAT не начинает ICE-проверки против recvonly-ответа.
        item {
            ToggleRow(
                title = "Отправлять видеозаглушку",
                subtitle = "Симметричный звонок: чёрные кадры вместо камеры (у собеседника — чёрный тайл). Помогает, если собеседник не подключается",
                checked = s.callsVideoTx,
            ) { v ->
                scope.launch { app.prefs.setCallsVideoTx(v) }
            }
        }
        // #CALLS-SWDECODE (01.09): решающая диагностика чёрного экрана при
        // доказанном рендере (TextureView отрисовал 1354 кадра — экран чёрный).
        item {
            ToggleRow(
                title = "Программный декодер видео",
                subtitle = "Диагностика чёрного экрана: программное декодирование вместо аппаратного. Вступает после перезапуска приложения",
                checked = s.callsVideoSwDecode,
            ) { v ->
                scope.launch { app.prefs.setCallsVideoSwDecode(v) }
            }
        }

        // #CALLS-AUTO (2026-08-23): session_key и queue-credential получаются
        // автоматически (как браузер): get_anonym_token → auth.anonymLogin →
        // session_key; queue.subscribe → queue-credential. Ручной ввод убран.
        item { SectionHeader("Входящие звонки") }
        item {
            val queueRunning = app.queuev4Client.isRunning()
            val sessOk = s.callsSessionKey.isNotBlank()
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        if (queueRunning && sessOk) "Статус: активно" else "Статус: не подключено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (queueRunning && sessOk) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Session_key: ${if (sessOk) "получен автоматически" else "не получен"}" +
                            (if (sessOk) " (len=${s.callsSessionKey.length})" else ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Queuev4 (входящие): ${if (queueRunning) "слушаем" else "не запущен"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                // Автопереподключение: получаем СВЕЖИЙ session_key
                                // (force=true — старый может быть протухшим, PARAM_SESSION_EXPIRED)
                                // и queue-credential.
                                val sk = app.ensureCallsSessionKey(force = true)
                                val cred = app.apiClient.queueSubscribe()
                                if (cred != null) {
                                    app.queuev4Client.setCredential(cred)
                                    app.queuev4Client.start()
                                }
                                android.widget.Toast.makeText(
                                    context,
                                    if (sk != null && cred != null) "Звонки подключены"
                                    else "Не удалось подключить (см. лог)",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Переподключить") }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            app.queuev4Client.stop()
                            android.widget.Toast.makeText(context, "Queuev4 остановлен", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Остановить") }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Интерфейс
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun InterfaceTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Тема") }
        item {
            ToggleRow(
                title = "Тёмная тема",
                checked = s.themeDark,
                onToggle = { scope.launch { app.prefs.setThemeDark(it) } },
            )
        }
        item {
            // #MONET-DYNAMIC-COLOR: Material You / Monet — адаптивная цветовая
            // тема, извлекает цвета из обоев. Доступна ТОЛЬКО на Android 12+
            // (API 31, S). На более старых Android переключатель виден, но
            // бесполезен — silent fallback на B&W схему с accent color.
            // Показываем подпись с требованием версии если Android < 12.
            val monetSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            ToggleRow(
                title = if (monetSupported) "Material You (dynamic)"
                        else "Material You (нужен Android 12+)",
                checked = s.themeDynamic && monetSupported,
                enabled = monetSupported,
                onToggle = { scope.launch { app.prefs.setThemeDynamic(it) } },
            )
        }
        item {
            // #MONET-HYBRID: гибридный режим — при включённом Material You
            // перекрывает primary/secondary/tertiary = accent (пользовательский),
            // а surface/background/surfaceVariant остаются от обоев. Без этого
            // Monet полностью перекрашивает все активные элементы под обои, и
            // выбранный accent теряется. Toggle активен только когда Material
            // You включён И Android 12+. По умолчанию = true (accent важнее).
            val monetSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            val hybridAvailable = monetSupported && s.themeDynamic
            ToggleRow(
                title = "Гибридный accent",
                subtitle = "Кнопки/свитчи = ваш accent, фон = из обоев. " +
                           "Без этого Material You перекрашивает accent под обои.",
                checked = s.themeMonetHybrid && hybridAvailable,
                enabled = hybridAvailable,
                onToggle = { scope.launch { app.prefs.setThemeMonetHybrid(it) } },
            )
        }
        item { AccentPicker(s.themeAccentIndex) { idx -> scope.launch { app.prefs.setThemeAccentIndex(idx) } } }

        item { SectionHeader("Текст и анимации") }
        item {
            FontScaleRow(
                value = s.fontScale,
                onChange = { scope.launch { app.prefs.setFontScale(it) } },
            )
        }
        // Fix #224: скорость анимаций интерфейса (0..100%). 0 = выключены.
        item {
            AnimSpeedRow(
                value = s.interfaceAnimSpeed,
                onChange = { scope.launch { app.prefs.setInterfaceAnimSpeed(it) } },
            )
        }
        // Fix #228: масштаб стикер-фото в чате (0..40%, шаг 1%).
        item {
            StickerPhotoScaleRow(
                value = s.stickerPhotoScale,
                onChange = { scope.launch { app.prefs.setStickerPhotoScale(it) } },
            )
        }

        item { SectionHeader("Лента") }
        // #238: показ FAB «подняться в верх ленты» при прокрутке вниз.
        // Default: true — FAB виден по умолчанию, пользователь может скрыть.
        item {
            ToggleRow(
                title = "Кнопка «наверх» в ленте",
                subtitle = "Плавающая стрелка для быстрого возврата к началу ленты",
                checked = s.feedShowScrollFab,
            ) { scope.launch { app.prefs.setFeedShowScrollFab(it) } }
        }

        item { SectionHeader("Сеть") }
        // #NET-SWITCH-POPUP (2026-08-03): popup при переключении сети.
        // Управляет ТОЛЬКО видимостью popup. Логика переключения (grace period,
        // silent refresh, AuthActivity) работает в фоне независимо от тумблера.
        // Default = true — popup виден (пользователь видит что происходит).
        item {
            ToggleRow(
                title = "Окно переключения сети",
                subtitle = "Всплывающее окно при смене Wi-Fi↔Mobile и обновлении " +
                    "токена. При выключении переключение работает скрыто, без UI. " +
                    "Функционал приложения не теряется.",
                checked = s.netSwitchPopupEnabled,
            ) { scope.launch { app.prefs.setNetSwitchPopupEnabled(it) } }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Новости
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun NewsTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Фильтрация ленты") }
        item { ToggleRow("Блокировка рекламы", s.newsAdsBlocked) { scope.launch { app.prefs.setNewsAdsBlocked(it) } } }
        item { ToggleRow("Скрывать репосты", s.newsRepostsHidden) { scope.launch { app.prefs.setNewsRepostsHidden(it) } } }
        item { ToggleRow("Скрывать промо", s.newsPromoHidden) { scope.launch { app.prefs.setNewsPromoHidden(it) } } }
        item { SectionHeader("Интерфейс ленты") }
        item {
            ToggleRow(
                title = "Фильтр ленты",
                subtitle = "Выпадающий список разделов (Все/Реакции/Фото/Друзья/Поиск)",
                checked = s.feedShowFilter,
            ) { scope.launch { app.prefs.setFeedShowFilter(it) } }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Сообщения
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun MessagesTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Приватность сообщений") }
        item { ToggleRow("DNR (не читать)", s.msgDnr) { scope.launch { app.prefs.setMsgDnr(it) } } }
        item { ToggleRow("DNT (не печатать)", s.msgDnt) { scope.launch { app.prefs.setMsgDnt(it) } } }
        // Fix #REMOVE-UNDELETE-UNEDIT (2026-08-04): «Показать удалённые» и
        // «Показать оригинал правок» удалены из настроек по запросу пользователя.
        // Pref (msgUndelete / msgUnedit) и логика MessageMods.apply остаются
        // в коде с default=true — функция работает всегда, юзер не может выключить.
        // P2.6: read receipts (✓/✓✓) — статус прочтения исходящих.
        item { ToggleRow("Статус прочтения (✓/✓✓)", s.msgReadReceipts) { scope.launch { app.prefs.setMsgReadReceipts(it) } } }

        item { SectionHeader("Список чатов") }
        // #MSG-FAVORITES-TOGGLE: показывать «Избранное» в списке чатов.
        item { ToggleRow("«Избранное» в чатах", s.msgShowFavorites) { scope.launch { app.prefs.setMsgShowFavorites(it) } } }
        // P0.3: pinned message bar + pin/unpin в context menu (только group chats).
        item { ToggleRow("Закреплённые сообщения", s.msgPinBar) { scope.launch { app.prefs.setMsgPinBar(it) } } }
        // P1.4: search + tabs в MessagesScreen.
        item { ToggleRow("Поиск и вкладки в чатах", s.msgSearch) { scope.launch { app.prefs.setMsgSearch(it) } } }
        // P3.2: mute/unmute chat — toggle уведомлений.
        item { ToggleRow("Заглушение чатов", s.msgMute) { scope.launch { app.prefs.setMsgMute(it) } } }
        // P3.1: ChatInfo screen — отдельный экран информации о чате.
        item { ToggleRow("Экран информации о чате", s.msgChatInfo) { scope.launch { app.prefs.setMsgChatInfo(it) } } }
        // P3.3: folders system — пользовательские папки диалогов (экспериментально).
        item { ToggleRow(
            title = "Папки диалогов",
            subtitle = "Группировка чатов по темам (экспериментально)",
            checked = s.msgFolders,
        ) { scope.launch { app.prefs.setMsgFolders(it) } } }

        item { SectionHeader("Отображение сообщений") }
        // P0.1: typing indicator — показывает «N печатает…» в TopAppBar чата.
        item { ToggleRow("Индикатор «печатает…»", s.msgTypingIndicator) { scope.launch { app.prefs.setMsgTypingIndicator(it) } } }
        // P1.3: message grouping — объединение последовательных сообщений.
        item { ToggleRow("Группировка сообщений", s.msgGrouping) { scope.launch { app.prefs.setMsgGrouping(it) } } }
        // P1.1: date separators + unread divider + scroll-to-bottom FAB.
        item { ToggleRow("Разделители дат", s.msgDateSeparators) { scope.launch { app.prefs.setMsgDateSeparators(it) } } }
        item { ToggleRow("Разделитель непрочитанных", s.msgUnreadDivider) { scope.launch { app.prefs.setMsgUnreadDivider(it) } } }
        item { ToggleRow("Кнопка прокрутки вниз", s.msgScrollFab) { scope.launch { app.prefs.setMsgScrollFab(it) } } }
        // P1.2: reply via swipe — свайп для ответа на сообщение.
        item { ToggleRow("Ответ свайпом", s.msgSwipeReply) { scope.launch { app.prefs.setMsgSwipeReply(it) } } }
        // P3.7: bubble-less дизайн — flat layout сообщений (экспериментально).
        item { ToggleRow(
            title = "Bubble-less дизайн",
            subtitle = "Плоские сообщения без карточек (как в VK web)",
            checked = s.msgBubbleless,
        ) { scope.launch { app.prefs.setMsgBubbleless(it) } } }
        // P3.4: channel mode — отдельный UX для каналов (скрытие composer).
        item { ToggleRow(
            title = "Режим каналов",
            subtitle = "Скрывает поле ввода для каналов (где нельзя писать)",
            checked = s.msgChannelMode,
        ) { scope.launch { app.prefs.setMsgChannelMode(it) } } }

        item { SectionHeader("Действия с сообщениями") }
        // P2.5: multi-select mode — выделение нескольких сообщений.
        // Fix #244: прямой вход в selection по long-press (без DropdownMenu).
        item { ToggleRow("Выбор нескольких сообщений", s.msgMultiSelect) { scope.launch { app.prefs.setMsgMultiSelect(it) } } }
        // P3.5: multi-file upload — до 10 фото за раз.
        item { ToggleRow("Множественный выбор фото", s.msgMultiFile) { scope.launch { app.prefs.setMsgMultiFile(it) } } }
        // P3.6: dual send/mic button — state machine (EDIT/LOADING/LIMIT/MIC/SUBMIT).
        item { ToggleRow("Умная кнопка отправки", s.msgDualButton) { scope.launch { app.prefs.setMsgDualButton(it) } } }

        item { SectionHeader("Ссылки и браузер") }
        // P5.1: открытие ссылок из чата во внутреннем браузере (WebView).
        item { ToggleRow(
            title = "Открывать ссылки внутри приложения",
            subtitle = "Встроенный браузер (WebView) вместо внешнего",
            checked = s.openLinksInInternalBrowser,
        ) { scope.launch { app.prefs.setOpenLinksInInternalBrowser(it) } } }

        item { SectionHeader("LongPoll и transport (экспериментально)") }
        // P4.2: LongPoll backfill — восстановление пропущенных между сессиями.
        item { ToggleRow(
            title = "Восстановление пропущенных сообщений",
            subtitle = "LongPoll backfill: проверяет пропущенные события при запуске (экспериментально)",
            checked = s.msgLpBackfill,
        ) { scope.launch { app.prefs.setMsgLpBackfill(it) } } }
        // P4.1: LongPoll v14 — lp_version=14, mode=1226 (расширенные поля).
        item { ToggleRow(
            title = "LongPoll v14",
            subtitle = "Расширенный протокол (lp_version=14, mode=1226) — больше полей в ответе",
            checked = s.msgLpV14,
        ) { scope.launch { app.prefs.setMsgLpV14(it) } } }
        // §52.5 Sprint A (P0): Modern Sync API — messages.getDiff (lp_version=21).
        item { ToggleRow(
            title = "Modern Sync (getDiff)",
            subtitle = "messages.getDiff (lp_version=21): credentials + папки + счётчики одним запросом (экспериментально)",
            checked = s.msgModernSync,
        ) { scope.launch { app.prefs.setMsgModernSync(it) } } }
        // Fix #REMOVE-DEAD-TOGGLES (2026-08-04): удалены 2 DEAD настройки:
        // - "Execute batching" (msgExecuteBatch) — нет batch-логики в коде,
        //   setter сохраняется но VKApiClient не проверяет флаг.
        // - "WebSocket для каналов" (msgWsChannels) — ChannelWebSocketClient
        //   существует но не инстанциируется, методы STUB с TODO.
        // Pref и setter остаются в SovaPrefs (не трогаем — обратная совместимость).
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Музыка
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun MusicTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
    context: android.content.Context,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Воспроизведение") }
        // #FIX-A-HQ (2026-08-03): "Максимальное качество (quality=hq везде)".
        // Эта настройка применяет quality=hq ко ВСЕМ audio.get-family методам:
        // audio.get, audio.getById, audio.search, audio.getPlaylistById,
        // audio.getPlaylistTracks, audio.getRecommendations, audio.getByIdBatch,
        // audio.getAudiosByArtist, audio.getSnippets. Default = true.
        item {
            ToggleRow(
                title = "Максимальное качество (quality=hq)",
                subtitle = "320kbps MP3 / HQ AAC для всех аудио-запросов " +
                           "(библиотека, поиск, плейлисты, рекомендации, артисты).",
                checked = s.musicHighQuality,
            ) { scope.launch { app.prefs.setMusicHighQuality(it) } }
        }
        item { ToggleRow("Фоновое воспроизведение", s.musicBackgroundPlay) { scope.launch { app.prefs.setMusicBackgroundPlay(it) } } }

        // #AUDIO-QUALITY: выбор битрейта выходного MP3-файла.
        item { SectionHeader("Качество MP3") }
        item { QualityRow(current = s.audioQuality) { q -> scope.launch { app.prefs.setAudioQuality(q) } } }

        // #AUTOLOAD-BACK: секция «Авто-загрузка» возвращена во вкладку Музыка.
        // Раньше (#AUTO-CACHE-MOVE) тумблер убирали, считая что «Загрузить всё»
        // из вкладки Офлайн покрывает все сценарии. Но авто-загрузка решает
        // другую задачу: кэшировать ИГРАЮЩИЙ трек и следующий за ним в фоне —
        // чтобы при обрыве сети трек продолжал играть из кеша, а следующий
        // уже был готов. «Загрузить всё» качает всю библиотеку разом, это
        // другое. Поэтому тумблер нужен.
        //
        // PlayerConnection при autoCacheAudio=true:
        //   1) на onPlay → enqueueDownload(currentTrack) — кешит играющий трек;
        //   2) precacheNext() — ставит в очередь следующий трек заранее;
        //   3) при окончании URL — пере-резолвит и кешит заново.
        // Файлы складываются в ту же папку, что и «Загрузить всё» (s.musicDownloadPath).
        //
        // Переименование «Авто Кеш Аудио» → «Авто загрузка Аудио»: слово «кеш»
        // путало (кеш это технический термин), «загрузка» понятнее пользователю.
        // Поле в SovaPrefs осталось autoCacheAudio (без переименования, back-compat).
        item { SectionHeader("Авто-загрузка") }
        item {
            ToggleRow(
                title = "Авто загрузка Аудио",
                subtitle = "При включении играющий трек и следующий за ним " +
                    "автоматически кэшируются в фоне. Файлы сохраняются в папку " +
                    "«Аудио» (вкладка Офлайн). Полезно: при обрыве интернета " +
                    "трек доиграет до конца, а следующий уже будет готов. " +
                    "При выключении — только ручная загрузка через «Загрузить всё» " +
                    "или кнопку скачивания у трека.",
                checked = s.autoCacheAudio,
            ) { scope.launch { app.prefs.setAutoCacheAudio(it) } }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  Tab: Офлайн  (#OFFLINE-TAB)
// ════════════════════════════════════════════════════════════════════

/**
 * #OFFLINE-TAB: управление офлайн-кэшем аудио.
 *
 * Содержит:
 *  - Путь сохранения (PathSettingRow — переиспользуется из MusicTab).
 *  - Формат файлов (M4A по умолчанию | MP3 opt-in через ffmpeg-kit, P0 #2).
 *  - Хранилище: live-статистика из [re.pinok.media.TrackDownloadManager].
 *  - «Очистить всё» → [TrackDownloadManager.clearAllDownloads] (с confirm).
 *  - «Загрузить всё» → пагинация audioGet + [TrackDownloadManager.enqueueAll].
 *    Sequential queue (Fix #265) качает по одному треку за раз.
 */
@Composable
private fun OfflineTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
    context: android.content.Context,
) {
    // Live-карта загрузок — recompose при каждом изменении (enqueue/progress/done).
    val downloadsMap by re.pinok.media.TrackDownloadManager.downloads.collectAsState()
    val completedCount = downloadsMap.values.count { it.isCompleted }
    val activeCount = downloadsMap.values.count { it.isInProgress }
    // totalBytes делает File I/O (getLocalFile per track) — пересчитываем ТОЛЬКО
    // при смене completedCount (трек доскачался / удалён), а не на каждый progress-tick
    // сегмента (иначе O(N) File.exists на каждом из 50 сегментов трека = jank).
    val totalBytes = remember(completedCount) {
        re.pinok.media.TrackDownloadManager.getTotalDownloadedBytes()
    }
    // queueSize — ConcurrentLinkedQueue.size() O(n), n мало (<100); пересчитываем
    // при изменении activeCount (enqueue / worker-take / complete меняют карту).
    val queueSize = remember(activeCount, completedCount) {
        re.pinok.media.TrackDownloadManager.getQueueSize()
    }
    // #OFFLINE-STATUS-1: разбивка по статусам — чтобы пользователь ВИДЕЛ дохлые
    // треки и siren-кеш. deadTracks — для секции «Недоступные».
    val failedCount = downloadsMap.values.count { it.status == DownloadStatus.FAILED }
    val deadCount = downloadsMap.values.count { it.isDead }
    val sirenCount = downloadsMap.values.count { it.isSirenCache }
    val deadTracks = remember(downloadsMap) {
        downloadsMap.values.filter { it.isDead }.sortedByDescending { it.trackId }
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var showDownloadAllDialog by remember { mutableStateOf(false) }
    var downloadAllStatus by remember { mutableStateOf<DownloadAllStatus>(DownloadAllStatus.Idle) }

    // #DEAD-RECHECK: авто-проверка дохлых треков при открытии вкладки Офлайн.
    // Треки dead >1ч перепроверяются через audioGetById — VK мог пере-выдать URL.
    // Запускается ОДИН раз при первом входе во вкладку (Unit key). Если URL
    // восстановлен — трек авто-ставится в очередь (revived), пользователь видит
    // прогресс вместо «недоступен».
    LaunchedEffect(Unit) {
        val toRecheck = re.pinok.media.TrackDownloadManager.getDeadTracksForRecheck()
        if (toRecheck.isEmpty()) return@LaunchedEffect
        AppLog.i("Settings", "autoRecheck: ${toRecheck.size} dead tracks to recheck")
        var revived = 0
        for (st in toRecheck) {
            val minimal = Track(
                id = st.trackId, ownerId = st.ownerId,
                artist = st.artist, title = st.title,
                duration = 0, url = null,
            )
            val resolved = app.apiClient.audioGetById(minimal)
            if (resolved != null && resolved.url != null) {
                re.pinok.media.TrackDownloadManager.enqueueDownload(resolved)
                revived++
            }
        }
        if (revived > 0) {
            AppLog.i("Settings", "autoRecheck: revived $revived/${toRecheck.size} dead tracks")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Путь сохранения ──────────────────────────────────────────
        item { SectionHeader("Путь сохранения") }
        item {
            PathSettingRow(
                label = "Папка для скачанных аудио",
                currentPath = s.musicDownloadPath,
                onPathChange = { newPath ->
                    scope.launch {
                        app.prefs.setMusicDownloadPath(newPath)
                        re.pinok.media.TrackDownloadManager.reconfigurePath(newPath)
                    }
                },
                context = context,
            )
        }

        // ── Формат файлов ─────────────────────────────────────────────
        item { SectionHeader("Формат файлов") }
        item {
            AudioFormatRow(current = s.audioFormat) { fmt ->
                scope.launch { app.prefs.setAudioFormat(fmt) }
            }
        }

        // ── Метаданные и имена файлов (§42.12 P1 #3 / P2 #8 / P2 #9 / P1 #5) ─
        item { SectionHeader("Метаданные и имена") }
        item {
            ToggleRow(
                title = "Писать теги (©nam, ©ART, ©alb)",
                subtitle = "По умолчанию ВКЛ. В .m4a записываются MP4-теги: " +
                    "название, артист, альбом, кодировщик («PinoK»). Теги видны " +
                    "в любом плеере (Google Play Music, Poweramp, AIMP). " +
                    "Хеш файла пишется ДО тегов — integrity-check не ломается.",
                checked = s.writeId3Tags,
            ) { scope.launch { app.prefs.setWriteId3Tags(it) } }
        }
        item {
            ToggleRow(
                title = "Тексты песен из Genius",
                subtitle = "По умолчанию ВЫКЛ. При включении: для каждого трека " +
                    "дополнительный запрос к genius.com, текст пишется в тег ©lyr. " +
                    "Хрупко: если Genius поменяет вёрстку — перестанет работать " +
                    "(трек сохранится, просто без текста). Замедляет скачивание.",
                checked = s.writeGeniusLyrics,
            ) { scope.launch { app.prefs.setWriteGeniusLyrics(it) } }
        }
        item {
            ToggleRow(
                title = "Промо-комментарий",
                subtitle = "По умолчанию ВЫКЛ. Добавляет в тег cmt строку " +
                    "«Downloaded by PinoK v<version>». Опционально, для распространения.",
                checked = s.writePromoComment,
            ) { scope.launch { app.prefs.setWritePromoComment(it) } }
        }
        item {
            ToggleRow(
                title = "Номер трека в имени файла",
                subtitle = "По умолчанию ВКЛ. При скачивании плейлиста добавляет " +
                    "префикс «NN. » (01., 02., ...). Удобно для сохранения порядка " +
                    "в файловом менеджере. Для одиночных треков не используется.",
                checked = s.numTracksInPlaylist,
            ) { scope.launch { app.prefs.setNumTracksInPlaylist(it) } }
        }

        // ── Метод конвертации (§42.12 P3 #11) ─────────────────────────
        item { SectionHeader("Метод конвертации Siren") }
        item {
            ConvertMethodRow(current = s.audioConvertMethod) { method ->
                scope.launch { app.prefs.setAudioConvertMethod(method) }
            }
        }

        // ── Хранилище ─────────────────────────────────────────────────
        item { SectionHeader("Хранилище") }
        item {
            OfflineStatsCard(
                downloadedCount = completedCount,
                totalBytes = totalBytes,
                activeCount = activeCount,
                queueSize = queueSize,
                failedCount = failedCount,
                deadCount = deadCount,
                sirenCount = sirenCount,
            )
        }

        // ── Что значат значки (#WIFI-LEGEND) ──────────────────────────
        // Инструкция-расшифровка: что значит зелёный Wi-Fi на скачанном треке
        // (siren-кеш), чем отличается от обычной галки (M4A) и красного значка
        // (недоступен). Ставим сразу после статистики — естественный поток:
        // вижу числа → вижу расшифровку статусов → вижу действия.
        item { SectionHeader("Что значят значки") }
        item { OfflineLegendCard() }

        // ── Действия ──────────────────────────────────────────────────
        item { SectionHeader("Действия") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = completedCount > 0 || activeCount > 0 || queueSize > 0,
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Очистить всё", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showDownloadAllDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Загрузить всё", maxLines = 1)
                }
            }
        }
        // Inline-статус операции «Загрузить всё».
        item { DownloadAllStatusRow(downloadAllStatus) }

        // ── Недоступные треки (#OFFLINE-STATUS-1) ─────────────────────
        // Треки с DEAD_URL (URL протух/удалён) — то, что пользователь называет
        // «трек скончался». Показываем отдельным списком, только если есть дохлые.
        if (deadTracks.isNotEmpty()) {
            item { SectionHeader("Недоступные (${deadTracks.size})") }
            // #DEAD-RECHECK: кнопка «Повторить все» — массовый retry всех дохлых
            // треков через audioGetById. URL мог протухнуть временно (VK пере-выдал).
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                var revived = 0
                                var stillDead = 0
                                for (st in deadTracks) {
                                    val minimal = Track(
                                        id = st.trackId, ownerId = st.ownerId,
                                        artist = st.artist, title = st.title,
                                        duration = 0, url = null,
                                    )
                                    val resolved = app.apiClient.audioGetById(minimal)
                                    if (resolved != null && resolved.url != null) {
                                        re.pinok.media.TrackDownloadManager.enqueueDownload(resolved)
                                        revived++
                                    } else {
                                        stillDead++
                                    }
                                }
                                AppLog.i("Settings", "retryAll: revived=$revived, stillDead=$stillDead")
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Повторить все", maxLines = 1)
                    }
                }
            }
            item {
                DeadTracksCard(
                    tracks = deadTracks,
                    onRetry = { st ->
                        scope.launch {
                            // Пере-резолвим URL через audioGetById и ставим в очередь.
                            val minimal = Track(
                                id = st.trackId, ownerId = st.ownerId,
                                artist = st.artist, title = st.title,
                                duration = 0, url = null,
                            )
                            val resolved = app.apiClient.audioGetById(minimal)
                            if (resolved != null && resolved.url != null) {
                                re.pinok.media.TrackDownloadManager.enqueueDownload(resolved)
                                AppLog.i("Settings", "retry: re-enqueued dead track #${st.trackId}")
                            } else {
                                AppLog.w("Settings", "retry: audioGetById returned no URL for #${st.trackId}")
                            }
                        }
                    },
                    onRemove = { st ->
                        re.pinok.media.TrackDownloadManager.removeDownload(st.trackId)
                    },
                )
            }
        }
    }

    // ── Confirm: Очистить всё ────────────────────────────────────────
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить офлайн-кэш?") },
            text = {
                Text(
                    "Будут удалены все $completedCount скачанных аудио " +
                        "(${fmtSize(totalBytes)}) и отменена текущая очередь загрузки. " +
                        "Действие необратимо.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    re.pinok.media.TrackDownloadManager.clearAllDownloads()
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            },
        )
    }

    // ── Confirm: Загрузить всё ───────────────────────────────────────
    if (showDownloadAllDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadAllDialog = false },
            title = { Text("Загрузить всю музыку?") },
            text = {
                Text(
                    "Будут загружены все треки из вашей библиотеки (кроме уже " +
                        "скачанных). Файлы ставятся в очередь и качаются по одному " +
                        "(sequential queue). Уже скачано: $completedCount. " +
                        "Это может занять продолжительное время и расходовать трафик.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadAllDialog = false
                    scope.launch {
                        downloadAllStatus = DownloadAllStatus.Counting
                        val all = ArrayList<re.pinok.data.model.Track>()
                        try {
                            val first = app.apiClient.audioGetWithCount(count = 100, offset = 0)
                            val total = first.first
                            all.addAll(first.second)
                            downloadAllStatus = DownloadAllStatus.CountingProgress(all.size, total)
                            var offset = first.second.size
                            // Cap at 5000 to avoid runaway loops on huge libraries.
                            while (offset < total && all.size < 5000) {
                                val page = app.apiClient.audioGet(count = 100, offset = offset)
                                if (page.isEmpty()) break
                                all.addAll(page)
                                offset += page.size
                                downloadAllStatus = DownloadAllStatus.CountingProgress(all.size, total)
                            }
                        } catch (e: Exception) {
                            val errMsg = e.message
                            val errText = if (errMsg != null) errMsg else "network error"
                            downloadAllStatus = DownloadAllStatus.Error(errText)
                            return@launch
                        }
                        downloadAllStatus = DownloadAllStatus.Enqueuing(all.size)
                        val enqueued = re.pinok.media.TrackDownloadManager.enqueueAll(all)
                        downloadAllStatus = DownloadAllStatus.Done(enqueued, all.size)
                    }
                }) { Text("Загрузить") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadAllDialog = false }) { Text("Отмена") }
            },
        )
    }
}

/**
 * Состояние операции «Загрузить всё».
 *
 * Idle → Counting → CountingProgress(fetched,total) → Enqueuing(total) →
 *   Done(enqueued,total) | Error(message).
 */
private sealed class DownloadAllStatus {
    object Idle : DownloadAllStatus()
    object Counting : DownloadAllStatus()
    data class CountingProgress(val fetched: Int, val total: Int) : DownloadAllStatus()
    data class Enqueuing(val total: Int) : DownloadAllStatus()
    data class Done(val enqueued: Int, val total: Int) : DownloadAllStatus()
    data class Error(val message: String) : DownloadAllStatus()
}

/** Inline-карточка статуса «Загрузить всё» (показывается только если не Idle). */
@Composable
private fun DownloadAllStatusRow(status: DownloadAllStatus) {
    if (status is DownloadAllStatus.Idle) return

    val isBusy = status is DownloadAllStatus.Counting ||
        status is DownloadAllStatus.CountingProgress ||
        status is DownloadAllStatus.Enqueuing

    val text: String = when (status) {
        DownloadAllStatus.Counting -> "Подсчёт треков…"
        is DownloadAllStatus.CountingProgress -> "Получено ${status.fetched} из ${status.total} треков…"
        is DownloadAllStatus.Enqueuing -> "Постановка в очередь: ${status.total} треков…"
        is DownloadAllStatus.Done -> "Поставлено в очередь: ${status.enqueued} из ${status.total} " +
            "(уже скачанные пропущены)"
        is DownloadAllStatus.Error -> "Ошибка: ${status.message}"
        DownloadAllStatus.Idle -> ""  // unreachable (handled above)
    }
    val tint = if (status is DownloadAllStatus.Done) MaterialTheme.colorScheme.primary
        else if (status is DownloadAllStatus.Error) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = if (status is DownloadAllStatus.Error) Icons.Outlined.WarningAmber
        else Icons.Outlined.DownloadForOffline

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = tint)
        }
    }
}

/** Радио-выбор формата сохранения (M4A / MP3). */
@Composable
private fun QualityRow(current: AudioQuality, onChange: (AudioQuality) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Битрейт MP3",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Влияет на размер файла: 128kbps ~1MB/мин, 320kbps ~2.4MB/мин",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            for (q in AudioQuality.entries) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onChange(q) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = current == q, onClick = { onChange(q) })
                    Spacer(Modifier.width(8.dp))
                    Text(q.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun AudioFormatRow(current: AudioFormat, onChange: (AudioFormat) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Формат сохранения скачанных аудио",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            for (fmt in AudioFormat.entries) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChange(fmt) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = current == fmt, onClick = { onChange(fmt) })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(fmt.label, style = MaterialTheme.typography.bodyLarge)
                        if (fmt == AudioFormat.M4A) {
                            Text(
                                "AAC в MP4-контейнере. Играет везде, без зависимостей. " +
                                    "Используется по умолчанию. Siren-треки транскодируются " +
                                    "в M4A через ffmpeg-kit (§42.12 P0 #2).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Универсальный формат для экспорта (рингтоны, плееры), " +
                                    "ID3v2.4 теги. ffmpeg-kit подключён (P0 #2) — MP3 " +
                                    "кодирование доступно через transcoding pipeline.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * §42.12 P3 #11: выбор метода конвертации Siren-треков.
 *
 * Две опции:
 *  - siren_transcoder (default): ffmpeg-kit декодирует Siren→AAC офлайн.
 *    Трек играет без интернета. +15-20 MB к APK (native libs).
 *  - hls_native: Siren-треки кэшируются как .ts (codec=siren), стримятся
 *    онлайн через HLS. Wi-Fi бейдж в UI. Меньше размер APK, но нужен интернет.
 *
 * VKNext имеет 3 метода (ffmpeg/hlsjs/vknext cloud). У нас cloud недоступен —
 * только 2 локальных. Default: siren_transcoder (P0 #2 уже подключён).
 */
@Composable
private fun ConvertMethodRow(current: String, onChange: (String) -> Unit) {
    val methods = listOf(
        Triple(
            "siren_transcoder",
            "Siren-транскодер (ffmpeg-kit)",
            "По умолчанию. Siren-треки декодируются в M4A офлайн через " +
                "ffmpeg-kit. Трек играет без интернета. +15-20 MB к APK.",
        ),
        Triple(
            "hls_native",
            "HLS-нативный (без транскодера)",
            "Siren-треки кэшируются как .ts, стримятся онлайн через HLS. " +
                "Wi-Fi бейдж в UI. Меньше размер APK, но нужен интернет " +
                "для проигрывания siren-кэша.",
        ),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Как обрабатывать Siren-треки (VK проприетарный кодек)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            for ((key, title, desc) in methods) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChange(key) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = current == key, onClick = { onChange(key) })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Карточка статистики хранилища: размер + количество + активная очередь. */
@Composable
private fun OfflineStatsCard(
    downloadedCount: Int,
    totalBytes: Long,
    activeCount: Int,
    queueSize: Int,
    failedCount: Int = 0,
    deadCount: Int = 0,
    sirenCount: Int = 0,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Скачано",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${fmtSize(totalBytes)} ($downloadedCount треков)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
            if (activeCount > 0 || queueSize > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Очередь загрузки",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "$activeCount активных, $queueSize ожидают",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            // #OFFLINE-STATUS-1: разбивка ошибок и siren-кеша.
            if (failedCount > 0 || sirenCount > 0) {
                val parts = ArrayList<String>()
                if (deadCount > 0) parts.add("недоступно $deadCount")
                if (failedCount - deadCount > 0) parts.add("ошибок ${failedCount - deadCount}")
                if (sirenCount > 0) parts.add("siren-кеш $sirenCount")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Ошибки / кеш",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (deadCount > 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (downloadedCount == 0 && activeCount == 0 && queueSize == 0) {
                Text(
                    "Пока ничего не скачано. Включите «Авто загрузка Аудио» во вкладке " +
                        "«Музыка» или нажмите «Загрузить всё».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


/**
 * #OFFLINE-STATUS-1: Список «недоступных» треков (DEAD_URL — URL протух/удалён).
 *
 * Каждый трек — строка с названием/артистом + кнопки «Повторить» (пере-резолв
 * URL через audioGetById → enqueue) и «Удалить» (убрать из списка/диска).
 * Список скроллится если треков много (max-h с overflow).
 */
@Composable
private fun DeadTracksCard(
    tracks: List<DownloadState>,
    onRetry: (DownloadState) -> Unit,
    onRemove: (DownloadState) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            tracks.forEachIndexed { idx, st ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            st.title.ifBlank { "#${st.trackId}" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            st.artist.ifBlank { "неизвестный исполнитель" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onRetry(st) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Refresh, "Повторить", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onRemove(st) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Delete, "Удалить", modifier = Modifier.size(18.dp))
                    }
                }
                if (idx < tracks.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/**
 * #WIFI-LEGEND: карточка-инструкция, расшифровывающая значки скачивания.
 *
 * Пользователь спросил: «почему значок скачано имеет зелёную отметку в виде
 * ви-фи». Это siren-кеш: файл есть (COMPLETED, codec=siren), но офлайн НЕ
 * играется — трек стримится онлайн через HLS, потому что Siren (проприетарный
 * VK кодек, модификация ITU-T G.722.1, 16kHz mono) не декодируется ExoPlayer.
 * Маленький зелёный Wi-Fi в углу = «нужен интернет для воспроизведения».
 *
 * Карточка показывает три варианта значка ровно в том виде, в каком они
 * рисуются в MusicScreen.kt (DownloadDone 20dp + Wifi 10dp BottomEnd offset 1dp,
 * цвет 0xFF22C55E), чтобы пользователь узнал их вживую.
 */
@Composable
private fun OfflineLegendCard() {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val sirenGreen = Color(0xFF22C55E)
    val deadRed = MaterialTheme.colorScheme.error

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Заголовок карточки.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    "Что значат значки у треков",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Строка 1: полноценный офлайн-кеш (M4A).
            LegendRow(
                badge = {
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = "Скачано (M4A)",
                description = "Трек полностью сохранён в формате M4A. " +
                    "Играет офлайн без интернета. Тап по значку — удалить файл.",
                titleColor = MaterialTheme.colorScheme.onSurface,
                descColor = muted,
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            // Строка 2: siren-кеш (Wi-Fi бейдж) — то, ради чего сделана карточка.
            LegendRow(
                badge = {
                    // Точно такой же Box, как в MusicScreen.kt: DownloadDone 20dp
                    // + Wifi 10dp в BottomEnd, offset 1.dp, цвет 0xFF22C55E.
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.DownloadDone,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Icon(
                            Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = sirenGreen,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 1.dp, y = 1.dp)
                                .size(11.dp),
                        )
                    }
                },
                title = "Онлайн-кеш (Siren)",
                description = "Файл скачан, но VK отдал его в кодеке Siren " +
                    "(проприетарный формат, модификация G.722.1). ExoPlayer не " +
                    "умеет декодировать Siren офлайн — трек стримится через HLS " +
                    "при наличии интернета. Зелёный значок Wi-Fi означает: " +
                    "для проигрывания нужно подключение. Тап — удалить кеш.",
                titleColor = MaterialTheme.colorScheme.onSurface,
                descColor = muted,
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            // Строка 3: недоступный трек (DEAD_URL).
            LegendRow(
                badge = {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = deadRed,
                        modifier = Modifier.size(22.dp),
                    )
                },
                title = "Недоступен",
                description = "URL трека протух или трек удалён владельцем. " +
                    "VK пере-выдаёт URL не сразу. Нажмите «Повторить все» ниже — " +
                    "приложение попробует пере-резолвить URL через audioGetById " +
                    "и поставить трек в очередь заново.",
                titleColor = MaterialTheme.colorScheme.onSurface,
                descColor = muted,
            )
        }
    }
}

/** Один ряд инструкции: значок-пример слева, заголовок+описание справа. */
@Composable
private fun LegendRow(
    badge: @Composable () -> Unit,
    title: String,
    description: String,
    titleColor: Color,
    descColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Фиксированная ширина под бейдж — чтобы заголовки выровнялись по вертикали.
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            badge()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = descColor,
            )
        }
    }
}


// ══════════════════════════════════════════════════════════════════════
//  Tab: Видео
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun VideoTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
    context: android.content.Context,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Качество воспроизведения") }
        item { VideoQualityCard(s, app, scope) }
        // #VIDEO-AUTOPLAY: тумблер автовоспроизведения при открытии видео.
        // Default ON — пользователь запросил «по умолчанию включено».
        // При выключении ExoPlayer создаётся с playWhenReady=false и
        // LifecycleStartEffect не форсирует play при возврате из фона.
        item {
            ToggleRow(
                title = "Автовоспроизведение при открытии",
                subtitle = "По умолчанию ВКЛ. Видео начинает играть сразу при " +
                    "открытии плеера. При выключении — плеер готов, но ждёт " +
                    "нажатия кнопки play. Полезно если не хотите автоматически " +
                    "звучать звук при случайном тапе на видео в ленте.",
                checked = s.videoAutoplay,
            ) { scope.launch { app.prefs.setVideoAutoplay(it) } }
        }

        // #AUTO-CACHE-MOVE: «Авто Кеш Историй» перенесён сюда из вкладки Музыка.
        // Логично: истории это видео-контент. При включении StoryVideoDownloadService
        // авто-скачивает истории при просмотре для офлайн-доступа.
        item { SectionHeader("Авто-кэш") }
        item {
            ToggleRow(
                title = "Авто Кеш Историй",
                subtitle = "При включении истории автоматически скачиваются " +
                    "при просмотре — доступны офлайн до истечения 24ч TTL. " +
                    "При выключении — только ручное сохранение.",
                checked = s.autoCacheStories,
            ) { scope.launch { app.prefs.setAutoCacheStories(it) } }
        }
        // #SETTINGS-FIX: лимит кэша историй (был только в Snapshot без UI).
        item {
            val limMb = s.storyCacheLimitMb
            Text(
                "Лимит кэша: ${limMb} MB",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
        item {
            Slider(
                value = s.storyCacheLimitMb.toFloat(),
                onValueChange = { v -> scope.launch { app.prefs.setStoryCacheLimitMb(v.toInt()) } },
                valueRange = 50f..2000f,
                steps = 38, // 50, 100, 150, ..., 2000
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // #VIDEO-PATH: путь сохранения видео. Раньше был захардкожен в
        // VideoDownloadsCard (filesDir/video_downloads) и не давал выбрать
        // папку. Теперь — PathSettingRow с s.videoDownloadPath, аналогично
        // аудио во вкладке Офлайн.
        item { SectionHeader("Путь сохранения") }
        item {
            PathSettingRow(
                label = "Папка для скачанных видео",
                currentPath = s.videoDownloadPath,
                onPathChange = { newPath ->
                    scope.launch {
                        app.prefs.setVideoDownloadPath(newPath)
                        re.pinok.media.VideoDownloadManager.reconfigurePath(newPath)
                    }
                },
                context = context,
            )
        }

        item { SectionHeader("Скачанные видео") }
        item { VideoDownloadsCard(context) }
        // OK-IMPL-1 (Stage 7) + FEED-FIX-4 (#349): toggle для встраивания внешних
        // видео (YouTube/OK iframe) И нативного OK-воспроизведения.
        // Default true — пользователь запросил «тумблер включён по умолчанию».
        // VK-видео играет нативно всегда — этот тумблер на VK НЕ влияет.
        // OkVideoRepository (нативный OK path) НЕ использует WebView → НЕ влияет
        // на авторизацию. WebView включается только для YouTube/EXTERNAL_IFRAME.
        item { SectionHeader("Внешние видео") }
        item {
            ToggleRow(
                title = "Воспроизводить из OK / YouTube",
                subtitle = "По умолчанию ВКЛ. VK-видео работает всегда (нативный " +
                    "ExoPlayer, без рекламы). При включении: OK-видео играет " +
                    "нативно (OkVideoRepository → ExoPlayer, без рекламы, без " +
                    "WebView — не влияет на авторизацию); YouTube/external — через " +
                    "WebView с блокировкой рекламы (7 методов: network block, " +
                    "AdmanHTML stub, advForce, flashvars, ad-cap fake, HD force, " +
                    "device-id wipe). При выключении показывается кнопка «Открыть " +
                    "в браузере».",
                checked = s.externalVideosEnabled,
            ) { scope.launch { app.prefs.setExternalVideosEnabled(it) } }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Эквалайзер (Этап 2)
// ══════════════════════════════════════════════════════════════════════
//  Вкл/выкл отдельных эффектов. Скрывает соответствующие вкладки в
//  полноэкранном EqualizerScreen и toggle'ы в упрощённом EQ аудиоплеера.
//
//  Default: все ВКЛ кроме PresetReverb (часто ломает звук на custom ROM).
//
//  ВАЖНО: это НЕ отключает AudioEffect-объект в AudioEffectsEngine —
//  эффект остаётся созданным (на случай если пользователь вернётся),
//  но enabled=false и UI не даёт к нему доступа.
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun EqualizerTab() {
    // Читаем snapshot один раз при входе. Перечитывается при возврате на вкладку
    // (LazyColumn item recompose). Для мгновенного отклика используем mutableState.
    var flags by remember { mutableStateOf(re.pinok.media.EqualizerFeatureFlags.snapshot()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionHeader("Видимость эффектов")
        }
        item {
            Text(
                "Отключите эффекты, которые не нужны или плохо работают " +
                "на вашем устройстве. Они исчезнут из экрана эквалайзера " +
                "и упрощённой панели в плеере. Сами настройки сохраняются.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            ToggleRow(
                title = "Эквалайзер (полосы)",
                subtitle = "Частотные полосы ±15 dB. Базовая функция — " +
                    "рекомендуется оставить включённым.",
                checked = flags.eqEnabled,
            ) { on ->
                re.pinok.media.EqualizerFeatureFlags.setEqEnabled(on)
                flags = re.pinok.media.EqualizerFeatureFlags.snapshot()
            }
        }
        item {
            ToggleRow(
                title = "BassBoost",
                subtitle = "Усиление низких частот (0..1000).",
                checked = flags.bassEnabled,
            ) { on ->
                re.pinok.media.EqualizerFeatureFlags.setBassEnabled(on)
                flags = re.pinok.media.EqualizerFeatureFlags.snapshot()
            }
        }
        item {
            ToggleRow(
                title = "Virtualizer",
                subtitle = "Пространственный эффект. Лучше работает в наушниках.",
                checked = flags.virtualizerEnabled,
            ) { on ->
                re.pinok.media.EqualizerFeatureFlags.setVirtualizerEnabled(on)
                flags = re.pinok.media.EqualizerFeatureFlags.snapshot()
            }
        }
        item {
            ToggleRow(
                title = "PresetReverb",
                subtitle = "Реверберация. ВЫКЛ по умолчанию — на некоторых " +
                    "устройствах (Xiaomi/Huawei custom ROM) искажает звук.",
                checked = flags.reverbEnabled,
            ) { on ->
                re.pinok.media.EqualizerFeatureFlags.setReverbEnabled(on)
                flags = re.pinok.media.EqualizerFeatureFlags.snapshot()
            }
        }
        item {
            ToggleRow(
                title = "LoudnessEnhancer",
                subtitle = "Нормализация громкости (Android 4.4+). " +
                    "Поднимает тихие участки трека.",
                checked = flags.loudnessEnabled,
            ) { on ->
                re.pinok.media.EqualizerFeatureFlags.setLoudnessEnabled(on)
                flags = re.pinok.media.EqualizerFeatureFlags.snapshot()
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Сеть
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun NetworkTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Безопасность соединения") }
        item { ToggleRow("SSL pinning (применяется после перезапуска)", s.netSslPinning) { scope.launch { app.prefs.setNetSslPinning(it) } } }
        item { ToggleRow("Обход away.php", s.netAwayBypass) { scope.launch { app.prefs.setNetAwayBypass(it) } } }

        item { SectionHeader("Фильтрация трафика") }
        item { ToggleRow("Блокировка рекламы (сеть)", s.netAdBlock) { scope.launch { app.prefs.setNetAdBlock(it) } } }
        // Task #Web-API: переключатель мобильного web-шлюза m.vk.ru (web.api.vk.ru).
        // По умолчанию выключен — используется стандартный api.vk.com (Android).
        // Включение направляет ВСЕ VK API-запросы на web.api.vk.ru. Полезно при
        // блокировках/throttling со стороны api.vk.com или для диагностики.
        item {
            ToggleRow(
                title = "Web API-шлюз (web.api.vk.ru)",
                subtitle = "Маршрутировать запросы через мобильный web-шлюз m.vk.ru вместо api.vk.com. Применяется сразу. Экспериментально.",
                checked = s.netUseWebApiGateway,
            ) { scope.launch { app.prefs.setNetUseWebApiGateway(it) } }
        }

        // #SETTINGS-FIX: Proxy UI — настройки HTTP/SOCKS прокси (netProxyEnabled/Host/Port).
        item { SectionHeader("Прокси") }
        item {
            ToggleRow("Прокси-сервер", s.netProxyEnabled) {
                scope.launch { app.prefs.setNetProxyEnabled(it) }
            }
        }
        if (s.netProxyEnabled) {
            item {
                Text(
                    "Хост",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = s.netProxyHost,
                    onValueChange = { scope.launch { app.prefs.setNetProxyHost(it) } },
                    singleLine = true,
                    placeholder = { Text("proxy.example.com") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                Text(
                    "Порт",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = s.netProxyPort.toString(),
                    onValueChange = { v ->
                        val p = v.toIntOrNull() ?: return@OutlinedTextField
                        scope.launch { app.prefs.setNetProxyPort(p.coerceIn(1, 65535)) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Приватность
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun PrivacyTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Приватность") }
        // #38: toggle «Офлайн-режим» убран из настроек — теперь офлайн
        // включается автоматически после N сетевых неудач либо вручную
        // из drawer (кнопка «Офлайн» → OfflineManagerScreen).
        item { ToggleRow("Маскировка устройства", s.privacyDeviceMask) { scope.launch { app.prefs.setPrivacyDeviceMask(it) } } }
        item { ToggleRow("Анти-телеметрия", s.privacyAntiTelemetry) { scope.launch { app.prefs.setPrivacyAntiTelemetry(it) } } }
        item { ToggleRow("Скрывать «был в сети»", s.privacyHideLastSeen) { scope.launch { app.prefs.setPrivacyHideLastSeen(it) } } }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Защита
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun SecurityTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
    onOpenDevices: () -> Unit = {},
) {
    // #SETTINGS-FIX: состояние диалога создания PIN-кода.
    var showPinSetup by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // §49.6 Sprint VK-ID-1.2: Управление сессиями/устройствами аккаунта.
        // Позволяет удалённо завершать сессии (через cua verification framework).
        item { SectionHeader("Аккаунт VK") }
        item {
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpenDevices() }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.DevicesOther,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Устройства и сессии",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Просмотр активных сессий и удалённое завершение",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            // §49.5.1 #SAFETY-NET-ALERTS: quick toggle в security tab тоже.
            ToggleRow(
                title = "Оповещения о входе",
                subtitle = "Уведомлять о подозрительных входах (новое устройство, город, IP). " +
                    "Проверка каждые ${s.safetyNetPollIntervalMin} мин.",
                checked = s.pushSafetyNetAlerts,
                onToggle = { v ->
                    scope.launch {
                        app.prefs.setPushSafetyNetAlerts(v)
                        if (v) app.securityAlertsPoller?.triggerImmediatePoll()
                    }
                },
            )
        }

        item { SectionHeader("Блокировка приложения") }
        // #SETTINGS-FIX: при включении lockerEnabled без PIN — показываем
        // диалог создания PIN. Без этого lockerEnabled=true, а lockerPinHash=""
        // → toggles бесполезны (MainActivity всегда скипает LockerActivity).
        item {
            ToggleRow("PIN-код", s.lockerEnabled) {
                scope.launch {
                    if (it && s.lockerPinHash.isBlank()) {
                        showPinSetup = true
                    } else {
                        app.prefs.setLockerEnabled(it)
                    }
                }
            }
        }
        if (showPinSetup) {
            item {
                PinSetupDialog(
                    onDismiss = { showPinSetup = false },
                    onPinSet = { hash ->
                        showPinSetup = false
                        scope.launch {
                            app.prefs.setLockerPinHash(hash)
                            app.prefs.setLockerEnabled(true)
                        }
                    },
                )
            }
        }
        item { ToggleRow("Биометрия (требует PIN)", s.lockerBiometric) { scope.launch { app.prefs.setLockerBiometric(it) } } }
        item { ToggleRow("Блокировка при возврате из фона", s.lockerOnBackground) { scope.launch { app.prefs.setLockerOnBackground(it) } } }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Логирование
//
//  #245: отдельная вкладка для отладочных настроек. Сейчас здесь только
//  toggle показа плавающего значка логов (DraggableLogFab) — кнопка
//  ВКЛ/ВЫКЛ, по запросу пользователя. При расширении системы логирования
//  (file logging, level filters, log rotation) сюда добавятся соотв.
//  настройки.
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun LoggingTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Плавающий значок логов") }
        // Fix #237: показ плавающего значка логирования (DraggableLogFab).
        // Default = BuildConfig.DEBUG (виден в debug-сборке, скрыт в release).
        // Пользователь может включить/выключить вручную в любой сборке —
        // это и есть та самая «кнопка ВК/ВЫКЛ» в настройках.
        item {
            ToggleRow(
                title = "Показывать значок логирования",
                subtitle = "Плавающая кнопка просмотра логов (BugReport). " +
                    "По умолчанию: только в debug-сборке.",
                checked = s.showLogFab,
            ) { scope.launch { app.prefs.setShowLogFab(it) } }
        }

        item { SectionHeader("Logcat (adb)") }
        // #LOGCAT-NOISE-FIX (2026-08-03): verbose logcat toggle.
        // В release-сборке DEBUG/VERBOSE логи НЕ пишутся в logcat по умолчанию
        // (только INFO/WARN/ERROR) — logcat чистый от «мусора» (per-segment
        // download progress, per-op URL unmask). Все логи по-прежнему в
        // in-app LogViewer (buffer) и persistent.log файле.
        // Пользователь может включить verbose для глубокой отладки через adb.
        item {
            val verboseDefault = BuildConfig.DEBUG
            ToggleRow(
                title = "Подробный лог в logcat",
                subtitle = "DEBUG/VERBOSE в adb logcat (сегменты загрузки, " +
                    "unmask URL, API-запросы). По умолчанию: ${if (verboseDefault) "вкл (debug)" else "выкл (release)"}. " +
                    "In-app просмотрщик логов содержит всё всегда.",
                checked = re.pinok.util.AppLog.verboseToLogcat,
            ) { enabled ->
                re.pinok.util.AppLog.setVerboseLogcatEnabled(enabled)
            }
        }

        // ─── #LOG-CATEGORIES (2026-08-04): per-category gating ──────────
        //
        // 11 тумблеров для выборочного логирования по разделам приложения.
        // Отключенная категория = логи этого раздела НЕ пишутся ВООБЩЕ
        // (buffer + file + logcat). WARN/ERROR всегда пишутся (критичные
        // события нельзя терять при диагностике).
        //
        // #LOG-CATEGORIES-DEFAULT-CRITICAL (2026-08-05): по умолчанию включены
        // только критичные (AUTH+SYSTEM+NETWORK, см. AppLog.CRITICAL_CATEGORIES).
        // Остальные 8 выключены. Кнопка «Только критичные» = вернуть к дефолту.
        //
        // Состояние:
        //  - В AppLog хранится @Volatile Set<LogCategory> enabledCategories.
        //  - В SovaPrefs.DataStore хранится Set<String> logCategoriesDisabled
        //    (имена отключенных категорий как enum.name).
        //  - UI: локальный mutableStateMapOf для реактивности. При тумблере:
        //    1) AppLog.setCategoryEnabled(cat, enabled) — мгновенно применяется.
        //    2) prefs.setLogCategoriesDisabled(newSet) — persist для следующего старта.
        item { SectionHeader("Разделы приложения (фильтрация логов)") }
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "По умолчанию включены только критичные разделы (Авторизация, " +
                        "Система, Сеть). Остальные можно включить вручную. " +
                        "Критичные события (WARN/ERROR) пишутся всегда.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                )
                val catStates = remember {
                    mutableStateMapOf<re.pinok.util.AppLog.LogCategory, Boolean>().apply {
                        re.pinok.util.AppLog.LogCategory.values().forEach { c ->
                            put(c, re.pinok.util.AppLog.isCategoryEnabled(c))
                        }
                    }
                }
                re.pinok.util.AppLog.LogCategory.values().forEach { cat ->
                    val checked = catStates[cat] ?: true
                    ToggleRow(
                        title = cat.title,
                        subtitle = cat.description,
                        checked = checked,
                    ) { enabled ->
                        catStates[cat] = enabled
                        re.pinok.util.AppLog.setCategoryEnabled(cat, enabled)
                        // Persist полного множества отключенных категорий
                        // (замена, не merge). Для следующего старта приложения.
                        val disabled = catStates.entries
                            .filter { !it.value }
                            .map { it.key.name }
                            .toSet()
                        scope.launch { app.prefs.setLogCategoriesDisabled(disabled) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        re.pinok.util.AppLog.LogCategory.values().forEach { c ->
                            catStates[c] = true
                            re.pinok.util.AppLog.setCategoryEnabled(c, true)
                        }
                        scope.launch { app.prefs.setLogCategoriesDisabled(emptySet()) }
                    }) { Text("Включить все") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        // #LOG-CATEGORIES-DEFAULT-CRITICAL: тот же набор что и
                        // default в SovaPrefs/AppLog.CRITICAL_CATEGORIES —
                        // AUTH + SYSTEM + NETWORK. Кнопка возвращает к дефолту.
                        val keep = re.pinok.util.AppLog.CRITICAL_CATEGORIES
                        re.pinok.util.AppLog.LogCategory.values().forEach { c ->
                            val en = c in keep
                            catStates[c] = en
                            re.pinok.util.AppLog.setCategoryEnabled(c, en)
                        }
                        val disabled = re.pinok.util.AppLog.LogCategory.values()
                            .filter { it !in keep }
                            .map { it.name }
                            .toSet()
                        scope.launch { app.prefs.setLogCategoriesDisabled(disabled) }
                    }) { Text("Только критичные") }
                }
            }
        }

        item { SectionHeader("Информация") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Логи приложения пишутся в Android Logcat " +
                            "(теги начинаются на «Sova», «Pinok», «VK»). " +
                            "Плавающий значок позволяет открыть окно просмотра " +
                            "логов прямо из любого экрана без подключения adb.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "Build type: ${BuildConfig.BUILD_TYPE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (BuildConfig.DEBUG) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "DEBUG-сборка: значок логов виден по умолчанию.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Video Quality Card — Fix #334: предпочтительное качество видео
// ══════════════════════════════════════════════════════════════════════

/**
 * Fix #334: карта вариантов качества. Порядок — от максимального к минимальному.
 * Значения сохраняются в SovaPrefs.videoPreferredQuality и читаются VideoPlayerScreen
 * при выборе начального качества для воспроизведения.
 *
 * Логика выбора в плеере: если preferred = "1080", плеер ищет доступное качество
 * ≤ 1080 (ближайшее снизу). Если все доступные выше 1080 — берётся минимальное из
 * них (лучше меньшее качество, чем совсем ничего). "auto" = всегда максимальное.
 */
private val VIDEO_QUALITY_OPTIONS = listOf(
    "auto" to "Авто (максимальное)",
    "2160" to "4K (2160p)",
    "1440" to "1440p",
    "1080" to "1080p",
    "720"  to "720p",
    "480"  to "480p",
    "360"  to "360p",
    "240"  to "240p",
    "144"  to "144p",
)

@Composable
private fun VideoQualityCard(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Предпочтительное качество",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Применяется к видео в плеере и клипам. Если выбранное недоступно — берётся ближайшее ниже.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            // Список вариантов как вертикальный radio-список.
            VIDEO_QUALITY_OPTIONS.forEach { (value, label) ->
                val selected = s.videoPreferredQuality == value
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { scope.launch { app.prefs.setVideoPreferredQuality(value) } }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { scope.launch { app.prefs.setVideoPreferredQuality(value) } },
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Video Downloads Card
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun VideoDownloadsCard(context: android.content.Context) {
    var totalBytes by remember { mutableLongStateOf(0L) }
    var fileCount by remember { mutableIntStateOf(0) }
    var dirPath by remember { mutableStateOf("") }
    // #DOCFILE-SD: отдельная статистика для SD-карты (когда активирована).
    var sdActive by remember { mutableStateOf(false) }
    var sdBytes by remember { mutableLongStateOf(0L) }
    var sdCount by remember { mutableIntStateOf(0) }

    // #VIDEO-PATH: читаем реальные данные из VideoDownloadManager (а не
    // захардкоженный filesDir/video_downloads). Путь меняется через
    // PathSettingRow выше — перечитываем при каждом входе во вкладку.
    LaunchedEffect(Unit) {
        try {
            val stats = re.pinok.media.VideoDownloadManager.getStorageStats()
            totalBytes = stats.first
            fileCount = stats.second
            dirPath = re.pinok.media.VideoDownloadManager.getDownloadDir().absolutePath
            // #DOCFILE-SD: SD-карта stats (если активирована).
            sdActive = re.pinok.media.VideoDownloadManager.isSdCardActive()
            if (sdActive) {
                val sdStats = re.pinok.media.VideoDownloadManager.getSdCardStats()
                sdBytes = sdStats.first
                sdCount = sdStats.second
            }
        } catch (e: IllegalStateException) {
            // VideoDownloadManager ещё не инициализирован — показываем internal fallback.
            val dir = java.io.File(context.filesDir, "video_downloads")
            dirPath = dir.absolutePath
            if (dir.exists()) {
                val files = dir.listFiles() ?: emptyArray()
                totalBytes = files.sumOf { it.length() }
                fileCount = files.size
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Использовано", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${fmtSize(totalBytes)} ($fileCount файлов)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
            }
            // #DOCFILE-SD: отдельная строка для SD-карты если активирована.
            if (sdActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("SD-карта", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${fmtSize(sdBytes)} ($sdCount файлов)",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Видео не кэшируются при просмотре — только сохраняются по запросу.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (totalBytes > 0 || sdBytes > 0) {
                    OutlinedButton(
                        onClick = {
                            // #DOCFILE-SD: используем clearAllDownloads() — чистит и
                            // internal, и SD-карту (DocumentFile tree). Раньше UI
                            // напрямую дёргал dir.listFiles() → SD-карта оставалась
                            // с «зомби»-файлами после «Удалить все».
                            try {
                                val deleted = re.pinok.media.VideoDownloadManager.clearAllDownloads()
                                totalBytes = 0L
                                fileCount = 0
                                sdBytes = 0L
                                sdCount = 0
                                re.pinok.util.AppLog.i("VideoDownloadsCard",
                                    "clearAllDownloads: $deleted files deleted (internal + SD)")
                            } catch (e: IllegalStateException) {
                                // fallback — internal dir (менеджер не инициализирован).
                                val dir = java.io.File(context.filesDir, "video_downloads")
                                val files = dir.listFiles()
                                if (files != null) {
                                    var deleted = 0
                                    for (f in files) { if (f.delete()) deleted++ }
                                    totalBytes = 0L
                                    fileCount = 0
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Удалить все") }
                }
            }
            // Путь сохранения видео — реальный из VideoDownloadManager.
            Text(
                "Папка сохранения",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                dirPath,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Shared UI Components
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

// ВАЖНО: onToggle должен быть ПОСЛЕДНИМ параметром, чтобы работала
// trailing-lambda форма вызова: ToggleRow("x", bool) { ... }.
// Если enabled поставить последним, лямбда уйдёт в enabled (Boolean) и
// компилятор выдаст "No value passed for parameter 'onToggle'" +
// "Unresolved reference 'it'" (баг коммита a4d354d, фикс #TOGGLE-SIG).
@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
        }
    }
}

/**
 * Overload with subtitle — used for experimental toggles that need explanation.
 * Task #Web-API: web.api.vk.ru toggle uses this overload.
 * #MONET-HYBRID: добавлен enabled (onToggle остаётся последним для trailing-lambda).
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onToggle, enabled = enabled)
        }
    }
}

@Composable
private fun FontScaleRow(value: Int, onChange: (Int) -> Unit) {
    // Масштаб текста в процентах: 70% (мельче) .. 150% (крупнее).
    // Шаг 5% — достаточно мелкий для точной настройки, но не бесконечный.
    // Локальный state для мгновенного отклика слайдера; в prefs пишем onValueChangeFinished.
    var local by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) { local = value }

    val clamped = local.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)
    val display = if (clamped == 100) "100% (системный)" else "$clamped%"

    Card {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Размер текста", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Масштаб интерфейса: $display",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = display,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = clamped.toFloat(),
                onValueChange = { local = it.roundToInt() },
                onValueChangeFinished = { onChange(local.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX)) },
                valueRange = FONT_SCALE_MIN.toFloat()..FONT_SCALE_MAX.toFloat(),
                steps = ((FONT_SCALE_MAX - FONT_SCALE_MIN) / 5) - 1, // шаг 5%
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("A", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                Text("A", style = MaterialTheme.typography.bodySmall, fontSize = 18.sp)
            }
        }
    }
}

private const val FONT_SCALE_MIN = 70
private const val FONT_SCALE_MAX = 150

/**
 * Fix #224: слайдер скорости анимаций интерфейса.
 * 0% — анимации выключены (мгновенные переходы через snap).
 * 100% — нормальная скорость. 50% — вдвое быстрее (длительность × 0.5).
 * Применяется к NavHost-переходам и swipe-reply spring в чатах.
 */
@Composable
private fun AnimSpeedRow(value: Int, onChange: (Int) -> Unit) {
    var local by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) { local = value }

    val clamped = local.coerceIn(0, 100)
    val display = when (clamped) {
        0 -> "Выключены"
        100 -> "Норма"
        else -> "$clamped%"
    }
    val hint = when (clamped) {
        0 -> "мгновенные переходы, без анимации"
        100 -> "стандартные переходы между экранами"
        else -> "длительность ${clamped}% от нормы (≈ в ${(100.0 / clamped).formatScaleFactor()}× быстрее)"
    }

    Card {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Скорость анимации", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Переходы между экранами, swipe-ответ: $hint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = display,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = clamped.toFloat(),
                onValueChange = { local = it.roundToInt() },
                onValueChangeFinished = { onChange(local.coerceIn(0, 100)) },
                valueRange = 0f..100f,
                steps = 9, // 11 значений: 0,10,20,…,100
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Выкл", style = MaterialTheme.typography.bodySmall)
                Text("Норма", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun Double.formatScaleFactor(): String = DecimalFormat("0.#").format(this)

/**
 * Fix #228: масштаб стикер-фото в чате (0..40%, шаг 1%).
 *
 * Стикеры, отправленные как картинка (messagesSendStickerAsImage), рендерятся
 * в исходном размере (Fix #227). Ползунок позволяет опционально увеличить их
 * размер — итоговый размер = naturalSize × (1 + scale/100).
 *
 * 0%  — исходный размер (по умолчанию).
 * 40% — +40% к оригиналу (максимум, ограничение пользователя).
 *
 * steps = 39 → 41 значение (0..40) с шагом 1.
 */
@Composable
private fun StickerPhotoScaleRow(value: Int, onChange: (Int) -> Unit) {
    var local by remember { mutableIntStateOf(value) }
    LaunchedEffect(value) { local = value }

    val clamped = local.coerceIn(0, 40)
    val display = when (clamped) {
        0 -> "Оригинал"
        else -> "+$clamped%"
    }
    val hint = when (clamped) {
        0 -> "исходный размер стикер-картинки"
        else -> "итоговый размер ${(100 + clamped)}% от оригинала"
    }

    Card {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Размер стикер-картинок", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Стикеры как фото в чате: $hint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = display,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = clamped.toFloat(),
                onValueChange = { local = it.roundToInt() },
                onValueChangeFinished = { onChange(local.coerceIn(0, 40)) },
                valueRange = 0f..40f,
                steps = 39, // 41 значение: 0,1,2,…,40
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Оригинал", style = MaterialTheme.typography.bodySmall)
                Text("+40%", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AccentPicker(selectedIndex: Int, onPick: (Int) -> Unit) {
    val safeIndex = selectedIndex.coerceIn(0, SovaColors.accents.lastIndex)
    val accent = SovaColors.accents[safeIndex]
    val name = SovaColors.accentNames.getOrElse(safeIndex) { "—" }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Акцентный цвет", style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${safeIndex + 1} / ${SovaColors.accents.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Slider(
                value = safeIndex.toFloat(),
                onValueChange = { v ->
                    val idx = v.roundToInt().coerceIn(0, SovaColors.accents.lastIndex)
                    if (idx != safeIndex) onPick(idx)
                },
                valueRange = 0f..SovaColors.accents.lastIndex.toFloat(),
                steps = SovaColors.accents.size - 2,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("PinoK", style = MaterialTheme.typography.headlineMedium)
        HorizontalDivider()
        Text("Версия: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Text("Сборка: ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodyMedium)
        Text("VK API: ${BuildConfig.VK_API_VERSION}", style = MaterialTheme.typography.bodyMedium)
        Text("Application ID: ${BuildConfig.APPLICATION_ID}", style = MaterialTheme.typography.bodySmall)
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Formatting helpers
// ══════════════════════════════════════════════════════════════════════

private val sizeFormat = DecimalFormat("#,##0.0")

private fun fmtSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes Б"
        bytes < 1024 * 1024 -> "${sizeFormat.format(bytes / 1024.0)} КБ"
        bytes < 1024 * 1024 * 1024 -> "${sizeFormat.format(bytes / (1024.0 * 1024.0))} МБ"
        else -> "${sizeFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))} ГБ"
    }
}

/**
 * #75: Строка настройки пути сохранения.
 * Показывает текущий путь + кнопку выбора новой директории (SAF).
 */
@Composable
private fun PathSettingRow(
    label: String,
    currentPath: String,
    onPathChange: (String) -> Unit,
    context: android.content.Context,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // #SAF-PERSIST: персистим URI-grant чтобы выбор папки переживал
            // рестарт приложения. Без этого grant живёт только до process death
            // — после рестарта contentResolver.openFileDescriptor(uri) кидает
            // SecurityException, и пользователь не понимает почему «папка
            // выбрана но не пишется». takePersistableUriPermission хранит grant
            // в системе до явного releasePersistableUriPermission.
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                AppLog.i("Settings", "PathSettingRow: persisted URI grant for $uri")
            } catch (e: SecurityException) {
                AppLog.w("Settings", "PathSettingRow: takePersistableUriPermission failed: ${e.message}")
            }
            // Сохраняем полный URI (content://...). TrackDownloadManager.reconfigurePath
            // парсит /tree/primary:Music/PinoK → /storage/emulated/0/Music/PinoK (File API).
            // Если URI не tree-primary (SD-карта XXXX-XXXX:..., USB OTG) — активируется
            // DocumentFile API (#DOCFILE-SD): финальный файл копируется на SD-карту через
            // DocumentFileStorage.copyFileToTree, internal копия остаётся для playback.
            val path = uri.toString()
            AppLog.i("Settings", "PathSettingRow: selected=$path")
            onPathChange(path)
        }
    }

    // Fix #119: На Android 11+ (API 30+) для записи в /Music/PinoK и другие
    // public-папки через File API нужно MANAGE_EXTERNAL_STORAGE.
    val needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        !Environment.isExternalStorageManager()
    val allFilesAccessGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Environment.isExternalStorageManager()

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (currentPath.isBlank()) "По умолчанию (внутренняя память)"
                       else formatDisplayPath(currentPath),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))

            // Fix #119: подсказка про MANAGE_EXTERNAL_STORAGE
            if (needsAllFilesAccess) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Для записи в /Music/PinoK нужен доступ ко всем файлам",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (allFilesAccessGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Доступ ко всем файлам предоставлен",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { launcher.launch(null) }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Выбрать папку")
                }
                if (needsAllFilesAccess) {
                    OutlinedButton(onClick = {
                        // Fix #119: открываем системные настройки для MANAGE_EXTERNAL_STORAGE
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback: общий экран всех файлов
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Доступ к файлам")
                    }
                }
                if (currentPath.isNotBlank()) {
                    TextButton(onClick = { onPathChange("") }) {
                        Text("Сбросить")
                    }
                }
            }
        }
    }
}

/**
 * #SAF-PERSIST: преобразует сохранённый путь в читаемый вид для UI.
 *
 * Поддерживаемые форматы:
 *  - "" → "По умолчанию"
 *  - "/Music/PinoK" → как есть
 *  - "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FPinoK"
 *    → "/Music/PinoK" (извлекаем tree-part, URL-decode)
 *  - "content://com.android.externalstorage.documents/tree/XXXX-XXXX%3AMusic"
 *    → "SD-карта: Music" (non-primary volume)
 *  - прочее → как есть
 */
private fun formatDisplayPath(path: String): String {
    if (path.isBlank()) return "По умолчанию"
    if (!path.startsWith("content://")) return path
    val treeIdx = path.indexOf("/tree/")
    if (treeIdx < 0) return path
    val raw = path.substring(treeIdx + 6)
    val decoded = runCatching { android.net.Uri.decode(raw) }.getOrDefault(raw)
    return when {
        decoded.startsWith("primary:") -> {
            val sub = decoded.removePrefix("primary:").removePrefix("/")
            if (sub.isBlank()) "/storage/emulated/0" else "/$sub"
        }
        decoded.contains(":") -> {
            val vol = decoded.substringBefore(":")
            val sub = decoded.substringAfter(":").removePrefix("/")
            if (sub.isBlank()) "[$vol]" else "[$vol] /$sub"
        }
        else -> decoded
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Tab: Уведомления (Fix #298)
// ══════════════════════════════════════════════════════════════════════

/**
 * Fix #302 (Task 2-b): определение одного sn_* notification-тоггла.
 *
 * @param key     BFF-ключ параметра (например "sn_messages"). Отправляется
 *                на сервер через settingsGeneral.toggleNotify(key, enabled).
 * @param title   Русский заголовок строки.
 * @param default Значение по умолчанию (используется пока API не ответил
 *                и нет кэша в SovaPrefs.notifyCacheJson).
 */
private data class NotifyToggleDef(
    val key: String,
    val title: String,
    val default: Boolean,
)

/**
 * Fix #302 (Task 2-b): карта всех sn_* параметров, добавленных на вкладку
 * «Уведомления» (поверх базового msgMute-тоггла и карточки «Не беспокоить»).
 *
 * Источник: §32 VK_IMPORT_API.MD + NOTIF-RESEARCH-1 (gap analysis G1 —
 * пользователь хотел конкретные per-category переключатели прямо во вкладке,
 * а не только ссылку на полный NotificationSettingsScreen).
 *
 * Ключи соответствуют BFF settingsGeneral.getNotifySettings(page="notify")
 * → sections[].params[].key. Defaults подобраны под «не терять важные
 * уведомления, но убрать шум»: likes/reposts/new_posts по умолчанию off
 * (слишком много уведомлений), остальное on.
 */
private val NOTIFY_MSG_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_messages",        "Личные сообщения",        default = true),
    NotifyToggleDef("sn_chats",           "Сообщения из бесед",      default = true),
    NotifyToggleDef("sn_mentions",        "Упоминания",              default = true),
    NotifyToggleDef("sn_mass_mentions",   "Массовые упоминания",     default = true),
    NotifyToggleDef("sn_message_requests","Запросы на переписку",    default = true),
)

private val NOTIFY_GROUPS_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_groups",          "Сообщения от сообществ",      default = true),
    NotifyToggleDef("sn_group_invites",   "Приглашения в сообщества",    default = true),
    NotifyToggleDef("sn_group_actions",   "Действия администратора",     default = false),
)

private val NOTIFY_FRIEND_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_friend_accepted", "Принятые заявки в друзья", default = true),
    NotifyToggleDef("sn_friend_requests", "Заявки в друзья",          default = true),
    NotifyToggleDef("sn_friend_found",    "Возможные друзья",         default = false),
    NotifyToggleDef("sn_birthdays",       "Дни рождения",             default = true),
)

private val NOTIFY_REACTION_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_likes",    "Отметки «Нравится»",   default = false),
    NotifyToggleDef("sn_comments", "Комментарии",          default = true),
    NotifyToggleDef("sn_reposts",  "Репосты",              default = false),
    NotifyToggleDef("sn_replies",  "Ответы на комментарии", default = true),
)

// §1-NOTIF-ARCHIVE: «Обратная связь» — категории из архива m.vk.ru/settings?act=notify
// (group= params), отсутствовавшие в приложении. Ключи sn_<archive_key>.
private val NOTIFY_FEEDBACK_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_copies",           "Поделились",                    default = false),
    NotifyToggleDef("sn_wall_posts",       "Посты на стене",                default = true),
    NotifyToggleDef("sn_related_events",   "Связано с вами",                default = true),
    NotifyToggleDef("sn_story_reply",      "Ответы на истории",             default = true),
    NotifyToggleDef("sn_story_question",   "Вопросы и мнения в историях",   default = false),
    NotifyToggleDef("sn_clips_duet",       "Дуэты с клипами",               default = false),
    NotifyToggleDef("sn_clips_from_video", "Клипы с видео",                 default = false),
    NotifyToggleDef("sn_co_ownership",     "Соавторство",                   default = false),
)

private val NOTIFY_CONTENT_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_new_posts",  "Новые записи друзей", default = false),
    NotifyToggleDef("sn_stories",    "Истории",            default = true),
    NotifyToggleDef("sn_photo_tags", "Отметки на фото",    default = true),
)

// §1-NOTIF-ARCHIVE: «События» — дополнительные категории из архива.
private val NOTIFY_EVENTS_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_friends_follow",       "Подписчики",              default = true),
    NotifyToggleDef("sn_event_soon",           "Ближайшие мероприятия",   default = false),
    NotifyToggleDef("sn_interest",             "Интересные материалы",    default = false),
    NotifyToggleDef("sn_group_recommendation", "Рекомендации сообществ",  default = false),
    NotifyToggleDef("sn_clips",                "Интересные клипы",        default = false),
    NotifyToggleDef("sn_feed_promo",           "Актуальное",              default = false),
)

private val NOTIFY_OTHER_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_app_invites",   "Приглашения в приложения", default = false),
    NotifyToggleDef("sn_events",        "События",                  default = true),
    NotifyToggleDef("sn_polls",         "Опросы",                   default = true),
    NotifyToggleDef("sn_market_orders", "Заказы из магазина",       default = true),
)

// §1-NOTIF-ARCHIVE: «Другое» — расширенный набор из архива (13 категорий).
private val NOTIFY_EXTRA_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_private_group_post",                  "Посты в закрытых сообществах", default = false),
    NotifyToggleDef("sn_gifts",                               "Подарки",                      default = true),
    NotifyToggleDef("sn_lives",                               "Трансляции",                   default = false),
    NotifyToggleDef("sn_video_playlists",                     "Обновление плейлиста",         default = false),
    NotifyToggleDef("sn_video_groups_publish",                "Новое видео в сообществах",    default = true),
    NotifyToggleDef("sn_content_achievements",                "Достижения",                   default = false),
    NotifyToggleDef("sn_service_recommend",                   "Рекомендации сервисов",        default = false),
    NotifyToggleDef("sn_bookmarks",                           "Закладки",                     default = false),
    NotifyToggleDef("sn_market",                              "Магазин",                      default = false),
    NotifyToggleDef("sn_lovina",                              "Знакомства",                   default = false),
    NotifyToggleDef("sn_stickers_bonus_expiration",           "Энергия скоро сгорит",         default = false),
    NotifyToggleDef("sn_stickers_bonus_discounts_expiration", "Скидки на стикеры",            default = false),
)

// §1-NOTIF-ARCHIVE: master-toggle «Получать push-уведомления» (push_send в архиве).
// Отдельно от секций — управляет всей доставкой push на устройство.
private val NOTIFY_MASTER_TOGGLES: List<NotifyToggleDef> = listOf(
    NotifyToggleDef("sn_push_send", "Получать push-уведомления", default = true),
)

/** Все sn_* toggles одним списком (для построения defaults/titles map). */
private val ALL_NOTIFY_TOGGLES: List<NotifyToggleDef> =
    NOTIFY_MASTER_TOGGLES +
    NOTIFY_MSG_TOGGLES +
    NOTIFY_GROUPS_TOGGLES +
    NOTIFY_FRIEND_TOGGLES +
    NOTIFY_REACTION_TOGGLES +
    NOTIFY_FEEDBACK_TOGGLES +
    NOTIFY_CONTENT_TOGGLES +
    NOTIFY_EVENTS_TOGGLES +
    NOTIFY_OTHER_TOGGLES +
    NOTIFY_EXTRA_TOGGLES

/** Дефолты (для инициализации UI до ответа API). */
private val NOTIFY_DEFAULTS: Map<String, Boolean> =
    ALL_NOTIFY_TOGGLES.associate { it.key to it.default }

/** Русский заголовок по ключу (для Toast-сообщений об ошибке). */
private val NOTIFY_TITLES: Map<String, String> =
    ALL_NOTIFY_TOGGLES.associate { it.key to it.title }

/**
 * Fix #302 (Task 2-b): десериализация кэша sn_* состояний из JSON-строки
 * (SovaPrefs.notifyCacheJson). Формат: `{"sn_messages":true,...}`.
 * Толерантна к мусору/повреждённому JSON — возвращает emptyMap.
 */
private fun parseNotifyCache(json: String): Map<String, Boolean> {
    if (json.isBlank()) return emptyMap()
    return try {
        val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
        val out = mutableMapOf<String, Boolean>()
        obj.entrySet().forEach { (k, v) ->
            if (k.isBlank() || !v.isJsonPrimitive) return@forEach
            // Gson getAsBoolean: boolean→as-is, string→parseBoolean, number→!=0.
            // Нам важны только настоящие boolean-примитивы; остальные игнорируем.
            if (!v.asJsonPrimitive.isBoolean) return@forEach
            out[k] = v.asBoolean
        }
        out
    } catch (e: Exception) {
        AppLog.w("NotificationsTab", "notify cache parse failed: ${e.message}")
        emptyMap()
    }
}

/**
 * Fix #302 (Task 2-b): сериализация кэша sn_* состояний в JSON-строку.
 * Сохраняется в SovaPrefs.notifyCacheJson после успешной загрузки с API
 * или после успешного toggle, чтобы при повторном входе на вкладку
 * пользователь сразу видел актуальное состояние без ожидания сети.
 */
private fun serializeNotifyCache(map: Map<String, Boolean>): String {
    val obj = com.google.gson.JsonObject()
    map.forEach { (k, v) -> obj.addProperty(k, v) }
    return obj.toString()
}

/**
 * Fix #302 (Task 2-b): вариант ToggleRow с состоянием загрузки.
 *
 * Пока API-вызов в полёте, Switch заменяется на маленький
 * CircularProgressIndicator (24dp, strokeWidth=2dp). Это даёт пользователю
 * явный визуальный фидбек, что изменение применяется, и блокирует повторный
 * тап (нельзя начать второй API-вызов поверх первого).
 *
 * @param title   Заголовок строки (крупный текст).
 * @param checked Текущее состояние (optimistic — обновляется мгновенно).
 * @param loading Идёт ли API-вызов для этого ключа.
 * @param onToggle Колбэк на переключение. Вызывается только если !loading.
 */
@Composable
private fun ToggleRowWithLoading(
    title: String,
    checked: Boolean,
    loading: Boolean,
    onToggle: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    // alpha 0.7 пока идёт запрос — визуально «серое» состояние,
                    // но не ниже 0.7 чтобы сохранить WCAG AA на тёмной теме.
                    color = if (loading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Switch(checked = checked, onCheckedChange = onToggle)
            }
        }
    }
}

/**
 * Fix #302 (Task 2-b): выводит секцию заголовок + N toggle-строк.
 *
 * Хелпер-расширение на LazyListScope — позволяет компактно описать
 * 6 секций (~20 тогглов) без дублирования кода. Каждый item получает
 * уникальный key (sn_*) для стабильной recomposition.
 */
private fun LazyListScope.notifyToggleSection(
    sectionTitle: String,
    toggles: List<NotifyToggleDef>,
    states: Map<String, Boolean>,
    loadingKeys: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    item(key = "header_${sectionTitle}") { SectionHeader(sectionTitle) }
    items(toggles, key = { it.key }) { t ->
        ToggleRowWithLoading(
            title = t.title,
            checked = states[t.key] ?: NOTIFY_DEFAULTS[t.key] ?: true,
            loading = t.key in loadingKeys,
            onToggle = { onToggle(t.key, it) },
        )
    }
}

/**
 * Fix #298: вкладка «Уведомления» в настройках.
 *
 * Показывает быстрые переключатели (звук уведомлений, признак «Не беспокоить»,
 * чаты с отключёнными уведомлениями в счётчике) + кнопку перехода к полному
 * экрану [NotificationSettingsScreen], где находятся:
 *  - «Не беспокоить» с таймерами 15мин/1ч/8ч/навсегда
 *  - BFF-секции (account.setInfo / settingsGeneral.setNotifySettings)
 *  - Заблокированные пользователи (account.getBanned)
 *  - Фильтр нецензурной лексики (account.setObsceneFilter)
 *  - Per-community / per-app push (messages.allowMessagesFromGroup / apps.allowNotifications)
 *
 * Карта API и UI структуры построена в NOTIF-RESEARCH-1 (см. WORKLOG.md).
 *
 * @param onOpenNotificationSettings переход к Screen.NotificationSettings
 */
@Composable
private fun NotificationsTab(
    s: SovaPrefs.Snapshot,
    app: SovaApp,
    scope: CoroutineScope,
    onOpenNotificationSettings: () -> Unit,
) {
    val context = LocalContext.current
    // Локальный кэш «Не беспокоить» — загружаем при первом показе.
    var silentUntil by remember { androidx.compose.runtime.mutableStateOf<Long?>(null) }
    var silentLoading by remember { androidx.compose.runtime.mutableStateOf(false) }

    // ────────────────────────────────────────────────────────────────────
    // Fix #302 (Task 2-b): состояние sn_* notification toggles.
    // ────────────────────────────────────────────────────────────────────
    // Стратегия:
    //   1. На первом показе UI инициализируется из кэша (SovaPrefs.notifyCacheJson)
    //      — мгновенно, без ожидания сети.
    //   2. Параллельно идёт запрос settingsGeneral.getNotifySettings(page="notify").
    //      При успехе — обновляем states и персистим кэш.
    //   3. На каждый тап тоггла:
    //      a) optimistic update (UI flip мгновенно),
    //      b) добавляем ключ в loadingKeys (Switch → CircularProgressIndicator),
    //      c) асинхронно вызываем settingsGeneral.toggleNotify(key, value),
    //      d) на успех — персистим кэш,
    //      e) на ошибку — откат UI + Toast «Не удалось изменить «…»»,
    //      f) убираем ключ из loadingKeys.
    var notifyStates by remember {
        mutableStateOf<Map<String, Boolean>>(parseNotifyCache(s.notifyCacheJson))
    }
    var loadingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var notifyInitialLoaded by remember { mutableStateOf(false) }

    /**
     * Optimistic toggle sn_<key> с API-вызовом и revert-on-failure.
     * См. стратегию выше. Локальная функция (замыкает notifyStates / loadingKeys /
     * scope / context / app).
     */
    fun toggleNotify(key: String, newValue: Boolean) {
        val oldValue = notifyStates[key] ?: NOTIFY_DEFAULTS[key] ?: true
        if (oldValue == newValue) return  // нет изменения — нет запроса
        notifyStates = notifyStates + (key to newValue)
        loadingKeys = loadingKeys + key
        scope.launch {
            val ok = try {
                app.apiClient.settingsGeneralToggleNotify(key, newValue)
            } catch (e: Exception) {
                AppLog.e("NotificationsTab", "toggleNotify($key, $newValue) failed", e)
                false
            }
            if (ok) {
                // Персистим кэш для следующего входа на вкладку.
                app.prefs.setNotifyCacheJson(serializeNotifyCache(notifyStates))
            } else {
                // Откатываем UI на прежнее значение + Toast.
                notifyStates = notifyStates + (key to oldValue)
                val title = NOTIFY_TITLES[key] ?: key
                android.widget.Toast.makeText(
                    context,
                    "Не удалось изменить «$title»",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            loadingKeys = loadingKeys - key
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        // 1) Silent-mode — загрузка статуса.
        if (silentUntil == null && !silentLoading) {
            silentLoading = true
            try {
                val st = app.apiClient.accountGetSilentModeStatus()
                silentUntil = st?.silentUntil ?: 0L
            } catch (e: Exception) {
                silentUntil = 0L
            } finally {
                silentLoading = false
            }
        }
        // 2) sn_* toggles — первичная загрузка с BFF (один раз за сессию вкладки).
        if (!notifyInitialLoaded) {
            try {
                val sections = app.apiClient.settingsGeneralGetNotifySettings("notify")
                if (sections != null) {
                    val apiMap = mutableMapOf<String, Boolean>()
                    sections.flatMap { it.params }.forEach { p ->
                        if (p.isChecked != null &&
                            p.key.isNotBlank() &&
                            NOTIFY_DEFAULTS.containsKey(p.key)
                        ) {
                            apiMap[p.key] = p.isChecked
                        }
                    }
                    // Merge с учётом in-flight тогглов:
                    //   - если ключ в loadingKeys (пользователь только что тапнул)
                    //     — сохраняем его optimistic-значение, не затираем API-ответом;
                    //   - иначе приоритет: API → cache (notifyStates) → default.
                    // Это предотвращает гонку «пользователь тапнул → API применил
                    // старое значение → UI моргнул обратно».
                    val merged = NOTIFY_DEFAULTS.keys.associateWith { k ->
                        when {
                            k in loadingKeys -> notifyStates[k] ?: NOTIFY_DEFAULTS[k]!!
                            apiMap.containsKey(k) -> apiMap[k]!!
                            notifyStates.containsKey(k) -> notifyStates[k]!!
                            else -> NOTIFY_DEFAULTS[k]!!
                        }
                    }
                    notifyStates = merged
                    app.prefs.setNotifyCacheJson(serializeNotifyCache(merged))
                }
            } catch (e: Exception) {
                AppLog.w("NotificationsTab", "getNotifySettings failed: ${e.message}")
            } finally {
                notifyInitialLoaded = true
            }
        }
    }

    val now = System.currentTimeMillis() / 1000
    val isSilentActive = silentUntil != null && (silentUntil == -1L || (silentUntil!! > 0 && silentUntil!! > now))

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader("Не беспокоить") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSilentActive) Icons.Outlined.NotificationsOff
                                          else Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = if (isSilentActive) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isSilentActive) "Не беспокоить включено" else "Не беспокоить выключено",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                            )
                            Text(
                                text = when {
                                    silentLoading -> "Проверка статуса…"
                                    silentUntil == -1L -> "До отключения вручную"
                                    silentUntil != null && silentUntil!! > 0 && isSilentActive ->
                                        "До ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(silentUntil!! * 1000))}"
                                    else -> "Уведомления включены"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Быстрые таймеры «Не беспокоить»
                        listOf(
                            "15 мин" to 900L,
                            "1 час" to 3600L,
                            "8 часов" to 28800L,
                        ).forEach { (label, secs) ->
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            app.apiClient.accountStartSilentMode(secs)
                                            silentUntil = now + secs
                                            android.widget.Toast.makeText(context,
                                                "Не беспокоить на $label", android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context,
                                                "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isSilentActive,
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1, fontSize = 11.sp) }
                        }
                    }
                    if (isSilentActive) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        app.apiClient.accountStopSilentMode()
                                        silentUntil = 0L
                                        android.widget.Toast.makeText(context,
                                            "Не беспокоить выключено", android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context,
                                            "Ошибка: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Выключить «Не беспокоить»") }
                    }
                }
            }
        }

        // §1-NOTIF-ARCHIVE: master-toggle «Получать push-уведомления» — главный
        // выключатель всей push-доставки (push_send в архиве m.vk.ru/settings?act=notify).
        // Стоит выше всех секций, т.к. при выключении все остальные тогглы не имеют эффекта.
        item { SectionHeader("Push-уведомления") }
        items(NOTIFY_MASTER_TOGGLES, key = { it.key }) { t ->
            ToggleRowWithLoading(
                title = t.title,
                subtitle = "Разрешить ВКонтакте отправлять push на это устройство",
                checked = notifyStates[t.key] ?: NOTIFY_DEFAULTS[t.key] ?: true,
                loading = t.key in loadingKeys,
                onToggle = { toggleNotify(t.key, it) },
            )
        }

        item { SectionHeader("Сообщения") }
        item {
            ToggleRow(
                title = "Звук уведомлений о сообщениях",
                subtitle = "Воспроизводить звук при получении новых сообщений",
                checked = s.msgMute,
            ) { scope.launch { app.prefs.setMsgMute(it) } }
        }
        // Fix #302 (Task 2-b): 5 per-category sn_* тогглов (BFF-backed).
        items(NOTIFY_MSG_TOGGLES, key = { it.key }) { t ->
            ToggleRowWithLoading(
                title = t.title,
                checked = notifyStates[t.key] ?: NOTIFY_DEFAULTS[t.key] ?: true,
                loading = t.key in loadingKeys,
                onToggle = { toggleNotify(t.key, it) },
            )
        }

        // Fix #302 (Task 2-b): 5 новых секций с sn_* тогглами (всего ~18 строк).
        notifyToggleSection(
            sectionTitle = "Сообщества",
            toggles = NOTIFY_GROUPS_TOGGLES,
            states = notifyStates,
            loadingKeys = loadingKeys,
        ) { k, v -> toggleNotify(k, v) }
        notifyToggleSection(
            sectionTitle = "Друзья",
            toggles = NOTIFY_FRIEND_TOGGLES,
            states = notifyStates,
            loadingKeys = loadingKeys,
        ) { k, v -> toggleNotify(k, v) }
        notifyToggleSection(
            sectionTitle = "Реакции и комментарии",
            toggles = NOTIFY_REACTION_TOGGLES,
            states = notifyStates,
            loadingKeys = loadingKeys,
        ) { k, v -> toggleNotify(k, v) }
        notifyToggleSection(
            sectionTitle = "Контент",
            toggles = NOTIFY_CONTENT_TOGGLES,
            states = notifyStates,
            loadingKeys = loadingKeys,
        ) { k, v -> toggleNotify(k, v) }
        notifyToggleSection(
            sectionTitle = "Прочее",
            toggles = NOTIFY_OTHER_TOGGLES,
            states = notifyStates,
            loadingKeys = loadingKeys,
        ) { k, v -> toggleNotify(k, v) }

        // §1-NOTIF-ARCHIVE: 3 новые секции из архива m.vk.ru/settings?act=notify
        // (раньше в приложении были только 5 базовых групп; архив раскрыл ещё 27
        // категорий, которые ВК предлагает настроить, но которых не было в UI).
        notifyToggleSection(
            sectionTitle = "Обратная связь",
            toggles = NOTIFY_FEEDBACK_TOGGLES,
            states = notifyStates,
            loadingKeys = loadingKeys,
        ) { k, v -> toggleNotify(k, v) }
        notifyToggleSection(
            sectionTitle = "События",
            toggles = NOTIFY_EVENTS_TOGGLES,
            states = notifyStates,
            loadingKeys = loadingKeys,
        ) { k, v -> toggleNotify(k, v) }
        notifyToggleSection(
            sectionTitle = "Дополнительно",
            toggles = NOTIFY_EXTRA_TOGGLES,
            states = notifyStates,
            loadingKeys = loadingKeys,
        ) { k, v -> toggleNotify(k, v) }

        // §1-NOTIF-ARCHIVE: Email-уведомления — частота и категории из архива.
        item { SectionHeader("Уведомления на почту") }
        item {
            EmailNotifyCard(
                emailFreq = s.emailNotifyFreq,
                onFreqChange = { freq -> scope.launch { app.prefs.setEmailNotifyFreq(freq) } },
            )
        }

        // ────────────────────────────────────────────────────────────────────
        // §42 #PUSH-NOTIFICATIONS: локальные push-уведомления PinoK.
        // ────────────────────────────────────────────────────────────────────
        // В отличие от sn_* toggles (server-side, управляют что VK шлёт через FCM),
        // эти — client-side: управляет показывает ли PinoK system notification
        // когда NotificationsPoller находит новые элементы в notifications.getRedesign.
        //
        // Архитектура: LongPoll code 114 → triggerImmediatePoll → getRedesign →
        // diff → system notification (per-category channel). Тап → deep-link →
        // Screen (PostDetail / VideoPlayer / UserProfile / Community / InternalBrowser).
        item { SectionHeader("Push-уведомления (лайки, комментарии, ответы)") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "PinoK показывает системные уведомления для лайков, " +
                            "комментариев, репостов, ответов, подписок, упоминаний, " +
                            "подарков и записей на стене. Тап по уведомлению " +
                            "открывает место события (пост, видео, профиль).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Канал уведомлений: «Push-уведомления ВК» в системных " +
                            "настройках Android. LongPoll code 114 даёт near-real-time " +
                            "доставку без FCM. Fallback-интервал опроса ниже.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            ToggleRow(
                title = "Включить push-уведомления",
                subtitle = "Глобальный переключатель. Отключает все push-уведомления PinoK.",
                checked = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushEnabled(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Лайки",
                subtitle = "Лайки ваших постов, комментариев, фото и видео",
                checked = s.pushLikes,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushLikes(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Комментарии",
                subtitle = "Новые комментарии к вашим записям",
                checked = s.pushComments,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushComments(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Ответы",
                subtitle = "Ответы на ваши комментарии",
                checked = s.pushReplies,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushReplies(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Новые подписчики",
                subtitle = "Новые подписчики и принятые заявки в друзья",
                checked = s.pushFollows,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushFollows(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Упоминания",
                subtitle = "Упоминания вас в постах и комментариях",
                checked = s.pushMentions,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushMentions(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Репосты",
                subtitle = "Кто поделился вашими записями",
                checked = s.pushReposts,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushReposts(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Записи на стене",
                subtitle = "Новые записи на вашей стене",
                checked = s.pushWall,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushWall(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Подарки",
                subtitle = "Полученные подарки",
                checked = s.pushGifts,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushGifts(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Прочее",
                subtitle = "Приглашения в группы, приложения и т.д.",
                checked = s.pushOther,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushOther(v) } },
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Интервал опроса (fallback): ${s.pushPollingIntervalSec}с",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (s.pushEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Если LongPoll недоступен или code 114 не пришёл, PinoK " +
                            "опрашивает notifications.getRedesign каждые N секунд. " +
                            "Меньше = быстрее, но больше расход батареи.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(60, 120, 180, 300).forEach { sec ->
                            androidx.compose.material3.FilterChip(
                                selected = s.pushPollingIntervalSec == sec,
                                onClick = { scope.launch { app.prefs.setPushPollingIntervalSec(sec) } },
                                label = { Text("${sec}с") },
                                enabled = s.pushEnabled,
                            )
                        }
                    }
                }
            }
        }

        // ── §42.3 #PUSH-SOURCE-FILTER: фильтр по источнику ─────────────
        item { SectionHeader("Источник уведомлений (от кого)") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Фильтр по источнику: показывать уведомления только от " +
                            "сообществ или только от пользователей.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            ToggleRow(
                title = "От сообществ",
                subtitle = "Уведомления где владелец контента — сообщество (посты групп, фото групп, ...)",
                checked = s.pushFromCommunities,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushFromCommunities(v) } },
            )
        }
        item {
            ToggleRow(
                title = "От пользователей (друзей)",
                subtitle = "Уведомления где владелец контента — пользователь (посты на стене, фото, ...)",
                checked = s.pushFromUsers,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushFromUsers(v) } },
            )
        }
        item {
            // §49.5.1 #SAFETY-NET-ALERTS (2026-08-04): уведомления о
            // подозрительных входах (новое устройство/город/IP).
            // Poller каждые 10 мин опрашивает accountPersonal.getSecurityAlerts.
            // Heads-up notification (channel vk_security_alerts, IMPORTANCE_HIGH).
            ToggleRow(
                title = "Оповещения о входе",
                subtitle = "Уведомлять о подозрительных входах в аккаунт (новое устройство, " +
                    "город, IP). Проверка каждые ${s.safetyNetPollIntervalMin} мин. " +
                    "Канал: «Безопасность аккаунта» (высокий приоритет, звук).",
                checked = s.pushSafetyNetAlerts,
                enabled = s.pushEnabled,
                onToggle = { v ->
                    scope.launch {
                        app.prefs.setPushSafetyNetAlerts(v)
                        // Триггерим немедленный poll при включении.
                        if (v) app.securityAlertsPoller?.triggerImmediatePoll()
                    }
                },
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Подробные фильтры по типу события (лайки, комментарии, " +
                            "репосты, ответы, упоминания, подарки, посты на стене, " +
                            "новые подписчики) — в секции «Push-уведомления» выше " +
                            "(pushLikes, pushComments, ...).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Дополнительно: настройки sn_* ниже (sn_likes, sn_reposts, " +
                            "sn_wall_posts, sn_groups, ...) теперь применяются " +
                            "client-side — они фильтруют уведомления PinoK " +
                            "напрямую, а не только FCM-push VK.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                }
            }
        }

        // ── §42.2 #PUSH-ENHANCED: группировка ──────────────────────────
        item { SectionHeader("Группировка уведомлений") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Режим группировки: ${groupingLabel(s.pushGroupingMode)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (s.pushEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "«Выкл» (рекомендуется) — каждое уведомление отдельной " +
                            "карточкой, можно напрямую тапнуть на конкретный пост/фото/видео.\n" +
                            "Группировка сворачивает N уведомлений в стопку «N новых».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // §42.6 #PUSH-GROUP-EXPAND-HINT: пошаговая инструкция жеста
                    // pinch-out (разведение двумя пальцами). Жест не очевидный —
                    // без подсказки юзер разворачивает группу и видит «один пост»
                    // (InboxStyle-сводку) вместо списка отдельных тапаемых карточек.
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Как развернуть группу (pinch-out)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Когда уведомления свернуты в стопку «N новых», " +
                                    "развернуть её в список отдельных карточек можно " +
                                    "жестом pinch-out (разведение двумя пальцами):\n\n" +
                                    "1. Откройте шторку уведомлений Android (свайп вниз сверху).\n" +
                                    "2. Найдите свернутую стопку «N новых».\n" +
                                    "3. Положите ДВА пальца одновременно на карточку стопки.\n" +
                                    "4. Разведите пальцы в стороны — как при Zoom (увеличении).\n" +
                                    "5. Стопка развернётся в список отдельных уведомлений.\n" +
                                    "6. Тапните по нужному — откроется конкретный пост, " +
                                    "фото или видео по прямой ссылке.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Не получается развернуть? Некоторые оболочки " +
                                    "(MIUI, One UI, EMUI, старые Android) не поддерживают " +
                                    "жест pinch-out для уведомлений. Тогда выберите «Выкл» " +
                                    "— каждое уведомление будет отдельной карточкой, " +
                                    "и разворачивать ничего не нужно.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "none" to "Выкл",
                            "category" to "По типу",
                            "community" to "По сообществу",
                            "user" to "По человеку",
                        ).forEach { (mode, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = s.pushGroupingMode == mode,
                                onClick = { scope.launch { app.prefs.setPushGroupingMode(mode) } },
                                label = { Text(label) },
                                enabled = s.pushEnabled,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Порог сворачивания: ${s.pushGroupThreshold}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.pushEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "При N и более уведомлений в группе — показывается " +
                            "сворачиваемый summary. Меньше = агрессивнее сворачивание.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1, 2, 3, 5, 10).forEach { thr ->
                            androidx.compose.material3.FilterChip(
                                selected = s.pushGroupThreshold == thr,
                                onClick = { scope.launch { app.prefs.setPushGroupThreshold(thr) } },
                                label = { Text("$thr") },
                                enabled = s.pushEnabled,
                            )
                        }
                    }
                }
            }
        }

        // ── §42.2 #PUSH-ENHANCED: приватность превью ───────────────────
        item { SectionHeader("Приватность и превью") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Содержимое уведомления: ${previewModeLabel(s.pushPreviewMode)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (s.pushEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "«Полное» — отправитель + текст. «Только отправитель» — " +
                            "имя без текста (приватность). «Скрыто» — только " +
                            "«Новое уведомление».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "full" to "Полное",
                            "sender_only" to "Отправитель",
                            "hidden" to "Скрыто",
                        ).forEach { (mode, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = s.pushPreviewMode == mode,
                                onClick = { scope.launch { app.prefs.setPushPreviewMode(mode) } },
                                label = { Text(label) },
                                enabled = s.pushEnabled,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Длина превью текста: ${if (s.pushPreviewLength == 0) "выкл" else "${s.pushPreviewLength} симв."}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (s.pushEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0, 40, 80, 160).forEach { len ->
                            androidx.compose.material3.FilterChip(
                                selected = s.pushPreviewLength == len,
                                onClick = { scope.launch { app.prefs.setPushPreviewLength(len) } },
                                label = { Text(if (len == 0) "выкл" else "$len") },
                                enabled = s.pushEnabled,
                            )
                        }
                    }
                }
            }
        }
        item {
            ToggleRow(
                title = "Показывать аватар отправителя",
                subtitle = "Large icon с фото профиля (загружается из VK)",
                checked = s.pushShowAvatar,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushShowAvatar(v) } },
            )
        }
        item {
            ToggleRow(
                title = "BigPicture для фото",
                subtitle = "Превью фото в уведомлении (like_photo, comment_photo)",
                checked = s.pushShowBigPicture,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushShowBigPicture(v) } },
            )
        }

        // ── §42.2 #PUSH-ENHANCED: отображение ──────────────────────────
        item { SectionHeader("Отображение и звук") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Авто-скрытие: ${autoDismissLabel(s.pushAutoDismissMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (s.pushEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Уведомление исчезает из шторки через N. «Никогда» — " +
                            "висит до ручного смахивания.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0L, 10000L, 30000L, 60000L, 1800000L).forEach { ms ->
                            androidx.compose.material3.FilterChip(
                                selected = s.pushAutoDismissMs == ms,
                                onClick = { scope.launch { app.prefs.setPushAutoDismissMs(ms) } },
                                label = { Text(autoDismissLabel(ms)) },
                                enabled = s.pushEnabled,
                            )
                        }
                    }
                }
            }
        }
        item {
            ToggleRow(
                title = "Звук уведомления",
                subtitle = "Если выключено — silent channel (без звука и heads-up)",
                checked = s.pushSoundEnabled,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushSoundEnabled(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Вибрация",
                subtitle = "Вибрация при показе уведомления",
                checked = s.pushVibrationEnabled,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushVibrationEnabled(v) } },
            )
        }
        item {
            ToggleRow(
                title = "Кнопка «Прочитать»",
                subtitle = "Кнопка в уведомлении: помечает прочитанным на сервере и убирает из шторки",
                checked = s.pushActionButtons,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushActionButtons(v) } },
            )
        }
        // §46 #REMOTE-INPUT: кнопка «Ответить» с прямым вводом из шторки.
        item {
            ToggleRow(
                title = "Кнопка «Ответить» (из шторки)",
                subtitle = "Прямой ответ на комментарий/пост из уведомления — без открытия приложения. " +
                    "Появляется для комментариев, ответов, упоминаний и постов на стене",
                checked = s.pushReplyButton,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushReplyButton(v) } },
            )
        }

        // ── §42.2 #PUSH-ENHANCED: режим «не беспокоить» ────────────────
        item { SectionHeader("Не беспокоить (quiet hours)") }
        item {
            ToggleRow(
                title = "Включить quiet hours",
                subtitle = "Не показывать push-уведомления в заданное окно времени",
                checked = s.pushQuietHoursEnabled,
                enabled = s.pushEnabled,
                onToggle = { v -> scope.launch { app.prefs.setPushQuietHoursEnabled(v) } },
            )
        }
        // #SETTINGS-FIX: задержка показа push-уведомлений (была только в Snapshot).
        item { SectionHeader("Задержка показа") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Задержка: ${delayMsLabel(s.pushShowDelayMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (s.pushEnabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Уведомление показывается через N после получения. " +
                            "0 = сразу. Полезно при высокой частоте уведомлений.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0L, 500L, 1000L, 3000L, 5000L).forEach { ms ->
                            androidx.compose.material3.FilterChip(
                                selected = s.pushShowDelayMs == ms,
                                onClick = { scope.launch { app.prefs.setPushShowDelayMs(ms) } },
                                label = { Text(delayMsLabel(ms)) },
                                enabled = s.pushEnabled,
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Окно тишины: ${minutesToTime(s.pushQuietHoursStart)} – ${minutesToTime(s.pushQuietHoursEnd)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (s.pushEnabled && s.pushQuietHoursEnabled)
                                    MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Поддерживает переход через полночь (например 22:00→08:00). " +
                            "Выберите начало и конец окна.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Начало:", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        // Пресеты по 2 часа + полуночь.
                        listOf(0, 480, 600, 720, 840, 960, 1080, 1200, 1320, 1380).forEach { min ->
                            androidx.compose.material3.FilterChip(
                                selected = s.pushQuietHoursStart == min,
                                onClick = { scope.launch { app.prefs.setPushQuietHoursStart(min) } },
                                label = { Text(minutesToTime(min)) },
                                enabled = s.pushEnabled && s.pushQuietHoursEnabled,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Конец:", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(0, 360, 420, 480, 600, 720, 840, 960, 1080, 1200).forEach { min ->
                            androidx.compose.material3.FilterChip(
                                selected = s.pushQuietHoursEnd == min,
                                onClick = { scope.launch { app.prefs.setPushQuietHoursEnd(min) } },
                                label = { Text(minutesToTime(min)) },
                                enabled = s.pushEnabled && s.pushQuietHoursEnabled,
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader("Полные настройки уведомлений") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
                    .androidx_clickable { onOpenNotificationSettings() },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Открыть настройки уведомлений",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        )
                        Text(
                            "Звуки, сообщества, приложения, заблокированные, фильтр мата",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Вспомогательный Modifier-мост для clickable без отдельного import-блока. */
private fun Modifier.androidx_clickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/**
 * §1-NOTIF-ARCHIVE: Карточка «Уведомления на почту».
 * Из архива m.vk.ru/settings?act=notify — секция «Уведомления по электронной почте»:
 *   - Частота: Всегда / Не чаще одного раза в день / Никогда
 *   - 18 категорий (checkboxes)
 *
 * ВК web использует для этого BFF settingsGeneral.setNotifySettings с email_* ключами
 * либо account.setPushSettings. Здесь — локальная настройка частоты (persisted in
 * SovaPrefs); категории пока показываются информационно, т.к. требуют отдельного
 * BFF-эндпоинта для email-настроек (TODO: исследовать settingsGeneral page="email").
 */
@Composable
private fun EmailNotifyCard(
    emailFreq: Int,
    onFreqChange: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Частота уведомлений",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Как часто ВКонтакте присылает уведомления на почту",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val freqOptions = listOf(
                0 to "Всегда",
                1 to "Не чаще одного раза в день",
                2 to "Никогда",
            )
            freqOptions.forEach { (value, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .androidx_clickable { onFreqChange(value) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = emailFreq == value,
                        onClick = { onFreqChange(value) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Категории: события, заявки в друзья, приглашения в сообщества, " +
                    "грядущие мероприятия, дни рождения, отметки на фото, интересные публикации, " +
                    "ответы и комментарии, упоминания, личные сообщения, подарки и др. " +
                    "(настройка категорий будет доступна после подключения email-BFF)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
// §42.2 #PUSH-ENHANCED: helper-функции для лейблов настроек push.
// ──────────────────────────────────────────────────────────────────────

/** Человекочитаемый лейбл для режима группировки. */
private fun groupingLabel(mode: String): String = when (mode) {
    "none" -> "выключена"
    "category" -> "по типу (лайки/комментарии/...)"
    "community" -> "по сообществу"
    "user" -> "по человеку"
    else -> mode
}

/** Человекочитаемый лейбл для режима превью. */
private fun previewModeLabel(mode: String): String = when (mode) {
    "full" -> "полное (отправитель + текст)"
    "sender_only" -> "только отправитель"
    "hidden" -> "скрыто"
    else -> mode
}

/** Человекочитаемый лейбл для авто-скрытия. */
private fun autoDismissLabel(ms: Long): String = when (ms) {
    0L -> "никогда"
    10000L -> "10 сек"
    30000L -> "30 сек"
    60000L -> "1 мин"
    1800000L -> "30 мин"
    else -> "${ms / 1000} сек"
}

/** Конвертация минут от полуночи в HH:MM. */
private fun minutesToTime(min: Int): String {
    val h = (min / 60) % 24
    val m = min % 60
    return "%02d:%02d".format(h, m)
}

/** Человекочитаемый лейбл для задержки показа. */
private fun delayMsLabel(ms: Long): String = when (ms) {
    0L -> "сразу"
    500L -> "0.5 сек"
    1000L -> "1 сек"
    3000L -> "3 сек"
    5000L -> "5 сек"
    else -> "${ms / 1000} сек"
}

// #SETTINGS-FIX: диалог создания PIN-кода при первом включении блокировки.
// Вызывается из SecurityTab когда lockerEnabled=true, а lockerPinHash пуст.
@Composable
private fun PinSetupDialog(
    onDismiss: () -> Unit,
    onPinSet: (hash: String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) } // 0=enter, 1=confirm
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == 0) "Создайте PIN-код" else "Подтвердите PIN-код") },
        text = {
            Column {
                if (step == 0) {
                    Text("Введите 4 цифры для блокировки приложения")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { v ->
                            if (v.length <= 4 && v.all { it.isDigit() }) {
                                pin = v
                                error = null
                                if (v.length == 4) step = 1
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    )
                } else {
                    Text("Повторите PIN-код")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { v ->
                            if (v.length <= 4 && v.all { it.isDigit() }) {
                                confirm = v
                                error = null
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    )
                }
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (step == 1) {
                TextButton(
                    onClick = {
                        if (confirm == pin && confirm.length == 4) {
                            onPinSet(re.pinok.locker.LockerActivity.hashPin(pin))
                        } else {
                            error = "PIN-коды не совпадают"
                            confirm = ""
                            step = 0
                            pin = ""
                        }
                    },
                ) { Text("Сохранить") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

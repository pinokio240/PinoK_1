package re.pinok.ui.screens.calls

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog

/**
 * #CALLS-SNAP (2026-09-06, РЕВИЗИЯ-2 по REV-DEEP-2): модалка «Запланировать /
 * Редактировать» (ModalScheduleCall, calls_schedule_call_modal_root) —
 * ПОЛНЫЙ wire. Создание и правка — одна модалка:
 *  - создание (editItem=null) → calls.start (bridge@777039 `(0,y.callsStart)(PO(e))`
 *    без call-аргумента; прежняя гипотеза «messages.editCall с call_id="0"`
 *    ОПРОВЕРГНУТА реверсом — call_id:"0" в коде web нигде не встречается);
 *  - правка/перенос (editItem=сырой айтем messages.getScheduledCalls) →
 *    messages.editCall (PO@726449: wO + call_id + marker_time; перенос Uk
 *    @899680 шлёт тот же payload — один пункт «Редактировать/перенести»).
 *
 * Wire-объект wO (bridge@725601): {mute_audio, mute_video, name,
 * only_auth_users, duration (СЕКУНДЫ), time (unix СЕКУНДЫ, пересчёт КАЛЕНДАРЁМ
 * в выбранной TZ из локальных полей даты/времени), recurrence_rule,
 * waiting_hall, feedback, only_admin_can_share_movie, only_admin_can_record,
 * mute_screen_sharing} + условные [group_id], [skip_notification],
 * [show_chat_history], [recurrence_until_time]. Calendar/displayName —
 * КЛИЕНТСКИЕ поля web, в API не уходят.
 *
 * РЕНДЕРЯТСЯ ВСЕ 18 КОНТРОЛОВ реверса (кроме №9, см. отклонения):
 *  1 «От имени» (я + messagesGetGroupsForCallForSchedule, value=-id;
 *    виден при опциях>1 или правке; group_id уходит отрицательным —
 *    wire owner-семантика, id чата не арифметика),
 *  2 название (лимит 100, calls_validation_length; пустое → «Звонок»),
 *  3 начало (нативные date+time пикеры, wall-clock в выбранной TZ),
 *  4 длительность-чипы 8 значений (30minutes…1day = 1800000…86400000 ms,
 *    дефолт создания 30minutes, dO@720384),
 *  5 окончание read-only (start+duration; disabled при 1day),
 *  6 часовой пояс (полный java.util.TimeZone.getAvailableIDs; unix
 *    пересчитывается календарём в выбранной TZ),
 *  7 повтор (never|daily|weekly|weekdays|weekend|monthly|yearly — wire-енум
 *    модуля 912069; same_week_day UI → weekdays/weekend, RO@725500),
 *  8 конец повтора (виден при rrule!=never; date picker; дефолт 23:59:59 дня
 *    старта),
 *  10 напоминание switch (дефолт ВЫКЛ → skip_notification=1, web-дефолт),
 *  11 зал ожидания switch (дефолт off),
 *  12 анонимная ссылка switch (дефолт ON → only_auth_users=0),
 *  13 история чата switch (ТОЛЬКО при тоггле enableChatHistoryToggle
 *    calls.getSettings; скрыт при «от имени группы»; иначе omit),
 *  14 микрофоны select (unmute|mute|mute_permanent — uO@720715),
 *  15 камеры select (те же 3 MuteState),
 *  16 демонстрация экрана switch ("unmute"/"mute_permanent" — при создании
 *    промежуточного "mute" не существует),
 *  17 реакции switch (дефолт ON → feedback=1),
 *  18 совместный просмотр switch (дефолт ON → only_admin_can_share_movie=0).
 *
 * ПОСЛЕ УСПЕШНОГО СОЗДАНИЯ: ответ calls.start {call_id, join_link,
 * short_credentials{...}} (FM@773057) → пост-модалка «Звонок запланирован»:
 * join_link (авто-копия в буфер — подпись calls_will_copy_to_clipboard),
 * кнопки «Скопировать ссылку» / «Готово». Затем repo.refresh(SCHEDULED).
 *
 * ЧЕСТНЫЕ ОТКЛОНЕНИЯ:
 *  - №9 «Добавить в календарь» НЕ рендерится: это клиентская .ics-фича web
 *    (Kb.Kf MailRu|iCloud|Google|Outlook|Other, генерация ссылки .ics/
 *    Google-календаря на клиенте) — в API НЕ уходит (в wO/PO поля calendar
 *    нет); имитировать выбор без отправки нечестно.
 *  - only_admin_can_record всегда false (wire 0): web шлёт 1/0 по флагу
 *    isSferum (на vk.ru — 0); Sferum-ветка в проекте не реализована.
 *  - Дефолт-название: рус. значение lang-ключа calls_default_meeting_name
 *    в снапшотах не сохранилось — использована строка «Звонок».
 *  - Каталог таймзон web (модуль 952586, {id,title,subtitle,offset}) не
 *    вырезан — используется полный java.util.TimeZone.getAvailableIDs().
 *  - Пикеры — нативные системные диалоги (date → time) вместо web-композитных;
 *    при предзаполнении правки wall-clock берётся в TZ устройства (поведение
 *    web при правке из снапшотов не восстановлено).
 *  - displayName (текст приглашения) — клиентское поле, не эмулируется.
 *
 * #NULL-EXPLICIT: в новом коде ноль safe-call/elvis/non-null assertion —
 * явные if с захватом в локальный val.
 */

/** MuteState-строки wire (bridge@720715 uO: Allowed/DisabledOnEnter/Disabled). */
private const val MUTE_ALLOWED = "unmute"
private const val MUTE_ON_ENTER = "mute"
private const val MUTE_DISABLED = "mute_permanent"

/** Дефолт названия (рус. значение calls_default_meeting_name неизвестно — см. KDoc). */
private const val DEFAULT_CALL_NAME = "Звонок"

/** Граничное значение «unix-миллисекунды» (защита парсинга schedule.time). */
private const val MS_EPOCH_THRESHOLD = 100_000_000_000L

/** Пара «wire-значение — рус. подпись» для select-контролов модалки. */
private class WireOption(val wire: String, val label: String)

/** Чип длительности: wire-ключ, подпись, миллисекунды (dO@720384). */
private class DurationChip(val key: String, val label: String, val ms: Long)

private val DURATION_CHIPS: List<DurationChip> = listOf(
    DurationChip("30minutes", "30 минут", 1_800_000L),
    DurationChip("45minutes", "45 минут", 2_700_000L),
    DurationChip("1hour", "1 час", 3_600_000L),
    DurationChip("2hours", "2 часа", 7_200_000L),
    DurationChip("3hours", "3 часа", 10_800_000L),
    DurationChip("4hours", "4 часа", 14_400_000L),
    DurationChip("5hours", "5 часов", 18_000_000L),
    DurationChip("1day", "1 день", 86_400_000L),
)

/** Повтор — 7 wire-значений (модуль 912069); рус. подписи наши (ланг-ключи не вырезаны). */
private val REPEAT_OPTIONS: List<WireOption> = listOf(
    WireOption("never", "Никогда"),
    WireOption("daily", "Каждый день"),
    WireOption("weekly", "Каждую неделю"),
    WireOption("weekdays", "По будням"),
    WireOption("weekend", "По выходным"),
    WireOption("monthly", "Каждый месяц"),
    WireOption("yearly", "Каждый год"),
)

/** Микрофоны/камеры — 3 MuteState (uO@720715). */
private val MUTE_OPTIONS: List<WireOption> = listOf(
    WireOption(MUTE_ALLOWED, "Разрешены"),
    WireOption(MUTE_ON_ENTER, "Без звука при входе"),
    WireOption(MUTE_DISABLED, "Без звука всегда"),
)

/** Локальные поля даты/времени (wall-clock) — первичны; unix выводится календарём в TZ. */
private class WallDateTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int)

/** Опция «От имени»: 0 = я, отрицательное = группа (value=-id, bridge@776476). */
private class IdentityOption(val value: Long, val label: String)

/** Предзаполнение модалки из сырого айтема messages.getScheduledCalls (правка). */
private class SchedulePrefill(
    val callId: String,
    val name: String,
    val startWall: WallDateTime,
    val durationKey: String,
    val recurrenceRule: String,
    val untilWall: WallDateTime?,
    val markerTime: Long,
    val groupId: Long,
    val remindOn: Boolean,
    val waitingHallOn: Boolean,
    val anonOn: Boolean,
    val chatHistoryOn: Boolean,
    val micWire: String,
    val camWire: String,
    val screenShareOn: Boolean,
    val reactionsOn: Boolean,
    val movieOn: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScheduleCallDialog(
    editItem: JsonObject?,
    onDismiss: () -> Unit,
) {
    val deps = LocalCallsDeps.current
    val repo = LocalCallsSectionRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val prefill = remember(editItem) { parseSchedulePrefill(editItem) }
    val isEdit = editItem != null

    var nameText by remember { mutableStateOf(prefill.name) }
    var tzId by remember { mutableStateOf(TimeZone.getDefault().id) }
    var startWall by remember { mutableStateOf(prefill.startWall) }
    var durationKey by remember { mutableStateOf(prefill.durationKey) }
    var repeatWire by remember { mutableStateOf(prefill.recurrenceRule) }
    var untilWall by remember { mutableStateOf(prefill.untilWall) }
    var remindOn by remember { mutableStateOf(prefill.remindOn) }
    var waitingHallOn by remember { mutableStateOf(prefill.waitingHallOn) }
    var anonOn by remember { mutableStateOf(prefill.anonOn) }
    var chatHistoryOn by remember { mutableStateOf(prefill.chatHistoryOn) }
    var micWire by remember { mutableStateOf(prefill.micWire) }
    var camWire by remember { mutableStateOf(prefill.camWire) }
    var screenShareOn by remember { mutableStateOf(prefill.screenShareOn) }
    var reactionsOn by remember { mutableStateOf(prefill.reactionsOn) }
    var movieOn by remember { mutableStateOf(prefill.movieOn) }
    var identity by remember { mutableStateOf(prefill.groupId) }

    var saving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showUntilPicker by remember { mutableStateOf(false) }
    var chatToggleOn by remember { mutableStateOf(false) }
    var groupOptions by remember { mutableStateOf<List<IdentityOption>>(emptyList()) }
    var createdJoinLink by remember { mutableStateOf("") }

    // Тоггл «История чата» (REV-DEEP-2: только enableChatHistoryToggle из
    // calls.getSettings → toggles[{name,enabled}]) и опции «От имени»
    // (messagesGetGroupsForCallForSchedule). Tolerant-парсинг: любой сбой —
    // контрол честно скрыт/опции только «От себя».
    LaunchedEffect(Unit) {
        val settings = try {
            deps.apiClient.callsGetSettings()
        } catch (e: Exception) {
            AppLog.w("CallsSchedule", "callsGetSettings failed — история чата скрыта", e)
            null
        }
        if (settings != null) {
            chatToggleOn = parseChatHistoryToggle(settings)
        }
        val groups = try {
            deps.apiClient.messagesGetGroupsForCallForSchedule()
        } catch (e: Exception) {
            AppLog.w("CallsSchedule", "messagesGetGroupsForCallForSchedule failed — «От имени» только «От себя»", e)
            emptyList<JsonObject>()
        }
        val opts = ArrayList<IdentityOption>()
        opts.add(IdentityOption(0L, "От себя"))
        for (g in groups) {
            val gid = jsonLong(g, "id")
            if (gid > 0L) {
                var label = jsonStr(g, "name")
                if (label.isBlank()) label = "Группа " + gid
                opts.add(IdentityOption(-gid, label))
            }
        }
        groupOptions = opts
    }

    val tz = TimeZone.getTimeZone(tzId)
    val startUnix = secFromWall(startWall, tz)
    val durationMs = durationMsOf(durationKey)
    val endUnix = startUnix + durationMs / 1000L

    // Пост-модалка создания (SHARE_PLANNED_CALL, FM@773057) — вместо главной
    // формы (ранний возврат после всех remember — порядок композиции стабилен).
    val createdLink = createdJoinLink
    if (createdLink.isNotBlank()) {
        ScheduledCreatedDialog(
            joinLink = createdLink,
            onCopy = {
                clipboard.setText(AnnotatedString(createdLink))
                Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
            },
            onDone = onDismiss,
        )
        return
    }

    val canSubmit = !saving && (!isEdit || prefill.callId.isNotBlank())

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.testTag("calls_schedule_call_modal_root"),
        title = { Text(if (isEdit) "Редактировать звонок" else "Запланировать звонок") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // №1 «От имени» — виден при опциях>1 или правке (web: user_select).
                val showIdentitySelect = groupOptions.size > 1 || isEdit
                if (showIdentitySelect) {
                    var identityLabel = "От себя"
                    for (opt in groupOptions) {
                        if (opt.value == identity) identityLabel = opt.label
                    }
                    val identityOpts = ArrayList<WireOption>()
                    for (opt in groupOptions) {
                        identityOpts.add(WireOption(opt.value.toString(), opt.label))
                    }
                    ScheduleSelectField(
                        label = "От имени",
                        value = identityLabel,
                        options = identityOpts,
                        enabled = !saving,
                        tag = "calls_schedule_call_modal_user_select",
                        onSelect = { raw ->
                            val parsed = raw.toLongOrNull()
                            if (parsed != null) identity = parsed
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (isEdit && prefill.callId.isBlank()) {
                    Text(
                        "Айтем без call_id — правка невозможна (честный отказ)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // №2 Название (лимит 100, calls_validation_length).
                TextField(
                    value = nameText,
                    onValueChange = { if (it.length <= 100) nameText = it },
                    placeholder = { Text(DEFAULT_CALL_NAME) },
                    singleLine = true,
                    enabled = !saving,
                    label = { Text("Название") },
                    supportingText = { Text(nameText.length.toString() + "/100") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calls_schedule_call_modal_title_input"),
                )
                Spacer(Modifier.height(8.dp))

                // №3 Начало: wall-clock поля + нативные date+time пикеры.
                TextField(
                    value = formatWall(startWall, tz),
                    onValueChange = {},
                    readOnly = true,
                    enabled = !saving,
                    label = { Text("Начало") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calls_schedule_call_modal_start_time_picker"),
                )
                Text(
                    "Нажмите, чтобы выбрать дату и время начала",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !saving) { showStartPicker = true }
                        .testTag("calls_schedule_call_modal_pick_start"),
                )
                Spacer(Modifier.height(8.dp))

                // №4 Длительность-чипы (8 значений, dO@720384).
                Text("Длительность", style = MaterialTheme.typography.bodySmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    for (chip in DURATION_CHIPS) {
                        FilterChip(
                            selected = durationKey == chip.key,
                            onClick = { durationKey = chip.key },
                            enabled = !saving,
                            label = { Text(chip.label) },
                            modifier = Modifier.testTag("calls_schedule_call_modal_duration_" + chip.key),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // №5 Окончание = start+duration, read-only; disabled при 1day (web isAllDay).
                TextField(
                    value = formatSec(endUnix, tz),
                    onValueChange = {},
                    readOnly = true,
                    enabled = !saving && durationKey != "1day",
                    label = {
                        Text(
                            if (durationKey == "1day") {
                                "Окончание (весь день — не настраивается)"
                            } else {
                                "Окончание"
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calls_schedule_call_modal_finish_time_picker"),
                )
                Spacer(Modifier.height(8.dp))

                // №6 Часовой пояс: полный каталог ОС; дефолт — TZ устройства.
                ScheduleTimeZoneField(
                    tzId = tzId,
                    enabled = !saving,
                    onPick = { tzId = it },
                )
                Spacer(Modifier.height(8.dp))

                // №7 Повтор (wire-енум).
                ScheduleSelectField(
                    label = "Повтор",
                    value = wireLabel(REPEAT_OPTIONS, repeatWire),
                    options = REPEAT_OPTIONS,
                    enabled = !saving,
                    tag = "calls_schedule_call_modal_repeat_select",
                    onSelect = { repeatWire = it },
                )
                Spacer(Modifier.height(8.dp))

                // №8 Конец повтора — виден при rrule != never.
                if (repeatWire != "never") {
                    val until = untilWall
                    val untilDisplay = if (until != null) {
                        formatWall(until, tz)
                    } else {
                        "23:59:59 дня старта (не выбрано)"
                    }
                    TextField(
                        value = untilDisplay,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !saving,
                        label = { Text("Конец повтора") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calls_schedule_call_modal_end_repeat_select"),
                    )
                    Text(
                        "Нажмите, чтобы выбрать дату конца повтора",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !saving) { showUntilPicker = true }
                            .testTag("calls_schedule_call_modal_pick_end_repeat"),
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // №10 Напоминание (дефолт ВЫКЛ → skip_notification=1).
                ScheduleSwitchRow(
                    label = "Напоминание",
                    checked = remindOn,
                    enabled = !saving,
                    tag = "calls_schedule_call_modal_remind_switch",
                    onChange = { remindOn = it },
                )
                // №11 Зал ожидания.
                ScheduleSwitchRow(
                    label = "Зал ожидания",
                    checked = waitingHallOn,
                    enabled = !saving,
                    tag = "calls_schedule_call_modal_waiting_hall_switch",
                    onChange = { waitingHallOn = it },
                )
                // №12 Анонимная ссылка (ON → only_auth_users=0).
                ScheduleSwitchRow(
                    label = "Анонимная ссылка",
                    checked = anonOn,
                    enabled = !saving,
                    tag = "calls_schedule_call_modal_anon_switch",
                    onChange = { anonOn = it },
                )
                // №13 История чата — только по тогглу settings и «от себя».
                if (chatToggleOn && identity == 0L) {
                    ScheduleSwitchRow(
                        label = "История чата",
                        checked = chatHistoryOn,
                        enabled = !saving,
                        tag = "calls_schedule_call_modal_chat_history_switch",
                        onChange = { chatHistoryOn = it },
                    )
                }

                // №14/№15 Микрофоны и камеры.
                ScheduleSelectField(
                    label = "Микрофоны",
                    value = wireLabel(MUTE_OPTIONS, micWire),
                    options = MUTE_OPTIONS,
                    enabled = !saving,
                    tag = "calls_schedule_call_modal_mic_select",
                    onSelect = { micWire = it },
                )
                Spacer(Modifier.height(8.dp))
                ScheduleSelectField(
                    label = "Камеры",
                    value = wireLabel(MUTE_OPTIONS, camWire),
                    options = MUTE_OPTIONS,
                    enabled = !saving,
                    tag = "calls_schedule_call_modal_cameras_select",
                    onSelect = { camWire = it },
                )
                Spacer(Modifier.height(8.dp))

                // №16 Демонстрация экрана (unmute/mute_permanent).
                ScheduleSwitchRow(
                    label = "Демонстрация экрана",
                    checked = screenShareOn,
                    enabled = !saving,
                    tag = "calls_schedule_call_modal_screen_sharing_switch",
                    onChange = { screenShareOn = it },
                )
                // №17 Реакции (дефолт ON → feedback=1).
                ScheduleSwitchRow(
                    label = "Реакции",
                    checked = reactionsOn,
                    enabled = !saving,
                    tag = "calls_schedule_call_modal_reactions_switch",
                    onChange = { reactionsOn = it },
                )
                // №18 Совместный просмотр (дефолт ON → only_admin_can_share_movie=0).
                ScheduleSwitchRow(
                    label = "Совместный просмотр",
                    checked = movieOn,
                    enabled = !saving,
                    tag = "calls_schedule_call_modal_movie_sharing_switch",
                    onChange = { movieOn = it },
                )

                if (!isEdit) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ссылка будет скопирована",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("calls_schedule_call_modal_footer_link_will_be_copied"),
                    )
                }
                if (errorText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        errorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("calls_schedule_call_error"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    saving = true
                    errorText = ""
                    val tzArg = TimeZone.getTimeZone(tzId)
                    val timeArg = secFromWall(startWall, tzArg)
                    val durationArg = durationMs / 1000L
                    val nameArg = nameText.trim()
                    val nameWire = if (nameArg.length > 100) nameArg.substring(0, 100) else nameArg
                    val nameFinal = if (nameWire.isBlank()) DEFAULT_CALL_NAME else nameWire
                    val onlyAuthUsersArg = !anonOn
                    val adminMovieArg = !movieOn
                    val screenShareArg = if (screenShareOn) MUTE_ALLOWED else MUTE_DISABLED
                    val groupIdArg: Long? = if (identity == 0L) null else identity
                    val skipNotifArg = !remindOn
                    val chatHistArg: Boolean? = if (chatToggleOn && identity == 0L) chatHistoryOn else null
                    val untilArg: Long? = if (repeatWire != "never") {
                        val until = untilWall
                        val untilEffective = if (until != null) {
                            until
                        } else {
                            WallDateTime(startWall.year, startWall.month, startWall.day, 23, 59)
                        }
                        secFromWall(untilEffective, tzArg)
                    } else {
                        null
                    }
                    val markerArg: Long? = if (prefill.markerTime > 0L) prefill.markerTime else null
                    scope.launch {
                        if (isEdit) {
                            val ok = try {
                                deps.apiClient.messagesEditCallScheduled(
                                    callId = prefill.callId,
                                    name = nameFinal,
                                    timeSec = timeArg,
                                    durationSec = durationArg,
                                    muteAudio = micWire,
                                    muteVideo = camWire,
                                    onlyAuthUsers = onlyAuthUsersArg,
                                    recurrenceRule = repeatWire,
                                    waitingHall = waitingHallOn,
                                    feedback = reactionsOn,
                                    onlyAdminCanShareMovie = adminMovieArg,
                                    onlyAdminCanRecord = false,
                                    muteScreenSharing = screenShareArg,
                                    markerTime = markerArg,
                                    groupId = groupIdArg,
                                    skipNotification = skipNotifArg,
                                    showChatHistory = chatHistArg,
                                    recurrenceUntilSec = untilArg,
                                )
                            } catch (e: Exception) {
                                AppLog.e("CallsSchedule", "messagesEditCallScheduled error (callId=" + prefill.callId + ")", e)
                                false
                            }
                            if (ok) {
                                AppLog.i("CallsSchedule", "editCall OK: callId=" + prefill.callId + " time=$timeArg")
                                repo.refresh(CallsSectionKey.SCHEDULED, force = true)
                                Toast.makeText(context, "Изменения сохранены", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                saving = false
                                errorText = submitError(deps.apiClient.lastApiError, "сервер не подтвердил messages.editCall")
                                Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val resp = try {
                                deps.apiClient.callsStartScheduledCall(
                                    name = nameFinal,
                                    timeSec = timeArg,
                                    durationSec = durationArg,
                                    muteAudio = micWire,
                                    muteVideo = camWire,
                                    onlyAuthUsers = onlyAuthUsersArg,
                                    recurrenceRule = repeatWire,
                                    waitingHall = waitingHallOn,
                                    feedback = reactionsOn,
                                    onlyAdminCanShareMovie = adminMovieArg,
                                    onlyAdminCanRecord = false,
                                    muteScreenSharing = screenShareArg,
                                    groupId = groupIdArg,
                                    skipNotification = skipNotifArg,
                                    showChatHistory = chatHistArg,
                                    recurrenceUntilSec = untilArg,
                                )
                            } catch (e: Exception) {
                                AppLog.e("CallsSchedule", "callsStartScheduledCall error", e)
                                null
                            }
                            if (resp != null) {
                                AppLog.i("CallsSchedule", "calls.start OK: time=$timeArg duration=$durationArg")
                                repo.refresh(CallsSectionKey.SCHEDULED, force = true)
                                val link = jsonStr(resp, "join_link")
                                if (link.isNotBlank()) {
                                    // Подпись calls_will_copy_to_clipboard: ссылка копируется сразу.
                                    clipboard.setText(AnnotatedString(link))
                                    createdJoinLink = link
                                } else {
                                    Toast.makeText(context, "Звонок запланирован", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            } else {
                                saving = false
                                errorText = submitError(deps.apiClient.lastApiError, "сервер не подтвердил calls.start")
                                Toast.makeText(context, errorText, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("calls_schedule_call_modal_footer_button_submit"),
            ) {
                Text(if (isEdit) "Сохранить" else "Запланировать")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !saving,
                onClick = onDismiss,
                modifier = Modifier.testTag("calls_schedule_call_modal_footer_button_cancel"),
            ) { Text("Отмена") }
        },
    )

    // Дата/время начала: нативные системные пикеры (show() асинхронно —
    // main-поток не блокируют, #ANR-MAIN-IO).
    if (showStartPicker) {
        StartDateTimePicker(
            currentSec = startUnix,
            onPicked = { pickedSec ->
                startWall = wallFromSec(pickedSec, tz)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }

    // Дата конца повтора (date-only пикер).
    if (showUntilPicker) {
        UntilDatePicker(
            currentWall = untilWall,
            fallbackWall = startWall,
            onPicked = { pickedYear, pickedMonth, pickedDay ->
                untilWall = WallDateTime(pickedYear, pickedMonth, pickedDay, 23, 59)
                showUntilPicker = false
            },
            onDismiss = { showUntilPicker = false },
        )
    }
}

/** Пост-модалка успешного создания (web SHARE_PLANNED_CALL / CallsModalEventPlanned). */
@Composable
private fun ScheduledCreatedDialog(
    joinLink: String,
    onCopy: () -> Unit,
    onDone: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Звонок запланирован") },
        text = {
            Column {
                Text(
                    joinLink,
                    modifier = Modifier.testTag("calls_schedule_call_created_link"),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ссылка будет скопирована",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("calls_schedule_call_modal_footer_link_will_be_copied"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onCopy,
                modifier = Modifier.testTag("calls_schedule_call_created_copy"),
            ) { Text("Скопировать ссылку") }
        },
        dismissButton = {
            TextButton(
                onClick = onDone,
                modifier = Modifier.testTag("calls_schedule_call_created_done"),
            ) { Text("Готово") }
        },
    )
}

/** Селект-поле на readOnly TextField: клик-цель — оверлей поверх поля. */
@Composable
private fun ScheduleSelectField(
    label: String,
    value: String,
    options: List<WireOption>,
    enabled: Boolean,
    tag: String,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag),
        )
        if (enabled) {
            // readOnly-TextField глотает касания — клик-оверлей открывает меню.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { open = true },
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            for (opt in options) {
                DropdownMenuItem(
                    text = { Text(opt.label) },
                    onClick = {
                        open = false
                        onSelect(opt.wire)
                    },
                )
            }
        }
    }
}

/** Часовой пояс: readOnly-поле + DropdownMenu по полному каталогу TimeZone ОС. */
@Composable
private fun ScheduleTimeZoneField(
    tzId: String,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val tzIds = remember {
        val all = TimeZone.getAvailableIDs()
        val unique = LinkedHashSet<String>()
        for (id in all) unique.add(id)
        unique.sorted()
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = tzId,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Часовой пояс") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("calls_schedule_call_modal_timezone_select"),
        )
        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { open = true },
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
        ) {
            for (id in tzIds) {
                DropdownMenuItem(
                    text = { Text(id) },
                    onClick = {
                        open = false
                        onPick(id)
                    },
                )
            }
        }
    }
}

/** Строка «подпись + switch» (remind/waiting_hall/anon/chat_history/screen/reactions/movie). */
@Composable
private fun ScheduleSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** Честное сообщение об ошибке сабмита: lastApiError фасада, иначе общий текст. */
private fun submitError(apiError: String?, fallback: String): String {
    val err = apiError
    if (err != null && err.isNotBlank()) return "Ошибка: $err"
    return "Не удалось сохранить ($fallback)"
}

// ─── парсинг предзаполнения (новый код — #NULL-EXPLICIT: без safe-call/elvis) ───

private fun jsonStr(obj: JsonObject, key: String): String {
    val el = obj.get(key)
    if (el == null || !el.isJsonPrimitive) return ""
    return el.asString
}

private fun jsonLong(obj: JsonObject, key: String): Long {
    val el = obj.get(key)
    if (el == null || !el.isJsonPrimitive) return 0L
    return el.asLong
}

private fun jsonObj(obj: JsonObject, key: String): JsonObject? {
    val el = obj.get(key)
    if (el == null || !el.isJsonObject) return null
    return el.asJsonObject
}

/**
 * Разбор айтема messages.getScheduledCalls в предзаполнение (wire REV-DEEP-2:
 * schedule{time, duration, recurrence_rule, recurrence_until_time, marker_time},
 * mute_audio/mute_video/mute_screen_sharing, only_auth_users, waiting_hall,
 * feedback, only_admin_can_share_movie, skip_notification, show_chat_history;
 * group_id — поле айтема, иначе -group.id). null-айтем = дефолты создания.
 */
private fun parseSchedulePrefill(item: JsonObject?): SchedulePrefill {
    if (item == null) {
        return SchedulePrefill(
            callId = "",
            name = "",
            startWall = nowRoundedToHalfHourWall(),
            durationKey = "30minutes",
            recurrenceRule = "never",
            untilWall = null,
            markerTime = 0L,
            groupId = 0L,
            remindOn = false,
            waitingHallOn = false,
            anonOn = true,
            chatHistoryOn = false,
            micWire = MUTE_ALLOWED,
            camWire = MUTE_ALLOWED,
            screenShareOn = false,
            reactionsOn = true,
            movieOn = true,
        )
    }
    val sched = jsonObj(item, "schedule")

    var timeSec = 0L
    var recurrenceUntilSec = 0L
    var markerTime = 0L
    var recurrenceRule = ""
    var durationSec = 0L
    if (sched != null) {
        timeSec = jsonLong(sched, "time")
        recurrenceUntilSec = jsonLong(sched, "recurrence_until_time")
        markerTime = jsonLong(sched, "marker_time")
        recurrenceRule = jsonStr(sched, "recurrence_rule")
        durationSec = jsonLong(sched, "duration")
    }
    if (timeSec > MS_EPOCH_THRESHOLD) timeSec = timeSec / 1000L
    if (recurrenceUntilSec > MS_EPOCH_THRESHOLD) recurrenceUntilSec = recurrenceUntilSec / 1000L

    val startWall = if (timeSec > 0L) {
        wallFromSec(timeSec, TimeZone.getDefault())
    } else {
        nowRoundedToHalfHourWall()
    }

    var untilWall: WallDateTime? = null
    if (recurrenceUntilSec > 0L) {
        val w = wallFromSec(recurrenceUntilSec, TimeZone.getDefault())
        untilWall = WallDateTime(w.year, w.month, w.day, 23, 59)
    }

    // Дефолты web (создание): напоминание ВЫКЛ, анонимная ссылка ON, реакции ON,
    // совместный просмотр ON, зал ожидания off, history off.
    val skipNotification = jsonLong(item, "skip_notification") == 1L
    val onlyAuthUsers = jsonLong(item, "only_auth_users") == 1L
    val adminMovie = jsonLong(item, "only_admin_can_share_movie") == 1L

    var groupId = jsonLong(item, "group_id")
    if (groupId == 0L) {
        val grp = jsonObj(item, "group")
        if (grp != null) {
            val gid = jsonLong(grp, "id")
            if (gid > 0L) groupId = -gid
        }
    }

    return SchedulePrefill(
        callId = jsonStr(item, "call_id"),
        name = jsonStr(item, "name"),
        startWall = startWall,
        durationKey = durationKeyOf(durationSec),
        recurrenceRule = normalizeRepeat(recurrenceRule),
        untilWall = untilWall,
        markerTime = markerTime,
        groupId = groupId,
        remindOn = !skipNotification,
        waitingHallOn = jsonLong(item, "waiting_hall") == 1L,
        anonOn = !onlyAuthUsers,
        chatHistoryOn = jsonLong(item, "show_chat_history") == 1L,
        micWire = validateMute(jsonStr(item, "mute_audio"), MUTE_ALLOWED),
        camWire = validateMute(jsonStr(item, "mute_video"), MUTE_ALLOWED),
        screenShareOn = jsonStr(item, "mute_screen_sharing") == MUTE_ALLOWED,
        reactionsOn = jsonLong(item, "feedback") == 1L,
        movieOn = !adminMovie,
    )
}

/**
 * Тоггл «История чата» из calls.getSettings: toggles[{name,enabled}],
 * имя enableChatHistoryToggle (tolerant-парсинг, дефолт false).
 */
private fun parseChatHistoryToggle(settings: JsonObject): Boolean {
    val togglesEl = settings.get("toggles")
    if (togglesEl == null || !togglesEl.isJsonArray) return false
    val arr = togglesEl.asJsonArray
    for (el in arr) {
        if (!el.isJsonObject) continue
        val obj = el.asJsonObject
        if (jsonStr(obj, "name") == "enableChatHistoryToggle") {
            val enabledEl = obj.get("enabled")
            if (enabledEl != null && enabledEl.isJsonPrimitive) return enabledEl.asBoolean
            return false
        }
    }
    return false
}

private fun normalizeRepeat(wire: String): String {
    for (opt in REPEAT_OPTIONS) {
        if (opt.wire == wire) return wire
    }
    return "never"
}

private fun validateMute(wire: String, fallback: String): String {
    if (wire == MUTE_ALLOWED || wire == MUTE_ON_ENTER || wire == MUTE_DISABLED) return wire
    return fallback
}

private fun durationMsOf(key: String): Long {
    for (chip in DURATION_CHIPS) {
        if (chip.key == key) return chip.ms
    }
    return 1_800_000L
}

private fun durationKeyOf(sec: Long): String {
    if (sec > 0L) {
        for (chip in DURATION_CHIPS) {
            if (chip.ms / 1000L == sec) return chip.key
        }
    }
    return "30minutes"
}

private fun wireLabel(options: List<WireOption>, wire: String): String {
    for (opt in options) {
        if (opt.wire == wire) return opt.label
    }
    return wire
}

/** «Сейчас, округлённое до 30 минут» — дефолт старта создания (web dM/_M). */
private fun nowRoundedToHalfHourWall(): WallDateTime {
    val cal = Calendar.getInstance()
    cal.add(Calendar.MINUTE, 15)
    val minute = cal.get(Calendar.MINUTE)
    cal.set(Calendar.MINUTE, if (minute < 30) 0 else 30)
    cal.set(Calendar.SECOND, 0)
    return WallDateTime(
        year = cal.get(Calendar.YEAR),
        month = cal.get(Calendar.MONTH),
        day = cal.get(Calendar.DAY_OF_MONTH),
        hour = cal.get(Calendar.HOUR_OF_DAY),
        minute = cal.get(Calendar.MINUTE),
    )
}

/** Unix-секунды из wall-clock полей КАЛЕНДАРЁМ в выбранной TZ (web SO(CO(start,tz))). */
private fun secFromWall(wall: WallDateTime, tz: TimeZone): Long {
    val cal = Calendar.getInstance(tz)
    cal.clear()
    cal.set(wall.year, wall.month, wall.day, wall.hour, wall.minute, 0)
    return cal.timeInMillis / 1000L
}

/** Wall-clock поля из unix-секунд в заданной TZ. */
private fun wallFromSec(sec: Long, tz: TimeZone): WallDateTime {
    val cal = Calendar.getInstance(tz)
    cal.timeInMillis = sec * 1000L
    return WallDateTime(
        year = cal.get(Calendar.YEAR),
        month = cal.get(Calendar.MONTH),
        day = cal.get(Calendar.DAY_OF_MONTH),
        hour = cal.get(Calendar.HOUR_OF_DAY),
        minute = cal.get(Calendar.MINUTE),
    )
}

private val WALL_FORMAT = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("ru"))

/** Формат wall-clock полей в заданной TZ (для полей «Начало»/«Конец повтора»). */
private fun formatWall(wall: WallDateTime, tz: TimeZone): String {
    val cal = Calendar.getInstance(tz)
    cal.clear()
    cal.set(wall.year, wall.month, wall.day, wall.hour, wall.minute, 0)
    return formatMillis(cal.timeInMillis, tz)
}

/** Формат unix-секунд в заданной TZ (поле «Окончание»). */
private fun formatSec(sec: Long, tz: TimeZone): String {
    return formatMillis(sec * 1000L, tz)
}

private fun formatMillis(ms: Long, tz: TimeZone): String {
    val sdf = WALL_FORMAT
    synchronized(sdf) {
        sdf.timeZone = tz
        return sdf.format(Date(ms))
    }
}

/** date→time цепочка системных пикеров; результат — unixtime в секундах. */
@Composable
private fun StartDateTimePicker(
    currentSec: Long,
    onPicked: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val cal = Calendar.getInstance()
        if (currentSec > 0L) cal.timeInMillis = currentSec * 1000L
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        DatePickerDialog(
            context,
            { _, pickedYear, pickedMonth, pickedDay ->
                TimePickerDialog(
                    context,
                    { _, pickedHour, pickedMinute ->
                        val picked = Calendar.getInstance()
                        picked.set(pickedYear, pickedMonth, pickedDay, pickedHour, pickedMinute, 0)
                        onPicked(picked.timeInMillis / 1000L)
                    },
                    hour,
                    minute,
                    true,
                ).show()
            },
            year,
            month,
            day,
        ).show()
        // Флаг снят сразу: пикеры живут в собственных окнах, повторы
        // композиции не должны плодить новые инстансы.
        onDismiss()
    }
}

/** Date-only пикер конца повтора (дефолт — день старта, 23:59). */
@Composable
private fun UntilDatePicker(
    currentWall: WallDateTime?,
    fallbackWall: WallDateTime,
    onPicked: (Int, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val current = currentWall
        val wall = if (current != null) current else fallbackWall
        DatePickerDialog(
            context,
            { _, pickedYear, pickedMonth, pickedDay ->
                onPicked(pickedYear, pickedMonth, pickedDay)
            },
            wall.year,
            wall.month,
            wall.day,
        ).show()
        onDismiss()
    }
}

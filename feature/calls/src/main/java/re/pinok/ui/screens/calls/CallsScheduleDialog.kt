package re.pinok.ui.screens.calls

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Calendar
import kotlinx.coroutines.launch
import re.pinok.feature.calls.CallsSectionKey
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.feature.calls.LocalCallsSectionRepository
import re.pinok.util.AppLog
import re.pinok.util.toAbsoluteTime

/**
 * #CALLS-SNAP (2026-09-06): Этап Г/Г2 плана «звонки.перенос.план.md» — модалка
 * «Запланировать» (REV-UI §6.3, ModalScheduleCall calls_schedule_call_modal_root).
 * Создание и правка — одна модалка (по реверсу: headerTitle calls_plan_event /
 * calls_edit_call_title, submit calls_landing_standalone_schedule_call / calls_save);
 * создание = messages.editCall с call_id="0" (у web в создании call_id
 * отсутствует — реализация фасада добавляет поле всегда, «0» = новый звонок),
 * правка — с существующим call_id.
 *
 * РЕНДЕРЯТСЯ (обеспечены фасадом CallsApi.messagesEditCall(callId, name, scheduledDate)):
 *  1. Название (реверс #2, calls_title) — input calls_schedule_call_modal_title_input,
 *     лимит 100 символов (calls_validation_length), пустое → null (серверный дефолт);
 *  2. Начало (реверс #3, calls_start_time) — date+time picker
 *     (calls_schedule_call_modal_start_time_picker) → scheduled_date (unixtime,
 *     СЕКУНДЫ — формат VK API; секция SCHEDULED репозитория парсит те же секунды).
 *
 * НЕ РЕНДЕРЯТСЯ (no-stub: у фасада НЕТ соответствующих мутаторов; честное
 * отклонение, прецедент волны-3 — pen_outline_16):
 *  - #1 «От имени» (юзер/группа, user_select) — API выбора авторства отсутствует;
 *  - #4 «Длительность» (чипы 30мин…1день) и #5 «Окончание» (finish_time_picker) —
 *    messages.editCall не принимает duration/end;
 *  - #6 «Часовой пояс», #7 «Повтор» (rrule), #8 «Конец повтора» — нет
 *    timezone/rrule/recurrenceUntilTime в messages.editCall;
 *  - #9 «Добавить в календарь» (.ics/ссылка) — нет API;
 *  - #10 «Напоминание» (remind_switch) — нет API;
 *  - #11 «Зал ожидания», #12 «Анонимная ссылка», #13 «История чата», #14
 *    «Микрофоны», #15 «Камеры», #16 «Демонстрация экрана», #17 «Реакции», #18
 *    «Совместный просмотр» — payload web-сабмита (conversationOptions/mediaOptions)
 *    фасадом не покрывается (calls.updateCallSettings принимает show_chat_history
 *    и требует СУЩЕСТВУЮЩИЙ call_id);
 *  - футер-подпись «Ссылка будет скопирована» (calls_will_copy_to_clipboard) —
 *    messagesEditCall возвращает Boolean, ссылки-результата нет; копирование
 *    несуществующего объекта имитировать нельзя.
 */
@Composable
fun CallsScheduleCallDialog(
    editCallId: String?,
    initialName: String,
    initialDateSec: Long,
    onDismiss: () -> Unit,
) {
    val deps = LocalCallsDeps.current
    val repo = LocalCallsSectionRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = editCallId != null

    var nameText by remember { mutableStateOf(initialName) }
    // Дефолт старта: переданная дата (правка) либо ближайший целый час (создание).
    var startSec by remember {
        mutableLongStateOf(
            if (initialDateSec > 0L) {
                initialDateSec
            } else {
                val cal = Calendar.getInstance()
                cal.add(Calendar.HOUR_OF_DAY, 1)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis / 1000L
            }
        )
    }
    var saving by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (isEdit) "Редактировать звонок" else "Запланировать звонок") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Название (calls_title, лимит 100 — calls_validation_length).
                TextField(
                    value = nameText,
                    onValueChange = { if (it.length <= 100) nameText = it },
                    placeholder = { Text("Название звонка") },
                    singleLine = true,
                    enabled = !saving,
                    supportingText = { Text("${nameText.length}/100") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("calls_schedule_call_modal_title_input"),
                )
                Spacer(Modifier.height(8.dp))
                // Начало (calls_start_time): readOnly-поле показывает выбранное;
                // выбор — нативные date+time пикеры по клику на строку-подсказку.
                TextField(
                    value = startSec.toAbsoluteTime(),
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
                        .clickable(enabled = !saving) { showPicker = true }
                        .testTag("calls_schedule_call_modal_pick_start"),
                )
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
                enabled = !saving,
                onClick = {
                    saving = true
                    errorText = ""
                    val callId: String = if (editCallId != null) editCallId else "0" // создание: call_id "0" (см. KDoc)
                    val nameArg = nameText.trim().ifBlank { null }
                    val dateArg = startSec
                    scope.launch {
                        val ok = try {
                            deps.apiClient.messagesEditCall(
                                callId = callId,
                                name = nameArg,
                                scheduledDate = dateArg,
                            )
                        } catch (e: Exception) {
                            AppLog.e("CallsSchedule", "messagesEditCall error (callId=$callId)", e)
                            false
                        }
                        if (ok) {
                            AppLog.i("CallsSchedule", "editCall OK: callId=$callId name=$nameArg date=$dateArg")
                            repo.refresh(CallsSectionKey.SCHEDULED, force = true)
                            Toast.makeText(
                                context,
                                if (isEdit) "Изменения сохранены" else "Звонок запланирован",
                                Toast.LENGTH_SHORT,
                            ).show()
                            onDismiss()
                        } else {
                            saving = false
                            // Честное РЕАЛЬНОЕ сообщение: lastApiError фасада, иначе общий текст.
                            val apiErr = deps.apiClient.lastApiError
                            val msg = if (apiErr.isNullOrBlank()) {
                                "Не удалось сохранить (сервер не подтвердил messages.editCall)"
                            } else {
                                "Ошибка: $apiErr"
                            }
                            errorText = msg
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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

    // Дата/время: нативные системные пикеры (отдельные окна, show() —
    // асинхронно, main-поток не блокируют — #ANR-MAIN-IO).
    if (showPicker) {
        StartDateTimePicker(
            currentSec = startSec,
            onPicked = { pickedSec ->
                startSec = pickedSec
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
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

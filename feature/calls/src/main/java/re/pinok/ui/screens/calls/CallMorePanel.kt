package re.pinok.ui.screens.calls

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import re.pinok.realtime.CallSignalingClient
import re.pinok.util.AppLog

/**
 * #CALLS-ZH2 (2026-09-06, Этап Ж-2, Task 6-a): панель «Ещё» активного звонка —
 * меню дополнительных функций (web-зона calls_call_menu_*; вход — кнопка «Ещё» футера).
 *
 * РЕАЛИЗОВАНО (реальные wire-пути, no-stub):
 *  - Ж6 ЗАПИСЬ (Ж0 §4, 16131@422393/422494): record-start {movieId:null,name,
 *    privacy:"DIRECT_LINK",groupId:null,roomId:null,streamMovie:false} / record-stop
 *    {roomId:null}. Статус — серверные record-started/stopped (CallScreen-стейт).
 *    Сервисное сообщение о записи в conversation-чат НЕ шлём (у эталона его создаёт
 *    сервер — i18n calls_participant_call_record_started/_stopped; API отправки
 *    сообщений в conversation звонка фасадом не обеспечен — честное ограничение);
 *  - Ж7 РАСШИФРОВКА (Ж0 §5.1, 16131@310522/310539): asr-start {fileName≤128,
 *    валидация символов} / asr-stop. Результат — «Расшифровки звонков» (раздел есть).
 *    Право управления (isMeCanManageAsr = админ/автор, bridge@204911) НЕ проверяем
 *    локально — сервер отклонит не-админу ошибкой (type:"error" виден в логе);
 *  - Ж7 СУБТИТРЫ (Ж0 §5.2/§5.3): тоггл → WebRtcEngine.sendRequestAsr (DC
 *    producerCommand, varint [3][0][seq][bool] — JSON-фолбэка НЕТ) + оверлей
 *    CallSubtitlesOverlay (текст по DC "asr"). Автоповтор при переподключении —
 *    LaunchedEffect по фазе ACTIVE в CallScreen (эталон bridge@181914);
 *  - Ж9 ЗАЛЫ (Ж0 §8) и Ж8 ЗАЛ ОЖИДАНИЯ (Ж0 §7) — отдельные панели CallRoomsPanel /
 *    CallWaitingHallPanel (кнопки-переходы; signaling-методы добавлены аддитивно).
 *
 * ЧЕСТНЫЕ ОТКЛОНЕНИЯ (no-stub, зафиксированы в отчёте Task 6-a):
 *  - Ж10 WATCH TOGETHER — video.getWatchTogetherOwnerVideos/video.search +
 *    add-movie/update-movie/remove-movie: API video.* фасадом CallsDependencies
 *    НЕ обеспечен, WS-методы movie-группы без него мертвы (отклонение);
 *  - Ж11 ТРАНСЛЯЦИЯ — двухфазна: video.startStreaming (фасадом НЕ обеспечен) →
 *    record-start{streamMovie:true}; параметр streamMovie существует, но без
 *    первой фазы бессмыслен (отклонение; запись Ж6 без трансляции — реализована);
 *  - Ж11 VMOJI — локальный рендер-пайплайн (canvas/animoji) — в движке нет
 *    capture-пайплайна вообще (прецедент отклонений CallMediaSettingsPanel);
 *  - Ж11 ВИРТУАЛЬНЫЙ ФОН — photos.getCallBackgroundsPhotoUploadServer/
 *    saveBackgroundPhoto в фасаде НЕТ + сегментация видео в движке нет (отклонение);
 *  - Ж11 ТЕЛЕФОННЫЙ ЗВОНОК — вне скоупа плана (КТС/PSTN).
 *
 * #NULL-EXPLICIT: без `!!`/`?.`/`?:` — захват nullable в локальный val + if.
 */
private const val CALL_MORE_TAG = "CallMorePanel"

@Composable
internal fun CallMorePanel(
    signaling: CallSignalingClient,
    isRecording: Boolean,
    isAsrRunning: Boolean,
    subtitlesEnabled: Boolean,
    defaultRecordName: String,
    defaultAsrName: String,
    onToggleSubtitles: (Boolean) -> Unit,
    onOpenRooms: () -> Unit,
    onOpenWaitingHall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    // Ж6: старт/стоп записи. Имя по умолчанию эталона «<имя звонившего> <дата>»
    // формирует CallScreen (defaultRecordName); лимит 128 — guard на месте.
    val toggleRecord: () -> Unit = {
        if (isRecording) {
            val ok = signaling.stopRecording()
            AppLog.i(CALL_MORE_TAG, "record-stop: отправлено=" + ok)
            if (!ok) Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
        } else {
            val name = if (defaultRecordName.length > 128) defaultRecordName.take(128) else defaultRecordName
            val ok = signaling.startRecording(name = name)
            AppLog.i(CALL_MORE_TAG, "record-start: name=" + name + " отправлено=" + ok)
            if (!ok) Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
        }
    }

    // Ж7: старт/стоп расшифровки (fileName = «Расшифровка <дата>» от CallScreen).
    val toggleAsr: () -> Unit = {
        if (isAsrRunning) {
            val ok = signaling.stopAsr()
            AppLog.i(CALL_MORE_TAG, "asr-stop: отправлено=" + ok)
            if (!ok) Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
        } else {
            val ok = signaling.startAsr(defaultAsrName)
            AppLog.i(CALL_MORE_TAG, "asr-start: name=" + defaultAsrName + " отправлено=" + ok)
            if (!ok) Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF20203A),
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ещё",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // ══ Ж6: запись звонка (calls_record_start / calls_end_recording) ══
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { toggleRecord() },
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = if (isRecording) Color(0xFFE53935) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRecording) "Остановить запись" else "Начать запись",
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = if (isRecording) "Запись идёт — все уведомлены сервером" else "Имя: " + defaultRecordName,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(8.dp))

                // ══ Ж7: расшифровка (calls_asr_modal/_start/_stop) ══
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { toggleAsr() },
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = if (isAsrRunning) Color(0xFF43A047) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAsrRunning) "Остановить расшифровку" else "Начать расшифровку",
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = if (isAsrRunning) "Расшифровка создаётся сервером" else "Заголовок: " + defaultAsrName,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ══ Ж7: субтитры для себя (calls_call_menu_enable_subtitles) ══
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Субтитры (только для себя)",
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = if (subtitlesEnabled) "Живые титры включены" else "Требуется включённая расшифровка",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = subtitlesEnabled,
                        onCheckedChange = { on -> onToggleSubtitles(on) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF43A047)),
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(8.dp))

                // ══ Ж9: залы (calls_rooms_*; список/создание/переключение — CallRoomsPanel) ══
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenRooms() },
                ) {
                    Icon(
                        imageVector = Icons.Default.MeetingRoom,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Залы (брейкаут)",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ══ Ж8: зал ожидания (calls_waiting_hall_*; впуск/отказ — CallWaitingHallPanel) ══
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenWaitingHall() },
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Зал ожидания",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(10.dp))

                // Честные отклонения Ж10/Ж11 (no-stub) — видимым текстом (детали — KDoc панели).
                Text(
                    text = "Watch together, трансляция, vmoji, виртуальный фон и телефонный звонок не перенесены: API video.*/photos.getCallBackgroundsPhotoUploadServer отсутствуют в фасаде, рендер-пайплайна камеры в движке нет (детали — KDoc панели).",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

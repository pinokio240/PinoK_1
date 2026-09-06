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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import re.pinok.feature.calls.LocalCallsDeps
import re.pinok.realtime.CallSignalingClient
import re.pinok.util.AppLog

/**
 * #CALLS-ZH (2026-09-06, Этап Ж/Ж4, Task 5-b): настройки медиа в звонке
 * (web: calls_media_settings_modal_root/_cancel/_save; поля _camera/_mic/_noise/
 * _video_quality — REV-UI §11.3; testid-зона calls_call_settings_root).
 *
 * РЕНДЕРЯТСЯ (реальные пути, no-stub):
 *  - МИКРОФОН: локальный mute (тот же стейт isMuted, что и футерная кнопка) +
 *    WS change-media-settings {mediaSettings: ПОЛНЫЕ 6 bool} (Ж0 §9.1,
 *    16131@306103) — включается onMuteChange в CallScreen;
 *  - ШУМОДАВ: WS update-media-modifiers {mediaModifiers:{denoise,denoiseAnn}}
 *    (Ж0 §9.2, 16131@308860; маппинг 9644@18175: AUTO/NEURAL={true,true},
 *    SIMPLE={true,false}, NONE={false,false}). Дефолт — SovaPrefs
 *    calls_noise_cancel_default (З2); выбор ПЕРСИСТИТСЯ тем же сеттером —
 *    web-аналог localStorage NOISE_CANCELLATION_MODE (Ж0 §9.2: «не сервер»).
 *    Значение "NONE" не входит в тройку Этапа З2 (AUTO/SIMPLE/NEURAL) —
 *    настройки раздела его просто не подсветят (чтение tolerant);
 *  - МАРШРУТ ЗВУКА: громкая связь/телефонный динамик — engine.setSpeakerOn
 *    (тот же стейт isSpeakerOn, что и футерная кнопка). Дефолт
 *    calls_route_default применяется при СТАРТЕ звонка (CallScreen #CALLS-ZH).
 *
 * НЕ РЕНДЕРЯТСЯ (no-stub, честные отклонения — зафиксированы в отчёте 5-b):
 *  - КАЧЕСТВО ВИДЕО (calls_video_quality_optimal/high, констрейнты 640×360/
 *    1280×720 — Ж0 §9.3): качество — ЛОКАЛЬНЫЕ capture-констрейнты (wire-эффекта
 *    нет), а в WebRtcEngine камеры НЕТ (только чёрная видеозаглушка
 *    #CALLS-SYMMETRIC) — констрейнты не к чему применять;
 *  - КАМЕРА front/back (calls_camera_default) — capture-пайплайна нет;
 *  - ВЫБОР МИКРОФОНА-УСТРОЙСТВА (calls_mic_default) — API смены input-устройства
 *    в движке нет (AudioRecord создаётся JavaAudioDeviceModule libwebrtc);
 *  - BLUETOOTH-маршрут (calls_route_default=bt) — SCO-форсирование не реализовано.
 *  Дефолты mic/camera остаются потребителями следующей волны (с появлением
 *  capture-пайплайна/устройствых API движка).
 *
 * #NULL-EXPLICIT: без `!!`/`?.`/`?:` — захват nullable в локальный val + if.
 */
private const val CALL_MEDIA_TAG = "CallMediaSettingsPanel"

/** Опции шумодава: значение SovaPrefs → подпись (web: calls_noise_* — §11.3). */
private val CALL_NOISE_OPTIONS = listOf(
    "AUTO" to "Авто (нейросетевой)",
    "NEURAL" to "Нейросетевой",
    "SIMPLE" to "Простой",
    "NONE" to "Выключен",
)

@Composable
internal fun CallMediaSettingsPanel(
    signaling: CallSignalingClient,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onMuteChange: (Boolean) -> Unit,
    onSpeakerChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val deps = LocalCallsDeps.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Текущий режим шумодава: дефолт из SovaPrefs (З2), затем — применённый выбор.
    var noiseMode by remember { mutableStateOf("NEURAL") }
    var noiseApplied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val saved = runCatching { deps.prefs.callsNoiseCancelDefault.first() }.getOrDefault("NEURAL")
        noiseMode = saved
        AppLog.i(CALL_MEDIA_TAG, "дефолт шумодава из SovaPrefs: " + saved)
    }

    // Применить шумодав: update-media-modifiers (точный wire Ж0 §9.2); успех →
    // персист (аналог localStorage NOISE_CANCELLATION_MODE web, Ж0 §9.2).
    val applyNoise: (String) -> Unit = { mode ->
        val denoiseAnn = mode == "AUTO" || mode == "NEURAL"
        val denoise = denoiseAnn || mode == "SIMPLE"
        val ok = signaling.updateMediaModifiers(denoise = denoise, denoiseAnn = denoiseAnn)
        if (ok) {
            noiseMode = mode
            noiseApplied = true
            AppLog.i(CALL_MEDIA_TAG, "update-media-modifiers: mode=" + mode + " denoise=" + denoise + " denoiseAnn=" + denoiseAnn)
            scope.launch {
                runCatching { deps.prefs.setCallsNoiseCancelDefault(mode) }
            }
        } else {
            Toast.makeText(context, "Не отправлено: сигналинг закрыт", Toast.LENGTH_SHORT).show()
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
                        text = "Настройки",
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

                // Микрофон (calls_media_settings_field_mic): mute + change-media-settings.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isMuted) "Микрофон выключен" else "Микрофон включён",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = !isMuted,
                        onCheckedChange = { micOn -> onMuteChange(!micOn) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF43A047)),
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Маршрут (calls_media_settings_field_route-зона): speaker/earpiece.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.width(28.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isSpeakerOn) "Громкая связь" else "Телефонный динамик",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = isSpeakerOn,
                        onCheckedChange = { on -> onSpeakerChange(on) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF43A047)),
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(10.dp))

                // Шумодав (calls_media_settings_field_noise).
                Text(
                    text = "Шумоподавление",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (noiseApplied) "Применено в этом звонке и сохранено по умолчанию" else "По умолчанию из настроек: " + noiseMode,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
                for (opt in CALL_NOISE_OPTIONS) {
                    val optValue = opt.first
                    val optLabel = opt.second
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { applyNoise(optValue) },
                    ) {
                        RadioButton(
                            selected = noiseMode == optValue,
                            onClick = { applyNoise(optValue) },
                        )
                        Text(text = optLabel, color = Color.White, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(10.dp))

                // Честные отклонения (no-stub) — видимым текстом, не притворяемся контролами.
                Text(
                    text = "Качество видео, камера (фронт/тыл), выбор микрофона и Bluetooth-маршрут не перенесены: в движке звонка нет capture-пайплайна и API устройств (детали — KDoc панели).",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

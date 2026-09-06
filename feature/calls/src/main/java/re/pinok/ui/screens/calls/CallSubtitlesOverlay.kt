package re.pinok.ui.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * #CALLS-ZH2 (2026-09-06, Этап Ж-2, Task 6-a) Ж7/Ж6: оверлеи ПОВЕРХ звонка.
 *
 * 1) [CallSubtitlesOverlay] — живые субтитры (Ж0 §5.3): последние строки транскрипции
 *    по DataChannel "asr" (приём — WebRtcEngine.handleAsrFrame; текст без атрибуции
 *    спикера — ssrc→участник не мапится, реестра в движке нет, честное ограничение).
 *    Эталон склеивает строки при интервале <5с (bridge@181914-зона) — здесь приходит
 *    УЖЕ пословный текст сервера, строки добавляются как есть (склейка не нужна).
 *    Размещение — над футером (низ экрана): зона Top-55% может быть перекрыта
 *    SurfaceViewRenderer видео (#CALLS-SURFACE-ZTOP — hardware-слой ПОВЕРХ окна,
 *    Compose-оверлеи в его границах не видны — честное ограничение размещения).
 *
 * 2) [CallRecordingBadge] — индикация записи (Ж0 §4: i18n calls_name_is_recording /
 *    calls_recording_in_progress): красная точка + текст. Состояние — ТОЛЬКО из
 *    серверных уведомлений record-started/record-stopped (CallScreen #CALLS-ZH2
 *    when-ветки) и reconnect-снимка не парсится (честно: после реконнекта бейдж может
 *    погаснуть до следующего уведомления — wire-снимок recordInfo не восстановлен,
 *    Ж0 §13.4). Кнопка старта записи — CallMorePanel (record-start/stop — Ж0 §4).
 *
 * #NULL-EXPLICIT: без `!!`/`?.`/`?:` — захват nullable в локальный val + if.
 */

/** Сколько последних строк субтитров показываем (эталон — бегущая строка последних фраз). */
private const val SUBTITLES_VISIBLE_LINES = 3

@Composable
internal fun CallSubtitlesOverlay(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    if (lines.isEmpty()) return
    // Последние N строк; последняя — ярче (текущая фраза).
    val from = if (lines.size > SUBTITLES_VISIBLE_LINES) lines.size - SUBTITLES_VISIBLE_LINES else 0
    val visible: List<String> = lines.subList(from, lines.size)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
    ) {
        for (i in visible.indices) {
            val isCurrent = i == visible.size - 1
            Text(
                text = visible[i],
                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.6f),
                fontSize = if (isCurrent) 15.sp else 13.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
                modifier = Modifier
                    .background(
                        color = Color(0xB3141428),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
internal fun CallRecordingBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    // no-stub: не идёт запись — контрол не рендерится вовсе (честная индикация).
    if (!visible) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(color = Color(0xB3141428), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = Color(0xFFE53935), shape = CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Идёт запись звонка",
            color = Color.White,
            fontSize = 12.sp,
        )
    }
}

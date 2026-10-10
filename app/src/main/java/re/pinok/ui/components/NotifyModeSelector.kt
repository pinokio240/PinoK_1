package re.pinok.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import re.pinok.data.local.SovaPrefs
import re.pinok.util.AppLog

// ═══════════════════════════════════════════════════════════════════════
// #NOTIFY-MODES (Fix #390): общий закреплённый элемент «Режим уведомлений».
//
// Переехал с правой панели ленты (FeedRightPanel) в ОСНОВНУЮ (глобальную)
// боковую панель навигации приложения (SovaNavHost.ModalNavigationDrawer),
// доступную из всех разделов. Логика и данные общие — один компонент
// используется глобально, чтобы не дублировать ни опции, ни диалог выбора.
//
// #NOTIFY-MODES-ORDER (2026-10-01): пользователь задал НОВЫЙ ПОРЯДОК и подписи
// вариантов в списке — по логичности использования (не по значению режима):
//   1) ALL (1) — «по умолчанию», включён именно он
//   2) MESSAGES_ONLY (0)
//   3) COMMUNITIES_ONLY (2)
//   4) SILENT (3)
// Значения режимов НЕ меняются — только порядок отображения и подписи.
// (2026-10-10: пункты 2 и 3 поменяны местами — Сообщения перед Сообществами.)
// ═══════════════════════════════════════════════════════════════════════

/** Одна опция выбора режима уведомлений: значение + название + пояснение. */
internal data class NotifyModeOption(
    val mode: Int,
    val title: String,
    val description: String,
)

/** Опции диалога выбора режима уведомлений — названия и пояснения 1:1 с
 * формулировками юзера (Fix #390) и с секцией «Режимы уведомлений»
 * в SettingsScreen.NotificationsTab. Делится компонентами (internal). */
internal val NOTIFY_MODE_OPTIONS = listOf(
    NotifyModeOption(
        SovaPrefs.NOTIFY_MODE_ALL,
        "Уведомления сообщений и сообществ (вибрация и звук)",
        "Всплывающие от Сообщений и от Сообществ — вибрация и звук",
    ),
    NotifyModeOption(
        SovaPrefs.NOTIFY_MODE_MESSAGES_ONLY,
        "Уведомления сообщений (вибрация и звук)",
        "Всплывающие только от Сообщений — вибрация и звук; уведомления сообществ скрыты",
    ),
    NotifyModeOption(
        SovaPrefs.NOTIFY_MODE_COMMUNITIES_ONLY,
        "Уведомления сообществ (вибрация и звук)",
        "Всплывающие только от Сообществ — вибрация и звук; уведомления сообщений скрыты",
    ),
    NotifyModeOption(
        SovaPrefs.NOTIFY_MODE_SILENT,
        "Тихий режим (всплывающие сообщения без звука и вибрации)",
        "Всплывающие только от Сообщений, но без звука и вибрации",
    ),
)

/** Короткое имя режима для подстроки закреплённой кнопки. */
private fun notifyModeLabel(mode: Int): String {
    val option = NOTIFY_MODE_OPTIONS.firstOrNull { it.mode == mode }
    if (option != null) return option.title
    return "Уведомления Сообщений и Сообществ"  // дефолт = NOTIFY_MODE_ALL
}

/**
 * Закреплённый блок «Режим уведомлений»: кнопка текущего режима +
 * AlertDialog выбора из [NOTIFY_MODE_OPTIONS]. Размещается в шапке глобального
 * drawer (SovaNavHost) — виден всегда, вне прокрутки. Компонент сам владеет
 * internal-состоянием диалога и рендерит его.
 *
 * @param notifyMode текущий режим уведомлений (SovaPrefs.Snapshot.notifyMode,
 *                   значения SovaPrefs.NOTIFY_MODE_*) — из реактивного снапшота.
 * @param onNotifyModeSelected(mode) выбор режима — вызывающая сторона пишет в
 *                   SovaPrefs.setNotifyMode; значение применяется без перезапуска
 *                   (SovaApp и VkNotificationsNotifier читают актуальный снапшот).
 */
@Composable
fun NotifyModeItem(
    notifyMode: Int,
    onNotifyModeSelected: (Int) -> Unit,
) {
    var notifyDialogOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable {
                    AppLog.i("NotifyModeItem", "notify modes: open dialog (current=$notifyMode)")
                    notifyDialogOpen = true
                }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Режим уведомлений",
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = notifyModeLabel(notifyMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Изменить режим уведомлений",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        HorizontalDivider()
    }

    // Диалог выбора режима уведомлений. 4 RadioButton-опции с пояснениями
    // (те же названия/описания, что в Настройки → Уведомления →
    // «Режимы уведомлений»). Выбор применяется НЕМЕДЛЕННО (SovaNavHost пишет
    // в SovaPrefs.setNotifyMode — снапшот реактивный, пуш-логика подхватит
    // без перезапуска) и диалог закрывается.
    if (notifyDialogOpen) {
        AlertDialog(
            onDismissRequest = { notifyDialogOpen = false },
            title = { Text("Режим уведомлений") },
            text = {
                Column {
                    NOTIFY_MODE_OPTIONS.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    AppLog.i("NotifyModeItem", "notify mode select: ${option.mode} (${option.title})")
                                    onNotifyModeSelected(option.mode)
                                    notifyDialogOpen = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = notifyMode == option.mode,
                                onClick = {
                                    AppLog.i("NotifyModeItem", "notify mode select: ${option.mode} (${option.title})")
                                    onNotifyModeSelected(option.mode)
                                    notifyDialogOpen = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}
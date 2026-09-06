package re.pinok.ui.screens.calls

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import re.pinok.feature.calls.CallHistoryEntry

/** Максимум плиток панели — как превью «Последние звонки» на «Главной». */
private const val FC_PANEL_MAX_TILES = 5

/**
 * Плитка панели быстрых звонковых чатов (REV-UI §1.5 FCThumb): последний
 * звонок с данным peer из HISTORY-состояния репозитория раздела. Источник
 * данных — реальный: записи calls.getHistory с chat/peer-данными
 * (CallHistoryEntry); плитки без реального источника НЕ рендерятся (no-stub).
 *
 * callId — открытие чата звонка (CallChatScreen, callsGetConversationByCall);
 * recordId/groupId — «Убрать чат из списка» (крестик): репозиторий
 * removeFromHistory → callsDeleteHistoryRecords{record_ids} либо
 * callsDeleteGroupHistoryRecords{record_ids, group_id} при groupId>0 +
 * форс-обновление HISTORY/MISSED. recordId=0 (desktop-запись с синтетическим
 * callId) — крестик честно отключён (адресации удаления нет — как пункт
 * «Убрать из списка» меню строки Этапа Б3).
 */
internal data class FastChatTileData(
    val callId: String,
    val peerId: Long,
    val name: String,
    val photo: String?,
    val recordId: Long,
    val groupId: Long,
)

/**
 * Дедупликация записей истории в плитки: по одному тайлу на peer (последний —
 * самый свежий — звонок с ним, entries идут от свежих к старым), максимум
 * [FC_PANEL_MAX_TILES]. Записи без peer (peerId=0) пропускаются — чата для
 * плитки не существует.
 */
internal fun buildFastChatTiles(entries: List<CallHistoryEntry>): List<FastChatTileData> {
    val out = ArrayList<FastChatTileData>()
    val seen = HashSet<Long>()
    for (e in entries) {
        if (e.peerId <= 0L) continue
        if (seen.contains(e.peerId)) continue
        seen.add(e.peerId)
        out.add(
            FastChatTileData(
                callId = e.callId,
                peerId = e.peerId,
                name = e.name,
                photo = e.photo,
                recordId = e.recordId,
                groupId = e.groupId,
            ),
        )
        if (out.size >= FC_PANEL_MAX_TILES) break
    }
    return out
}

/**
 * #CALLS-SNAP (2026-09-05): Этап Д плана «звонки.перенос.план.md» — FCPanel,
 * панель быстрых звонковых чатов (REV-UI §1.5, общая для S1-S6; в PinoK
 * размещена на «Главной» раздела над «Последними звонками»).
 *
 * Состав по срезу: список плиток (FCPanel__list), переключатель ширины
 * (FCPanel__widthToggle, aria «Развернуть» — реальное состояние collapsed:
 * аватар 48dp / expanded: аватар 64dp + имя), крестик плитки
 * (FCThumb__close, aria «Убрать чат из списка» — верхний угол плитки).
 *
 * Честные отклонения (no-stub):
 *  - кнопка FCPanel__add НЕ рендерится: реального источника «добавить чат»
 *    нет (выбор диалога мессенджера требует messages.getConversations, его в
 *    фасаде нет; источник панели — история звонков);
 *  - persist открытых плиток (localStorage reforged-storage-db-v1-*-fc-heads)
 *    недоступен: SovaPrefs.kt — запретный файл этапа; состав панели
 *    выводится из HISTORY-состояния репозитория (свежие звонки = открытые
 *    чаты), после удаления записи панель обновляется тем же refresh'ем;
 *  - tiles.isEmpty() → панель не рендерится (нет реальных данных).
 */
@Composable
internal fun CallsFastChatsPanel(
    tiles: List<FastChatTileData>,
    onOpenChat: (FastChatTileData) -> Unit,
    onRemoveChat: (FastChatTileData) -> Unit,
) {
    if (tiles.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Быстрые звонковые чаты",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("fc_panel_width_toggle"),
            ) {
                Text(if (expanded) "Свернуть" else "Развернуть")
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("fc_panel_list"),
        ) {
            items(tiles, key = { "fc_tile_" + it.peerId }) { tile ->
                FastChatTile(
                    tile = tile,
                    expanded = expanded,
                    onOpenChat = onOpenChat,
                    onRemoveChat = onRemoveChat,
                )
            }
        }
    }
}

/**
 * Плитка (FCThumb--collapsed): аватар чата = кнопка открытия чата
 * (FCThumb__link, aria = имя чата) + крестик «Убрать чат из списка»
 * (FCThumb__close) в верхнем углу плитки; крестик отключён при
 * recordId=0 (нет числового id записи — удалять нечем, честно).
 */
@Composable
private fun FastChatTile(
    tile: FastChatTileData,
    expanded: Boolean,
    onOpenChat: (FastChatTileData) -> Unit,
    onRemoveChat: (FastChatTileData) -> Unit,
) {
    val avatarSize = if (expanded) 64.dp else 48.dp
    Column(
        modifier = Modifier.width(if (expanded) 88.dp else 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier.size(avatarSize).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    .clickable(onClick = { onOpenChat(tile) })
                    .testTag("fc_thumb_link"),
                contentAlignment = Alignment.Center,
            ) {
                val ph = tile.photo
                if (ph != null) {
                    AsyncImage(
                        model = ph,
                        contentDescription = tile.name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(tile.name.take(1), fontWeight = FontWeight.Bold)
                }
            }
            IconButton(
                onClick = { onRemoveChat(tile) },
                enabled = tile.recordId > 0L,
                modifier = Modifier.align(Alignment.TopStart).size(20.dp).testTag("fc_thumb_close"),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Убрать чат из списка",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(
                tile.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

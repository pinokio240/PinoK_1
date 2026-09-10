package re.pinok.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import re.pinok.ui.navigation.LocalBottomBarVisible

/**
 * Единая FAB-стрелка «наверх» для всех экранов с длинной (бесконечной)
 * пагинацией.
 *
 * WHY Fix #389 #SCROLL-TOP-PARITY: кнопка «наверх» была только на
 * FeedScreen/MusicScreen/MessagesScreen/ChatDetailScreen — остальные экраны с
 * бесконечной пагинацией (уведомления, видео, стены, закладки, поиск, друзья,
 * участники, документы, чёрный список…) заставляли юзера скроллить сотни
 * позиций вручную. Копипаста FAB-кода из FeedScreen в каждый экран плодила бы
 * 15 копий — теперь компонент один: вставляется одним вызовом в Box-оверлей
 * экрана, alignment задаёт вызывающий (компонент — просто FAB, НЕ Box).
 *
 * Механика показа — по паттерну FeedScreen (#238 + #FEED-FAB-SYNC):
 *  - скролл ниже 7-го item (firstVisibleItemIndex > 6) → показать;
 *  - нижняя панель скрыта скроллом (LocalBottomBarVisible == false) → показать
 *    сразу, не дожидаясь порога item'ов (тот же «слепой» рассинхрон
 *    порогов панели (24px) и FAB, что чинил #238: панель уже спрятана,
 *    а кнопки ещё нет);
 *  - тап → поднимаем нижнюю панель ЯВНО (animateScrollToItem нагенерит
 *    scroll-дельты только пока идёт анимация — на коротких списках панель
 *    осталась бы скрытой после возврата наверх) + плавная прокрутка к item 0.
 *
 * @param listState состояние lazy-списка экрана — ТЕМ ЖЕ экземпляром, что
 *   передан в LazyColumn(state = ...): из него читается позиция и выполняется
 *   прокрутка.
 * @param visibleExtraGate дополнительный гейт вызывающего экрана (список не
 *   пуст, активна вкладка с пагинацией и т.п.); false → FAB не показывается.
 *   Передаётся в remember-ключ — изменение гейта пересоздаёт derived-состояние.
 */
@Composable
fun ScrollToTopFab(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    visibleExtraGate: Boolean = true,
) {
    // #SCROLL-TOP-PARITY: синхронизация с hide-on-scroll нижней панелью —
    // SovaNavHost пробрасывает State через CompositionLocal (паттерн
    // #FEED-FAB-SYNC из FeedScreen). Дефолт compositionLocal = true (панель
    // видима) — безопасно для превью/тестов без provider'а.
    val bottomBarVisibleState = LocalBottomBarVisible.current
    // remember(visibleExtraGate): derivedStateOf захватывает гейт по значению
    // на момент создания — без ключа в remember он «застыл» бы на первом.
    val showFab by remember(visibleExtraGate) {
        derivedStateOf {
            visibleExtraGate &&
                (!bottomBarVisibleState.value || listState.firstVisibleItemIndex > 6)
        }
    }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = showFab,
        enter = fadeIn(tween(150)) + scaleIn(tween(150)),
        exit = fadeOut(tween(150)) + scaleOut(tween(150)),
        modifier = modifier,
    ) {
        FloatingActionButton(
            onClick = {
                bottomBarVisibleState.value = true
                // Явный if (NULL-ЯВНО): прокрутку по пустому списку даже не
                // планируем — FAB теоретически может мелькнуть через
                // bottom-bar-гейт до загрузки первой страницы.
                if (listState.layoutInfo.totalItemsCount > 0) {
                    scope.launch { listState.animateScrollToItem(0) }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Наверх",
            )
        }
    }
}

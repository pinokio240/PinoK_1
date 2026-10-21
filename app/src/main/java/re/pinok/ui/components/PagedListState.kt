package re.pinok.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * ЕДИНЫЙ паттерн пагинации (эталон — FeedScreen, вариант A Fix #83).
 *
 * Экраны с бесконечным списком (лена/стена/видео/файлы/…) вместо копипасты
 * собственных `*LoadingMore`/`*EndReached`-флагов + snapshotFlow-триггеров
 * используют одну сущность: [PagedListState], созданную через
 * [rememberPagedListState], + общий триггер [PagingTrigger] и общий футер
 * [PagingFooter].
 *
 * Ключевые правила эталона:
 *  - Конец списка задаёт СЕРВЕРНЫЙ курсор: `endReached = (курсор == null)`,
 *    а НЕ `size < pageSize` (последний штрафует неполные страницы на конце).
 *  - `endReached`/`loadingMore`-флаги — внутри состояния, экран их не заводит.
 *  - Триггер НЕ ставит `items.size` в ключ `LaunchedEffect` — иначе каждый
 *    `loadMore()` перезапускает эффект, snapshotFlow излучает true заново и
 *    получается бесконечный цикл запросов → VK Flood control (error 9).
 */
interface PagedCursor

/**
 * Курсор «offset». offset увеличивается на РАЗМЕР СТРАНИЦЫ (page.size),
 * а не на `items.size` — иначе при дедупликации/мягком удалении курсор
 * «разъезжается» с серверным смещением и часть страниц перетеряется.
 */
class ServerOffset(var offset: Int) : PagedCursor

/**
 * Курсор «start_from/next_from» (строка). Конец списка при value == null.
 */
class ServerNextFrom(var value: String?) : PagedCursor

/**
 * Состояние бесконечного списка с серверной пагинацией.
 *
 * @param fetcher загрузчик одной страницы. Первый вызов получает null
 *   (первая страница), последующие — не-null курсор из предыдущего ответа.
 *   Возвращает `(элементы страницы, следующий курсор)`; следующий курсор
 *   == null означает «больше нет — конец списка».
 * @param keyOf ключ элемента для дедупликации при догрузке и для `key` в
 *   LazyColumn. Отдаёт строку вида `"${it.ownerId}_${it.id}"` или число.
 */
class PagedListState<T : Any>(
    private val fetcher: suspend (PagedCursor?) -> Pair<List<T>, PagedCursor?>,
    private val keyOf: (T) -> Any = { it.hashCode() },
) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set
    /** Текущий серверный курсор; null = больше нечего грузить. */
    var cursor by mutableStateOf<PagedCursor?>(null)
        private set
    /** Идёт загрузка ПЕРВОЙ страницы (фулл-спиннер). */
    var loading by mutableStateOf(false)
        private set
    /** Идёт догрузка следующей страницы по скроллу. */
    var loadingMore by mutableStateOf(false)
        private set
    /** Достигнут конец списка (серверный курсор == null). */
    var endReached by mutableStateOf(false)
        private set
    /** Строка ошибки последнего запроса, либо null. */
    var error by mutableStateOf<String?>(null)
        private set

    /** Загрузка первой страницы: сброс состояния + опрос fetcher(null). */
    suspend fun loadFirst() {
        if (loading) return
        loading = true
        loadingMore = false
        endReached = false
        error = null
        try {
            val (firstPage, newCursor) = fetcher(null)
            items = firstPage.distinctBy { keyOf(it) }
            cursor = newCursor
            // Конец = серверный курсор пришёл пустым (а не size < pageSize).
            endReached = newCursor == null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    /** Догрузка следующей страницы. Гард: не грузим повторно/после конца/без курсора. */
    suspend fun loadMore() {
        if (loadingMore || endReached) return
        val c = cursor
        if (c == null) {
            // Первая страница ещё не загружалась (ошибка на старте) и список пуст —
            // «Повторить» из PagingFooter пробует заново с первой страницы.
            if (items.isEmpty()) loadFirst()
            return
        }
        loadingMore = true
        try {
            val (page, newCursor) = fetcher(c)
            // Дедупликация против уже загруженных + от случайных дублей страницы.
            val newItems = page.filter { np -> items.none { keyOf(it) == keyOf(np) } }
            if (newItems.isNotEmpty()) {
                items = (items + newItems).distinctBy { keyOf(it) }
            }
            cursor = newCursor
            // Конец списка задаёт ТОЛЬКО серверный курсор (newCursor == null).
            // НЕ ставим конец по `newItems.isEmpty()`: пустая страница после
            // дедуп-фильтра ещё не означает, что данных нет — серверный курсор
            // продолжает указывать на следующую страницу, по которой триггер
            // догрузит дальше. Иначе «Это все …» появляется раньше времени.
            if (newCursor == null) endReached = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            loadingMore = false
        }
    }

    /** Полный сброс (перезаход на экран, смена сущности). */
    fun reset() {
        items = emptyList()
        cursor = null
        loading = false
        loadingMore = false
        endReached = false
        error = null
    }

    /** Мягкое удаление элемента по его [keyOf]-ключу (оптимистичный soft-remove). */
    fun removeById(id: Any) {
        items = items.filterNot { keyOf(it) == id }
    }
}

/**
 * Композ-фабрика состояния пагинации.
 *
 * @param rememberKey ключ для `remember`: при смене значения (например,
 *   другой groupId на том же экране) состояние пересоздаётся — список и
 *   курсор сбрасываются. По умолчанию [Unit] (создаётся один раз).
 */
@Composable
fun <T : Any> rememberPagedListState(
    fetcher: suspend (PagedCursor?) -> Pair<List<T>, PagedCursor?>,
    keyOf: (T) -> Any = { it.hashCode() },
    rememberKey: Any = Unit,
): PagedListState<T> = remember(rememberKey) {
    PagedListState(fetcher = fetcher, keyOf = keyOf)
}

/**
 * КАНОНИЧЕСКИЙ триггер бесконечной пагинации — детект скролла к концу.
 *
 * Правила (эталон FeedScreen, Fix #83):
 *  - `state.items` и `totalItemsCount` читаются ВНУТРИ snapshotFlow (Compose
 *    snapshot reads), а НЕ в ключе `LaunchedEffect`. Иначе каждый loadMore()
 *    пересоздаёт эффект → snapshotFlow снова излучает true → бесконечный цикл
 *    запросов (VK error 9).
 *  - `enabled` (гейт вкладки) В КЛЮЧЕ: он меняется редко (переключение вкладки),
 *    поэтому безопасен и не даёт списку расти. Если бы гейт жил только в теле
 *    эффекта со стабильным ключом, Compose НЕ перезапускал бы эффект при смене
 *    вкладки и замыкание держало бы устаревший `enabled`.
 *  - Конец определяется по `totalItemsCount` (учитывает header/табы/footer
 *    РАЗДЕЛЯЕМОГО списка; `items.size` в одиночку верен только когда элементы —
 *    почти весь список).
 */
@Composable
fun <T : Any> PagingTrigger(
    listState: LazyListState,
    state: PagedListState<T>,
    enabled: Boolean = true,
) {
    LaunchedEffect(listState, enabled) {
        snapshotFlow {
            if (!enabled) return@snapshotFlow false
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= layoutInfo.totalItemsCount - 3 && state.items.isNotEmpty()
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { state.loadMore() }
    }
}

/**
 * Общий футер пагинации. Ставится последним `item {}` блока списка.
 *  - loadingMore → спиннер;
 *  - error != null → текст ошибки + кнопка «Повторить»;
 *  - endReached и список непуст → «Это все …».
 */
@Composable
fun <T : Any> PagingFooter(
    state: PagedListState<T>,
    emptyText: String = "Это все",
) {
    val scope = rememberCoroutineScope()
    when {
        state.loadingMore -> {
            Box(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        state.error != null -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { scope.launch { state.loadMore() } },
                ) { Text("Повторить") }
            }
        }
        state.endReached && state.items.isNotEmpty() -> {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}
package re.pinok.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Fix #256: Единый TopBar для всех главных экранов.
 *
 * Раньше NotificationsScreen имел СОБСТВЕННЫЙ Scaffold.topBar с заголовком
 * «Уведомления» + поиск + фильтр. Поверх него рисовался ГЛОБАЛЬНЫЙ TopAppBar
 * из SovaNavHost (hamburger + «Уведомления»). Юзер видел ДВЕ панели с одинаковым
 * заголовком. Теперь глобальный TopAppBar один, а экраны регистрируют свои
 * действия (search, filter, etc.) через [ScreenTopBar].
 *
 * ## Архитектура
 *
 * 1. SovaNavHost владеет единственным `TopAppBar`.
 * 2. Когда экран активен, он вызывает `ScreenTopBar.configure { ... }` в
 *    `DisposableEffect` — передаёт actions и/или titleOverride.
 *    Метод возвращает уникальный токен-владельца.
 * 3. При уходе экрана — `ScreenTopBar.clear(token)` в `onDispose`.
 *    clear(token) сносит конфигурацию ТОЛЬКО если token всё ещё активен
 *    (никто другой не перезаписал). Это решает race condition:
 *    навигация A → B: B.configure() → A.onDispose.clear(tokenA) —
 *    tokenA уже не активен (B перезаписал), clear = no-op, конфиг B живёт.
 * 4. SovaNavHost читает `ScreenTopBar.actions` / `titleOverride` и рендерит.
 *
 * ## Race condition (Fix #262)
 *
 * ДО Fix #262: `clear()` без токена сносил всё безусловно. При навигации
 * A → B порядок событий:
 *   1. B входит → configure() ставит actions B
 *   2. A уходит → onDispose → clear() сносит actions B
 * Результат: на экране B иконка поиска пропадала.
 * ФИКС: configure() возвращает token; clear(token) проверяет владельца.
 */
object ScreenTopBar {

    /**
     * Токен текущего владельца TopBar. Каждый configure() создаёт новый
     * token и сохраняет сюда. clear(token) сравнивает с этим значением.
     */
    private var ownerToken: Any? = null

    /**
     * Экшены для правой части TopAppBar (иконки search, filter, ...).
     * null = показываем дефолтные (ничего).
     */
    var actions: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    /**
     * Кастомный контент для title-слота (например, TextField при активном поиске).
     * null = показываем обычный заголовок (currentTitle).
     */
    var titleOverride: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    /**
     * Кастомный navigationIcon (например, back button вместо hamburger).
     * null = показываем hamburger (drawer).
     */
    var navigationIconOverride: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    /**
     * Дополнительный контент ПОД TopAppBar (например, строка filter-chips).
     * Рисуется SovaNavHost под TopAppBar как отдельная Column-строка.
     * null = ничего.
     */
    var subBar: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    /**
     * Конфигурируем TopBar для текущего экрана.
     * Вызывать в `DisposableEffect` внутри Composable.
     *
     * @param actions правые иконки (search, filter, ...) — null = нет
     * @param titleOverride кастомный title (TextField) — null = обычный заголовок
     * @param navigationIconOverride кастомный nav icon (back) — null = hamburger
     * @param subBar контент под TopAppBar (filter chips) — null = нет
     * @return уникальный токен-владелец. Передать в [clear] при onDispose,
     *         чтобы не снести конфигурацию следующего экрана.
     */
    fun configure(
        actions: (@Composable () -> Unit)? = null,
        titleOverride: (@Composable () -> Unit)? = null,
        navigationIconOverride: (@Composable () -> Unit)? = null,
        subBar: (@Composable () -> Unit)? = null,
    ): Any {
        val token = Any()
        ownerToken = token
        this.actions = actions
        this.titleOverride = titleOverride
        this.navigationIconOverride = navigationIconOverride
        this.subBar = subBar
        return token
    }

    /**
     * Сбрасываем TopBar при уходе экрана.
     * Вызывать в `onDispose` блока `DisposableEffect`.
     *
     * @param token токен, возвращённый [configure]. Если передан и не совпадает
     *        с текущим ownerToken (значит, другой экран уже перезаписал) —
     *        clear = no-op. Не передавай token только для force-clear
     *        (например, safety net в SovaNavHost для hasOwnTopBar экранов).
     */
    fun clear(token: Any? = null) {
        if (token != null && ownerToken !== token) return
        ownerToken = null
        actions = null
        titleOverride = null
        navigationIconOverride = null
        subBar = null
    }
}

package re.pinok.ui.navigation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * #FEED-FAB-SYNC: состояние видимости нижней панели (NavigationBar).
 *
 * SovaNavHost обновляет это состояние при hide-on-scroll (#299):
 *  - true  → панель видна
 *  - false → панель скрыта скроллом вниз (accumulator < -24f)
 *
 * Предоставляется через CompositionLocalProvider внутри Scaffold content,
 * чтобы дочерние экраны могли реагировать на скрытие панели.
 *
 * Применение: FeedScreen показывает FAB «наверх» КАК ТОЛЬКО панель скрылась,
 * а не ждёт порога скролла 200px (#238). Раньше это создавало «слепую зону»
 * 24–200px: нижнее меню уже спрятано (порог 24px), а кнопки «наверх» ещё нет
 * (порог 200px) → пользователь видел «кнопка пропала при скрытии меню».
 *
 * Безопасный дефолт: mutableStateOf(true) — экраны без provider (превью,
 * тесты) считают панель видимой, чтобы не показывать FAB лишний раз.
 */
val LocalBottomBarVisible = compositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(true)
}

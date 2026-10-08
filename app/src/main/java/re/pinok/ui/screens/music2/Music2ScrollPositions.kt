// File: music2/Music2ScrollPositions.kt
// P0.38-POSITION: держатель позиций прокрутки списка треков НА СЕССИЮ.
//
// Проблема: экран Music2Screen пересоздаётся при каждой навигации
// (navigate(Music2Friend) даёт новый backStackEntry). rememberLazyListState()
// живёт только пока экран в композиции — при возврате к другу позиция
// и подгруженный список сбрасываются.
//
// Решение: синглтон вне композиции, хранящий для каждого ключа
// (ownerId == null → «Моя музыка», ownerId → конкретный друг) последнюю
// позицию прокрутки (firstVisibleItemIndex + scrollOffset). Экран читает
// её при входе и восстанавливает через listState.scrollToItem.
//
// Хранится ТОЛЬКО позиция (не сам список треков) — по требованию пользователя:
// «восстанавливать только позицию прокрутки, не сам список». Если ушёл далеко
// (индекс за пределами загруженной первой страницы) — список перезагружается
// с начала и пролистывается до сохранённой позиции с догрузкой страниц.
package re.pinok.ui.screens.music2

/**
 * Сохранённая позиция прокрутки для одного списка (Моей музыки или друга).
 *
 * @param firstVisibleItemIndex индекс первого видимого элемента.
 * @param scrollOffset сдвиг в пикселях внутри этого элемента (точность позиции).
 */
data class Music2ScrollPos(
    val firstVisibleItemIndex: Int = 0,
    val scrollOffset: Int = 0,
)

/**
 * Держатель позиций прокрутки на время сессии.
 * Ключ: ownerId — null означает «Моя музыка» (вкладка 0), конкретное число —
 * экран музыки друга.
 */
object Music2ScrollPositions {
    private val positions = mutableListOf<Pair<Long?, Music2ScrollPos>>()

    /** Сохраняет позицию для ключа [ownerId]. */
    fun save(ownerId: Long?, pos: Music2ScrollPos) {
        positions.removeAll { it.first == ownerId }
        positions.add(ownerId to pos)
    }

    /** Возвращает сохранённую позицию для ключа [ownerId] (или дефолт, если её нет). */
    fun get(ownerId: Long?): Music2ScrollPos {
        return positions.firstOrNull { it.first == ownerId }?.second ?: Music2ScrollPos()
    }

    /** Удаляет позицию для ключа [ownerId] (например, при полном обновлении списка через refresh). */
    fun clear(ownerId: Long? = null) {
        if (ownerId == null) positions.clear() else positions.removeAll { it.first == ownerId }
    }
}
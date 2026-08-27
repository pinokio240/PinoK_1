package re.pinok.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Floating draggable FAB for instant access to the in-app log viewer.
 *
 * Design goals (per user requirements, session #16):
 *  - **Enabled by default** — appears on every screen where the host composable
 *    is rendered (AuthActivity, MainActivity, IM screen, etc.).
 *  - **On top of everything** — host must wrap its content in a [Box] and place
 *    [DraggableLogFab] as the last child. Internally we also use `pointerInput`
 *    so drag events are captured before any underlying scrollable.
 *  - **Free dragging, no snap / no stick** — we do NOT clamp to screen edges
 *    and we do NOT animate to a "resting" position. Where the finger drops the
 *    FAB, there it stays. Soft bounds keep it visible (no clamping to a margin).
 *  - **Tap (without drag) opens the log viewer** — short tap = onClick, drag
 *    beyond a small threshold = move.
 *
 * Position state is retained at the Activity level (not navigation level) so
 * it persists across screen switches within the same Activity.
 *
 * UTF-8 log export is handled by [LogViewerDialog] (long-press / menu action).
 *
 * ⚠️ Используем `awaitEachGesture` вместо `detectDragGestures`, потому что
 * `detectDragGestures` вызывает `awaitFirstDown()` который потребляет
 * down-событие, из-за чего `onClick` FAB-кнопки никогда не срабатывает.
 */
@Composable
fun DraggableLogFab(onClick: () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val fabSizePx = with(density) { 56.dp.toPx() }

    // Initial position: bottom-right corner, with a 16dp inset.
    var offsetX by remember {
        mutableFloatStateOf((screenWidthPx - fabSizePx - with(density) { 16.dp.toPx() }).coerceAtLeast(0f))
    }
    var offsetY by remember {
        mutableFloatStateOf((screenHeightPx - fabSizePx - with(density) { 80.dp.toPx() }).coerceAtLeast(0f))
    }

    // Track total drag distance to distinguish tap from drag.
    var totalDragDistance by remember { mutableFloatStateOf(0f) }
    val tapThresholdPx = with(density) { 8.dp.toPx() } // drag < 8dp = treat as tap

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitPointerEvent() // initial DOWN — consumed below if tap
                    totalDragDistance = 0f

                    var isDragging = false
                    var event = awaitPointerEvent()
                    do {
                        val drag = event.changes.firstOrNull()?.let {
                            androidx.compose.ui.geometry.Offset(
                                it.position.x - it.previousPosition.x,
                                it.position.y - it.previousPosition.y,
                            )
                        } ?: androidx.compose.ui.geometry.Offset.Zero
                        if (drag != androidx.compose.ui.geometry.Offset.Zero) {
                            isDragging = true
                            totalDragDistance += kotlin.math.abs(drag.x) + kotlin.math.abs(drag.y)
                            val newX = (offsetX + drag.x).coerceIn(-fabSizePx * 0.4f, screenWidthPx - fabSizePx * 0.6f)
                            val newY = (offsetY + drag.y).coerceIn(-fabSizePx * 0.4f, screenHeightPx - fabSizePx * 0.6f)
                            offsetX = newX
                            offsetY = newY
                            // Потребляем событие перетаскивания, чтобы underlying scroll
                            // не реагировал и onClick FAB не срабатывал параллельно.
                            event.changes.forEach { it.consume() }
                        }
                        if (event.changes.any { it.pressed }) {
                            event = awaitPointerEvent()
                        }
                    } while (event.changes.any { it.pressed })

                    // Если почти не двигались — трактуем как тап.
                    // ВАЖНО: потребляем down/up, чтобы SmallFloatingActionButton.onClick
                    // не сработал повторно (audit High #4 — двойной onClick).
                    if (!isDragging || totalDragDistance < tapThresholdPx) {
                        event.changes.forEach { it.consume() }
                        onClick()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SmallFloatingActionButton(
            // onClick пустой — реальный клик обрабатывается в pointerInput выше.
            // Это устраняет двойной вызов (audit High #4).
            onClick = {},
            shape = CircleShape,
            // #32: tertiaryContainer слишком тёмный в тёмной теме — не виден.
            // Используем primary (контрастный) с тенью elevation для видимости на любом фоне.
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 8.dp,
            ),
        ) {
            Icon(Icons.Default.BugReport, contentDescription = "Логи")
        }
    }
}
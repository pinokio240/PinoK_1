package re.pinok.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global observable state for the in-app log viewer dialog.
 *
 * Allows any screen to open the log viewer via [show] without needing access
 * to a navigation controller. The host Activity renders [LogViewerDialog] once
 * at the root level — it listens to this state and shows/hides accordingly.
 *
 * Usage:
 * ```kotlin
 * DraggableLogFab(onClick = { LogDialogState.show() })
 * // ... elsewhere, at the Activity root:
 * LogViewerDialog()
 * ```
 */
object LogDialogState {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun show() { _visible.value = true }
    fun hide() { _visible.value = false }
    fun toggle() { _visible.value = !_visible.value }
}

/**
 * Host-side dialog component — observes [LogDialogState] and renders the log
 * viewer (with UTF-8 export action) when visible.
 *
 * Should be placed once at the Activity root, OUTSIDE any navigation graph
 * so it overlays every screen.
 */
@Composable
fun LogViewerDialog() {
    val visible by LogDialogState.visible.collectAsState()
    if (visible) {
        LogViewerDialogContent(onDismiss = { LogDialogState.hide() })
    }
}

package re.pinok.ui.anim

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Fix #224: глобальный масштаб длительности анимаций интерфейса.
 *
 * Берётся из [re.pinok.data.local.SovaPrefs.Snapshot.interfaceAnimSpeed] (0..100 → /100f):
 *  - 1f  — нормальная скорость (100%)
 *  - 0.5f — вдвое быстрее (50%)
 *  - 0f  — анимации полностью выключены (мгновенные переходы через [snap])
 *
 * Предоставляется через CompositionLocalProvider в SovaNavHost (обёрнуто вокруг
 * Scaffold+NavHost, чтобы покрывать все экраны). Глубокие composable-области
 * (message bubbles, swipe gestures) читают [LocalAnimScale].current через
 * @Composable-хелперы [scaledTween]/[scaledSpring].
 *
 * Для transition-ламбд NavHost (где нет @Composable scope) используется
 * не-композабл [tweenScaled] с явным scale-параметром.
 */
val LocalAnimScale = staticCompositionLocalOf { 1f }

/**
 * Fix #228: масштаб стикер-фото в чате (0..40, % увеличения от оригинала).
 * 0 — исходный размер, 40 — +40% к оригиналу. Берётся из
 * [re.pinok.data.local.SovaPrefs.Snapshot.stickerPhotoScale].
 * Предоставляется через CompositionLocalProvider в ChatDetailScreen,
 * читается в MessageBubble для рендера стикер-фото (isStickerLike).
 */
val LocalStickerPhotoScale = staticCompositionLocalOf { 0 }

/**
 * tween с учётом [LocalAnimScale]. При scale ≤ 0f возвращает [snap] (мгновенно).
 * @Composable — читает [LocalAnimScale].current.
 */
@Composable
fun <T> scaledTween(
    durationMillis: Int = 300,
    easing: Easing = FastOutSlowInEasing,
): FiniteAnimationSpec<T> {
    val scale = LocalAnimScale.current
    return if (scale <= 0f) snap()
    else tween(durationMillis = (durationMillis * scale).toInt().coerceAtLeast(1), easing = easing)
}

/**
 * spring с учётом [LocalAnimScale]. При scale ≤ 0f возвращает [snap] (мгновенно).
 * Иначе — [spring] с пропорционально увеличенной stiffness (быстрее settle при
 * меньшем scale). Spring не длительность-ориентирован, поэтому «скорость»
 * аппроксимируется через stiffness.
 */
@Composable
fun <T> scaledSpring(
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = Spring.StiffnessMedium,
): FiniteAnimationSpec<T> {
    val scale = LocalAnimScale.current
    return if (scale <= 0f) snap()
    else spring(dampingRatio = dampingRatio, stiffness = (stiffness / scale.coerceAtLeast(0.15f)))
}

/**
 * Не-@Composable вариант [scaledTween] для transition-ламбд NavHost
 * (AnimatedContentTransitionScope.() -> EnterTransition не имеет Composable scope).
 * scale читается один раз в родительском @Composable и передаётся явно.
 */
fun <T> tweenScaled(
    scale: Float,
    durationMillis: Int = 300,
    easing: Easing = FastOutSlowInEasing,
): FiniteAnimationSpec<T> = if (scale <= 0f) snap()
else tween(durationMillis = (durationMillis * scale).toInt().coerceAtLeast(1), easing = easing)

/**
 * Не-@Composable вариант [scaledSpring] для gesture-колбэков
 * (detectHorizontalDragGestures onDragEnd/onDragCancel — не @Composable scope).
 * scale читается в родительском @Composable (через LocalAnimScale.current) и
 * передаётся явно. При scale ≤ 0f → snap (мгновенно), иначе spring с stiffness/scale.
 */
fun <T> springScaled(
    scale: Float,
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = Spring.StiffnessMedium,
): FiniteAnimationSpec<T> = if (scale <= 0f) snap()
else spring(dampingRatio = dampingRatio, stiffness = (stiffness / scale.coerceAtLeast(0.15f)))

package re.pinok.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val LocalSovaAccent = compositionLocalOf { SovaColors.Black }

/**
 * SOVA 2.0 theme — B&W minimalist base, swappable accent color,
 * optional Material You dynamic colors.
 *
 * Inspired by VK's ColorScheme system (VK Light / VK Dark / VK Blue),
 * but preconfigured for the SOVA "B&W + accent" look.
 *
 * fontScale: целочисленный масштаб шрифта в процентах (70..150).
 *   100 = системный размер, <100 = мельче, >100 = крупнее.
 *   Реализован через переопределение LocalDensity (fontScale множитель)
 *   И scaled-копию SovaTypography (на случай прямых .sp без MaterialTheme).
 *
 * #MONET-DYNAMIC-COLOR: Material You / Monet — адаптивная цветовая тема,
 * доступна ТОЛЬКО на Android 12+ (API 31, S). На более старых версиях
 * Android система НЕ извлекает цвета из обоев — SystemColors API
 * отсутствует. Compose Material3 dynamicLightColorScheme/dynamicDarkColorScheme
 * помечены @RequiresApi(Build.VERSION_CODES.S), поэтому вызов ниже API 31
 * либо невозможен (если не защищён), либо вернёт fallback colorScheme
 * (НЕ настоящие dynamic colors).
 *
 * На Android < 12 мы игнорируем dynamicColor=true и используем статичную
 * B&W схему с пользовательским accent color. Пользователь видит в настройках
 * что Material You переключатель доступен, но если он на старом Android —
 * переключатель бесполезен (можно отключить визуально, но это усложнит UI;
 * лучше оставить включаемым с silent fallback).
 *
 * Источник: https://developer.android.com/develop/ui/views/theming/dynamic-colors
 */
@Composable
fun SOVATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    monetHybrid: Boolean = false,
    accentIndex: Int = 0,
    fontScale: Int = 100,
    content: @Composable () -> Unit,
) {
    val accent = SovaColors.accents.getOrElse(accentIndex) { SovaColors.Black }
    val white = Color.White

    // #MONET-DYNAMIC-COLOR: dynamic colors только на Android 12+ (API 31, S).
    // На более старых версиях dynamicLightColorScheme/dynamicDarkColorScheme
    // либо crash (нет @RequiresApi guard), либо возвращают fallback. Мы
    // явно проверяем SDK_INT — на Android < 12 dynamicColor silent-отключается.
    val canUseDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // #MONET-HYBRID: гибридный режим Material You.
    // Проблема: dynamicLightColorScheme/dynamicDarkColorScheme возвращают
    //   ПОЛНОСТЬЮ обоевую схему — primary/secondary/tertiary = цвета из обоев,
    //   а НЕ пользовательский accent. Все Material3-компоненты (Switch, Button,
    //   FAB, ProgressIndicator и т.п. через MaterialTheme.colorScheme.primary)
    //   перекрашиваются под обои, и выбранный accent теряется.
    // Гибрид: берём dynamic-схему, но .copy() оверрайдим accent-роли (primary,
    //   secondary, tertiary, onPrimary, onSecondary, onTertiary) = accent.
    //   Surface/background/surfaceVariant остаются от обоев. Так активные
    //   элементы (кнопки, свитчи, прогресс) = accent, а подложка = обои.
    // При monetHybrid=false — чистый Monet (все роли = обои), как раньше.
    val colorScheme = when {
        canUseDynamicColor -> {
            val ctx = LocalContext.current
            val dyn = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            if (monetHybrid) {
                // Вычисляем контрастный onPrimary: accent может быть тёмным
                // или светлым — для readability текста на accent-кнопке.
                val onAccent = if (accent.luminance() > 0.5f) SovaColors.Black else white
                dyn.copy(
                    primary = accent,
                    onPrimary = onAccent,
                    secondary = accent,
                    onSecondary = onAccent,
                    tertiary = accent,
                    onTertiary = onAccent,
                    // inversePrimary оставляем от обоев — для inverse-поверхностей.
                )
            } else {
                dyn
            }
        }
        darkTheme -> darkColorScheme(
            primary = accent,
            onPrimary = white,
            secondary = accent,
            onSecondary = white,
            tertiary = accent,
            background = Color(0xFF121212),       // VK desktop: page background
            onBackground = white,
            surface = Color(0xFF1E1E1E),          // VK desktop: card/surface
            onSurface = white,
            surfaceVariant = Color(0xFF2A2A2A),  // VK: slightly lighter surface
            onSurfaceVariant = Color(0xFF999999), // VK: secondary text
            surfaceContainer = Color(0xFF1E1E1E),
            surfaceContainerLow = Color(0xFF1A1A1A),
            surfaceContainerHigh = Color(0xFF2C2C2C),
            error = Color(0xFFCF6679),
            outline = Color(0xFF444444),
        )
        else -> lightColorScheme(
            primary = accent,
            onPrimary = white,
            secondary = accent,
            onSecondary = white,
            tertiary = accent,
            background = white,
            onBackground = SovaColors.Black,
            surface = white,
            onSurface = SovaColors.Black,
            surfaceVariant = Color(0xFFF5F5F5),
            onSurfaceVariant = Color(0xFF333333),
            error = Color(0xFFB00020),
            outline = Color(0xFFCCCCCC),
        )
    }

    // Масштаб шрифта: переопределяем LocalDensity, чтобы fontScale применился
    // ко всем sp-значениям глобально (включая явные .sp вне MaterialTheme.typography).
    // Важно: сохраняем исходный density (x dpi), меняем только fontScale.
    val original = LocalDensity.current
    val scaledDensity = if (fontScale == 100) {
        original
    } else {
        Density(
            density = original.density,
            fontScale = original.fontScale * (fontScale / 100f),
        )
    }
    val scaledTypography = if (fontScale == 100) SovaTypography else scaleTypography(SovaTypography, fontScale)

    CompositionLocalProvider(
        LocalSovaAccent provides accent,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            content = content,
        )
    }
}

/**
 * Возвращает копию Typography с пересчитанными fontSize/lineHeight.
 * Нужна как страховка для компонентов, которые берут TextStyle напрямую
 * из MaterialTheme.typography, а не через LocalDensity (редкие случаи).
 */
private fun scaleTypography(src: Typography, scalePercent: Int): Typography {
    val k = scalePercent / 100f
    fun TextStyle.scale() = copy(
        fontSize = (fontSize.value * k).sp,
        lineHeight = (lineHeight.value * k).sp,
    )
    return Typography(
        displayLarge   = src.displayLarge.scale(),
        displayMedium  = src.displayMedium.scale(),
        displaySmall   = src.displaySmall.scale(),
        headlineLarge  = src.headlineLarge.scale(),
        headlineMedium = src.headlineMedium.scale(),
        headlineSmall  = src.headlineSmall.scale(),
        titleLarge     = src.titleLarge.scale(),
        titleMedium    = src.titleMedium.scale(),
        titleSmall     = src.titleSmall.scale(),
        bodyLarge      = src.bodyLarge.scale(),
        bodyMedium     = src.bodyMedium.scale(),
        bodySmall      = src.bodySmall.scale(),
        labelLarge     = src.labelLarge.scale(),
        labelMedium    = src.labelMedium.scale(),
        labelSmall     = src.labelSmall.scale(),
    )
}

/** Read the current accent color from any composable. */
object SovaTheme {
    val accent: Color
        @Composable get() = LocalSovaAccent.current
}

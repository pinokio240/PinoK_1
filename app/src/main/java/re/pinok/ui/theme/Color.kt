package re.pinok.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SOVA 2.0 color palette — B&W minimalist base + 10 accent colors.
 *
 * Mirrors the original SOVA V RE accent set (SovaBlack, SovaRed, etc.)
 * but rebuilt with Compose Color values.
 */
object SovaColors {

    // Base B&W (always present)
    val Black   = Color(0xFF000000)
    val White   = Color(0xFFFFFFFF)
    val Gray    = Color(0xFF888888)
    val Light   = Color(0xFFEEEEEE)
    val Dark    = Color(0xFF111111)

    // 10 accent colors (matches SOVA V RE 1.2.1)
    val accents: List<Color> = listOf(
        Color(0xFF000000), // 0: Pure Black (default)
        Color(0xFFE53935), // 1: Red
        Color(0xFF1E88E5), // 2: Blue
        Color(0xFF43A047), // 3: Green
        Color(0xFFFB8C00), // 4: Orange
        Color(0xFF8E24AA), // 5: Purple
        Color(0xFF00ACC1), // 6: Cyan
        Color(0xFF6D4C41), // 7: Brown
        Color(0xFFC0CA33), // 8: Lime
        Color(0xFFEC407A), // 9: Pink
    )

    val accentNames: List<String> = listOf(
        "Black", "Red", "Blue", "Green", "Orange",
        "Purple", "Cyan", "Brown", "Lime", "Pink",
    )
}

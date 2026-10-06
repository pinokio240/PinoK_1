package re.pinok.ui.theme

import androidx.compose.ui.graphics.Color

// SOVA 2.0 color palette — B&W minimalist base + VK accents + 10 accent colors.
//
// Mirrors the original SOVA V RE accent set (SovaBlack, SovaRed, etc.)
// but rebuilt with Compose Color values.
//
// P0.21 #VK-ACCENTS (2026-10): добавлены VK-фирменные акценты в НАЧАЛО списка —
// становятся первыми при переключении акцента цвета в настройках (slider).
// Историческая палитра VK (от старой к современной):
//   - VK Classic 2016-2022: #4A76A8 (классический «вкотый» синий)
//   - VK Modern 2022+: #0077FF (ярко-синий, «цифровой»)
//   - VK Pink/Red (редкий акцент из VK Donut/Pro): #FF3347
// Фирменные цвета выбраны по официальной VK Brand Guidelines (vk.design).
object SovaColors {

    // Base B&W (always present)
    val Black   = Color(0xFF000000)
    val White   = Color(0xFFFFFFFF)
    val Gray    = Color(0xFF888888)
    val Light   = Color(0xFFEEEEEE)
    val Dark    = Color(0xFF111111)

    // P0.21: VK-фирменные акценты в НАЧАЛЕ списка (indices 0-2).
    // Это первые цвета при переключении акцента в настройках (slider start).
    val VK_MODERN   = Color(0xFF0077FF)  // VK 2022+ «цифровой» синий (основной акцент VK)
    val VK_CLASSIC  = Color(0xFF4A76A8)  // VK 2016-2022 классический «вкотый» синий
    val VK_PINK     = Color(0xFFE033AC)  // VK Pink/Pro акцент (Donut/Pro highlights)

    // 13 accent colors: 3 VK + 10 original SOVA (matches SOVA V RE 1.2.1 + VK).
    val accents: List<Color> = listOf(
        Color(0xFF0077FF), // 0: VK Modern (P0.21 — primary VK accent, default)
        Color(0xFF4A76A8), // 1: VK Classic (вкотый синий, 2016-2022)
        Color(0xFFE033AC), // 2: VK Pink (Pro/Donut)
        Color(0xFF000000), // 3: Pure Black (старый default, сдвинут с 0 на 3)
        Color(0xFFE53935), // 4: Red
        Color(0xFF1E88E5), // 5: Blue
        Color(0xFF43A047), // 6: Green
        Color(0xFFFB8C00), // 7: Orange
        Color(0xFF8E24AA), // 8: Purple
        Color(0xFF00ACC1), // 9: Cyan
        Color(0xFF6D4C41), // 10: Brown
        Color(0xFFC0CA33), // 11: Lime
        Color(0xFFEC407A), // 12: Pink
    )

    val accentNames: List<String> = listOf(
        "VK Modern", "VK Classic", "VK Pink",
        "Black", "Red", "Blue", "Green", "Orange",
        "Purple", "Cyan", "Brown", "Lime", "Pink",
    )
}

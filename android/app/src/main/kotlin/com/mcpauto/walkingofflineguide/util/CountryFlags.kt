package com.mcpauto.walkingofflineguide.util

/** ISO 3166-1 alpha-2 → 국기 이모지 */
fun countryFlagEmoji(code: String): String {
    if (code.length != 2) return "🌐"
    val upper = code.uppercase()
    val first = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
    val second = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
    return String(intArrayOf(first, second), 0, 2)
}

package com.example.tetris

import androidx.compose.ui.graphics.Color

class Theme(
    val name: String,
    val bg: List<Color>,
    val board: Color,
    val grid: Color,
    val panel: Color,
    val text: Color,
    val accent: Color,
    val pieces: List<Color>, // I, O, T, S, Z, J, L
)

private fun cs(vararg h: Long) = h.map { Color(it) }

val THEMES = listOf(
    Theme("Néon", cs(0xFF0B0F1E, 0xFF2A0B4F), Color(0xFF080B16), Color(0x1FFFFFFF), Color(0x26FFFFFF),
        Color.White, Color(0xFF00E5FF),
        cs(0xFF00E5FF, 0xFFFFEA00, 0xFFD500F9, 0xFF00E676, 0xFFFF1744, 0xFF2979FF, 0xFFFF9100)),
    Theme("Classique", cs(0xFF263238, 0xFF455A64), Color(0xFF11161A), Color(0x1AFFFFFF), Color(0x26FFFFFF),
        Color.White, Color(0xFFFDD835),
        cs(0xFF00BCD4, 0xFFFDD835, 0xFF8E24AA, 0xFF43A047, 0xFFE53935, 0xFF1E88E5, 0xFFFB8C00)),
    Theme("Océan", cs(0xFF03045E, 0xFF0077B6), Color(0xFF021A33), Color(0x1FFFFFFF), Color(0x2EFFFFFF),
        Color.White, Color(0xFF48CAE4),
        cs(0xFF90E0EF, 0xFFFFD166, 0xFF48CAE4, 0xFF2EC4B6, 0xFFFF6B6B, 0xFF4361EE, 0xFFF4A261)),
    Theme("Sunset", cs(0xFF3D1E6D, 0xFFE4572E), Color(0xFF2A0F3A), Color(0x1FFFFFFF), Color(0x2EFFFFFF),
        Color.White, Color(0xFFFFB703),
        cs(0xFFFFD166, 0xFFFFB703, 0xFFFF6B6B, 0xFFF78C6B, 0xFFEF476F, 0xFF9B5DE5, 0xFFF15BB5)),
    Theme("Rétro", cs(0xFF9BBC0F, 0xFF8BAC0F), Color(0xFF9BBC0F), Color(0x220F380F), Color(0x330F380F),
        Color(0xFF0F380F), Color(0xFF0F380F),
        cs(0xFF0F380F, 0xFF306230, 0xFF1C4A1C, 0xFF0F380F, 0xFF306230, 0xFF1C4A1C, 0xFF0F380F)),
)

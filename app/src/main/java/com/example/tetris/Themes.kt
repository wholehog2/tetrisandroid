package com.example.tetris

import androidx.compose.ui.graphics.Color

class Theme(
    val name: String,
    val wood: Boolean,          // true = fond en planches de bois, sinon dégradé
    val bg: List<Color>,
    val board: Color,
    val grid: Color,
    val well: Color,            // fond foncé des cadres (score, suivant...)
    val plaque: Color,          // « plaque » de titre des cadres
    val onPlaque: Color,
    val text: Color,
    val accent: Color,
    val btn: Color,             // couleur de base des boutons
    val pieces: List<Color>,    // I, O, T, S, Z, J, L
)

private fun cs(vararg h: Long) = h.map { Color(it) }

val THEMES = listOf(
    Theme("Bois", true, cs(0xFFC8A06A, 0xFFBE9560, 0xFFD2AA72, 0xFFC39A63), Color(0xFF3B2A18), Color(0x40000000),
        Color(0xFF3B2A18), Color(0xFF9A6A35), Color.White, Color.White, Color(0xFFFFE600), Color(0xFFC8651B),
        cs(0xFF1FB5E5, 0xFFFFC107, 0xFFB04ED6, 0xFF2ECC1F, 0xFFE53A2A, 0xFF2F6FE0, 0xFFFF8C1A)),
    Theme("Néon", false, cs(0xFF0B0F1E, 0xFF2A0B4F), Color(0xFF080B16), Color(0x1FFFFFFF),
        Color(0xFF0A0D1A), Color(0xFF2B1B5A), Color.White, Color.White, Color(0xFF00E5FF), Color(0xFF5B2DBF),
        cs(0xFF00E5FF, 0xFFFFEA00, 0xFFD500F9, 0xFF00E676, 0xFFFF1744, 0xFF2979FF, 0xFFFF9100)),
    Theme("Classique", false, cs(0xFF263238, 0xFF455A64), Color(0xFF11161A), Color(0x1AFFFFFF),
        Color(0xFF11161A), Color(0xFF37474F), Color.White, Color.White, Color(0xFFFDD835), Color(0xFF607D8B),
        cs(0xFF00BCD4, 0xFFFDD835, 0xFF8E24AA, 0xFF43A047, 0xFFE53935, 0xFF1E88E5, 0xFFFB8C00)),
    Theme("Océan", false, cs(0xFF03045E, 0xFF0077B6), Color(0xFF021A33), Color(0x1FFFFFFF),
        Color(0xFF021A33), Color(0xFF0A4A7A), Color.White, Color.White, Color(0xFF48CAE4), Color(0xFF0F86C4),
        cs(0xFF90E0EF, 0xFFFFD166, 0xFF48CAE4, 0xFF2EC4B6, 0xFFFF6B6B, 0xFF4361EE, 0xFFF4A261)),
    Theme("Forêt", false, cs(0xFF0F2A1B, 0xFF1F5130), Color(0xFF0B1A12), Color(0x1FFFFFFF),
        Color(0xFF0B1A12), Color(0xFF2E6B45), Color.White, Color.White, Color(0xFFB8F27A), Color(0xFF3E8E57),
        cs(0xFF7DE3C4, 0xFFF2E15C, 0xFFB68CFF, 0xFF6FD66A, 0xFFFF7A6B, 0xFF5CA8FF, 0xFFFFA94D)),
    Theme("Lave", false, cs(0xFF1A0A0A, 0xFF5A1209), Color(0xFF120606), Color(0x1FFFFFFF),
        Color(0xFF120606), Color(0xFF7A1F12), Color.White, Color.White, Color(0xFFFFB300), Color(0xFFD84315),
        cs(0xFFFFE082, 0xFFFFC107, 0xFFFF7043, 0xFFFFA726, 0xFFE53935, 0xFFEF9A9A, 0xFFFF8A65)),
    Theme("Bonbon", false, cs(0xFF3A1C5E, 0xFF7B2F8E), Color(0xFF2A1245), Color(0x22FFFFFF),
        Color(0xFF2A1245), Color(0xFF8E3FA6), Color.White, Color.White, Color(0xFFFF80CF), Color(0xFFE0559F),
        cs(0xFF80F0FF, 0xFFFFF176, 0xFFE1A0FF, 0xFF9CFFB0, 0xFFFF7FA8, 0xFF8FA8FF, 0xFFFFB380)),
)

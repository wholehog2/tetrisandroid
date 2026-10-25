package com.example.tetris

import androidx.compose.ui.graphics.Color

/** Un pack de formes du mode FORMES : 8 pièces dessinées en texte ('#' = bloc, '/' = changement de ligne). */
class ShapePack(val name: String, val desc: String, val shapes: List<String>)

/** Les 10 packs du mode FORMES. */
val SHAPE_PACKS: List<ShapePack> = listOf(
    ShapePack(
        "MINI",
        "Des petites pièces faciles à placer : point, domino, trio et tétrominos de base.",
        listOf("#", "##", "###", "#./##", "##/##", "###/.#.", ".##/##.", "##./.##"),
    ),
    ShapePack(
        "PENTOS I",
        "Huit pentominos (5 blocs) : I, L, L miroir, P, T, U, V et Y.",
        listOf("#####", "#.../####", "...#/####", "##/##/#.", "###/.#./.#.", "#.#/###", "#../#../###", ".#../####"),
    ),
    ShapePack(
        "PENTOS II",
        "Les pentominos tordus : F, N, Z, P et Y en version miroir.",
        listOf(".##/##./.#.", "##./.##/.#.", "##../.###", ".###/##..", "##./.#./.##", ".##/.#./##.", "##/##/.#", "..#./####"),
    ),
    ShapePack(
        "GÉANTS",
        "De grosses pièces de 6 à 9 blocs. Peu de pièces, beaucoup de place prise !",
        listOf("..#../.###./#####", "#..#/####", "#.../#.../####", "###/.#./.#./.#.", "###/###/#..", "#.#.#/#####", "####./.####", "####/.##./.##."),
    ),
    ShapePack(
        "HEXOS",
        "Huit hexominos : des pièces de 6 blocs aux formes inattendues.",
        listOf("######", "##/##/#./#.", "###/#../#../#..", "##../.##./..##", ".#./###/#.#", "##./.##/..#/..#", ".###/###.", "#./##/##/#."),
    ),
    ShapePack(
        "ESCALIERS",
        "Marches et zigzags de 5 à 10 blocs. Idéal pour combler les pentes.",
        listOf("#../##./.##", "..#/.##/###", "##./##./.##", "#.../##../.##./..##", "...#/..##/.###/####", "##.../.##../..##./...##", "#../##./.#./.##", "..##/.##./##.."),
    ),
    ShapePack(
        "SYMÉTRIQUES",
        "Croix, poutres et échelles : des pièces parfaitement symétriques.",
        listOf(".#./###/.#.", "#.#/###/#.#", "..#../..#../#####/..#../..#..", ".##./####/.##.", "#..#/####/#..#", "#.#/#.#/###", ".#./.#./###/.#./.#.", "##/#./#./##"),
    ),
    ShapePack(
        "BARRES",
        "Des barres de 1 à 8 blocs de long. La plus longue traverse presque tout le plateau !",
        listOf("#", "##", "###", "####", "#####", "######", "#######", "########"),
    ),
    ShapePack(
        "COINS",
        "La famille des L et des J, de 3 à 5 blocs, avec leurs versions miroir.",
        listOf("#./##", "#./#./##", ".#/.#/##", "#./#./#./##", ".#/.#/.#/##", "#../#../###", "#./#./#./#./##", ".#/.#/.#/.#/##"),
    ),
    ShapePack(
        "BLOCS",
        "Des rectangles pleins, du simple point au carré 3×3 : faciles à empiler, mais ils laissent peu de marge.",
        listOf("#", "##", "###", "##/##", "###/###", "####/####", "###/###/###", "#####"),
    ),
)

/** Nombre de pièces par pack. */
const val PACK_SIZE = 8

/** Les pièces des packs sont numérotées à partir de PACK_BASE (0 à 7 = pièces classiques et bombe) : pack p, forme i → PACK_BASE + p * PACK_SIZE + i. */
const val PACK_BASE = 10

fun packType(pack: Int, i: Int): Int = PACK_BASE + pack * PACK_SIZE + i

private val PACK_EXTRA = Color(0xFFECEFF1)   // 8e couleur (les thèmes n'en ont que 7)

/** Cellules de base d'une forme : alignées en haut à gauche. */
private fun parseShape(s: String): List<Pair<Int, Int>> =
    s.split("/").flatMapIndexed { y, row -> row.mapIndexedNotNull { x, ch -> if (ch == '#') x to y else null } }

/** Les 4 rotations de chaque pièce de pack, calculées dans une boîte carrée comme pour les pièces classiques. */
private val PACK_ROTS: List<List<List<Pair<Int, Int>>>> by lazy {
    SHAPE_PACKS.flatMap { pack ->
        pack.shapes.map { s ->
            val base = parseShape(s)
            val n = maxOf(base.maxOf { it.first }, base.maxOf { it.second }) + 1
            val r = mutableListOf(base)
            repeat(3) { r.add(r.last().map { (x, y) -> (n - 1 - y) to x }) }
            r
        }
    }
}

/** Côté de la boîte de rotation de chaque pièce de pack. */
private val PACK_BOX: List<Int> by lazy {
    SHAPE_PACKS.flatMap { pack ->
        pack.shapes.map { s -> val b = parseShape(s); maxOf(b.maxOf { it.first }, b.maxOf { it.second }) + 1 }
    }
}

/** Les 4 rotations d'une pièce, classique ou de pack. */
fun shapeRots(type: Int): List<List<Pair<Int, Int>>> = if (type >= PACK_BASE) PACK_ROTS[type - PACK_BASE] else SHAPES[type]

/** Colonne d'apparition : les pièces de pack sont centrées. */
fun spawnX(type: Int): Int = if (type >= PACK_BASE) (COLS - PACK_BOX[type - PACK_BASE]) / 2 else 3

/** Couleur d'une pièce (0 à 6 classiques, 7 bombe, ≥ PACK_BASE pièces de pack). */
fun typeColor(t: Theme, type: Int): Color = when {
    type == 7 -> Color(0xFFFF3B30)
    type >= PACK_BASE -> ((type - PACK_BASE) % PACK_SIZE).let { if (it < 7) t.pieces[it] else PACK_EXTRA }
    else -> t.pieces[type]
}

/** Couleur d'une case du plateau : 8 = bloc pirate, ≥ PACK_BASE + 1 = pièce de pack, sinon pièce classique (valeur = type + 1). */
fun boardColor(t: Theme, v: Int): Color = when {
    v == 8 -> Color(0xFF8A8F98)
    v > 9 -> typeColor(t, v - 1)
    else -> t.pieces[v - 1]
}

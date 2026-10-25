package com.example.tetris

import android.content.SharedPreferences
import android.os.SystemClock
import org.json.JSONObject
import androidx.compose.runtime.*
import kotlin.math.abs
import kotlin.random.Random

const val COLS = 10
const val ROWS = 20

private const val LOCK_MS = 500L   // délai avant verrouillage d'une pièce posée
private const val MAX_RESETS = 15  // nombre max de déplacements/rotations qui repoussent le verrouillage

enum class GState { READY, PLAYING, PAUSED, OVER }

enum class Mode(val title: String, val desc: String) {
    CLASSIC("CLASSIQUE", "Le Tetris traditionnel : la vitesse augmente tous les 10 lignes. Tenez le plus longtemps possible !"),
    PACKS("FORMES", "Le jeu classique, mais avec des pièces inédites ! Choisissez parmi 10 packs de 8 formes : minis, pentominos, géants, escaliers, barres…"),
    PICTURE("DESSIN", "Le plateau démarre avec des lignes incomplètes qui forment un dessin. Complétez-les pour effacer le tableau : 1000 niveaux de plus en plus difficiles !"),
    SURVIVAL("SURVIE", "Des lignes pirates remontent du bas de plus en plus vite. Effacez-les avant d'être submergé !"),
    MISSIONS("MISSIONS", "Remplissez des objectifs pendant la partie (ex. : 3 lignes d'un coup, 4 fois). Chaque mission validée rapporte un gros bonus."),
    PUZZLE("PUZZLE", "Un plateau déjà rempli et une liste de pièces imposées : placez-les toutes pour tout effacer. 100 niveaux de plus en plus difficiles !"),
    CASCADE("CASCADE", "Après chaque ligne, les blocs retombent un par un et peuvent former de nouvelles lignes. Enchaînez les combos !"),
    DUEL("DUEL", "Affrontez l'ordinateur : vos lignes multiples lui envoient des lignes pirates, et inversement. Le premier débordé perd."),
    CHAOS("CHAOS", "Une pièce sur deux porte un événement : vitesse, commandes inversées, rotation bloquée, pièce fantôme, barre magique ou bombe. Gardez votre calme !"),
}

fun fmtTime(ms: Long): String = "%02d:%02d.%d".format(ms / 60000, (ms / 1000) % 60, (ms / 100) % 10)

/**
 * 1000 dessins de départ du mode DESSIN, de plus en plus difficiles. Les niveaux sont séparés par ';', les lignes par ',' ;
 * chaque ligne est un nombre hexadécimal de 10 bits (bit à 1 = bloc).
 */
private const val PIC_DATA = "030,078,0fc;001,007,01f,07f;030,078,0fc,1fe,030,030,030;1ce,1fe,1fe,0fc,078,030;030,078,0fc,1fe,0fc,078,030;0cc,0fc,0fc,0fc;048,0fc,0fc,0fc;048,0cc,0fc,0fc;030,078,078,0fc;084,0cc,0cc,0fc;030,030,030,0fc;048,0fc,0fc,0fc,0fc;078,078,078,0fc,0fc;030,030,0fc,0fc,0fc;048,078,078,0fc,0fc;084,084,0fc,0fc,0fc;030,030,078,0fc,0fc;030,030,030,0fc,0fc;030,030,030,078,0fc;030,030,078,078,078;0cc,0fc,0fc,0fc,0fc,0fc;048,048,0fc,0fc,0fc,0fc;084,0cc,0cc,0fc,0fc,0fc;048,0cc,0cc,0fc,0fc,0fc;084,084,0cc,0fc,0fc,0fc;048,048,0cc,0fc,0fc,0fc;048,048,048,0fc,0fc,0fc;030,030,078,078,078,0fc;084,084,0cc,0cc,0cc,0fc;030,030,030,078,0fc,0fc;084,084,0cc,0fc,0fc;078,078,0fc,0fc,078;0cc,0cc,0cc,0fc,078;048,0cc,0cc,0fc,078;078,078,030,0fc,078;048,048,0fc,0fc,030;084,0cc,084,0fc,078;0cc,084,084,0cc,0fc;078,0fc,0fc,0fc,0fc,030;048,048,0fc,0fc,078,078;084,0cc,0cc,078,0fc,078;048,0cc,0cc,048,048,0fc;078,0fc,030,0fc,030;078,030,030,0fc,030;0cc,048,048,0fc,030;0cc,0fc,078,078,0fc,0fc,0fc;078,030,078,078,0fc,030;0fc,030,078,078,078;084,0cc,048,0cc,078,0fc,0fc;0cc,078,078,078,0fc,078;0cc,0fc,0fc,0fc,030,030;078,0fc,030,0fc,0fc,0fc,078;0cc,048,0fc,0fc,030,078;084,084,084,0cc,0cc,078,078;0cc,084,0fc,078,078,078;030,078,0fc,0fc,030,030;0fc,030,0fc,078,0fc,030;0cc,048,0cc,048,078,030;0cc,0cc,084,0cc,084,0fc,0fc,078;048,0fc,030,0fc,030,030;0cc,0fc,0fc,030,078,0fc,0fc,078;0cc,0cc,078,078,078,0fc,030;0cc,078,0fc,0fc,078,0fc,078,078;0cc,084,084,0cc,048,0fc,030;084,0cc,048,078,030,0fc,030;048,0cc,0cc,0cc,0fc,0cc,1fe;084,084,0cc,1ce,17a,078,0fc;0cc,078,0b4,0fc,078,0fc,0fc;048,048,1ce,048,1ce,186,0fc;0fc,1fe,1fe,0cc,078,1fe,0fc;1ce,1fe,0fc,0fc,0fc,0cc,1fe;078,030,030,030,0b4,0fc,1fe,078;078,030,078,078,0b4,0fc,078,030;1b6,17a,1fe,0fc,0fc,078,0fc;0cc,0cc,0cc,0cc,0cc,0cc,0cc,0cc,1fe,0b4;078,0fc,0fc,0b4,0cc,078,0fc,078;030,0fc,078,078,078,0cc,0fc,0b4;1b6,0fc,1fe,1fe,0cc,048,0fc;1ce,0fc,078,078,0fc,17a,1fe,078;078,048,17a,1ce,0fc,0cc,1fe;303,303,387,387,201,249,3cf,1ce,37b;0fc,078,030,0fc,0cc,186,0fc;078,078,0cc,084,0fc,1b6,1fe;0b4,0fc,048,078,0cc,084,1fe;078,17a,1fe,0cc,084,1b6,0fc;078,1fe,0fc,0b4,0b4,078,030,0fc;387,303,34b,3cf,37b,279,0fc;078,0cc,048,078,1fe,1ce,0fc,1b6;0cc,1fe,17a,1ce,084,0fc,030;186,1ce,0fc,1ce,084,0cc,078;1fe,0fc,1fe,1ce,0cc,0cc,078,030;048,078,048,0cc,14a,1fe,0cc,0fc,030;303,3cf,285,2cd,279,2fd,0fc;0cc,1fe,186,186,0cc,0fc,0fc,078,0fc;0fc,078,030,0fc,078,14a,1ce,048,0cc,0fc;1fe,1ce,14a,0cc,084,0fc,078,030,0fc;0fc,1b6,132,186,084,0fc,078,030;3cf,387,084,1ce,1b6,0fc,1b6;0fc,048,078,0cc,285,3cf,34b,387,2fd;1fe,3cf,2cd,084,0fc,030,0fc;084,0cc,078,030,030,078;078,030,030,0fc,030,030;0cc,048,0cc,078,030,030;030,078,0fc,030,030,030;048,0fc,078,0fc,030,030,0fc;078,030,0fc,030,0fc,0fc,030;0cc,0cc,078,030,0fc,078,078;048,0fc,078,078,0fc,078,030;048,0fc,078,030,0fc,030,0fc;0cc,048,078,078,030,0fc,0fc;084,0fc,078,078,030,078,0fc;030,0fc,0fc,030,078,078,078;0cc,0cc,078,030,078,078,0fc;084,084,084,0cc,0cc,078,078,078;048,048,0cc,048,048,048,0fc,0fc;0cc,078,078,0fc,078,0fc,0fc,078;030,030,030,030,0fc,0fc,078,030;030,078,0fc,030,0fc,030,078;030,078,0fc,0fc,078,030,030;084,0cc,0fc,078,078,030,078;048,0cc,0fc,030,078,030,0fc;0cc,048,048,0fc,030,0fc,078;0cc,048,0fc,078,0fc,078,0fc,078;030,0fc,030,078,0fc,0fc,078,0fc;030,030,0fc,030,030,078,0fc;0cc,048,0cc,0cc,078,078,030;078,030,030,078,0fc,030,078;078,030,078,078,0fc,030,030;0cc,048,0cc,084,0fc,030,078;048,048,0fc,0fc,030,078,030;084,0cc,048,048,0cc,186,0fc;084,084,0fc,0fc,030,030,078;048,0cc,0fc,078,0fc,0fc,030,078;084,084,084,0cc,048,048,0cc,078;0cc,084,0cc,048,078,0fc,078,0fc;0cc,084,084,084,0cc,048,078;030,078,030,078,0fc,0fc,078,030;078,0fc,030,030,030,078;0cc,078,078,030,030,078;0fc,0fc,030,030,0fc,0fc,030;048,078,078,078,078,0cc,0fc,1fe;048,048,048,048,0cc,084,0cc,084,0fc;0cc,0cc,078,030,030,030;030,0fc,030,030,078,030;0cc,078,078,030,030,0fc,0fc;078,0fc,078,1fe,078,17a,1fe;048,0cc,048,0cc,0cc,048,078,078;078,078,078,030,030,030,030,0fc;084,0cc,0cc,048,0cc,0fc,078,0fc,078;0cc,0cc,0fc,030,030,078,078;030,0fc,078,030,030,078,0fc;030,0fc,0fc,030,030,030,0fc;0cc,048,0fc,030,030,0fc,078;030,0fc,030,078,078,078,078;084,0cc,0cc,0cc,1ce,1fe,17a;186,0cc,0cc,0fc,1fe,0fc,0fc;048,048,0cc,084,084,0cc,0fc,030;0cc,078,030,0fc,078,0fc,078,0fc;030,078,0fc,0fc,030,030,030;0cc,084,084,0fc,078,030,078;078,0fc,078,0fc,0fc,048,078;0cc,0fc,078,078,0fc,048,0fc;0cc,0fc,030,0fc,0cc,078,0fc;0cc,048,078,0fc,030,0fc,0fc,078;030,0fc,0fc,030,0fc,030,0fc,078;078,078,0fc,078,0fc,030,078,078;0cc,084,0cc,0fc,0fc,0fc,0b4,030;030,030,030,078,078,0fc,030,078,0fc;078,030,030,030,0fc,030,078;0cc,084,084,0cc,078,030,078;048,0cc,0cc,078,030,030,078;078,030,030,030,0fc,078,030;0cc,0cc,078,030,0fc,078,0fc,0fc,0fc;084,0cc,048,048,078,078,030;0cc,0cc,0cc,1fe,078,0fc,030;084,0cc,048,0fc,078,0fc,078,030;030,078,078,0fc,030,078,030,0fc;048,078,0fc,0fc,078,030,0fc,0fc,078;0cc,0cc,048,1ce,14a,048,1fe;078,030,078,078,078,0fc,030,030;078,030,078,078,030,0fc,030,078;0fc,0fc,078,078,078,030,030;0fc,078,0fc,078,030,030,078;0fc,0fc,030,030,0fc,030,078;030,0fc,030,030,030,030;0cc,048,078,030,030,030;0cc,078,078,030,078,030,0fc;0cc,078,030,030,078,078,0fc;048,048,0cc,0cc,048,078,030,078;0cc,048,0fc,078,078,030,030;0cc,0cc,078,030,030,078,078;0cc,0cc,078,0fc,030,030,030;0cc,048,078,030,078,078,078;030,0fc,0fc,1fe,132,17a,078;0fc,030,078,078,0cc,0fc,0fc;0cc,0fc,030,078,0fc,0b4,0fc;078,078,078,030,078,078,030,0fc,030;186,0cc,0cc,0cc,078,0fc,1fe;084,186,0cc,0fc,078,1fe,078;030,0fc,0fc,0b4,1b6,030,0fc;078,030,078,0fc,078,030,0fc,030;048,0cc,084,084,0cc,084,0cc,0cc,078;0cc,048,078,030,078,0fc,0fc,0fc,0fc;0cc,048,0cc,0cc,078,078,030,078;030,030,0fc,030,078,030,078,0fc;0fc,078,030,030,030,030;0cc,048,1ce,048,0cc,084,1fe;030,078,078,0fc,084,0fc,1fe;0b4,0fc,0cc,0fc,078,0fc,1fe,1fe;084,0cc,0cc,0fc,030,0fc,030,0fc,078;0fc,030,078,030,0fc,030,078;0fc,030,078,078,030,078,078;0fc,078,078,1fe,17a,17a,078;078,0fc,078,030,030,030,078;0cc,0fc,078,1fe,1fe,0b4,030;0fc,030,030,078,0fc,030,0fc,0fc;0fc,0fc,078,078,0fc,078,030,030;0fc,030,078,078,030,078,0fc,0fc;048,0fc,078,030,030,030,078;078,078,0fc,030,030,030,030;048,0cc,084,1ce,0fc,0cc,0fc;0fc,0cc,078,0fc,078,078,030;078,0fc,078,078,078,030,030,0fc;078,0fc,0fc,078,078,030,030,078;0cc,078,0fc,030,0fc,030,078,078;078,0fc,078,030,0fc,030,0fc,030;048,0cc,078,078,030,030,030;078,078,0fc,030,030,0fc,030,078;084,0fc,0fc,030,078,030,078,078;0cc,048,078,0fc,030,078,0fc,030;0cc,084,0cc,048,048,048,048,0cc,0fc;0cc,0cc,048,048,0cc,084,084,084,0fc;078,0fc,030,0fc,078,078,0fc,078,078;078,0fc,030,078,030,0fc,078,0fc,0fc;186,0cc,048,1ce,1ce,084,0fc;102,102,1b6,0fc,0fc,030,0fc;048,0fc,078,030,1b6,1fe,030;084,084,0fc,1fe,132,132,078;0cc,048,048,0fc,078,030,078,078;0cc,084,0fc,0b4,1fe,078,1fe;0cc,078,0fc,0cc,048,0fc,0fc;048,0fc,078,0cc,1fe,1fe,1fe,0fc;102,1b6,1fe,0fc,1fe,1fe,1fe,078;1b6,132,1fe,0fc,0fc,078,078;030,030,0fc,1fe,1fe,14a,078;1ce,0fc,030,078,0fc,1fe,0fc;078,030,030,030,030,0fc,078,030;048,048,0fc,078,030,0fc,030,0fc,078;0fc,0b4,0fc,0fc,0b4,0fc,1fe;030,030,030,0fc,0fc,0b4,1b6;084,0cc,048,048,048,078,030,078;048,078,030,078,0fc,030,030,0fc,078;0cc,078,1fe,078,030,078,1fe;084,0cc,14a,1ce,0cc,0fc,078;0cc,084,0cc,1ce,084,084,1ce,0fc;048,0cc,084,0cc,1ce,0fc,1b6,030;0cc,084,0cc,0cc,048,048,078,030,0fc;0cc,084,0cc,084,084,0cc,078,078,078;0cc,048,048,0cc,084,0cc,0fc,030,078;048,0cc,0cc,048,048,078,0fc,078,030;030,030,030,0fc,030,0fc,030,030,0fc;048,048,048,048,0fc,0b4,1b6;084,0fc,030,132,1fe,1fe,030;186,0cc,0fc,078,0fc,078,078;030,078,030,078,1fe,0b4,078;048,0cc,048,0cc,186,084,186,1fe;078,078,078,078,0b4,1fe,078,0fc;078,078,078,0b4,0fc,0fc,1fe,030;14a,1ce,048,1ce,1ce,1ce,0fc,0fc;084,084,0cc,048,0cc,078,030,078,078;387,387,3cf,285,3cf,387,1fe;078,030,0fc,078,078,030,078,030;0cc,048,078,030,0fc,030,030,0fc;0cc,0fc,0fc,030,078,078,078,078,078;084,0cc,078,132,1fe,078,030;1ce,0cc,084,084,1ce,084,0fc;0cc,048,048,0fc,030,078,078,030;0cc,048,0cc,078,030,078,078,030;030,078,0fc,030,078,078,030,030;0cc,1ce,084,0cc,0cc,0fc,078,1fe;084,0b4,0b4,0fc,0b4,0cc,0fc;048,078,132,1b6,0fc,0cc,1fe;0cc,048,048,048,0fc,030,078,030;186,186,084,186,186,1fe,0fc,078;030,030,17a,132,1fe,0fc,0fc,030;048,0cc,0fc,030,0fc,030,078,030,0fc;078,078,0cc,078,0cc,078,078;0cc,0cc,048,0fc,1b6,1fe,1fe,078;0cc,0cc,0cc,078,078,1fe,0b4,030;0cc,084,0cc,048,0fc,078,030,0fc,030;084,084,0fc,078,0fc,078,030,030,078;0cc,048,0cc,0cc,078,030,078,030,0fc;0fc,030,030,030,078,078,030;0cc,0cc,078,0fc,1ce,1fe,1b6;0cc,0fc,078,0cc,1fe,1ce,0fc;285,387,285,303,387,387,1fe;0cc,1ce,1fe,0b4,030,0fc,078;084,0fc,078,0fc,1fe,17a,17a;2cd,387,3cf,387,3cf,3b7,2fd;0cc,0cc,084,0fc,048,048,0fc;0cc,078,030,078,0cc,1fe,078;201,201,34b,14a,3cf,387,2fd;0fc,030,0fc,078,030,030,078,078;048,078,078,132,17a,14a,1fe,0fc;084,0cc,084,084,084,0fc,0fc,030,030;0cc,0cc,084,0cc,1fe,186,1fe;0fc,0cc,1fe,1fe,0fc,030,078;0cc,078,030,078,030,078,030,0fc;14a,1ce,0cc,048,078,0fc,030;048,0cc,084,0cc,1fe,1fe,132;0cc,0cc,078,078,030,030,078,030;0cc,078,030,0fc,078,078,078,0fc,030;030,1b6,0fc,048,078,0fc,1fe;084,1ce,084,186,084,0b4,0fc;0fc,0fc,078,1fe,1b6,17a,0fc;186,0cc,084,0cc,0fc,1ce,1fe;078,030,1b6,1fe,0cc,0fc,078;030,0fc,1fe,17a,0fc,030,078;084,0cc,0fc,030,078,030,030,030;0fc,030,0fc,030,030,0b4,1fe,0fc;078,030,078,1fe,078,078,030,1fe;030,0fc,1ce,0fc,0fc,0fc,078,1fe;1b6,132,1b6,1fe,17a,078,0fc,1fe;048,1ce,0fc,0fc,0cc,078,078;030,030,0fc,1fe,17a,078,078,030;0fc,1fe,1fe,078,0fc,1ce,1fe,1fe;078,0fc,0b4,078,0cc,0fc,078,0fc;048,0cc,0fc,030,078,0fc,030,078,030;084,186,132,1b6,0fc,048,0fc;084,0fc,0cc,048,0fc,030,078;048,0cc,1ce,0cc,0fc,1fe,1ce,078;0cc,048,0cc,048,078,030,030,0fc,078;084,084,186,1ce,048,14a,1fe,0fc,1fe;030,0fc,0fc,1fe,0cc,1ce,078;1ce,0fc,030,0fc,1fe,0cc,1fe;1ce,0fc,078,0fc,0fc,0fc,078,078;14a,1ce,1ce,1fe,0fc,030,0fc,078;14a,14a,1fe,078,078,030,078;1ce,048,0fc,1fe,078,0fc,1b6;048,048,1ce,17a,0fc,0fc,1b6;1ce,084,084,084,0b4,0fc,0fc;186,0b4,0fc,0fc,0cc,078,1fe;0cc,084,0cc,048,14a,1ce,0fc,1b6;14a,1ce,186,1ce,048,1ce,084,1fe;186,084,1ce,0fc,0fc,078,0fc,1fe,0fc;1ce,1ce,14a,1ce,1ce,0cc,0cc,1fe;0cc,078,0fc,030,030,0fc,078,078,030;0b4,0fc,1ce,1fe,0cc,0fc,0fc;0fc,084,0fc,048,0fc,0fc,1fe;048,1ce,0cc,1fe,078,0cc,1fe,0fc;048,048,048,1ce,048,0fc,0fc,0b4;030,0fc,030,0fc,078,030,030,030,0fc;048,0fc,0fc,078,0fc,030,030,030,030;048,0fc,0fc,078,030,030,078,030,078;078,0cc,0fc,078,0b4,0fc,030,0fc;048,048,048,0cc,1fe,0cc,1fe,17a;084,0cc,078,078,078,030,078,030,078;0cc,0cc,048,0cc,078,030,0fc,1b6,0fc;084,1ce,0cc,0cc,186,0cc,0fc,078,1fe;0cc,084,1ce,048,078,048,1fe;030,030,030,0b4,1fe,3cf,279;1fe,0b4,1fe,0fc,030,0fc,0fc;0cc,0fc,030,078,0cc,0cc,078,0fc;078,132,17a,132,1fe,1fe,132,078;030,078,1fe,0fc,048,1ce,0fc;1fe,1fe,0fc,030,0fc,0cc,078;0cc,0cc,0cc,1ce,048,078,1fe,0b4;17a,14a,1fe,078,078,0fc,078;078,078,0cc,0fc,1fe,17a,3b7;0fc,0fc,1b6,17a,132,030,0fc;186,0cc,1fe,078,078,048,1fe;078,1fe,078,0fc,030,0fc,0b4;078,0fc,1ce,17a,030,0b4,0fc;084,1ce,0cc,0fc,078,1fe,132,030;0fc,0fc,030,078,030,030,0fc,030,078;030,0fc,0fc,0cc,1fe,132,0fc;102,17a,1ce,0fc,0fc,078,030;0cc,078,078,0fc,078,0cc,0b4,1fe;0b4,0fc,0fc,078,078,0fc,0b4,0b4;030,078,078,048,0cc,0fc,030,030;084,2cd,285,2cd,3cf,3cf,3b7,2fd;078,0fc,030,078,030,030,030,078,0fc;0cc,078,030,078,030,030,078,0fc,078;186,1ce,1ce,0cc,084,0fc,0fc,0fc,078;0fc,078,048,078,0cc,078,030;030,0b4,0fc,0cc,0fc,1ce,17a;0fc,0fc,0fc,048,048,0cc,1fe;0fc,0b4,030,0fc,0fc,1ce,17a,078;0cc,0fc,1fe,1ce,0fc,0fc,0fc,1b6;084,186,0b4,0fc,0b4,078,078;048,17a,1ce,0fc,1ce,0fc,078;048,078,048,048,078,0fc,0b4,0fc;1ce,1ce,084,0fc,030,0b4,1fe,1fe;078,078,030,17a,1fe,078,0fc,030,078;048,0cc,048,0fc,0cc,14a,1fe;078,1fe,1fe,17a,078,078,078,030;084,0fc,0cc,078,078,0b4,0fc,030;048,0cc,084,0fc,030,030,030,078,030;084,0cc,084,0fc,030,030,030,078,030;048,048,0cc,1fe,0fc,030,078,030,1fe;030,078,0fc,1fe,14a,14a,078;078,0fc,078,0fc,1ce,0fc,1ce,078;030,0fc,0cc,1fe,1ce,1b6,0fc;078,0fc,030,1b6,1fe,1b6,030,030;0fc,0cc,0fc,078,048,1ce,1fe;1ce,0cc,084,1b6,0fc,0cc,1fe,0fc;048,078,030,030,17a,1fe,0cc,1fe,078;0cc,0cc,1ce,048,0cc,0cc,0cc,048,0fc,0fc;030,078,0b4,0fc,1ce,17a,0fc;0cc,0fc,1b6,0fc,0fc,0fc,0b4,030;0fc,078,030,0fc,0fc,0cc,0fc,1ce,0fc;0fc,048,0fc,1ce,17a,030,1fe;078,078,1fe,1fe,078,048,078,078;048,048,0cc,0cc,1fe,1b6,1b6,0fc,0fc;0cc,0fc,1fe,078,048,1ce,078;0fc,030,0fc,030,078,0cc,048,0fc;078,030,030,030,0fc,0b4,0b4,1fe;084,1ce,186,084,084,0fc,1ce,1fe;084,084,1ce,1b6,1fe,132,030,0fc;084,0cc,186,0cc,1fe,078,0fc,078,030;0cc,048,048,0cc,0fc,1b6,132;0cc,078,048,078,0cc,0b4,0fc;0cc,0b4,0fc,030,030,030,1b6,1fe;0b4,1fe,0fc,0fc,030,0b4,0fc,030;0cc,078,17a,1fe,17a,132,078,030;1ce,17a,030,0fc,1ce,0fc,030;030,030,0fc,048,0cc,1ce,078;1ce,1fe,0fc,1fe,0cc,078,078,078;14a,1fe,1fe,1b6,0fc,0fc,030,078;084,1ce,0cc,0fc,048,0cc,078;0fc,0cc,048,078,1fe,1ce,0fc;078,0cc,0cc,048,0fc,0fc,030,0fc;102,17a,1fe,0fc,078,030,030,078;030,0b4,0fc,0b4,0fc,078,1fe,1ce,1fe;078,0fc,0cc,0fc,1ce,14a,078;030,078,17a,1b6,0fc,084,0fc;0b4,1fe,1ce,0cc,0fc,030,0fc;1ce,1ce,0cc,084,0fc,078,048,0fc;102,1b6,1fe,1ce,0fc,0fc,0cc,0fc;0cc,186,14a,1ce,0fc,0cc,1fe,1fe;048,0fc,0fc,0b4,078,0fc,0b4,1b6;084,0cc,1ce,0fc,0fc,030,078,078,030;0fc,030,0fc,0b4,0fc,1fe,17a,1fe,0fc;078,0cc,1fe,0cc,0b4,0fc,1fe;078,048,0fc,0fc,0cc,186,1fe;0cc,1ce,1fe,1b6,0fc,084,0fc;0fc,048,078,078,0fc,0cc,0fc,0b4;078,0cc,078,1fe,0fc,030,0fc,1b6;048,078,048,0fc,078,078,078,14a,1fe;084,0cc,1ce,186,1ce,1ce,3cf,1ce,1fe;048,1ce,1ce,1ce,048,1ce,084,1ce,0cc,078;0cc,078,078,078,14a,3cf,2fd;084,1ce,048,078,048,0fc,1b6;186,132,1b6,0fc,078,030,078,030;0b4,1fe,1fe,0cc,0fc,078,1fe,17a;084,186,186,0cc,14a,1ce,1fe,0b4;0cc,186,1ce,1ce,186,084,1b6,1fe,030;048,1ce,3cf,387,3cf,1ce,078;0fc,0cc,0fc,0cc,1fe,17a,1b6;0cc,048,078,0fc,1b6,1b6,0b4;0cc,078,078,0cc,0fc,17a,17a,1fe;0fc,048,0cc,078,0fc,17a,1fe,0fc;030,078,048,1ce,17a,048,1fe;1ce,14a,1fe,0cc,078,078,078;186,0cc,078,0b4,1fe,0cc,078;1ce,1b6,1fe,0fc,084,0fc,0fc;186,084,0cc,078,078,1fe,14a,078;102,17a,1ce,0fc,048,0fc,030;078,078,1fe,078,0fc,0b4,0b4,0fc;078,0fc,1fe,1b6,1ce,048,078;078,048,048,048,0fc,078,0cc,0fc;0fc,084,0fc,1fe,0fc,0fc,1fe,0b4;084,0cc,186,14a,1fe,0fc,0fc,030,078;186,084,0fc,1ce,0cc,1ce,0fc;0cc,0cc,1fe,0b4,0cc,0b4,0fc;084,0fc,1fe,17a,1ce,0fc,0cc,0fc;1ce,186,0b4,0fc,0fc,078,048,1fe;1b6,1b6,0b4,0fc,17a,1fe,0fc,078,078;0fc,0cc,0b4,0b4,0fc,1b6,030;030,030,0b4,0fc,084,1ce,17a;0fc,078,030,0fc,084,084,0fc;1ce,14a,14a,048,078,030,0fc,030;186,0b4,1fe,0fc,1fe,17a,0fc,0b4;030,078,078,0cc,084,1ce,078;0fc,0cc,0b4,0fc,17a,1ce,0fc;102,186,0cc,084,1ce,0fc,084,0fc;084,0fc,0cc,078,14a,1ce,0fc,1fe;0fc,1ce,1ce,1fe,078,078,078,0fc;0fc,078,048,0cc,0fc,0fc,1fe,1b6,1fe;078,048,0cc,0cc,048,078,078;1fe,0cc,0fc,1fe,0cc,0cc,078;030,078,048,048,048,078,37b,1fe;34b,34b,3cf,303,3cf,37b,231;084,0cc,0fc,0cc,186,0b4,1fe;0fc,1fe,0b4,1b6,1b6,030,0fc,0b4;102,186,1ce,186,1fe,1ce,0cc,078;0fc,1b6,030,1b6,1fe,17a,1fe,078,0fc;030,078,0fc,084,1b6,0fc,0b4;201,387,3b7,132,37b,3cf,0fc;0cc,0cc,1fe,0cc,0b4,0fc,078,030;186,0cc,048,0cc,0fc,1ce,048,1fe;1fe,0fc,1fe,1fe,14a,078,048,0fc;084,186,0cc,1ce,1fe,17a,17a,1b6;1fe,0b4,078,030,078,0fc,1b6;1fe,17a,0fc,0cc,078,078,078,1fe;102,132,1fe,1fe,0fc,030,0fc,0cc,1fe,1fe;0fc,1b6,132,078,048,048,1fe;030,078,0cc,078,0cc,3cf,279;0fc,0cc,048,0fc,1b6,1fe,0b4;0fc,030,0fc,0cc,14a,1fe,1b6;078,0fc,0cc,186,0b4,0fc,1fe;279,37b,231,3b7,37b,3cf,279;078,030,078,14a,17a,1ce,1fe,0b4;078,0b4,0fc,0fc,048,0fc,078,1fe,17a,078;201,279,2fd,231,333,3b7,2fd,279;1ce,0fc,030,0fc,1b6,186,0fc;0cc,2fd,3cf,2fd,3cf,3cf,2fd;078,1fe,17a,078,0cc,0fc,048,078;048,078,0fc,1fe,0cc,0cc,048,078;14a,1ce,1fe,0cc,0fc,0cc,1ce,1fe;1ce,0cc,0b4,0fc,048,0fc,0fc,078;186,1fe,0cc,0b4,0fc,048,0fc;048,078,048,1ce,17a,0fc,0b4;048,1ce,1fe,1ce,048,0fc,030,078;078,048,048,0cc,1fe,1fe,1ce,0fc;1fe,0fc,0fc,0b4,078,0fc,030,17a,1fe;084,1ce,102,1ce,186,186,084,0fc,1b6;3cf,0cc,1ce,34b,3cf,3cf,37b;34b,249,3cf,3cf,2cd,1ce,387,0fc;186,1ce,14a,14a,0cc,078,0fc,030;0fc,0cc,1fe,0cc,1ce,0fc,030,0fc;132,1b6,1fe,17a,078,078,0b4,1fe,078;078,0fc,078,14a,1fe,1ce,048,0fc;1ce,1ce,17a,078,078,0b4,1b6,1fe;084,186,0b4,0fc,17a,17a,1b6,0fc;078,279,279,34b,1ce,3cf,2fd;048,078,1fe,3cf,387,3b7,1fe;0fc,0fc,078,048,0cc,1fe,37b,3b7;0fc,048,078,0cc,1fe,078,048,1fe;0fc,1b6,17a,030,0fc,0fc,17a,1fe,0fc;0cc,084,0fc,17a,1ce,048,078,0fc;0fc,030,078,0fc,1ce,0cc,048,0fc;1b6,0fc,030,078,0fc,048,1ce,1fe;1b6,0b4,1fe,186,1fe,0fc,0cc,1fe;0cc,0fc,0fc,0fc,1ce,048,0cc,078,1fe;084,0fc,0fc,1ce,084,0fc,030,078;0cc,1fe,1fe,0cc,0fc,0cc,1ce,1fe,1fe;078,17a,1fe,1fe,186,0cc,0fc,1fe,1fe;1ce,0fc,0cc,0cc,1ce,17a,030;17a,1b6,1fe,078,0cc,048,078;048,1ce,1b6,1fe,0b4,078,1fe,17a;048,048,078,1fe,132,1fe,0fc,048,0fc;0cc,0cc,0cc,078,078,0fc,1fe,1fe,132,1b6;078,0cc,084,0cc,1fe,1fe,17a;132,1fe,078,048,048,1ce,0fc;0cc,078,0b4,0fc,048,0fc,1fe,132;078,0cc,0fc,048,0fc,0b4,1fe,0b4;186,0cc,078,0fc,0cc,186,1fe,1fe;0fc,0cc,084,0fc,048,1ce,1fe;030,0fc,084,084,1b6,1fe,1fe;0b4,0fc,1ce,048,0fc,1ce,0fc,078;1ce,084,1ce,0fc,084,0fc,0cc,1fe;0fc,030,0b4,0fc,1fe,3cf,37b,333;078,078,078,0fc,0b4,030,078,048,1ce,078;0cc,0fc,0b4,084,0cc,0b4,1fe;030,0fc,084,0b4,0fc,1ce,17a;030,078,0fc,0cc,186,14a,1fe;048,1ce,17a,048,0fc,0cc,0fc,1b6;084,0cc,186,1b6,0fc,030,0fc,084,1fe;048,0fc,084,0fc,1ce,084,0fc;0cc,0fc,048,1ce,0fc,0b4,0fc,030;0fc,1ce,0fc,0b4,078,132,1fe,030;048,0cc,1fe,0fc,030,078,0cc,048,0fc;078,0cc,078,14a,14a,1ce,0fc;078,0cc,0fc,1fe,186,0cc,1fe,078;030,030,17a,1b6,0fc,0cc,078,078,030;186,084,0fc,0cc,186,0cc,0fc;048,249,34b,1ce,14a,303,3cf,0fc;078,078,0fc,0cc,084,0b4,0fc,1b6;186,0cc,0cc,1fe,1ce,1ce,17a,078,078;186,1ce,0fc,1ce,1fe,0cc,078,0fc,030;0fc,1ce,0fc,0fc,048,078,14a,1fe;1fe,0fc,078,048,0cc,0fc,1ce,0fc;048,0cc,0fc,1ce,0cc,0cc,1fe,0b4;1b6,0fc,0fc,0fc,0cc,186,1fe,030;0fc,084,0fc,0b4,0fc,1b6,0fc,030;0fc,0fc,078,17a,17a,1ce,048,0fc,030;1ce,084,0fc,1ce,0cc,0fc,0fc,030,0fc;0b4,0fc,1fe,0fc,078,0cc,1ce,1fe,17a;132,1b6,0fc,048,048,078,030,0fc;084,1ce,17a,1ce,17a,078,048,1fe;078,1fe,17a,0fc,1b6,0fc,078,0cc,0fc;048,0fc,084,0cc,078,078,0fc,048,078;0fc,0cc,0cc,0b4,0fc,078,0fc,1fe,0b4;0cc,1ce,0cc,084,084,1b6,1fe,0cc,078,078;1ce,0fc,1fe,186,0cc,0cc,078;0fc,1ce,17a,0fc,1ce,048,078;084,084,2cd,3cf,3cf,14a,0cc,1fe;0fc,1ce,0cc,0fc,0cc,0cc,1fe,1fe;078,0b4,1fe,0cc,0cc,1fe,1b6,1fe;0cc,0fc,0b4,1b6,0b4,078,1fe,030;1ce,0fc,0b4,030,0fc,030,0fc,0fc,078,078;078,1fe,1ce,1ce,048,0cc,078;186,0cc,1ce,0fc,0cc,048,0fc,0b4;048,1ce,17a,048,048,0fc,1ce,0fc;078,0cc,1ce,048,1ce,17a,078;078,0b4,1fe,14a,0cc,078,078;186,0cc,0b4,1fe,0cc,084,0fc;030,0fc,0b4,0fc,0cc,078,0cc,1ce,078;387,201,387,285,2b5,2fd,279;030,078,048,0fc,0fc,1ce,17a,132;303,186,3cf,3cf,1fe,0fc,0fc;186,0cc,1fe,0b4,0b4,1fe,1b6,078;1ce,14a,1fe,186,1fe,0fc,030,078;030,0fc,1ce,084,0cc,1ce,1fe;0fc,078,0fc,0b4,186,186,0fc;1fe,1fe,0cc,0fc,078,048,048,0fc;0fc,084,084,0fc,0b4,030,17a,1fe;14a,1fe,0cc,1ce,0fc,17a,17a,1fe;0b4,1fe,1ce,0fc,048,0cc,1fe,030;084,0cc,3cf,303,201,201,333,1b6,1fe;078,0fc,0b4,0cc,0fc,1ce,1fe,0cc,078;0cc,0fc,0fc,030,17a,1fe,1fe,0b4,0b4,078;1fe,186,1b6,0fc,0cc,0fc,030;078,0cc,0fc,0cc,1fe,186,0cc,1fe;084,1ce,1fe,0cc,0b4,0b4,1fe,030;102,1b6,0fc,1fe,14a,14a,048,0fc;0fc,0cc,0cc,048,1ce,048,0fc;0fc,030,0fc,1fe,17a,17a,1b6,0b4;084,1ce,1fe,132,1b6,0b4,1fe,0fc;1fe,17a,078,0fc,0b4,0cc,1fe,17a,078;1fe,0cc,0fc,078,0b4,1fe,1fe,1b6,030;084,285,3cf,303,303,3b7,1fe,078;0fc,1ce,1fe,078,0cc,0fc,084,0fc;186,186,1fe,186,1ce,0cc,0fc,0fc;084,0cc,078,14a,17a,34b,3cf,2fd;0cc,084,186,0cc,0b4,1b6,0fc,078,030;084,186,1fe,17a,048,0cc,0fc,1b6,0fc;078,0fc,0fc,1ce,0fc,17a,1b6,0b4,1fe;078,078,048,0fc,1ce,0cc,084,1fe;186,0cc,048,0fc,1ce,048,048,0fc;0fc,1b6,030,17a,17a,1b6,1fe,132;084,1ce,084,084,084,0fc,0cc,0fc,1ce,1fe;0fc,0fc,1fe,0b4,084,084,1fe;249,3cf,303,34b,3cf,249,34b,279;186,084,0cc,0fc,0fc,1b6,132,17a;0cc,078,17a,17a,37b,3cf,2cd,2fd;048,14a,3cf,37b,3cf,249,2fd;0fc,0b4,0cc,1ce,1ce,048,0fc;1ce,1fe,0fc,078,1fe,14a,17a,17a;048,0fc,1b6,084,0cc,1fe,0b4;1b6,0fc,030,0fc,048,0cc,0b4,1fe;030,0fc,048,1ce,0fc,0cc,3cf,2fd;1fe,1ce,048,0fc,0fc,0fc,1ce,17a;0cc,0fc,084,1ce,0fc,1ce,1ce,0fc;1ce,048,0cc,0fc,1b6,0b4,0fc,078,030;084,0cc,3cf,387,201,285,3cf,249,279;1ce,084,0cc,078,048,0fc,0fc,1fe,1ce,0fc;1fe,0fc,0cc,084,0b4,1fe,030;0fc,048,048,0cc,1ce,048,1fe;14a,1ce,1fe,132,1b6,0fc,1b6,078;186,1ce,1b6,0fc,1ce,14a,1fe,078;3cf,34b,387,201,387,387,3cf,34b,279;1ce,048,078,0fc,0b4,0cc,048,0fc;048,1ce,17a,1b6,17a,048,078,030;030,078,0cc,078,0b4,1b6,0fc,0b4,0fc;1fe,1b6,0b4,0fc,078,078,1fe,17a,030;0cc,048,0fc,048,0cc,0cc,0cc,0b4,0fc;0fc,084,084,0fc,0b4,0b4,030,030;0b4,1fe,0cc,084,0fc,078,0fc,1b6;084,0cc,1ce,3cf,186,0cc,078;303,249,3cf,303,186,1fe,132;0fc,084,084,0fc,078,0cc,1fe,030;078,0fc,1fe,1fe,1ce,14a,186,0fc;078,048,0fc,048,0fc,0fc,1ce,17a,1b6;186,303,303,186,285,387,1b6,3b7,1fe;1ce,1ce,1b6,1fe,17a,0fc,0fc,030,030,0fc;0fc,1fe,078,0cc,186,084,0fc;1ce,14a,078,0b4,1b6,0b4,1fe,078;0cc,084,084,084,0fc,084,1b6,0fc,0b4;201,279,2fd,249,3cf,285,1fe;0cc,1fe,0fc,0b4,084,084,0fc;1fe,0cc,048,0cc,0fc,030,0b4,1fe;078,1fe,14a,048,17a,14a,1fe,030;0fc,1fe,14a,078,1fe,0b4,186,1fe;1fe,0fc,0fc,1fe,1ce,0cc,048,0fc,030;084,0cc,078,132,1fe,0fc,084,0cc,0fc,0fc;048,0fc,078,0cc,1ce,1fe,0cc,0fc,1b6,0fc;078,048,1ce,14a,048,0cc,0fc;1fe,0fc,084,0fc,048,078,078,030;333,231,2fd,37b,333,3b7,0fc;078,048,048,048,0cc,0fc,1b6,17a;084,084,186,0cc,0b4,1fe,3b7,078;048,048,0fc,0cc,285,3cf,37b,2fd;078,0fc,0cc,0cc,0cc,0b4,1fe,0fc,0b4;084,0fc,078,048,0cc,1ce,048,078,0fc;078,030,0fc,084,186,132,1fe;078,048,0fc,048,1ce,14a,17a;0fc,0cc,078,1fe,078,048,0cc,1ce,1fe;0cc,1fe,0cc,048,048,078,0cc,0fc;0cc,0fc,084,0cc,1fe,1ce,17a,030,078;0fc,1fe,14a,048,0fc,1ce,1fe,1fe,1b6;0cc,0fc,0b4,078,048,0fc,084,0fc,0b4,030;231,333,37b,2fd,3cf,249,279;048,078,1fe,1ce,048,048,048,078;078,048,048,0fc,084,1ce,1ce,0fc;14a,1fe,1b6,1ce,048,048,17a,1fe;14a,17a,1b6,0fc,048,078,030,078,030;1ce,1fe,078,048,0cc,048,0fc,1fe,078;0fc,078,0fc,1b6,1fe,0b4,030,078,048,078;186,1ce,1ce,1ce,0fc,048,078,0b4,0fc,030;231,279,249,3cf,1ce,1fe,0fc;1fe,14a,1ce,048,078,030,078;030,0fc,0b4,084,0cc,048,0fc,1b6;1ce,0cc,0fc,1ce,1fe,132,17a,1b6;1ce,0cc,078,1fe,0fc,0fc,0cc,0cc,048,0fc;1fe,186,0cc,0cc,0fc,0b4,078;249,3cf,303,3cf,1fe,3cf,14a,1fe;1fe,078,0cc,0cc,084,1ce,078;0b4,1fe,0fc,0cc,0cc,0cc,0fc,048,0fc;1fe,17a,078,078,0cc,048,078,078,0fc,1fe;1fe,078,0fc,084,084,0cc,078;1fe,0fc,0b4,1b6,1fe,0b4,0b4,078;1ce,0cc,0fc,084,084,0fc,078,030;17a,1fe,17a,1fe,0fc,0b4,0cc,0fc,030,0fc;0fc,048,0fc,1b6,186,0cc,1fe,030;0b4,0fc,0cc,0cc,14a,1fe,0fc,030,030;084,0fc,1ce,1b6,0fc,0cc,0fc,0cc,0fc,078;1ce,3cf,0cc,14a,3cf,249,1fe;0b4,0fc,048,0cc,084,0b4,0fc,0b4;1fe,1ce,1fe,1fe,1b6,0b4,084,0fc;0fc,048,1ce,17a,030,030,030,078,078;078,078,048,0cc,0fc,0cc,14a,1fe,17a;048,048,1ce,17a,1ce,1b6,0fc,1ce,17a;078,030,078,14a,1fe,1ce,0fc,048,0cc,1fe;1fe,1b6,030,078,0cc,0cc,084,1fe;1fe,0b4,0cc,0fc,0b4,0fc,048,078;084,387,3cf,3cf,2fd,279,249,1fe;0fc,078,17a,1fe,132,14a,1fe,0fc,030;084,387,285,2cd,279,0fc,3b7;1ce,17a,0fc,048,048,078,0b4,0fc;078,0cc,048,0cc,1ce,0fc,1ce,17a;0cc,0fc,1b6,1ce,1fe,0b4,0fc,1ce,17a;0fc,084,0cc,084,1ce,1b6,1fe;0cc,186,1fe,186,0cc,0cc,1fe,0b4;0fc,0fc,1b6,0fc,0fc,048,0fc,0b4,0cc,0fc;1b6,0fc,0cc,0cc,0cc,1ce,0cc,078;030,0fc,0cc,048,0cc,048,14a,1fe,078;102,1ce,186,0cc,078,048,048,0cc,078;1ce,0fc,17a,1ce,084,084,0fc;1b6,1fe,0cc,084,084,1b6,0fc;078,078,1fe,3cf,34b,3cf,1fe,333;048,0fc,1ce,102,1ce,0fc,078,030;0fc,1ce,14a,078,0b4,1fe,1b6,37b;1fe,1ce,1fe,17a,048,1ce,084,1fe;048,0cc,186,084,0fc,1fe,1ce,084,0cc,1fe;078,0cc,1fe,1b6,084,0cc,0fc,1b6;0fc,1fe,186,14a,17a,1fe,1b6,030;0fc,0b4,1b6,084,0fc,030,0b4,1fe,1fe;0cc,3cf,387,084,1ce,1ce,078;084,0cc,1ce,17a,1b6,084,1ce,17a;078,0cc,0cc,078,048,1ce,1b6,0fc,030;0cc,1fe,0b4,1b6,0fc,0fc,048,0cc,0fc,0fc;0b4,0b4,0fc,1b6,0fc,1fe,132,17a,0fc,030;078,048,0fc,1b6,084,0cc,048,0fc;17a,14a,1ce,048,0fc,0cc,0cc,078;084,387,387,186,387,1ce,1fe,030;0cc,1fe,0cc,14a,17a,1fe,14a,0fc;0fc,0fc,084,0fc,084,1ce,186,0fc;084,186,3cf,37b,3cf,2fd,2b5,333;048,0fc,0b4,078,132,1fe,14a,1ce,17a;1fe,186,1fe,0fc,078,0cc,0fc,0fc,030,030;0cc,2fd,3cf,303,3cf,3cf,17a;084,1b6,1b6,17a,1fe,3cf,249,279;303,3cf,285,3cf,285,2fd,3cf,37b,2fd;1fe,078,048,0cc,048,078,0cc,1fe,0fc;249,3cf,387,34b,1ce,1ce,1ce,1fe,1fe;0cc,1fe,186,1ce,0fc,1b6,17a,048,1fe;0fc,1ce,0fc,1ce,186,1b6,1fe,078,078;303,3cf,249,34b,34b,279,0fc;0fc,0b4,0fc,078,030,132,1fe,186,1fe,0b4;387,3cf,249,279,37b,1fe,1b6;030,0fc,084,0b4,0fc,1b6,1b6,132;0fc,0cc,048,048,0cc,084,1b6,1fe;1fe,1ce,0fc,1ce,0fc,048,0fc,1ce,0fc,030;078,279,34b,3cf,2cd,1ce,0fc;14a,1ce,0cc,0fc,084,186,084,1fe;084,2cd,3cf,34b,249,3cf,34b,303,3cf,078;078,1fe,186,186,0cc,084,0fc;201,279,2fd,3b7,285,387,0fc;333,37b,17a,17a,17a,1ce,3cf,1fe;078,0cc,048,0fc,048,0fc,0fc,0b4,0b4,0b4;0fc,030,1b6,0fc,0b4,186,1fe,0fc,048,0fc;1fe,0cc,078,048,1ce,048,048,1fe;030,0fc,0fc,1ce,186,1ce,17a,132;1fe,0fc,084,0fc,084,0b4,1b6,1fe;1b6,0fc,1ce,0cc,1fe,3cf,34b,279;0fc,1b6,0fc,1ce,048,078,0cc,1ce,078;0fc,0cc,048,1ce,1ce,0fc,1fe,1b6,17a,078;3cf,34b,14a,303,249,3cf,1fe,3cf,3b7,1fe;048,078,0cc,0cc,048,0fc,048,048,048,1fe;078,1fe,0cc,084,0cc,3cf,2fd,37b;17a,1b6,1fe,078,078,17a,1fe,14a,0cc,078;0fc,084,084,186,1fe,0fc,1fe,0fc,0fc,1fe;0cc,048,048,14a,1fe,0cc,1ce,17a,1b6,17a;387,3cf,387,2cd,1ce,17a,030;0fc,048,0fc,1b6,0b4,0fc,084,1b6,0fc;078,0cc,186,0b4,0fc,0cc,048,078;030,0fc,2fd,285,3cf,303,3cf,37b;030,078,1fe,17a,132,132,1fe,132;078,0fc,1ce,048,1ce,084,0cc,0fc,0fc;048,1ce,048,078,132,37b,1fe,17a,0fc;078,048,078,1fe,0cc,1fe,0cc,14a,17a,1fe;17a,1b6,0fc,17a,1fe,17a,14a,0cc,0fc;102,14a,1fe,0b4,078,030,0fc,0cc,1ce,17a;048,0fc,0cc,0cc,1fe,0fc,0b4,186,0cc,0fc;078,14a,1ce,14a,048,14a,1ce,1fe;078,1fe,0cc,14a,1fe,132,17a,37b;0cc,0b4,1b6,132,1fe,0cc,1fe,132;186,0b4,0fc,0cc,186,084,0fc,0b4;1fe,0fc,030,078,14a,1fe,17a,17a,132;078,1fe,14a,17a,0fc,048,0cc,0cc,0fc;084,0fc,1fe,0cc,186,1fe,078,048,0cc,078;078,0cc,084,0fc,084,1b6,1fe,132;084,285,2fd,37b,249,3cf,2fd,0fc;387,3cf,0cc,0fc,078,1fe,37b;1fe,1fe,132,132,0b4,0fc,0b4,078;048,0fc,0cc,1ce,14a,1fe,1b6,1fe,132;3cf,0cc,3cf,2cd,249,078,1fe;078,0cc,0cc,14a,1ce,0fc,0b4,0b4;084,0cc,1fe,14a,0cc,084,1b6,0fc,030;0cc,0fc,0cc,048,34b,2cd,3cf,279;1fe,132,186,0cc,0cc,078,1fe,030;2cd,2cd,2cd,2fd,2cd,249,3cf,0cc,1fe;1b6,132,1fe,186,0cc,084,1b6,1fe,0fc;078,078,048,048,1ce,048,078,0b4,1fe,030;048,279,2cd,3cf,37b,279,030;0fc,048,0cc,084,0fc,17a,14a,1fe,078;2fd,2cd,3cf,1fe,3cf,249,0fc;387,084,084,186,1fe,3cf,0fc;084,1ce,3cf,2fd,2b5,0fc,1b6,3b7;1fe,186,1fe,1fe,0b4,0cc,078,0fc,030,030;186,1fe,1b6,084,084,1ce,1ce,0fc;231,3b7,333,279,231,2b5,2b5,3b7,1fe,0b4;249,34b,3cf,0cc,3cf,3cf,37b,132,1b6;1fe,0b4,084,0fc,030,0fc,078,048,078,1fe;078,048,1ce,084,1ce,0fc,078,078,0fc,030;1ce,14a,0cc,3cf,249,2cd,2fd,231;0cc,249,3cf,2cd,3cf,34b,37b,249,279;3cf,1ce,1ce,14a,3cf,249,078;0fc,1b6,084,0cc,084,1ce,17a;1fe,1b6,0fc,0b4,1b6,0fc,084,0b4,0fc;048,0fc,1ce,0fc,1ce,186,084,0cc,0fc;0fc,1b6,1b6,1fe,186,0cc,0fc,078,0fc,1b6;1ce,17a,048,0cc,1fe,0fc,084,186,0fc;0fc,1b6,1fe,14a,17a,0fc,1b6,186,0fc;0cc,1fe,132,1b6,186,132,1fe,030;37b,333,231,2fd,3b7,285,2b5,2fd;249,3cf,3cf,249,249,34b,2cd,078;3cf,3cf,285,2fd,249,2cd,279;084,1ce,17a,14a,17a,1fe,1ce,084,0fc;084,1ce,0fc,0b4,0fc,1ce,084,0cc,0cc,0fc;1fe,132,14a,1fe,0cc,1fe,0fc,0b4,0fc,0fc;0fc,0cc,0cc,1fe,1ce,186,084,0fc,030;0fc,084,1ce,084,0cc,1ce,17a,0fc;3cf,3cf,186,186,186,084,1ce,3cf,37b;387,285,186,387,3cf,2fd,231,231;17a,1b6,17a,1ce,186,0cc,0fc,1b6,0fc;1b6,0fc,0cc,048,0fc,084,1ce,1b6,1fe,0fc;084,0fc,279,37b,17a,1fe,17a,030;0fc,0cc,1ce,084,1b6,0b4,0fc,030,030;0fc,084,084,186,0cc,084,1fe;2cd,3cf,34b,3cf,1ce,0fc,0fc,0b4;303,249,279,333,17a,1fe,14a,3cf,279;387,333,3b7,1fe,1b6,3b7,37b,333,2fd;186,3cf,1ce,3cf,37b,1fe,3b7,3cf,3cf,17a;1ce,387,186,0b4,1fe,3cf,1fe,333;0fc,0fc,0fc,1b6,084,0cc,1ce,0cc,0b4,0fc;1fe,0cc,3cf,3b7,1fe,3cf,3cf,3cf,0fc;048,1ce,1fe,17a,078,078,0cc,285,387,1fe;17a,1fe,0fc,0b4,1fe,0fc,084,084,0fc,0b4;0fc,1ce,1fe,0b4,0cc,048,078,0cc,0cc,1fe;078,1fe,0cc,1ce,3cf,3cf,3cf,249,1fe;078,0b4,0b4,1fe,078,0cc,048,0cc,0b4,0fc;17a,1fe,3cf,3cf,14a,0cc,0fc;1fe,17a,37b,279,279,2fd,1fe,3b7;084,1ce,14a,37b,231,37b,279,2fd;132,1fe,132,030,078,14a,1ce,186,0cc,0fc;303,102,17a,14a,34b,37b,3cf,078;0fc,0fc,1ce,1ce,084,084,1ce,0cc,0fc;102,102,1b6,0fc,0b4,084,084,084,0fc;030,1b6,1fe,0cc,078,048,14a,14a,1fe,132;387,186,0cc,0cc,3cf,34b,279,279;0cc,1fe,3cf,1ce,3cf,249,37b,0fc;0cc,3cf,37b,37b,3b7,333,231,333;303,3cf,3b7,1fe,2fd,3cf,2fd,37b,132;387,3cf,186,14a,1fe,0fc,030;0fc,1fe,1ce,1ce,3cf,37b,3b7,231,2fd;078,078,0fc,048,1ce,186,1ce,17a,17a,1b6;186,132,1fe,0b4,0fc,084,1ce,1ce,048,078;2cd,387,084,084,387,2b5,0b4,1fe;0b4,0fc,1b6,17a,333,333,231,37b;2cd,285,3cf,2cd,249,279,279,231;37b,37b,3cf,249,2fd,249,37b,2fd;1fe,0cc,186,1ce,084,1b6,0fc,0b4,030;333,17a,1fe,1fe,3b7,37b,279,231;1ce,0fc,0b4,0b4,0b4,0b4,0cc,084,0fc;231,37b,3cf,2fd,2b5,285,2fd,333;078,37b,1fe,2fd,285,3cf,303,1fe;078,0cc,0cc,084,0cc,048,078,0cc,186,1fe;0fc,1ce,0fc,1fe,14a,1ce,0fc,084,1ce,17a;0cc,0fc,0fc,17a,1b6,3b7,2fd,231,3b7,2fd;1b6,3b7,333,37b,333,17a,1fe,132,078;303,231,2fd,279,34b,1ce,34b,1ce,37b;1fe,14a,17a,14a,078,078,048,0cc,084,0fc;3cf,2cd,2cd,2fd,0fc,1fe,186,1fe;1fe,0fc,0b4,0cc,048,048,078,048,1ce,078;17a,1ce,084,084,0b4,0fc,0b4,078,1fe,1fe;078,030,0fc,048,1ce,084,0cc,048,1ce,17a;0fc,1b6,1ce,186,102,1ce,14a,078;078,2fd,3cf,3cf,34b,37b,249,078;0fc,084,186,102,14a,1fe,078,030;0fc,1ce,048,0cc,1ce,0fc,0b4,1b6,17a,1b6;1fe,1ce,084,084,1b6,0fc,1fe,132,17a;030,0fc,1fe,1ce,3cf,387,387,34b,37b,1fe;084,0fc,1ce,14a,048,1ce,0cc,186,0b4,0fc;048,1ce,3cf,3cf,37b,17a,0fc,030,0fc;37b,34b,34b,3cf,387,1b6,1b6,0fc;084,0fc,084,2cd,3cf,186,387,3cf,2fd;078,17a,17a,1ce,387,3b7,1fe,37b,333,0fc;078,249,3cf,2fd,0b4,084,0fc;2fd,3cf,387,2cd,084,0fc,3b7;0fc,279,37b,1fe,2fd,2b5,3b7,1fe,030;1b6,0fc,084,084,1ce,14a,14a,17a;2cd,387,3cf,186,3cf,387,2cd,249,078,078;0fc,2cd,387,2b5,0fc,1b6,078;0fc,084,084,1ce,14a,1ce,0fc,048,0fc,078;078,0cc,186,0cc,0cc,186,0cc,0fc,1ce,078;1fe,132,14a,1ce,048,0cc,0b4,1b6,0fc;14a,1fe,186,3cf,285,3cf,2fd,0b4;1fe,0fc,084,387,34b,3cf,1fe,3cf,0fc;0cc,078,048,1ce,34b,37b,3cf,37b,2fd,333;1fe,2fd,3cf,34b,3cf,2cd,2fd,37b,333;078,0cc,1fe,1ce,17a,102,1ce,14a,048,0fc;3cf,249,2fd,37b,279,2fd,2b5,231;1fe,0cc,078,37b,1fe,0b4,0fc,1b6,37b;3cf,34b,34b,37b,3b7,3cf,2fd,279,2fd,279;37b,3cf,2fd,2cd,249,249,37b,2fd;1fe,186,0b4,0b4,1fe,37b,231,078;0fc,0cc,285,3cf,14a,0cc,078;37b,3cf,1fe,132,030,0fc,0fc;1fe,14a,078,0cc,048,0cc,186,1ce,186,0fc;186,3cf,37b,333,333,201,303,1fe;1ce,0fc,1ce,387,3cf,249,048,0fc;1fe,0b4,285,3cf,0cc,0cc,2fd,3b7;3cf,249,249,078,14a,1fe,078;1ce,387,084,084,387,387,1b6,0fc,0cc,1fe;3cf,249,078,0cc,0cc,3cf,2fd,3b7;2fd,3cf,37b,37b,333,132,102,1fe;17a,1ce,0cc,285,3b7,2fd,279,34b,279;048,279,2fd,37b,3cf,387,2cd,279,249,279;37b,37b,1fe,1ce,0cc,1ce,17a,078;387,1ce,048,0cc,084,387,387,3cf,14a,17a;0cc,3cf,37b,303,102,387,387,0fc;333,17a,1ce,3cf,2fd,0fc,0fc,1b6,17a;1ce,3cf,0cc,0b4,1fe,0fc,1b6,3b7,333;2fd,3cf,387,387,387,3cf,3cf,2fd,030;1ce,14a,1fe,1fe,37b,249,279,030,0fc;3cf,2cd,0fc,1b6,333,37b,249,078;1ce,3cf,34b,303,3cf,0cc,078,030,030;3cf,186,084,387,1b6,0fc,0b4,030;3cf,2cd,279,249,279,0fc,0cc,0fc;2fd,3cf,387,102,387,2b5,1b6,1fe;0b4,1fe,14a,0cc,2fd,3b7,231,333,030;3cf,249,249,279,231,078,1fe,17a;2fd,285,3cf,1fe,14a,34b,17a,078;37b,3cf,2fd,285,0cc,249,3cf,078;3cf,303,3b7,333,17a,1ce,34b,303,186,1fe;3cf,2fd,249,279,0fc,078,1fe,37b,0fc;285,3cf,37b,279,279,37b,3b7,186,303,1fe;3cf,387,285,285,2cd,249,231,2fd,3b7,0b4;201,387,1ce,0cc,3cf,2fd,279,078,078,078;1ce,1ce,1b6,0fc,1b6,3b7,333,17a,048,078;279,2cd,2cd,387,285,2fd,0fc,048,1fe;1fe,14a,303,3cf,2fd,231,231,231;0fc,0cc,3cf,1fe,37b,14a,078,048,0fc;3cf,186,0cc,186,084,186,0cc,14a,1fe,0fc;2fd,3b7,303,201,285,3b7,132,1fe;2fd,2b5,387,102,303,3b7,2fd,1fe,078;37b,37b,3cf,37b,132,132,078,0b4,0fc;34b,1ce,17a,333,030,1b6,0fc,17a,1fe;37b,132,1b6,1fe,0cc,0fc,078,0cc,0fc;231,2fd,37b,333,132,1fe,132,132,078;048,14a,3cf,2fd,0cc,084,0b4,0fc,1b6,3b7;279,2cd,3cf,14a,078,048,0cc,078;34b,1ce,14a,0cc,078,249,3cf,37b,078,1fe;0cc,0fc,17a,34b,249,3cf,249,078,0b4,0fc;3cf,2fd,249,3cf,2fd,0b4,285,2fd,3b7,030;3cf,1fe,0fc,1b6,084,0fc,0fc,048,078;3cf,249,078,078,030,279,249,3cf,2fd,030"

/** Dessins décodés : '#' = bloc, '.' = vide. Toutes les lignes sont incomplètes. */
val PICTURES: List<List<String>> by lazy {
    PIC_DATA.split(";").map { lvl ->
        lvl.split(",").map { hex ->
            val v = hex.toInt(16)
            (9 downTo 0).joinToString("") { b -> if (((v shr b) and 1) == 1) "#" else "." }
        }
    }
}

/** Niveau du mode PUZZLE : plateau de départ (du haut vers le bas) et pièces imposées (0=I 1=O 2=T 3=S 4=Z 5=J 6=L). */
class PuzzleLevel(val rows: List<String>, val seq: List<Int>)

/**
 * 100 niveaux du mode PUZZLE, de plus en plus difficiles (de 1 à 11 pièces imposées). Chaque niveau a été vérifié résoluble par un solveur.
 * Niveaux séparés par ';' ; format « lignes | pièces » : lignes en hexadécimal (10 bits, bit à 1 = bloc) séparées par ',' ;
 * pièces = suite de chiffres (0=I 1=O 2=T 3=S 4=Z 5=J 6=L).
 */
private const val PUZ_DATA = "30f|0;0c7,0f7|51;338,338,3fa|251;019,1b9,1ff|512;00f,21f,39f|140;3c0,3e8,3fc|362;083,0d7,3f7|215;061,1e9,3ef|156;038,07d,37f|152;027,227,37f|412;380,38b,3bf|635;398,398,3fc|136;00f,18f,1bf|156;330,3b8,3bd|426;209,20d,3cd|1166;008,03d,0ff|6025;040,1c1,3fd|5611;013,013,27f|4511;240,2cc,2cf|1510;380,3b0,3f0|1160;081,1c1,1df|1615;200,294,3bf|1426;009,309,39f|6115;003,023,3f7|1421;210,232,3be|6511;044,0f4,0ff,1ff|0155;200,230,3f3,3f3|11116;209,2a9,2a9,3b9|11650;021,021,3e5,3fd|60111;001,0a7,0af,1ef|22605;078,07f,37f|16605;191,191,199,19f|50010;030,038,27e,27f|14116;031,031,33b,33b|56111;280,284,2ee,2ef|20666;060,068,26d,3ef|40425;311,311,331,3f1|10145;003,007,307,30f|111115;021,029,07b,07f,27f|511146;300,310,313,373,37f|511061;001,201,2f1,3f9,3fd|312615;310,310,333,333,33f|151536;010,098,09d,0ff,1ff|234552;309,309,389,389,3cf|113105;080,091,09f,19f,1ff|531121;381,381,3e1,3e1,3e1|511016;240,240,3cc,3dc,3df|061111;031,031,037,0b7,3bf|116551;003,24b,2cb,2cb,2cf|110050;040,040,243,3db,3df,3df|1310066;211,291,291,2f1,2f1,3f7|6064105;080,387,3c7,3f7,3f7|3611155;200,248,3ca,3ca,3cb,3fb|6105661;062,062,062,366,36f,37f|2011560;0e0,0e3,2f3,3f3,3fb|0631521;241,241,349,349,379,37f|0515116;201,311,311,311,37f,37f|1030002;100,104,115,17d,3fd,3fd|0500415;011,051,05b,05b,0ff,2ff|3406515;240,240,260,268,3fc,3fe|20360111;001,001,30d,31d,31f,39f|11035065;080,084,087,08f,0ff,0ff|01656616;008,00d,20f,20f,21f,33f|31260110;020,031,33f,33f,33f|15525636;202,2c2,3f2,3f2,3f6|10001565;001,301,301,31d,31f,33f|54450600;200,223,273,2fb,3fb|30012130;301,301,301,301,31f,37f|05116106;060,061,36b,36f,36f|10205551;210,210,212,217,21f,25f,3df|121051051;020,020,127,12f,32f,32f,32f|101154051;019,019,019,019,219,3fd,3fd|105641016;080,0cb,0cb,0db,1df,1ff|500501400;084,0c4,0c4,0e4,3e4,3e5,3fd|611001500;030,032,032,032,333,377,37f|045104160;201,281,281,381,3a5,3ad,3bf|000434156;200,301,305,305,305,3ef,3ef|065011560;008,109,119,119,399,39d,3df|060014200;208,208,228,22e,23e,33e,3bf|100325060;001,041,341,34b,34b,34b,3cb,3eb|0100231061;084,085,285,287,287,297,397,39f|6010500011;200,240,341,341,341,357,3f7,3f7|0101001250;012,352,352,356,356,376,37f|0000611220;081,085,385,385,385,385,3d5,3d7|0611001065;108,10e,30f,30f,32f,32f,33f|6101052020;00c,20c,20c,20c,24f,24f,27f,37f|0021210616;020,026,026,02f,03f,03f,23f,37f|2160064166;206,206,206,3c7,3c7,3cf,3ef|0206040065;280,280,290,290,2f2,2f3,2fb,2ff|0002110610;201,201,205,205,247,2c7,3c7,3df,3df|20001151501;100,100,308,3cf,3cf,3cf,3cf,3df|16412305615;001,001,201,213,393,393,3db,3df,3df|10010162211;204,20c,22c,22c,32c,32c,32f,3af,3af|00006050010;001,001,081,281,2b1,2f7,2f7,2ff,2ff|16050561151;020,020,0e0,0f8,2f8,2fc,2fc,2fc,3fe|20506100055;001,101,101,103,3c3,3d3,3f3,3f3,3f7|01020303512;210,212,292,292,292,292,2de,2ff,2ff|00002000216;221,229,229,229,229,269,369,379,37f|10104510510;014,094,094,0b4,0bc,2bc,2bc,3be,3fe|00610126501;006,206,24f,2ef,2ff,2ff,2ff|00012301065"

val PUZZLES: List<PuzzleLevel> by lazy {
    PUZ_DATA.split(";").map { lvl ->
        val (rowsPart, seqPart) = lvl.split("|")
        PuzzleLevel(
            rowsPart.split(",").map { hex ->
                val v = hex.toInt(16)
                (9 downTo 0).joinToString("") { b -> if (((v shr b) and 1) == 1) "#" else "." }
            },
            seqPart.map { it - '0' },
        )
    }
}

/** Ordre des pièces : I, O, T, S, Z, J, L, puis la bombe du mode CHAOS (index 7) — 4 rotations pré-calculées. */
val SHAPES: List<List<List<Pair<Int, Int>>>> = listOf(
    4 to listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
    2 to listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
    3 to listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
    3 to listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
    3 to listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
    3 to listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
    3 to listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
    1 to listOf(0 to 0),
).map { (n, base) ->
    val r = mutableListOf(base)
    repeat(3) { r.add(r.last().map { (x, y) -> (n - 1 - y) to x }) }
    r
}

data class Piece(val type: Int, val rot: Int = 0, val x: Int = 3, val y: Int = 0) {
    val cells get() = shapeRots(type)[rot].map { (dx, dy) -> (x + dx) to (y + dy) }
}

/** Animation d'effacement en cours : lignes (positions avant effacement), nombre de lignes, enchaînement, durée en ms. */
class ClearFx(val id: Int, val rows: List<Int>, val count: Int, val chain: Int, val duration: Int)

private fun garbageFor(n: Int) = when { n == 2 -> 1; n == 3 -> 2; n >= 4 -> 4; else -> 0 }

class Game(private val prefs: SharedPreferences) {
    var board by mutableStateOf(IntArray(COLS * ROWS))
    var piece by mutableStateOf<Piece?>(null)
    var next by mutableIntStateOf(0)
    var hold by mutableIntStateOf(-1)
    var score by mutableIntStateOf(0)
    var lines by mutableIntStateOf(0)
    var best by mutableIntStateOf(0)            // meilleur score du mode en cours
    var mode by mutableStateOf(Mode.CLASSIC)
    var picture by mutableIntStateOf(1)         // DESSIN : tableau en cours ; PUZZLE : niveau en cours
    var timeMs by mutableLongStateOf(0L)        // temps de jeu actif (arrondi au dixième)
    var state by mutableStateOf(GState.READY)
    var result by mutableStateOf("GAME OVER")
    var canRetry by mutableStateOf(false)
    var banner by mutableStateOf("")
    var fx by mutableStateOf<ClearFx?>(null)
    var missions by mutableStateOf<List<Pair<Int, Int>>>(emptyList())   // (lignes d'un coup, fois restantes)
    var oppBoard by mutableStateOf(IntArray(COLS * ROWS))
    var piecesLeft by mutableIntStateOf(0)
    var picDone by mutableStateOf(loadDone())     // DESSIN : numéros (1..100) des tableaux validés
    var chaosDetail by mutableStateOf("")     // CHAOS : explication de l'événement
    var chaosLabel by mutableStateOf("")      // CHAOS : événement actif sur la pièce en cours (vide = aucun)
    var onEvent: (Int) -> Unit = {}   // 0 = pose, n>0 = n lignes
    var onSfx: (String) -> Unit = {}  // bruitages / vibrations : rot, lock, clear1..3, tetris, combo, bomb, over, win, mission, level, event
    var picStars by mutableStateOf(loadStars("pic_stars"))        // DESSIN : étoiles (1 à 3) par tableau validé
    var puzDone by mutableStateOf(loadSet("puz_done", PUZZLES.size))   // PUZZLE : niveaux validés
    var puzStars by mutableStateOf(loadStars("puz_stars"))
    var chaosOn by mutableStateOf(loadChaos())                    // CHAOS : événements activés (0 à 5)
    var pack by mutableIntStateOf(prefs.getInt("pack", 0).coerceIn(0, SHAPE_PACKS.lastIndex))   // FORMES : pack de formes choisi
    var duelLevel by mutableIntStateOf(prefs.getInt("duel_lvl", 1).coerceIn(0, 2))   // DUEL : 0 facile, 1 normal, 2 difficile

    val level get() = (lines - levelBase) / 10 + 1
    val delayMs: Long
        get() {
            if (mode == Mode.PUZZLE) return 1200L
            val base = maxOf(80L, 800L - (level - 1) * 70L)
            return if (mode == Mode.CHAOS && evSpeed) maxOf(60L, base / 3) else base
        }

    private var bag = mutableListOf<Int>()
    private var canHold = true
    private var levelBase = 0   // lignes déjà faites au début du tableau en cours (mode DESSIN : le niveau repart de 1)
    private var lastFall = 0L
    private var lockAt = 0L
    private var lockResets = 0
    private var lastTickAt = 0L
    private var timeAcc = 0L
    private var garbageAt = 0L
    private var bannerUntil = 0L
    private var clearRows: List<Int> = emptyList()
    private var clearAt = 0L
    private var chain = 0
    private var fxId = 0
    private var seq: List<Int> = emptyList()
    private var seqIdx = 0
    private var evSpeed = false     // CHAOS : événement de la pièce en cours
    private var evReverse = false
    private var evNoRot = false
    private var evGhost = false     // pièce fantôme : clignote 4 s puis disparaît
    private var spawnedAt = 0L
    private var botAt = 0L
    private var bot = Bot()
    private var statBuf = 0L        // temps de jeu pas encore écrit dans les statistiques
    private var levelStartAcc = 0L  // DESSIN / PUZZLE : début du niveau en cours (pour les étoiles)

    private fun resetTimers() { lastFall = SystemClock.uptimeMillis(); lockAt = 0L; lockResets = 0 }

    /** Un déplacement ou une rotation réussis repoussent le verrouillage, mais pas indéfiniment. */
    private fun touched() {
        if (lockAt != 0L && lockResets < MAX_RESETS) { lockAt = SystemClock.uptimeMillis(); lockResets++ }
    }

    private fun say(text: String, ms: Long = 1600L) { banner = text; bannerUntil = timeAcc + ms }

    private fun draw(): Int {
        if (mode == Mode.PUZZLE) return if (seqIdx < seq.size) seq[seqIdx++] else -1
        if (mode == Mode.PACKS) {   // sac de 8 : chaque forme du pack sort une fois avant de recommencer
            if (bag.isEmpty()) bag = (0 until PACK_SIZE).map { packType(pack, it) }.shuffled().toMutableList()
            return bag.removeAt(bag.lastIndex)
        }
        if (bag.isEmpty()) bag = (0..6).shuffled().toMutableList()
        return bag.removeAt(bag.lastIndex)
    }

    /** Clé du record : un record par pack de formes pour le mode FORMES. */
    private fun bestKey(m: Mode) = if (m == Mode.PACKS) "best_PACKS_" + pack else "best_" + m.name

    private fun loadBest(m: Mode) {
        best = prefs.getInt(bestKey(m), if (m == Mode.CLASSIC) prefs.getInt("best", 0) else 0)
    }

    /** Choisit un pack de formes et lance une partie du mode FORMES. */
    fun startPack(i: Int) {
        pack = i.coerceIn(0, SHAPE_PACKS.lastIndex)
        prefs.edit().putInt("pack", pack).apply()
        start(Mode.PACKS)
    }

    /** Record d'un pack de formes. */
    fun packRecord(i: Int): String = "Record : " + prefs.getInt("best_PACKS_" + i, 0)

    /** Texte du record affiché sur l'écran de sélection. */
    fun recordLabel(m: Mode): String =
        if (m == Mode.PICTURE) "Tableaux validés : " + picDone.size + "/" + PICTURES.size else if (m == Mode.PUZZLE) "Niveaux validés : " + puzDone.size + "/" + PUZZLES.size else if (m == Mode.PACKS) "Record : " + SHAPE_PACKS.indices.maxOf { prefs.getInt("best_PACKS_" + it, 0) } else "Record : " + prefs.getInt("best_" + m.name, if (m == Mode.CLASSIC) prefs.getInt("best", 0) else 0)

    /** Plateau de départ d'un tableau du mode DESSIN : les blocs du dessin portent la valeur 9. */
    private fun pictureBoard(index: Int): IntArray {
        val pic = PICTURES[index % PICTURES.size]
        val b = IntArray(COLS * ROWS)
        pic.forEachIndexed { i, row ->
            val y = ROWS - pic.size + i
            row.forEachIndexed { x, ch -> if (ch == '#') b[y * COLS + x] = 9 }
        }
        return b
    }

    private fun loadPuzzle() {
        val lvl = PUZZLES[(picture - 1).coerceIn(0, PUZZLES.size - 1)]
        val b = IntArray(COLS * ROWS)
        lvl.rows.forEachIndexed { i, row ->
            val y = ROWS - lvl.rows.size + i
            row.forEachIndexed { x, ch -> if (ch == '#') b[y * COLS + x] = 9 }
        }
        board = b; seq = lvl.seq; seqIdx = 0
    }

    private fun newMission(taken: List<Int>): Pair<Int, Int> {
        val k = listOf(2, 2, 3, 3, 4).filter { it !in taken }.random()
        val c = when (k) { 2 -> (3..5).random(); 3 -> (2..4).random(); else -> (1..2).random() }
        return k to c
    }

    fun start(m: Mode = mode, retry: Boolean = false, level: Int = 1) {
        flushStats()
        mode = m; timeAcc = 0L; timeMs = 0L; levelStartAcc = 0L; loadBest(m)
        statAdd("games", 1); statAdd("games_" + m.name, 1)
        score = 0; lines = 0; hold = -1; banner = ""; fx = null; clearRows = emptyList()
        canRetry = false; result = "GAME OVER"; chain = 0; levelBase = 0
        clearChaos()
        bag.clear(); eventBag.clear()
        when (m) {
            Mode.PICTURE -> { picture = level.coerceIn(1, PICTURES.size); board = pictureBoard(picture - 1) }
            Mode.PUZZLE -> { picture = level.coerceIn(1, PUZZLES.size); loadPuzzle() }
            else -> { picture = 1; board = IntArray(COLS * ROWS) }
        }
        if (m == Mode.MISSIONS) { val a = newMission(emptyList()); missions = listOf(a, newMission(listOf(a.first))) }
        if (m == Mode.DUEL) { bot = Bot(botNoise()); oppBoard = bot.board.copyOf(); botAt = 3000L }
        garbageAt = 15_000L
        next = draw(); state = GState.PLAYING
        lastTickAt = SystemClock.uptimeMillis()
        spawnNext()
        if (m == Mode.PACKS) say("PACK " + SHAPE_PACKS[pack].name, 2200L)
    }

    fun toMenu() { state = GState.READY }

    // ---------- statistiques ----------

    fun statGet(key: String): Long = prefs.getLong("st_" + key, 0L)
    private fun statAdd(key: String, v: Long) { prefs.edit().putLong("st_" + key, statGet(key) + v).apply() }
    private fun flushStats() { if (statBuf > 0L) { statAdd("time", statBuf); statBuf = 0L } }

    // ---------- niveaux validés et étoiles ----------

    private fun loadSet(key: String, max: Int): Set<Int> =
        (prefs.getString(key, "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.filter { it in 1..max }.toSet()

    private fun loadStars(key: String): Map<Int, Int> =
        (prefs.getString(key, "") ?: "").split(",").mapNotNull { e ->
            val p = e.split(":")
            val n = p.getOrNull(0)?.toIntOrNull()
            val s = p.getOrNull(1)?.toIntOrNull()
            if (n != null && s != null) n to s else null
        }.toMap()

    private fun starsFor(elapsedMs: Long, parMs: Long): Int = if (elapsedMs <= parMs) 3 else if (elapsedMs <= 2 * parMs) 2 else 1

    private fun saveStars(pic: Boolean, n: Int, s: Int) {
        val cur = if (pic) picStars else puzStars
        if ((cur[n] ?: 0) >= s) return
        val m = cur + (n to s)
        if (pic) picStars = m else puzStars = m
        prefs.edit().putString(if (pic) "pic_stars" else "puz_stars", m.entries.joinToString(",") { it.key.toString() + ":" + it.value }).apply()
    }

    private fun markPuzDone(n: Int) {
        if (n in puzDone) return
        puzDone = puzDone + n
        prefs.edit().putString("puz_done", puzDone.sorted().joinToString(",")).apply()
    }

    /** Premier niveau du Puzzle non validé (ou le dernier si tout est validé). */
    fun firstTodoPuz(): Int = (1..PUZZLES.size).firstOrNull { it !in puzDone } ?: PUZZLES.size

    // ---------- réglages des modes ----------

    private fun loadChaos(): Set<Int> {
        val s = prefs.getString("chaos_on", null) ?: return (0..5).toSet()
        return s.split(",").mapNotNull { it.toIntOrNull() }.filter { it in 0..5 }.toSet()
    }

    fun setChaosOn(id: Int, on: Boolean) {
        chaosOn = if (on) chaosOn + id else chaosOn - id
        eventBag.clear()
        prefs.edit().putString("chaos_on", chaosOn.sorted().joinToString(",")).apply()
    }

    fun changeDuelLevel(l: Int) { duelLevel = l; prefs.edit().putInt("duel_lvl", l).apply() }

    private fun botNoise(): Float = when (duelLevel) { 0 -> 4f; 2 -> 0.2f; else -> 1.2f }

    // ---------- sauvegarde de la partie en cours ----------

    fun hasSave(): Boolean = prefs.getString("save", null) != null
    fun clearSave() { prefs.edit().remove("save").apply() }

    /** Texte du bouton « reprendre » de l'écran de sélection. */
    fun saveLabel(): String = try {
        val o = JSONObject(prefs.getString("save", "") ?: "")
        val m = Mode.valueOf(o.getString("mode"))
        m.title + (if (m == Mode.PACKS) " " + SHAPE_PACKS[o.optInt("pack", 0).coerceIn(0, SHAPE_PACKS.lastIndex)].name else "") + " · score " + o.getInt("score")
    } catch (e: Exception) { "" }

    private fun ints(s: String): List<Int> = if (s.isEmpty()) emptyList() else s.split(",").map { it.trim().toInt() }

    /** Enregistre la partie (appelée quand l'application passe en arrière-plan). */
    fun saveGame() {
        if (state == GState.OVER) { clearSave(); return }
        if (state != GState.PAUSED && state != GState.PLAYING) return
        flushStats()
        if (clearRows.isNotEmpty()) { finishClear(); if (state == GState.OVER) return }
        val o = JSONObject()
        o.put("mode", mode.name); o.put("pack", pack)
        o.put("board", board.joinToString(","))
        val p = piece
        o.put("piece", if (p == null) "" else listOf(p.type, p.rot, p.x, p.y).joinToString(","))
        o.put("next", next); o.put("hold", hold); o.put("score", score); o.put("lines", lines)
        o.put("picture", picture); o.put("timeAcc", timeAcc); o.put("levelBase", levelBase); o.put("levelStart", levelStartAcc)
        o.put("canHold", canHold); o.put("bag", bag.joinToString(","))
        o.put("garbageAt", garbageAt); o.put("botAt", botAt); o.put("spawnedAt", spawnedAt); o.put("seqIdx", seqIdx)
        o.put("missions", missions.joinToString(";") { it.first.toString() + "," + it.second })
        o.put("bot", bot.board.joinToString(",")); o.put("botDead", bot.dead); o.put("botBag", bot.bag.joinToString(","))
        o.put("evSpeed", evSpeed); o.put("evReverse", evReverse); o.put("evNoRot", evNoRot); o.put("evGhost", evGhost)
        o.put("chaosLabel", chaosLabel); o.put("chaosDetail", chaosDetail); o.put("eventBag", eventBag.joinToString(","))
        prefs.edit().putString("save", o.toString()).apply()
    }

    /** Recharge la partie sauvegardée ; elle démarre en pause. Renvoie false si la sauvegarde est absente ou illisible. */
    fun restoreGame(): Boolean {
        val s = prefs.getString("save", null) ?: return false
        return try {
            val o = JSONObject(s)
            val m = Mode.valueOf(o.getString("mode"))
            val b = ints(o.getString("board")).toIntArray()
            require(b.size == COLS * ROWS)
            pack = o.optInt("pack", 0).coerceIn(0, SHAPE_PACKS.lastIndex)
            mode = m; loadBest(m); board = b
            val pv = ints(o.getString("piece"))
            piece = if (pv.size == 4) Piece(pv[0], pv[1], pv[2], pv[3]) else null
            next = o.getInt("next"); hold = o.getInt("hold"); score = o.getInt("score"); lines = o.getInt("lines")
            picture = o.getInt("picture"); timeAcc = o.getLong("timeAcc"); timeMs = timeAcc / 100 * 100
            levelBase = o.getInt("levelBase"); levelStartAcc = o.getLong("levelStart")
            canHold = o.getBoolean("canHold"); bag = ints(o.getString("bag")).toMutableList()
            garbageAt = o.getLong("garbageAt"); botAt = o.getLong("botAt"); spawnedAt = o.getLong("spawnedAt")
            seqIdx = o.getInt("seqIdx")
            seq = if (m == Mode.PUZZLE) PUZZLES[(picture - 1).coerceIn(0, PUZZLES.size - 1)].seq else emptyList()
            if (m == Mode.PUZZLE) piecesLeft = 1 + (if (next >= 0) 1 else 0) + (seq.size - seqIdx)
            missions = o.getString("missions").split(";").filter { it.isNotEmpty() }.map { e -> val q = e.split(","); q[0].toInt() to q[1].toInt() }
            if (m == Mode.DUEL) {
                bot = Bot(botNoise())
                bot.board = ints(o.getString("bot")).toIntArray(); bot.dead = o.getBoolean("botDead")
                bot.bag = ints(o.getString("botBag")).toMutableList()
                oppBoard = bot.board.copyOf()
            }
            evSpeed = o.getBoolean("evSpeed"); evReverse = o.getBoolean("evReverse")
            evNoRot = o.getBoolean("evNoRot"); evGhost = o.getBoolean("evGhost")
            chaosLabel = o.getString("chaosLabel"); chaosDetail = o.getString("chaosDetail")
            eventBag = ints(o.getString("eventBag")).toMutableList()
            banner = ""; fx = null; clearRows = emptyList(); canRetry = false; result = "GAME OVER"; chain = 0
            state = GState.PAUSED
            true
        } catch (e: Exception) { false }
    }

    private fun loadDone(): Set<Int> =
        (prefs.getString("pic_done", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.filter { it in 1..PICTURES.size }.toSet()

    private fun markDone(n: Int) {
        if (n in picDone) return
        picDone = picDone + n
        prefs.edit().putString("pic_done", picDone.sorted().joinToString(",")).apply()
    }

    /** Premier tableau non validé (ou le dernier si tout est validé). */
    fun firstTodo(): Int = (1..PICTURES.size).firstOrNull { it !in picDone } ?: PICTURES.size

    fun pause() { if (state == GState.PLAYING) { state = GState.PAUSED; flushStats() } }
    fun resume() {
        if (state != GState.PAUSED) return
        state = GState.PLAYING
        val now = SystemClock.uptimeMillis()
        lastFall = now
        lastTickAt = now
        if (lockAt != 0L) lockAt = now
        if (clearRows.isNotEmpty()) clearAt = now + 150
    }

    private fun fits(p: Piece) = p.cells.all { (x, y) ->
        x in 0 until COLS && y < ROWS && (y < 0 || board[y * COLS + x] == 0)
    }

    private fun clearChaos() { evSpeed = false; evReverse = false; evNoRot = false; evGhost = false; chaosLabel = ""; chaosDetail = "" }

    private var eventBag = mutableListOf<Int>()

    /**
     * CHAOS : tirage sans remise dans un sac de 12 (6 pièces normales + chacun des 6 événements une fois), mélangé.
     * -1 = pièce normale ; 0..5 = événement spécial. Garantit 50 % / 50 % et au moins un de chaque événement toutes les 12 pièces.
     */
    private fun rollEvent(): Int {
        if (chaosOn.isEmpty()) return -1
        if (eventBag.isEmpty()) eventBag = (List(chaosOn.size) { -1 } + chaosOn.toList()).toMutableList().apply { shuffle() }
        return eventBag.removeAt(eventBag.lastIndex)
    }

    /** Pièce revenue de la réserve : 1 chance sur 2 d'avoir un événement activé, sans bombe. */
    private fun rollHoldEvent(): Int {
        val c = chaosOn.filter { it != 5 }
        return if (c.isEmpty() || (0..1).random() == 0) -1 else c.random()
    }

    /** Applique l'événement e (0 vitesse, 1 inversé, 2 rotation bloquée, 3 fantôme, 4 barre magique, 5 bombe) ; renvoie le type de pièce lancé. */
    private fun applyEvent(e: Int, t: Int): Int {
        clearChaos()
        var type = t
        when (e) {
            0 -> { evSpeed = true; chaosLabel = "VITESSE ×3"; chaosDetail = "Cette pièce tombe 3 fois plus vite." }
            1 -> { evReverse = true; chaosLabel = "COMMANDES INVERSÉES"; chaosDetail = "◀ et ▶ sont échangés pour cette pièce." }
            2 -> { evNoRot = true; chaosLabel = "ROTATION BLOQUÉE"; chaosDetail = "Impossible de tourner cette pièce." }
            3 -> { evGhost = true; chaosLabel = "PIÈCE FANTÔME"; chaosDetail = "Elle clignote 4 secondes puis disparaît : placez-la de mémoire." }
            4 -> { type = 0; chaosLabel = "BARRE MAGIQUE"; chaosDetail = "Cette pièce devient une barre : parfaite pour un Tétris !" }
            5 -> { chaosLabel = "BOMBE !"; chaosDetail = "Détruit les blocs autour de son point de chute (zone 3×3)." }
        }
        if (e >= 0) onSfx("event")
        return type
    }

    private fun spawn(t: Int, ev: Int = -2) {
        var type = t
        if (mode == Mode.CHAOS) {
            val e = if (t == 7) 5 else if (ev != -2) ev else rollHoldEvent()
            type = applyEvent(e, t)
        }
        val p = Piece(type, 0, spawnX(type), 0); piece = p; canHold = true; resetTimers(); spawnedAt = timeAcc / 100 * 100
        if (!fits(p)) gameOver()
    }

    /** 1 = visible ; 0,15 / 1 en alternance pendant les 4 premières secondes d'une pièce fantôme ; 0 = invisible ensuite. */
    fun pieceAlpha(): Float {
        if (!evGhost) return 1f
        val age = timeMs - spawnedAt
        return if (age >= 4000L) 0f else if ((age / 250L) % 2L == 0L) 1f else 0.15f
    }

    private fun spawnNext() {
        var e = -1
        if (mode == Mode.CHAOS) {
            e = rollEvent()
            if (e == 5) { spawn(7); return }   // bombe : pièce en plus, la file n'avance pas
        }
        val t = next
        if (t < 0) {   // PUZZLE : plus de pièces et le dessin n'est pas effacé
            result = "ÉCHEC — NIVEAU " + picture; canRetry = true; state = GState.OVER; saveBest(); flushStats(); clearSave(); onSfx("over"); return
        }
        next = draw()
        if (mode == Mode.PUZZLE) piecesLeft = 1 + (if (next >= 0) 1 else 0) + (seq.size - seqIdx)
        spawn(t, e)
    }

    private fun saveBest() {
        if (score > best) { best = score; prefs.edit().putInt(bestKey(mode), best).apply() }
    }

    private fun gameOver() {
        if (state == GState.OVER) return
        result = if (mode == Mode.DUEL) "DÉFAITE" else "GAME OVER"
        state = GState.OVER; saveBest(); flushStats(); clearSave()
        if (mode == Mode.DUEL) statAdd("duel_loss", 1)
        onSfx("over")
    }

    private fun win(msg: String) {
        if (state == GState.OVER) return
        result = msg; state = GState.OVER; saveBest(); flushStats(); clearSave()
        if (mode == Mode.DUEL) statAdd("duel_win", 1)
        onSfx("win")
    }

    fun move(dx0: Int) {
        val p = piece ?: return
        if (state != GState.PLAYING) return
        val dx = if (mode == Mode.CHAOS && evReverse) -dx0 else dx0
        p.copy(x = p.x + dx).let { if (fits(it)) { piece = it; touched() } }
    }

    fun rotate() {
        val p = piece ?: return
        if (state != GState.PLAYING) return
        if (mode == Mode.CHAOS && evNoRot) return
        if (!fits(p.copy(y = p.y + 1))) return   // la pièce touche : plus de rotation possible
        val r = (p.rot + 1) % 4
        for (k in intArrayOf(0, -1, 1, -2, 2)) {          // « wall kicks » simplifiés
            val q = p.copy(rot = r, x = p.x + k)
            if (fits(q)) { piece = q; touched(); onSfx("rot"); return }
            val up = q.copy(y = q.y - 1)
            if (fits(up)) { piece = up; touched(); onSfx("rot"); return }
        }
    }

    /** Appelée très souvent : temps, événements du mode, animation d'effacement, gravité et verrouillage. */
    fun tick(now: Long) {
        if (state != GState.PLAYING) return
        val dt = now - lastTickAt
        timeAcc += dt; statBuf += dt; lastTickAt = now
        if (statBuf >= 5000L) flushStats()
        val shown = timeAcc / 100 * 100
        if (shown != timeMs) timeMs = shown
        if (banner.isNotEmpty() && timeAcc >= bannerUntil) banner = ""
        if (clearRows.isNotEmpty()) { if (now >= clearAt) finishClear(); return }
        when (mode) {
            Mode.SURVIVAL -> if (timeAcc >= garbageAt) {
                addGarbage(1)
                garbageAt = timeAcc + maxOf(6000L, 15000L - (timeAcc / 20000L) * 1000L)
            }
            Mode.DUEL -> if (timeAcc >= botAt) botTurn()
            else -> {}
        }
        if (state != GState.PLAYING) return
        val p = piece ?: return
        val q = p.copy(y = p.y + 1)
        if (fits(q)) {
            lockAt = 0L
            if (now - lastFall >= delayMs) { piece = q; lastFall = now; lockResets = 0 }
        } else {
            if (lockAt == 0L) lockAt = now
            if (now - lockAt >= LOCK_MS) lock()
        }
    }

    /** Descend d'une ligne sans verrouiller (le verrouillage reste géré par la gravité). */
    fun softDrop() {
        val p = piece ?: return
        if (state != GState.PLAYING) return
        val q = p.copy(y = p.y + 1)
        if (fits(q)) { piece = q; score += 1 }
    }

    fun ghost(): Piece? {
        var p = piece ?: return null
        while (fits(p.copy(y = p.y + 1))) p = p.copy(y = p.y + 1)
        return p
    }

    fun hardDrop() {
        val p = piece ?: return
        if (state != GState.PLAYING) return
        val g = ghost() ?: return
        score += 2 * (g.y - p.y); piece = g; lock()
    }

    fun holdPiece() {
        val p = piece ?: return
        if (state != GState.PLAYING || !canHold || mode == Mode.PUZZLE || p.type == 7) return
        val h = hold; hold = p.type
        if (h < 0) spawnNext() else spawn(h)
        canHold = false
    }

    // ---------- pose, effacement, enchaînements ----------

    private fun lock() {
        val p = piece ?: return
        clearChaos()
        if (p.type == 7) { explode(p); return }
        val b = board.copyOf()
        p.cells.forEach { (x, y) -> if (y >= 0) b[y * COLS + x] = p.type + 1 }
        board = b; piece = null; chain = 0
        if (!detectClear()) { onEvent(0); onSfx("lock"); afterClear() }
    }

    /** Marque les lignes pleines (elles restent visibles pendant l'animation) ; renvoie false s'il n'y en a pas. */
    private fun detectClear(): Boolean {
        val rows = (0 until ROWS).filter { r -> (0 until COLS).all { board[r * COLS + it] != 0 } }
        if (rows.isEmpty()) return false
        val n = rows.size
        val lv = level
        chain++
        score += intArrayOf(0, 100, 300, 500, 800)[minOf(n, 4)] * lv * chain
        lines += n
        if (chain > 1) say("COMBO ×" + chain)
        onSfx(if (n >= 4) "tetris" else if (chain > 1) "combo" else "clear" + n)
        statAdd("lines", n.toLong()); if (n >= 4) statAdd("tetris", 1)
        if (mode == Mode.MISSIONS) missionProgress(n)
        if (mode == Mode.DUEL) sendToBot(n)
        val dur = when (n) { 1 -> 280; 2 -> 420; 3 -> 560; else -> 1100 }
        fxId++
        fx = ClearFx(fxId, rows, n, chain, dur)
        clearRows = rows
        clearAt = SystemClock.uptimeMillis() + dur
        return true
    }

    private fun finishClear() {
        val rows = clearRows.toSet()
        val kept = (0 until ROWS).filter { it !in rows }
        val nb = IntArray(COLS * ROWS)
        kept.forEachIndexed { i, r -> System.arraycopy(board, r * COLS, nb, (ROWS - kept.size + i) * COLS, COLS) }
        board = nb; clearRows = emptyList()
        onEvent(rows.size)
        if (mode == Mode.CASCADE) {          // gravité « naïve » : chaque bloc retombe, ce qui peut former de nouvelles lignes
            applyGravity()
            if (detectClear()) return
        }
        afterClear()
    }

    private fun applyGravity() {
        val nb = IntArray(COLS * ROWS)
        for (x in 0 until COLS) {
            var y = ROWS - 1
            for (r in ROWS - 1 downTo 0) {
                val v = board[r * COLS + x]
                if (v != 0) { nb[y * COLS + x] = v; y-- }
            }
        }
        board = nb
    }

    private fun afterClear() {
        if (state != GState.PLAYING) return
        if (mode == Mode.PICTURE && board.none { it == 9 }) {
            score += 1000
            markDone(picture)
            val st = starsFor(timeAcc - levelStartAcc, 9000L * PICTURES[picture - 1].size)
            saveStars(true, picture, st)
            if (picture >= PICTURES.size) { win("BRAVO ! LES " + PICTURES.size + " TABLEAUX"); return }
            onSfx("level")
            picture++; levelBase = lines; levelStartAcc = timeAcc; board = pictureBoard(picture - 1)
            say("TABLEAU RÉUSSI " + "★".repeat(st), 2200L)
        }
        if (mode == Mode.PUZZLE && board.none { it == 9 }) {
            score += 500 + 100 * (seq.size - seqIdx + (if (next >= 0) 1 else 0))
            markPuzDone(picture)
            val st = starsFor(timeAcc - levelStartAcc, 10000L * PUZZLES[picture - 1].seq.size)
            saveStars(false, picture, st)
            picture++
            if (picture > PUZZLES.size) { win("BRAVO ! TOUS LES NIVEAUX"); return }
            onSfx("level")
            say("NIVEAU RÉUSSI " + "★".repeat(st), 2200L); levelStartAcc = timeAcc; loadPuzzle(); next = draw()
        }
        saveBest()
        spawnNext()
    }

    /** CHAOS : la bombe détruit les blocs autour de son point de chute. */
    private fun explode(p: Piece) {
        val (cx, cy) = p.cells[0]
        val b = board.copyOf()
        var cnt = 0
        for (dy in -1..1) for (dx in -1..1) {
            val x = cx + dx; val y = cy + dy
            if (x in 0 until COLS && y in 0 until ROWS && b[y * COLS + x] != 0) { b[y * COLS + x] = 0; cnt++ }
        }
        board = b; piece = null; chain = 0
        score += 20 * cnt
        say("BOUM !", 1000)
        onSfx("bomb")
        onEvent(3)
        if (!detectClear()) afterClear()
    }

    // ---------- événements propres aux modes ----------

    private fun missionProgress(n: Int) {
        val updated = missions.map { (k, c) -> if (n >= k) k to (c - 1) else k to c }.toMutableList()
        if (updated.size != 2) { missions = updated; return }
        for (i in 0..1) {
            if (updated[i].second <= 0) {
                val k = updated[i].first
                score += 500 * k
                say("MISSION ACCOMPLIE ! +" + 500 * k)
                onSfx("mission")
                updated[i] = newMission(listOf(updated[1 - i].first))
            }
        }
        missions = updated
    }

    /** SURVIE / DUEL : ajoute des lignes pirates (avec un trou) par le bas ; si le haut est occupé, c'est perdu. */
    private fun addGarbage(rows: Int) {
        val hole = (0 until COLS).random()
        repeat(rows) {
            val topOccupied = (0 until COLS).any { board[it] != 0 }
            val nb = IntArray(COLS * ROWS)
            System.arraycopy(board, COLS, nb, 0, (ROWS - 1) * COLS)
            for (x in 0 until COLS) nb[(ROWS - 1) * COLS + x] = if (x == hole) 0 else 8
            board = nb
            if (topOccupied) { gameOver(); return }
        }
        val p = piece
        if (p != null) {
            var q: Piece = p
            var tries = 0
            while (!fits(q) && tries <= rows) { q = q.copy(y = q.y - 1); tries++ }
            if (fits(q)) piece = q else gameOver()
        }
    }

    private fun sendToBot(n: Int) {
        val g = garbageFor(n)
        if (g <= 0) return
        bot.addGarbage(g)
        oppBoard = bot.board.copyOf()
        if (bot.dead) { score += 2000; win("VICTOIRE !") }
    }

    private fun botTurn() {
        val c = bot.play()
        val (slow, fast) = when (duelLevel) { 0 -> 2400L to 1300L; 2 -> 1000L to 350L; else -> 1400L to 500L }
        botAt = timeAcc + maxOf(fast, slow - (timeAcc / 15000L) * 100L)
        oppBoard = bot.board.copyOf()
        if (bot.dead) { score += 2000; win("VICTOIRE !"); return }
        val g = garbageFor(c)
        if (g > 0) { say("ATTAQUE ! +" + g); addGarbage(g) }
    }
}

/** Adversaire du mode DUEL : place chaque pièce avec une heuristique simple (hauteur, trous, irrégularité, lignes). */
class Bot(private val noise: Float = 1.2f) {
    var board = IntArray(COLS * ROWS)
    var dead = false
    var bag = mutableListOf<Int>()

    private fun ok(cells: List<Pair<Int, Int>>) = cells.all { (x, y) ->
        x in 0 until COLS && y < ROWS && (y < 0 || board[y * COLS + x] == 0)
    }

    private fun evaluate(p: Piece): Float {
        val b = board.copyOf()
        for ((x, y) in p.cells) if (y >= 0) b[y * COLS + x] = 1
        var full = 0
        for (r in 0 until ROWS) if ((0 until COLS).all { b[r * COLS + it] != 0 }) full++
        val h = IntArray(COLS)
        var holes = 0
        for (x in 0 until COLS) {
            var seen = false
            for (y in 0 until ROWS) {
                if (b[y * COLS + x] != 0) { if (!seen) { h[x] = ROWS - y; seen = true } } else if (seen) holes++
            }
        }
        var bump = 0
        for (x in 0 until COLS - 1) bump += abs(h[x] - h[x + 1])
        return -0.51f * h.sum() + 0.76f * full - 0.36f * holes - 0.18f * bump
    }

    /** Joue une pièce ; renvoie le nombre de lignes effacées. */
    fun play(): Int {
        if (bag.isEmpty()) bag = (0..6).shuffled().toMutableList()
        val type = bag.removeAt(bag.lastIndex)
        var best: Piece? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (rot in 0..3) for (x in -2..COLS) {
            var p = Piece(type, rot, x, 0)
            if (!ok(p.cells)) continue
            while (ok(p.copy(y = p.y + 1).cells)) p = p.copy(y = p.y + 1)
            val sc = evaluate(p) + Random.nextFloat() * noise
            if (sc > bestScore) { bestScore = sc; best = p }
        }
        val p = best ?: run { dead = true; return 0 }
        for ((x, y) in p.cells) if (y >= 0) board[y * COLS + x] = type + 1
        val kept = (0 until ROWS).filter { r -> (0 until COLS).any { board[r * COLS + it] == 0 } }
        val cleared = ROWS - kept.size
        if (cleared > 0) {
            val nb = IntArray(COLS * ROWS)
            kept.forEachIndexed { i, r -> System.arraycopy(board, r * COLS, nb, (ROWS - kept.size + i) * COLS, COLS) }
            board = nb
        }
        return cleared
    }

    fun addGarbage(rows: Int) {
        val hole = (0 until COLS).random()
        repeat(rows) {
            if ((0 until COLS).any { board[it] != 0 }) dead = true
            val nb = IntArray(COLS * ROWS)
            System.arraycopy(board, COLS, nb, 0, (ROWS - 1) * COLS)
            for (x in 0 until COLS) nb[(ROWS - 1) * COLS + x] = if (x == hole) 0 else 8
            board = nb
        }
    }
}

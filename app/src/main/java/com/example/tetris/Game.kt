package com.example.tetris

import android.content.SharedPreferences
import android.os.SystemClock
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
    PICTURE("DESSIN", "Le plateau démarre avec des lignes incomplètes qui forment un dessin. Complétez-les pour effacer le tableau : 100 niveaux de plus en plus difficiles !"),
    SURVIVAL("SURVIE", "Des lignes pirates remontent du bas de plus en plus vite. Effacez-les avant d'être submergé !"),
    MISSIONS("MISSIONS", "Remplissez des objectifs pendant la partie (ex. : 3 lignes d'un coup, 4 fois). Chaque mission validée rapporte un gros bonus."),
    PUZZLE("PUZZLE", "Un plateau déjà rempli et une liste de pièces imposées : placez-les toutes pour tout effacer. 8 niveaux de plus en plus difficiles !"),
    CASCADE("CASCADE", "Après chaque ligne, les blocs retombent un par un et peuvent former de nouvelles lignes. Enchaînez les combos !"),
    DUEL("DUEL", "Affrontez l'ordinateur : vos lignes multiples lui envoient des lignes pirates, et inversement. Le premier débordé perd."),
    CHAOS("CHAOS", "Événements aléatoires : vitesse, commandes inversées, rotation bloquée et bombes qui explosent. Gardez votre calme !"),
}

fun fmtTime(ms: Long): String = "%02d:%02d.%d".format(ms / 60000, (ms / 1000) % 60, (ms / 100) % 10)

/** 100 dessins de départ du mode DESSIN, de plus en plus difficiles : '#' = bloc, '.' = vide. Toutes les lignes sont incomplètes. */
val PICTURES: List<List<String>> = listOf(
    listOf("....##....", "...####...", "..######.."),  // 1
    listOf(".........#", ".......###", ".....#####", "...#######"),  // 2
    listOf("....##....", "...####...", "..######..", ".########.", "....##....", "....##....", "....##...."),  // 3
    listOf(".###..###.", ".########.", ".########.", "..######..", "...####...", "....##...."),  // 4
    listOf("....##....", "...####...", "..######..", ".########.", "..######..", "...####...", "....##...."),  // 5
    listOf("..##..##..", "..######..", "..######..", "..######.."),  // 6
    listOf("...#..#...", "..######..", "..######..", "..######.."),  // 7
    listOf("...#..#...", "..##..##..", "..######..", "..######.."),  // 8
    listOf("....##....", "...####...", "...####...", "..######.."),  // 9
    listOf("..#....#..", "..##..##..", "..##..##..", "..######.."),  // 10
    listOf("....##....", "....##....", "....##....", "..######.."),  // 11
    listOf("...#..#...", "..######..", "..######..", "..######..", "..######.."),  // 12
    listOf("...####...", "...####...", "...####...", "..######..", "..######.."),  // 13
    listOf("....##....", "....##....", "..######..", "..######..", "..######.."),  // 14
    listOf("...#..#...", "...####...", "...####...", "..######..", "..######.."),  // 15
    listOf("..#....#..", "..#....#..", "..######..", "..######..", "..######.."),  // 16
    listOf("....##....", "....##....", "...####...", "..######..", "..######.."),  // 17
    listOf("....##....", "....##....", "....##....", "..######..", "..######.."),  // 18
    listOf("....##....", "....##....", "....##....", "...####...", "..######.."),  // 19
    listOf("....##....", "....##....", "...####...", "...####...", "...####..."),  // 20
    listOf("..##..##..", "..######..", "..######..", "..######..", "..######..", "..######.."),  // 21
    listOf("...#..#...", "...#..#...", "..######..", "..######..", "..######..", "..######.."),  // 22
    listOf("..#....#..", "..##..##..", "..##..##..", "..######..", "..######..", "..######.."),  // 23
    listOf("...#..#...", "..##..##..", "..##..##..", "..######..", "..######..", "..######.."),  // 24
    listOf("..#....#..", "..#....#..", "..##..##..", "..######..", "..######..", "..######.."),  // 25
    listOf("...#..#...", "...#..#...", "..##..##..", "..######..", "..######..", "..######.."),  // 26
    listOf("...#..#...", "...#..#...", "...#..#...", "..######..", "..######..", "..######.."),  // 27
    listOf("....##....", "....##....", "...####...", "...####...", "...####...", "..######.."),  // 28
    listOf("..#....#..", "..#....#..", "..##..##..", "..##..##..", "..##..##..", "..######.."),  // 29
    listOf("....##....", "....##....", "....##....", "...####...", "..######..", "..######.."),  // 30
    listOf("..#....#..", "..#....#..", "..##..##..", "..######..", "..######.."),  // 31
    listOf("...####...", "...####...", "..######..", "..######..", "...####..."),  // 32
    listOf("..##..##..", "..##..##..", "..##..##..", "..######..", "...####..."),  // 33
    listOf("...#..#...", "..##..##..", "..##..##..", "..######..", "...####..."),  // 34
    listOf("...####...", "...####...", "....##....", "..######..", "...####..."),  // 35
    listOf("...#..#...", "...#..#...", "..######..", "..######..", "....##...."),  // 36
    listOf("..#....#..", "..##..##..", "..#....#..", "..######..", "...####..."),  // 37
    listOf("..##..##..", "..#....#..", "..#....#..", "..##..##..", "..######.."),  // 38
    listOf("...####...", "..######..", "..######..", "..######..", "..######..", "....##...."),  // 39
    listOf("...#..#...", "...#..#...", "..######..", "..######..", "...####...", "...####..."),  // 40
    listOf("..#....#..", "..##..##..", "..##..##..", "...####...", "..######..", "...####..."),  // 41
    listOf("...#..#...", "..##..##..", "..##..##..", "...#..#...", "...#..#...", "..######.."),  // 42
    listOf("...####...", "..######..", "....##....", "..######..", "....##...."),  // 43
    listOf("...####...", "....##....", "....##....", "..######..", "....##...."),  // 44
    listOf("..##..##..", "...#..#...", "...#..#...", "..######..", "....##...."),  // 45
    listOf("..##..##..", "..######..", "...####...", "...####...", "..######..", "..######..", "..######.."),  // 46
    listOf("...####...", "....##....", "...####...", "...####...", "..######..", "....##...."),  // 47
    listOf("..######..", "....##....", "...####...", "...####...", "...####..."),  // 48
    listOf("..#....#..", "..##..##..", "...#..#...", "..##..##..", "...####...", "..######..", "..######.."),  // 49
    listOf("..##..##..", "...####...", "...####...", "...####...", "..######..", "...####..."),  // 50
    listOf("..##..##..", "..######..", "..######..", "..######..", "....##....", "....##...."),  // 51
    listOf("...####...", "..######..", "....##....", "..######..", "..######..", "..######..", "...####..."),  // 52
    listOf("..##..##..", "...#..#...", "..######..", "..######..", "....##....", "...####..."),  // 53
    listOf("..#....#..", "..#....#..", "..#....#..", "..##..##..", "..##..##..", "...####...", "...####..."),  // 54
    listOf("..##..##..", "..#....#..", "..######..", "...####...", "...####...", "...####..."),  // 55
    listOf("....##....", "...####...", "..######..", "..######..", "....##....", "....##...."),  // 56
    listOf("..######..", "....##....", "..######..", "...####...", "..######..", "....##...."),  // 57
    listOf("..##..##..", "...#..#...", "..##..##..", "...#..#...", "...####...", "....##...."),  // 58
    listOf("..##..##..", "..##..##..", "..#....#..", "..##..##..", "..#....#..", "..######..", "..######..", "...####..."),  // 59
    listOf("...#..#...", "..######..", "....##....", "..######..", "....##....", "....##...."),  // 60
    listOf("..##..##..", "..######..", "..######..", "....##....", "...####...", "..######..", "..######..", "...####..."),  // 61
    listOf("..##..##..", "..##..##..", "...####...", "...####...", "...####...", "..######..", "....##...."),  // 62
    listOf("..##..##..", "...####...", "..######..", "..######..", "...####...", "..######..", "...####...", "...####..."),  // 63
    listOf("..##..##..", "..#....#..", "..#....#..", "..##..##..", "...#..#...", "..######..", "....##...."),  // 64
    listOf("..#....#..", "..##..##..", "...#..#...", "...####...", "....##....", "..######..", "....##...."),  // 65
    listOf("...#..#...", "..##..##..", "..##..##..", "..##..##..", "..######..", "..##..##..", ".########."),  // 66
    listOf("..#....#..", "..#....#..", "..##..##..", ".###..###.", ".#.####.#.", "...####...", "..######.."),  // 67
    listOf("..##..##..", "...####...", "..#.##.#..", "..######..", "...####...", "..######..", "..######.."),  // 68
    listOf("...#..#...", "...#..#...", ".###..###.", "...#..#...", ".###..###.", ".##....##.", "..######.."),  // 69
    listOf("..######..", ".########.", ".########.", "..##..##..", "...####...", ".########.", "..######.."),  // 70
    listOf(".###..###.", ".########.", "..######..", "..######..", "..######..", "..##..##..", ".########."),  // 71
    listOf("...####...", "....##....", "....##....", "....##....", "..#.##.#..", "..######..", ".########.", "...####..."),  // 72
    listOf("...####...", "....##....", "...####...", "...####...", "..#.##.#..", "..######..", "...####...", "....##...."),  // 73
    listOf(".##.##.##.", ".#.####.#.", ".########.", "..######..", "..######..", "...####...", "..######.."),  // 74
    listOf("..##..##..", "..##..##..", "..##..##..", "..##..##..", "..##..##..", "..##..##..", "..##..##..", "..##..##..", ".########.", "..#.##.#.."),  // 75
    listOf("...####...", "..######..", "..######..", "..#.##.#..", "..##..##..", "...####...", "..######..", "...####..."),  // 76
    listOf("....##....", "..######..", "...####...", "...####...", "...####...", "..##..##..", "..######..", "..#.##.#.."),  // 77
    listOf(".##.##.##.", "..######..", ".########.", ".########.", "..##..##..", "...#..#...", "..######.."),  // 78
    listOf(".###..###.", "..######..", "...####...", "...####...", "..######..", ".#.####.#.", ".########.", "...####..."),  // 79
    listOf("...####...", "...#..#...", ".#.####.#.", ".###..###.", "..######..", "..##..##..", ".########."),  // 80
    listOf("##......##", "##......##", "###....###", "###....###", "#........#", "#..#..#..#", "####..####", ".###..###.", "##.####.##"),  // 81
    listOf("..######..", "...####...", "....##....", "..######..", "..##..##..", ".##....##.", "..######.."),  // 82
    listOf("...####...", "...####...", "..##..##..", "..#....#..", "..######..", ".##.##.##.", ".########."),  // 83
    listOf("..#.##.#..", "..######..", "...#..#...", "...####...", "..##..##..", "..#....#..", ".########."),  // 84
    listOf("...####...", ".#.####.#.", ".########.", "..##..##..", "..#....#..", ".##.##.##.", "..######.."),  // 85
    listOf("...####...", ".########.", "..######..", "..#.##.#..", "..#.##.#..", "...####...", "....##....", "..######.."),  // 86
    listOf("###....###", "##......##", "##.#..#.##", "####..####", "##.####.##", "#..####..#", "..######.."),  // 87
    listOf("...####...", "..##..##..", "...#..#...", "...####...", ".########.", ".###..###.", "..######..", ".##.##.##."),  // 88
    listOf("..##..##..", ".########.", ".#.####.#.", ".###..###.", "..#....#..", "..######..", "....##...."),  // 89
    listOf(".##....##.", ".###..###.", "..######..", ".###..###.", "..#....#..", "..##..##..", "...####..."),  // 90
    listOf(".########.", "..######..", ".########.", ".###..###.", "..##..##..", "..##..##..", "...####...", "....##...."),  // 91
    listOf("...#..#...", "...####...", "...#..#...", "..##..##..", ".#.#..#.#.", ".########.", "..##..##..", "..######..", "....##...."),  // 92
    listOf("##......##", "####..####", "#.#....#.#", "#.##..##.#", "#..####..#", "#.######.#", "..######.."),  // 93
    listOf("..##..##..", ".########.", ".##....##.", ".##....##.", "..##..##..", "..######..", "..######..", "...####...", "..######.."),  // 94
    listOf("..######..", "...####...", "....##....", "..######..", "...####...", ".#.#..#.#.", ".###..###.", "...#..#...", "..##..##..", "..######.."),  // 95
    listOf(".########.", ".###..###.", ".#.#..#.#.", "..##..##..", "..#....#..", "..######..", "...####...", "....##....", "..######.."),  // 96
    listOf("..######..", ".##.##.##.", ".#..##..#.", ".##....##.", "..#....#..", "..######..", "...####...", "....##...."),  // 97
    listOf("####..####", "###....###", "..#....#..", ".###..###.", ".##.##.##.", "..######..", ".##.##.##."),  // 98
    listOf("..######..", "...#..#...", "...####...", "..##..##..", "#.#....#.#", "####..####", "##.#..#.##", "###....###", "#.######.#"),  // 99
    listOf(".########.", "####..####", "#.##..##.#", "..#....#..", "..######..", "....##....", "..######.."),  // 100
)

/** Niveau du mode PUZZLE : plateau de départ (du haut vers le bas) et pièces imposées (0=I 1=O 2=T 3=S 4=Z 5=J 6=L). */
class PuzzleLevel(val rows: List<String>, val seq: List<Int>)

// Chaque niveau a été vérifié résoluble par un solveur.
val PUZZLES = listOf(
    PuzzleLevel(listOf("##....####"), listOf(0)),
    PuzzleLevel(listOf("..##...###", "..####.###"), listOf(5, 1)),
    PuzzleLevel(listOf("##..###...", "##..###...", "#######.#."), listOf(2, 5, 1)),
    PuzzleLevel(listOf("#.....#..#", "#.....##.#", "####..##.#"), listOf(1, 1, 6, 6)),
    PuzzleLevel(listOf("...#...#..", "..####.#..", "..########", ".#########"), listOf(0, 1, 5, 5)),
    PuzzleLevel(listOf("#.........", "#...##....", "######..##", "######..##"), listOf(1, 1, 1, 1, 6)),
    PuzzleLevel(listOf("........##", ".......###", "##.....###", "##....####"), listOf(1, 1, 1, 1, 1, 5)),
    PuzzleLevel(listOf("....#....#", "....#.#..#", "...####.##", "...#######", "#..#######"), listOf(5, 1, 1, 1, 4, 6)),
)

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
    val cells get() = SHAPES[type][rot].map { (dx, dy) -> (x + dx) to (y + dy) }
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
    var chaosLabel by mutableStateOf("")      // CHAOS : événement actif sur la pièce en cours (vide = aucun)
    var onEvent: (Int) -> Unit = {}   // 0 = pose, n>0 = n lignes

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
    private var botAt = 0L
    private var bot = Bot()

    private fun resetTimers() { lastFall = SystemClock.uptimeMillis(); lockAt = 0L; lockResets = 0 }

    /** Un déplacement ou une rotation réussis repoussent le verrouillage, mais pas indéfiniment. */
    private fun touched() {
        if (lockAt != 0L && lockResets < MAX_RESETS) { lockAt = SystemClock.uptimeMillis(); lockResets++ }
    }

    private fun say(text: String, ms: Long = 1600L) { banner = text; bannerUntil = timeAcc + ms }

    private fun draw(): Int {
        if (mode == Mode.PUZZLE) return if (seqIdx < seq.size) seq[seqIdx++] else -1
        if (bag.isEmpty()) bag = (0..6).shuffled().toMutableList()
        return bag.removeAt(bag.lastIndex)
    }

    private fun loadBest(m: Mode) {
        best = prefs.getInt("best_" + m.name, if (m == Mode.CLASSIC) prefs.getInt("best", 0) else 0)
    }

    /** Texte du record affiché sur l'écran de sélection. */
    fun recordLabel(m: Mode): String =
        "Record : " + prefs.getInt("best_" + m.name, if (m == Mode.CLASSIC) prefs.getInt("best", 0) else 0)

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

    fun start(m: Mode = mode, retry: Boolean = false) {
        mode = m; timeAcc = 0L; timeMs = 0L; loadBest(m)
        score = 0; lines = 0; hold = -1; banner = ""; fx = null; clearRows = emptyList()
        canRetry = false; result = "GAME OVER"; chain = 0; levelBase = 0
        clearChaos()
        bag.clear()
        when (m) {
            Mode.PICTURE -> { picture = 1; board = pictureBoard(0) }
            Mode.PUZZLE -> { if (!retry) picture = 1; loadPuzzle() }
            else -> { picture = 1; board = IntArray(COLS * ROWS) }
        }
        if (m == Mode.MISSIONS) { val a = newMission(emptyList()); missions = listOf(a, newMission(listOf(a.first))) }
        if (m == Mode.DUEL) { bot = Bot(); oppBoard = bot.board.copyOf(); botAt = 3000L }
        garbageAt = 15_000L
        next = draw(); state = GState.PLAYING
        lastTickAt = SystemClock.uptimeMillis()
        spawnNext()
    }

    fun toMenu() { state = GState.READY }

    fun pause() { if (state == GState.PLAYING) state = GState.PAUSED }
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

    private fun clearChaos() { evSpeed = false; evReverse = false; evNoRot = false; chaosLabel = "" }

    /** CHAOS : à chaque nouvelle pièce, un événement au hasard (ou aucun) qui dure jusqu'à sa pose. */
    private fun rollChaos(t: Int) {
        clearChaos()
        if (t == 7) { chaosLabel = "BOMBE !"; return }
        when ((0..9).random()) {
            0, 1 -> { evSpeed = true; chaosLabel = "VITESSE ×3" }
            2, 3 -> { evReverse = true; chaosLabel = "COMMANDES INVERSÉES" }
            4, 5 -> { evNoRot = true; chaosLabel = "ROTATION BLOQUÉE" }
            else -> {}
        }
    }

    private fun spawn(t: Int) {
        val p = Piece(t); piece = p; canHold = true; resetTimers()
        if (mode == Mode.CHAOS) rollChaos(t)
        if (!fits(p)) gameOver()
    }

    private fun spawnNext() {
        if (mode == Mode.CHAOS && (0..9).random() < 2) { spawn(7); return }   // une bombe de temps en temps
        val t = next
        if (t < 0) {   // PUZZLE : plus de pièces et le dessin n'est pas effacé
            result = "ÉCHEC — NIVEAU " + picture; canRetry = true; state = GState.OVER; saveBest(); return
        }
        next = draw()
        if (mode == Mode.PUZZLE) piecesLeft = 1 + (if (next >= 0) 1 else 0) + (seq.size - seqIdx)
        spawn(t)
    }

    private fun saveBest() {
        if (score > best) { best = score; prefs.edit().putInt("best_" + mode.name, best).apply() }
    }

    private fun gameOver() {
        result = if (mode == Mode.DUEL) "DÉFAITE" else "GAME OVER"
        state = GState.OVER; saveBest()
    }

    private fun win(msg: String) { result = msg; state = GState.OVER; saveBest() }

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
            if (fits(q)) { piece = q; touched(); return }
            val up = q.copy(y = q.y - 1)
            if (fits(up)) { piece = up; touched(); return }
        }
    }

    /** Appelée très souvent : temps, événements du mode, animation d'effacement, gravité et verrouillage. */
    fun tick(now: Long) {
        if (state != GState.PLAYING) return
        timeAcc += now - lastTickAt; lastTickAt = now
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
        if (!detectClear()) { onEvent(0); afterClear() }
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
            if (picture >= PICTURES.size) { win("BRAVO ! LES " + PICTURES.size + " TABLEAUX"); return }
            picture++; levelBase = lines; board = pictureBoard(picture - 1); say("TABLEAU SUIVANT !")
        }
        if (mode == Mode.PUZZLE && board.none { it == 9 }) {
            score += 500 + 100 * (seq.size - seqIdx + (if (next >= 0) 1 else 0))
            picture++
            if (picture > PUZZLES.size) { win("BRAVO ! TOUS LES NIVEAUX"); return }
            say("NIVEAU RÉUSSI !"); loadPuzzle(); next = draw()
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
        botAt = timeAcc + maxOf(500L, 1400L - (timeAcc / 15000L) * 100L)
        oppBoard = bot.board.copyOf()
        if (bot.dead) { score += 2000; win("VICTOIRE !"); return }
        val g = garbageFor(c)
        if (g > 0) { say("ATTAQUE ! +" + g); addGarbage(g) }
    }
}

/** Adversaire du mode DUEL : place chaque pièce avec une heuristique simple (hauteur, trous, irrégularité, lignes). */
class Bot {
    var board = IntArray(COLS * ROWS)
    var dead = false
    private var bag = mutableListOf<Int>()

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
            val sc = evaluate(p) + Random.nextFloat() * 1.2f
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

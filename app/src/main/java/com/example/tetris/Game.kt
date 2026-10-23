package com.example.tetris

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.compose.runtime.*

const val COLS = 10
const val ROWS = 20

private const val LOCK_MS = 500L   // délai avant verrouillage d'une pièce posée
private const val MAX_RESETS = 15  // nombre max de déplacements/rotations qui repoussent le verrouillage

enum class GState { READY, PLAYING, PAUSED, OVER }

enum class Mode(val title: String, val desc: String) {
    CLASSIC("CLASSIQUE", "Le Tetris traditionnel : la vitesse augmente tous les 10 lignes. Tenez le plus longtemps possible !"),
    PICTURE("DESSIN", "Le plateau démarre avec des lignes incomplètes qui forment un dessin. Complétez-les pour effacer le tableau, puis passez au suivant !"),
    SURVIVAL("SURVIE", "Des lignes pirates remontent du bas de plus en plus vite. Effacez-les avant d'être submergé !"),
}

fun fmtTime(ms: Long): String = "%02d:%02d.%d".format(ms / 60000, (ms / 1000) % 60, (ms / 100) % 10)

/** Dessins de départ du mode DESSIN : '#' = bloc, '.' = vide. Toutes les lignes sont incomplètes. */
val PICTURES: List<List<String>> = listOf(
    listOf(".###..###.", ".########.", ".########.", "..######..", "...####...", "....##...."),                              // cœur
    listOf("....##....", "...####...", "..######..", "...####...", "..######..", ".########.", "....##....", "....##...."), // sapin
    listOf("....##....", "...####...", "..######..", ".########.", "....##....", "....##....", "....##...."),               // flèche
    listOf("....##....", "...####...", "..######..", ".########.", "..######..", "...####...", "....##...."),               // diamant
)

/** Ordre des pièces : I, O, T, S, Z, J, L — 4 rotations pré-calculées. */
val SHAPES: List<List<List<Pair<Int, Int>>>> = listOf(
    4 to listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1),
    2 to listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
    3 to listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1),
    3 to listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),
    3 to listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1),
    3 to listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1),
    3 to listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1),
).map { (n, base) ->
    val r = mutableListOf(base)
    repeat(3) { r.add(r.last().map { (x, y) -> (n - 1 - y) to x }) }
    r
}

data class Piece(val type: Int, val rot: Int = 0, val x: Int = 3, val y: Int = 0) {
    val cells get() = SHAPES[type][rot].map { (dx, dy) -> (x + dx) to (y + dy) }
}

class Game(private val prefs: SharedPreferences) {
    var board by mutableStateOf(IntArray(COLS * ROWS))
    var piece by mutableStateOf<Piece?>(null)
    var next by mutableIntStateOf(0)
    var hold by mutableIntStateOf(-1)
    var score by mutableIntStateOf(0)
    var lines by mutableIntStateOf(0)
    var best by mutableIntStateOf(0)            // meilleur score du mode en cours
    var mode by mutableStateOf(Mode.CLASSIC)
    var picture by mutableIntStateOf(1)         // mode DESSIN : numéro du tableau en cours
    var timeMs by mutableLongStateOf(0L)        // temps de jeu actif (arrondi au dixième)
    var state by mutableStateOf(GState.READY)
    var onEvent: (Int) -> Unit = {}   // 0 = pose, n>0 = n lignes

    val level get() = lines / 10 + 1
    val delayMs get() = maxOf(80L, 800L - (level - 1) * 70L)

    private var bag = mutableListOf<Int>()
    private var canHold = true
    private var lastFall = 0L
    private var lockAt = 0L
    private var lockResets = 0
    private var lastTickAt = 0L
    private var timeAcc = 0L
    private var garbageAt = 0L

    private fun resetTimers() { lastFall = SystemClock.uptimeMillis(); lockAt = 0L; lockResets = 0 }

    /** Un déplacement ou une rotation réussis repoussent le verrouillage, mais pas indéfiniment. */
    private fun touched() {
        if (lockAt != 0L && lockResets < MAX_RESETS) { lockAt = SystemClock.uptimeMillis(); lockResets++ }
    }

    private fun draw(): Int {
        if (bag.isEmpty()) bag = (0..6).shuffled().toMutableList()
        return bag.removeAt(bag.lastIndex)
    }

    private fun loadBest(m: Mode) {
        best = prefs.getInt("best_" + m.name, if (m == Mode.CLASSIC) prefs.getInt("best", 0) else 0)
    }

    /** Texte du record affiché sur l'écran de sélection. */
    fun recordLabel(m: Mode): String =
        "Record : " + prefs.getInt("best_" + m.name, if (m == Mode.CLASSIC) prefs.getInt("best", 0) else 0)

    /** Plateau de départ d'un tableau : les blocs du dessin portent la valeur 9. */
    private fun pictureBoard(index: Int): IntArray {
        val pic = PICTURES[index % PICTURES.size]
        val b = IntArray(COLS * ROWS)
        pic.forEachIndexed { i, row ->
            val y = ROWS - pic.size + i
            row.forEachIndexed { x, ch -> if (ch == '#') b[y * COLS + x] = 9 }
        }
        return b
    }

    fun start(m: Mode = mode) {
        mode = m; timeAcc = 0L; timeMs = 0L; picture = 1; loadBest(m)
        board = if (m == Mode.PICTURE) pictureBoard(0) else IntArray(COLS * ROWS)
        score = 0; lines = 0; hold = -1
        bag.clear(); next = draw(); state = GState.PLAYING
        lastTickAt = SystemClock.uptimeMillis(); garbageAt = 15_000L
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
    }

    private fun fits(p: Piece) = p.cells.all { (x, y) ->
        x in 0 until COLS && y < ROWS && (y < 0 || board[y * COLS + x] == 0)
    }

    private fun spawn(t: Int) {
        val p = Piece(t); piece = p; canHold = true; resetTimers()
        if (!fits(p)) gameOver()
    }

    private fun spawnNext() { val t = next; next = draw(); spawn(t) }

    private fun saveBest() {
        if (score > best) { best = score; prefs.edit().putInt("best_" + mode.name, best).apply() }
    }

    private fun gameOver() { state = GState.OVER; saveBest() }

    /** Survie : une ligne pirate (avec un trou) remonte du bas ; si le haut est occupé, c'est perdu. */
    private fun addGarbage() {
        val topOccupied = (0 until COLS).any { board[it] != 0 }
        val nb = IntArray(COLS * ROWS)
        System.arraycopy(board, COLS, nb, 0, (ROWS - 1) * COLS)
        val hole = (0 until COLS).random()
        for (x in 0 until COLS) nb[(ROWS - 1) * COLS + x] = if (x == hole) 0 else 8
        board = nb
        if (topOccupied) { gameOver(); return }
        val p = piece
        if (p != null && !fits(p)) {
            val up = p.copy(y = p.y - 1)
            if (fits(up)) piece = up else gameOver()
        }
    }

    fun move(dx: Int) {
        val p = piece ?: return
        if (state != GState.PLAYING) return
        p.copy(x = p.x + dx).let { if (fits(it)) { piece = it; touched() } }
    }

    fun rotate() {
        val p = piece ?: return
        if (state != GState.PLAYING) return
        if (!fits(p.copy(y = p.y + 1))) return   // la pièce touche : plus de rotation possible
        val r = (p.rot + 1) % 4
        for (k in intArrayOf(0, -1, 1, -2, 2)) {          // « wall kicks » simplifiés
            val q = p.copy(rot = r, x = p.x + k)
            if (fits(q)) { piece = q; touched(); return }
            val up = q.copy(y = q.y - 1)
            if (fits(up)) { piece = up; touched(); return }
        }
    }

    /** Appelée très souvent : fait tomber la pièce selon le niveau, puis la verrouille LOCK_MS après qu'elle a touché. */
    fun tick(now: Long) {
        if (state != GState.PLAYING) return
        timeAcc += now - lastTickAt; lastTickAt = now
        val shown = timeAcc / 100 * 100
        if (shown != timeMs) timeMs = shown
        if (mode == Mode.SURVIVAL && timeAcc >= garbageAt) {
            addGarbage()
            garbageAt = timeAcc + maxOf(6000L, 15000L - (timeAcc / 20000L) * 1000L)
            if (state != GState.PLAYING) return
        }
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
        if (state != GState.PLAYING || !canHold) return
        val h = hold; hold = p.type
        if (h < 0) spawnNext() else spawn(h)
        canHold = false
    }

    private fun lock() {
        val p = piece ?: return
        val b = board.copyOf()
        p.cells.forEach { (x, y) -> if (y >= 0) b[y * COLS + x] = p.type + 1 }
        val kept = (0 until ROWS).filter { r -> (0 until COLS).any { b[r * COLS + it] == 0 } }
        val cleared = ROWS - kept.size
        val nb = IntArray(COLS * ROWS)
        kept.forEachIndexed { i, r -> System.arraycopy(b, r * COLS, nb, (ROWS - kept.size + i) * COLS, COLS) }
        val lv = level
        board = nb
        if (cleared > 0) { score += intArrayOf(0, 100, 300, 500, 800)[cleared] * lv; lines += cleared }
        if (mode == Mode.PICTURE && nb.none { it == 9 }) {   // tableau terminé : bonus et dessin suivant
            score += 1000; picture++; board = pictureBoard(picture - 1)
        }
        saveBest()
        onEvent(cleared)
        spawnNext()
    }
}

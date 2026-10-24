package com.example.tetris

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TetrisApp() }
    }
}

@Composable
fun TetrisApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("tetris", Context.MODE_PRIVATE) }
    val game = remember { Game(prefs) }
    var themeIdx by remember { mutableIntStateOf(prefs.getInt("theme", 0).coerceIn(0, THEMES.lastIndex)) }
    var mirror by remember { mutableStateOf(prefs.getBoolean("mirror", false)) } // true = rotation à gauche, flèches à droite
    var lift by remember { mutableFloatStateOf(prefs.getFloat("lift", 60f)) } // décalage des boutons depuis le bas (dp)
    var style by remember { mutableIntStateOf(prefs.getInt("style", 0).coerceIn(0, STYLES.lastIndex)) }
    var arrow by remember { mutableFloatStateOf(prefs.getFloat("arrow", 1f)) }   // taille des flèches (x1)
    var rshift by remember { mutableFloatStateOf(prefs.getFloat("rshift", 0f)) } // décalage de la flèche droite (dp)
    var lshift by remember { mutableFloatStateOf(prefs.getFloat("lshift", 0f)) } // décalage de la flèche gauche vers le bouton de rotation (dp)
    var showSettings by remember { mutableStateOf(false) }
    var inMenu by remember { mutableStateOf(true) }   // écran de sélection du mode
    var picking by remember { mutableStateOf(false) } // écran de choix du tableau (mode DESSIN)
    val zoneH = maxOf(150f, 140f * arrow + 14f)
    val t = THEMES[themeIdx]
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        game.onEvent = { n -> haptic.performHapticFeedback(if (n > 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) }
    }
    LaunchedEffect(game.state) {
        while (game.state == GState.PLAYING) {
            delay(25)
            game.tick(SystemClock.uptimeMillis())
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { game.pause() }
    BackHandler(enabled = game.state == GState.PLAYING) { game.pause() }
    BackHandler(enabled = !inMenu && game.state != GState.PLAYING) { game.toMenu(); inMenu = true }
    BackHandler(enabled = inMenu && picking) { picking = false }
    BackHandler(enabled = showSettings) { showSettings = false; if (game.state == GState.READY) inMenu = true }

    Box(Modifier.fillMaxSize()) {
        Backdrop(t)
        if (inMenu) {
            if (picking) {
                TableauSelect(game, t, { n -> game.start(Mode.PICTURE, level = n); picking = false; inMenu = false }, { picking = false })
            } else {
                ModeSelect(
                    game, t,
                    { m -> if (m == Mode.PICTURE) picking = true else { game.start(m); inMenu = false } },
                    { showSettings = true; inMenu = false },
                )
            }
        } else {
        Column(Modifier.fillMaxSize()) {
            // ---- zone de jeu : plateau à gauche, cadres à droite ----
            Row(
                Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(2.3f).fillMaxHeight()) {
                    ScoreBar(game.score, t)
                    if (game.mode == Mode.CHAOS) {   // hauteur fixe : le plateau ne bouge pas quand l'événement change
                        val on = game.chaosLabel.isNotEmpty()
                        Box(
                            Modifier.fillMaxWidth().height(30.dp).background(if (on) Color(0xFFE53935) else t.well),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (on) "⚠ " + game.chaosLabel else "Pas d'événement",
                                color = if (on) Color.White else t.text.copy(alpha = 0.5f),
                                fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1,
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopStart) { BoardView(game, t, style) }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        InfoPanel("RÉSERVE", t, Modifier.weight(1f)) { PieceCanvas(game.hold, t, style) }
                        WoodPill("II", t, Modifier.size(38.dp)) { game.pause() }
                    }
                    InfoPanel("SUIVANT", t, Modifier.fillMaxWidth()) { PieceCanvas(game.next, t, style) }
                    InfoPanel("NIV " + game.level + " · LIGNES", t, Modifier.fillMaxWidth(), t.accent) { Value(game.lines.toString(), t) }
                    when (game.mode) {
                        Mode.PICTURE -> InfoPanel("TABLEAU", t, Modifier.fillMaxWidth()) { Value(game.picture.toString() + "/" + PICTURES.size, t) }
                        Mode.PUZZLE -> {
                            InfoPanel("NIVEAU", t, Modifier.fillMaxWidth()) { Value(game.picture.toString() + "/" + PUZZLES.size, t) }
                            InfoPanel("PIÈCES", t, Modifier.fillMaxWidth()) { Value(game.piecesLeft.toString(), t) }
                        }
                        Mode.MISSIONS -> InfoPanel("MISSIONS", t, Modifier.fillMaxWidth()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                game.missions.forEach { (k, c) ->
                                    Text("×" + k + " → " + c, color = t.text, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Mode.DUEL -> InfoPanel("ADVERSAIRE", t, Modifier.fillMaxWidth()) { OpponentBoard(game.oppBoard, t) }
                        else -> InfoPanel("TEMPS", t, Modifier.fillMaxWidth()) { Value(fmtTime(game.timeMs), t) }
                    }
                    if (game.mode != Mode.PUZZLE && game.mode != Mode.DUEL) {
                        InfoPanel("RECORD", t, Modifier.fillMaxWidth()) { Value(game.best.toString(), t) }
                    }
                    Spacer(Modifier.weight(1f))
                    WoodPill("RÉSERVE", t, Modifier.fillMaxWidth().height(44.dp)) { game.holdPiece() }
                }
            }
            // ---- zone des pouces ----
            Box(
                Modifier.fillMaxWidth()
                    .background(if (t.wood) Color(0xFFDDBB8E) else Color.Black.copy(alpha = 0.22f))
                    .navigationBarsPadding().height(zoneH.dp + lift.dp),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp + lift.dp)) {
                    RotateButton(game, t, Modifier.align(if (mirror) Alignment.CenterStart else Alignment.CenterEnd))
                    MoveCluster(game, t, arrow, rshift, lshift, mirror, maxWidth.value, Modifier.align(Alignment.CenterStart))
                }
            }
        }

        if (game.state != GState.PLAYING) {
            if (showSettings) {
                SettingsPanel(
                    t, lift, { lift = it }, { prefs.edit().putFloat("lift", lift).apply() },
                    mirror, { mirror = it; prefs.edit().putBoolean("mirror", it).apply() },
                    themeIdx, { themeIdx = it; prefs.edit().putInt("theme", it).apply() },
                    style, { style = it; prefs.edit().putInt("style", it).apply() },
                    arrow, { arrow = it }, { prefs.edit().putFloat("arrow", arrow).apply() },
                    rshift, { rshift = it }, { prefs.edit().putFloat("rshift", rshift).apply() },
                    lshift, { lshift = it }, { prefs.edit().putFloat("lshift", lshift).apply() },
                    { showSettings = false; if (game.state == GState.READY) inMenu = true },
                )
            } else if (game.state != GState.READY) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).pointerInput(Unit) { detectTapGestures { } },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.padding(24.dp).clip(RoundedCornerShape(28.dp)).background(t.board).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            if (game.state == GState.PAUSED) "PAUSE" else game.result,
                            color = t.accent, fontSize = 34.sp, fontWeight = FontWeight.Black,
                        )
                        Text(game.mode.title, color = t.text.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (game.state == GState.OVER) {
                            Text(
                                "Score : " + game.score + (if (game.mode == Mode.PICTURE || game.mode == Mode.PUZZLE) "\nNiveau : " + game.picture else "") + "\n" + game.recordLabel(game.mode),
                                color = t.text, textAlign = TextAlign.Center, fontSize = 18.sp,
                            )
                        }
                        val btn = ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.board)
                        Button(
                            onClick = { if (game.state == GState.PAUSED) game.resume() else game.start(retry = game.canRetry, level = game.picture) },
                            colors = btn, modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(when (game.state) { GState.READY -> "JOUER"; GState.PAUSED -> "REPRENDRE"; else -> if (game.canRetry) "RÉESSAYER" else "REJOUER" }, fontWeight = FontWeight.Bold)
                        }
                        if (game.state == GState.PAUSED) {
                            Button(onClick = { game.start(level = game.picture) }, colors = btn, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("RECOMMENCER") }
                        }
                        Button(
                            onClick = { game.toMenu(); inMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = t.plaque, contentColor = t.onPlaque),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text("CHANGER DE MODE", fontWeight = FontWeight.Bold) }
                        Button(
                            onClick = { showSettings = true },
                            colors = ButtonDefaults.buttonColors(containerColor = t.plaque, contentColor = t.onPlaque),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text("RÉGLAGES", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        }
    }
}

// ---------- décor ----------

@Composable
fun Backdrop(t: Theme) {
    Canvas(Modifier.fillMaxSize()) {
        if (t.wood) {
            val n = 7
            val w = size.width / n
            for (i in 0 until n) {
                drawRect(t.bg[i % t.bg.size], Offset(i * w, 0f), Size(w, size.height))
                drawLine(Color(0x55000000), Offset(i * w, 0f), Offset(i * w, size.height), 2f)
            }
        } else drawRect(Brush.verticalGradient(t.bg))
    }
}

@Composable
fun ScoreBar(score: Int, t: Theme) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(t.plaque)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SCORE", color = t.onPlaque, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(t.well).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
            Text(score.toString(), color = t.text, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1)
        }
    }
}

@Composable
fun InfoPanel(title: String, t: Theme, modifier: Modifier = Modifier, titleColor: Color = t.onPlaque, content: @Composable BoxScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(t.plaque).padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = titleColor, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(t.well).padding(4.dp),
            contentAlignment = Alignment.Center, content = content,
        )
    }
}

@Composable
fun Value(v: String, t: Theme) {
    Text(v, color = t.text, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
}

val STYLES = listOf("Biseauté", "Arrondi", "Plat", "Contour", "Bulle")

private fun DrawScope.cellAt(px: Float, py: Float, s: Float, c: Color, a: Float = 1f, style: Int = 0) {
    when (style) {
        1 -> { // Arrondi
            val pad = s * 0.05f
            drawRoundRect(c.copy(alpha = a), Offset(px + pad, py + pad), Size(s - 2 * pad, s - 2 * pad), CornerRadius(s * 0.35f))
            if (a == 1f) drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(px + s * 0.2f, py + s * 0.15f), Size(s * 0.45f, s * 0.18f), CornerRadius(s * 0.09f))
        }
        2 -> drawRect(c.copy(alpha = a), Offset(px + s * 0.02f, py + s * 0.02f), Size(s * 0.96f, s * 0.96f)) // Plat
        3 -> { // Contour
            val pad = s * 0.08f
            drawRoundRect(c.copy(alpha = 0.25f * a), Offset(px + pad, py + pad), Size(s - 2 * pad, s - 2 * pad), CornerRadius(s * 0.12f))
            drawRoundRect(c.copy(alpha = a), Offset(px + pad, py + pad), Size(s - 2 * pad, s - 2 * pad), CornerRadius(s * 0.12f), style = Stroke(s * 0.1f))
        }
        4 -> { // Bulle
            val ctr = Offset(px + s / 2, py + s / 2)
            drawCircle(
                Brush.radialGradient(listOf(lerp(c, Color.White, 0.55f), c), Offset(ctr.x - s * 0.12f, ctr.y - s * 0.14f), s * 0.6f),
                s * 0.46f, ctr, a,
            )
        }
        else -> { // Biseauté
            val pad = s * 0.03f
            drawRoundRect(c.copy(alpha = a), Offset(px + pad, py + pad), Size(s - 2 * pad, s - 2 * pad), CornerRadius(s * 0.1f))
            if (a == 1f) {
                drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(px + pad * 3, py + pad * 3), Size(s - 6 * pad, (s - 6 * pad) * 0.3f), CornerRadius(s * 0.08f))
                drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(px + pad * 3, py + s * 0.7f), Size(s - 6 * pad, s * 0.22f), CornerRadius(s * 0.08f))
            }
        }
    }
}

/** Bloc « à effacer » : bleu glacier avec un halo bleu qui pulse doucement. */
private fun DrawScope.pictureCell(px: Float, py: Float, s: Float, pulse: Float) {
    val halo = Color(0xFF4DB8FF)
    val pad = s * 0.04f
    val glow = 0.35f + 0.5f * pulse
    for (k in 3 downTo 1) {
        val e = s * 0.09f * k
        drawRoundRect(
            halo.copy(alpha = glow * 0.22f), Offset(px + pad - e, py + pad - e),
            Size(s - 2 * pad + 2 * e, s - 2 * pad + 2 * e), CornerRadius(s * 0.1f + e),
        )
    }
    drawRoundRect(lerp(Color(0xFFA8E4FF), Color.White, pulse * 0.5f), Offset(px + pad, py + pad), Size(s - 2 * pad, s - 2 * pad), CornerRadius(s * 0.1f))
    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(px + s * 0.17f, py + s * 0.17f), Size(s * 0.66f, s * 0.2f), CornerRadius(s * 0.08f))
}

private val RAINBOW = listOf(
    Color(0xFFFF3B30), Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF00C7FF), Color(0xFF5E5CE6), Color(0xFFFF2D92), Color(0xFFFF3B30),
)

/**
 * Animations selon le nombre de lignes : 1 = balayage blanc ; 2 = onde depuis le centre ; 3 = clignotement + éclats + secousse ;
 * 4 (Tétris) = flash plein plateau, arc-en-ciel, confettis et grosse secousse.
 */
private fun DrawScope.clearEffect(f: ClearFx, ph: Float, s: Float, t: Theme) {
    val w = size.width
    fun particles(count: Int, power: Float) {
        for (i in 0 until count) {
            val r = f.rows[i % f.rows.size]
            val ang = i * 2.4f
            val sp = (40 + (i * 53) % 140) * power
            val px = ((i * 7) % COLS + 0.5f) * s + cos(ang) * sp * ph
            val py = (r + 0.5f) * s + sin(ang) * sp * ph + 260f * ph * ph
            val sz = s * 0.35f * (1f - ph)
            drawRect(t.pieces[(i + f.id) % 7].copy(alpha = 1f - ph * 0.7f), Offset(px - sz / 2, py - sz / 2), Size(sz, sz))
        }
    }
    when {
        f.count >= 4 -> {
            drawRect(Color.White.copy(alpha = 0.6f * (1f - ph) * (1f - ph)), Offset.Zero, size)
            f.rows.forEach { r ->
                val x0 = ph * 2f * w
                drawRect(Brush.horizontalGradient(RAINBOW, x0, x0 + w, TileMode.Repeated), Offset(0f, r * s), Size(w, s), 0.95f * (1f - ph * ph))
            }
            particles(110, 2.2f)
        }
        f.count == 3 -> {
            val a = abs(sin(ph * PI.toFloat() * 3f)) * 0.9f
            f.rows.forEach { r -> drawRect(t.pieces[0].copy(alpha = a), Offset(0f, r * s), Size(w, s)) }
            particles(40, 1.4f)
        }
        f.count == 2 -> {
            val ww = w * minOf(1f, ph * 1.7f)
            f.rows.forEach { r ->
                drawRect(t.accent.copy(alpha = 0.85f * (1f - ph * ph)), Offset((w - ww) / 2, r * s), Size(ww, s))
                drawRect(Color.White.copy(alpha = 0.5f * (1f - ph)), Offset(0f, r * s), Size(w, s))
            }
        }
        else -> f.rows.forEach { r ->
            drawRect(Color.White.copy(alpha = 0.5f * (1f - ph)), Offset(0f, r * s), Size(w, s))
            drawRect(Color.White.copy(alpha = 0.85f * (1f - ph * ph)), Offset(0f, r * s), Size(w * ph, s))
        }
    }
}

@Composable
fun BoardView(game: Game, t: Theme, style: Int) {
    val fx = game.fx
    var prog by remember { mutableFloatStateOf(1f) }
    var pulse by remember { mutableFloatStateOf(0f) }   // 0..1, pour le halo des blocs à effacer
    LaunchedEffect(game.mode) {
        if (game.mode == Mode.PICTURE || game.mode == Mode.PUZZLE) {
            while (true) {
                val tNs = withFrameNanos { it }
                pulse = ((sin(tNs / 1_000_000_000.0 * 2.6) + 1.0) / 2.0).toFloat()
            }
        }
    }
    LaunchedEffect(fx?.id) {
        val f = fx
        if (f != null) {
            prog = 0f
            val start = withFrameNanos { it }
            while (prog < 1f) {
                val now = withFrameNanos { it }
                prog = ((now - start) / 1_000_000f / f.duration).coerceAtMost(1f)
            }
        }
    }
    Box(
        Modifier.aspectRatio(COLS / ROWS.toFloat(), matchHeightConstraintsFirst = true)
            .graphicsLayer {
                val f = game.fx
                if (f != null && prog < 1f && f.count >= 3) {
                    val amp = if (f.count >= 4) 14f else 6f
                    translationX = sin(prog * 50f) * amp * (1f - prog) * density
                }
            },
    ) {
        Canvas(
            Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(t.board)
                .border(3.dp, t.plaque, RoundedCornerShape(6.dp)),
        ) {
            val s = size.width / COLS
            for (i in 1 until COLS) drawLine(t.grid, Offset(i * s, 0f), Offset(i * s, size.height))
            for (j in 1 until ROWS) drawLine(t.grid, Offset(0f, j * s), Offset(size.width, j * s))
            val b = game.board
            for (i in b.indices) if (b[i] > 0 && b[i] != 9) {
                val c = if (b[i] == 8) Color(0xFF8A8F98) else t.pieces[b[i] - 1]
                cellAt((i % COLS) * s, (i / COLS) * s, s, c, 1f, style)
            }
            for (i in b.indices) if (b[i] == 9) pictureCell((i % COLS) * s, (i / COLS) * s, s, pulse)
            game.ghost()?.let { g ->
                val gc = if (g.type == 7) Color(0xFFFF3B30) else t.pieces[g.type]
                g.cells.forEach { (x, y) -> if (y >= 0) cellAt(x * s, y * s, s, gc, 0.25f, style) }
            }
            game.piece?.let { p ->
                val pc = if (p.type == 7) Color(0xFFFF3B30) else t.pieces[p.type]
                val st = if (p.type == 7) 4 else style
                p.cells.forEach { (x, y) -> if (y >= 0) cellAt(x * s, y * s, s, pc, 1f, st) }
            }
            val f = game.fx
            if (f != null && prog < 1f) clearEffect(f, prog, s, t)
        }
        val f2 = game.fx
        if (f2 != null && f2.count >= 2 && prog < 1f) {
            Text(
                if (f2.count >= 4) "TÉTRIS !" else if (f2.count == 3) "TRIPLE !" else "DOUBLE !",
                color = Color.White, fontSize = if (f2.count >= 4) 36.sp else 22.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center).graphicsLayer {
                    val sc = 0.4f + minOf(1f, prog * 4f)
                    scaleX = sc; scaleY = sc
                    alpha = 1f - maxOf(0f, (prog - 0.7f) / 0.3f)
                },
            )
        }
        if (game.banner.isNotEmpty()) {
            Text(
                game.banner, color = t.board, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).clip(RoundedCornerShape(50))
                    .background(t.accent).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** Mini plateau de l'adversaire (mode DUEL). */
@Composable
fun OpponentBoard(b: IntArray, t: Theme) {
    Canvas(Modifier.fillMaxWidth(0.75f).aspectRatio(COLS / ROWS.toFloat()).background(t.board)) {
        val s = size.width / COLS
        for (i in b.indices) if (b[i] > 0) {
            val c = if (b[i] == 8) Color(0xFF8A8F98) else t.pieces[(b[i] - 1) % 7]
            cellAt((i % COLS) * s, (i / COLS) * s, s, c, 1f, 2)
        }
    }
}

@Composable
fun PieceCanvas(type: Int, t: Theme, style: Int) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        if (type < 0) return@Canvas
        val cells = SHAPES[type][0]
        val s = size.width / 4.5f
        val w = cells.maxOf { it.first } - cells.minOf { it.first } + 1
        val h = cells.maxOf { it.second } - cells.minOf { it.second } + 1
        val ox = (size.width - w * s) / 2 - cells.minOf { it.first } * s
        val oy = (size.height - h * s) / 2 - cells.minOf { it.second } * s
        cells.forEach { (x, y) -> cellAt(ox + x * s, oy + y * s, s, t.pieces[type], 1f, style) }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, t: Theme, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) t.accent else t.plaque)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = if (selected) t.board else t.onPlaque, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}

// ---------- boutons style « bois » ----------

private fun btnBrush(c: Color, pressed: Boolean): Brush = Brush.verticalGradient(
    listOf(lerp(c, Color.White, 0.25f), lerp(c, Color.Black, 0.3f)).map { if (pressed) lerp(it, Color.Black, 0.25f) else it },
)

@Composable
fun WoodPill(label: String, t: Theme, modifier: Modifier, shape: Shape = RoundedCornerShape(50), action: () -> Unit) {
    val act by rememberUpdatedState(action)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.clip(shape).background(btnBrush(t.btn, pressed)).border(1.5.dp, Color.White.copy(alpha = 0.35f), shape)
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; act(); tryAwaitRelease(); pressed = false }) },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
}

enum class Kind { LEFT, RIGHT, DOWN, ROTATE }

/** Boutons « touches » modernes : coins arrondis, dégradé, reflet, ombre portée et chevrons épais. */
@Composable
fun WoodButton(kind: Kind, t: Theme, modifier: Modifier, repeat: Boolean = false, action: () -> Unit) {
    val scope = rememberCoroutineScope()
    val act by rememberUpdatedState(action)
    var pressed by remember { mutableStateOf(false) }
    Canvas(
        modifier.pointerInput(repeat) {
            detectTapGestures(onPress = {
                pressed = true
                act()
                val job = if (repeat) scope.launch { delay(150); while (true) { act(); delay(45) } } else null
                tryAwaitRelease()
                job?.cancel()
                pressed = false
            })
        },
    ) {
        val w = size.width
        val h = size.height
        val m = minOf(w, h)
        val bodyH = h - m * 0.07f
        val bw = if (kind == Kind.ROTATE) bodyH else w
        val bx = (w - bw) / 2
        val r = if (kind == Kind.ROTATE) bodyH / 2 else m * 0.3f
        val corner = CornerRadius(r)
        drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(bx, m * 0.07f), Size(bw, bodyH), corner)
        translate(0f, if (pressed) m * 0.05f else 0f) {
            drawRoundRect(btnBrush(t.btn, pressed), Offset(bx, 0f), Size(bw, bodyH), corner)
            drawRoundRect(Color.White.copy(alpha = 0.22f), Offset(bx + bw * 0.08f, bodyH * 0.06f), Size(bw * 0.84f, bodyH * 0.38f), CornerRadius(r * 0.8f))
            drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(bx, 0f), Size(bw, bodyH), corner, style = Stroke(m * 0.03f))
            val cx = bx + bw / 2
            val cy = bodyH / 2
            val white = Color.White
            if (kind == Kind.ROTATE) {
                val ar = bw * 0.26f
                drawArc(white, -60f, 290f, false, Offset(cx - ar, cy - ar), Size(2 * ar, 2 * ar), style = Stroke(bw * 0.085f, cap = StrokeCap.Round))
                val a = Math.toRadians(230.0).toFloat()
                val px = cx + ar * cos(a); val py = cy + ar * sin(a)
                val k = bw * 0.1f
                val p = Path()
                p.moveTo(px - sin(a) * k * 1.4f, py + cos(a) * k * 1.4f)
                p.lineTo(px + cos(a) * k, py + sin(a) * k)
                p.lineTo(px - cos(a) * k, py - sin(a) * k)
                p.close()
                drawPath(p, white)
            } else {
                val s = m * 0.24f
                val p = Path()
                when (kind) {
                    Kind.LEFT -> { p.moveTo(cx + s * 0.5f, cy - s); p.lineTo(cx - s * 0.5f, cy); p.lineTo(cx + s * 0.5f, cy + s) }
                    Kind.RIGHT -> { p.moveTo(cx - s * 0.5f, cy - s); p.lineTo(cx + s * 0.5f, cy); p.lineTo(cx - s * 0.5f, cy + s) }
                    else -> { p.moveTo(cx - s, cy - s * 0.5f); p.lineTo(cx, cy + s * 0.5f); p.lineTo(cx + s, cy - s * 0.5f) }
                }
                drawPath(p, white, style = Stroke(m * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Flèches positionnées librement (en dp) dans la zone des pouces.
 * lshift déplace ◀ vers le bouton de rotation, jusqu'à le toucher ; shift écarte ▶ de ▼.
 */
@Composable
fun MoveCluster(game: Game, t: Theme, scale: Float, shift: Float, lshift: Float, mirror: Boolean, w: Float, modifier: Modifier) {
    val aw = 76f * scale; val ah = 66f * scale; val gap = 20f * scale; val vgap = 8f * scale
    val base = if (mirror) w - (2 * aw + gap + shift) else 0f
    val xL = (base + (if (mirror) -lshift else lshift)).coerceIn(0f, maxOf(0f, w - aw))
    val xR = base + aw + gap + shift
    val xD = base + aw / 2 + gap / 2
    Box(modifier.fillMaxWidth().height((2 * ah + vgap).dp)) {
        WoodButton(Kind.LEFT, t, Modifier.offset(xL.dp, 0.dp).size(aw.dp, ah.dp), repeat = true) { game.move(-1) }
        WoodButton(Kind.RIGHT, t, Modifier.offset(xR.dp, 0.dp).size(aw.dp, ah.dp), repeat = true) { game.move(1) }
        WoodButton(Kind.DOWN, t, Modifier.offset(xD.dp, (ah + vgap).dp).size(aw.dp, ah.dp), repeat = true) { game.softDrop() }
    }
}

@Composable
fun RotateButton(game: Game, t: Theme, modifier: Modifier) {
    WoodButton(Kind.ROTATE, t, modifier.size(116.dp)) { game.rotate() }
}

// ---------- menu de réglages ----------

@Composable
fun SettingsPanel(
    t: Theme,
    lift: Float, onLift: (Float) -> Unit, onLiftDone: () -> Unit,
    mirror: Boolean, onMirror: (Boolean) -> Unit,
    themeIdx: Int, onTheme: (Int) -> Unit,
    style: Int, onStyle: (Int) -> Unit,
    arrow: Float, onArrow: (Float) -> Unit, onArrowDone: () -> Unit,
    rshift: Float, onRshift: (Float) -> Unit, onRshiftDone: () -> Unit,
    lshift: Float, onLshift: (Float) -> Unit, onLshiftDone: () -> Unit,
    onClose: () -> Unit,
) {
    // Le panneau reste en haut : on voit les boutons bouger en bas pendant le réglage.
    val maxL = (LocalConfiguration.current.screenWidthDp - 24f - 116f - 8f - 76f * arrow).coerceAtLeast(1f)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth().heightIn(max = maxHeight * 0.62f)
                .clip(RoundedCornerShape(24.dp)).background(t.board).border(2.dp, t.plaque, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("RÉGLAGES", color = t.accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
            SettingSlider("Hauteur des boutons : " + lift.toInt() + " dp depuis le bas", lift, 0f..140f, t, onLift, onLiftDone)
            SettingSlider("Taille des flèches : " + (arrow * 100).toInt() + " %", arrow, 0.7f..1.5f, t, onArrow, onArrowDone)
            SettingSlider("Flèche gauche, vers le bouton de rotation : " + lshift.toInt() + " dp", lshift.coerceIn(0f, maxL), 0f..maxL, t, onLshift, onLshiftDone)
            SettingSlider("Décalage de la flèche droite : " + rshift.toInt() + " dp", rshift, 0f..160f, t, onRshift, onRshiftDone)
            Label("Disposition", t)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Rotation à droite", !mirror, t) { onMirror(false) }
                Chip("Inversé", mirror, t) { onMirror(true) }
            }
            Label("Thème", t)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                THEMES.forEachIndexed { i, th -> Chip(th.name, i == themeIdx, t) { onTheme(i) } }
            }
            Label("Style des pièces", t)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                STYLES.forEachIndexed { i, name -> StyleTile(name, i, i == style, t) { onStyle(i) } }
            }
            Button(
                onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.board),
            ) { Text("OK", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun Label(text: String, t: Theme) {
    Text(text, color = t.text.copy(alpha = 0.75f), fontSize = 12.sp)
}

@Composable
fun StyleTile(name: String, idx: Int, selected: Boolean, t: Theme, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(t.well)
                .border(2.dp, if (selected) t.accent else t.plaque, RoundedCornerShape(10.dp)),
        ) {
            val c = size.width / 3.2f
            val x0 = (size.width - 2 * c) / 2
            val y0 = (size.height - 2 * c) / 2
            cellAt(x0, y0, c, t.pieces[0], 1f, idx)
            cellAt(x0 + c, y0, c, t.pieces[1], 1f, idx)
            cellAt(x0, y0 + c, c, t.pieces[3], 1f, idx)
            cellAt(x0 + c, y0 + c, c, t.pieces[4], 1f, idx)
        }
        Text(name, color = t.text, fontSize = 10.sp)
    }
}

@Composable
fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, t: Theme, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Label(label, t)
    Slider(
        value = value, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = t.accent, activeTrackColor = t.accent, inactiveTrackColor = t.plaque),
    )
}

// ---------- écran de sélection du mode ----------

@Composable
fun ModeSelect(game: Game, t: Theme, onPick: (Mode) -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("TETRIS", color = t.accent, fontSize = 38.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Choisissez un mode de jeu", color = t.text.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Mode.values().forEach { m -> ModeCard(m, game.recordLabel(m), t) { onPick(m) } }
        }
        Button(
            onClick = onSettings, modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = t.plaque, contentColor = t.onPlaque),
        ) { Text("RÉGLAGES", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun ModeCard(m: Mode, record: String, t: Theme, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.plaque).clickable { onClick() }.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeVisual(m, t, Modifier.size(84.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(m.title, color = t.accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
            Text(m.desc, color = t.onPlaque, fontSize = 12.sp, lineHeight = 16.sp)
            Text(record, color = t.onPlaque.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Petite illustration du mode, dessinée avec les couleurs du thème. */
@Composable
fun ModeVisual(m: Mode, t: Theme, modifier: Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(t.board).border(2.dp, t.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) {
        val n = 6
        val s = size.width / n
        val p = t.pieces
        fun cell(x: Int, y: Int, c: Color) = cellAt(x * s, y * s, s, c, 1f, 0)
        when (m) {
            Mode.CLASSIC -> {
                for (x in 0..4) cell(x, 5, p[x % 7])
                cell(5, 5, p[4])
                for (x in 0..2) cell(x, 4, p[(x + 3) % 7])
                cell(2, 1, p[2]); cell(3, 1, p[2]); cell(4, 1, p[2]); cell(3, 2, p[2])
            }
            Mode.PICTURE -> {
                val rows = listOf(".#..#.", "######", "######", ".####.", "..##..")   // un petit cœur
                rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> if (ch == '#') cell(x, y + 1, Color(0xFFA8E4FF)) } }
            }
            Mode.MISSIONS -> {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(t.accent, size.width * 0.38f, c, style = Stroke(s * 0.3f))
                drawCircle(t.accent, size.width * 0.22f, c, style = Stroke(s * 0.3f))
                drawCircle(t.accent, size.width * 0.07f, c)
                drawLine(Color.White, c, Offset(c.x + s * 2.2f, c.y - s * 2.2f), s * 0.18f, StrokeCap.Round)
            }
            Mode.PUZZLE -> {
                for (y in 4..5) for (x in 0 until n) if (x != 2 && x != 3) cell(x, y, p[(x + y) % 7])
                cell(2, 1, p[1]); cell(3, 1, p[1]); cell(2, 2, p[1]); cell(3, 2, p[1])
            }
            Mode.CASCADE -> {
                for (y in 3..5) cell(0, y, p[0])
                cell(1, 5, p[1]); cell(2, 4, p[2]); cell(2, 5, p[2]); cell(4, 5, p[3])
                cell(1, 1, p[4]); cell(4, 2, p[5])
                for (ax in listOf(1.5f, 4.5f)) {
                    val tri = Path()
                    tri.moveTo(ax * s - s * 0.35f, (if (ax < 3f) 2.2f else 3.3f) * s)
                    tri.lineTo(ax * s + s * 0.35f, (if (ax < 3f) 2.2f else 3.3f) * s)
                    tri.lineTo(ax * s, (if (ax < 3f) 2.7f else 3.8f) * s)
                    tri.close()
                    drawPath(tri, t.accent)
                }
            }
            Mode.DUEL -> {
                val h = s * 0.55f
                val oxL = s * 0.15f
                val oxR = size.width - s * 0.15f - 5 * h
                for (x in 0..4) { cellAt(oxL + x * h, 10 * h, h, p[x % 7], 1f, 0); cellAt(oxR + x * h, 10 * h, h, p[(x + 3) % 7], 1f, 0) }
                for (x in 0..2) cellAt(oxL + x * h, 9 * h, h, p[(x + 1) % 7], 1f, 0)
                for (x in 1..4) cellAt(oxR + x * h, 9 * h, h, p[(x + 2) % 7], 1f, 0)
                cellAt(oxL, 8 * h, h, p[2], 1f, 0)
                cellAt(oxR + 3 * h, 8 * h, h, p[5], 1f, 0)
                drawLine(t.accent, Offset(size.width / 2, s * 0.6f), Offset(size.width / 2, size.height - s * 0.6f), s * 0.15f)
            }
            Mode.CHAOS -> {
                val c = Offset(size.width / 2, size.height * 0.58f)
                val r = size.width * 0.26f
                drawCircle(Color(0xFF2B2B2B), r, c)
                drawCircle(Color.White.copy(alpha = 0.35f), r * 0.25f, Offset(c.x - r * 0.35f, c.y - r * 0.35f))
                drawLine(Color(0xFFCCCCCC), Offset(c.x + r * 0.4f, c.y - r * 0.8f), Offset(c.x + r * 0.9f, c.y - r * 1.3f), s * 0.18f, StrokeCap.Round)
                drawCircle(t.accent, s * 0.28f, Offset(c.x + r * 0.95f, c.y - r * 1.4f))
            }
            Mode.SURVIVAL -> {
                val g = Color(0xFF8A8F98)
                for (y in 4..5) for (x in 0 until n) if (x != (if (y == 5) 2 else 4)) cell(x, y, g)
                cell(1, 3, p[0]); cell(2, 3, p[0]); cell(2, 2, p[1])
                val tri = Path()
                tri.moveTo(s * 4.5f, s * 0.4f); tri.lineTo(s * 5.4f, s * 1.6f); tri.lineTo(s * 3.6f, s * 1.6f); tri.close()
                drawPath(tri, t.accent)
            }
        }
    }
}

// ---------- DESSIN : récapitulatif des tableaux ----------

@Composable
fun TableauSelect(game: Game, t: Theme, onPlay: (Int) -> Unit, onBack: () -> Unit) {
    val todo = game.firstTodo()             // le jeu se place sur le premier tableau non validé
    var sel by remember { mutableIntStateOf(todo) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) { gridState.scrollToItem(maxOf(0, (todo - 1) / 5 - 1) * 5) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("DESSIN · TABLEAUX", color = t.accent, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            game.picDone.size.toString() + " / " + PICTURES.size + " validés",
            color = t.text.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5), state = gridState, modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PICTURES.size) { i ->
                val n = i + 1
                TableauTile(n, PICTURES[i], n in game.picDone, n == sel, n <= todo, t) { sel = n }
            }
        }
        Button(
            onClick = { onPlay(sel) }, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.board),
        ) { Text("JOUER LE TABLEAU " + sel, fontWeight = FontWeight.Bold) }
        Button(
            onClick = onBack, modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = t.plaque, contentColor = t.onPlaque),
        ) { Text("RETOUR", fontWeight = FontWeight.Bold) }
    }
}

/** Vignette d'un tableau : aperçu du dessin, numéro, ✓ vert si validé ; grisée tant qu'un tableau précédent n'est pas validé. */
@Composable
fun TableauTile(n: Int, pic: List<String>, done: Boolean, selected: Boolean, unlocked: Boolean, t: Theme, onClick: () -> Unit) {
    val border = if (selected) t.accent else if (done) Color(0xFF4CD964) else t.plaque.copy(alpha = 0.6f)
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(t.plaque)
            .border(if (selected) 3.dp else 1.5.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = unlocked) { onClick() },
    ) {
        Canvas(Modifier.fillMaxSize().padding(7.dp).graphicsLayer { alpha = if (unlocked) 1f else 0.3f }) {
            val s = size.width / 10
            val top = size.height - pic.size * s
            pic.forEachIndexed { r, row ->
                row.forEachIndexed { c, ch ->
                    if (ch == '#') drawRect(Color(0xFFA8E4FF), Offset(c * s, top + r * s), Size(s * 0.92f, s * 0.92f))
                }
            }
        }
        Text(n.toString(), color = t.onPlaque, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopStart).padding(5.dp))
        if (done) Text("✓", color = Color(0xFF4CD964), fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 5.dp))
    }
}

package com.example.tetris

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
    var showSettings by remember { mutableStateOf(false) }
    val t = THEMES[themeIdx]
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        game.onEvent = { n -> haptic.performHapticFeedback(if (n > 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) }
    }
    LaunchedEffect(game.state, game.level) {
        while (game.state == GState.PLAYING) {
            delay(game.delayMs)
            game.step()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { game.pause() }
    BackHandler(enabled = game.state == GState.PLAYING) { game.pause() }
    BackHandler(enabled = showSettings) { showSettings = false }

    Box(Modifier.fillMaxSize()) {
        Backdrop(t)
        Column(Modifier.fillMaxSize()) {
            // ---- zone de jeu : plateau à gauche, cadres à droite ----
            Row(
                Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(2.3f).fillMaxHeight()) {
                    ScoreBar(game.score, t)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopStart) { BoardView(game, t, style) }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        InfoPanel("RÉSERVE", t, Modifier.weight(1f)) { PieceCanvas(game.hold, t, style) }
                        WoodPill("II", t, Modifier.size(38.dp)) { game.pause() }
                    }
                    InfoPanel("SUIVANT", t, Modifier.fillMaxWidth()) { PieceCanvas(game.next, t, style) }
                    InfoPanel("NIVEAU", t, Modifier.fillMaxWidth(), t.accent) { Value(game.level.toString(), t) }
                    InfoPanel("LIGNES", t, Modifier.fillMaxWidth()) { Value(game.lines.toString(), t) }
                    InfoPanel("RECORD", t, Modifier.fillMaxWidth()) { Value(game.best.toString(), t) }
                    Spacer(Modifier.weight(1f))
                    WoodPill("RÉSERVE", t, Modifier.fillMaxWidth().height(44.dp)) { game.holdPiece() }
                }
            }
            // ---- zone des pouces ----
            Box(
                Modifier.fillMaxWidth()
                    .background(if (t.wood) Color(0xFFDDBB8E) else Color.Black.copy(alpha = 0.22f))
                    .navigationBarsPadding().height(150.dp + lift.dp),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp + lift.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!mirror) { MoveCluster(game, t); RotateButton(game, t) }
                    else { RotateButton(game, t); MoveCluster(game, t) }
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
                    { showSettings = false },
                )
            } else {
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
                            when (game.state) { GState.READY -> "TETRIS"; GState.PAUSED -> "PAUSE"; else -> "GAME OVER" },
                            color = t.accent, fontSize = 34.sp, fontWeight = FontWeight.Black,
                        )
                        if (game.state == GState.OVER) {
                            Text("Score : " + game.score + "\nRecord : " + game.best, color = t.text, textAlign = TextAlign.Center, fontSize = 18.sp)
                        }
                        val btn = ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.board)
                        Button(
                            onClick = { if (game.state == GState.PAUSED) game.resume() else game.start() },
                            colors = btn, modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(when (game.state) { GState.READY -> "JOUER"; GState.PAUSED -> "REPRENDRE"; else -> "REJOUER" }, fontWeight = FontWeight.Bold)
                        }
                        if (game.state == GState.PAUSED) {
                            Button(onClick = { game.start() }, colors = btn, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("RECOMMENCER") }
                        }
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
    Text(v, color = t.text, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
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

@Composable
fun BoardView(game: Game, t: Theme, style: Int) {
    Canvas(
        Modifier.aspectRatio(COLS / ROWS.toFloat(), matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(6.dp)).background(t.board)
            .border(3.dp, t.plaque, RoundedCornerShape(6.dp)),
    ) {
        val s = size.width / COLS
        for (i in 1 until COLS) drawLine(t.grid, Offset(i * s, 0f), Offset(i * s, size.height))
        for (j in 1 until ROWS) drawLine(t.grid, Offset(0f, j * s), Offset(size.width, j * s))
        val b = game.board
        for (i in b.indices) if (b[i] > 0) cellAt((i % COLS) * s, (i / COLS) * s, s, t.pieces[b[i] - 1], 1f, style)
        game.ghost()?.let { g -> g.cells.forEach { (x, y) -> if (y >= 0) cellAt(x * s, y * s, s, t.pieces[g.type], 0.25f, style) } }
        game.piece?.let { p -> p.cells.forEach { (x, y) -> if (y >= 0) cellAt(x * s, y * s, s, t.pieces[p.type], 1f, style) } }
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

private val RIM = Brush.verticalGradient(listOf(Color(0xFFF2F2F2), Color(0xFF858585)))

private fun btnBrush(c: Color, pressed: Boolean): Brush = Brush.verticalGradient(
    listOf(lerp(c, Color.White, 0.25f), lerp(c, Color.Black, 0.3f)).map { if (pressed) lerp(it, Color.Black, 0.25f) else it },
)

@Composable
fun WoodPill(label: String, t: Theme, modifier: Modifier, shape: Shape = RoundedCornerShape(50), action: () -> Unit) {
    val act by rememberUpdatedState(action)
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier.clip(shape).background(RIM).padding(3.dp).clip(shape).background(btnBrush(t.btn, pressed))
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; act(); tryAwaitRelease(); pressed = false }) },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp) }
}

enum class Kind { LEFT, RIGHT, DOWN, ROTATE }

/** Bouton dessiné au Canvas : flèches hexagonales, flèche du bas en « maison », gros bouton rond de rotation. */
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
        val shape = Path()
        when (kind) {
            Kind.LEFT -> { shape.moveTo(0f, h / 2); shape.lineTo(w * 0.28f, 0f); shape.lineTo(w, 0f); shape.lineTo(w, h); shape.lineTo(w * 0.28f, h); shape.close() }
            Kind.RIGHT -> { shape.moveTo(w, h / 2); shape.lineTo(w * 0.72f, 0f); shape.lineTo(0f, 0f); shape.lineTo(0f, h); shape.lineTo(w * 0.72f, h); shape.close() }
            Kind.DOWN -> { shape.moveTo(w / 2, 0f); shape.lineTo(w, h * 0.28f); shape.lineTo(w, h); shape.lineTo(0f, h); shape.lineTo(0f, h * 0.28f); shape.close() }
            Kind.ROTATE -> shape.addOval(Rect(0f, 0f, w, h))
        }
        drawPath(shape, RIM)
        scale(0.88f) { drawPath(shape, btnBrush(t.btn, pressed)) }
        val white = Color.White
        when (kind) {
            Kind.LEFT -> {
                val cx = w * 0.55f; val cy = h / 2; val s = h * 0.26f
                val p = Path()
                p.moveTo(cx - s, cy); p.lineTo(cx + s * 0.8f, cy - s); p.lineTo(cx + s * 0.8f, cy + s); p.close()
                drawPath(p, white)
            }
            Kind.RIGHT -> {
                val cx = w * 0.45f; val cy = h / 2; val s = h * 0.26f
                val p = Path()
                p.moveTo(cx + s, cy); p.lineTo(cx - s * 0.8f, cy - s); p.lineTo(cx - s * 0.8f, cy + s); p.close()
                drawPath(p, white)
            }
            Kind.DOWN -> {
                val cx = w / 2; val cy = h * 0.64f; val s = w * 0.26f
                val p = Path()
                p.moveTo(cx - s, cy - s * 0.7f); p.lineTo(cx + s, cy - s * 0.7f); p.lineTo(cx, cy + s * 0.9f); p.close()
                drawPath(p, white)
                drawLine(white.copy(alpha = 0.85f), Offset(w * 0.3f, h * 0.38f), Offset(w * 0.7f, h * 0.38f), h * 0.035f)
                drawLine(white.copy(alpha = 0.85f), Offset(w * 0.3f, h * 0.47f), Offset(w * 0.7f, h * 0.47f), h * 0.035f)
            }
            Kind.ROTATE -> {
                val cx = w / 2; val cy = h / 2; val r = w * 0.24f
                drawArc(white, -60f, 290f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(w * 0.075f, cap = StrokeCap.Round))
                val a = Math.toRadians(230.0).toFloat()
                val px = cx + r * cos(a); val py = cy + r * sin(a)
                val k = w * 0.1f
                val p = Path()
                p.moveTo(px - sin(a) * k * 1.4f, py + cos(a) * k * 1.4f)
                p.lineTo(px + cos(a) * k, py + sin(a) * k)
                p.lineTo(px - cos(a) * k, py - sin(a) * k)
                p.close()
                drawPath(p, white)
            }
        }
    }
}

@Composable
fun MoveCluster(game: Game, t: Theme) {
    Box(Modifier.width(188.dp).fillMaxHeight()) {
        WoodButton(Kind.LEFT, t, Modifier.align(Alignment.TopStart).size(84.dp, 62.dp), repeat = true) { game.move(-1) }
        WoodButton(Kind.RIGHT, t, Modifier.align(Alignment.TopEnd).size(84.dp, 62.dp), repeat = true) { game.move(1) }
        WoodButton(Kind.DOWN, t, Modifier.align(Alignment.BottomCenter).size(56.dp, 78.dp), repeat = true) { game.softDrop() }
    }
}

@Composable
fun RotateButton(game: Game, t: Theme) {
    WoodButton(Kind.ROTATE, t, Modifier.size(116.dp)) { game.rotate() }
}

// ---------- menu de réglages ----------

@Composable
fun SettingsPanel(
    t: Theme,
    lift: Float, onLift: (Float) -> Unit, onLiftDone: () -> Unit,
    mirror: Boolean, onMirror: (Boolean) -> Unit,
    themeIdx: Int, onTheme: (Int) -> Unit,
    style: Int, onStyle: (Int) -> Unit,
    onClose: () -> Unit,
) {
    // Le panneau reste en haut : on voit les boutons bouger en bas pendant le réglage.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth().heightIn(max = maxHeight * 0.62f)
                .clip(RoundedCornerShape(24.dp)).background(t.board).border(2.dp, t.plaque, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("RÉGLAGES", color = t.accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Label("Hauteur des boutons : " + lift.toInt() + " dp depuis le bas", t)
            Slider(
                value = lift, onValueChange = onLift, onValueChangeFinished = onLiftDone, valueRange = 0f..140f,
                colors = SliderDefaults.colors(thumbColor = t.accent, activeTrackColor = t.accent, inactiveTrackColor = t.plaque),
            )
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

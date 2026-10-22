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
import androidx.compose.ui.graphics.StrokeJoin
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
    val zoneH = maxOf(150f, 140f * arrow + 14f)
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

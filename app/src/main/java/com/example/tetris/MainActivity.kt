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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(t.bg))) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("SCORE", game.score.toString(), t, Modifier.weight(1.5f))
                Stat("NIVEAU", game.level.toString(), t, Modifier.weight(1f))
                Stat("LIGNES", game.lines.toString(), t, Modifier.weight(1f))
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    BoardView(game, t)
                }
                Column(Modifier.width(92.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Preview("RÉSERVE", game.hold, t)
                    Preview("SUIVANT", game.next, t)
                    Stat("RECORD", game.best.toString(), t, Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    CtrlButton("II", t, Modifier.fillMaxWidth()) { game.pause() }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtrlButton("RÉSERVE", t, Modifier.weight(1.3f), fs = 11) { game.holdPiece() }
                CtrlButton("◀", t, Modifier.weight(1f), repeat = true) { game.move(-1) }
                CtrlButton("▶", t, Modifier.weight(1f), repeat = true) { game.move(1) }
                CtrlButton("↻", t, Modifier.weight(1f)) { game.rotate() }
                CtrlButton("▼", t, Modifier.weight(1f), repeat = true) { game.softDrop() }
                CtrlButton("⇩", t, Modifier.weight(1f)) { game.hardDrop() }
            }
        }

        if (game.state != GState.PLAYING) {
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
                    Text("THÈME", color = t.text.copy(alpha = 0.7f), fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        THEMES.forEachIndexed { i, th ->
                            val sel = i == themeIdx
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(if (sel) t.accent else t.panel)
                                    .clickable { themeIdx = i; prefs.edit().putInt("theme", i).apply() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text(th.name, color = if (sel) t.board else t.text, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.cellAt(px: Float, py: Float, s: Float, c: Color, a: Float = 1f) {
    val pad = s * 0.06f
    drawRoundRect(c.copy(alpha = a), Offset(px + pad, py + pad), Size(s - 2 * pad, s - 2 * pad), CornerRadius(s * 0.22f))
    if (a == 1f) drawRoundRect(
        Color.White.copy(alpha = 0.25f), Offset(px + pad * 2, py + pad * 2),
        Size(s - 4 * pad, (s - 4 * pad) * 0.35f), CornerRadius(s * 0.15f),
    )
}

@Composable
fun BoardView(game: Game, t: Theme) {
    Canvas(
        Modifier.aspectRatio(COLS / ROWS.toFloat(), matchHeightConstraintsFirst = true)
            .clip(RoundedCornerShape(12.dp)).background(t.board)
            .border(2.dp, t.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .pointerInput(Unit) { detectTapGestures { game.rotate() } },
    ) {
        val s = size.width / COLS
        for (i in 1 until COLS) drawLine(t.grid, Offset(i * s, 0f), Offset(i * s, size.height))
        for (j in 1 until ROWS) drawLine(t.grid, Offset(0f, j * s), Offset(size.width, j * s))
        val b = game.board
        for (i in b.indices) if (b[i] > 0) cellAt((i % COLS) * s, (i / COLS) * s, s, t.pieces[b[i] - 1])
        game.ghost()?.let { g -> g.cells.forEach { (x, y) -> if (y >= 0) cellAt(x * s, y * s, s, t.pieces[g.type], 0.25f) } }
        game.piece?.let { p -> p.cells.forEach { (x, y) -> if (y >= 0) cellAt(x * s, y * s, s, t.pieces[p.type]) } }
    }
}

@Composable
fun Panel(t: Theme, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(t.panel).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, content = content,
    )
}

@Composable
fun Stat(label: String, value: String, t: Theme, modifier: Modifier) {
    Panel(t, modifier) {
        Text(label, color = t.text.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = t.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Preview(title: String, type: Int, t: Theme) {
    Panel(t, Modifier.fillMaxWidth()) {
        Text(title, color = t.text.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            if (type < 0) return@Canvas
            val cells = SHAPES[type][0]
            val s = size.width / 4.5f
            val w = cells.maxOf { it.first } - cells.minOf { it.first } + 1
            val h = cells.maxOf { it.second } - cells.minOf { it.second } + 1
            val ox = (size.width - w * s) / 2 - cells.minOf { it.first } * s
            val oy = (size.height - h * s) / 2 - cells.minOf { it.second } * s
            cells.forEach { (x, y) -> cellAt(ox + x * s, oy + y * s, s, t.pieces[type]) }
        }
    }
}

@Composable
fun CtrlButton(label: String, t: Theme, modifier: Modifier, repeat: Boolean = false, fs: Int = 22, action: () -> Unit) {
    val scope = rememberCoroutineScope()
    val act by rememberUpdatedState(action)
    Box(
        modifier.height(58.dp).clip(RoundedCornerShape(16.dp)).background(t.panel).pointerInput(repeat) {
            detectTapGestures(onPress = {
                act()
                val job = if (repeat) scope.launch { delay(170); while (true) { act(); delay(55) } } else null
                tryAwaitRelease()
                job?.cancel()
            })
        },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = t.text, fontSize = fs.sp, fontWeight = FontWeight.Bold) }
}

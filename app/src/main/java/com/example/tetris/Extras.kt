package com.example.tetris

import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val APP_TITLE = "TETRIS CHAOS"

/** Réglages d'affichage, de son et de disposition des boutons (enregistrés dans les préférences). */
class UiPrefs(private val p: SharedPreferences) {
    var themeIdx by mutableIntStateOf(p.getInt("theme", 0).coerceIn(0, THEMES.lastIndex))
    var mirror by mutableStateOf(p.getBoolean("mirror", false))      // true = rotation à gauche, flèches à droite
    var lift by mutableFloatStateOf(p.getFloat("lift", 60f))          // décalage des boutons depuis le bas (dp)
    var style by mutableIntStateOf(p.getInt("style", 0).coerceIn(0, STYLES.lastIndex))
    var arrow by mutableFloatStateOf(p.getFloat("arrow", 1f))         // taille des flèches (x1)
    var rshift by mutableFloatStateOf(p.getFloat("rshift", 0f))       // décalage de la flèche droite (dp)
    var lshift by mutableFloatStateOf(p.getFloat("lshift", 0f))       // décalage de la flèche gauche vers la rotation (dp)
    var musicVol by mutableFloatStateOf(p.getFloat("music_vol", 0.35f))
    var sfxVol by mutableFloatStateOf(p.getFloat("sfx_vol", 0.7f))
    var vib by mutableStateOf(p.getBoolean("vib", true))
    var autoRotate by mutableStateOf(p.getBoolean("auto_rotate", false))
    /** Positions libres des boutons (dp dans la zone des pouces) : L ◀, R ▶, D ▼, T rotation. Absent = disposition automatique. */
    var custom by mutableStateOf(loadCustom())

    private fun loadCustom(): Map<String, Pair<Float, Float>> =
        (p.getString("custom", "") ?: "").split(";").mapNotNull { e ->
            val k = e.substringBefore(":", "")
            val v = e.substringAfter(":", "").split(",")
            val x = v.getOrNull(0)?.toFloatOrNull()
            val y = v.getOrNull(1)?.toFloatOrNull()
            if (k.isNotEmpty() && x != null && y != null) k to Pair(x, y) else null
        }.toMap()

    fun commit() {
        p.edit()
            .putInt("theme", themeIdx).putBoolean("mirror", mirror).putFloat("lift", lift).putInt("style", style)
            .putFloat("arrow", arrow).putFloat("rshift", rshift).putFloat("lshift", lshift)
            .putFloat("music_vol", musicVol).putFloat("sfx_vol", sfxVol).putBoolean("vib", vib).putBoolean("auto_rotate", autoRotate)
            .putString("custom", custom.entries.joinToString(";") { it.key + ":" + it.value.first + "," + it.value.second })
            .apply()
    }
}

@Composable
fun Header(text: String, t: Theme) {
    Text(text, color = t.accent, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun SwitchRow(label: String, checked: Boolean, t: Theme, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = t.board, checkedTrackColor = t.accent, uncheckedTrackColor = t.plaque),
        )
    }
}

fun fmtLong(ms: Long): String {
    val s = ms / 1000
    return (s / 3600).toString() + " h " + "%02d".format((s / 60) % 60) + " min " + "%02d".format(s % 60) + " s"
}

// ---------- DESSIN / PUZZLE : récapitulatif des niveaux ----------

@Composable
fun LevelSelect(
    title: String, count: Int, done: Set<Int>, stars: Map<Int, Int>, todo: Int,
    preview: (Int) -> List<String>, t: Theme, onPlay: (Int) -> Unit, onBack: () -> Unit,
) {
    var sel by remember { mutableIntStateOf(todo) }   // le jeu se place sur le premier niveau non validé
    val gridState = rememberLazyGridState()
    LaunchedEffect(Unit) { gridState.scrollToItem(maxOf(0, (todo - 1) / 5 - 1) * 5) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = t.accent, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            done.size.toString() + " / " + count + " validés  ·  ★ " + stars.values.sum(),
            color = t.text.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5), state = gridState, modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(count) { i ->
                val n = i + 1
                LevelTile(n, preview(i), n in done, stars[n] ?: 0, n == sel, n <= todo, t) { sel = n }
            }
        }
        Button(
            onClick = { onPlay(sel) }, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.board),
        ) { Text("JOUER LE NIVEAU " + sel, fontWeight = FontWeight.Bold) }
        Button(
            onClick = onBack, modifier = Modifier.fillMaxWidth().height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = t.plaque, contentColor = t.onPlaque),
        ) { Text("RETOUR", fontWeight = FontWeight.Bold) }
    }
}

/** Vignette d'un niveau : aperçu du dessin, numéro, ✓ et étoiles si validé ; grisée tant qu'un niveau précédent n'est pas validé. */
@Composable
fun LevelTile(n: Int, pic: List<String>, done: Boolean, stars: Int, selected: Boolean, unlocked: Boolean, t: Theme, onClick: () -> Unit) {
    val green = Color(0xFF4CD964)
    val border = if (selected) t.accent else if (done) green else t.plaque.copy(alpha = 0.6f)
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
        if (done) {
            Text("✓", color = green, fontSize = 15.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = 5.dp))
            Text(
                "★".repeat(stars) + "☆".repeat(3 - stars), color = Color(0xFFFFD54F), fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
    }
}

// ---------- statistiques ----------

@Composable
fun StatRow(label: String, value: String, t: Theme) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.plaque).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = t.onPlaque, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = t.accent, fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
    }
}

@Composable
fun StatsScreen(game: Game, t: Theme, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("STATISTIQUES", color = t.accent, fontSize = 30.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Header("EN TOUT", t)
            StatRow("Parties jouées", game.statGet("games").toString(), t)
            StatRow("Lignes effacées", game.statGet("lines").toString(), t)
            StatRow("Tétris (4 lignes d'un coup)", game.statGet("tetris").toString(), t)
            StatRow("Temps de jeu", fmtLong(game.statGet("time")), t)
            StatRow("Duels gagnés / perdus", game.statGet("duel_win").toString() + " / " + game.statGet("duel_loss"), t)
            Header("PAR MODE", t)
            Mode.values().forEach { m ->
                StatRow(m.title, game.statGet("games_" + m.name).toString() + " parties · " + game.recordLabel(m), t)
            }
            Header("PROGRESSION", t)
            StatRow("Dessin : tableaux validés", game.picDone.size.toString() + " / " + PICTURES.size + "  ·  ★ " + game.picStars.values.sum(), t)
            StatRow("Puzzle : niveaux validés", game.puzDone.size.toString() + " / " + PUZZLES.size + "  ·  ★ " + game.puzStars.values.sum(), t)
        }
        Button(
            onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = t.accent, contentColor = t.board),
        ) { Text("RETOUR", fontWeight = FontWeight.Bold) }
    }
}

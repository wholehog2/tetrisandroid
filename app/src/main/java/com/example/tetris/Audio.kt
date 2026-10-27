package com.example.tetris

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Sons, musique et vibrations. Tout est synthétisé par le code : aucun fichier audio dans l'application. */
class Audio(ctx: Context) {
    private val rate = 22050
    var sfxVol = 0.7f
    var musicVol = 0.35f
    var vibrate = true

    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator? = ctx.getSystemService(Vibrator::class.java)
    private val sounds = HashMap<String, ShortArray>()
    private var musicData: ShortArray? = null
    private var music: AudioTrack? = null
    private var wantMusic = false

    init {
        Thread { musicData = buildMusic() }.start()   // la boucle musicale est préparée en arrière-plan
    }

    // ---------- synthèse ----------

    private fun hz(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Une note : wave 0 = sinus, 1 = carré, 2 = triangle, 3 = bruit. */
    private fun note(freq: Double, ms: Int, vol: Double = 0.5, wave: Int = 0): ShortArray {
        val n = rate * ms / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            val ph = (t * freq) % 1.0
            val s = when (wave) {
                0 -> sin(2 * PI * freq * t)
                1 -> if (ph < 0.5) 1.0 else -1.0
                2 -> 4 * abs(ph - 0.5) - 1.0
                else -> Random.nextDouble() * 2 - 1
            }
            val env = minOf(1.0, i / (rate * 0.004)) * minOf(1.0, (n - i) / (rate * 0.03))
            out[i] = (s * env * vol * 32767).toInt().toShort()
        }
        return out
    }

    private fun join(parts: List<ShortArray>): ShortArray {
        val out = ShortArray(parts.sumOf { it.size })
        var at = 0
        for (p in parts) { System.arraycopy(p, 0, out, at, p.size); at += p.size }
        return out
    }

    private fun mix(parts: List<ShortArray>): ShortArray {
        val out = ShortArray(parts.maxOf { it.size })
        for (i in out.indices) {
            var v = 0
            for (p in parts) if (i < p.size) v += p[i]
            out[i] = v.coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    private fun fade(a: ShortArray): ShortArray {
        val out = ShortArray(a.size)
        for (i in a.indices) out[i] = (a[i] * (1.0 - i.toDouble() / a.size)).toInt().toShort()
        return out
    }

    private fun seq(ms: Int, wave: Int, vol: Double, vararg midi: Int): ShortArray = join(midi.map { note(hz(it), ms, vol, wave) })

    private fun build(name: String): ShortArray? = when (name) {
        "rot" -> note(hz(88), 35, 0.3, 1)
        "lock" -> join(listOf(note(hz(57), 45, 0.5, 2), note(hz(50), 70, 0.5, 2)))
        "clear1" -> seq(70, 2, 0.5, 72, 76)
        "clear2" -> seq(70, 2, 0.5, 72, 76, 79)
        "clear3" -> seq(75, 2, 0.5, 72, 76, 79, 84)
        "tetris" -> join(listOf(
            seq(70, 1, 0.3, 72, 76, 79, 84, 88, 91),
            fade(mix(listOf(note(hz(84), 650, 0.25, 0), note(hz(88), 650, 0.25, 0), note(hz(91), 650, 0.25, 0), note(hz(96), 650, 0.25, 0)))),
        ))
        "combo" -> seq(60, 0, 0.45, 79, 83, 86)
        "over" -> seq(170, 2, 0.5, 67, 64, 60, 55)
        "win" -> join(listOf(seq(120, 1, 0.3, 72, 76, 79, 84), fade(note(hz(88), 450, 0.4, 1))))
        "mission" -> join(listOf(seq(90, 0, 0.5, 79, 84), note(hz(88), 160, 0.5, 0)))
        "level" -> seq(90, 0, 0.5, 76, 79, 83, 88)
        "record" -> join(listOf(seq(90, 1, 0.3, 72, 76, 79, 84), seq(90, 1, 0.3, 79, 84, 88, 91), fade(note(hz(96), 500, 0.4, 1))))
        "bomb" -> mix(listOf(fade(note(80.0, 300, 0.7, 0)), fade(note(100.0, 280, 0.5, 3))))
        "event" -> join(listOf(note(hz(81), 60, 0.3, 1), note(hz(74), 90, 0.3, 1)))
        else -> null
    }

    private fun newTrack(data: ShortArray, loop: Boolean): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(data, 0, data.size)
        if (loop) t.setLoopPoints(0, data.size, -1)
        return t
    }

    // ---------- effets sonores ----------

    fun play(name: String) {
        if (sfxVol <= 0f) return
        val data = sounds[name] ?: (build(name) ?: return).also { sounds[name] = it }
        try {
            val t = newTrack(data, false)
            t.setVolume(sfxVol)
            t.play()
            handler.postDelayed({ try { t.release() } catch (e: Exception) { } }, data.size * 1000L / rate + 400L)
        } catch (e: Exception) { }
    }

    // ---------- musique ----------

    /** Boucle originale de 4 accords (la, fa, do, sol) : basse, arpège et mélodie. */
    private fun buildMusic(): ShortArray {
        val bpm = 132.0
        val msE = (60000.0 / bpm / 2).toInt()
        val eighth = rate * msE / 1000
        val chords = listOf(45 to intArrayOf(0, 3, 7, 12), 41 to intArrayOf(0, 4, 7, 12), 48 to intArrayOf(0, 4, 7, 12), 43 to intArrayOf(0, 4, 7, 12))
        val scale = intArrayOf(69, 72, 74, 76, 79, 81, 84)
        val lead = listOf(
            intArrayOf(3, -1, 5, 6, 5, 3, 2, 3, 0, -1, 2, 3, 2, 0, -1, -1),
            intArrayOf(2, -1, 3, 5, 3, 2, 1, 2, 3, -1, 5, 6, 5, 3, -1, -1),
            intArrayOf(4, -1, 5, 6, 5, 4, 3, 4, 5, -1, 6, 5, 4, 3, 2, -1),
            intArrayOf(2, -1, 3, 4, 3, 2, 1, 0, 1, -1, 2, 3, 2, 1, 0, -1),
        )
        val total = eighth * 16 * 4
        val acc = DoubleArray(total)
        fun add(data: ShortArray, at: Int, gain: Double) {
            for (i in data.indices) { val j = at + i; if (j < total) acc[j] += data[i] * gain }
        }
        for (c in 0 until 4) {
            val (root, tones) = chords[c]
            for (e in 0 until 16) {
                val at = (c * 16 + e) * eighth
                if (e % 2 == 0) add(note(hz(root), msE * 2 - 20, 0.5, 2), at, 1.0)
                val ar = intArrayOf(0, 1, 2, 3, 2, 1, 2, 3)[e % 8]
                add(note(hz(root + 24 + tones[ar]), msE - 15, 0.25, 1), at, 0.5)
                val li = lead[c][e]
                if (li >= 0) add(note(hz(scale[li]), msE - 10, 0.3, 1), at, 0.6)
            }
        }
        var peak = 1.0
        for (v in acc) if (abs(v) > peak) peak = abs(v)
        val out = ShortArray(total)
        for (i in 0 until total) out[i] = (acc[i] / peak * 0.8 * 32767).toInt().toShort()
        return out
    }

    fun startMusic() { wantMusic = true; tryStartMusic() }

    private fun tryStartMusic() {
        if (!wantMusic || music != null || musicVol <= 0f) return
        val data = musicData ?: run { handler.postDelayed({ tryStartMusic() }, 400); return }
        try {
            val t = newTrack(data, true)
            t.setVolume(musicVol)
            t.play()
            music = t
        } catch (e: Exception) { }
    }

    fun stopMusic() {
        wantMusic = false
        music?.let { try { it.stop(); it.release() } catch (e: Exception) { } }
        music = null
    }

    fun setMusicVolume(v: Float) {
        musicVol = v
        if (v <= 0f) stopMusic() else music?.setVolume(v)
    }

    // ---------- vibrations ----------

    fun haptic(name: String) {
        if (!vibrate) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (name) {
            "rot" -> VibrationEffect.createOneShot(12, 60)
            "lock" -> VibrationEffect.createOneShot(25, 120)
            "clear1" -> VibrationEffect.createOneShot(40, 150)
            "clear2" -> VibrationEffect.createWaveform(longArrayOf(0, 40, 50, 40), -1)
            "clear3" -> VibrationEffect.createWaveform(longArrayOf(0, 40, 40, 40, 40, 60), -1)
            "tetris" -> VibrationEffect.createWaveform(longArrayOf(0, 50, 40, 50, 40, 50, 40, 220), -1)
            "combo" -> VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 30), -1)
            "bomb" -> VibrationEffect.createOneShot(200, 255)
            "over" -> VibrationEffect.createOneShot(400, 200)
            "win", "level", "mission", "record" -> VibrationEffect.createWaveform(longArrayOf(0, 60, 60, 120), -1)
            else -> null
        } ?: return
        try { v.vibrate(effect) } catch (e: Exception) { }
    }
}

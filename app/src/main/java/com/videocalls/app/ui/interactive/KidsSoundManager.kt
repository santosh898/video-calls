package com.videocalls.app.ui.interactive

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object KidsSoundManager {
    private val scope = CoroutineScope(Dispatchers.Default)

    /** Plays a bubbly pop sound (fast frequency drop) */
    fun playPop() {
        scope.launch {
            playToneSweep(startFreq = 650f, endFreq = 220f, durationMs = 45, volume = 0.8f)
        }
    }

    /** Plays a sparkling magic twinkle chime */
    fun playSparkle() {
        scope.launch {
            val notes = listOf(1046.5f, 1318.5f, 1567.98f, 2093.0f) // C6, E6, G6, C7
            val note = notes.random()
            playToneSweep(startFreq = note, endFreq = note * 1.15f, durationMs = 70, volume = 0.5f)
        }
    }

    /** Plays a cheerful peek-a-boo celebratory chime */
    fun playPeekABoo() {
        scope.launch {
            val notes = listOf(523.25f to 90, 659.25f to 90, 783.99f to 90, 1046.50f to 200) // C5, E5, G5, C6
            for ((freq, dur) in notes) {
                playToneSweep(startFreq = freq, endFreq = freq, durationMs = dur, volume = 0.7f)
                kotlinx.coroutines.delay(dur.toLong())
            }
        }
    }

    /** Plays a funny animal boing / sound */
    fun playBoing() {
        scope.launch {
            playToneSweep(startFreq = 200f, endFreq = 800f, durationMs = 120, volume = 0.7f)
        }
    }

    private fun playToneSweep(startFreq: Float, endFreq: Float, durationMs: Int, volume: Float) {
        try {
            val sampleRate = 44100
            val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
            val buffer = ShortArray(numSamples)

            var phase = 0.0
            for (i in 0 until numSamples) {
                val progress = i.toFloat() / numSamples
                val currentFreq = startFreq + (endFreq - startFreq) * progress
                val phaseIncrement = 2.0 * PI * currentFreq / sampleRate
                phase += phaseIncrement

                // Fade in and out envelope to prevent clicking
                val envelope = when {
                    progress < 0.1f -> progress / 0.1f
                    progress > 0.8f -> (1f - progress) / 0.2f
                    else -> 1f
                }
                val sample = (sin(phase) * 32767 * volume * envelope).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            Thread.sleep(durationMs.toLong() + 30L)
            audioTrack.release()
        } catch (_: Exception) {}
    }
}

package com.example.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object SoundFeedbackHelper {
    private const val PREFS_NAME = "attendance_audio_prefs"
    private const val KEY_SOUND_ENABLED = "sound_feedback_enabled"
    private val audioScope = CoroutineScope(Dispatchers.Default)

    fun isSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
    }

    fun toggleSound(context: Context): Boolean {
        val newState = !isSoundEnabled(context)
        setSoundEnabled(context, newState)
        return newState
    }

    /**
     * Instant, pleasant, low-latency success chime sound (similar to Apple Pay ding / pleasant marimba pop, 44.1kHz audio).
     */
    fun playApplePaySuccessDing(context: Context) {
        if (!isSoundEnabled(context)) return
        audioScope.launch {
            synthesizeAndPlayTone(
                freq1 = 1046.5, // C6
                freq2 = 1318.5, // E6 harmonic overtone
                durationMs = 240,
                volume = 0.65f,
                decayRate = 4.2
            )
        }
    }

    /**
     * Heavy / Success haptic vibration pattern for self-attendance registration.
     */
    fun performSuccessHaptic(context: Context) {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 35, 40, 55)
                    val amplitudes = intArrayOf(0, 180, 0, 255)
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 40, 40, 60), -1)
                }
            }
        } catch (e: Throwable) {
            Log.d("SoundFeedback", "Haptic bypassed: ${e.message}")
        }
    }

    /**
     * Soft, pleasant high chime (two-note harmonic chime ~180ms) for Present
     */
    fun playPresentSound(context: Context) {
        if (!isSoundEnabled(context)) return
        audioScope.launch {
            synthesizeAndPlayTone(
                freq1 = 659.25, // E5
                freq2 = 880.0,  // A5
                durationMs = 180,
                volume = 0.55f,
                decayRate = 5.0
            )
        }
    }

    /**
     * Distinct, soft lower tone (gentle ~160ms, not harsh or alarming) for Absent
     */
    fun playAbsentSound(context: Context) {
        if (!isSoundEnabled(context)) return
        audioScope.launch {
            synthesizeAndPlayTone(
                freq1 = 293.66, // D4
                freq2 = null,
                durationMs = 160,
                volume = 0.45f,
                decayRate = 6.0
            )
        }
    }

    /**
     * Gentle neutral tone (~150ms) for Late
     */
    fun playLateSound(context: Context) {
        if (!isSoundEnabled(context)) return
        audioScope.launch {
            synthesizeAndPlayTone(
                freq1 = 440.0, // A4
                freq2 = 554.37, // C#5
                durationMs = 150,
                volume = 0.45f,
                decayRate = 5.5
            )
        }
    }

    private fun synthesizeAndPlayTone(
        freq1: Double,
        freq2: Double?,
        durationMs: Int,
        volume: Float,
        decayRate: Double
    ) {
        var track: AudioTrack? = null
        try {
            val sampleRate = 44100
            val totalSamples = (sampleRate * (durationMs / 1000.0)).toInt()
            val pcmData = ShortArray(totalSamples)

            val attackSamples = (sampleRate * 0.015).toInt() // 15ms gentle attack

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                var wave = sin(2.0 * PI * freq1 * t)
                if (freq2 != null) {
                    wave = (wave * 0.65) + (sin(2.0 * PI * freq2 * t) * 0.35)
                }

                // Envelope: Smooth linear attack, then natural exponential decay
                val envelope = if (i < attackSamples) {
                    i.toDouble() / attackSamples
                } else {
                    exp(-decayRate * (t - (attackSamples.toDouble() / sampleRate)))
                }

                val sample = (wave * envelope * Short.MAX_VALUE * volume)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                pcmData[i] = sample.toShort()
            }

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
                .setBufferSizeInBytes(pcmData.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcmData, 0, pcmData.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 30L)
            track.stop()
        } catch (e: Throwable) {
            Log.d("SoundFeedback", "Playback bypassed: ${e.message}")
        } finally {
            try {
                track?.release()
            } catch (_: Throwable) {}
        }
    }
}

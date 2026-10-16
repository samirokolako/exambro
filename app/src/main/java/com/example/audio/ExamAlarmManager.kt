package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * ExamAlarmManager handles two distinct audio alarms:
 *
 * 1. Alarm A (Alarm Keluar):
 *    - Triggered when Exit button is pressed.
 *    - High-pitch alternating two-tone siren (850Hz <-> 1200Hz).
 *    - Loops continuously while the exit dialog is active.
 *    - Stops only when KEMBALI is pressed or correct exit code (1234) is entered.
 *    - Remains playing if wrong code is entered.
 *
 * 2. Alarm B (Alarm Kecurangan):
 *    - Triggered when window loses focus during exam, app switch attempt, or blocked navigation.
 *    - Distinct rapid staccato buzzer tone (520Hz + harmonics).
 *    - Automatically stops after exactly 5 seconds.
 *    - Protected against stacking/overlapping instances.
 */
class ExamAlarmManager(private val context: Context) {

    private val tag = "ExamAlarmManager"
    private val scope = CoroutineScope(Dispatchers.Default)

    // Alarm A state
    private var isAlarmARunning = false
    private var alarmAJob: Job? = null
    private var audioTrackA: AudioTrack? = null

    // Alarm B state
    private var isAlarmBRunning = false
    private var alarmBJob: Job? = null
    private var audioTrackB: AudioTrack? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var volumeEnforcerJob: Job? = null

    fun isAnyAlarmRunning(): Boolean = isAlarmARunning || isAlarmBRunning

    /**
     * Overrides device volume to 100% MAXIMUM and forces un-mute.
     * Even if phone is set to silent or vibrate mode, STREAM_ALARM will blast at maximum volume.
     */
    fun enforceMaximumAlarmVolume() {
        try {
            audioManager?.let { am ->
                val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)

                val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not set stream volume directly: ${e.message}")
        }
    }

    /**
     * Relentlessly enforces maximum volume in a loop as long as any alarm is sounding.
     * If the student tries to press volume down or mute, it instantly forces volume UP to max.
     */
    private fun startContinuousVolumeEnforcement() {
        if (volumeEnforcerJob?.isActive == true) return
        volumeEnforcerJob = scope.launch {
            while (isActive && isAnyAlarmRunning()) {
                enforceMaximumAlarmVolume()
                delay(200) // Re-enforce every 200ms
            }
        }
    }

    private fun checkStopVolumeEnforcement() {
        if (!isAnyAlarmRunning()) {
            volumeEnforcerJob?.cancel()
            volumeEnforcerJob = null
        }
    }

    /**
     * Start Alarm A (Alarm Keluar)
     * Continuous two-tone emergency siren
     */
    @Synchronized
    fun startAlarmA() {
        if (isAlarmARunning) {
            Log.d(tag, "Alarm A already running, ignoring duplicate request")
            return
        }
        isAlarmARunning = true
        Log.i(tag, "Starting Alarm A (Exit confirmation alert) with MAXIMUM VOLUME")
        enforceMaximumAlarmVolume()
        startContinuousVolumeEnforcement()

        alarmAJob = scope.launch {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
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
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrackA = track

            try {
                track.play()
                val chunkSamples = sampleRate / 4 // 250ms chunks
                val buffer = ShortArray(chunkSamples)
                var toggle = false

                // Vibrate pattern for Alarm A
                vibrateAlarmA()

                while (isActive && isAlarmARunning) {
                    val freq = if (toggle) 1200.0 else 850.0
                    toggle = !toggle

                    for (i in 0 until chunkSamples) {
                        val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                        val sample = (sin(angle) * 28000).toInt().toShort()
                        buffer[i] = sample
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in Alarm A audio playback", e)
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (ignored: Exception) {}
            }
        }
    }

    /**
     * Stop Alarm A
     */
    @Synchronized
    fun stopAlarmA() {
        if (!isAlarmARunning) return
        Log.i(tag, "Stopping Alarm A")
        isAlarmARunning = false
        alarmAJob?.cancel()
        alarmAJob = null
        try {
            audioTrackA?.stop()
            audioTrackA?.release()
        } catch (ignored: Exception) {}
        audioTrackA = null
        stopVibration()
        checkStopVolumeEnforcement()
    }

    /**
     * Start Alarm B (Alarm Kecurangan)
     * Distinct harsh warning buzzer for exactly 5 seconds, auto-terminates.
     */
    @Synchronized
    fun startAlarmB(onFinished: (() -> Unit)? = null) {
        if (isAlarmBRunning) {
            Log.d(tag, "Alarm B is already active. Guarding against stacked sound.")
            return
        }
        isAlarmBRunning = true
        Log.w(tag, "Starting Alarm B (Violation alert) for 5 seconds at MAXIMUM VOLUME")
        enforceMaximumAlarmVolume()
        startContinuousVolumeEnforcement()

        alarmBJob = scope.launch {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
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
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrackB = track

            try {
                track.play()
                val chunkSamples = sampleRate / 10 // 100ms
                val buffer = ShortArray(chunkSamples)
                val startTime = System.currentTimeMillis()
                val durationMs = 5000L

                // Pulse vibration
                vibrateAlarmB()

                var tick = 0
                while (isActive && isAlarmBRunning && (System.currentTimeMillis() - startTime < durationMs)) {
                    val isBeep = (tick % 4 < 2) // 200ms beep, 200ms silence
                    tick++

                    val freq1 = 520.0
                    val freq2 = 1040.0 // Harsh harmonic
                    for (i in 0 until chunkSamples) {
                        if (isBeep) {
                            val angle1 = 2.0 * Math.PI * i / (sampleRate / freq1)
                            val angle2 = 2.0 * Math.PI * i / (sampleRate / freq2)
                            val sample = ((sin(angle1) * 20000) + (sin(angle2) * 10000)).toInt().coerceIn(-32767, 32767).toShort()
                            buffer[i] = sample
                        } else {
                            buffer[i] = 0
                        }
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error in Alarm B playback", e)
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (ignored: Exception) {}
                audioTrackB = null
                isAlarmBRunning = false
                stopVibration()
                checkStopVolumeEnforcement()
                Log.i(tag, "Alarm B auto-stopped after 5 seconds")
                onFinished?.invoke()
            }
        }
    }

    @Synchronized
    fun stopAlarmB() {
        if (!isAlarmBRunning) return
        isAlarmBRunning = false
        alarmBJob?.cancel()
        alarmBJob = null
        try {
            audioTrackB?.stop()
            audioTrackB?.release()
        } catch (ignored: Exception) {}
        audioTrackB = null
        stopVibration()
        checkStopVolumeEnforcement()
    }

    /**
     * Release all audio resources when activity destroyed
     */
    fun releaseAll() {
        stopAlarmA()
        stopAlarmB()
    }

    private fun vibrateAlarmA() {
        try {
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 250, 100, 250)
                    val amplitudes = intArrayOf(0, 200, 0, 255)
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 250, 100, 250), 0)
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun vibrateAlarmB() {
        try {
            vibrator?.let { v ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = longArrayOf(0, 200, 200, 200, 200)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0)
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(5000)
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (ignored: Exception) {}
    }
}

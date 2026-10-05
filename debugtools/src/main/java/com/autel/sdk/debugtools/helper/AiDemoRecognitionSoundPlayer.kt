package com.autel.sdk.debugtools.helper

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.autel.drone.sdk.log.SDKLog

/**
 * AI recognition demo alert sound, aligned with Athena [AiRecognitionPlayer] throttled playback semantics.
 */
class AiDemoRecognitionSoundPlayer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var toneGenerator: ToneGenerator? = null
    private var soundEnabled = true
    private var sessionActive = false
    private var targetPresent = false
    private var lastBeepMs = 0L

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
        if (!enabled) {
            stopWarn()
        }
    }

    fun setSessionActive(active: Boolean) {
        sessionActive = active
        if (!active) {
            stopWarn()
        }
    }

    fun onTargetPresenceChanged(hasTargets: Boolean) {
        targetPresent = hasTargets
        if (!hasTargets) {
            stopWarn()
            return
        }
        tryStartBeep()
    }

    fun release() {
        stopWarn()
    }

    private fun tryStartBeep() {
        if (!soundEnabled || !sessionActive || !targetPresent) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBeepMs < BEEP_INTERVAL_MS) {
            return
        }
        lastBeepMs = now
        playOnce()
        mainHandler.postDelayed({ tryStartBeep() }, BEEP_INTERVAL_MS)
    }

    private fun playOnce() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 80)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
        } catch (e: Exception) {
            SDKLog.e(TAG, "play recognition beep failed: ${e.message}")
        }
    }

    private fun stopWarn() {
        mainHandler.removeCallbacksAndMessages(null)
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            SDKLog.e(TAG, "release tone generator failed: ${e.message}")
        } finally {
            toneGenerator = null
        }
    }

    companion object {
        private const val TAG = "AiDemoRecognitionSound"
        private const val BEEP_INTERVAL_MS = 1000L
        private const val BEEP_DURATION_MS = 200
    }
}

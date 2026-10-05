package com.autel.sdk.debugtools.helper

import com.autel.drone.sdk.log.SDKLog
import com.autel.player.player.autelplayer.AutelPlayer
import com.autel.player.player.autelplayer.PlayerWeight

/**
 * Set player watch weights, aligned with the Athena app updateCodecPlayerWeight semantics:
 * - Full-screen single stream: FULL_SCREEN (1.0)
 * - Split screen side by side: HALF_SCREEN (0.5)
 * - Not watching: NOT_WATCHING (0.0)
 */
object PlayerWeightHelper {

    private const val TAG = "PlayerWeightHelper"

    fun AutelPlayer?.applyFullScreenWeight() {
        applyPlayerWeight(PlayerWeight.FULL_SCREEN)
    }

    fun AutelPlayer?.applySplitScreenWeight() {
        applyPlayerWeight(PlayerWeight.HALF_SCREEN)
    }

    fun AutelPlayer?.applyNotWatchingWeight() {
        applyPlayerWeight(PlayerWeight.NOT_WATCHING)
    }

    /** Multi-stream grid: 2 streams 0.5, 3+ streams 0.25 */
    fun AutelPlayer?.applyGridWeight(visiblePlayerCount: Int) {
        val weight = when {
            visiblePlayerCount <= 1 -> PlayerWeight.FULL_SCREEN
            visiblePlayerCount == 2 -> PlayerWeight.HALF_SCREEN
            else -> PlayerWeight.QUARTER_SCREEN
        }
        applyPlayerWeight(weight)
    }

    private fun AutelPlayer?.applyPlayerWeight(weight: PlayerWeight) {
        if (this == null) return
        try {
            setPlayerWeight(weight)
            SDKLog.i(TAG, "setPlayerWeight playerId=$autelPlayerID weight=$weight(${weight.weightValue})")
        } catch (e: Exception) {
            SDKLog.w(TAG, "setPlayerWeight failed playerId=$autelPlayerID: ${e.message}")
        }
    }
}

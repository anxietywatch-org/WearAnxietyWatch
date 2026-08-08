package com.anxietywatch.wear.intervention

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticBreathingEngine(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun alert() = vibrate(longArrayOf(0, 220, 120, 220), intArrayOf(0, 180, 0, 220))

    fun inhale() = vibrate(
        longArrayOf(0, 500, 500, 500, 500, 500, 500, 500, 500),
        intArrayOf(0, 35, 55, 75, 100, 130, 165, 200, 235),
    )

    fun exhale() = vibrate(
        longArrayOf(0, 500, 500, 500, 500, 500, 500, 500, 500),
        intArrayOf(0, 235, 200, 165, 130, 100, 75, 55, 35),
    )

    fun confirmation() = vibrate(longArrayOf(0, 120, 80, 180), intArrayOf(0, 100, 0, 180))

    fun cancel() = vibrator.cancel()

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}

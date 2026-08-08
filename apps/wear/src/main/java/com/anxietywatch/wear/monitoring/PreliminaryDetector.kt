package com.anxietywatch.wear.monitoring

import com.anxietywatch.wear.domain.BaselineSnapshot
import com.anxietywatch.wear.domain.DerivedFeatures
import com.anxietywatch.wear.domain.DetectionResult
import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.RulesConfig

class PreliminaryDetector(
    private val config: RulesConfig,
) {
    fun evaluate(features: DerivedFeatures, baseline: BaselineSnapshot): DetectionResult {
        if (baseline.sampleCount < config.calibrationSamples || features.sampleCount < 5) {
            return DetectionResult(
                score = 0.0,
                decision = MonitoringState.NORMAL,
                reasons = listOf("Calibración en curso"),
                rulesVersion = config.version,
            )
        }

        var score = 0.0
        val reasons = mutableListOf<String>()
        val delta = features.heartRateDeltaFromBaseline
        if (delta != null && delta >= config.heartRateDeltaBpmThreshold) {
            score += 0.45
            reasons += "Frecuencia cardíaca por encima de la línea base"
        }
        val slope = features.heartRateSlopeBpmPerMinute
        if (slope != null && slope >= config.heartRateRampBpmPerMinute) {
            score += 0.15
            reasons += "Aumento rápido de frecuencia cardíaca"
        }
        val rmssd = features.rmssdMillis
        if (rmssd != null && rmssd < config.hrvAbsoluteThresholdMillis) {
            score += 0.15
            reasons += "Variabilidad menor a la habitual"
        }
        val motion = features.movementVariance
        if (motion != null && motion <= config.maximumMovementEnergy) {
            score += 0.20
            reasons += "Movimiento bajo"
        } else if (motion != null) {
            score -= 0.35
            reasons += "El movimiento puede explicar el cambio"
        }
        if (features.validSampleRatio >= 0.6 && features.lastSampleAgeSeconds <= 10) {
            score += 0.05
        }
        val normalized = score.coerceIn(0.0, 1.0)
        val decision = when {
            normalized >= config.suspectedScore -> MonitoringState.USER_VALIDATION
            normalized >= config.observingScore -> MonitoringState.OBSERVING
            else -> MonitoringState.NORMAL
        }
        return DetectionResult(normalized, decision, reasons, config.version)
    }
}

package com.anxietywatch.wear.monitoring

import com.anxietywatch.wear.domain.BaselineSnapshot
import com.anxietywatch.wear.domain.DerivedFeatures
import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.RulesConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreliminaryDetectorTest {
    private val config = RulesConfig()
    private val baseline = BaselineSnapshot(100, 70.0, 900.0, 0)

    @Test
    fun `flags a multi-signal change when movement is low`() {
        val result = PreliminaryDetector(config).evaluate(
            features = features(delta = 40.0, slope = 18.0, rmssd = 22.0, movement = 0.02),
            baseline = baseline,
        )

        assertEquals(MonitoringState.USER_VALIDATION, result.decision)
        assertTrue(result.score >= config.suspectedScore)
        assertEquals("rules-v2", result.rulesVersion)
    }

    @Test
    fun `does not escalate the same heart-rate change during high movement`() {
        val result = PreliminaryDetector(config).evaluate(
            features = features(delta = 40.0, slope = 18.0, rmssd = 45.0, movement = 0.8),
            baseline = baseline,
        )

        assertEquals(MonitoringState.NORMAL, result.decision)
    }

    @Test
    fun `detects a real-sensor change without IBI when movement is low`() {
        val result = PreliminaryDetector(config).evaluate(
            features = features(delta = 18.0, slope = 12.0, rmssd = null, movement = 0.02),
            baseline = baseline,
        )

        assertEquals(MonitoringState.USER_VALIDATION, result.decision)
    }

    @Test
    fun `suppresses new escalation during physical activity`() {
        val result = PreliminaryDetector(config).evaluate(
            features = features(delta = 40.0, slope = 18.0, rmssd = 22.0, movement = 0.02),
            baseline = baseline,
            physicalActivity = true,
        )

        assertEquals(MonitoringState.NORMAL, result.decision)
        assertEquals(0.0, result.score, 0.0)
    }

    @Test
    fun `physical activity does not synthesize activity confirmed`() {
        val result = PreliminaryDetector(config).evaluate(
            features = features(delta = 40.0, slope = 18.0, rmssd = 22.0, movement = 0.02),
            baseline = baseline,
            physicalActivity = true,
        )

        assertTrue(result.reasons.none { it.contains("ACTIVITY_CONFIRMED") })
    }

    @Test
    fun `fresh qualifying evidence resumes after physical activity`() {
        val detector = PreliminaryDetector(config)
        val suppressed = detector.evaluate(features(40.0, 18.0, 22.0, 0.02), baseline, true)
        val resumed = detector.evaluate(features(40.0, 18.0, 22.0, 0.02), baseline, false)

        assertEquals(MonitoringState.NORMAL, suppressed.decision)
        assertEquals(MonitoringState.USER_VALIDATION, resumed.decision)
    }

    private fun features(
        delta: Double,
        slope: Double,
        rmssd: Double?,
        movement: Double,
    ) = DerivedFeatures(
        heartRateMean = baseline.meanHeartRate + delta,
        heartRateMax = baseline.meanHeartRate + delta + 5,
        heartRateSlopeBpmPerMinute = slope,
        heartRateDeltaFromBaseline = delta,
        rmssdMillis = rmssd,
        sdnnMillis = 28.0,
        movementMagnitudeMean = 1.0,
        movementVariance = movement,
        validSampleRatio = 0.9,
        lastSampleAgeSeconds = 1,
        sampleCount = 30,
    )
}

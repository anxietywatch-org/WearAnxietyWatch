package com.anxietywatch.wear.monitoring

import com.anxietywatch.wear.domain.BaselineSnapshot
import com.anxietywatch.wear.domain.DerivedFeatures
import com.anxietywatch.wear.domain.SensorReading
import kotlin.math.pow
import kotlin.math.sqrt

class FeatureExtractor {
    fun extract(
        readings: List<SensorReading>,
        baseline: BaselineSnapshot,
        nowEpochMillis: Long,
        expectedHeartRateSamples: Int = 30,
    ): DerivedFeatures {
        val heartRates = readings.filterIsInstance<SensorReading.HeartRate>().sortedBy { it.capturedAtEpochMillis }
        val motions = readings.filterIsInstance<SensorReading.Motion>()
        val bpmValues = heartRates.map { it.bpm }
        val ibiValues = heartRates.flatMap { it.ibiMillis.orEmpty() }.filter { it in 250.0..2_000.0 }
        val slope = if (heartRates.size >= 2) {
            val minutes = (heartRates.last().capturedAtEpochMillis - heartRates.first().capturedAtEpochMillis) / 60_000.0
            if (minutes > 0.0) (heartRates.last().bpm - heartRates.first().bpm) / minutes else null
        } else {
            null
        }
        val rmssd = if (ibiValues.size >= 3) {
            val differences = ibiValues.zipWithNext { first, second -> second - first }
            sqrt(differences.sumOf { it.pow(2) } / differences.size)
        } else {
            null
        }
        val sdnn = standardDeviation(ibiValues)
        val movementMean = motions.map { it.magnitudeG }.takeIf { it.isNotEmpty() }?.average()
        val movementVariance = motions.map { it.variance }.takeIf { it.isNotEmpty() }?.average()
        val latestTimestamp = readings.maxOfOrNull { it.capturedAtEpochMillis }
        return DerivedFeatures(
            heartRateMean = bpmValues.takeIf { it.isNotEmpty() }?.average(),
            heartRateMax = bpmValues.maxOrNull(),
            heartRateSlopeBpmPerMinute = slope,
            heartRateDeltaFromBaseline = if (baseline.sampleCount > 0 && bpmValues.isNotEmpty()) {
                bpmValues.average() - baseline.meanHeartRate
            } else {
                null
            },
            rmssdMillis = rmssd,
            sdnnMillis = sdnn,
            movementMagnitudeMean = movementMean,
            movementVariance = movementVariance,
            validSampleRatio = (heartRates.count { it.signalQuality >= 0.5 }.toDouble() / expectedHeartRateSamples)
                .coerceIn(0.0, 1.0),
            lastSampleAgeSeconds = latestTimestamp?.let { ((nowEpochMillis - it) / 1_000).coerceAtLeast(0) }
                ?: Long.MAX_VALUE,
            sampleCount = heartRates.size,
        )
    }

    private fun standardDeviation(values: List<Double>): Double? {
        if (values.size < 2) return null
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean).pow(2) } / (values.size - 1))
    }
}

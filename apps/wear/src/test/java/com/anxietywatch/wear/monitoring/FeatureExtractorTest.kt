package com.anxietywatch.wear.monitoring

import com.anxietywatch.wear.domain.BaselineSnapshot
import com.anxietywatch.wear.domain.SensorReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FeatureExtractorTest {
    @Test
    fun `extracts heart-rate delta slope and HRV without requiring EDA`() {
        val start = 1_000_000L
        val baseline = BaselineSnapshot(120, 70.0, 400.0, start)
        val readings = (0 until 10).flatMap { index ->
            val timestamp = start + index * 5_000L
            listOf(
                SensorReading.HeartRate(
                    bpm = 78.0 + index * 2,
                    ibiMillis = listOf(780.0, 790.0, 775.0, 785.0),
                    signalQuality = 0.95,
                    capturedAtEpochMillis = timestamp,
                    source = "test",
                ),
                SensorReading.Motion(
                    magnitudeG = 1.0,
                    variance = 0.03,
                    capturedAtEpochMillis = timestamp,
                    source = "test",
                ),
            )
        }

        val features = FeatureExtractor().extract(
            readings = readings,
            baseline = baseline,
            nowEpochMillis = start + 45_000L,
            expectedHeartRateSamples = 10,
        )

        assertEquals(17.0, features.heartRateDeltaFromBaseline ?: 0.0, 0.001)
        assertEquals(24.0, features.heartRateSlopeBpmPerMinute ?: 0.0, 0.001)
        assertNotNull(features.rmssdMillis)
        assertEquals(1.0, features.validSampleRatio, 0.001)
    }
}

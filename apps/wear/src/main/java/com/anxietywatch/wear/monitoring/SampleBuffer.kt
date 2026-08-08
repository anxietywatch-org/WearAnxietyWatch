package com.anxietywatch.wear.monitoring

import com.anxietywatch.wear.domain.SensorReading

class SampleBuffer(
    private val retentionMillis: Long = 30 * 60 * 1000L,
) {
    private val readings = ArrayDeque<SensorReading>()

    @Synchronized
    fun add(reading: SensorReading) {
        readings.addLast(reading)
        val threshold = reading.capturedAtEpochMillis - retentionMillis
        while (readings.firstOrNull()?.capturedAtEpochMillis?.let { it < threshold } == true) {
            readings.removeFirst()
        }
    }

    @Synchronized
    fun window(nowEpochMillis: Long, durationMillis: Long): List<SensorReading> {
        val threshold = nowEpochMillis - durationMillis
        return readings.filter { it.capturedAtEpochMillis >= threshold }
    }

    @Synchronized
    fun clear() = readings.clear()
}

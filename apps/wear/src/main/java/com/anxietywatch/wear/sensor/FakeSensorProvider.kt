package com.anxietywatch.wear.sensor

import android.os.Build
import com.anxietywatch.wear.domain.CapabilityStatus
import com.anxietywatch.wear.domain.DeviceCapabilities
import com.anxietywatch.wear.domain.SensorCapability
import com.anxietywatch.wear.domain.SensorKind
import com.anxietywatch.wear.domain.SensorReading
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeSensorProvider(
    private val anomaly: Boolean,
    private val random: Random = Random(7),
) : SensorProvider {
    override val providerName: String = if (anomaly) "Simulador: anomalía" else "Simulador: normal"

    override suspend fun capabilities(): DeviceCapabilities = DeviceCapabilities(
        deviceModel = "Simulated Galaxy Watch7",
        wearOsVersion = Build.VERSION.RELEASE,
        healthPlatform = "FakeSensorProvider",
        sensors = SensorKind.entries.associateWith { kind ->
            val status = if (kind == SensorKind.EDA) CapabilityStatus.UNSUPPORTED else CapabilityStatus.AVAILABLE
            SensorCapability(kind, status, providerName)
        },
    )

    override fun readings(): Flow<SensorReading> = flow {
        var tick = 0
        while (true) {
            val base = if (anomaly) 72.0 + (tick.coerceAtMost(45) * 1.0) else 72.0
            val bpm = base + sin(tick / 4.0) * 2.0 + random.nextDouble(-1.0, 1.0)
            val ibi = 60_000.0 / bpm
            emit(
                SensorReading.HeartRate(
                    bpm = bpm,
                    ibiMillis = listOf(ibi - 8, ibi + 4, ibi - 3, ibi + 7),
                    signalQuality = 0.96,
                    capturedAtEpochMillis = System.currentTimeMillis(),
                    source = providerName,
                ),
            )
            emit(
                SensorReading.Motion(
                    magnitudeG = 1.0,
                    variance = if (anomaly) 0.02 else 0.04,
                    capturedAtEpochMillis = System.currentTimeMillis(),
                    source = providerName,
                ),
            )
            tick += 1
            delay(1_000)
        }
    }
}

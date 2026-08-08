package com.anxietywatch.wear.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import com.anxietywatch.wear.domain.CapabilityStatus
import com.anxietywatch.wear.domain.DeviceCapabilities
import com.anxietywatch.wear.domain.SensorCapability
import com.anxietywatch.wear.domain.SensorKind
import com.anxietywatch.wear.domain.SensorReading
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidMotionSensorProvider(
    context: Context,
) : SensorProvider {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val providerName: String = "Android SensorManager"

    override suspend fun capabilities(): DeviceCapabilities {
        val entries = SensorKind.entries.associateWith { kind ->
            when (kind) {
                SensorKind.ACCELEROMETER -> SensorCapability(
                    kind,
                    if (accelerometer == null) CapabilityStatus.UNSUPPORTED else CapabilityStatus.AVAILABLE,
                    providerName,
                )
                SensorKind.EDA -> SensorCapability(kind, CapabilityStatus.UNSUPPORTED, "Galaxy Watch7")
                else -> SensorCapability(kind, CapabilityStatus.UNAVAILABLE, providerName)
            }
        }
        return DeviceCapabilities(
            deviceModel = Build.MANUFACTURER + " " + Build.MODEL,
            wearOsVersion = Build.VERSION.RELEASE,
            healthPlatform = "Android sensors",
            sensors = entries,
        )
    }

    override fun readings(): Flow<SensorReading> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null) {
            trySend(
                SensorReading.Availability(
                    SensorKind.ACCELEROMETER,
                    CapabilityStatus.UNSUPPORTED,
                    "Este dispositivo no expone acelerómetro.",
                    System.currentTimeMillis(),
                    providerName,
                ),
            )
            close()
            return@callbackFlow
        }

        val magnitudes = ArrayList<Double>(32)
        var windowStartedAt = System.currentTimeMillis()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3) return
                val gravity = SensorManager.GRAVITY_EARTH.toDouble()
                val x = event.values[0] / gravity
                val y = event.values[1] / gravity
                val z = event.values[2] / gravity
                magnitudes += sqrt(x * x + y * y + z * z)
                val now = System.currentTimeMillis()
                if (now - windowStartedAt >= 1_000 && magnitudes.isNotEmpty()) {
                    val mean = magnitudes.average()
                    val variance = magnitudes.sumOf { value -> (value - mean) * (value - mean) } / magnitudes.size
                    trySend(SensorReading.Motion(mean, variance, now, providerName))
                    magnitudes.clear()
                    windowStartedAt = now
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = sensorManager.registerListener(listener, sensor, 40_000)
        if (!registered) {
            trySend(
                SensorReading.Availability(
                    SensorKind.ACCELEROMETER,
                    CapabilityStatus.UNAVAILABLE,
                    "No fue posible registrar el acelerómetro.",
                    System.currentTimeMillis(),
                    providerName,
                ),
            )
            close()
        }
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}

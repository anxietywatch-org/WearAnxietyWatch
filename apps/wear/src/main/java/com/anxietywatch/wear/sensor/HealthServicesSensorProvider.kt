package com.anxietywatch.wear.sensor

import android.content.Context
import android.os.Build
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import com.anxietywatch.wear.domain.CapabilityStatus
import com.anxietywatch.wear.domain.DeviceCapabilities
import com.anxietywatch.wear.domain.SensorCapability
import com.anxietywatch.wear.domain.SensorKind
import com.anxietywatch.wear.domain.SensorReading
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class HealthServicesSensorProvider(
    context: Context,
) : SensorProvider {
    private val appContext = context.applicationContext
    private val measureClient = HealthServices.getClient(appContext).measureClient

    override val providerName: String = "Wear OS Health Services"

    override suspend fun capabilities(): DeviceCapabilities = withContext(Dispatchers.IO) {
        val heartRateStatus = runCatching {
            val capabilities = measureClient.getCapabilitiesAsync().get(10, TimeUnit.SECONDS)
            if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure) {
                CapabilityStatus.AVAILABLE
            } else {
                CapabilityStatus.UNSUPPORTED
            }
        }.getOrDefault(CapabilityStatus.UNAVAILABLE)

        val entries = SensorKind.entries.associateWith { kind ->
            when (kind) {
                SensorKind.HEART_RATE -> SensorCapability(kind, heartRateStatus, providerName)
                SensorKind.IBI -> SensorCapability(
                    kind,
                    CapabilityStatus.UNAVAILABLE,
                    providerName,
                    "Health Services no expone IBI; requiere el adaptador Samsung.",
                )
                SensorKind.EDA -> SensorCapability(
                    kind,
                    CapabilityStatus.UNSUPPORTED,
                    "Galaxy Watch7",
                    "EDA no forma parte del flujo de Watch7.",
                )
                else -> SensorCapability(kind, CapabilityStatus.UNAVAILABLE, providerName)
            }
        }
        DeviceCapabilities(
            deviceModel = Build.MANUFACTURER + " " + Build.MODEL,
            wearOsVersion = Build.VERSION.RELEASE,
            healthPlatform = "Health Services",
            sensors = entries,
        )
    }

    override fun readings(): Flow<SensorReading> = callbackFlow {
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability,
            ) {
                val status = when (availability) {
                    DataTypeAvailability.AVAILABLE -> CapabilityStatus.AVAILABLE
                    DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY -> CapabilityStatus.UNAVAILABLE
                    DataTypeAvailability.ACQUIRING -> CapabilityStatus.UNAVAILABLE
                    else -> CapabilityStatus.UNAVAILABLE
                }
                val reason = when (availability) {
                    DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY -> "El reloj no detecta contacto con la muñeca."
                    DataTypeAvailability.ACQUIRING -> "Adquiriendo señal."
                    DataTypeAvailability.AVAILABLE -> "Señal disponible."
                    else -> "Señal no disponible."
                }
                trySend(
                    SensorReading.Availability(
                        kind = SensorKind.HEART_RATE,
                        status = status,
                        reason = reason,
                        capturedAtEpochMillis = System.currentTimeMillis(),
                        source = providerName,
                    ),
                )
            }

            override fun onDataReceived(data: DataPointContainer) {
                data.getData(DataType.HEART_RATE_BPM).forEach { point ->
                    val bpm = point.value
                    if (bpm in 20.0..240.0) {
                        trySend(
                            SensorReading.HeartRate(
                                bpm = bpm,
                                ibiMillis = null,
                                signalQuality = 1.0,
                                capturedAtEpochMillis = System.currentTimeMillis(),
                                source = providerName,
                            ),
                        )
                    }
                }
            }
        }

        runCatching {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        }.onFailure { error ->
            trySend(
                SensorReading.Availability(
                    kind = SensorKind.HEART_RATE,
                    status = CapabilityStatus.UNAVAILABLE,
                    reason = error.message ?: "No fue posible iniciar la medición.",
                    capturedAtEpochMillis = System.currentTimeMillis(),
                    source = providerName,
                ),
            )
            close(error)
        }

        awaitClose {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        }
    }
}

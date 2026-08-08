package com.anxietywatch.wear.sensor

import android.content.Context
import android.os.Build
import com.anxietywatch.wear.domain.CapabilityStatus
import com.anxietywatch.wear.domain.DeviceCapabilities
import com.anxietywatch.wear.domain.SensorCapability
import com.anxietywatch.wear.domain.SensorKind
import com.anxietywatch.wear.domain.SensorReading
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Adaptador para Samsung Health Sensor SDK.
 *
 * Se usa reflexión porque Samsung distribuye su API como un AAR descargable y no
 * mediante un repositorio Maven. Cuando `samsung-health-sensor-api.aar` está en
 * `libs`, este proveedor conecta con Health Platform y obtiene frecuencia cardiaca
 * e intervalos IBI reales. Sin el AAR, el runtime conserva Health Services como
 * alternativa para la frecuencia cardiaca.
 */
class SamsungSensorProvider(context: Context) : SensorProvider {
    override val providerName: String = "Samsung Health Sensor SDK"

    private val appContext = context.applicationContext

    val isSdkBundled: Boolean = runCatching {
        Class.forName(SERVICE_CLASS)
        Class.forName(TRACKER_TYPE_CLASS)
        Class.forName(HEART_RATE_KEYS_CLASS)
    }.isSuccess

    override suspend fun capabilities(): DeviceCapabilities {
        val ibi = if (isSdkBundled) {
            SensorCapability(
                SensorKind.IBI,
                CapabilityStatus.AVAILABLE,
                providerName,
                "IBI real mediante HEART_RATE_CONTINUOUS",
            )
        } else {
            SensorCapability(
                SensorKind.IBI,
                CapabilityStatus.UNAVAILABLE,
                providerName,
                "Falta samsung-health-sensor-api.aar",
            )
        }
        val entries = SensorKind.entries.associateWith { kind ->
            when (kind) {
                SensorKind.HEART_RATE -> SensorCapability(
                    kind,
                    if (isSdkBundled) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE,
                    providerName,
                )
                SensorKind.IBI -> ibi
                SensorKind.EDA -> SensorCapability(
                    kind,
                    CapabilityStatus.UNSUPPORTED,
                    "Galaxy Watch7",
                    "El sensor EDA está disponible solo en Galaxy Watch8 o posterior",
                )
                else -> SensorCapability(kind, CapabilityStatus.UNAVAILABLE, providerName)
            }
        }
        return DeviceCapabilities(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            wearOsVersion = Build.VERSION.RELEASE,
            healthPlatform = if (isSdkBundled) "SDK incluido" else "SDK no incluido",
            sensors = entries,
        )
    }

    override fun readings(): Flow<SensorReading> = callbackFlow {
        if (!isSdkBundled) {
            close()
            return@callbackFlow
        }

        val serviceRef = AtomicReference<Any?>()
        val trackerRef = AtomicReference<Any?>()

        fun availability(kind: SensorKind, status: CapabilityStatus, reason: String) {
            trySend(
                SensorReading.Availability(
                    kind = kind,
                    status = status,
                    reason = reason,
                    capturedAtEpochMillis = System.currentTimeMillis(),
                    source = providerName,
                ),
            )
        }

        val connectionClass = Class.forName(CONNECTION_LISTENER_CLASS)
        val connectionListener = Proxy.newProxyInstance(
            connectionClass.classLoader,
            arrayOf(connectionClass),
        ) { proxy, method, args ->
            when (method.name) {
                "onConnectionSuccess" -> runCatching {
                    val service = requireNotNull(serviceRef.get())
                    val trackerTypeClass = Class.forName(TRACKER_TYPE_CLASS)
                    val heartRateType = trackerTypeClass.getField("HEART_RATE_CONTINUOUS").get(null)
                    val capability = service.javaClass.getMethod("getTrackingCapability").invoke(service)
                    val supported = capability.javaClass
                        .getMethod("getSupportHealthTrackerTypes")
                        .invoke(capability) as? Collection<*>
                    if (supported?.contains(heartRateType) != true) {
                        availability(
                            SensorKind.IBI,
                            CapabilityStatus.UNSUPPORTED,
                            "HEART_RATE_CONTINUOUS no está disponible en este reloj",
                        )
                        return@runCatching
                    }

                    val tracker = service.javaClass
                        .getMethod("getHealthTracker", trackerTypeClass)
                        .invoke(service, heartRateType)
                    trackerRef.set(tracker)
                    val eventClass = Class.forName(TRACKER_EVENT_LISTENER_CLASS)
                    val valueKeyClass = Class.forName(VALUE_KEY_CLASS)
                    val keysClass = Class.forName(HEART_RATE_KEYS_CLASS)
                    val heartRateKey = keysClass.getField("HEART_RATE").get(null)
                    val heartRateStatusKey = keysClass.getField("HEART_RATE_STATUS").get(null)
                    val ibiKey = keysClass.getField("IBI_LIST").get(null)
                    val ibiStatusKey = keysClass.getField("IBI_STATUS_LIST").get(null)

                    val eventListener = Proxy.newProxyInstance(
                        eventClass.classLoader,
                        arrayOf(eventClass),
                    ) { eventProxy, eventMethod, eventArgs ->
                        when (eventMethod.name) {
                            "onDataReceived" -> {
                                val points = eventArgs?.firstOrNull() as? List<*> ?: emptyList<Any>()
                                points.filterNotNull().forEach { point ->
                                    val getValue = point.javaClass.getMethod("getValue", valueKeyClass)
                                    val bpm = getValue.invoke(point, heartRateKey) as? Number
                                    val hrStatus = (getValue.invoke(point, heartRateStatusKey) as? Number)?.toInt()
                                    val ibiValues = (getValue.invoke(point, ibiKey) as? List<*>)
                                        .orEmpty()
                                        .mapNotNull { (it as? Number)?.toDouble() }
                                    val ibiStatuses = (getValue.invoke(point, ibiStatusKey) as? List<*>)
                                        .orEmpty()
                                        .mapNotNull { (it as? Number)?.toInt() }
                                    val validIbi = ibiValues.filterIndexed { index, value ->
                                        value in 250.0..2_000.0 && ibiStatuses.getOrNull(index) == 0
                                    }
                                    val timestamp = (point.javaClass
                                        .getMethod("getTimestamp")
                                        .invoke(point) as? Number)?.toLong()
                                        ?: System.currentTimeMillis()
                                    if (bpm != null && hrStatus == 1) {
                                        trySend(
                                            SensorReading.HeartRate(
                                                bpm = bpm.toDouble(),
                                                ibiMillis = validIbi.ifEmpty { null },
                                                signalQuality = 1.0,
                                                capturedAtEpochMillis = timestamp,
                                                source = providerName,
                                            ),
                                        )
                                    }
                                }
                                null
                            }
                            "onError" -> {
                                val errorName = eventArgs?.firstOrNull()?.toString().orEmpty()
                                val permissionError = errorName.contains("PERMISSION_ERROR")
                                availability(
                                    SensorKind.IBI,
                                    if (permissionError) {
                                        CapabilityStatus.PERMISSION_REQUIRED
                                    } else {
                                        CapabilityStatus.UNAVAILABLE
                                    },
                                    if (permissionError) {
                                        "Samsung Health Sensor requiere permiso de frecuencia cardiaca"
                                    } else {
                                        "Activa el modo desarrollador de Health Platform: $errorName"
                                    },
                                )
                                null
                            }
                            "toString" -> "AnxietyWatchSamsungTrackerListener"
                            "hashCode" -> System.identityHashCode(eventProxy)
                            "equals" -> eventProxy === eventArgs?.firstOrNull()
                            else -> null
                        }
                    }
                    tracker.javaClass.getMethod("setEventListener", eventClass)
                        .invoke(tracker, eventListener)
                    availability(SensorKind.IBI, CapabilityStatus.AVAILABLE, "IBI real activo")
                }.onFailure { error ->
                    availability(
                        SensorKind.IBI,
                        CapabilityStatus.UNAVAILABLE,
                        "No fue posible iniciar IBI: ${error.cause?.message ?: error.message}",
                    )
                }
                "onConnectionFailed" -> availability(
                    SensorKind.IBI,
                    CapabilityStatus.UNAVAILABLE,
                    "Health Platform rechazó la conexión; actualízalo y activa el modo desarrollador",
                )
                "onConnectionEnded" -> availability(
                    SensorKind.IBI,
                    CapabilityStatus.UNAVAILABLE,
                    "Se cerró la conexión con Health Platform",
                )
                "toString" -> "AnxietyWatchSamsungConnectionListener"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }

        runCatching {
            val serviceClass = Class.forName(SERVICE_CLASS)
            val service = serviceClass
                .getConstructor(connectionClass, Context::class.java)
                .newInstance(connectionListener, appContext)
            serviceRef.set(service)
            serviceClass.getMethod("connectService").invoke(service)
        }.onFailure { error ->
            availability(
                SensorKind.IBI,
                CapabilityStatus.UNAVAILABLE,
                "No fue posible conectar Samsung Health Sensor: ${error.cause?.message ?: error.message}",
            )
        }

        awaitClose {
            runCatching { trackerRef.get()?.javaClass?.getMethod("unsetEventListener")?.invoke(trackerRef.get()) }
            runCatching { serviceRef.get()?.javaClass?.getMethod("disconnectService")?.invoke(serviceRef.get()) }
        }
    }

    private companion object {
        const val SERVICE_CLASS = "com.samsung.android.service.health.tracking.HealthTrackingService"
        const val CONNECTION_LISTENER_CLASS = "com.samsung.android.service.health.tracking.ConnectionListener"
        const val TRACKER_EVENT_LISTENER_CLASS =
            "com.samsung.android.service.health.tracking.HealthTracker\$TrackerEventListener"
        const val TRACKER_TYPE_CLASS = "com.samsung.android.service.health.tracking.data.HealthTrackerType"
        const val VALUE_KEY_CLASS = "com.samsung.android.service.health.tracking.data.ValueKey"
        const val HEART_RATE_KEYS_CLASS =
            "com.samsung.android.service.health.tracking.data.ValueKey\$HeartRateSet"
    }
}

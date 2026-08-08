package com.anxietywatch.wear.sensor

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.UserActivityInfo
import androidx.health.services.client.data.UserActivityState
import com.anxietywatch.wear.AnxietyWatchApplication
import com.anxietywatch.wear.domain.CapabilityStatus
import com.anxietywatch.wear.domain.SensorKind
import com.anxietywatch.wear.domain.SensorReading

class PassiveHealthDataService : PassiveListenerService() {
    private val runtime
        get() = (application as AnxietyWatchApplication).runtime

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val now = System.currentTimeMillis()
        dataPoints.getData(DataType.HEART_RATE_BPM).forEach { point ->
            val bpm = point.value
            if (bpm in 20.0..240.0) {
                runtime.acceptPassiveReading(
                    SensorReading.HeartRate(
                        bpm = bpm,
                        ibiMillis = null,
                        signalQuality = 1.0,
                        capturedAtEpochMillis = now,
                        source = "Health Services pasivo",
                    ),
                )
            }
        }
        dataPoints.getData(DataType.STEPS_DAILY).forEach { point ->
            runtime.acceptPassiveReading(
                SensorReading.Steps(
                    dailyTotal = point.value,
                    capturedAtEpochMillis = now,
                    source = "Health Services pasivo",
                ),
            )
        }
    }

    override fun onUserActivityInfoReceived(info: UserActivityInfo) {
        runtime.setPhysicalActivity(
            info.userActivityState == UserActivityState.USER_ACTIVITY_EXERCISE,
        )
    }

    override fun onPermissionLost() {
        runtime.acceptPassiveReading(
            SensorReading.Availability(
                kind = SensorKind.HEART_RATE,
                status = CapabilityStatus.PERMISSION_REQUIRED,
                reason = "Se retiró el permiso de frecuencia cardíaca.",
                capturedAtEpochMillis = System.currentTimeMillis(),
                source = "Health Services pasivo",
            ),
        )
    }
}

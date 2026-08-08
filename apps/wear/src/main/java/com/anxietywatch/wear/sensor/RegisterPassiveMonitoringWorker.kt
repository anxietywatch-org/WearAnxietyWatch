package com.anxietywatch.wear.sensor

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RegisterPassiveMonitoringWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!PermissionPolicy.hasBackgroundHeartRate(applicationContext)) {
            return@withContext Result.success()
        }
        runCatching {
            val client = HealthServices.getClient(applicationContext).passiveMonitoringClient
            val capabilities = client.getCapabilitiesAsync().get(15, TimeUnit.SECONDS)
            val requested = buildSet {
                if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesPassiveMonitoring) {
                    add(DataType.HEART_RATE_BPM)
                }
                if (
                    PermissionPolicy.hasActivityRecognition(applicationContext) &&
                    DataType.STEPS_DAILY in capabilities.supportedDataTypesPassiveMonitoring
                ) {
                    add(DataType.STEPS_DAILY)
                }
            }
            if (requested.isEmpty()) return@runCatching
            val config = PassiveListenerConfig.builder()
                .setDataTypes(requested)
                .setShouldUserActivityInfoBeRequested(
                    PermissionPolicy.hasActivityRecognition(applicationContext),
                )
                .build()
            client.setPassiveListenerServiceAsync(
                PassiveHealthDataService::class.java,
                config,
            ).get(20, TimeUnit.SECONDS)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (runAttemptCount < 3) Result.retry() else Result.failure(
                    androidx.work.workDataOf("reason" to (error.message ?: "registration_failed")),
                )
            },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "register-passive-health-monitoring"
    }
}

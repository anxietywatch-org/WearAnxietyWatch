package com.anxietywatch.wear.domain

import java.util.UUID

enum class SensorKind {
    HEART_RATE,
    IBI,
    ACCELEROMETER,
    STEPS,
    SKIN_TEMPERATURE,
    PPG,
    EDA,
}

enum class CapabilityStatus {
    AVAILABLE,
    UNAVAILABLE,
    UNSUPPORTED,
    PERMISSION_REQUIRED,
}

data class SensorCapability(
    val kind: SensorKind,
    val status: CapabilityStatus,
    val source: String,
    val detail: String? = null,
)

data class DeviceCapabilities(
    val deviceModel: String,
    val wearOsVersion: String,
    val healthPlatform: String,
    val sensors: Map<SensorKind, SensorCapability>,
) {
    fun status(kind: SensorKind): CapabilityStatus =
        sensors[kind]?.status ?: CapabilityStatus.UNSUPPORTED
}

sealed interface SensorReading {
    val capturedAtEpochMillis: Long
    val source: String

    data class HeartRate(
        val bpm: Double,
        val ibiMillis: List<Double>?,
        val signalQuality: Double,
        override val capturedAtEpochMillis: Long,
        override val source: String,
    ) : SensorReading

    data class Motion(
        val magnitudeG: Double,
        val variance: Double,
        override val capturedAtEpochMillis: Long,
        override val source: String,
    ) : SensorReading

    data class Steps(
        val dailyTotal: Long,
        override val capturedAtEpochMillis: Long,
        override val source: String,
    ) : SensorReading

    data class SkinTemperature(
        val celsius: Double,
        override val capturedAtEpochMillis: Long,
        override val source: String,
    ) : SensorReading

    data class Availability(
        val kind: SensorKind,
        val status: CapabilityStatus,
        val reason: String,
        override val capturedAtEpochMillis: Long,
        override val source: String,
    ) : SensorReading
}

data class DerivedFeatures(
    val heartRateMean: Double?,
    val heartRateMax: Double?,
    val heartRateSlopeBpmPerMinute: Double?,
    val heartRateDeltaFromBaseline: Double?,
    val rmssdMillis: Double?,
    val sdnnMillis: Double?,
    val movementMagnitudeMean: Double?,
    val movementVariance: Double?,
    val validSampleRatio: Double,
    val lastSampleAgeSeconds: Long,
    val sampleCount: Int,
)

data class BaselineSnapshot(
    val sampleCount: Long,
    val meanHeartRate: Double,
    val heartRateM2: Double,
    val updatedAtEpochMillis: Long,
) {
    val standardDeviation: Double
        get() = if (sampleCount > 1) kotlin.math.sqrt(heartRateM2 / (sampleCount - 1)) else 0.0

    fun add(value: Double, capturedAt: Long): BaselineSnapshot {
        val nextCount = sampleCount + 1
        val delta = value - meanHeartRate
        val nextMean = meanHeartRate + delta / nextCount
        val nextM2 = heartRateM2 + delta * (value - nextMean)
        return copy(
            sampleCount = nextCount,
            meanHeartRate = nextMean,
            heartRateM2 = nextM2,
            updatedAtEpochMillis = capturedAt,
        )
    }

    companion object {
        fun empty() = BaselineSnapshot(0, 0.0, 0.0, 0)
    }
}

enum class MonitoringState {
    NORMAL,
    OBSERVING,
    USER_VALIDATION,
    SECOND_VALIDATION,
    INTERVENTION,
    SOS_PENDING,
    SOS_ACTIVE,
    RESOLVED,
    COOLDOWN,
}

enum class UserResponse {
    ACTIVITY_CONFIRMED,
    USER_OK,
    SUPPORT_REQUESTED,
    NO_RESPONSE,
    BREATHING_HELPED,
    SOS_REQUESTED,
    SOS_CANCELLED,
}

data class DetectionResult(
    val score: Double,
    val decision: MonitoringState,
    val reasons: List<String>,
    val rulesVersion: String,
)

data class RulesConfig(
    val version: String = "rules-v2",
    val calibrationSamples: Long = 20,
    val heartRateDeltaBpmThreshold: Double = 15.0,
    val heartRateRampBpmPerMinute: Double = 8.0,
    val hrvAbsoluteThresholdMillis: Double = 30.0,
    val maximumMovementEnergy: Double = 0.18,
    val observingScore: Double = 0.40,
    val suspectedScore: Double = 0.65,
    val cooldownMillis: Long = 5 * 60 * 1000L,
)

data class PendingEvent(
    val id: String = UUID.randomUUID().toString(),
    val startedAtEpochMillis: Long,
    val state: MonitoringState,
    val triggerScore: Double,
    val rulesVersion: String,
    val userResponse: UserResponse? = null,
    val sosStatus: String? = null,
    val endedAtEpochMillis: Long? = null,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0L,
)

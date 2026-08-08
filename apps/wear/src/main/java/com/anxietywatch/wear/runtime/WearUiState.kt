package com.anxietywatch.wear.runtime

import com.anxietywatch.wear.domain.CapabilityStatus
import com.anxietywatch.wear.domain.MonitoringState

enum class WearScreen {
    HOME,
    PERMISSIONS,
    MONITORING,
    VALIDATION,
    BREATHING,
    GROUNDING,
    SOS_CONFIRM,
    SOS_COUNTDOWN,
    SOS_ACTIVE,
    SETTINGS,
    FINISHED,
}

data class WearUiState(
    val screen: WearScreen = WearScreen.HOME,
    val monitoringState: MonitoringState = MonitoringState.NORMAL,
    val heartRateBpm: Int? = null,
    val baselineBpm: Int? = null,
    val calibrationProgress: Float = 0f,
    val heartRateStatus: CapabilityStatus = CapabilityStatus.PERMISSION_REQUIRED,
    val accelerometerStatus: CapabilityStatus = CapabilityStatus.UNAVAILABLE,
    val ibiStatus: CapabilityStatus = CapabilityStatus.UNAVAILABLE,
    val ibiDetail: String = "Requiere Samsung Health Sensor SDK",
    val edaDetail: String = "Disponible solo en Galaxy Watch8 o posterior",
    val phoneConnected: Boolean = false,
    val batteryPercent: Int = 0,
    val pendingSamples: Int = 0,
    val simulatedData: Boolean = false,
    val anomalySimulation: Boolean = false,
    val physicalActivity: Boolean = false,
    val detectionScore: Double = 0.0,
    val detectionReasons: List<String> = emptyList(),
    val message: String = "Preparando monitoreo",
    val sosMessage: String = "",
)

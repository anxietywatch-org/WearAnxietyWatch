package com.anxietywatch.wear.runtime

import android.content.Context
import android.os.BatteryManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.anxietywatch.wear.datalayer.PhoneConnectionObserver
import com.anxietywatch.wear.datalayer.WatchDataClient
import com.anxietywatch.wear.domain.BaselineSnapshot
import com.anxietywatch.wear.domain.CapabilityStatus
import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.domain.RulesConfig
import com.anxietywatch.wear.domain.SensorKind
import com.anxietywatch.wear.domain.SensorReading
import com.anxietywatch.wear.domain.UserResponse
import com.anxietywatch.wear.intervention.AlertNotifier
import com.anxietywatch.wear.intervention.HapticBreathingEngine
import com.anxietywatch.wear.monitoring.FeatureExtractor
import com.anxietywatch.wear.monitoring.MonitoringStateMachine
import com.anxietywatch.wear.monitoring.PreliminaryDetector
import com.anxietywatch.wear.monitoring.SampleBuffer
import com.anxietywatch.wear.sensor.AndroidMotionSensorProvider
import com.anxietywatch.wear.sensor.FakeSensorProvider
import com.anxietywatch.wear.sensor.HealthServicesSensorProvider
import com.anxietywatch.wear.sensor.PermissionPolicy
import com.anxietywatch.wear.sensor.RegisterPassiveMonitoringWorker
import com.anxietywatch.wear.sensor.SamsungSensorProvider
import com.anxietywatch.wear.sensor.SensorProvider
import com.anxietywatch.wear.storage.WearDatabase
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WearRuntime(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val database = WearDatabase(appContext)
    private val sampleBuffer = SampleBuffer()
    private val featureExtractor = FeatureExtractor()
    private val rulesConfig = RulesConfig()
    private val detector = PreliminaryDetector(rulesConfig)
    private val stateMachine = MonitoringStateMachine()
    private val healthServicesProvider = HealthServicesSensorProvider(appContext)
    private val motionProvider = AndroidMotionSensorProvider(appContext)
    private val samsungProvider = SamsungSensorProvider(appContext)
    private val connectionObserver = PhoneConnectionObserver(appContext)
    private val dataClient = WatchDataClient(appContext, database, connectionObserver)
    private val notifier = AlertNotifier(appContext)
    val haptics = HapticBreathingEngine(appContext)

    private val mutableState = MutableStateFlow(WearUiState())
    val state: StateFlow<WearUiState> = mutableState.asStateFlow()

    private var baseline: BaselineSnapshot = database.loadBaseline()
    private var monitoringJob: Job? = null
    private var statusJob: Job? = null
    private var activeEvent: PendingEvent? = null
    private val processing = AtomicBoolean(false)

    init {
        refreshStaticState()
        if (PermissionPolicy.hasForegroundHeartRate(appContext)) {
            enqueuePassiveRegistration()
        }
    }

    fun startForegroundMonitoring() {
        if (!PermissionPolicy.hasForegroundHeartRate(appContext)) {
            mutableState.value = mutableState.value.copy(
                screen = WearScreen.PERMISSIONS,
                heartRateStatus = CapabilityStatus.PERMISSION_REQUIRED,
                message = "Autoriza los sensores para iniciar.",
            )
            return
        }
        if (monitoringJob?.isActive == true) return
        monitoringJob = scope.launch {
            val simulated = mutableState.value.simulatedData
            val heartProvider: SensorProvider = if (simulated) {
                FakeSensorProvider(mutableState.value.anomalySimulation)
            } else if (samsungProvider.isSdkBundled) {
                samsungProvider
            } else {
                healthServicesProvider
            }
            updateCapabilities(heartProvider)
            mutableState.value = mutableState.value.copy(
                screen = WearScreen.MONITORING,
                message = if (simulated) "Datos simulados activos" else "Monitoreo activo",
            )
            merge(heartProvider.readings(), if (simulated) kotlinx.coroutines.flow.emptyFlow() else motionProvider.readings())
                .catch { error ->
                    mutableState.value = mutableState.value.copy(
                        message = error.message ?: "Una fuente de sensores no está disponible.",
                    )
                }
                .collect { reading -> processReading(reading) }
        }
        startStatusLoop()
    }

    fun stopForegroundMonitoring() {
        scope.launch {
            monitoringJob?.cancelAndJoin()
            monitoringJob = null
            haptics.cancel()
        }
    }

    fun onPermissionsResult() {
        if (PermissionPolicy.hasForegroundHeartRate(appContext)) {
            enqueuePassiveRegistration()
            mutableState.value = mutableState.value.copy(
                heartRateStatus = CapabilityStatus.AVAILABLE,
                message = if (PermissionPolicy.hasBackgroundHeartRate(appContext)) {
                    "Monitoreo pasivo autorizado"
                } else {
                    "Monitoreo disponible mientras usas la app"
                },
            )
            startForegroundMonitoring()
        } else {
            mutableState.value = mutableState.value.copy(
                screen = WearScreen.PERMISSIONS,
                heartRateStatus = CapabilityStatus.PERMISSION_REQUIRED,
                message = "Sin permiso, el reloj no leerá frecuencia cardíaca.",
            )
        }
    }

    fun acceptPassiveReading(reading: SensorReading) {
        scope.launch { processReading(reading) }
    }

    fun setPhysicalActivity(active: Boolean) {
        mutableState.value = mutableState.value.copy(physicalActivity = active)
    }

    fun navigate(screen: WearScreen) {
        mutableState.value = mutableState.value.copy(screen = screen)
    }

    fun useSimulatedData(enabled: Boolean) {
        if (enabled != mutableState.value.simulatedData) {
            resetBaselineForProviderChange()
        }
        database.saveSetting(SETTING_FAKE, enabled.toString())
        mutableState.value = mutableState.value.copy(
            simulatedData = enabled,
            anomalySimulation = false,
            message = if (enabled) "Simulador listo" else "Sensores reales seleccionados",
        )
        restartMonitoring()
    }

    fun simulateAnomaly() {
        if (baseline.sampleCount < rulesConfig.calibrationSamples) {
            mutableState.value = mutableState.value.copy(
                simulatedData = true,
                anomalySimulation = false,
                message = "Calibra primero con datos normales (${baseline.sampleCount}/${rulesConfig.calibrationSamples}).",
                screen = WearScreen.MONITORING,
            )
        } else {
            mutableState.value = mutableState.value.copy(
                simulatedData = true,
                anomalySimulation = true,
                message = "Simulando cambios fisiológicos",
                screen = WearScreen.MONITORING,
            )
        }
        database.saveSetting(SETTING_FAKE, "true")
        restartMonitoring()
    }

    fun respond(response: UserResponse) {
        notifier.clearPossibleEvent()
        val next = stateMachine.onUserResponse(response)
        updateActiveEvent(next, response)
        val screen = when (next) {
            MonitoringState.INTERVENTION -> WearScreen.BREATHING
            MonitoringState.SECOND_VALIDATION -> WearScreen.VALIDATION
            MonitoringState.COOLDOWN -> WearScreen.FINISHED
            MonitoringState.RESOLVED -> WearScreen.FINISHED
            MonitoringState.SOS_PENDING -> WearScreen.SOS_COUNTDOWN
            else -> mutableState.value.screen
        }
        mutableState.value = mutableState.value.copy(
            monitoringState = next,
            screen = screen,
            message = when (response) {
                UserResponse.ACTIVITY_CONFIRMED -> "Actividad registrada; no se interpreta como evento."
                UserResponse.USER_OK -> "Respuesta guardada."
                UserResponse.SUPPORT_REQUESTED -> "Iniciemos una técnica de apoyo."
                UserResponse.NO_RESPONSE -> "Segunda comprobación."
                UserResponse.BREATHING_HELPED -> "Sesión finalizada."
                UserResponse.SOS_REQUESTED -> "Confirma o cancela el SOS."
                UserResponse.SOS_CANCELLED -> "SOS cancelado."
            },
        )
        if (next == MonitoringState.COOLDOWN || next == MonitoringState.RESOLVED) scheduleCooldown()
    }

    fun startManualSos() {
        val now = System.currentTimeMillis()
        activeEvent = PendingEvent(
            startedAtEpochMillis = now,
            state = MonitoringState.SOS_PENDING,
            triggerScore = mutableState.value.detectionScore,
            rulesVersion = rulesConfig.version,
            sosStatus = "pending_confirmation",
        ).also(database::upsertEvent)
        stateMachine.onUserResponse(UserResponse.SOS_REQUESTED)
        mutableState.value = mutableState.value.copy(
            screen = WearScreen.SOS_COUNTDOWN,
            monitoringState = MonitoringState.SOS_PENDING,
            sosMessage = "SOS pendiente de confirmación",
        )
        haptics.alert()
    }

    fun confirmSos() {
        val next = stateMachine.confirmSos()
        val event = activeEvent ?: PendingEvent(
            startedAtEpochMillis = System.currentTimeMillis(),
            state = next,
            triggerScore = mutableState.value.detectionScore,
            rulesVersion = rulesConfig.version,
        )
        activeEvent = event.copy(state = next, sosStatus = "queued_on_watch").also(database::upsertEvent)
        mutableState.value = mutableState.value.copy(
            screen = WearScreen.SOS_ACTIVE,
            monitoringState = next,
            sosMessage = "Solicitud guardada en el reloj",
        )
        haptics.confirmation()
        scope.launch {
            val sent = dataClient.sendImmediateEvent(activeEvent ?: return@launch)
            val status = if (sent) "Enviado al teléfono" else "Pendiente: teléfono no conectado"
            activeEvent = activeEvent?.copy(sosStatus = if (sent) "sent_to_phone" else "queued_on_watch")
            activeEvent?.let(database::upsertEvent)
            mutableState.value = mutableState.value.copy(sosMessage = status)
        }
    }

    fun cancelSos() {
        activeEvent = activeEvent?.copy(
            state = MonitoringState.RESOLVED,
            userResponse = UserResponse.SOS_CANCELLED,
            sosStatus = "cancelled",
            endedAtEpochMillis = System.currentTimeMillis(),
        ).also { it?.let(database::upsertEvent) }
        stateMachine.onUserResponse(UserResponse.SOS_CANCELLED)
        mutableState.value = mutableState.value.copy(
            screen = WearScreen.FINISHED,
            monitoringState = MonitoringState.RESOLVED,
            sosMessage = "SOS cancelado",
            message = "La cancelación quedó registrada.",
        )
        scheduleCooldown()
    }

    fun finishEvent() {
        activeEvent = activeEvent?.copy(
            state = MonitoringState.RESOLVED,
            endedAtEpochMillis = System.currentTimeMillis(),
        ).also { it?.let(database::upsertEvent) }
        stateMachine.finishResolution()
        mutableState.value = mutableState.value.copy(
            screen = WearScreen.MONITORING,
            monitoringState = MonitoringState.COOLDOWN,
            anomalySimulation = false,
            message = "Monitoreo en periodo de descanso",
        )
        scheduleCooldown()
    }

    private suspend fun processReading(reading: SensorReading) {
        if (!processing.compareAndSet(false, true)) return
        try {
            withContext(Dispatchers.IO) { database.insertReading(reading) }
            sampleBuffer.add(reading)
            when (reading) {
                is SensorReading.HeartRate -> processHeartRate(reading)
                is SensorReading.Availability -> {
                    if (reading.kind == SensorKind.HEART_RATE) {
                        mutableState.value = mutableState.value.copy(
                            heartRateStatus = reading.status,
                            message = reading.reason,
                        )
                    } else if (reading.kind == SensorKind.IBI) {
                        mutableState.value = mutableState.value.copy(
                            ibiStatus = reading.status,
                            ibiDetail = reading.reason,
                        )
                    }
                }
                else -> Unit
            }
            mutableState.value = mutableState.value.copy(pendingSamples = database.pendingCount())
        } finally {
            processing.set(false)
        }
    }

    private suspend fun processHeartRate(reading: SensorReading.HeartRate) {
        val currentState = stateMachine.state
        if (
            currentState == MonitoringState.NORMAL &&
            baseline.sampleCount < rulesConfig.calibrationSamples &&
            !mutableState.value.physicalActivity &&
            reading.signalQuality >= 0.5 &&
            !mutableState.value.anomalySimulation
        ) {
            baseline = baseline.add(reading.bpm, reading.capturedAtEpochMillis)
            if (baseline.sampleCount % 10L == 0L) {
                withContext(Dispatchers.IO) { database.saveBaseline(baseline) }
            }
        }
        val features = featureExtractor.extract(
            readings = sampleBuffer.window(reading.capturedAtEpochMillis, 60_000),
            baseline = baseline,
            nowEpochMillis = reading.capturedAtEpochMillis,
        )
        val detection = detector.evaluate(features, baseline)
        val nextState = stateMachine.onDetection(detection.decision)
        mutableState.value = mutableState.value.copy(
            heartRateBpm = reading.bpm.toInt(),
            baselineBpm = baseline.meanHeartRate.takeIf { baseline.sampleCount > 0 }?.toInt(),
            calibrationProgress = (baseline.sampleCount.toFloat() / rulesConfig.calibrationSamples).coerceIn(0f, 1f),
            heartRateStatus = CapabilityStatus.AVAILABLE,
            monitoringState = nextState,
            detectionScore = detection.score,
            detectionReasons = detection.reasons,
            message = if (baseline.sampleCount < rulesConfig.calibrationSamples) {
                "Calibrando ${baseline.sampleCount}/${rulesConfig.calibrationSamples}"
            } else if (nextState == MonitoringState.OBSERVING) {
                "Observando cambios"
            } else {
                mutableState.value.message
            },
        )
        if (nextState == MonitoringState.USER_VALIDATION && activeEvent == null) {
            activeEvent = PendingEvent(
                startedAtEpochMillis = reading.capturedAtEpochMillis,
                state = nextState,
                triggerScore = detection.score,
                rulesVersion = detection.rulesVersion,
            ).also(database::upsertEvent)
            mutableState.value = mutableState.value.copy(
                screen = WearScreen.VALIDATION,
                message = "Detectamos cambios fisiológicos inusuales.",
            )
            haptics.alert()
            notifier.showPossibleEvent()
        }
    }

    private fun updateActiveEvent(state: MonitoringState, response: UserResponse) {
        activeEvent = activeEvent?.copy(
            state = state,
            userResponse = response,
            endedAtEpochMillis = if (state == MonitoringState.COOLDOWN || state == MonitoringState.RESOLVED) {
                System.currentTimeMillis()
            } else {
                null
            },
        ).also { it?.let(database::upsertEvent) }
    }

    private fun restartMonitoring() {
        scope.launch {
            monitoringJob?.cancelAndJoin()
            monitoringJob = null
            startForegroundMonitoring()
        }
    }

    private fun resetBaselineForProviderChange() {
        baseline = BaselineSnapshot.empty()
        sampleBuffer.clear()
        stateMachine.reset()
        activeEvent = null
        database.clearBaseline()
        mutableState.value = mutableState.value.copy(
            monitoringState = MonitoringState.NORMAL,
            heartRateBpm = null,
            baselineBpm = null,
            calibrationProgress = 0f,
            detectionScore = 0.0,
            detectionReasons = emptyList(),
            message = "Iniciando una nueva calibración",
        )
    }

    private fun scheduleCooldown() {
        scope.launch {
            delay(rulesConfig.cooldownMillis)
            stateMachine.cooldownExpired()
            activeEvent = null
            mutableState.value = mutableState.value.copy(
                monitoringState = MonitoringState.NORMAL,
                anomalySimulation = false,
                message = "Estado normal",
            )
        }
    }

    private fun enqueuePassiveRegistration() {
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            RegisterPassiveMonitoringWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RegisterPassiveMonitoringWorker>().build(),
        )
    }

    private fun startStatusLoop() {
        if (statusJob?.isActive == true) return
        statusJob = scope.launch {
            while (true) {
                val connected = connectionObserver.isConnected()
                val batteryManager = appContext.getSystemService(BatteryManager::class.java)
                val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    .coerceIn(0, 100)
                if (connected) dataClient.syncPendingBatch()
                mutableState.value = mutableState.value.copy(
                    phoneConnected = connected,
                    batteryPercent = battery,
                    pendingSamples = database.pendingCount(),
                )
                delay(30_000)
            }
        }
    }

    private fun refreshStaticState() {
        val simulated = database.setting(SETTING_FAKE)?.toBooleanStrictOrNull() ?: false
        val batteryManager = appContext.getSystemService(BatteryManager::class.java)
        mutableState.value = mutableState.value.copy(
            simulatedData = simulated,
            baselineBpm = baseline.meanHeartRate.takeIf { baseline.sampleCount > 0 }?.toInt(),
            calibrationProgress = (baseline.sampleCount.toFloat() / rulesConfig.calibrationSamples).coerceIn(0f, 1f),
            batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .coerceIn(0, 100),
            pendingSamples = database.pendingCount(),
        )
    }

    private suspend fun updateCapabilities(provider: SensorProvider) {
        val primary = provider.capabilities()
        val motion = motionProvider.capabilities()
        val samsung = samsungProvider.capabilities()
        val ibiCapability = samsung.sensors[SensorKind.IBI]
        val edaCapability = samsung.sensors[SensorKind.EDA]
        mutableState.value = mutableState.value.copy(
            heartRateStatus = primary.status(SensorKind.HEART_RATE),
            accelerometerStatus = if (mutableState.value.simulatedData) {
                CapabilityStatus.AVAILABLE
            } else {
                motion.status(SensorKind.ACCELEROMETER)
            },
            ibiStatus = if (mutableState.value.simulatedData) {
                CapabilityStatus.AVAILABLE
            } else {
                samsung.status(SensorKind.IBI)
            },
            ibiDetail = if (mutableState.value.simulatedData) {
                "IBI generado únicamente para pruebas"
            } else {
                ibiCapability?.detail.orEmpty()
            },
            edaDetail = edaCapability?.detail
                ?: "Disponible solo en Galaxy Watch8 o posterior",
        )
    }

    companion object {
        private const val SETTING_FAKE = "use_fake_sensor"
    }
}

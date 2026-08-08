@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.anxietywatch.wear.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.anxietywatch.wear.domain.CapabilityStatus
import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.UserResponse
import com.anxietywatch.wear.runtime.WearRuntime
import com.anxietywatch.wear.runtime.WearScreen
import com.anxietywatch.wear.runtime.WearUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AnxietyWatchApp(
    runtime: WearRuntime,
    requestPermissions: () -> Unit,
) {
    val state by runtime.state.collectAsStateCompat()
    AppScaffold {
        key(state.screen) {
            when (state.screen) {
                WearScreen.HOME,
                WearScreen.MONITORING,
                -> MonitoringScreen(state, runtime)
                WearScreen.PERMISSIONS -> PermissionsScreen(state, requestPermissions, runtime)
                WearScreen.VALIDATION -> ValidationScreen(state, runtime)
                WearScreen.BREATHING -> BreathingScreen(state, runtime)
                WearScreen.GROUNDING -> GroundingScreen(runtime)
                WearScreen.SOS_CONFIRM -> SosConfirmScreen(runtime)
                WearScreen.SOS_COUNTDOWN -> SosCountdownScreen(runtime)
                WearScreen.SOS_ACTIVE -> SosActiveScreen(state, runtime)
                WearScreen.SETTINGS -> SettingsScreen(state, runtime)
                WearScreen.FINISHED -> FinishedScreen(state, runtime)
            }
        }
    }
}

@Composable
private fun MonitoringScreen(state: WearUiState, runtime: WearRuntime) {
    WatchColumn {
        StatusRow(state)
        Spacer(Modifier.height(8.dp))
        HeartRateOrb(state)
        Text(
            text = monitoringLabel(state),
            color = stateColor(state.monitoringState),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = state.message,
            color = WearDesign.TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        if (state.calibrationProgress < 1f) {
            Text(
                text = "Calibración ${(state.calibrationProgress * 100).toInt()}%",
                color = WearDesign.CalmAmber,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        PrimaryAction("Respirar ahora") { runtime.navigate(WearScreen.BREATHING) }
        HoldSosButton { runtime.navigate(WearScreen.SOS_CONFIRM) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MiniAction("Ajustes") { runtime.navigate(WearScreen.SETTINGS) }
                    MiniAction("Anclaje") { runtime.navigate(WearScreen.GROUNDING) }
        }
    }
}

@Composable
private fun StatusRow(state: WearUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            if (state.phoneConnected) "Teléfono conectado" else "Sin teléfono",
            color = if (state.phoneConnected) WearDesign.CalmGreen else WearDesign.TextMuted,
            fontSize = 10.sp,
        )
        Text("${state.batteryPercent}%", color = WearDesign.TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun HeartRateOrb(state: WearUiState) {
    Box(
        modifier = Modifier
            .size(142.dp)
            .clip(CircleShape)
            .background(WearDesign.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.heartRateBpm?.toString() ?: "—",
                color = WearDesign.TextPrimary,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("BPM", color = WearDesign.TextMuted, fontSize = 13.sp)
            state.baselineBpm?.let {
                Text("Base $it", color = WearDesign.CalmBlue, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PermissionsScreen(
    state: WearUiState,
    requestPermissions: () -> Unit,
    runtime: WearRuntime,
) {
    WatchColumn {
        ScreenTitle("Permisos de bienestar")
        Text(
            "La frecuencia cardíaca permite identificar cambios respecto a tu patrón habitual. " +
                "La actividad ayuda a distinguir movimiento o ejercicio.",
            color = WearDesign.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            "Puedes negarlos. La app seguirá ofreciendo respiración y SOS local.",
            color = WearDesign.CalmBlue,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
        PrimaryAction("Autorizar sensores", requestPermissions)
        PrimaryAction("Usar simulador") {
            runtime.useSimulatedData(true)
            runtime.navigate(WearScreen.MONITORING)
        }
        Text(state.message, color = WearDesign.TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun ValidationScreen(state: WearUiState, runtime: WearRuntime) {
    WatchColumn {
        ScreenTitle("Detectamos cambios inusuales")
        Text(
            if (state.monitoringState == MonitoringState.SECOND_VALIDATION) {
                "Queremos comprobar de nuevo: ¿necesitas apoyo?"
            } else {
                "¿Estás realizando actividad física?"
            },
            color = WearDesign.TextPrimary,
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
        )
        PrimaryAction("Sí, estoy activo") { runtime.respond(UserResponse.ACTIVITY_CONFIRMED) }
        PrimaryAction("No, necesito apoyo") { runtime.respond(UserResponse.SUPPORT_REQUESTED) }
        PrimaryAction("Me siento bien") { runtime.respond(UserResponse.USER_OK) }
        Text(
            "Puntuación ${(state.detectionScore * 100).toInt()}% · ${state.detectionReasons.joinToString()}",
            color = WearDesign.TextMuted,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private enum class BreathPhase { INHALE, EXHALE, COMPLETE }

@Composable
private fun BreathingScreen(state: WearUiState, runtime: WearRuntime) {
    var phase by remember { mutableStateOf(BreathPhase.INHALE) }
    var seconds by remember { mutableIntStateOf(4) }
    var cycles by remember { mutableIntStateOf(0) }
    val scale by animateFloatAsState(
        targetValue = if (phase == BreathPhase.INHALE) 1f else 0.58f,
        animationSpec = tween(4_000, easing = LinearEasing),
        label = "breathing-scale",
    )

    LaunchedEffect(Unit) {
        while (cycles < 3) {
            phase = BreathPhase.INHALE
            seconds = 4
            runtime.haptics.inhale()
            repeat(4) { delay(1_000); seconds -= 1 }
            phase = BreathPhase.EXHALE
            seconds = 4
            runtime.haptics.exhale()
            repeat(4) { delay(1_000); seconds -= 1 }
            cycles += 1
        }
        phase = BreathPhase.COMPLETE
        runtime.haptics.cancel()
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(WearDesign.OledBlack),
    ) {
        val compactLayout = maxWidth < 220.dp || maxHeight < 220.dp
        val shortestSide = minOf(maxWidth, maxHeight)
        val orbSize = (shortestSide * if (compactLayout) 0.36f else 0.40f)
            .coerceIn(76.dp, 118.dp)
        val horizontalPadding = if (compactLayout) 16.dp else 24.dp
        val verticalPadding = if (compactLayout) 8.dp else 16.dp
        val itemSpacing = if (compactLayout) 5.dp else 8.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            Text(
                when (phase) {
                    BreathPhase.INHALE -> "Inhala"
                    BreathPhase.EXHALE -> "Exhala"
                    BreathPhase.COMPLETE -> "¿Cómo te sientes?"
                },
                color = WearDesign.TextPrimary,
                fontSize = if (compactLayout) 17.sp else 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier.size(orbSize),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(orbSize * scale)
                        .clip(CircleShape)
                        .background(WearDesign.CalmBlue.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (phase == BreathPhase.COMPLETE) {
                            "3 ciclos"
                        } else {
                            seconds.coerceAtLeast(0).toString()
                        },
                        color = WearDesign.OledBlack,
                        fontSize = if (compactLayout) 16.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Text(
                "Ciclo ${cycles.coerceAtMost(2) + 1} de 3",
                color = WearDesign.TextMuted,
                fontSize = if (compactLayout) 9.sp else 10.sp,
            )
            if (phase == BreathPhase.COMPLETE) {
                MiniAction("Me ayudó") { runtime.respond(UserResponse.BREATHING_HELPED) }
                    MiniAction("Anclaje") { runtime.navigate(WearScreen.GROUNDING) }
            }
            MiniAction("Necesito ayuda") { runtime.navigate(WearScreen.SOS_CONFIRM) }
            MiniAction("Detener") {
                runtime.haptics.cancel()
                runtime.navigate(WearScreen.MONITORING)
            }
        }
    }
}

@Composable
private fun GroundingScreen(runtime: WearRuntime) {
    val prompts = listOf(
        "Mira 5 cosas a tu alrededor",
        "Siente 4 puntos de contacto",
        "Escucha 3 sonidos",
        "Identifica 2 aromas",
        "Nota 1 sensación amable",
    )
    var index by remember { mutableIntStateOf(0) }
    WatchColumn {
        ScreenTitle("Anclaje 5–4–3–2–1")
        Text(
            prompts[index],
            color = WearDesign.TextPrimary,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Paso ${index + 1} de 5", color = WearDesign.CalmViolet, fontSize = 12.sp)
        PrimaryAction(if (index == prompts.lastIndex) "Finalizar" else "Siguiente") {
            if (index == prompts.lastIndex) runtime.respond(UserResponse.BREATHING_HELPED) else index += 1
        }
        MiniAction("Volver") { runtime.navigate(WearScreen.MONITORING) }
    }
}

@Composable
private fun SosConfirmScreen(runtime: WearRuntime) {
    WatchColumn {
        ScreenTitle("Solicitar apoyo")
        Text(
            "Mantén presionado el botón. Después tendrás 5 segundos para cancelar.",
            color = WearDesign.TextMuted,
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
        )
        HoldConfirmButton { runtime.startManualSos() }
        MiniAction("Volver") { runtime.navigate(WearScreen.MONITORING) }
        Text(
            "El MVP no llama automáticamente a servicios públicos de emergencia.",
            color = WearDesign.TextMuted,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SosCountdownScreen(runtime: WearRuntime) {
    var seconds by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1_000)
            seconds -= 1
        }
        runtime.confirmSos()
    }
    WatchColumn {
        ScreenTitle("Confirmando solicitud")
        Text(
            seconds.toString(),
            color = WearDesign.CalmAmber,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
        )
        PrimaryAction("Cancelar") { runtime.cancelSos() }
    }
}

@Composable
private fun SosActiveScreen(state: WearUiState, runtime: WearRuntime) {
    WatchColumn {
        ScreenTitle("Solicitud registrada")
        Text(
            state.sosMessage,
            color = if (state.phoneConnected) WearDesign.CalmGreen else WearDesign.CalmAmber,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
        )
        Text(
            "El reloj conservará la solicitud localmente si no encuentra un teléfono compatible.",
            color = WearDesign.TextMuted,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
        )
        PrimaryAction("Ya estoy acompañado") { runtime.finishEvent() }
        PrimaryAction("Cancelar solicitud") { runtime.cancelSos() }
    }
}

@Composable
private fun SettingsScreen(state: WearUiState, runtime: WearRuntime) {
    WatchColumn {
        ScreenTitle("Estado del reloj")
        CapabilityLine("Frecuencia", state.heartRateStatus)
        CapabilityLine("Acelerómetro", state.accelerometerStatus)
        CapabilityLine("IBI", state.ibiStatus, state.ibiDetail)
        CapabilityLine("EDA", CapabilityStatus.UNSUPPORTED, state.edaDetail)
        Text("Pendientes: ${state.pendingSamples}", color = WearDesign.TextMuted, fontSize = 11.sp)
        Text(
            "Lectura actual: ${state.heartRateBpm?.let { "$it BPM" } ?: "esperando señal"}",
            color = WearDesign.TextPrimary,
            fontSize = 11.sp,
        )
        Text(
            "Calibración: ${(state.calibrationProgress * 100).toInt()}%",
            color = if (state.calibrationProgress >= 1f) WearDesign.CalmGreen else WearDesign.CalmAmber,
            fontSize = 11.sp,
        )
        Text(
            if (state.simulatedData) "Proveedor: simulado" else "Proveedor: sensores reales",
            color = WearDesign.CalmBlue,
            fontSize = 11.sp,
        )
        PrimaryAction(if (state.simulatedData) "Usar sensores reales" else "Usar simulador") {
            runtime.useSimulatedData(!state.simulatedData)
        }
        PrimaryAction("Simular anomalía") { runtime.simulateAnomaly() }
        MiniAction("Volver") { runtime.navigate(WearScreen.MONITORING) }
    }
}

@Composable
private fun FinishedScreen(state: WearUiState, runtime: WearRuntime) {
    WatchColumn {
        ScreenTitle("Evento finalizado")
        Text(state.message, color = WearDesign.TextPrimary, textAlign = TextAlign.Center)
        if (state.sosMessage.isNotBlank()) {
            Text(state.sosMessage, color = WearDesign.TextMuted, fontSize = 11.sp)
        }
        Text(
            "Tu respuesta ayuda a reducir falsos positivos; no confirma una condición médica.",
            color = WearDesign.TextMuted,
            textAlign = TextAlign.Center,
            fontSize = 10.sp,
        )
        PrimaryAction("Volver al monitoreo") { runtime.finishEvent() }
    }
}

@Composable
private fun WatchColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearDesign.OledBlack)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, top = 54.dp, end = 24.dp, bottom = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text = text,
        color = WearDesign.TextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MiniAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = WearDesign.CalmBlue,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(WearDesign.SurfaceRaised)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun HoldSosButton(onClick: () -> Unit) {
    Text(
        text = "SOS manual",
        color = WearDesign.CalmAmber,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(WearDesign.SurfaceRaised)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(vertical = 13.dp),
    )
}

@Composable
private fun HoldConfirmButton(onLongClick: () -> Unit) {
    Text(
        text = "Mantén para confirmar",
        color = WearDesign.OledBlack,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(WearDesign.CalmAmber)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 16.dp),
    )
}

@Composable
private fun CapabilityLine(label: String, status: CapabilityStatus, detail: String? = null) {
    val text = when (status) {
        CapabilityStatus.AVAILABLE -> "disponible"
        CapabilityStatus.UNAVAILABLE -> "no disponible"
        CapabilityStatus.UNSUPPORTED -> "no compatible"
        CapabilityStatus.PERMISSION_REQUIRED -> "requiere permiso"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$label · $text",
            color = if (status == CapabilityStatus.AVAILABLE) WearDesign.CalmGreen else WearDesign.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        if (!detail.isNullOrBlank() && status != CapabilityStatus.AVAILABLE) {
            Text(
                detail,
                color = WearDesign.TextMuted,
                fontSize = 9.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun monitoringLabel(state: WearUiState): String = when (state.monitoringState) {
    MonitoringState.NORMAL -> "Estado normal"
    MonitoringState.OBSERVING -> "En observación"
    MonitoringState.USER_VALIDATION,
    MonitoringState.SECOND_VALIDATION,
    -> "Comprobación necesaria"
    MonitoringState.INTERVENTION -> "Apoyo en curso"
    MonitoringState.SOS_PENDING -> "SOS pendiente"
    MonitoringState.SOS_ACTIVE -> "SOS activo"
    MonitoringState.RESOLVED -> "Evento resuelto"
    MonitoringState.COOLDOWN -> "Periodo de descanso"
}

private fun stateColor(state: MonitoringState): Color = when (state) {
    MonitoringState.NORMAL -> WearDesign.CalmGreen
    MonitoringState.OBSERVING -> WearDesign.CalmAmber
    MonitoringState.USER_VALIDATION,
    MonitoringState.SECOND_VALIDATION,
    MonitoringState.SOS_PENDING,
    MonitoringState.SOS_ACTIVE,
    -> WearDesign.CalmAmber
    else -> WearDesign.CalmBlue
}

@Composable
private fun <T> StateFlow<T>.collectAsStateCompat() = collectAsState()

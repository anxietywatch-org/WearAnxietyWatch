package com.anxietywatch.wear.datalayer

import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.storage.StoredTelemetry
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contrato de salida del reloj hacia el teléfono mediante Wear Data Layer.
 *
 * Las rutas coinciden con los endpoints del backend para que el receptor móvil
 * pueda enrutar cada mensaje sin reglas adicionales. El reloj no realiza HTTP:
 * el teléfono agrega la identidad autenticada y transforma los registros del
 * reloj al DTO público antes de llamar al API.
 */
object BackendEndpointContract {
    const val TELEMETRY_BATCH_ENDPOINT = "/api/v1/telemetry/batch"
    const val SOS_TRIGGER_ENDPOINT = "/api/v1/sos/trigger"

    private const val TELEMETRY_SCHEMA = "wear-telemetry-records-v2"
    private const val SOS_SCHEMA = "wear-sos-trigger-v1"

    fun telemetryPath(batchId: String): String = "$TELEMETRY_BATCH_ENDPOINT/$batchId"

    fun telemetryEnvelope(batchId: String, rows: List<StoredTelemetry>): JSONObject {
        require(rows.isNotEmpty()) { "El lote de telemetría no puede estar vacío." }
        val orderedRows = rows.sortedBy { it.capturedAtEpochMillis }
        return JSONObject()
            .put("schemaVersion", TELEMETRY_SCHEMA)
            .put("targetEndpoint", TELEMETRY_BATCH_ENDPOINT)
            .put("transport", "WEAR_DATA_LAYER")
            .put("batchId", batchId)
            .put("startedAt", Instant.ofEpochMilli(orderedRows.first().capturedAtEpochMillis).toString())
            .put("endedAt", Instant.ofEpochMilli(orderedRows.last().capturedAtEpochMillis).toString())
            .put(
                "mobileEnrichmentRequired",
                JSONArray(listOf("userId", "deviceId", "sessionId", "sequence", "samples")),
            )
            .put(
                "records",
                JSONArray().apply {
                    orderedRows.forEach { row ->
                        put(
                            JSONObject()
                                .put("id", row.id)
                                .put("capturedAt", Instant.ofEpochMilli(row.capturedAtEpochMillis).toString())
                                .put("type", row.type)
                                .put("payload", runCatching { JSONObject(row.payload) }.getOrNull()),
                        )
                    }
                },
            )
    }

    fun sosEnvelope(event: PendingEvent): JSONObject = JSONObject()
        .put("schemaVersion", SOS_SCHEMA)
        .put("targetEndpoint", SOS_TRIGGER_ENDPOINT)
        .put("transport", "WEAR_DATA_LAYER")
        .put("eventId", event.id)
        .put("triggeredAt", Instant.ofEpochMilli(event.startedAtEpochMillis).toString())
        .put("source", "WATCH")
        .put("reason", "Solicitud manual confirmada en el reloj")
        .put("state", event.state.name)
        .put("score", event.triggerScore)
        .put("rulesVersion", event.rulesVersion)
        .put("mobileEnrichmentRequired", JSONArray(listOf("userId", "deviceId")))
}

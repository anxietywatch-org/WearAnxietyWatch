package com.anxietywatch.wear.datalayer

import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.storage.StoredTelemetry
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Contrato de salida del reloj hacia el teléfono mediante Wear Data Layer.
 *
 * Rutas finales de la capa fog (`/fog/v1/...`), por identificador. El reloj no
 * realiza HTTP: el teléfono (nodo fog, `fog_phone_v1`) agrega la identidad
 * autenticada y transforma los sobres al DTO público del cloud antes de llamar
 * al API. El reloj anuncia `fog_watch_v1`.
 */
object BackendEndpointContract {
    const val FOG_PROTOCOL = "fog_watch_v1"

    const val TELEMETRY_ENDPOINT = "/fog/v1/telemetry"
    const val SOS_ENDPOINT = "/fog/v1/sos"
    const val SOS_CANCEL_ENDPOINT = "/fog/v1/sos/cancel"
    const val SUSPECTED_EVENT_ENDPOINT = "/fog/v1/events/suspected"
    const val CAPABILITIES_ENDPOINT = "/fog/v1/capabilities"
    const val CONFIG_RULES_ENDPOINT = "/fog/v1/config/rules"
    const val EVENTS_DECISION_ENDPOINT = "/fog/v1/events/decision"

    const val ACK_TELEMETRY_PREFIX = "/fog/v1/ack/telemetry/"
    const val ACK_SOS_PREFIX = "/fog/v1/ack/sos/"
    const val ACK_SOS_CANCEL_PREFIX = "/fog/v1/ack/sos-cancel/"

    private const val TELEMETRY_SCHEMA = "wear-telemetry-records-v2"
    private const val SOS_SCHEMA = "wear-sos-trigger-v1"

    fun telemetryPath(batchId: String): String = "$TELEMETRY_ENDPOINT/$batchId"
    fun sosPath(eventId: String): String = "$SOS_ENDPOINT/$eventId"
    fun sosCancelPath(eventId: String): String = "$SOS_CANCEL_ENDPOINT/$eventId"
    fun suspectedPath(eventId: String): String = "$SUSPECTED_EVENT_ENDPOINT/$eventId"

    fun telemetryEnvelope(batchId: String, rows: List<StoredTelemetry>): JSONObject {
        require(rows.isNotEmpty()) { "El lote de telemetría no puede estar vacío." }
        val orderedRows = rows.sortedBy { it.capturedAtEpochMillis }
        return JSONObject()
            .put("schemaVersion", TELEMETRY_SCHEMA)
            .put("targetEndpoint", TELEMETRY_ENDPOINT)
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
        .put("targetEndpoint", SOS_ENDPOINT)
        .put("transport", "WEAR_DATA_LAYER")
        .put("eventId", event.id)
        .put("triggeredAt", Instant.ofEpochMilli(event.startedAtEpochMillis).toString())
        .put("source", "WATCH")
        .put("reason", "Solicitud manual confirmada en el reloj")
        .put("state", event.state.name)
        .put("score", event.triggerScore)
        .put("rulesVersion", event.rulesVersion)
        .put("mobileEnrichmentRequired", JSONArray(listOf("userId", "deviceId")))

    fun sosCancelEnvelope(event: PendingEvent): JSONObject {
        val cancelledAtMillis = event.endedAtEpochMillis ?: event.startedAtEpochMillis
        return JSONObject()
            .put("schemaVersion", SOS_SCHEMA)
            .put("targetEndpoint", SOS_CANCEL_ENDPOINT)
            .put("transport", "WEAR_DATA_LAYER")
            .put("eventId", event.id)
            .put("triggeredAt", Instant.ofEpochMilli(event.startedAtEpochMillis).toString())
            .put("cancelledAt", Instant.ofEpochMilli(cancelledAtMillis).toString())
            .put("source", "WATCH")
            .put("reason", "SOS cancelado por el usuario en el reloj")
            .put("state", event.state.name)
            .put("cancelled", true)
            .put("mobileEnrichmentRequired", JSONArray(listOf("userId", "deviceId")))
    }

    fun capabilitiesEnvelope(deviceModel: String, wearOsVersion: String): JSONObject = JSONObject()
        .put("schemaVersion", "fog-capabilities-v1")
        .put("targetEndpoint", CAPABILITIES_ENDPOINT)
        .put("transport", "WEAR_DATA_LAYER")
        .put("fogProtocol", FOG_PROTOCOL)
        .put("deviceModel", deviceModel)
        .put("wearOsVersion", wearOsVersion)
        .put("mobileEnrichmentRequired", JSONArray(listOf("userId", "deviceId")))
}

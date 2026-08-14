package com.anxietywatch.mobile.fog

import com.anxietywatch.mobile.fog.room.FogOutboxEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
class FogNativeSyncTest {
    private val identity = JSONObject().put("userId", "user-1").put("deviceId", "device-1")
        .put("sessionId", "session-1").put("sequence", 7)

    @Test fun enrichesKnownTelemetryAndDropsUnknownRecords() {
        val envelope = JSONObject().put("targetEndpoint", "/fog/v1/telemetry").put("batchId", "batch-1")
            .put("startedAt", "2026-08-13T10:00:00Z").put("endedAt", "2026-08-13T10:01:00Z")
            .put("records", org.json.JSONArray()
                .put(record("heart_rate", JSONObject().put("bpm", 72).put("ibiMillis", org.json.JSONArray().put(830)).put("quality", .9)))
                .put(record("motion", JSONObject().put("x", 1).put("y", 2).put("z", 3)))
                .put(record("skin_temperature", JSONObject().put("celsius", 36.4)))
                .put(record("steps", JSONObject().put("count", 10))))
        val request = FogNativeSync.requestFor(entry("telemetry", envelope), identity)
        assertNotNull(request)
        assertEquals("/api/v1/telemetry/batch", request!!.first)
        val samples = request.second.getJSONArray("samples")
        assertEquals(3, samples.length())
        assertEquals(72.0, samples.getJSONObject(0).getDouble("heartRateBpm"), 0.0)
        assertEquals(1, samples.getJSONObject(1).getJSONObject("accelerometer").getInt("x"))
        assertEquals(36.4, samples.getJSONObject(2).getDouble("skinTemperatureCelsius"), 0.0)
    }

    @Test fun poisonEnvelopeIsRejected() {
        assertNull(FogNativeSync.requestFor(FogOutboxEntry(kind = "telemetry", entityId = "x", payload = "{"), identity))
    }

    @Test fun cancellationUsesCloudContract() {
        val envelope = JSONObject().put("targetEndpoint", "/fog/v1/sos/cancel")
            .put("eventId", "event-1").put("cancelled", true).put("cancelledAt", "2026-08-13T10:00:00Z")
        val request = FogNativeSync.requestFor(entry("sos-cancel", envelope), identity)!!
        assertEquals("/api/v1/sos/cancel", request.first)
        assertEquals("user-1", request.second.getString("userId"))
    }

    private fun record(type: String, payload: JSONObject) = JSONObject()
        .put("type", type).put("capturedAt", "2026-08-13T10:00:00Z").put("payload", payload)
    private fun entry(kind: String, envelope: JSONObject) =
        FogOutboxEntry(kind = kind, entityId = "id-1", payload = envelope.toString())
}

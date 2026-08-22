package com.anxietywatch.wear.datalayer

import com.anxietywatch.wear.domain.BaselineSnapshot
import com.anxietywatch.wear.domain.DerivedFeatures
import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.domain.UserResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchDataListenerServiceTest {

    @Test
    fun `acker telemetry path carries batch id`() {
        assertEquals("/fog/v1/ack/telemetry/batch-123", BackendEndpointContract.ACK_TELEMETRY_PREFIX + "batch-123")
    }

    @Test
    fun `acker sos path carries event id`() {
        assertEquals("/fog/v1/ack/sos/event-456", BackendEndpointContract.ACK_SOS_PREFIX + "event-456")
    }

    @Test
    fun `acker sos cancel path carries event id`() {
        assertEquals("/fog/v1/ack/sos-cancel/event-789", BackendEndpointContract.ACK_SOS_CANCEL_PREFIX + "event-789")
    }

    @Test
    fun `acker suspected path carries event id`() {
        assertEquals("/fog/v1/ack/events/suspected/event-abc", BackendEndpointContract.ACK_SUSPECTED_PREFIX + "event-abc")
    }

    @Test
    fun `acker decision path carries event id`() {
        assertEquals("/fog/v1/ack/events/decision/event-def", BackendEndpointContract.ACK_DECISION_PREFIX + "event-def")
    }

    @Test
    fun `listener recognizes exact suspected ack path`() {
        assertEquals(
            "SUSPECTED",
            WatchDataListenerService.ackKind("/fog/v1/ack/events/suspected/event-abc")?.name,
        )
    }

    @Test
    fun `listener recognizes exact decision ack path`() {
        assertEquals(
            "DECISION",
            WatchDataListenerService.ackKind("/fog/v1/ack/events/decision/event-def")?.name,
        )
    }

    @Test
    fun `telemetry route is the fog endpoint plus batch id`() {
        assertEquals("/fog/v1/telemetry/batch-123", BackendEndpointContract.telemetryPath("batch-123"))
        assertEquals("/fog/v1/telemetry", BackendEndpointContract.TELEMETRY_ENDPOINT)
    }

    @Test
    fun `sos routes tensor onto fog v1 paths`() {
        assertEquals("/fog/v1/sos/event-1", BackendEndpointContract.sosPath("event-1"))
        assertEquals("/fog/v1/sos/cancel/event-1", BackendEndpointContract.sosCancelPath("event-1"))
        assertEquals("/fog/v1/events/suspected/event-1", BackendEndpointContract.suspectedPath("event-1"))
        assertEquals("/fog/v1/events/decision/event-1", BackendEndpointContract.decisionPath("event-1"))
    }

    @Test
    fun `watch announces fog_watch_v1`() {
        assertEquals("fog_watch_v1", BackendEndpointContract.FOG_PROTOCOL)
    }

    @Test
    fun `sos cancel envelope is flagged cancelled`() {
        val event = PendingEvent(
            startedAtEpochMillis = 1_000_000L,
            state = MonitoringState.RESOLVED,
            triggerScore = 0.8,
            rulesVersion = "rules-v2",
            userResponse = UserResponse.SOS_CANCELLED,
        )
        val raw = BackendEndpointContract.sosCancelEnvelope(event).toString()
        assertTrue(raw.contains("\"targetEndpoint\":\"/fog/v1/sos/cancel\""))
        assertTrue(raw.contains("\"cancelled\":true"))
        assertTrue(raw.contains("\"source\":\"WATCH\""))
    }

    @Test
    fun `sos envelope targets fog v1 sos with watch source`() {
        val event = PendingEvent(
            startedAtEpochMillis = 2_000_000L,
            state = MonitoringState.SOS_ACTIVE,
            triggerScore = 0.9,
            rulesVersion = "rules-v2",
        )
        val raw = BackendEndpointContract.sosEnvelope(event).toString()
        assertTrue(raw.contains("\"targetEndpoint\":\"/fog/v1/sos\""))
        assertTrue(raw.contains("\"source\":\"WATCH\""))
        assertTrue(raw.contains("\"eventId\":\""))
    }

    @Test
    fun `suspected envelope carries detection snapshot without sos fields`() {
        val event = PendingEvent(
            startedAtEpochMillis = 3_000_000L,
            state = MonitoringState.USER_VALIDATION,
            triggerScore = 0.88,
            rulesVersion = "rules-v2",
            features = DerivedFeatures(
                heartRateMean = 96.0,
                heartRateMax = 108.0,
                heartRateSlopeBpmPerMinute = 1.2,
                heartRateDeltaFromBaseline = 12.0,
                rmssdMillis = 21.0,
                sdnnMillis = 30.0,
                movementMagnitudeMean = 0.05,
                movementVariance = 0.0004,
                validSampleRatio = 0.95,
                lastSampleAgeSeconds = 5L,
                sampleCount = 60,
            ),
            baseline = BaselineSnapshot(
                sampleCount = 240L,
                meanHeartRate = 82.0,
                heartRateM2 = 310.0,
                updatedAtEpochMillis = 2_900_000L,
            ),
        )
        val raw = BackendEndpointContract.suspectedEventEnvelope(event).toString()
        assertTrue(raw.contains("\"schemaVersion\":\"wear-suspected-event-v1\""))
        assertTrue(raw.contains("\"targetEndpoint\":\"/fog/v1/events/suspected\""))
        assertTrue(raw.contains("\"state\":\"USER_VALIDATION\""))
        assertTrue(raw.contains("\"score\":0.88"))
        val json = org.json.JSONObject(raw)
        assertEquals(96.0, json.getJSONObject("features").getDouble("heartRateMean"), 0.001)
        assertEquals(240, json.getJSONObject("baseline").getInt("sampleCount"))
        assertFalse(raw.contains("\"sosStatus\""))
    }

    @Test
    fun `decision envelope carries primary response`() {
        val event = PendingEvent(
            startedAtEpochMillis = 3_000_000L,
            state = MonitoringState.RESOLVED,
            triggerScore = 0.88,
            rulesVersion = "rules-v2",
            userResponse = UserResponse.SUPPORT_REQUESTED,
            endedAtEpochMillis = 3_200_000L,
        )
        val raw = BackendEndpointContract.eventDecisionEnvelope(event).toString()
        assertTrue(raw.contains("\"schemaVersion\":\"wear-event-decision-v1\""))
        assertTrue(raw.contains("\"targetEndpoint\":\"/fog/v1/events/decision\""))
        assertTrue(raw.contains("\"response\":\"SUPPORT_REQUESTED\""))
        assertTrue(raw.contains("\"detectedAt\":\"1970-01-01T00:50:00Z\""))
        assertFalse(raw.contains("\"features\""))
    }

    @Test
    fun `capabilities envelope announces fog protocol`() {
        val raw = BackendEndpointContract.capabilitiesEnvelope("Watch6", "5.0").toString()
        assertTrue(raw.contains("\"fogProtocol\":\"fog_watch_v1\""))
        assertTrue(raw.contains("\"targetEndpoint\":\"/fog/v1/capabilities\""))
    }
}

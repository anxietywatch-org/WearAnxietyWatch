package com.anxietywatch.wear.datalayer

import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.domain.UserResponse
import org.junit.Assert.assertEquals
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
    fun `telemetry route is the fog endpoint plus batch id`() {
        assertEquals("/fog/v1/telemetry/batch-123", BackendEndpointContract.telemetryPath("batch-123"))
        assertEquals("/fog/v1/telemetry", BackendEndpointContract.TELEMETRY_ENDPOINT)
    }

    @Test
    fun `sos routes tensor onto fog v1 paths`() {
        assertEquals("/fog/v1/sos/event-1", BackendEndpointContract.sosPath("event-1"))
        assertEquals("/fog/v1/sos/cancel/event-1", BackendEndpointContract.sosCancelPath("event-1"))
        assertEquals("/fog/v1/events/suspected/event-1", BackendEndpointContract.suspectedPath("event-1"))
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
    fun `capabilities envelope announces fog protocol`() {
        val raw = BackendEndpointContract.capabilitiesEnvelope("Watch6", "5.0").toString()
        assertTrue(raw.contains("\"fogProtocol\":\"fog_watch_v1\""))
        assertTrue(raw.contains("\"targetEndpoint\":\"/fog/v1/capabilities\""))
    }
}

package com.anxietywatch.wear.datalayer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.domain.SensorReading
import com.anxietywatch.wear.storage.WearDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OutboxSyncerSchedulerTest {
    private lateinit var db: WearDatabase
    private lateinit var transport: RecordingTransport
    private val t = System.currentTimeMillis()

    @Before fun setUp() {
        db = WearDatabase(ApplicationProvider.getApplicationContext<Context>())
        transport = RecordingTransport()
    }

    @Test fun `full dependency chain is scheduled in order`() = runBlocking {
        val id = "event-x"
        db.insertReading(SensorReading.HeartRate(bpm = 80.0, ibiMillis = null, signalQuality = 1.0, capturedAtEpochMillis = t - 1_000, source = "test"))
        db.upsertSuspectedEvent(event(id))
        db.upsertDecisionEvent(event(id))
        val syncer = syncer()
        syncer.syncOnceForTest("phone")
        assertTrue(transport.data.isNotEmpty())
        assertFalse(transport.messages.any { it.contains("suspected") })
        assertFalse(transport.messages.any { it.contains("decision") })
        val batch = db.pendingBatches(Long.MAX_VALUE).first()
        db.markBatchConfirmed(batch.batchId); db.markTelemetryConfirmedByBatch(batch.batchId)
        syncer.syncOnceForTest("phone")
        assertTrue(transport.messages.any { it.contains("suspected") })
        assertFalse(transport.messages.any { it.contains("decision") })
        db.markEventConfirmed(WearDatabase.EVENT_KIND_SUSPECTED, id)
        syncer.syncOnceForTest("phone")
        assertTrue(transport.messages.any { it.contains("decision") })
        db.markEventConfirmed(WearDatabase.EVENT_KIND_DECISION, id)
        assertTrue(db.isDecisionEventConfirmed(id))
        assertEquals(2, transport.messages.count { it.contains(id) })
    }

    @Test fun `blocked event does not block eligible decision`() = runBlocking {
        db.insertReading(SensorReading.HeartRate(bpm = 80.0, ibiMillis = null, signalQuality = 1.0, capturedAtEpochMillis = t - 1_000, source = "test"))
        db.upsertSuspectedEvent(event("a"))
        db.upsertSuspectedEvent(event("b"))
        db.upsertDecisionEvent(event("b"))
        db.markEventConfirmed(WearDatabase.EVENT_KIND_SUSPECTED, "b")
        syncer().syncOnceForTest("phone")
        assertFalse(transport.messages.any { it.contains("suspected/a") })
        assertTrue(transport.messages.any { it.contains("decision/b") })
    }

    @Test fun `post T telemetry does not starve eligible suspected`() = runBlocking {
        val id = "event-x-post-${System.nanoTime()}"
        val inWindow = db.insertReading(SensorReading.HeartRate(bpm = 80.0, ibiMillis = null, signalQuality = 1.0, capturedAtEpochMillis = t - 1_000, source = "test"))
        db.markTelemetrySent(listOf(inWindow), "confirmed-batch")
        db.markTelemetryConfirmedByBatch("confirmed-batch")
        db.insertReading(SensorReading.HeartRate(bpm = 81.0, ibiMillis = null, signalQuality = 1.0, capturedAtEpochMillis = t + 1_000, source = "test"))
        db.upsertSuspectedEvent(event(id))
        syncer().syncOnceForTest("phone")
        assertTrue(transport.data.isNotEmpty())
        assertTrue(transport.messages.toString(), transport.messages.any { it.contains("suspected/$id") })
    }

    private fun event(id: String) = PendingEvent(id, t, MonitoringState.USER_VALIDATION, .8, "test")
    private fun syncer() = OutboxSyncer(
        ApplicationProvider.getApplicationContext(), db,
        PhoneConnectionObserver(ApplicationProvider.getApplicationContext()), kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        transport,
    )

    private class RecordingTransport : OutboxTransport {
        val data = mutableListOf<String>(); val messages = mutableListOf<String>()
        override suspend fun sendData(nodeId: String, route: String, payload: ByteArray) { data += route }
        override suspend fun sendMessage(nodeId: String, route: String, payload: ByteArray) { messages += route }
    }
}

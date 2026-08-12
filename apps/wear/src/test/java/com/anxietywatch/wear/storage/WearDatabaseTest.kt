package com.anxietywatch.wear.storage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.domain.SensorReading
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifica el ciclo de la cola de salida sobre SQLite real (Robolectric):
 * la telemetría se inserta como QUEUED y debe ser recogida por pendingTelemetry.
 * Regresión del bug de estados minúsculas ('pending') vs enum names ('QUEUED').
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WearDatabaseTest {

    private lateinit var db: WearDatabase

    @Before
    fun setUp() {
        db = WearDatabase(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `queued telemetry is picked up by pendingTelemetry`() {
        db.insertReading(heartRate())
        val pending = db.pendingTelemetry(10)
        assertEquals(1, pending.size)
        assertEquals(80.0, heartRateBpm(pending.first().payload), 0.001)
    }

    @Test
    fun `pendingCount matches pending telemetry`() {
        db.insertReading(heartRate())
        db.insertReading(motion())
        assertEquals(2, db.pendingCount())
        assertEquals(2, db.pendingTelemetry(10).size)
    }

    @Test
    fun `failed telemetry is retried by pendingTelemetry`() {
        val id = db.insertReading(heartRate())
        db.markTelemetryFailed(listOf(id))
        val pending = db.pendingTelemetry(10)
        assertEquals(1, pending.size)
        assertEquals(id, pending.first().id)
    }

    @Test
    fun `confirmed telemetry is not pending anymore`() {
        val id = db.insertReading(heartRate())
        val batchId = "batch-test-1"
        db.markTelemetrySent(listOf(id), batchId)
        db.markTelemetryConfirmedByBatch(batchId)
        assertEquals(0, db.pendingTelemetry(10).size)
        assertEquals(0, db.pendingCount())
    }

    @Test
    fun `queued sos event is picked up until confirmed`() {
        val event = PendingEvent(
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.SOS_ACTIVE,
            triggerScore = 0.9,
            rulesVersion = "rules-v2",
        )
        db.upsertEvent(event)
        assertTrue(db.pendingEvents(now = Long.MAX_VALUE).any { it.id == event.id })

        db.markEventConfirmed(event.id)
        assertTrue(db.pendingEvents(now = Long.MAX_VALUE).none { it.id == event.id })
    }

    @Test
    fun `sent batch is retried until confirmed`() {
        db.upsertBatch(
            StoredBatch(
                batchId = "batch-1",
                fromMillis = 1L,
                toMillis = 2L,
                state = SyncState.SENT,
                payload = "{}",
                attempts = 1,
                nextAttemptAt = 0L,
                remoteAck = false,
            ),
        )
        assertTrue(db.pendingBatches(now = Long.MAX_VALUE).any { it.batchId == "batch-1" })

        db.markBatchConfirmed("batch-1")
        assertTrue(db.pendingBatches(now = Long.MAX_VALUE).none { it.batchId == "batch-1" })
    }

    private fun heartRate() = SensorReading.HeartRate(
        bpm = 80.0,
        ibiMillis = listOf(750.0, 745.0),
        signalQuality = 0.9,
        capturedAtEpochMillis = 1_000L,
        source = "test",
    )

    private fun motion() = SensorReading.Motion(
        magnitudeG = 1.02,
        variance = 0.01,
        capturedAtEpochMillis = 2_000L,
        source = "test",
    )

    private fun heartRateBpm(payload: String): Double {
        val json = org.json.JSONObject(payload)
        return json.getDouble("bpm")
    }
}

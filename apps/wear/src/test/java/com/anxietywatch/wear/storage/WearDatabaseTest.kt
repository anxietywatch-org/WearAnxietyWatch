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

@Test
    fun `sent sos event is requeued when cancelled so the cancel reaches the phone`() {
        val event = PendingEvent(
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.SOS_ACTIVE,
            triggerScore = 0.9,
            rulesVersion = "rules-v2",
        )
        db.upsertEvent(event)
        db.markEventSent(event.id, attempts = 1, nextAttemptAt = Long.MAX_VALUE)
        assertTrue(db.pendingEvents(now = Long.MAX_VALUE).none { it.id == event.id })

        val cancelled = event.copy(
            state = MonitoringState.RESOLVED,
            userResponse = com.anxietywatch.wear.domain.UserResponse.SOS_CANCELLED,
            endedAtEpochMillis = 2_000L,
        )
        db.upsertEvent(cancelled)

        val pending = db.pendingEvents(now = Long.MAX_VALUE).first { it.id == event.id }
        assertEquals(
            com.anxietywatch.wear.domain.UserResponse.SOS_CANCELLED,
            pending.userResponse,
        )
        assertEquals(2_000L, pending.endedAtEpochMillis)
    }

    @Test
    fun `migration v3 normalizes lowercase sync states to enum names`() {
        // 1. Crear el esquema actual (v3) con el código de la app.
        val seed = WearDatabase(ApplicationProvider.getApplicationContext<Context>())
        seed.writableDatabase
        seed.close()

        // 2. Plantar valores de la versión 2 (estados en minúsculas) y
        //    retroceder PRAGMA user_version para forzar onUpgrade(2, 3).
        val path = ApplicationProvider
            .getApplicationContext<Context>()
            .getDatabasePath("anxietywatch-wear.db")
        android.database.sqlite.SQLiteDatabase.openDatabase(
            path.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        ).use { raw ->
            raw.execSQL("PRAGMA user_version = 2")
            raw.execSQL("INSERT INTO telemetry (id, captured_at, type, payload, sync_state) VALUES ('m1', 1000, 'heart_rate', '{}', 'pending')")
            raw.execSQL("INSERT INTO telemetry (id, captured_at, type, payload, sync_state) VALUES ('m2', 2000, 'motion', '{}', 'synced')")
            raw.execSQL("UPDATE sync_batches SET state = 'sent'")
            raw.execSQL("UPDATE events SET sync_state = 'queued'")
        }

        // 3. Reabrir: la migración v3 debe normalizar a mayúsculas.
        val reopened = WearDatabase(ApplicationProvider.getApplicationContext<Context>())
        val pending = reopened.pendingTelemetry(10)
        assertEquals(1, pending.size)
        assertEquals("m1", pending.first().id)
        val confirmedCount = reopened.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM telemetry WHERE sync_state = ?",
            arrayOf(SyncState.CONFIRMED.name),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else -1 }
        assertEquals(1, confirmedCount)
        reopened.close()
        db = WearDatabase(ApplicationProvider.getApplicationContext<Context>())
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

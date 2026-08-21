package com.anxietywatch.wear.storage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anxietywatch.wear.domain.BaselineSnapshot
import com.anxietywatch.wear.domain.DerivedFeatures
import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.domain.SensorReading
import com.anxietywatch.wear.domain.UserResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        db.upsertSosEvent(event)
        assertTrue(db.pendingEvents(now = Long.MAX_VALUE).any { it.event.id == event.id })

        db.markEventConfirmed(WearDatabase.EVENT_KIND_SOS, event.id)
        assertTrue(db.pendingEvents(now = Long.MAX_VALUE).none { it.event.id == event.id })
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
    fun `sos and cancellation coexist and are acknowledged independently`() {
        val event = PendingEvent(
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.SOS_ACTIVE,
            triggerScore = 0.9,
            rulesVersion = "rules-v2",
        )
db.upsertSosEvent(event)
        db.markEventSent(
            WearDatabase.EVENT_KIND_SOS,
            event.id,
            attempts = 1,
            nextAttemptAt = Long.MAX_VALUE,
        )
        assertTrue(db.pendingEvents(now = Long.MAX_VALUE).none { it.event.id == event.id })

        val cancelled = event.copy(
            state = MonitoringState.RESOLVED,
            userResponse = com.anxietywatch.wear.domain.UserResponse.SOS_CANCELLED,
            endedAtEpochMillis = 2_000L,
        )
db.upsertSosCancelEvent(cancelled)

        val pending = db.pendingEvents(now = Long.MAX_VALUE)
            .first { it.kind == WearDatabase.EVENT_KIND_SOS_CANCEL && it.event.id == event.id }
        assertEquals(
            com.anxietywatch.wear.domain.UserResponse.SOS_CANCELLED,
            pending.event.userResponse,
        )
        assertEquals(2_000L, pending.event.endedAtEpochMillis)
        val operationCount = db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM events WHERE id = ?",
            arrayOf(event.id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        assertEquals(2, operationCount)

        db.markEventConfirmed(WearDatabase.EVENT_KIND_SOS_CANCEL, event.id)
        val sosState = db.readableDatabase.rawQuery(
            "SELECT sync_state FROM events WHERE kind = ? AND id = ?",
            arrayOf(WearDatabase.EVENT_KIND_SOS, event.id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        assertEquals(SyncState.SENT.name, sosState)
    }

    @Test
    fun `suspected detection is persisted and delivered as suspected, not sos`() {
        val event = PendingEvent(
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.USER_VALIDATION,
            triggerScore = 0.88,
            rulesVersion = "rules-v2",
        )
        db.upsertSuspectedEvent(event)

        val pending = db.pendingEvents(now = Long.MAX_VALUE).first { it.event.id == event.id }
        assertEquals(WearDatabase.EVENT_KIND_SUSPECTED, pending.kind)
        assertEquals(MonitoringState.USER_VALIDATION, pending.event.state)

        val sosRows = db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM events WHERE kind = ? AND id = ?",
            arrayOf(WearDatabase.EVENT_KIND_SOS, event.id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else -1 }
        assertEquals(0, sosRows)
    }

    @Test
    fun `primary decision and suspected rows coexist for same event id`() {
        val event = PendingEvent(
            id = "shared-id",
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.INTERVENTION,
            triggerScore = 0.88,
            rulesVersion = "rules-v2",
            userResponse = UserResponse.SUPPORT_REQUESTED,
            endedAtEpochMillis = 2_000L,
        )
        db.upsertSuspectedEvent(event)
        db.upsertDecisionEvent(event)

        val pending = db.pendingEvents(now = Long.MAX_VALUE)
            .filter { it.event.id == "shared-id" }
        assertEquals(2, pending.size)
        assertTrue(pending.any { it.kind == WearDatabase.EVENT_KIND_SUSPECTED })
        assertTrue(pending.any { it.kind == WearDatabase.EVENT_KIND_DECISION })
        val decision = pending.first { it.kind == WearDatabase.EVENT_KIND_DECISION }.event
        assertEquals(UserResponse.SUPPORT_REQUESTED, decision.userResponse)
    }

    @Test
    fun `primary decision is not overwritten by a later activity response`() {
        val event = PendingEvent(
            id = "shared-id",
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.INTERVENTION,
            triggerScore = 0.88,
            rulesVersion = "rules-v2",
            userResponse = UserResponse.SUPPORT_REQUESTED,
            endedAtEpochMillis = 2_000L,
        )
        db.upsertSuspectedEvent(event)
        db.upsertDecisionEvent(event)

        val later = event.copy(
            state = MonitoringState.RESOLVED,
            userResponse = UserResponse.BREATHING_HELPED,
            endedAtEpochMillis = 5_000L,
        )
        db.upsertSuspectedEvent(later)

        val decision = db.pendingEvents(now = Long.MAX_VALUE)
            .first { it.kind == WearDatabase.EVENT_KIND_DECISION && it.event.id == "shared-id" }
            .event
        assertEquals(UserResponse.SUPPORT_REQUESTED, decision.userResponse)

        val suspected = db.pendingEvents(now = Long.MAX_VALUE)
            .first { it.kind == WearDatabase.EVENT_KIND_SUSPECTED && it.event.id == "shared-id" }
            .event
        assertEquals(UserResponse.BREATHING_HELPED, suspected.userResponse)
    }

    @Test
    fun `suspected event round-trips features and baseline snapshot`() {
        val event = PendingEvent(
            id = "snapshot-id",
            startedAtEpochMillis = 1_000L,
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
                updatedAtEpochMillis = 900L,
            ),
        )
        db.upsertSuspectedEvent(event)

        val restored = db.pendingEvents(now = Long.MAX_VALUE)
            .first { it.kind == WearDatabase.EVENT_KIND_SUSPECTED && it.event.id == "snapshot-id" }
            .event
        assertEquals(96.0, restored.features?.heartRateMean, 0.001)
        assertEquals(12.0, restored.features?.heartRateDeltaFromBaseline, 0.001)
        assertEquals(60, restored.features?.sampleCount)
        assertEquals(240L, restored.baseline?.sampleCount)
        assertEquals(82.0, restored.baseline?.meanHeartRate, 0.001)
        assertEquals(310.0, restored.baseline?.heartRateM2, 0.001)
        assertEquals(900L, restored.baseline?.updatedAtEpochMillis)
    }

    @Test
    fun `suspected and decision are acknowledged independently of sos`() {
        val event = PendingEvent(
            id = "shared-id",
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.USER_VALIDATION,
            triggerScore = 0.88,
            rulesVersion = "rules-v2",
        )
        db.upsertSuspectedEvent(event)
        db.upsertDecisionEvent(event.copy(userResponse = UserResponse.USER_OK, endedAtEpochMillis = 2_000L))

        db.markEventConfirmed(WearDatabase.EVENT_KIND_SUSPECTED, event.id)
        val remaining = db.pendingEvents(now = Long.MAX_VALUE)
            .filter { it.event.id == "shared-id" }
        assertEquals(1, remaining.size)
        assertEquals(WearDatabase.EVENT_KIND_DECISION, remaining.single().kind)

        db.markEventConfirmed(WearDatabase.EVENT_KIND_DECISION, event.id)
        assertTrue(db.pendingEvents(now = Long.MAX_VALUE).none { it.event.id == "shared-id" })
    }

    @Test
    fun `migration v4 keeps events and adds detection snapshot columns`() {
        val seed = WearDatabase(ApplicationProvider.getApplicationContext<Context>())
        seed.writableDatabase
        seed.close()

        val path = ApplicationProvider
            .getApplicationContext<Context>()
            .getDatabasePath("anxietywatch-wear.db")
        android.database.sqlite.SQLiteDatabase.openDatabase(
            path.path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        ).use { raw ->
            raw.execSQL("PRAGMA user_version = 4")
            raw.execSQL(
                "INSERT INTO events (kind, id, started_at, ended_at, state, trigger_score, rules_version, user_response, sos_status, sync_state, attempts, next_attempt_at, remote_ack) " +
                    "VALUES ('sos', 'legacy-1', 1000, 2000, 'SOS_ACTIVE', 0.9, 'rules-v2', NULL, 'queued_on_watch', 'QUEUED', 0, 0, 0)",
            )
        }

        val reopened = WearDatabase(ApplicationProvider.getApplicationContext<Context>())
        val restored = reopened.pendingEvents(now = Long.MAX_VALUE)
            .first { it.kind == WearDatabase.EVENT_KIND_SOS && it.event.id == "legacy-1" }
            .event
        assertEquals("rules-v2", restored.rulesVersion)
        assertNull(restored.features)
        assertNull(restored.baseline)
        reopened.close()
        db = WearDatabase(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun `migration v3 normalizes lowercase sync states to enum names`() {
        // 1. Crear el esquema actual (v4) con el código de la app.
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

        // 3. Reabrir: v3 normaliza estados y v4 conserva las filas al añadir kind.
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

    @Test
    fun `isTelemetryWindowConfirmed returns false when batches are pending`() {
        val event = PendingEvent(
            startedAtEpochMillis = 100_000L,
            state = MonitoringState.USER_VALIDATION,
            triggerScore = 0.8,
            rulesVersion = "rules-v2",
        )
        db.upsertSuspectedEvent(event)

        // Insert a batch covering the window but not confirmed
        db.upsertBatch(
            StoredBatch(
                batchId = "batch-1",
                fromMillis = 50_000L,
                toMillis = 100_000L,
                state = SyncState.SENT,
                payload = "{}",
                attempts = 1,
                nextAttemptAt = 0L,
                remoteAck = false,
            ),
        )

        assertTrue(!db.isTelemetryWindowConfirmed(event))
    }

    @Test
    fun `isTelemetryWindowConfirmed returns true when all covering batches confirmed`() {
        val event = PendingEvent(
            startedAtEpochMillis = 100_000L,
            state = MonitoringState.USER_VALIDATION,
            triggerScore = 0.8,
            rulesVersion = "rules-v2",
        )
        db.upsertSuspectedEvent(event)

        // Insert a batch covering the window and confirm it
        db.upsertBatch(
            StoredBatch(
                batchId = "batch-1",
                fromMillis = 50_000L,
                toMillis = 100_000L,
                state = SyncState.SENT,
                payload = "{}",
                attempts = 1,
                nextAttemptAt = 0L,
                remoteAck = false,
            ),
        )
        db.markBatchConfirmed("batch-1")

        assertTrue(db.isTelemetryWindowConfirmed(event))
    }

    @Test
    fun `isTelemetryWindowConfirmed returns true when no batches cover window`() {
        val event = PendingEvent(
            startedAtEpochMillis = 100_000L,
            state = MonitoringState.USER_VALIDATION,
            triggerScore = 0.8,
            rulesVersion = "rules-v2",
        )
        db.upsertSuspectedEvent(event)

        // No batches covering the window -> vacuously true (empty window)
        assertTrue(db.isTelemetryWindowConfirmed(event))
    }

    @Test
    fun `isSuspectedEventConfirmed returns false for queued suspected`() {
        val event = PendingEvent(
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.USER_VALIDATION,
            triggerScore = 0.8,
            rulesVersion = "rules-v2",
        )
        db.upsertSuspectedEvent(event)
        assertTrue(!db.isSuspectedEventConfirmed(event.id))
    }

    @Test
    fun `isSuspectedEventConfirmed returns true after markEventConfirmed`() {
        val event = PendingEvent(
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.USER_VALIDATION,
            triggerScore = 0.8,
            rulesVersion = "rules-v2",
        )
        db.upsertSuspectedEvent(event)
        db.markEventConfirmed(WearDatabase.EVENT_KIND_SUSPECTED, event.id)
        assertTrue(db.isSuspectedEventConfirmed(event.id))
    }

    @Test
    fun `isDecisionEventConfirmed returns false for queued decision`() {
        val event = PendingEvent(
            id = "decision-1",
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.INTERVENTION,
            triggerScore = 0.8,
            rulesVersion = "rules-v2",
            userResponse = com.anxietywatch.wear.domain.UserResponse.USER_OK,
            endedAtEpochMillis = 2_000L,
        )
        db.upsertDecisionEvent(event)
        assertTrue(!db.isDecisionEventConfirmed(event.id))
    }

    @Test
    fun `isDecisionEventConfirmed returns true after markEventConfirmed`() {
        val event = PendingEvent(
            id = "decision-2",
            startedAtEpochMillis = 1_000L,
            state = MonitoringState.INTERVENTION,
            triggerScore = 0.8,
            rulesVersion = "rules-v2",
            userResponse = com.anxietywatch.wear.domain.UserResponse.USER_OK,
            endedAtEpochMillis = 2_000L,
        )
        db.upsertDecisionEvent(event)
        db.markEventConfirmed(WearDatabase.EVENT_KIND_DECISION, event.id)
        assertTrue(db.isDecisionEventConfirmed(event.id))
    }
}

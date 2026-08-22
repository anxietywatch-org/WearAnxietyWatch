package com.anxietywatch.wear.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.anxietywatch.wear.domain.BaselineSnapshot
import com.anxietywatch.wear.domain.MonitoringState
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.domain.SensorReading
import com.anxietywatch.wear.domain.UserResponse
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SyncState {
    QUEUED,
    SENT,
    CONFIRMED,
    FAILED,
}

data class StoredTelemetry(
    val id: String,
    val capturedAtEpochMillis: Long,
    val type: String,
    val payload: String,
)

data class StoredBatch(
    val batchId: String,
    val fromMillis: Long,
    val toMillis: Long,
    val state: SyncState,
    val payload: String,
    val attempts: Int,
    val nextAttemptAt: Long,
    val remoteAck: Boolean,
)

data class StoredEventOperation(
    val kind: String,
    val event: PendingEvent,
)

class WearDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE telemetry (
                id TEXT PRIMARY KEY,
                captured_at INTEGER NOT NULL,
                type TEXT NOT NULL,
                payload TEXT NOT NULL,
                sync_state TEXT NOT NULL DEFAULT 'QUEUED',
                attempts INTEGER NOT NULL DEFAULT 0,
                batch_id TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX telemetry_sync_idx ON telemetry(sync_state, captured_at)")
        db.execSQL(
            """
            CREATE TABLE sync_batches (
                batch_id TEXT PRIMARY KEY,
                from_millis INTEGER NOT NULL,
                to_millis INTEGER NOT NULL,
                state TEXT NOT NULL DEFAULT 'SENT',
                payload TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                next_attempt_at INTEGER NOT NULL DEFAULT 0,
                remote_ack INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX batches_idx ON sync_batches(state, next_attempt_at)")
        db.execSQL(
            """
            CREATE TABLE events (
                kind TEXT NOT NULL,
                id TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                state TEXT NOT NULL,
                trigger_score REAL NOT NULL,
                rules_version TEXT NOT NULL,
                user_response TEXT,
                sos_status TEXT,
                sync_state TEXT NOT NULL DEFAULT 'QUEUED',
                attempts INTEGER NOT NULL DEFAULT 0,
                next_attempt_at INTEGER NOT NULL DEFAULT 0,
                remote_ack INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (kind, id)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX events_sync_idx ON events(sync_state, next_attempt_at)")
        db.execSQL(
            """
            CREATE TABLE baseline (
                name TEXT PRIMARY KEY,
                sample_count INTEGER NOT NULL,
                mean_hr REAL NOT NULL,
                m2_hr REAL NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE telemetry ADD COLUMN batch_id TEXT")
            db.execSQL("UPDATE telemetry SET sync_state = 'queued' WHERE sync_state = 'pending'")
            db.execSQL("UPDATE telemetry SET sync_state = 'confirmed' WHERE sync_state = 'synced'")
            db.execSQL(
                """
                CREATE TABLE sync_batches (
                    batch_id TEXT PRIMARY KEY,
                    from_millis INTEGER NOT NULL,
                    to_millis INTEGER NOT NULL,
                    state TEXT NOT NULL DEFAULT 'sent',
                    payload TEXT NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    next_attempt_at INTEGER NOT NULL DEFAULT 0,
                    remote_ack INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX batches_idx ON sync_batches(state, next_attempt_at)")
            db.execSQL(
                "ALTER TABLE events ADD COLUMN sync_state TEXT NOT NULL DEFAULT 'queued'",
            )
            db.execSQL(
                "ALTER TABLE events ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE events ADD COLUMN next_attempt_at INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "ALTER TABLE events ADD COLUMN remote_ack INTEGER NOT NULL DEFAULT 0",
            )
        }
        if (oldVersion < 3) {
            // Normaliza los estados a los nombres del enum SyncState (upper case).
            // Las consultas filtran por SyncState.QUEUED.name etc., por lo que los
            // valores en minúsculas ('pending', 'queued', ...) dejaban la telemetría
            // atascada sin salir nunca del reloj.
            db.execSQL("UPDATE telemetry SET sync_state = 'QUEUED' WHERE sync_state IN ('pending', 'queued')")
            db.execSQL("UPDATE telemetry SET sync_state = 'CONFIRMED' WHERE sync_state IN ('synced', 'confirmed')")
            db.execSQL("UPDATE events SET sync_state = 'QUEUED' WHERE sync_state IN ('pending', 'queued')")
            db.execSQL("UPDATE events SET sync_state = 'CONFIRMED' WHERE sync_state IN ('synced', 'confirmed')")
            db.execSQL("UPDATE sync_batches SET state = 'SENT' WHERE state IN ('sent', 'queued')")
            db.execSQL("UPDATE sync_batches SET state = 'CONFIRMED' WHERE state = 'confirmed'")
            db.execSQL("UPDATE sync_batches SET state = 'FAILED' WHERE state = 'failed'")
        }
        if (oldVersion < 4) {
            db.execSQL(
                """
                CREATE TABLE events_v4 (
                    kind TEXT NOT NULL,
                    id TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER,
                    state TEXT NOT NULL,
                    trigger_score REAL NOT NULL,
                    rules_version TEXT NOT NULL,
                    user_response TEXT,
                    sos_status TEXT,
                    sync_state TEXT NOT NULL DEFAULT 'QUEUED',
                    attempts INTEGER NOT NULL DEFAULT 0,
                    next_attempt_at INTEGER NOT NULL DEFAULT 0,
                    remote_ack INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (kind, id)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO events_v4 (
                    kind, id, started_at, ended_at, state, trigger_score,
                    rules_version, user_response, sos_status, sync_state,
                    attempts, next_attempt_at, remote_ack
                )
                SELECT
                    CASE WHEN user_response = 'SOS_CANCELLED' THEN 'sos-cancel' ELSE 'sos' END,
                    id, started_at, ended_at, state, trigger_score,
                    rules_version, user_response, sos_status, sync_state,
                    attempts, next_attempt_at, remote_ack
                FROM events
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE events")
            db.execSQL("ALTER TABLE events_v4 RENAME TO events")
            db.execSQL("CREATE INDEX events_sync_idx ON events(sync_state, next_attempt_at)")
        }
    }

    @Synchronized
    fun insertReading(reading: SensorReading): String {
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("captured_at", reading.capturedAtEpochMillis)
            put("type", reading.typeName())
            put("payload", reading.toJson().toString())
            put("sync_state", SyncState.QUEUED.name)
        }
        writableDatabase.insertOrThrow("telemetry", null, values)
        trimTelemetry()
        return id
    }

    @Synchronized
    fun clearBaseline() {
        writableDatabase.delete("baseline", "name = ?", arrayOf(BASELINE_NAME))
    }

    @Synchronized
    fun pendingTelemetry(limit: Int = 50): List<StoredTelemetry> {
        val safeLimit = limit.coerceIn(1, 100)
        readableDatabase.query(
            "telemetry",
            arrayOf("id", "captured_at", "type", "payload"),
            "sync_state IN (?, ?)",
            arrayOf(SyncState.QUEUED.name, SyncState.FAILED.name),
            null,
            null,
            "captured_at ASC",
            safeLimit.toString(),
        ).use { cursor ->
            val rows = mutableListOf<StoredTelemetry>()
            while (cursor.moveToNext()) {
                rows += StoredTelemetry(
                    id = cursor.getString(0),
                    capturedAtEpochMillis = cursor.getLong(1),
                    type = cursor.getString(2),
                    payload = cursor.getString(3),
                )
            }
            return rows
        }
    }

    @Synchronized
    fun markTelemetrySent(ids: List<String>, batchId: String) {
        if (ids.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            ids.forEach { id ->
                val values = ContentValues().apply {
                    put("sync_state", SyncState.SENT.name)
                    put("batch_id", batchId)
                }
                writableDatabase.update("telemetry", values, "id = ?", arrayOf(id))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    @Synchronized
    fun markTelemetryConfirmedByBatch(batchId: String) {
        val values = ContentValues().apply { put("sync_state", SyncState.CONFIRMED.name) }
        writableDatabase.update("telemetry", values, "batch_id = ?", arrayOf(batchId))
    }

    @Synchronized
    fun markTelemetryFailed(ids: List<String>) {
        if (ids.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            ids.forEach { id ->
                val values = ContentValues().apply { put("sync_state", SyncState.FAILED.name) }
                writableDatabase.update("telemetry", values, "id = ?", arrayOf(id))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    @Synchronized
    fun upsertBatch(batch: StoredBatch) {
        val values = ContentValues().apply {
            put("batch_id", batch.batchId)
            put("from_millis", batch.fromMillis)
            put("to_millis", batch.toMillis)
            put("state", batch.state.name)
            put("payload", batch.payload)
            put("attempts", batch.attempts)
            put("next_attempt_at", batch.nextAttemptAt)
            put("remote_ack", if (batch.remoteAck) 1 else 0)
        }
        writableDatabase.insertWithOnConflict(
            "sync_batches",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun pendingBatches(now: Long): List<StoredBatch> = readableDatabase.query(
        "sync_batches",
        arrayOf("batch_id", "from_millis", "to_millis", "state", "payload", "attempts", "next_attempt_at", "remote_ack"),
        "state IN (?, ?) AND next_attempt_at <= ?",
        arrayOf(SyncState.SENT.name, SyncState.FAILED.name, now.toString()),
        null,
        null,
        "next_attempt_at ASC",
    ).use { cursor ->
        val rows = mutableListOf<StoredBatch>()
        while (cursor.moveToNext()) {
            rows += StoredBatch(
                batchId = cursor.getString(0),
                fromMillis = cursor.getLong(1),
                toMillis = cursor.getLong(2),
                state = SyncState.valueOf(cursor.getString(3)),
                payload = cursor.getString(4),
                attempts = cursor.getInt(5),
                nextAttemptAt = cursor.getLong(6),
                remoteAck = cursor.getInt(7) == 1,
            )
        }
        rows
    }

    @Synchronized
    fun markBatchConfirmed(batchId: String) {
        val values = ContentValues().apply {
            put("state", SyncState.CONFIRMED.name)
            put("remote_ack", 1)
        }
        writableDatabase.update("sync_batches", values, "batch_id = ?", arrayOf(batchId))
    }

    @Synchronized
    fun markBatchFailed(batchId: String, attempts: Int, nextAttemptAt: Long) {
        val values = ContentValues().apply {
            put("state", SyncState.FAILED.name)
            put("attempts", attempts)
            put("next_attempt_at", nextAttemptAt)
        }
        writableDatabase.update("sync_batches", values, "batch_id = ?", arrayOf(batchId))
    }

    @Synchronized
    fun pendingCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM telemetry WHERE sync_state IN (?, ?)",
        arrayOf(SyncState.QUEUED.name, SyncState.FAILED.name),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    @Synchronized
    fun upsertEvent(event: PendingEvent) {
        val kind = eventKind(event)
        val existing = eventSyncState(kind, event.id)
        val values = ContentValues().apply {
            put("kind", kind)
            put("id", event.id)
            put("started_at", event.startedAtEpochMillis)
            event.endedAtEpochMillis?.let { put("ended_at", it) }
            put("state", event.state.name)
            put("trigger_score", event.triggerScore)
            put("rules_version", event.rulesVersion)
            event.userResponse?.let { put("user_response", it.name) }
            event.sosStatus?.let { put("sos_status", it) }
            put("sync_state", existing?.first ?: SyncState.QUEUED.name)
            put("attempts", existing?.second ?: 0)
            put("next_attempt_at", existing?.third ?: 0L)
        }
        writableDatabase.insertWithOnConflict(
            "events",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    private fun eventSyncState(kind: String, eventId: String): Triple<String, Int, Long>? =
        readableDatabase.query(
            "events",
            arrayOf("sync_state", "attempts", "next_attempt_at"),
            "kind = ? AND id = ?",
            arrayOf(kind, eventId),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                Triple(cursor.getString(0), cursor.getInt(1), cursor.getLong(2))
            } else {
                null
            }
        }

    @Synchronized
    fun pendingEvents(now: Long): List<StoredEventOperation> = readableDatabase.query(
        "events",
        arrayOf(
            "kind", "id", "started_at", "ended_at", "state", "trigger_score",
            "rules_version", "user_response", "sos_status", "attempts", "next_attempt_at",
        ),
        "sync_state IN (?, ?) AND next_attempt_at <= ?",
        arrayOf(SyncState.QUEUED.name, SyncState.FAILED.name, now.toString()),
        null,
        null,
        "started_at ASC",
    ).use { cursor ->
        val rows = mutableListOf<StoredEventOperation>()
        while (cursor.moveToNext()) {
            rows += StoredEventOperation(
                kind = cursor.getString(0),
                event = PendingEvent(
                    id = cursor.getString(1),
                    startedAtEpochMillis = cursor.getLong(2),
                    endedAtEpochMillis = cursor.getLong(3).takeIf { !cursor.isNull(3) },
                    state = MonitoringState.valueOf(cursor.getString(4)),
                    triggerScore = cursor.getDouble(5),
                    rulesVersion = cursor.getString(6),
                    userResponse = cursor.getString(7)?.let { UserResponse.valueOf(it) },
                    sosStatus = cursor.getString(8),
                    attempts = cursor.getInt(9),
                    nextAttemptAt = cursor.getLong(10),
                ),
            )
        }
        rows
    }

    @Synchronized
    fun markEventSent(kind: String, eventId: String, attempts: Int, nextAttemptAt: Long) {
        val values = ContentValues().apply {
            put("sync_state", SyncState.SENT.name)
            put("attempts", attempts)
            put("next_attempt_at", nextAttemptAt)
        }
        writableDatabase.update("events", values, "kind = ? AND id = ?", arrayOf(kind, eventId))
    }

    @Synchronized
    fun markEventConfirmed(kind: String, eventId: String) {
        val values = ContentValues().apply {
            put("sync_state", SyncState.CONFIRMED.name)
            put("remote_ack", 1)
        }
        writableDatabase.update("events", values, "kind = ? AND id = ?", arrayOf(kind, eventId))
    }

    @Synchronized
    fun markEventFailed(kind: String, eventId: String, attempts: Int, nextAttemptAt: Long) {
        val values = ContentValues().apply {
            put("sync_state", SyncState.FAILED.name)
            put("attempts", attempts)
            put("next_attempt_at", nextAttemptAt)
        }
        writableDatabase.update("events", values, "kind = ? AND id = ?", arrayOf(kind, eventId))
    }

    @Synchronized
    fun loadBaseline(): BaselineSnapshot = readableDatabase.query(
        "baseline",
        arrayOf("sample_count", "mean_hr", "m2_hr", "updated_at"),
        "name = ?",
        arrayOf(BASELINE_NAME),
        null,
        null,
        null,
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use BaselineSnapshot.empty()
        BaselineSnapshot(
            sampleCount = cursor.getLong(0),
            meanHeartRate = cursor.getDouble(1),
            heartRateM2 = cursor.getDouble(2),
            updatedAtEpochMillis = cursor.getLong(3),
        )
    }

    @Synchronized
    fun saveBaseline(baseline: BaselineSnapshot) {
        val values = ContentValues().apply {
            put("name", BASELINE_NAME)
            put("sample_count", baseline.sampleCount)
            put("mean_hr", baseline.meanHeartRate)
            put("m2_hr", baseline.heartRateM2)
            put("updated_at", baseline.updatedAtEpochMillis)
        }
        writableDatabase.insertWithOnConflict(
            "baseline",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun setting(key: String): String? = readableDatabase.query(
        "settings",
        arrayOf("value"),
        "key = ?",
        arrayOf(key),
        null,
        null,
        null,
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    @Synchronized
    fun saveSetting(key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        writableDatabase.insertWithOnConflict(
            "settings",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun trimTelemetry() {
        writableDatabase.delete(
            "telemetry",
            "sync_state = ? AND captured_at < ?",
            arrayOf(SyncState.CONFIRMED.name, (System.currentTimeMillis() - SEVEN_DAYS_MILLIS).toString()),
        )
        writableDatabase.execSQL(
            """
            DELETE FROM telemetry
            WHERE id IN (
                SELECT id FROM telemetry
                WHERE sync_state = ?
                ORDER BY captured_at ASC
                LIMIT MAX(0, (SELECT COUNT(*) FROM telemetry) - $MAX_ROWS)
            )
            """.trimIndent(),
            arrayOf(SyncState.CONFIRMED.name),
        )
    }

    companion object {
        private const val DATABASE_NAME = "anxietywatch-wear.db"
        const val EVENT_KIND_SOS = "sos"
        const val EVENT_KIND_SOS_CANCEL = "sos-cancel"
        private const val DATABASE_VERSION = 4
        private const val BASELINE_NAME = "heart-rate-v1"
        private const val MAX_ROWS = 10_000
        private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }

    private fun eventKind(event: PendingEvent): String =
        if (event.userResponse == UserResponse.SOS_CANCELLED) EVENT_KIND_SOS_CANCEL else EVENT_KIND_SOS
}

private fun SensorReading.typeName(): String = when (this) {
    is SensorReading.HeartRate -> "heart_rate"
    is SensorReading.Motion -> "motion"
    is SensorReading.Steps -> "steps"
    is SensorReading.SkinTemperature -> "skin_temperature"
    is SensorReading.Availability -> "availability"
}

private fun SensorReading.toJson(): JSONObject = when (this) {
    is SensorReading.HeartRate -> JSONObject()
        .put("bpm", bpm)
        .put("ibiMillis", ibiMillis?.let { JSONArray(it) } ?: JSONObject.NULL)
        .put("signalQuality", signalQuality)
        .put("source", source)
    is SensorReading.Motion -> JSONObject()
        .put("magnitudeG", magnitudeG)
        .put("variance", variance)
        .put("source", source)
    is SensorReading.Steps -> JSONObject()
        .put("dailyTotal", dailyTotal)
        .put("source", source)
    is SensorReading.SkinTemperature -> JSONObject()
        .put("celsius", celsius)
        .put("source", source)
    is SensorReading.Availability -> JSONObject()
        .put("sensor", kind.name)
        .put("status", status.name)
        .put("reason", reason)
        .put("source", source)
}

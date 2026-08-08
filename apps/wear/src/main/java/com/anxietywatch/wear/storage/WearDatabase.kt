package com.anxietywatch.wear.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.anxietywatch.wear.domain.BaselineSnapshot
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.domain.SensorReading
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StoredTelemetry(
    val id: String,
    val capturedAtEpochMillis: Long,
    val type: String,
    val payload: String,
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
                sync_state TEXT NOT NULL DEFAULT 'pending',
                attempts INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX telemetry_sync_idx ON telemetry(sync_state, captured_at)")
        db.execSQL(
            """
            CREATE TABLE events (
                id TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                state TEXT NOT NULL,
                trigger_score REAL NOT NULL,
                rules_version TEXT NOT NULL,
                user_response TEXT,
                sos_status TEXT
            )
            """.trimIndent(),
        )
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun insertReading(reading: SensorReading): String {
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("captured_at", reading.capturedAtEpochMillis)
            put("type", reading.typeName())
            put("payload", reading.toJson().toString())
            put("sync_state", "pending")
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
            "sync_state = ?",
            arrayOf("pending"),
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
    fun markTelemetrySynced(ids: List<String>) {
        if (ids.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            ids.forEach { id ->
                val values = ContentValues().apply { put("sync_state", "synced") }
                writableDatabase.update("telemetry", values, "id = ?", arrayOf(id))
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    @Synchronized
    fun pendingCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM telemetry WHERE sync_state = 'pending'",
        null,
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    @Synchronized
    fun upsertEvent(event: PendingEvent) {
        val values = ContentValues().apply {
            put("id", event.id)
            put("started_at", event.startedAtEpochMillis)
            event.endedAtEpochMillis?.let { put("ended_at", it) }
            put("state", event.state.name)
            put("trigger_score", event.triggerScore)
            put("rules_version", event.rulesVersion)
            event.userResponse?.let { put("user_response", it.name) }
            event.sosStatus?.let { put("sos_status", it) }
        }
        writableDatabase.insertWithOnConflict(
            "events",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
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
            "sync_state = 'synced' AND captured_at < ?",
            arrayOf((System.currentTimeMillis() - SEVEN_DAYS_MILLIS).toString()),
        )
        writableDatabase.execSQL(
            """
            DELETE FROM telemetry
            WHERE id IN (
                SELECT id FROM telemetry
                WHERE sync_state = 'synced'
                ORDER BY captured_at ASC
                LIMIT MAX(0, (SELECT COUNT(*) FROM telemetry) - $MAX_ROWS)
            )
            """.trimIndent(),
        )
    }

    companion object {
        private const val DATABASE_NAME = "anxietywatch-wear.db"
        private const val DATABASE_VERSION = 1
        private const val BASELINE_NAME = "heart-rate-v1"
        private const val MAX_ROWS = 10_000
        private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
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

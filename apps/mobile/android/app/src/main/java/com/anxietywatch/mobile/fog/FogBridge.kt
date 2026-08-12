package com.anxietywatch.mobile.fog

import android.content.Context
import com.anxietywatch.mobile.fog.room.FogDatabase
import com.anxietywatch.mobile.fog.room.FogOutboxEntry
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Buffer compartido entre el servicio de escucha y el módulo React Native,
 * persistido en SQLite (Room) en lugar de SharedPreferences.
 *
 * El servicio (proceso del sistema) recibe sobres del reloj aunque la app no
 * esté abierta; quedan encolados de forma atómica por fila y JavaScript los
 * recolecta al arrancar o al recibir el evento de despertar.
 *
 * Cada entrada sigue el ciclo: PENDING -> CLOUD_ACKED -> WATCH_ACKED (se
 * elimina). El borrado solo ocurre cuando el reloj ya confirmó el sobre.
 */
object FogBridge {

    private const val RETENTION_DAYS = 7L

    private var emitListener: ((String) -> Unit)? = null

    fun setEmitListener(listener: ((String) -> Unit)?) {
        emitListener = listener
    }

    fun isAppHosted(context: Context): Boolean = emitListener != null

    @Synchronized
    fun enqueueInbound(context: Context, kind: String, key: String, envelope: String) {
        val dao = FogDatabase.get(context).fogOutboxDao()
        cleanupRetained(context)
        // UNIQUE(kind, entity_id) + IGNORE: deduplica sin sobrescribir el
        // estado de confirmación si el sobre ya fue procesado.
        dao.insert(FogOutboxEntry(kind = kind, entityId = key, payload = envelope))
        emitListener?.invoke(envelope)
    }

    @Synchronized
    fun peekInbound(context: Context): String {
        val entries = FogDatabase.get(context).fogOutboxDao().pending()
        val out = JSONArray()
        for (entry in entries) {
            out.put(
                JSONObject()
                    .put("kind", entry.kind)
                    .put("key", entry.key)
                    .put("envelope", entry.payload)
                    .put("receivedAt", entry.receivedAt)
                    .put("state", entry.state),
            )
        }
        return out.toString()
    }

    @Synchronized
    fun markCloudAcked(context: Context, key: String) {
        val (kind, entityId) = splitKey(key) ?: return
        FogDatabase.get(context).fogOutboxDao()
            .markCloudAcked(kind, entityId, System.currentTimeMillis())
    }

    @Synchronized
    fun markWatchAcked(context: Context, key: String) {
        val (kind, entityId) = splitKey(key) ?: return
        FogDatabase.get(context).fogOutboxDao()
            .markWatchAcked(kind, entityId, System.currentTimeMillis())
    }

    /**
     * Solo elimina la entrada si el reloj ya confirmó (estado WATCH_ACKED).
     * Si la confirmación falló, devuelve false y la entrada queda pendiente
     * para reintentar.
     */
    @Synchronized
    fun completeInbound(context: Context, key: String): Boolean {
        val (kind, entityId) = splitKey(key) ?: return false
        val deleted = FogDatabase.get(context).fogOutboxDao()
            .completeOnlyIfWatchAcked(kind, entityId)
        return deleted > 0
    }

    @Synchronized
    fun inboundCount(context: Context): Int =
        FogDatabase.get(context).fogOutboxDao().countPending()

    @Synchronized
    fun cleanupRetained(context: Context) {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24 * 60 * 60 * 1000
        FogDatabase.get(context).fogOutboxDao().cleanupAcked(cutoff)
    }

    private fun splitKey(key: String): Pair<String, String>? {
        val separator = key.indexOf(':')
        if (separator <= 0 || separator >= key.length - 1) return null
        return key.substring(0, separator) to key.substring(separator + 1)
    }
}

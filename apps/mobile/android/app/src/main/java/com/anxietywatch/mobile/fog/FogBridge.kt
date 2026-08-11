package com.anxietywatch.mobile.fog

import android.content.Context
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Buffer compartido entre el servicio de escucha y el módulo React Native.
 *
 * El servicio (proceso del sistema) recibe sobres del reloj aunque la app no
 * esté abierta. Aquí se persisten en SharedPreferences para que JavaScript los
 * recoja al arrancar o al emitir un evento de despertar.
 */
object FogBridge {

    private const val PREFS = "fog_inbound"
    private const val KEY_QUEUE = "inbound_queue"

    private var emitListener: ((String) -> Unit)? = null

    fun setEmitListener(listener: ((String) -> Unit)?) {
        emitListener = listener
    }

    fun isAppHosted(context: Context): Boolean = emitListener != null

    @Synchronized
    fun enqueueInbound(context: Context, kind: String, key: String, envelope: String) {
        val queue = loadQueue(context)
        if (queueEntries(queue).any { it.optString("key") == key && it.optString("kind") == kind }) {
            return
        }
        val entry = JSONObject()
            .put("kind", kind)
            .put("key", key)
            .put("envelope", envelope)
            .put("receivedAt", System.currentTimeMillis())
        queue.put(entry)
        saveQueue(context, queue)
        emitListener?.invoke(envelope)
    }

    @Synchronized
    fun peekInbound(context: Context): String = loadQueue(context).toString()

    @Synchronized
    fun completeInbound(context: Context, key: String) {
        val queue = loadQueue(context)
        val updated = JSONArray()
        for (entry in queueEntries(queue)) {
            if (entry.optString("key") != key) {
                updated.put(entry)
            }
        }
        saveQueue(context, updated)
    }

    @Synchronized
    fun inboundCount(context: Context): Int = loadQueue(context).length()

    private fun queueEntries(queue: JSONArray): List<JSONObject> =
        (0 until queue.length()).map { queue.optJSONObject(it) }.filterNotNull()

    private fun loadQueue(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return runCatching {
            JSONArray(prefs.getString(KEY_QUEUE, "[]"))
        }.getOrDefault(JSONArray())
    }

    private fun saveQueue(context: Context, queue: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE, queue.toString())
            .apply()
    }
}

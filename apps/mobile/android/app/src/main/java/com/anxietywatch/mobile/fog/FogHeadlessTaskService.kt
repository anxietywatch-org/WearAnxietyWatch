package com.anxietywatch.mobile.fog

import android.content.Context
import android.content.Intent
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.jstasks.HeadlessJsTaskConfig

/**
 * Ejecuta una sincronización corta cuando Wear Data Layer entrega un sobre y
 * la interfaz React Native no está abierta. La cola Room mantiene el trabajo
 * pendiente si todavía no hay una sesión válida o la red falla.
 */
class FogHeadlessTaskService : HeadlessJsTaskService() {
    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig =
        HeadlessJsTaskConfig(TASK_NAME, null, 30_000L, true)

    companion object {
        const val TASK_NAME = "AnxietyWatchFogSync"

        fun start(context: Context) {
            val intent = Intent(context, FogHeadlessTaskService::class.java)
            runCatching {
                context.startService(intent)
                acquireWakeLockNow(context)
            }
        }
    }
}

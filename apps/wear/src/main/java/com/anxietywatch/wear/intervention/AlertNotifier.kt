package com.anxietywatch.wear.intervention

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.anxietywatch.wear.R
import com.anxietywatch.wear.presentation.MainActivity

class AlertNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Cambios fisiológicos",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Avisos de bienestar que requieren una respuesta en el reloj."
                enableVibration(true)
            },
        )
    }

    fun showPossibleEvent() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            100,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Detectamos cambios inusuales")
            .setContentText("Toca para indicar cómo te encuentras.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(EVENT_NOTIFICATION_ID, notification)
    }

    fun clearPossibleEvent() = manager.cancel(EVENT_NOTIFICATION_ID)

    companion object {
        private const val CHANNEL_ID = "anxietywatch-events"
        private const val EVENT_NOTIFICATION_ID = 701
    }
}

package com.anxietywatch.wear.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object PermissionPolicy {
    const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"
    const val READ_HEALTH_DATA_IN_BACKGROUND =
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    fun foregroundPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= 36) {
            add(READ_HEART_RATE)
        } else {
            add(Manifest.permission.BODY_SENSORS)
        }
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun backgroundPermission(): String? = when {
        Build.VERSION.SDK_INT >= 36 -> READ_HEALTH_DATA_IN_BACKGROUND
        Build.VERSION.SDK_INT >= 33 -> Manifest.permission.BODY_SENSORS_BACKGROUND
        else -> null
    }

    fun hasForegroundHeartRate(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 36) READ_HEART_RATE else Manifest.permission.BODY_SENSORS
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundHeartRate(context: Context): Boolean {
        if (!hasForegroundHeartRate(context)) return false
        val permission = backgroundPermission() ?: return true
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasActivityRecognition(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
}

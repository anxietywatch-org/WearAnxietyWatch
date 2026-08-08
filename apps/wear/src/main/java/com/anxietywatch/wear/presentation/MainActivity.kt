package com.anxietywatch.wear.presentation

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.anxietywatch.wear.AnxietyWatchApplication
import com.anxietywatch.wear.presentation.theme.AnxietyWatchTheme
import com.anxietywatch.wear.sensor.PermissionPolicy

class MainActivity : ComponentActivity() {
    private val runtime
        get() = (application as AnxietyWatchApplication).runtime

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        runtime.onPermissionsResult()
    }

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val requiredGranted = results
            .filterKeys { permission -> permission != android.Manifest.permission.POST_NOTIFICATIONS }
            .values
            .all { it }
        val backgroundPermission = PermissionPolicy.backgroundPermission()
        if (
            requiredGranted &&
            backgroundPermission != null &&
            checkSelfPermission(backgroundPermission) != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundPermissionLauncher.launch(backgroundPermission)
        } else {
            runtime.onPermissionsResult()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnxietyWatchTheme {
                AnxietyWatchApp(
                    runtime = runtime,
                    requestPermissions = {
                        foregroundPermissionLauncher.launch(PermissionPolicy.foregroundPermissions())
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runtime.startForegroundMonitoring()
    }

    override fun onStop() {
        runtime.stopForegroundMonitoring()
        super.onStop()
    }
}

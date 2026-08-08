package com.anxietywatch.wear.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

@Composable
fun AnxietyWatchTheme(
    content: @Composable () -> Unit
) {
    /**
     * Tema base que concentra la personalización visual de la aplicación.
     * Consulta: https://developer.android.com/jetpack/compose/designsystems/custom
     */
    MaterialTheme(
        content = content
    )
}

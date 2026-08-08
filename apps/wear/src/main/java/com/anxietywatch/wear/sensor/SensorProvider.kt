package com.anxietywatch.wear.sensor

import com.anxietywatch.wear.domain.DeviceCapabilities
import com.anxietywatch.wear.domain.SensorReading
import kotlinx.coroutines.flow.Flow

interface SensorProvider {
    val providerName: String

    suspend fun capabilities(): DeviceCapabilities

    fun readings(): Flow<SensorReading>
}

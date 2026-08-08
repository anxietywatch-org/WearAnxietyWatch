package com.anxietywatch.wear.datalayer

import android.content.Context
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.storage.WearDatabase
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine

class WatchDataClient(
    context: Context,
    private val database: WearDatabase,
    private val connectionObserver: PhoneConnectionObserver,
) {
    private val appContext = context.applicationContext

    suspend fun syncPendingBatch(): Int = withContext(Dispatchers.IO) {
        if (!connectionObserver.isConnected()) return@withContext 0
        val rows = database.pendingTelemetry(50)
        if (rows.isEmpty()) return@withContext 0
        val batchId = UUID.randomUUID().toString()
        val payload = BackendEndpointContract.telemetryEnvelope(batchId, rows).toString()
            .toByteArray(StandardCharsets.UTF_8)
        if (payload.size > MAX_DATA_ITEM_BYTES) return@withContext 0
        val request = PutDataRequest.create(BackendEndpointContract.telemetryPath(batchId))
            .setData(payload)
            .setUrgent()
        val accepted = suspendCancellableCoroutine { continuation ->
            Wearable.getDataClient(appContext).putDataItem(request)
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(true)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(false)
                }
        }
        if (accepted) {
            database.markTelemetrySynced(rows.map { it.id })
            rows.size
        } else {
            0
        }
    }

    suspend fun sendImmediateEvent(event: PendingEvent): Boolean {
        val nodes = connectionObserver.connectedNodes()
        if (nodes.isEmpty()) return false
        val bytes = BackendEndpointContract.sosEnvelope(event)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        return nodes.any { node ->
            sendMessage(node.id, BackendEndpointContract.SOS_TRIGGER_ENDPOINT, bytes)
        }
    }

    private suspend fun sendMessage(nodeId: String, path: String, bytes: ByteArray): Boolean =
        suspendCancellableCoroutine { continuation ->
            Wearable.getMessageClient(appContext).sendMessage(nodeId, path, bytes)
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(true)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(false)
                }
        }

    companion object {
        private const val MAX_DATA_ITEM_BYTES = 95_000
    }
}

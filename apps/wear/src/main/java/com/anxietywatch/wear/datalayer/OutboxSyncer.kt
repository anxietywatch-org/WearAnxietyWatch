package com.anxietywatch.wear.datalayer

import android.content.Context
import com.anxietywatch.wear.domain.PendingEvent
import com.anxietywatch.wear.storage.StoredBatch
import com.anxietywatch.wear.storage.SyncState
import com.anxietywatch.wear.storage.WearDatabase
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

/**
 * Cola de salida con reintentos y confirmación por el teléfono.
 *
 * El reloj no realiza HTTP: entrega lotes de telemetría mediante DataClient en
 * `/fog/v1/telemetry/{batchId}` y eventos SOS (incluida su cancelación) mediante
 * MessageClient en `/fog/v1/sos/{eventId}` y `/fog/v1/sos/cancel/{eventId}`.
 * El teléfono (nodo fog) enriquece el payload con la identidad autenticada y
 * llama al API. Cuando el teléfono confirma la entrega responde ACK por
 * identificador (`/fog/v1/ack/telemetry/{batchId}`, ...) y el estado pasa a CONFIRMED.
 */
class OutboxSyncer(
    private val context: Context,
    private val database: WearDatabase,
    private val connectionObserver: PhoneConnectionObserver,
    private val scope: CoroutineScope,
) {
    private val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var job: Job? = null
    private val announcedNodes = mutableSetOf<String>()

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                trigger.first()
                runSyncCycle()
            }
        }
    }

    fun requestSync() {
        trigger.tryEmit(Unit)
    }

    suspend fun dispatchEventNow(event: PendingEvent) {
        database.upsertEvent(event)
        requestSync()
    }

    fun handleAck(batchId: String? = null, eventId: String? = null, sosCancelEventId: String? = null) {
        scope.launch {
            batchId?.takeIf { it.isNotEmpty() }?.let { id ->
                database.markBatchConfirmed(id)
                database.markTelemetryConfirmedByBatch(id)
            }
            eventId?.takeIf { it.isNotEmpty() }?.let { database.markEventConfirmed(it) }
            sosCancelEventId?.takeIf { it.isNotEmpty() }?.let { database.markEventConfirmed(it) }
            requestSync()
        }
    }

    private suspend fun runSyncCycle() {
        val node = connectedNode() ?: return
        announceIfNeeded(node)
        if (sendPendingEvents(node)) return
        if (sendOutstandingBatches(node)) return
        if (sendNewTelemetryBatches(node)) return
    }

    /**
     * Anuncia una sola vez por nodo que este reloj habla el protocolo fog
     * `fog_watch_v1` (sobre `/fog/v1/capabilities`).
     */
    private suspend fun announceIfNeeded(node: Node) {
        if (node.id in announcedNodes) return
        val envelope = BackendEndpointContract
            .capabilitiesEnvelope(android.os.Build.MODEL, android.os.Build.VERSION.RELEASE)
            .toString()
            .toByteArray(Charsets.UTF_8)
        try {
            dataClient.putDataItem(urgentItem(BackendEndpointContract.CAPABILITIES_ENDPOINT, envelope)).awaitResult()
            announcedNodes.add(node.id)
        } catch (_: Exception) {
        }
    }

    private suspend fun connectedNode(): Node? {
        return try {
            Wearable.getNodeClient(context).connectedNodes.awaitResult().firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun sendPendingEvents(node: Node): Boolean {
        val now = System.currentTimeMillis()
        val pending = database.pendingEvents(now)
        for (event in pending) {
            val cancelled = event.userResponse == com.anxietywatch.wear.domain.UserResponse.SOS_CANCELLED
            val route = if (cancelled) {
                BackendEndpointContract.sosCancelPath(event.id)
            } else {
                BackendEndpointContract.sosPath(event.id)
            }
            val bytes = (
                if (cancelled) {
                    BackendEndpointContract.sosCancelEnvelope(event)
                } else {
                    BackendEndpointContract.sosEnvelope(event)
                }
                ).toString().toByteArray(Charsets.UTF_8)
            try {
                messageClient.sendMessage(node.id, route, bytes).awaitResult()
                val attempt = event.attempts + 1
                database.markEventSent(event.id, attempt, now + backoffMillis(attempt))
            } catch (e: Exception) {
                val attempt = event.attempts + 1
                database.markEventFailed(event.id, attempt, now + backoffMillis(attempt))
            }
        }
        return database.pendingEvents(now).isNotEmpty()
    }

    private suspend fun sendOutstandingBatches(node: Node): Boolean {
        val now = System.currentTimeMillis()
        val outstanding = database.pendingBatches(now)
        for (batch in outstanding) {
            val route = BackendEndpointContract.telemetryPath(batch.batchId)
            try {
                dataClient.putDataItem(urgentItem(route, batch.payload.toByteArray())).awaitResult()
                val attempt = batch.attempts + 1
                database.upsertBatch(
                    batch.copy(
                        state = SyncState.SENT,
                        attempts = attempt,
                        nextAttemptAt = now + backoffMillis(attempt),
                    )
                )
            } catch (e: Exception) {
                val attempt = batch.attempts + 1
                database.markBatchFailed(batch.batchId, attempt, now + backoffMillis(attempt))
            }
        }
        return database.pendingBatches(now).isNotEmpty()
    }

    private suspend fun sendNewTelemetryBatches(node: Node): Boolean {
        val pending = database.pendingTelemetry(MAX_TELEMETRY_PER_BATCH)
        if (pending.isEmpty()) return false
        val batchId = UUID.randomUUID().toString()
        val payload = BackendEndpointContract.telemetryEnvelope(batchId, pending).toString()
        if (payload.toByteArray().size > MAX_BATCH_BYTES) {
            database.markTelemetryFailed(pending.map { it.id })
            return false
        }
        val route = BackendEndpointContract.telemetryPath(batchId)
        try {
            dataClient.putDataItem(urgentItem(route, payload.toByteArray())).awaitResult()
            val now = System.currentTimeMillis()
            database.upsertBatch(
                StoredBatch(
                    batchId = batchId,
                    fromMillis = pending.first().capturedAtEpochMillis,
                    toMillis = pending.last().capturedAtEpochMillis,
                    state = SyncState.SENT,
                    payload = payload,
                    attempts = 1,
                    nextAttemptAt = now + backoffMillis(1),
                    remoteAck = false,
                )
            )
            database.markTelemetrySent(pending.map { it.id }, batchId)
        } catch (e: Exception) {
            database.markTelemetryFailed(pending.map { it.id })
        }
        return true
    }

    private fun urgentItem(route: String, payload: ByteArray) =
        PutDataMapRequest.create(route)
            .apply { dataMap.putByteArray("payload", payload) }
            .asPutDataRequest()
            .setUrgent()

    private fun backoffMillis(attempt: Int): Long {
        val base = BASE_BACKOFF_MILLIS * (1L shl attempt.coerceAtMost(5))
        val jitter = Random.nextLong(0, 10_000)
        return minOf(base + jitter, MAX_BACKOFF_MILLIS)
    }

    companion object {
        private const val MAX_TELEMETRY_PER_BATCH = 50
        private const val MAX_BATCH_BYTES = 95_000
        private const val BASE_BACKOFF_MILLIS = 30_000L
        private const val MAX_BACKOFF_MILLIS = 15 * 60_000L
    }
}

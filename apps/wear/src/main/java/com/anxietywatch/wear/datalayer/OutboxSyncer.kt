package com.anxietywatch.wear.datalayer

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

/**
 * Cola de salida con reintentos y confirmación por el teléfono.
 *
 * El reloj no realiza HTTP: entrega lotes de telemetría mediante DataClient en
 * `/fog/v1/telemetry/{batchId}` y eventos (SOS y su cancelación, detecciones
 * sospechadas y decisiones del usuario) mediante MessageClient en las rutas
 * `/fog/v1/sos/{eventId}`, `/fog/v1/sos/cancel/{eventId}`,
 * `/fog/v1/events/suspected/{eventId}` y `/fog/v1/events/decision/{eventId}`.
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
    private val trigger = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private val announcedNodes = mutableSetOf<String>()

    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (true) {
                trigger.receive()
                runSyncCycle()
            }
        }
    }

    fun requestSync() {
        trigger.trySend(Unit)
    }

suspend fun dispatchEventNow(event: PendingEvent) {
        database.upsertSosEvent(event)
        requestSync()
    }

    fun handleAck(
        batchId: String? = null,
        eventId: String? = null,
        sosCancelEventId: String? = null,
        suspectedEventId: String? = null,
        decisionEventId: String? = null,
    ) {
        scope.launch {
            batchId?.takeIf { it.isNotEmpty() }?.let { id ->
                database.markBatchConfirmed(id)
                database.markTelemetryConfirmedByBatch(id)
                runCatching {
                    dataClient.deleteDataItems(
                        Uri.parse("wear://*" + BackendEndpointContract.telemetryPath(id)),
                        DataClient.FILTER_LITERAL,
                    ).awaitResult()
                }
            }
            eventId?.takeIf { it.isNotEmpty() }?.let {
                database.markEventConfirmed(WearDatabase.EVENT_KIND_SOS, it)
            }
            sosCancelEventId?.takeIf { it.isNotEmpty() }?.let {
                database.markEventConfirmed(WearDatabase.EVENT_KIND_SOS_CANCEL, it)
            }
            suspectedEventId?.takeIf { it.isNotEmpty() }?.let {
                database.markEventConfirmed(WearDatabase.EVENT_KIND_SUSPECTED, it)
                // When suspected event is confirmed, trigger sync to allow decision delivery
                requestSync()
            }
            decisionEventId?.takeIf { it.isNotEmpty() }?.let {
                database.markEventConfirmed(WearDatabase.EVENT_KIND_DECISION, it)
            }
            requestSync()
        }
    }

private suspend fun runSyncCycle() {
        val node = connectedNode() ?: return
        announceIfNeeded(node)
        // 1. Explicit SOS/SOS_CANCEL always eligible (emergency operations)
        sendPendingSosOperations(node)
        // 2. Telemetry batches first (independent)
        sendOutstandingBatches(node)
        sendNewTelemetryBatches(node)
        // 3. Suspected events only if their telemetry window is confirmed
        sendEligibleSuspectedEvents(node)
        // 4. Decision events only if their suspected event is confirmed
        sendEligibleDecisionEvents(node)
    }

    /**
     * Sends explicit manual SOS and SOS cancellation events.
     * These are emergency operations that must NEVER be blocked by telemetry,
     * suspected, or decision dependencies. They have their own independent retry/ACK logic.
     */
    private suspend fun sendPendingSosOperations(node: Node) {
        val now = System.currentTimeMillis()
        val pending = database.pendingEvents(now)
        for (operation in pending) {
            val event = operation.event
            val (route, envelope) = when (operation.kind) {
                WearDatabase.EVENT_KIND_SOS_CANCEL ->
                    BackendEndpointContract.sosCancelPath(event.id) to BackendEndpointContract.sosCancelEnvelope(event)
                WearDatabase.EVENT_KIND_SOS ->
                    BackendEndpointContract.sosPath(event.id) to BackendEndpointContract.sosEnvelope(event)
                else -> continue
            }
            val bytes = envelope.toString().toByteArray(Charsets.UTF_8)
            try {
                messageClient.sendMessage(node.id, route, bytes).awaitResult()
                val attempt = event.attempts + 1
                database.markEventSent(operation.kind, event.id, attempt, now + backoffMillis(attempt))
            } catch (e: Exception) {
                val attempt = event.attempts + 1
                database.markEventFailed(operation.kind, event.id, attempt, now + backoffMillis(attempt))
            }
        }
    }

    /**
     * Sends suspected events whose telemetry window has been cloud-ACKed.
     * Returns true if more suspected events remain pending.
     */
    private suspend fun sendEligibleSuspectedEvents(node: Node): Boolean {
        val now = System.currentTimeMillis()
        val pending = database.pendingEvents(now)
        var hasMore = false
        for (operation in pending) {
            if (operation.kind != WearDatabase.EVENT_KIND_SUSPECTED) continue
            val event = operation.event
            // Only send if telemetry window is confirmed
            if (!database.isTelemetryWindowConfirmed(event)) {
                hasMore = true
                continue
            }
            val (route, envelope) = BackendEndpointContract.suspectedPath(event.id) to BackendEndpointContract.suspectedEventEnvelope(event)
            val bytes = envelope.toString().toByteArray(Charsets.UTF_8)
            try {
                messageClient.sendMessage(node.id, route, bytes).awaitResult()
                val attempt = event.attempts + 1
                database.markEventSent(operation.kind, event.id, attempt, now + backoffMillis(attempt))
            } catch (e: Exception) {
                val attempt = event.attempts + 1
                database.markEventFailed(operation.kind, event.id, attempt, now + backoffMillis(attempt))
            }
        }
        return database.pendingEvents(now).any { it.kind == WearDatabase.EVENT_KIND_SUSPECTED }
    }

    /**
     * Sends decision events whose suspected event has been cloud-ACKed.
     * Returns true if more decision events remain pending.
     */
    private suspend fun sendEligibleDecisionEvents(node: Node): Boolean {
        val now = System.currentTimeMillis()
        val pending = database.pendingEvents(now)
        var hasMore = false
        for (operation in pending) {
            if (operation.kind != WearDatabase.EVENT_KIND_DECISION) continue
            val event = operation.event
            // Only send if suspected event is confirmed
            if (!database.isSuspectedEventConfirmed(event.id)) {
                hasMore = true
                continue
            }
            val (route, envelope) = BackendEndpointContract.decisionPath(event.id) to BackendEndpointContract.eventDecisionEnvelope(event)
            val bytes = envelope.toString().toByteArray(Charsets.UTF_8)
            try {
                messageClient.sendMessage(node.id, route, bytes).awaitResult()
                val attempt = event.attempts + 1
                database.markEventSent(operation.kind, event.id, attempt, now + backoffMillis(attempt))
            } catch (e: Exception) {
                val attempt = event.attempts + 1
                database.markEventFailed(operation.kind, event.id, attempt, now + backoffMillis(attempt))
            }
        }
        return database.pendingEvents(now).any { it.kind == WearDatabase.EVENT_KIND_DECISION }
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
        for (operation in pending) {
            val event = operation.event
            val (route, envelope) = when (operation.kind) {
                WearDatabase.EVENT_KIND_SOS_CANCEL ->
                    BackendEndpointContract.sosCancelPath(event.id) to BackendEndpointContract.sosCancelEnvelope(event)
                WearDatabase.EVENT_KIND_SOS ->
                    BackendEndpointContract.sosPath(event.id) to BackendEndpointContract.sosEnvelope(event)
                WearDatabase.EVENT_KIND_SUSPECTED ->
                    BackendEndpointContract.suspectedPath(event.id) to BackendEndpointContract.suspectedEventEnvelope(event)
                WearDatabase.EVENT_KIND_DECISION ->
                    BackendEndpointContract.decisionPath(event.id) to BackendEndpointContract.eventDecisionEnvelope(event)
                else -> return true
            }
            val bytes = envelope.toString().toByteArray(Charsets.UTF_8)
            try {
                messageClient.sendMessage(node.id, route, bytes).awaitResult()
                val attempt = event.attempts + 1
                database.markEventSent(operation.kind, event.id, attempt, now + backoffMillis(attempt))
            } catch (e: Exception) {
                val attempt = event.attempts + 1
                database.markEventFailed(operation.kind, event.id, attempt, now + backoffMillis(attempt))
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
        var limit = MAX_TELEMETRY_PER_BATCH
        var pending = database.pendingTelemetry(limit)
        if (pending.isEmpty()) return false
        val batchId = UUID.randomUUID().toString()
        var payload = BackendEndpointContract.telemetryEnvelope(batchId, pending).toString()
        while (payload.toByteArray().size > MAX_BATCH_BYTES && limit > 1) {
            limit = (limit / 2).coerceAtLeast(1)
            pending = database.pendingTelemetry(limit)
            payload = BackendEndpointContract.telemetryEnvelope(batchId, pending).toString()
        }
        if (payload.toByteArray().size > MAX_BATCH_BYTES) {
            database.markTelemetryFailed(listOf(pending.single().id))
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

package com.anxietywatch.wear.datalayer

import android.content.Context
import com.anxietywatch.wear.AnxietyWatchApplication
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Recibe las confirmaciones de entrega del teléfono (nodo fog) por identificador:
 *
 *  - `/fog/v1/ack/telemetry/{batchId}`
 *  - `/fog/v1/ack/sos/{eventId}`
 *  - `/fog/v1/ack/sos-cancel/{eventId}`
 *  - `/fog/v1/ack/events/suspected/{eventId}`
 *  - `/fog/v1/ack/events/decision/{eventId}`
 *
 * y marca los lotes/eventos como CONFIRMED en la base local.
 */
class WatchDataListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path ?: return
        val runtime = (applicationContext as? AnxietyWatchApplication)?.runtime ?: return
        when (ackKind(path)) {
            AckKind.TELEMETRY -> {
                runtime.handleAck(batchId = path.removePrefix(BackendEndpointContract.ACK_TELEMETRY_PREFIX))
            }
            AckKind.SOS -> {
                runtime.handleAck(eventId = path.removePrefix(BackendEndpointContract.ACK_SOS_PREFIX))
            }
            AckKind.SOS_CANCEL -> {
                runtime.handleAck(sosCancelEventId = path.removePrefix(BackendEndpointContract.ACK_SOS_CANCEL_PREFIX))
            }
            AckKind.SUSPECTED -> {
                runtime.handleAck(suspectedEventId = path.removePrefix(BackendEndpointContract.ACK_SUSPECTED_PREFIX))
            }
            AckKind.DECISION -> {
                runtime.handleAck(decisionEventId = path.removePrefix(BackendEndpointContract.ACK_DECISION_PREFIX))
            }
            null -> Unit
        }
    }

    companion object {
        const val TAG = "WatchDataListener"

        internal enum class AckKind { TELEMETRY, SOS, SOS_CANCEL, SUSPECTED, DECISION }

        internal fun ackKind(path: String): AckKind? = when {
            path.startsWith(BackendEndpointContract.ACK_TELEMETRY_PREFIX) -> AckKind.TELEMETRY
            path.startsWith(BackendEndpointContract.ACK_SOS_PREFIX) -> AckKind.SOS
            path.startsWith(BackendEndpointContract.ACK_SOS_CANCEL_PREFIX) -> AckKind.SOS_CANCEL
            path.startsWith(BackendEndpointContract.ACK_SUSPECTED_PREFIX) -> AckKind.SUSPECTED
            path.startsWith(BackendEndpointContract.ACK_DECISION_PREFIX) -> AckKind.DECISION
            else -> null
        }
    }
}

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
 *
 * y marca los lotes/eventos como CONFIRMED en la base local.
 */
class WatchDataListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path ?: return
        val runtime = (applicationContext as? AnxietyWatchApplication)?.runtime ?: return
        when {
            path.startsWith(BackendEndpointContract.ACK_TELEMETRY_PREFIX) -> {
                runtime.handleAck(batchId = path.removePrefix(BackendEndpointContract.ACK_TELEMETRY_PREFIX))
            }
            path.startsWith(BackendEndpointContract.ACK_SOS_PREFIX) -> {
                runtime.handleAck(eventId = path.removePrefix(BackendEndpointContract.ACK_SOS_PREFIX))
            }
            path.startsWith(BackendEndpointContract.ACK_SOS_CANCEL_PREFIX) -> {
                runtime.handleAck(sosCancelEventId = path.removePrefix(BackendEndpointContract.ACK_SOS_CANCEL_PREFIX))
            }
        }
    }

    companion object {
        const val TAG = "WatchDataListener"
    }
}

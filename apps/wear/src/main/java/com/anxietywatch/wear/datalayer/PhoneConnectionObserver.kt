package com.anxietywatch.wear.datalayer

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class PhoneConnectionObserver(context: Context) {
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)

    suspend fun connectedNodes(): List<Node> = suspendCancellableCoroutine { continuation ->
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (continuation.isActive) continuation.resume(nodes)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(emptyList())
            }
    }

    suspend fun isConnected(): Boolean = connectedNodes().isNotEmpty()
}

package org.mercurio.client.feature.entry.wiring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.channels.Channel

/**
 * Carries install session statuses from the manifest receiver to the adapter that awaits them. The
 * channel is process-scoped, and [drain] clears a status that no flow consumed before the next
 * install starts.
 */
internal object NightlyInstallStatusRelay {
    private val statuses = Channel<Intent>(Channel.UNLIMITED)

    fun deliver(intent: Intent) {
        statuses.trySend(intent)
    }

    suspend fun next(): Intent = statuses.receive()

    fun drain() {
        while (true) {
            val status = statuses.tryReceive()
            if (status.isFailure) return
        }
    }
}

/** Receives the session status the platform broadcasts through the committed pending intent. */
class NightlyInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        NightlyInstallStatusRelay.deliver(intent)
    }
}

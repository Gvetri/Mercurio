package org.mercurio.client.feature.entry.data

class GatewayEventStream(
    val statusCode: Int,
    val lines: Sequence<String>,
    private val closeAction: () -> Unit = {},
) : AutoCloseable {
    @Volatile
    private var closed = false

    override fun close() {
        if (!closed) {
            closed = true
            closeAction()
        }
    }
}

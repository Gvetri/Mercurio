package org.mercurio.client.feature.entry.domain

enum class RunEventType {
    STARTED,
    RUNNING,
    COMPLETING,
    MESSAGE_DELTA,
    TEXT_DELTA,
    COMPLETED,
    SUCCEEDED,
    FAILED,
    INTERRUPTED,
    CANCELLED,
}

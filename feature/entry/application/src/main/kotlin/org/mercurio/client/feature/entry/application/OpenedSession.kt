package org.mercurio.client.feature.entry.application

import org.mercurio.client.feature.entry.domain.Session
import org.mercurio.client.feature.entry.domain.SessionHistory

data class OpenedSession(
    val session: Session,
    val history: SessionHistory,
)

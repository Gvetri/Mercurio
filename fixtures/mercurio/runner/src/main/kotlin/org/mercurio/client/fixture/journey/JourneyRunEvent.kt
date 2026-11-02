package org.mercurio.client.fixture.journey

/** One pinned Run SSE payload: `{"event": <type>, "run_id": ..., ...}`. */
data class JourneyRunEvent(
    val type: String,
    val delta: String? = null,
)

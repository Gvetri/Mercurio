package org.mercurio.client.fixture.journey

data class JourneyRunScript(
    val runId: String,
    val sessionId: String,
    val createStatus: String = "started",
    val observation: List<JourneyRunEvent> = emptyList(),
    val interruptAfterEvents: Int? = null,
    val holdOpen: Boolean = false,
    val finalizeOnDisconnect: Boolean = false,
    val refuseCreate: Boolean = false,
    val admissionRefusalCode: String? = null,
    val liveFraming: Boolean = false,
    val finalStatus: String? = null,
    val terminalHistory: List<JourneyMessage> = emptyList(),
    val idleGapMillis: Int? = null,
) {
    init {
        require(interruptAfterEvents == null || interruptAfterEvents in 0..observation.size) {
            "interruptAfterEvents must be within the observation sequence."
        }
        require(!holdOpen || interruptAfterEvents == null) {
            "A held-open observation cannot interrupt."
        }
        require(!finalizeOnDisconnect || (holdOpen && finalStatus != null)) {
            "A disconnect-finalized observation must hold open and declare its final status."
        }
        require(idleGapMillis == null || idleGapMillis in 1..MAX_IDLE_GAP_MILLIS) {
            "idle_gap_ms must be between 1 and $MAX_IDLE_GAP_MILLIS milliseconds."
        }
        require(!holdOpen || idleGapMillis == null) {
            "A held-open observation cannot pace idle gaps between events."
        }
        require(
            !refuseCreate ||
                (observation.isEmpty() && finalStatus == null && terminalHistory.isEmpty()),
        ) {
            "A refused Run creation cannot script observation or terminal behavior."
        }
        require(admissionRefusalCode == null || !refuseCreate) {
            "A Run cannot both fail creation and refuse admission."
        }
        require(
            admissionRefusalCode == null ||
                (
                    admissionRefusalCode.isNotBlank() &&
                        observation.isEmpty() && finalStatus == null && terminalHistory.isEmpty()
                ),
        ) {
            "An admission-refused Run cannot carry a blank code or script observation or terminal behavior."
        }
    }
}

private const val MAX_IDLE_GAP_MILLIS = 30_000

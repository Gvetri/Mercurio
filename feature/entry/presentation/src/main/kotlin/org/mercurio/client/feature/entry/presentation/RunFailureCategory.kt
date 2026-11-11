package org.mercurio.client.feature.entry.presentation

enum class RunFailureCategory(
    val messageRes: Int,
) {
    GATEWAY_REPORTED(R.string.error_run_failed),
    MODEL_REFUSED(R.string.error_run_failed_model),
    EFFORT_REFUSED(R.string.error_run_failed_effort),
}

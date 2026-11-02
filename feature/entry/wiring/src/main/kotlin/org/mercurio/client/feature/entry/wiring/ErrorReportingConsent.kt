package org.mercurio.client.feature.entry.wiring

/** The stored answer to the request to send crash, error, and not-responding reports. */
enum class ErrorReportingConsent {
    /** No answer is stored yet: a fresh install starts here and sends nothing. */
    Undecided,

    /** The user declined: the answer is stored, so a later run does not read it as missing. */
    Declined,

    Granted,
    ;

    val isGranted: Boolean get() = this == Granted
}

package org.mercurio.client.feature.entry.domain

/** Reads and clears the private local diagnostics buffer. */
interface LocalDiagnosticsStore {
    /** Number of complete records the buffer currently retains. */
    fun recordCount(): Int

    /** Discards every retained record; collection stays ready for new records. */
    fun clear()
}

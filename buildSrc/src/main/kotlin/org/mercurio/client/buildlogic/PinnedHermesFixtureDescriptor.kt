package org.mercurio.client.buildlogic

/** The validated, repository-owned provenance and lifecycle contract for the deterministic fixture. */
data class PinnedHermesFixtureDescriptor(
    val schemaVersion: Int,
    val name: String,
    val provenanceField: String,
    val provenanceValue: String,
    val fields: Map<String, String>,
)

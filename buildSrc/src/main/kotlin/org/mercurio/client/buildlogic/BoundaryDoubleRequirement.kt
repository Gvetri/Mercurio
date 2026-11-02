package org.mercurio.client.buildlogic

/** A datasource or repository boundary that must keep a deterministic test double in the repository. */
data class BoundaryDoubleRequirement(
    val boundaryType: String,
    val kind: String,
)

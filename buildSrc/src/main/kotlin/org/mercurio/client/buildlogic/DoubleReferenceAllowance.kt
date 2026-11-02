package org.mercurio.client.buildlogic

/** A declared deviation from [QualityPolicy.forbiddenDoubleReferences] for one boundary double. */
data class DoubleReferenceAllowance(
    val doubleType: String,
    val reference: String,
    val reason: String,
)

package org.mercurio.client.buildlogic

data class ClassCoverage(
    val name: String,
    val lineCovered: Int,
    val lineTotal: Int,
    val branchCovered: Int,
    val branchTotal: Int,
)

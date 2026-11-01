package org.mercurio.client.fixture.contract

class ContractSseFixture(
    val sourceName: String,
    val provenanceField: String,
    val provenanceValue: String,
    val events: List<ContractSseEvent>,
)

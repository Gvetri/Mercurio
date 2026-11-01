package org.mercurio.client.fixture.contract

class ContractFixtureException(
    val category: ContractFixtureFailureCategory,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

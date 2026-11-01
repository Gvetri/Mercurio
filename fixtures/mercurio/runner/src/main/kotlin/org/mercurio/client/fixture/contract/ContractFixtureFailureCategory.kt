package org.mercurio.client.fixture.contract

enum class ContractFixtureFailureCategory {
    INVALID_JSON,
    INVALID_SSE_FRAMING,
    MISSING_PROVENANCE,
    DUPLICATE_PROVENANCE,
    INVALID_PROVENANCE,
    MISSING_REQUIRED_FIELD,
    INVALID_REQUIRED_FIELD_TYPE,
}

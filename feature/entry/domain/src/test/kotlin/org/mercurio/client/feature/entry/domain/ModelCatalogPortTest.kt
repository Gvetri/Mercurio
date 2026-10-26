package org.mercurio.client.feature.entry.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelCatalogPortTest {
    @Test
    fun a_port_that_never_implements_the_catalog_fetch_fails_closed() {
        val port = object : ModelGatewayPort {}

        try {
            port.fetchModelCatalog()
            fail("Expected an unimplemented catalog fetch to fail closed.")
        } catch (error: GatewayException) {
            assertEquals(GatewayErrorCategory.REQUIRED_FEATURE_UNAVAILABLE, error.category)
        }
    }

    @Test
    fun a_port_that_implements_the_catalog_fetch_returns_the_catalog() {
        val catalog =
            ModelCatalog(
                discriminator = "hermes.api_server.run_models",
                defaultModel = "deepseek-v4-flash",
                entries =
                    listOf(
                        ModelCatalogEntry(
                            id = "deepseek-v4-flash",
                            displayName = "DeepSeek V4 Flash",
                            provider = "opencode-go",
                            effort =
                                ModelEffortOptions(
                                    supported = listOf("low", "medium", "high"),
                                    default = "medium",
                                ),
                        ),
                        ModelCatalogEntry(
                            id = "hermes-agent",
                            displayName = "Hermes Agent",
                            provider = null,
                            effort = null,
                        ),
                    ),
            )
        val port =
            object : ModelGatewayPort {
                override fun fetchModelCatalog(): ModelCatalog = catalog
            }

        assertEquals(catalog, port.fetchModelCatalog())
        assertEquals(2, port.fetchModelCatalog().entries.size)
        assertTrue(port.fetchModelCatalog().entries.last().effort == null)
    }
}

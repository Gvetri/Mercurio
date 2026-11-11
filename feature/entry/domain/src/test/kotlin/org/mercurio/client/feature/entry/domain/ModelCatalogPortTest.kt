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
            port.fetchModelCatalog(ModelCatalogSurface.RUN_MODELS)
            fail("Expected an unimplemented catalog fetch to fail closed.")
        } catch (error: GatewayException) {
            assertEquals(GatewayErrorCategory.REQUIRED_FEATURE_UNAVAILABLE, error.category)
        }
    }

    @Test
    fun a_port_that_implements_the_catalog_fetch_returns_the_catalog() {
        val catalog =
            ModelCatalog(
                surface = ModelCatalogSurface.RUN_MODELS,
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
                override fun fetchModelCatalog(surface: ModelCatalogSurface): ModelCatalog = catalog
            }

        assertEquals(catalog, port.fetchModelCatalog(ModelCatalogSurface.RUN_MODELS))
        assertEquals(2, port.fetchModelCatalog(ModelCatalogSurface.RUN_MODELS).entries.size)
        assertTrue(port.fetchModelCatalog(ModelCatalogSurface.RUN_MODELS).entries.last().effort == null)
    }
}

package org.mercurio.client.feature.entry.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mercurio.client.feature.entry.domain.GatewayErrorCategory
import org.mercurio.client.feature.entry.domain.GatewayException
import java.time.Instant

class GatewayJsonParserSessionTest {
    @Test
    fun maps_last_active_epoch_seconds_to_the_session_projection() {
        val page =
            GatewayJsonParser.parseSessionPage(
                "list sessions",
                GatewayJsonParser.parseObject(
                    "list sessions",
                    """
                    {"object":"list","data":[{"id":"s1","title":null,"preview":null,
                    "pinned":false,"last_active":1757325600.5}],
                    "limit":20,"offset":0,"has_more":false}
                    """.trimIndent(),
                ),
            )

        assertEquals(Instant.ofEpochSecond(1757325600L, 500_000_000), page.sessions.single().lastActive)
    }

    @Test
    fun keeps_last_active_null_when_the_projection_omits_it() {
        val page =
            GatewayJsonParser.parseSessionPage(
                "list sessions",
                GatewayJsonParser.parseObject(
                    "list sessions",
                    """
                    {"object":"list","data":[{"id":"s1","title":"Session","preview":null,
                    "pinned":false}],"limit":20,"offset":0,"has_more":false}
                    """.trimIndent(),
                ),
            )

        assertNull(page.sessions.single().lastActive)
    }

    @Test
    fun rejects_a_non_numeric_last_active() {
        val error =
            assertThrows(GatewayException::class.java) {
                GatewayJsonParser.parseSessionPage(
                    "list sessions",
                    GatewayJsonParser.parseObject(
                        "list sessions",
                        """
                        {"object":"list","data":[{"id":"s1","title":"Session","preview":null,
                        "pinned":false,"last_active":"yesterday"}],
                        "limit":20,"offset":0,"has_more":false}
                        """.trimIndent(),
                    ),
                )
            }

        assertEquals(GatewayErrorCategory.INVALID_RESPONSE, error.category)
    }
}

package org.mercurio.client.feature.entry.data

import org.junit.Assert.assertTrue
import org.junit.Test

class MessageImageWiringTest {
    @Test
    fun the_default_transport_is_the_okhttp_implementation() {
        assertTrue(defaultMessageImageHttpTransport() is OkHttpMessageImageHttpTransport)
    }
}

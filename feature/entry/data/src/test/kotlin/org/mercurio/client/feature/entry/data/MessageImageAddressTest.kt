package org.mercurio.client.feature.entry.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageImageAddressTest {
    @Test
    fun public_literals_are_accepted() {
        val addresses =
            listOf(
                "93.184.216.34",
                "8.8.8.8",
                "1.1.1.1",
                "2606:4700:4700::1111",
                "2a00:1450:4001:80f::200e",
            )

        addresses.forEach { address -> assertTrue(address, isPublicMessageImageAddress(address)) }
    }

    @Test
    fun private_and_loopback_ipv4_ranges_are_refused() {
        val addresses =
            listOf(
                "10.0.0.1",
                "172.16.5.5",
                "172.31.255.254",
                "192.168.1.1",
                "127.0.0.1",
                "0.0.0.0",
                "169.254.10.10",
                "100.64.0.1",
                "100.127.255.254",
            )

        addresses.forEach { address -> assertFalse(address, isPublicMessageImageAddress(address)) }
    }

    @Test
    fun documentation_reserved_and_multicast_ipv4_ranges_are_refused() {
        val addresses =
            listOf(
                "192.0.0.8",
                "192.0.2.1",
                "198.18.0.1",
                "198.51.100.7",
                "203.0.113.5",
                "224.0.0.1",
                "239.255.255.250",
                "255.255.255.255",
            )

        addresses.forEach { address -> assertFalse(address, isPublicMessageImageAddress(address)) }
    }

    @Test
    fun private_link_local_and_special_ipv6_literals_are_refused() {
        val addresses =
            listOf(
                "::",
                "::1",
                "fc00::1",
                "fd12:3456:789a::1",
                "fe80::1",
                "ff02::1",
                "2001:db8::1",
            )

        addresses.forEach { address -> assertFalse(address, isPublicMessageImageAddress(address)) }
    }
}

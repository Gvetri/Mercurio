@file:Suppress("MagicNumber")

package org.mercurio.client.feature.entry.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress

/** Resolves one URL host to the address literals a request would reach; a test double replaces it. */
fun interface MessageImageHostResolver {
    suspend fun resolve(host: String): List<String>
}

/** The system resolver: every address the host maps to, including IPv6 answers. */
class SystemMessageImageHostResolver(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MessageImageHostResolver {
    override suspend fun resolve(host: String): List<String> =
        withContext(dispatcher) {
            InetAddress.getAllByName(host).mapNotNull { address -> address.hostAddress }
        }
}

/**
 * Whether an address literal belongs to a public unicast destination. Private, loopback,
 * link-local, carrier-grade NAT, documentation, and multicast ranges are refused, and an
 * unparsable literal fails closed.
 */
internal fun isPublicMessageImageAddress(address: String): Boolean {
    val parsed = runCatching { InetAddress.getByName(address) }.getOrNull() ?: return false
    return parsed.isPublicUnicast()
}

private fun InetAddress.isPublicUnicast(): Boolean {
    val isLocalOrLoopback = isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress
    if (isLocalOrLoopback || isSiteLocalAddress || isMulticastAddress) return false
    return if (this is Inet4Address) isPublicIpv4Address(address) else isPublicIpv6Address(address)
}

private fun isPublicIpv4Address(bytes: ByteArray): Boolean {
    val first = bytes[0].toInt() and BYTE_MASK
    val second = bytes[1].toInt() and BYTE_MASK
    return !(isSpecialFirstOctet(first) || isPrivateIpv4(first, second) || isReservedIpv4(first, second))
}

private fun isSpecialFirstOctet(first: Int): Boolean = first == 0 || first == 10 || first == 127 || first >= 224

private fun isPrivateIpv4(
    first: Int,
    second: Int,
): Boolean =
    when {
        first == 169 -> second == 254
        first == 172 -> second in 16..31
        first == 192 -> second == 168
        else -> false
    }

private fun isReservedIpv4(
    first: Int,
    second: Int,
): Boolean =
    when {
        first == 192 -> second == 0
        first == 198 -> second == 18 || second == 19 || second == 51
        first == 203 -> second == 0
        first == 100 -> second in 64..127
        else -> false
    }

private fun isPublicIpv6Address(bytes: ByteArray): Boolean {
    val first = bytes[0].toInt() and BYTE_MASK
    val second = bytes[1].toInt() and BYTE_MASK
    return !(
        hasIpv4MappedPrefix(bytes) ||
            isDocumentationIpv6Prefix(bytes) ||
            isLocalIpv6Prefix(first, second) ||
            first == IPV6_MULTICAST_FIRST_OCTET
    )
}

private fun hasIpv4MappedPrefix(bytes: ByteArray): Boolean =
    bytes.take(IPV4_MAPPED_PREFIX_BYTES).all { it.toInt() == 0 }

private fun isDocumentationIpv6Prefix(bytes: ByteArray): Boolean =
    bytes.take(DOCUMENTATION_PREFIX_OCTETS.size)
        .map { octet -> octet.toInt() and BYTE_MASK } == DOCUMENTATION_PREFIX_OCTETS

private fun isLocalIpv6Prefix(
    first: Int,
    second: Int,
): Boolean =
    (first and UNIQUE_LOCAL_MASK) == UNIQUE_LOCAL_PREFIX ||
        (first == LINK_LOCAL_FIRST_OCTET && (second and LINK_LOCAL_MASK) == LINK_LOCAL_PREFIX)

private const val BYTE_MASK = 0xff

private const val IPV4_MAPPED_PREFIX_BYTES = 12

private const val IPV6_MULTICAST_FIRST_OCTET = 0xff

private const val UNIQUE_LOCAL_MASK = 0xfe

private const val UNIQUE_LOCAL_PREFIX = 0xfc

private const val LINK_LOCAL_FIRST_OCTET = 0xfe

private const val LINK_LOCAL_MASK = 0xc0

private const val LINK_LOCAL_PREFIX = 0x80

private val DOCUMENTATION_PREFIX_OCTETS = listOf(0x20, 0x01, 0x0d, 0xb8)

package org.mercurio.client.feature.entry.data

import java.io.File
import java.security.MessageDigest
import java.util.TreeMap

internal val SESSION_OPEN_ENVELOPE =
    """
    {"object":"hermes.session","session":{
    "id":"synthetic-created-session-1","source":"api_server","title":"Live lane created session","preview":null,"pinned":false,"message_count":0}}
    """.trimIndent()

internal fun guardedSourcePaths(
    repositoryRoot: File,
    contractsRoot: File,
): List<File> =
    buildList {
        addAll(contractsRoot.walkTopDown().filter { it.isFile })
        add(repositoryRoot.resolve(PARSER_SOURCE_PATH))
        add(repositoryRoot.resolve(MANIFEST_SOURCE_PATH))
    }.sortedBy { it.path }

internal fun digests(
    repositoryRoot: File,
    paths: List<File>,
) = TreeMap(
    paths.associate { path ->
        path.relativeTo(repositoryRoot).path to
            MessageDigest.getInstance("SHA-256").digest(path.readBytes()).joinToString("") { "%02x".format(it) }
    },
)

private const val PARSER_SOURCE_PATH =
    "feature/entry/data/src/main/kotlin/org/mercurio/client/feature/entry/data/GatewayJsonParser.kt"

private const val MANIFEST_SOURCE_PATH =
    "feature/entry/domain/src/main/kotlin/org/mercurio/client/feature/entry/domain" +
        "/GatewayCapabilityManifest.kt"

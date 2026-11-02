package org.mercurio.client.feature.entry.wiring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SentryAdoptionScopeTest {
    private val repositoryRoot =
        File(
            requireNotNull(System.getProperty("fixture.repositoryRoot")) {
                "fixture.repositoryRoot must identify the repository root"
            },
        )

    @Test
    fun the_catalog_declares_the_core_sdk_and_no_replay_or_profiling_module() {
        val catalog = File(repositoryRoot, "gradle/libs.versions.toml").readText()

        val sentryModules = SENTRY_MODULE.findAll(catalog).map { match -> match.groupValues[1] }.toSet()

        assertEquals(setOf("sentry-android-core"), sentryModules)
    }

    @Test
    fun the_wiring_module_declares_no_sentry_dependency_beyond_the_core_sdk() {
        val buildScript = File(repositoryRoot, "feature/entry/wiring/build.gradle.kts").readText()

        val declared = SENTRY_DEPENDENCY_ALIAS.findAll(buildScript).map { match -> match.value }.toSet()

        assertEquals(setOf("libs.sentry.android.core"), declared)
    }

    @Test
    fun every_sentry_provider_is_removed_so_the_sdk_cannot_start_itself() {
        val providers = sentryProviders()

        assertTrue(
            "The wiring manifest declares no Sentry provider, so nothing keeps the SDK from starting itself.",
            providers.isNotEmpty(),
        )
        assertTrue(
            "A Sentry provider is merged without a removal, so the SDK would start without a stored decision.",
            providers.all { provider -> provider.getAttributeNS(TOOLS_NAMESPACE, "node") == REMOVE_NODE },
        )
    }

    private fun sentryProviders(): List<Element> {
        val manifest = File(repositoryRoot, WIRING_MANIFEST_PATH)
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
        val elements = document.parse(manifest).getElementsByTagName(PROVIDER_ELEMENT)
        return (0 until elements.length)
            .map { index -> elements.item(index) as Element }
            .filter { provider -> provider.getAttributeNS(ANDROID_NAMESPACE, "name").startsWith(SENTRY_PACKAGE) }
    }
}

private const val WIRING_MANIFEST_PATH = "feature/entry/wiring/src/main/AndroidManifest.xml"
private const val PROVIDER_ELEMENT = "provider"
private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
private const val TOOLS_NAMESPACE = "http://schemas.android.com/tools"
private const val REMOVE_NODE = "remove"
private const val SENTRY_PACKAGE = "io.sentry."

private val SENTRY_MODULE = Regex("""module = "io\.sentry:([^"]+)"""")

private val SENTRY_DEPENDENCY_ALIAS = Regex("""libs\.sentry\.[a-z.]+""")

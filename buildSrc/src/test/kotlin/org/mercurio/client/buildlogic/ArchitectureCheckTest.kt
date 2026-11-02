package org.mercurio.client.buildlogic

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoParameterDeclaration
import com.lemonappdev.konsist.api.declaration.type.KoTypeDeclaration
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The architecture rules of the `entry` feature, expressed as Konsist checks over the declared source
 * scopes. The layer graph runs through the Konsist architecture assertion; the reference rules read
 * the same scopes through Konsist's declaration model, because a fully qualified reference that
 * carries no import is invisible to an import-based dependency assertion. Every rule also runs
 * against a synthetic scope that carries one deliberate violation, so a rule that stopped rejecting
 * a violation fails this suite instead of passing it.
 */
class ArchitectureCheckTest {
    private val repositoryRoot =
        File(
            requireNotNull(System.getProperty("architecture.projectDir")) {
                "architecture.projectDir must identify the repository root"
            },
        )

    @Test
    fun layer_boundaries_hold_for_every_production_source() {
        assertLayerBoundaries(productionScope())
    }

    @Test
    fun domain_and_application_references_stay_inside_the_declared_packages() {
        assertNoForbiddenReferences(
            files = scopeOf(domainSourceRoot, applicationSourceRoot).files,
            patterns = listOf(forbiddenLayerReference),
            rule = domainReferenceRule,
            guidance = domainReferenceGuidance,
        )
    }

    @Test
    fun presentation_references_avoid_executable_and_network_content() {
        assertNoForbiddenReferences(
            files = scopeOf(presentationSourceRoot).files,
            patterns = presentationForbiddenReferences,
            rule = presentationReferenceRule,
            guidance = presentationReferenceGuidance,
        )
    }

    @Test
    fun every_production_source_keeps_the_app_external_file_boundary() {
        assertNoForbiddenReferences(
            files = productionScope().files,
            patterns = appExternalFileForbiddenReferences,
            rule = appExternalFileRule,
            guidance = appExternalFileGuidance,
        )
    }

    @Test
    fun domain_and_application_module_scripts_declare_no_forbidden_dependency() {
        assertNoForbiddenModuleDependencies(moduleBuildScripts.map { script -> repositoryRoot.resolve(script) })
    }

    @Test
    fun presentation_composables_take_state_and_events_only() {
        assertComposablesTakeStateAndEvents(productionScope())
    }

    @Test
    fun presentation_state_has_one_writer() {
        assertOneStateWriter(scopeOf(presentationSourceRoot))
    }

    @Test
    fun presentation_state_types_stay_immutable() {
        assertImmutableStateTypes(scopeOf(presentationSourceRoot))
    }

    @Test
    fun presentation_exposes_one_event_entry() {
        assertOneEventEntry(scopeOf(presentationSourceRoot))
    }

    @Test
    fun accepts_the_declared_inward_dependency_direction() {
        withFixtureScope(layerFixture()) { scope ->
            assertRuleAccepts(layerBoundaryRule) { assertLayerBoundaries(scope) }
        }
    }

    @Test
    fun rejects_a_scope_that_resolves_to_no_kotlin_source() {
        withFixtureScope(
            mapOf(fixturePath("domain", "FixtureNotes.txt") to "Not a Kotlin source."),
        ) { scope ->
            assertRuleRejects(domainReferenceRule, "resolved to no Kotlin source") { assertDomainAndApplicationReferences(scope) }
        }
    }

    @Test
    fun rejects_a_module_scope_that_resolves_to_no_build_script() {
        assertRuleRejects(moduleDependencyRule, "resolved to no module build script") {
            assertNoForbiddenModuleDependencies(emptyList())
        }
    }

    @Test
    fun rejects_a_domain_reference_to_an_outer_layer() {
        val violation =
            fixturePath("domain", "LayerViolation.kt") to
                """
                package org.mercurio.client.feature.entry.domain

                import org.mercurio.client.feature.entry.application.FixtureUseCase

                internal class LayerViolation(private val useCase: FixtureUseCase)
                """

        withFixtureScope(layerFixture(violation)) { scope ->
            assertRuleRejects(layerBoundaryRule, "LayerViolation.kt") { assertLayerBoundaries(scope) }
        }
    }

    @Test
    fun rejects_an_application_reference_to_a_concrete_data_implementation() {
        val violation =
            fixturePath("application", "LayerViolation.kt") to
                """
                package org.mercurio.client.feature.entry.application

                import org.mercurio.client.feature.entry.data.FixtureAdapter

                internal class LayerViolation(private val adapter: FixtureAdapter)
                """

        withFixtureScope(layerFixture(violation)) { scope ->
            assertRuleRejects(layerBoundaryRule, "LayerViolation.kt") { assertLayerBoundaries(scope) }
        }
    }

    @Test
    fun rejects_a_fully_qualified_android_reference_in_domain() {
        val violation =
            fixturePath("domain", "ReferenceViolation.kt") to
                """
                package org.mercurio.client.feature.entry.domain

                val referenceViolation = android.os.Bundle()
                """

        withFixtureScope(mapOf(violation)) { scope ->
            assertRuleRejects(domainReferenceRule, "android.os.Bundle") { assertDomainAndApplicationReferences(scope) }
        }
    }

    @Test
    fun rejects_a_transport_reference_in_application() {
        val violation =
            fixturePath("application", "ReferenceViolation.kt") to
                """
                package org.mercurio.client.feature.entry.application

                val referenceViolation = io.ktor.client.HttpClient()
                """

        withFixtureScope(mapOf(violation)) { scope ->
            assertRuleRejects(domainReferenceRule, "io.ktor.client.HttpClient") { assertDomainAndApplicationReferences(scope) }
        }
    }

    @Test
    fun rejects_an_escaped_concrete_data_reference_in_application() {
        val violation =
            fixturePath("application", "ReferenceViolation.kt") to
                """
                package org.mercurio.client.feature.entry.application

                val referenceViolation =
                    org.mercurio.client.feature.entry.`data`.DefaultGatewayConnectionRepository
                """

        withFixtureScope(mapOf(violation)) { scope ->
            assertRuleRejects(domainReferenceRule, "DefaultGatewayConnectionRepository") { assertDomainAndApplicationReferences(scope) }
        }
    }

    @Test
    fun accepts_a_domain_port_reference_in_application() {
        val accepted =
            fixturePath("application", "AllowedReference.kt") to
                """
                package org.mercurio.client.feature.entry.application

                val allowedPort: org.mercurio.client.feature.entry.domain.GatewayConnectionRepository? = null
                """

        withFixtureScope(mapOf(accepted)) { scope ->
            assertRuleAccepts(domainReferenceRule) { assertDomainAndApplicationReferences(scope) }
        }
    }

    @Test
    fun rejects_executable_web_content_in_presentation() {
        val violation =
            fixturePath("presentation", "ReferenceViolation.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                val referenceViolation = android.webkit.WebView(null)
                """

        withFixtureScope(mapOf(violation)) { scope ->
            assertRuleRejects(presentationReferenceRule, "android.webkit.WebView") { assertPresentationReferences(scope) }
        }
    }

    @Test
    fun rejects_network_access_to_a_link_destination_in_presentation() {
        val violation =
            fixturePath("presentation", "ReferenceViolation.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                val referenceViolation = java.net.URL("https://example.com/secure")
                """

        withFixtureScope(mapOf(violation)) { scope ->
            assertRuleRejects(presentationReferenceRule, "java.net.URL") { assertPresentationReferences(scope) }
        }
    }

    @Test
    fun rejects_a_coil_network_fetcher_in_presentation() {
        val violation =
            fixturePath("presentation", "CoilNetworkViolation.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                val networkFetcher = "coil3.network.okhttp.OkHttpNetworkFetcher"
                """

        withFixtureScope(mapOf(violation)) { scope ->
            assertRuleRejects(presentationReferenceRule, "coil3.network") { assertPresentationReferences(scope) }
        }
    }

    @Test
    fun accepts_the_presentation_renderer_of_a_supported_construct() {
        val accepted =
            fixturePath("presentation", "AllowedReference.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                val allowedContent = "https://example.com/secure"
                """

        withFixtureScope(mapOf(accepted)) { scope ->
            assertRuleAccepts(presentationReferenceRule) { assertPresentationReferences(scope) }
        }
    }

    @Test
    fun rejects_a_file_uri_handed_to_another_app() {
        val violation =
            fixturePath("wiring", "FileViolation.kt") to
                """
                package org.mercurio.client.feature.entry.wiring

                val fileViolation = android.net.Uri.fromFile(snapshotFile)
                """

        withFixtureScope(mapOf(violation)) { scope ->
            assertRuleRejects(appExternalFileRule, "Uri.fromFile") { assertAppExternalFileBoundary(scope) }
        }
    }

    @Test
    fun rejects_a_file_scheme_reference_to_another_app() {
        val violation =
            fixturePath("wiring", "FileViolation.kt") to
                """
                package org.mercurio.client.feature.entry.wiring

                val fileViolation = android.net.Uri.parse("file:///data/data/snapshot.jsonl")
                """

        withFixtureScope(mapOf(violation)) { scope ->
            assertRuleRejects(appExternalFileRule, "file:///data/data/snapshot.jsonl") { assertAppExternalFileBoundary(scope) }
        }
    }

    @Test
    fun rejects_a_writable_grant_on_an_exported_snapshot() {
        val violation =
            fixturePath("wiring", "FileViolation.kt") to
                """
                package org.mercurio.client.feature.entry.wiring

                val fileViolation = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                """

        withFixtureScope(mapOf(violation)) { scope ->
            assertRuleRejects(appExternalFileRule, "FLAG_GRANT_WRITE_URI_PERMISSION") { assertAppExternalFileBoundary(scope) }
        }
    }

    @Test
    fun accepts_a_read_only_provider_export_of_a_snapshot() {
        val accepted =
            fixturePath("wiring", "AllowedReference.kt") to
                """
                package org.mercurio.client.feature.entry.wiring

                val allowedSnapshotUri =
                    androidx.core.content.FileProvider.getUriForFile(context, authority, snapshotFile)
                val allowedGrant = Intent.FLAG_GRANT_READ_URI_PERMISSION
                """

        withFixtureScope(mapOf(accepted)) { scope ->
            assertRuleAccepts(appExternalFileRule) { assertAppExternalFileBoundary(scope) }
        }
    }

    @Test
    fun rejects_a_composable_that_takes_the_state_holder() {
        val violation =
            fixturePath("presentation", "HolderParameter.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                import androidx.compose.runtime.Composable

                @Composable
                internal fun HolderParameterScreen(holder: FixtureStateHolder) = Unit
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(composableContractRule, "FixtureStateHolder") { assertComposablesTakeStateAndEvents(scope) }
        }
    }

    @Test
    fun rejects_a_composable_that_takes_a_domain_port() {
        val violation =
            fixturePath("presentation", "PortParameter.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                import androidx.compose.runtime.Composable
                import org.mercurio.client.feature.entry.domain.FixtureStore

                @Composable
                internal fun PortParameterScreen(store: FixtureStore) = Unit
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(composableContractRule, "FixtureStore") { assertComposablesTakeStateAndEvents(scope) }
        }
    }

    @Test
    fun rejects_a_composable_scope_that_resolves_to_no_presentation_function() {
        withFixtureScope(
            mapOf(fixturePath("presentation", "FixtureNotes.txt") to "Not a Kotlin source."),
        ) { scope ->
            assertRuleRejects(composableContractRule, "resolved to no presentation function") {
                assertComposablesTakeStateAndEvents(scope)
            }
        }
    }

    @Test
    fun accepts_a_composable_that_takes_state_and_events() {
        withFixtureScope(presentationFixture()) { scope ->
            assertRuleAccepts(composableContractRule) { assertComposablesTakeStateAndEvents(scope) }
        }
    }

    @Test
    fun rejects_a_public_mutable_property_on_the_state_holder() {
        val violation =
            fixturePath("presentation", "EntryStateHolder.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                internal class FixtureStateHolder {
                    var requestPermission: (() -> Unit)? = null

                    internal val mutableUiState = MutableStateFlow(EntryUiState(title = "fixture"))
                    val uiState: StateFlow<EntryUiState> = mutableUiState.asStateFlow()

                    fun onEvent(event: EntryUiEvent) {
                        mutableUiState.value = mutableUiState.value.copy(title = event.toString())
                    }
                }
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(stateWriterRule, "requestPermission") { assertOneStateWriter(scope) }
        }
    }

    @Test
    fun rejects_a_public_mutable_property_on_a_state_holder_object() {
        val violation =
            fixturePath("presentation", "EntryStateHolder.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.StateFlow
                import kotlinx.coroutines.flow.asStateFlow

                internal object FixtureStateHolder {
                    var requestPermission: (() -> Unit)? = null

                    internal val mutableUiState = MutableStateFlow(EntryUiState(title = "fixture"))
                    val uiState: StateFlow<EntryUiState> = mutableUiState.asStateFlow()

                    fun onEvent(event: EntryUiEvent) {
                        mutableUiState.value = mutableUiState.value.copy(title = event.toString())
                    }
                }
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(stateWriterRule, "requestPermission") { assertOneStateWriter(scope) }
        }
    }

    @Test
    fun rejects_a_state_write_outside_the_state_holder_files() {
        val violation =
            fixturePath("presentation", "EntryScreen.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                import androidx.compose.runtime.Composable

                @Composable
                internal fun EntryScreen(
                    state: EntryUiState,
                    onEvent: (EntryUiEvent) -> Unit,
                ) {
                    mutableUiState.value = state.copy(title = "written by the screen")
                }
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(stateWriterRule, "EntryScreen.kt") { assertOneStateWriter(scope) }
        }
    }

    @Test
    fun rejects_a_state_write_through_the_update_helper() {
        val violation =
            fixturePath("presentation", "EntryScreen.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                import androidx.compose.runtime.Composable

                @Composable
                internal fun EntryScreen(
                    state: EntryUiState,
                    onEvent: (EntryUiEvent) -> Unit,
                ) {
                    mutableUiState.update { current -> current.copy(title = "written by the screen") }
                }
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(stateWriterRule, "EntryScreen.kt") { assertOneStateWriter(scope) }
        }
    }

    @Test
    fun rejects_a_renamed_state_flow_written_outside_the_state_holder_files() {
        val violation =
            fixturePath("presentation", "EntrySessionCache.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                import kotlinx.coroutines.flow.MutableStateFlow

                internal object EntrySessionCache {
                    val cachedStateFlow = MutableStateFlow(EntryUiState(title = "cache"))

                    fun replace(state: EntryUiState) {
                        cachedStateFlow.value = state
                    }
                }
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(stateWriterRule, "EntrySessionCache.kt") { assertOneStateWriter(scope) }
        }
    }

    @Test
    fun rejects_a_scope_that_declares_no_state_flow() {
        withFixtureScope(
            mapOf(
                fixturePath("presentation", "EntryScreen.kt") to
                    """
                    package org.mercurio.client.feature.entry.presentation

                    import androidx.compose.runtime.Composable

                    @Composable
                    internal fun EntryScreen(state: EntryUiState) = Unit
                    """,
            ),
        ) { scope ->
            assertRuleRejects(stateWriterRule, "declares no MutableStateFlow") { assertOneStateWriter(scope) }
        }
    }

    @Test
    fun accepts_the_state_writes_of_the_state_holder() {
        withFixtureScope(presentationFixture()) { scope ->
            assertRuleAccepts(stateWriterRule) { assertOneStateWriter(scope) }
        }
    }

    @Test
    fun accepts_a_comparison_of_the_state_flow_outside_the_state_holder_files() {
        val accepted =
            fixturePath("presentation", "EntryScreen.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                import androidx.compose.runtime.Composable

                @Composable
                internal fun EntryScreen(
                    state: EntryUiState,
                    onEvent: (EntryUiEvent) -> Unit,
                ): Boolean = mutableUiState.value == state

                @Composable
                internal fun EntryScreenDiffers(
                    state: EntryUiState,
                    onEvent: (EntryUiEvent) -> Unit,
                ): Boolean = mutableUiState.value !== state
                """

        withFixtureScope(presentationFixture(accepted)) { scope ->
            assertRuleAccepts(stateWriterRule) { assertOneStateWriter(scope) }
        }
    }

    @Test
    fun rejects_a_state_type_with_a_mutable_property() {
        val violation =
            fixturePath("presentation", "EntryUiState.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                internal data class EntryUiState(
                    val title: String,
                    var count: Int = 0,
                )
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(immutableStateRule, "count") { assertImmutableStateTypes(scope) }
        }
    }

    @Test
    fun rejects_a_state_type_with_a_mutable_collection() {
        val violation =
            fixturePath("presentation", "EntryUiState.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                internal data class EntryUiState(
                    val title: String,
                    val rows: MutableList<String> = mutableListOf(),
                )
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(immutableStateRule, "rows") { assertImmutableStateTypes(scope) }
        }
    }

    @Test
    fun rejects_a_state_type_with_a_nested_mutable_collection() {
        val violation =
            fixturePath("presentation", "EntryUiState.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                internal data class EntryUiState(
                    val title: String,
                    val rows: Map<String, MutableList<String>> = emptyMap(),
                )
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(immutableStateRule, "rows") { assertImmutableStateTypes(scope) }
        }
    }

    @Test
    fun accepts_an_immutable_state_type() {
        withFixtureScope(presentationFixture()) { scope ->
            assertRuleAccepts(immutableStateRule) { assertImmutableStateTypes(scope) }
        }
    }

    @Test
    fun rejects_a_second_public_event_entry() {
        val violation =
            fixturePath("presentation", "SecondEntry.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                internal fun dispatchClicked(event: EntryUiEvent) = event.toString()
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(eventEntryRule, "SecondEntry.kt") { assertOneEventEntry(scope) }
        }
    }

    @Test
    fun rejects_an_event_callback_property() {
        val violation =
            fixturePath("presentation", "FixtureChannel.kt") to
                """
                package org.mercurio.client.feature.entry.presentation

                internal class FixtureChannel {
                    var raise: (EntryUiEvent) -> Unit = {}
                }
                """

        withFixtureScope(presentationFixture(violation)) { scope ->
            assertRuleRejects(eventEntryRule, "raise") { assertOneEventEntry(scope) }
        }
    }

    @Test
    fun accepts_a_state_holder_with_one_event_entry() {
        withFixtureScope(presentationFixture()) { scope ->
            assertRuleAccepts(eventEntryRule) { assertOneEventEntry(scope) }
        }
    }

    @Test
    fun rejects_a_forbidden_dependency_declaration_in_a_module_script() {
        withFixtureDirectory(
            mapOf(
                "feature/entry/domain/build.gradle.kts" to
                    """
                    dependencies {
                        implementation("androidx.core:core-ktx:1.13.1")
                    }
                    """,
            ),
        ) { directory ->
            assertRuleRejects(moduleDependencyRule, "androidx.core:core-ktx") {
                assertNoForbiddenModuleDependencies(listOf(directory.resolve("feature/entry/domain/build.gradle.kts")))
            }
        }
    }

    @Test
    fun rejects_a_named_forbidden_project_dependency_in_a_module_script() {
        withFixtureDirectory(
            mapOf(
                "feature/entry/application/build.gradle.kts" to
                    """
                    dependencies {
                        implementation(project(path = ":feature:entry:presentation", configuration = "default"))
                    }
                    """,
            ),
        ) { directory ->
            assertRuleRejects(moduleDependencyRule, """project(path = ":feature:entry:presentation", configuration = "default")""") {
                assertNoForbiddenModuleDependencies(listOf(directory.resolve("feature/entry/application/build.gradle.kts")))
            }
        }
    }

    @Test
    fun accepts_a_named_allowed_project_dependency_in_a_module_script() {
        withFixtureDirectory(
            mapOf(
                "feature/entry/application/build.gradle.kts" to
                    """
                    dependencies {
                        implementation(project(path = ":feature:entry:domain", configuration = "default"))
                    }
                    """,
            ),
        ) { directory ->
            assertRuleAccepts(moduleDependencyRule) {
                assertNoForbiddenModuleDependencies(listOf(directory.resolve("feature/entry/application/build.gradle.kts")))
            }
        }
    }

    private fun assertLayerBoundaries(scope: KoScope) {
        val domain = Layer("Domain", "org.mercurio.client.feature.entry.domain..")
        val application = Layer("Application", "org.mercurio.client.feature.entry.application..")
        val data = Layer("Data", "org.mercurio.client.feature.entry.data..")
        val presentation = Layer("Presentation", "org.mercurio.client.feature.entry.presentation..")
        val wiring = Layer("Wiring", "org.mercurio.client.feature.entry.wiring..")

        scope.assertArchitecture(
            additionalMessage = "$layerBoundaryRule failed. $layerBoundaryGuidance",
            testName = layerBoundaryRule,
        ) {
            domain.dependsOnNothing()
            application.doesNotDependOn(data, presentation, wiring)
            data.doesNotDependOn(application, presentation, wiring)
            presentation.doesNotDependOn(data, wiring)
            wiring.include()
        }
    }

    private fun assertDomainAndApplicationReferences(scope: KoScope) {
        assertNoForbiddenReferences(
            files = scope.files,
            patterns = listOf(forbiddenLayerReference),
            rule = domainReferenceRule,
            guidance = domainReferenceGuidance,
        )
    }

    private fun assertPresentationReferences(scope: KoScope) {
        assertNoForbiddenReferences(
            files = scope.files,
            patterns = presentationForbiddenReferences,
            rule = presentationReferenceRule,
            guidance = presentationReferenceGuidance,
        )
    }

    private fun assertAppExternalFileBoundary(scope: KoScope) {
        assertNoForbiddenReferences(
            files = scope.files,
            patterns = appExternalFileForbiddenReferences,
            rule = appExternalFileRule,
            guidance = appExternalFileGuidance,
        )
    }

    private fun assertComposablesTakeStateAndEvents(scope: KoScope) {
        val functions =
            scope
                .functions()
                .filter { function -> function.packagee?.name?.startsWith(presentationPackageName) == true }
        require(functions.isNotEmpty()) {
            "$composableContractRule failed. The declared scope resolved to no presentation function."
        }

        val portNames =
            scope
                .interfaces()
                .filter { declaration -> declaration.packagee?.name?.startsWith(presentationPackageName) != true }
                .map { declaration -> declaration.name }
                .toSet()

        val violations =
            functions
                .filter { function -> function.hasAnnotationWithName(composableAnnotation) }
                .flatMap { function ->
                    function.parameters
                        .filter { parameter -> parameter.takesLayerHandle(portNames) }
                        .map { parameter ->
                            "${function.location}: ${function.name} takes ${parameter.type.name}, so the rendering " +
                                "surface can read or write the state holder or a port directly."
                        }
                }

        require(violations.isEmpty()) {
            "$composableContractRule failed. $composableContractGuidance\n${violations.joinToString("\n")}"
        }
    }

    private fun assertOneStateWriter(scope: KoScope) {
        val files = scope.files
        require(files.isNotEmpty()) {
            "$stateWriterRule failed. The declared scope resolved to no Kotlin source."
        }

        val stateFlows =
            scope
                .classesAndObjects()
                .flatMap { declaration -> declaration.properties() }
                .filter { property -> property.text.contains(mutableStateFlowType) }
                .map { property -> property.name }
                .distinct()
        require(stateFlows.isNotEmpty()) {
            "$stateWriterRule failed. The declared scope declares no $mutableStateFlowType, so the rule would " +
                "have nothing to guard."
        }

        val writePatterns = stateFlows.flatMap(::stateWritePatternsOf)

        val exposedWriters =
            scope.classesAndObjects()
                .filter { declaration -> declaration.name.endsWith(stateHolderTypeSuffix) }
                .flatMap { declaration ->
                    declaration.properties()
                        .filter { property -> property.isVar && property.hasPublicOrDefaultModifier }
                        .map { property ->
                            "${property.location}: ${declaration.name} exposes the mutable property ${property.name}, " +
                                "a second write path around the event entry."
                        }
                }

        val strayWrites =
            files
                .filterNot { file -> file.name.startsWith(stateHolderFilePrefix) }
                .flatMap { file ->
                    file.text.lines().mapIndexedNotNull { index, line ->
                        if (writePatterns.any { pattern -> pattern.containsMatchIn(line) }) {
                            "${file.path}:${index + 1}: a state write outside the state-holder files: $line"
                        } else {
                            null
                        }
                    }
                }

        val violations = exposedWriters + strayWrites
        require(violations.isEmpty()) {
            "$stateWriterRule failed. $stateWriterGuidance\n${violations.joinToString("\n")}"
        }
    }

    private fun assertImmutableStateTypes(scope: KoScope) {
        val stateTypes = scope.classes().filter { declaration -> declaration.name.endsWith(uiStateTypeSuffix) }
        require(stateTypes.isNotEmpty()) {
            "$immutableStateRule failed. The declared scope resolved to no UI state type."
        }

        val violations =
            stateTypes.flatMap { declaration ->
                declaration.properties().mapNotNull { property ->
                    when {
                        property.isVar ->
                            "${property.location}: ${declaration.name}.${property.name} is a mutable property, " +
                                "so a state comparison proves nothing."
                        property.type?.hasMutableCollectionType() == true ->
                            "${property.location}: ${declaration.name}.${property.name} holds a mutable collection, " +
                                "so the value can change behind the state flow."
                        else -> null
                    }
                }
            }

        require(violations.isEmpty()) {
            "$immutableStateRule failed. $immutableStateGuidance\n${violations.joinToString("\n")}"
        }
    }

    private fun assertOneEventEntry(scope: KoScope) {
        val entries =
            scope.functions(includeLocal = false).filter { function ->
                !function.hasPrivateModifier &&
                    function.parameters.any { parameter -> parameter.type.name == eventTypeName }
            }
        val callbacks =
            scope
                .classesAndObjects()
                .flatMap { declaration -> declaration.properties() }
                .filter { property ->
                    property.isVar &&
                        !property.hasPrivateModifier &&
                        property.text.contains(eventCallbackTypeFragment)
                }
        val found = entries.size + callbacks.size

        require(found == 1) {
            "$eventEntryRule failed. $eventEntryGuidance\nThe rule found $found reachable event entries:\n" +
                (entries.map { entry -> entry.locationWithText } + callbacks.map { callback -> callback.locationWithText })
                    .joinToString("\n")
        }
    }

    private fun KoParameterDeclaration.takesLayerHandle(portNames: Set<String>): Boolean =
        type.name.let { name -> name.endsWith(stateHolderTypeSuffix) || name in portNames }

    private fun stateWritePatternsOf(flow: String): List<Regex> =
        listOf(
            Regex("""\b${Regex.escape(flow)}\s*\.\s*value\s*[-+]?=(?!=)"""),
            Regex("""\b${Regex.escape(flow)}\s*\.\s*update\s*[({]"""),
            Regex("""\b${Regex.escape(flow)}\s*\.\s*(?:getAndSet|compareAndSet)\b"""),
        )

    private fun KoTypeDeclaration.hasMutableCollectionType(): Boolean {
        val type = text.trim()
        return mutableCollectionTypePattern.containsMatchIn(type) || arrayCollectionTypePattern.containsMatchIn(type)
    }

    private fun assertNoForbiddenReferences(
        files: List<KoFileDeclaration>,
        patterns: List<Regex>,
        rule: String,
        guidance: String,
    ) {
        require(files.isNotEmpty()) { "$rule failed. The declared scope resolved to no Kotlin source." }

        val violations =
            files.flatMap { file ->
                file.text.lines().mapIndexedNotNull { index, line ->
                    val scannable = line.replace("`", "")
                    if (patterns.any { pattern -> pattern.containsMatchIn(scannable) }) {
                        "${file.path}:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }

        require(violations.isEmpty()) { "$rule failed. $guidance\n${violations.joinToString("\n")}" }
    }

    private fun assertNoForbiddenModuleDependencies(buildScripts: List<File>) {
        require(buildScripts.isNotEmpty()) { "$moduleDependencyRule failed. The declared scope resolved to no module build script." }

        val violations =
            buildScripts.flatMap { buildScript ->
                val content = buildScript.readText()
                val tokenViolations =
                    content.lines().mapIndexedNotNull { index, line ->
                        if (forbiddenModuleDependencyTokens.any(line::contains)) {
                            "${buildScript.relativeToOrSelf(repositoryRoot)}:${index + 1}: forbidden module dependency: $line"
                        } else {
                            null
                        }
                    }
                val namedProjectViolations =
                    forbiddenNamedProjectDependency.findAll(content).map { match ->
                        val lineNumber = content.substring(0, match.range.first).count { it == '\n' } + 1
                        "${buildScript.relativeToOrSelf(repositoryRoot)}:$lineNumber: forbidden named project dependency: ${match.value}"
                    }
                tokenViolations + namedProjectViolations
            }

        require(violations.isEmpty()) { "$moduleDependencyRule failed. $moduleDependencyGuidance\n${violations.joinToString("\n")}" }
    }

    private fun productionScope(): KoScope = Konsist.scopeFromProject(sourceSetName = "main", ignoreBuildConfig = false)

    private fun scopeOf(vararg roots: String): KoScope = Konsist.scopeFromDirectories(roots.toList())

    private fun assertRuleRejects(
        rule: String,
        expected: String,
        block: () -> Unit,
    ) {
        val failure = runCatching(block).exceptionOrNull()

        assertNotNull("Architecture rule '$rule' accepted a deliberate violation.", failure)
        val message = failure?.message.orEmpty()
        assertTrue("Architecture rule '$rule' did not name the rule:\n$message", message.contains(rule))
        assertTrue("Architecture rule '$rule' did not report '$expected':\n$message", message.contains(expected))
    }

    private fun assertRuleAccepts(
        rule: String,
        block: () -> Unit,
    ) {
        val failure = runCatching(block).exceptionOrNull()

        assertNull("Architecture rule '$rule' rejected allowed code:\n${failure?.message}", failure)
    }

    private fun withFixtureScope(
        files: Map<String, String>,
        block: (KoScope) -> Unit,
    ) = withFixtureDirectory(files) { directory ->
        block(Konsist.scopeFromExternalDirectory(directory.absolutePath))
    }

    private fun withFixtureDirectory(
        files: Map<String, String>,
        block: (File) -> Unit,
    ) {
        val directory = Files.createTempDirectory("architecture-rule-fixture").toFile()
        try {
            files.forEach { (relativePath, content) ->
                val target = directory.resolve(relativePath)
                target.parentFile.mkdirs()
                target.writeText(content.trimIndent() + "\n")
            }
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun fixturePath(
        layer: String,
        fileName: String,
    ) = "feature/entry/$layer/src/main/kotlin/org/mercurio/client/feature/entry/$layer/$fileName"

    private fun layerFixture(violation: Pair<String, String>? = null): Map<String, String> {
        val files =
            mutableMapOf(
                fixturePath("domain", "FixturePort.kt") to
                    """
                    package org.mercurio.client.feature.entry.domain

                    internal interface FixturePort
                    """,
                fixturePath("application", "FixtureUseCase.kt") to
                    """
                    package org.mercurio.client.feature.entry.application

                    import org.mercurio.client.feature.entry.domain.FixturePort

                    internal class FixtureUseCase(private val port: FixturePort)
                    """,
                fixturePath("data", "FixtureAdapter.kt") to
                    """
                    package org.mercurio.client.feature.entry.data

                    import org.mercurio.client.feature.entry.domain.FixturePort

                    internal class FixtureAdapter : FixturePort
                    """,
                fixturePath("presentation", "FixtureHolder.kt") to
                    """
                    package org.mercurio.client.feature.entry.presentation

                    import org.mercurio.client.feature.entry.application.FixtureUseCase
                    import org.mercurio.client.feature.entry.domain.FixturePort

                    internal class FixtureHolder(
                        private val useCase: FixtureUseCase,
                        private val port: FixturePort,
                    )
                    """,
                fixturePath("wiring", "FixtureWiring.kt") to
                    """
                    package org.mercurio.client.feature.entry.wiring

                    import org.mercurio.client.feature.entry.application.FixtureUseCase
                    import org.mercurio.client.feature.entry.data.FixtureAdapter
                    import org.mercurio.client.feature.entry.domain.FixturePort
                    import org.mercurio.client.feature.entry.presentation.FixtureHolder

                    internal object FixtureWiring {
                        fun assemble(): FixtureHolder {
                            val port: FixturePort = FixtureAdapter()
                            return FixtureHolder(FixtureUseCase(port), port)
                        }
                    }
                    """,
            )

        violation?.let { (path, content) -> files[path] = content }

        return files
    }

    private fun presentationFixture(violation: Pair<String, String>? = null): Map<String, String> {
        val files =
            mutableMapOf(
                fixturePath("presentation", "EntryUiEvent.kt") to
                    """
                    package org.mercurio.client.feature.entry.presentation

                    internal sealed interface EntryUiEvent {
                        data object Clicked : EntryUiEvent
                    }
                    """,
                fixturePath("presentation", "EntryUiState.kt") to
                    """
                    package org.mercurio.client.feature.entry.presentation

                    internal data class EntryUiState(
                        val title: String,
                        val rows: List<String> = emptyList(),
                    )
                    """,
                fixturePath("domain", "FixtureStore.kt") to
                    """
                    package org.mercurio.client.feature.entry.domain

                    internal interface FixtureStore
                    """,
                fixturePath("presentation", "EntryStateHolder.kt") to
                    """
                    package org.mercurio.client.feature.entry.presentation

                    import kotlinx.coroutines.flow.MutableStateFlow
                    import kotlinx.coroutines.flow.StateFlow
                    import kotlinx.coroutines.flow.asStateFlow

                    internal class FixtureStateHolder {
                        internal val mutableUiState = MutableStateFlow(EntryUiState(title = "fixture"))
                        val uiState: StateFlow<EntryUiState> = mutableUiState.asStateFlow()

                        fun onEvent(event: EntryUiEvent) {
                            mutableUiState.value = mutableUiState.value.copy(title = event.toString())
                        }
                    }
                    """,
                fixturePath("presentation", "EntryScreen.kt") to
                    """
                    package org.mercurio.client.feature.entry.presentation

                    import androidx.compose.runtime.Composable

                    @Composable
                    internal fun EntryScreen(
                        state: EntryUiState,
                        onEvent: (EntryUiEvent) -> Unit,
                    ) = Unit
                    """,
            )

        violation?.let { (path, content) -> files[path] = content }

        return files
    }

    private companion object {
        const val layerBoundaryRule = "architecture/layer-boundaries"
        const val domainReferenceRule = "architecture/domain-application-references"
        const val presentationReferenceRule = "architecture/presentation-untrusted-content"
        const val appExternalFileRule = "architecture/app-external-file-boundary"
        const val moduleDependencyRule = "architecture/module-dependency-declarations"
        const val composableContractRule = "udf/composables-take-state-and-events"
        const val stateWriterRule = "udf/one-state-writer"
        const val immutableStateRule = "udf/immutable-state-types"
        const val eventEntryRule = "udf/one-event-entry"

        const val layerBoundaryGuidance =
            "The dependency direction is inward, so a domain or application declaration must not reach an outer layer."
        const val domainReferenceGuidance =
            "Domain and application source must reference neither a framework, transport, serialization or injection package nor a concrete data, presentation or wiring type; declare a domain port instead."
        const val presentationReferenceGuidance =
            "Presentation renders untrusted Gateway content, so it must not reach a web view, an HTTP stack, or a link destination."
        const val appExternalFileGuidance =
            "A file handed to another app must be a read-only content URI from the declared, non-exported file provider, never a file path or a writable grant."
        const val moduleDependencyGuidance =
            "The domain and application module scripts must not declare a framework, transport, serialization, injection, or concrete-layer dependency."
        const val composableContractGuidance =
            "A presentation composable takes immutable state and one event callback, never the state holder or a port, so it cannot read or write state itself."
        const val stateWriterGuidance =
            "State is written in one place: the state holder files, through the event entry. Another file that writes the state flow, or a public mutable property on the state holder, is a second writer."
        const val immutableStateGuidance =
            "A UI state type is immutable: every property is a val and every collection is a read-only type, so comparing two states means something."
        const val eventEntryGuidance =
            "Events enter the feature at exactly one function that is not private, so no second path can mutate state behind the state holder."

        const val composableAnnotation = "Composable"
        const val eventTypeName = "EntryUiEvent"
        const val eventCallbackTypeFragment = "($eventTypeName) ->"
        const val mutableStateFlowType = "MutableStateFlow"
        const val stateHolderTypeSuffix = "StateHolder"
        const val stateHolderFilePrefix = "EntryStateHolder"
        const val uiStateTypeSuffix = "UiState"
        const val presentationPackageName = "org.mercurio.client.feature.entry.presentation"

        val mutableCollectionTypePattern = Regex("""\bMutable[A-Za-z0-9]*""")
        val arrayCollectionTypePattern = Regex("""\bArray\s*<""")

        const val domainSourceRoot = "feature/entry/domain/src/main/kotlin"
        const val applicationSourceRoot = "feature/entry/application/src/main/kotlin"
        const val presentationSourceRoot = "feature/entry/presentation/src/main/kotlin"

        val moduleBuildScripts =
            listOf(
                "feature/entry/domain/build.gradle.kts",
                "feature/entry/application/build.gradle.kts",
            )

        val forbiddenLayerPackages =
            listOf(
                "android",
                "androidx",
                "com.google.gson",
                "com.squareup.moshi",
                "dagger",
                "hilt",
                "io.ktor",
                "jakarta.inject",
                "javax.inject",
                "kotlinx.serialization",
                "okhttp3",
                "org.json",
                "org.koin",
                "retrofit2",
                "java.net",
                "org.mercurio.client.feature.entry.data",
                "org.mercurio.client.feature.entry.presentation",
                "org.mercurio.client.feature.entry.wiring",
            )

        val forbiddenLayerReference =
            Regex(
                "(?<![A-Za-z0-9_])(?:${forbiddenLayerPackages.joinToString(separator = "|", transform = Regex::escape)})\\.[A-Za-z_][A-Za-z0-9_.]*",
            )

        val presentationForbiddenReferences =
            listOf(
                Regex("""android\.webkit|androidx\.webkit"""),
                Regex("""java\.net\.|okhttp3\.|HttpURLConnection|URLConnection"""),
                Regex("""coil3\.network"""),
            )

        val appExternalFileForbiddenReferences =
            listOf(
                Regex("""Uri\.fromFile"""),
                Regex("""file://"""),
                Regex("""FLAG_GRANT_WRITE_URI_PERMISSION"""),
                Regex("""FLAG_GRANT_PERSISTABLE_URI_PERMISSION"""),
            )

        val forbiddenModuleDependencyTokens =
            listOf(
                "android.",
                "androidx",
                "androidx.",
                "com.android.",
                "compose",
                "com.google.gson",
                "com.squareup.moshi",
                "dagger",
                "hilt",
                "io.ktor",
                "gson",
                "jakarta.inject",
                "javax.inject",
                "kotlinx.serialization",
                "serialization",
                "ktor",
                "moshi",
                "okhttp3",
                "okhttp",
                "org.json",
                "org.koin",
                "koin",
                "retrofit2",
                "retrofit",
                "java.net",
                "project(\":feature:entry:data\")",
                "project(\":feature:entry:presentation\")",
                "project(\":feature:entry:wiring\")",
            )

        val forbiddenNamedProjectDependency =
            Regex(
                """project\s*\([^)]*\bpath\s*=\s*["']:(?:feature:entry:(?:data|presentation|wiring))["'][^)]*\)""",
            )
    }
}

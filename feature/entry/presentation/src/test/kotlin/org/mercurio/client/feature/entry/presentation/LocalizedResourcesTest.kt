package org.mercurio.client.feature.entry.presentation

import android.content.Context
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.Locale

/**
 * The localization scaffolding this module carries: copy lives in string resources,
 * a translated locale directory drops in without further refactoring, and a locale
 * that carries no translation of a string falls back to the base locale.
 */
@RunWith(RobolectricTestRunner::class)
class LocalizedResourcesTest {
    @Test
    fun every_string_resource_resolves_under_a_translated_locale() {
        val italian = translatedContext()
        val ids = stringResourceIds()

        assertTrue(ids.size > MINIMUM_EXPECTED_STRINGS)
        ids.forEach { id ->
            assertFalse("String resource ${resourceName(id)} is empty", italian.getString(id).isBlank())
        }
    }

    @Test
    fun the_shipped_scaffolding_translates_nothing_yet_and_falls_back_to_the_base_locale() {
        val base = RuntimeEnvironment.getApplication()
        val italian = translatedContext()

        stringResourceIds().forEach { id ->
            assertEquals(
                "String resource ${resourceName(id)} must fall back to the base locale",
                base.getString(id),
                italian.getString(id),
            )
        }
    }

    @Test
    fun the_locale_scaffolding_directory_exists_for_every_module_that_owns_copy() {
        val repositoryRoot = requireNotNull(System.getProperty("fixture.repositoryRoot"))

        LOCALE_RESOURCE_FILES.forEach { path ->
            val file = File(repositoryRoot, path)
            assertTrue("Missing locale scaffolding at $path", file.isFile)
            assertTrue("Locale scaffolding at $path declares no resources", file.length() > 0)
        }
    }

    private fun translatedContext(): Context {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.ITALIAN)
        return base.createConfigurationContext(configuration)
    }

    private fun stringResourceIds(): List<Int> =
        R.string::class.java.declaredFields.mapNotNull { field ->
            runCatching { field.getInt(null) }.getOrNull()
        }.distinct()

    private fun resourceName(id: Int): String =
        R.string::class.java.declaredFields.firstOrNull { field ->
            runCatching { field.getInt(null) == id }.getOrDefault(false)
        }?.name ?: id.toString()

    private companion object {
        const val MINIMUM_EXPECTED_STRINGS = 100

        val LOCALE_RESOURCE_FILES =
            listOf(
                "feature/entry/presentation/src/main/res/values-it/strings.xml",
                "feature/entry/wiring/src/main/res/values-it/strings.xml",
            )
    }
}

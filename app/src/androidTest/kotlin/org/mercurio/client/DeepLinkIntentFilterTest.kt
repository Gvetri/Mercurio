package org.mercurio.client

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepLinkIntentFilterTest {
    @Test
    fun both_canonical_links_resolve_to_the_main_activity_only() {
        listOf("mercurio://session/session-one", "mercurio://compose").forEach { raw ->
            assertEquals(
                "Resolved activities for $raw",
                listOf(MainActivity::class.java.name),
                resolve(raw),
            )
        }
    }

    @Test
    fun an_unbuilt_action_does_not_resolve_to_the_client() {
        assertTrue(resolve("mercurio://settings").isEmpty())
    }

    @Test
    fun a_foreign_scheme_does_not_resolve_to_the_client() {
        assertTrue(resolve("https://example.com/session/session-one").isEmpty())
    }

    private fun resolve(raw: String): List<String> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(raw))
                .addCategory(Intent.CATEGORY_DEFAULT)
                .addCategory(Intent.CATEGORY_BROWSABLE)
        return context.packageManager
            .queryIntentActivities(intent, 0)
            .filter { resolved -> resolved.activityInfo.packageName == context.packageName }
            .map { resolved -> resolved.activityInfo.name }
    }
}

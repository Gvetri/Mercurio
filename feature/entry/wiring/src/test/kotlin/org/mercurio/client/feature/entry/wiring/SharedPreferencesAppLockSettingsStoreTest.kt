package org.mercurio.client.feature.entry.wiring

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedPreferencesAppLockSettingsStoreTest {
    private val context: Application = RuntimeEnvironment.getApplication()

    @Test
    fun a_fresh_install_leaves_the_gate_off() {
        assertFalse(newStore().loadEnabled())
    }

    @Test
    fun the_decision_survives_a_new_store_instance() {
        newStore().saveEnabled(true)

        assertTrue(newStore().loadEnabled())
    }

    private fun newStore(): SharedPreferencesAppLockSettingsStore = SharedPreferencesAppLockSettingsStore(context)
}

package org.mercurio.client.feature.entry.wiring

import android.content.Context
import org.mercurio.client.feature.entry.domain.AppLockSettingsStore

/** SharedPreferences-backed persistence for the app-lock preference. */
class SharedPreferencesAppLockSettingsStore(
    context: Context,
) : AppLockSettingsStore {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loadEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    override fun saveEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "app_lock_settings"
        const val KEY_ENABLED = "enabled"
    }
}

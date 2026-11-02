package org.mercurio.client.feature.entry.wiring

import android.content.Context
import org.mercurio.client.feature.entry.domain.ImageLoadingSettingsStore

/** SharedPreferences-backed persistence for automatic picture loading. */
class SharedPreferencesImageLoadingSettingsStore(
    context: Context,
) : ImageLoadingSettingsStore {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun loadEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, true)

    override fun saveEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "image_loading_settings"
        const val KEY_ENABLED = "enabled"
    }
}

package org.mercurio.client

import androidx.test.platform.app.InstrumentationRegistry
import org.mercurio.client.feature.entry.domain.ErrorReportingConsent
import org.mercurio.client.feature.entry.wiring.AndroidKeyStoreGatewayCredentialStore
import org.mercurio.client.feature.entry.wiring.SharedPreferencesErrorReportingConsentStore
import org.mercurio.client.feature.entry.wiring.SharedPreferencesGatewayConnectionDataSource

/**
 * Device tests must not depend on state that an earlier run left on the same device: the app
 * persists a Gateway connection in SharedPreferences and the entry screen differs while it is
 * present, so a test that assumes the first-use screen fails only on a used emulator. The
 * error-reporting question is answered here because these tests cover the shell, not the question,
 * which keeps its own renderer baselines.
 */
internal fun prepareFirstUseInstall() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    SharedPreferencesGatewayConnectionDataSource(context).clearEndpoint()
    AndroidKeyStoreGatewayCredentialStore(context).clear()
    SharedPreferencesErrorReportingConsentStore(context).write(ErrorReportingConsent.Declined)
}

package org.mercurio.client.feature.entry.wiring

import android.content.Context
import androidx.biometric.BiometricManager
import org.mercurio.client.feature.entry.domain.AppLockEnrolment

/**
 * Reports whether this device can prove the user's identity with a biometric or its own credential.
 *
 * Below API 30 the platform reports a device credential as enrolled whenever the device is secure,
 * which is the same precondition the Settings row names.
 */
class AndroidAppLockEnrolment(
    context: Context,
) : AppLockEnrolment {
    private val biometricManager = BiometricManager.from(context)

    override fun isEnrolled(): Boolean {
        val status = biometricManager.canAuthenticate(ALLOWED_AUTHENTICATORS)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    private companion object {
        val ALLOWED_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

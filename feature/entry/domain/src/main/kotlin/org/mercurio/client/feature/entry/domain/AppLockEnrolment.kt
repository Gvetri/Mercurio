package org.mercurio.client.feature.entry.domain

/**
 * Whether this device can prove the user's identity through its own screen lock: a biometric or the
 * device credential (PIN, pattern, or password).
 *
 * The app lock cannot be turned on while no such proof exists, so the Settings row explains the
 * precondition and stays off.
 */
interface AppLockEnrolment {
    fun isEnrolled(): Boolean
}

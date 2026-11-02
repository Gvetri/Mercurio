package org.mercurio.client.feature.entry.domain

enum class InstallSource {
    /** Google Play installed the client, so Play owns its updates. */
    GOOGLE_PLAY,

    /** Any other source installed the client: a Nightly APK, a file manager, or a direct install. */
    OTHER_SOURCE,
}

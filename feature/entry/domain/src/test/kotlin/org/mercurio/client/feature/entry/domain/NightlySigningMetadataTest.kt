package org.mercurio.client.feature.entry.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SOURCE_SHA = "0123456789abcdef0123456789abcdef01234567"

private const val APK_SHA = "1111111111111111111111111111111111111111111111111111111111111111"

private const val CERTIFICATE_SHA = "2222222222222222222222222222222222222222222222222222222222222222"

private const val VERSION_CODE = 7002L

private const val VERSION_NAME = "nightly-7002-0123456789ab"

private const val RELEASE_TAG = "nightly-7002-$SOURCE_SHA"

class NightlySigningMetadataTest {
    @Test
    fun accepts_a_document_that_matches_its_release() {
        assertFalse(metadata().contradicts(RELEASE_TAG))
    }

    @Test
    fun refuses_a_tag_that_names_another_version_code() {
        assertTrue(metadata().contradicts("nightly-7003-$SOURCE_SHA"))
    }

    @Test
    fun refuses_a_tag_that_names_another_source() {
        assertTrue(metadata().contradicts("nightly-7002-${"f".repeat(40)}"))
    }

    @Test
    fun refuses_a_version_name_that_names_another_version_code() {
        assertTrue(metadata().copy(versionName = "nightly-7003-0123456789ab").contradicts(RELEASE_TAG))
    }

    @Test
    fun refuses_a_version_name_that_does_not_carry_the_first_twelve_source_characters() {
        assertTrue(metadata().copy(versionName = "nightly-7002-0123456789ac").contradicts(RELEASE_TAG))
    }

    @Test
    fun refuses_a_source_commit_that_is_not_a_full_lowercase_sha() {
        assertTrue(metadata().copy(sourceSha = "0123456789ABCDEF0123456789abcdef01234567").contradicts(RELEASE_TAG))
        assertTrue(metadata().copy(sourceSha = "0123456").contradicts(RELEASE_TAG))
    }

    @Test
    fun refuses_a_version_code_that_is_not_positive() {
        assertTrue(
            metadata()
                .copy(versionCode = 0L, versionName = "nightly-0-0123456789ab")
                .contradicts("nightly-0-$SOURCE_SHA"),
        )
    }

    @Test
    fun refuses_an_apk_digest_that_is_not_a_sha256() {
        assertTrue(metadata().copy(apkSha256 = "not-a-digest").contradicts(RELEASE_TAG))
    }

    @Test
    fun refuses_a_certificate_digest_that_is_not_a_sha256() {
        assertTrue(metadata().copy(certificateSha256 = "ABC").contradicts(RELEASE_TAG))
    }

    @Test
    fun accepts_a_build_with_no_native_libraries() {
        assertFalse(metadata().copy(nativeAbis = emptyList()).contradicts(RELEASE_TAG))
    }

    @Test
    fun accepts_a_document_whose_version_name_uses_the_twelve_character_prefix() {
        assertFalse(metadata().copy(versionName = VERSION_NAME).contradicts(RELEASE_TAG))
    }

    private fun metadata(): NightlySigningMetadata =
        NightlySigningMetadata(
            sourceSha = SOURCE_SHA,
            versionCode = VERSION_CODE,
            versionName = VERSION_NAME,
            nativeAbis = listOf("arm64-v8a"),
            apkSha256 = APK_SHA,
            certificateSha256 = CERTIFICATE_SHA,
        )
}

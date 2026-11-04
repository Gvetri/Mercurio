package org.mercurio.client.feature.entry.presentation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalRoborazziApi::class)
class GatewayDesignStatesTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val roborazziRule = RoborazziRule()

    @Test
    fun gateway_01_empty_form_light() = capture(formState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_01_empty_form_dark() = capture(formState(), dark = true)

    @Test
    fun gateway_02_address_entering_light() = capture(typingState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_02_address_entering_dark() = capture(typingState(), dark = true)

    @Test
    fun gateway_03_credential_hidden_light() = capture(filledFormState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_03_credential_hidden_dark() = capture(filledFormState(), dark = true)

    @Test
    fun gateway_04_credential_shown_light() = capture(filledFormState(), before = { revealCredential() })

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_04_credential_shown_dark() = capture(filledFormState(), dark = true, before = { revealCredential() })

    @Test
    fun gateway_05_verifying_light() = capture(verifyingState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_05_verifying_dark() = capture(verifyingState(), dark = true)

    @Test
    fun gateway_06_verification_failed_network_light() = capture(networkFailure())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_06_verification_failed_network_dark() = capture(networkFailure(), dark = true)

    @Test
    fun gateway_07_verification_failed_credential_light() = capture(credentialRejected())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_07_verification_failed_credential_dark() = capture(credentialRejected(), dark = true)

    @Test
    fun gateway_08_verification_failed_capability_light() = capture(capabilityMissing())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_08_verification_failed_capability_dark() = capture(capabilityMissing(), dark = true)

    @Test
    fun gateway_09_verified_saving_light() = capture(savingState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_09_verified_saving_dark() = capture(savingState(), dark = true)

    @Test
    fun gateway_10_save_failed_light() = capture(storageFailure())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_10_save_failed_dark() = capture(storageFailure(), dark = true)

    @Test
    fun gateway_11_saved_connection_light() = capture(savedState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_11_saved_connection_dark() = capture(savedState(), dark = true)

    @Test
    fun gateway_12_saved_connection_menu_light() = capture(savedState(), before = { openConnectionMenu() })

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_12_saved_connection_menu_dark() = capture(savedState(), dark = true, before = { openConnectionMenu() })

    @Test
    fun gateway_13_reconnected_light() = capture(savedState(reconnected = true))

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_13_reconnected_dark() = capture(savedState(reconnected = true), dark = true)

    @Test
    fun gateway_14_change_credential_light() = capture(changingState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_14_change_credential_dark() = capture(changingState(), dark = true)

    @Test
    fun gateway_15_remove_confirmation_light() = capture(removeConfirmationState(), dialog = true)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_15_remove_confirmation_dark() = capture(removeConfirmationState(), dark = true, dialog = true)

    @Test
    fun gateway_16_long_error_light() = capture(longError())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_16_long_error_dark() = capture(longError(), dark = true)

    @Test
    @Config(qualifiers = "w360dp-h640dp-notnight")
    fun gateway_17_font_scale_200_top_light() = captureAtTwoHundredPercent()

    @Test
    @Config(qualifiers = "w360dp-h640dp-night")
    fun gateway_17_font_scale_200_top_dark() = captureAtTwoHundredPercent(dark = true)

    @Test
    @Config(qualifiers = "w360dp-h640dp-notnight")
    fun gateway_18_font_scale_200_action_light() = captureAtTwoHundredPercent(before = { scrollToAction() })

    @Test
    @Config(qualifiers = "w360dp-h640dp-night")
    fun gateway_18_font_scale_200_action_dark() = captureAtTwoHundredPercent(dark = true, before = { scrollToAction() })

    @Test
    fun gateway_19_reconnect_failed_light() = capture(reconnectFailedState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun gateway_19_reconnect_failed_dark() = capture(reconnectFailedState(), dark = true)

    @Test
    @Config(qualifiers = "w1000dp-h800dp-notnight")
    fun gateway_wide_saved_connection_light() = capture(savedState())

    @Test
    @Config(qualifiers = "w1000dp-h800dp-night")
    fun gateway_wide_saved_connection_dark() = capture(savedState(), dark = true)

    private fun capture(
        state: EntryUiState,
        dark: Boolean = false,
        fontScale: Float = 1f,
        dialog: Boolean = false,
        before: () -> Unit = {},
    ) {
        composeTestRule.activity.actionBar?.hide()
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                HermesTheme(darkTheme = dark) {
                    EntryScreen(state = state, onEvent = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        before()
        composeTestRule.waitForIdle()
        if (dialog) {
            composeTestRule.onNode(isDialog()).captureRoboImage()
        } else {
            captureScreenRoboImage()
        }
    }

    private fun captureAtTwoHundredPercent(
        dark: Boolean = false,
        before: () -> Unit = {},
    ) = capture(formState(endpoint = "https://gateway.example.com"), dark = dark, fontScale = 2f, before = before)

    private fun revealCredential() {
        composeTestRule.onNodeWithText("[ show ]").performClick()
    }

    private fun openConnectionMenu() {
        composeTestRule.onNode(hasContentDescription("Connection actions")).performClick()
    }

    private fun scrollToAction() {
        composeTestRule.onNodeWithText("Connect").performScrollTo()
    }

    private fun formState(
        endpoint: String = "",
        credential: String = "",
        isVerifying: Boolean = false,
        isSaving: Boolean = false,
        errorCategory: EntryErrorCategory? = null,
    ): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_verify_new),
            supportingText = UiText.Resource(R.string.entry_supporting_enter_credentials),
            actionLabel = UiText.Resource(R.string.entry_action_verify),
            connectionSetupRequested = true,
            endpoint = endpoint,
            bearerCredential = credential,
            isVerifying = isVerifying,
            isSaving = isSaving,
            errorCategory = errorCategory,
        )

    private fun typingState(): EntryUiState = formState(endpoint = "https://gateway.e")

    private fun filledFormState(
        isVerifying: Boolean = false,
        isSaving: Boolean = false,
        errorCategory: EntryErrorCategory? = null,
    ): EntryUiState =
        formState(
            endpoint = "https://gateway.example.com",
            credential = "mb_test_0a1b2c3d4e5f",
            isVerifying = isVerifying,
            isSaving = isSaving,
            errorCategory = errorCategory,
        )

    private fun verifyingState(): EntryUiState = filledFormState(isVerifying = true)

    private fun savingState(): EntryUiState = filledFormState(isSaving = true)

    private fun networkFailure(): EntryUiState {
        val category = EntryErrorCategory.GATEWAY_REQUEST_FAILED
        return filledFormState(errorCategory = category)
    }

    private fun credentialRejected(): EntryUiState {
        val category = EntryErrorCategory.AUTHENTICATION_FAILED
        return filledFormState(errorCategory = category)
    }

    private fun capabilityMissing(): EntryUiState {
        val category = EntryErrorCategory.REQUIRED_FEATURE_UNAVAILABLE
        return filledFormState(errorCategory = category)
    }

    private fun storageFailure(): EntryUiState {
        val category = EntryErrorCategory.CREDENTIAL_STORAGE_FAILED
        return filledFormState(errorCategory = category)
    }

    private fun longError(): EntryUiState {
        val category = EntryErrorCategory.SECURE_CONNECTION_FAILED
        return filledFormState(errorCategory = category)
    }

    private fun removeConfirmationState(): EntryUiState = savedState(removeConfirmationOpen = true)

    private fun savedState(
        reconnected: Boolean = false,
        removeConfirmationOpen: Boolean = false,
    ): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_connected),
            supportingText = UiText.Value(""),
            actionLabel = UiText.Value(""),
            isConnected = true,
            isGatewayConnectionConfigured = true,
            endpoint = "https://gateway.example.com",
            gatewayFacts =
                if (reconnected) {
                    UiText.Resource(R.string.gateway_facts_reconnected, listOf("09:41"))
                } else {
                    UiText.Resource(R.string.gateway_facts_verified, listOf("09:41"))
                },
            isRemoveConfirmationOpen = removeConfirmationOpen,
        )

    private fun changingState(): EntryUiState =
        EntryUiState(
            title = UiText.Resource(R.string.entry_title_change_credential),
            supportingText = UiText.Resource(R.string.entry_supporting_change_credential),
            actionLabel = UiText.Resource(R.string.entry_action_verify_and_save),
            connectionSetupRequested = true,
            isChangingCredential = true,
            isConnected = true,
            isGatewayConnectionConfigured = true,
            endpoint = "https://gateway.example.com",
            gatewayFacts = UiText.Resource(R.string.gateway_facts_verified, listOf("09:41")),
        )

    private fun reconnectFailedState(): EntryUiState =
        networkFailure().copy(
            isGatewayConnectionConfigured = true,
            gatewayFacts = UiText.Resource(R.string.gateway_facts_last_connected, listOf("09:41")),
        )
}

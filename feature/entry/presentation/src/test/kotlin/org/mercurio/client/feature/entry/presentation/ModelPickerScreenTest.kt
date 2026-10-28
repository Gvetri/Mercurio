package org.mercurio.client.feature.entry.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mercurio.client.feature.entry.domain.ModelSelection
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ModelPickerScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun the_control_shows_only_with_an_advertised_loaded_catalog_and_labels_the_effective_model() {
        val picker =
            ModelPickerUiState(
                capabilityKnown = true,
                capabilityAdvertised = true,
                catalog = pickerEffortCatalog(),
                selection = ModelSelection("alpha", "high"),
            )
        setContent(picker)

        composeTestRule.onNodeWithTag("composer-model-picker").assertIsDisplayed().assertTextEquals("First Model")
        composeTestRule.onNodeWithText(MODEL_CATALOG_UNAVAILABLE_COPY).assertDoesNotExist()
    }

    @Test
    fun the_control_renders_the_server_default_label_when_no_override_is_active() {
        val picker =
            ModelPickerUiState(
                capabilityKnown = true,
                capabilityAdvertised = true,
                catalog = pickerEffortCatalog(),
            )
        setContent(picker)

        composeTestRule.onNodeWithTag("composer-model-picker").assertTextEquals("Server default")
    }

    @Test
    fun a_gateway_without_the_row_hides_the_control_and_shows_the_truthful_copy() {
        setContent(ModelPickerUiState(capabilityKnown = true, capabilityAdvertised = false))

        composeTestRule.onNodeWithTag("composer-model-picker").assertDoesNotExist()
        composeTestRule.onNodeWithText(MODEL_CATALOG_UNAVAILABLE_COPY).assertIsDisplayed()
    }

    @Test
    fun a_loading_catalog_state_leaves_no_dead_control() {
        setContent(
            ModelPickerUiState(
                capabilityKnown = true,
                capabilityAdvertised = true,
                isFetchingCatalog = true,
            ),
        )
        composeTestRule.onNodeWithTag("composer-model-picker").assertDoesNotExist()
        composeTestRule.onNodeWithText(MODEL_CATALOG_UNAVAILABLE_COPY).assertDoesNotExist()
    }

    @Test
    fun a_failed_catalog_state_leaves_no_dead_control() {
        setContent(
            ModelPickerUiState(
                capabilityKnown = true,
                capabilityAdvertised = true,
                catalogFetchFailed = true,
            ),
        )
        composeTestRule.onNodeWithTag("composer-model-picker").assertDoesNotExist()
        composeTestRule.onNodeWithText(MODEL_CATALOG_UNAVAILABLE_COPY).assertDoesNotExist()
    }

    @Test
    fun opening_the_control_emits_the_picker_event() {
        val events = mutableListOf<EntryUiEvent>()
        setContent(ModelPickerUiState(capabilityAdvertised = true, catalog = pickerEffortCatalog()), events::add)

        composeTestRule.onNodeWithTag("composer-model-picker").performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.ModelPickerClicked), events)
    }

    @Test
    fun the_open_picker_orders_favorites_then_recents_then_catalog_with_the_default_first() {
        setContent(
            ModelPickerUiState(
                capabilityAdvertised = true,
                catalog = pickerEffortCatalog(),
                favorites = setOf("beta"),
                recents = listOf("gamma", "alpha"),
                isPickerOpen = true,
            ),
        )

        composeTestRule.onNodeWithTag("model-row-server-default").assertIsSelected()
        composeTestRule.onNodeWithTag("model-row-beta").assertIsNotSelected()
        val tags =
            listOf(
                "model-row-server-default",
                "model-row-beta",
                "model-row-gamma",
                "model-row-alpha",
                "model-row-delta",
            )
        assertEquals(tags, tags.sortedBy { topOf(it) })
    }

    @Test
    fun rows_select_emit_ids_and_the_favorite_toggle_flips_independent_of_selection() {
        val events = mutableListOf<EntryUiEvent>()
        setContent(
            ModelPickerUiState(
                capabilityAdvertised = true,
                catalog = pickerEffortCatalog(),
                selection = ModelSelection("alpha"),
                isPickerOpen = true,
            ),
            events::add,
        )

        composeTestRule.onNodeWithTag("model-row-server-default").performClick()
        composeTestRule.onNodeWithTag("model-row-beta").performClick()
        composeTestRule.onNodeWithTag("model-favorite-alpha").performClick()

        assertEquals(
            listOf<EntryUiEvent>(
                EntryUiEvent.ServerDefaultModelClicked,
                EntryUiEvent.ModelRowClicked("beta"),
                EntryUiEvent.ModelFavoriteClicked("alpha", true),
            ),
            events,
        )
    }

    @Test
    fun effort_rows_render_only_for_the_advertising_selected_entry_in_served_order_with_default_preselected() {
        setContent(
            ModelPickerUiState(
                capabilityAdvertised = true,
                catalog = pickerEffortCatalog(),
                selection = ModelSelection("alpha"),
                isPickerOpen = true,
            ),
        )

        composeTestRule.onNodeWithText("Reasoning effort").assertIsDisplayed()
        composeTestRule.onNodeWithTag("model-effort-low").assertIsDisplayed()
        composeTestRule.onNodeWithTag("model-effort-medium").assertIsSelected()
        composeTestRule.onNodeWithTag("model-effort-high").assertIsNotSelected()
        val effortTags = listOf("model-effort-low", "model-effort-medium", "model-effort-high")
        assertEquals(effortTags, effortTags.sortedBy { topOf(it) })
    }

    @Test
    fun effort_rows_are_absent_for_a_selected_entry_without_efforts() {
        setContent(
            ModelPickerUiState(
                capabilityAdvertised = true,
                catalog = pickerEffortCatalog(),
                selection = ModelSelection("beta"),
                isPickerOpen = true,
            ),
        )
        composeTestRule.onNodeWithText("Reasoning effort").assertDoesNotExist()
    }

    @Test
    fun effort_rows_are_unrepresentable_without_a_selected_model() {
        setContent(
            ModelPickerUiState(
                capabilityAdvertised = true,
                catalog = pickerEffortCatalog(),
                selection = null,
                isPickerOpen = true,
            ),
        )
        composeTestRule.onNodeWithText("Reasoning effort").assertDoesNotExist()
    }

    @Test
    fun clicking_an_effort_row_emits_the_effort_value() {
        val events = mutableListOf<EntryUiEvent>()
        setContent(
            ModelPickerUiState(
                capabilityAdvertised = true,
                catalog = pickerEffortCatalog(),
                selection = ModelSelection("alpha"),
                isPickerOpen = true,
            ),
            events::add,
        )

        composeTestRule.onNodeWithTag("model-effort-high").performClick()

        assertEquals(listOf<EntryUiEvent>(EntryUiEvent.ModelEffortClicked("high")), events)
    }

    @Test
    fun a_refused_model_shows_its_actionable_copy_and_not_the_effort_one() {
        setContent(
            ModelPickerUiState(capabilityAdvertised = true, catalog = pickerEffortCatalog()),
            sendErrorCategory = MessageSendErrorCategory.MODEL_REFUSED,
        )
        composeTestRule.onNodeWithText(MessageSendErrorCategory.MODEL_REFUSED.safeMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(MessageSendErrorCategory.EFFORT_REFUSED.safeMessage).assertDoesNotExist()
    }

    @Test
    fun a_refused_effort_shows_its_actionable_copy_and_not_the_model_one() {
        setContent(
            ModelPickerUiState(capabilityAdvertised = true, catalog = pickerEffortCatalog()),
            sendErrorCategory = MessageSendErrorCategory.EFFORT_REFUSED,
        )
        composeTestRule.onNodeWithText(MessageSendErrorCategory.EFFORT_REFUSED.safeMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(MessageSendErrorCategory.MODEL_REFUSED.safeMessage).assertDoesNotExist()
    }

    private fun setContent(
        picker: ModelPickerUiState,
        onEvent: (EntryUiEvent) -> Unit = {},
        sendErrorCategory: MessageSendErrorCategory? = null,
    ) {
        composeTestRule.setContent {
            HermesTheme {
                EntryScreen(state = hermesState(picker, sendErrorCategory), onEvent = onEvent)
            }
        }
    }

    private fun topOf(tag: String): Dp = composeTestRule.onNodeWithTag(tag).getBoundsInRoot().top

    private fun hermesState(
        picker: ModelPickerUiState,
        sendErrorCategory: MessageSendErrorCategory? = null,
    ): EntryUiState =
        entryState(
            SessionListUiState(
                sessions = listOf(session("first", "First Session")),
                openedSession =
                    OpenSessionUiState(
                        session = session("first", "First Session"),
                        messages = emptyList(),
                        sendErrorCategory = sendErrorCategory,
                    ),
                modelPicker = picker,
            ),
        )
}

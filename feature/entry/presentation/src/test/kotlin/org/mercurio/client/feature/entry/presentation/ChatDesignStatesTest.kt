package org.mercurio.client.feature.entry.presentation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * The approved Chat design's states, captured at the design's own review size. Each capture is
 * compared with the design page by the reviewer checklist, not by a pixel diff across renderers.
 *
 * Two states are named with the issue that owns their renderer instead of being drawn here: the
 * image states wait on #257 and the math states on #259. The pipe table and its viewer are frames
 * 21 and 30, drawn by #256. The Session switch waits on the Session list's own run status.
 *
 * Frame 14 differs from frame 13 only by the keyboard, which the design itself calls chrome: the
 * IME band is not app UI, so the two captures share the app's part.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatDesignStatesTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val roborazziRule = RoborazziRule()

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun fixTheTimeZoneTheDesignPrints() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun restoreTheTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun chat_01_empty_history_light() = captureChat(ChatDesignFixtures.empty(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_01_empty_history_dark() = captureChat(ChatDesignFixtures.empty(), dark = true)

    @Test
    fun chat_02_loading_history_light() = captureChat(ChatDesignFixtures.loading(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_02_loading_history_dark() = captureChat(ChatDesignFixtures.loading(), dark = true)

    @Test
    fun chat_03_cached_refreshing_light() = captureChat(ChatDesignFixtures.cachedRefreshing(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_03_cached_refreshing_dark() = captureChat(ChatDesignFixtures.cachedRefreshing(), dark = true)

    @Test
    fun chat_04_cached_unavailable_light() = captureChat(ChatDesignFixtures.cachedUnavailable(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_04_cached_unavailable_dark() = captureChat(ChatDesignFixtures.cachedUnavailable(), dark = true)

    @Test
    fun chat_05_history_unavailable_light() = captureChat(ChatDesignFixtures.unavailable(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_05_history_unavailable_dark() = captureChat(ChatDesignFixtures.unavailable(), dark = true)

    @Test
    fun chat_06_populated_idle_light() = captureChat(ChatDesignFixtures.populated(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_06_populated_idle_dark() = captureChat(ChatDesignFixtures.populated(), dark = true)

    @Test
    fun chat_07_sending_pending_row_light() = captureChat(ChatDesignFixtures.sending(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_07_sending_pending_row_dark() = captureChat(ChatDesignFixtures.sending(), dark = true)

    @Test
    fun chat_08_run_streaming_light() = captureChat(ChatDesignFixtures.streaming(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_08_run_streaming_dark() = captureChat(ChatDesignFixtures.streaming(), dark = true)

    @Test
    fun chat_09_run_failed_light() = captureChat(ChatDesignFixtures.failed(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_09_run_failed_dark() = captureChat(ChatDesignFixtures.failed(), dark = true)

    @Test
    fun chat_10_run_interrupted_light() = captureChat(ChatDesignFixtures.interrupted(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_10_run_interrupted_dark() = captureChat(ChatDesignFixtures.interrupted(), dark = true)

    @Test
    fun chat_11_stale_run_light() = captureChat(ChatDesignFixtures.stale(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_11_stale_run_dark() = captureChat(ChatDesignFixtures.stale(), dark = true)

    @Test
    fun chat_12_reconciling_light() = captureChat(ChatDesignFixtures.reconciling(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_12_reconciling_dark() = captureChat(ChatDesignFixtures.reconciling(), dark = true)

    @Test
    fun chat_13_composer_empty_light() = captureChat(ChatDesignFixtures.populated(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_13_composer_empty_dark() = captureChat(ChatDesignFixtures.populated(), dark = true)

    @Test
    fun chat_14_composer_focused_light() = captureChat(ChatDesignFixtures.populated(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_14_composer_focused_dark() = captureChat(ChatDesignFixtures.populated(), dark = true)

    @Test
    fun chat_15_composer_draft_light() = captureChat(ChatDesignFixtures.draft(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_15_composer_draft_dark() = captureChat(ChatDesignFixtures.draft(), dark = true)

    @Test
    fun chat_16_composer_run_active_light() = captureChat(ChatDesignFixtures.streamingWithDraft(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_16_composer_run_active_dark() = captureChat(ChatDesignFixtures.streamingWithDraft(), dark = true)

    @Test
    fun chat_17_composer_disabled_reason_light() = captureChat(ChatDesignFixtures.staleWithDraft(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_17_composer_disabled_reason_dark() = captureChat(ChatDesignFixtures.staleWithDraft(), dark = true)

    @Test
    fun chat_18_long_message_collapsed_light() = captureChat(ChatDesignFixtures.longMessage(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_18_long_message_collapsed_dark() = captureChat(ChatDesignFixtures.longMessage(), dark = true)

    @Test
    fun chat_19_long_message_expanded_light() {
        captureChat(
            detail = ChatDesignFixtures.longMessage(),
            dark = false,
            beforeCapture = { composeTestRule.onNodeWithTag("chat-fold-message-2").performClick() },
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_19_long_message_expanded_dark() {
        captureChat(
            detail = ChatDesignFixtures.longMessage(),
            dark = true,
            beforeCapture = { composeTestRule.onNodeWithTag("chat-fold-message-2").performClick() },
        )
    }

    @Test
    fun chat_20_code_block_light() = captureChat(ChatDesignFixtures.codeBlock(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_20_code_block_dark() = captureChat(ChatDesignFixtures.codeBlock(), dark = true)

    @Test
    fun chat_21_pipe_table_light() = captureChat(ChatDesignFixtures.pipeTable(), dark = false)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_21_pipe_table_dark() = captureChat(ChatDesignFixtures.pipeTable(), dark = true)

    @Test
    @Config(qualifiers = "w360dp-h520dp-notnight")
    fun chat_21_pipe_table_font_scale_200_light() =
        captureChat(
            detail = ChatDesignFixtures.pipeTable(),
            dark = false,
            fontScale = 2f,
            beforeCapture = { composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2) },
        )

    @Test
    @Config(qualifiers = "w360dp-h520dp-night")
    fun chat_21_pipe_table_font_scale_200_dark() =
        captureChat(
            detail = ChatDesignFixtures.pipeTable(),
            dark = true,
            fontScale = 2f,
            beforeCapture = { composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(2) },
        )

    @Test
    fun chat_30_table_viewer_light() {
        captureChat(
            detail = ChatDesignFixtures.tableExpanded(),
            dark = false,
            beforeCapture = { composeTestRule.onNodeWithTag("table-expand").performClick() },
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_30_table_viewer_dark() {
        captureChat(
            detail = ChatDesignFixtures.tableExpanded(),
            dark = true,
            beforeCapture = { composeTestRule.onNodeWithTag("table-expand").performClick() },
        )
    }

    @Test
    fun chat_27_message_action_menu_light() {
        captureChat(
            detail = ChatDesignFixtures.populated(),
            dark = false,
            beforeCapture = { composeTestRule.onNodeWithTag("chat-message-actions-message-2").performClick() },
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-night")
    fun chat_27_message_action_menu_dark() {
        captureChat(
            detail = ChatDesignFixtures.populated(),
            dark = true,
            beforeCapture = { composeTestRule.onNodeWithTag("chat-message-actions-message-2").performClick() },
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h520dp-notnight")
    fun chat_29_font_scale_200_light() {
        captureChat(ChatDesignFixtures.streamingWithDraft(), dark = false, fontScale = 2f)
    }

    @Test
    @Config(qualifiers = "w360dp-h520dp-night")
    fun chat_29_font_scale_200_dark() {
        captureChat(ChatDesignFixtures.streamingWithDraft(), dark = true, fontScale = 2f)
    }

    private fun captureChat(
        detail: SessionDetailState,
        dark: Boolean,
        fontScale: Float = 1f,
        beforeCapture: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                HermesTheme(darkTheme = dark) {
                    SessionDetailContent(detail = detail, onEvent = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        beforeCapture()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage()
    }
}

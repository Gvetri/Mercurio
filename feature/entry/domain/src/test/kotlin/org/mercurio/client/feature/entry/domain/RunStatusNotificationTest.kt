package org.mercurio.client.feature.entry.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunStatusNotificationTest {
    @Test
    fun only_succeeded_and_failed_are_notifiable_terminal_states() {
        assertTrue(RunPresentationState.SUCCEEDED.isNotifiableTerminal())
        assertTrue(RunPresentationState.FAILED.isNotifiableTerminal())
        assertFalse(RunPresentationState.CANCELLED.isNotifiableTerminal())
        assertFalse(RunPresentationState.UNCERTAIN.isNotifiableTerminal())
        assertFalse(RunPresentationState.STARTING.isNotifiableTerminal())
        assertFalse(RunPresentationState.RUNNING.isNotifiableTerminal())
        assertFalse(RunPresentationState.COMPLETING.isNotifiableTerminal())
    }

    @Test
    fun notification_requires_enabled_preference_platform_permission_and_terminal_state() {
        assertTrue(
            shouldPostRunStatusNotification(
                enabled = true,
                canPost = true,
                state = RunPresentationState.SUCCEEDED,
            ),
        )
        assertTrue(
            shouldPostRunStatusNotification(
                enabled = true,
                canPost = true,
                state = RunPresentationState.FAILED,
            ),
        )
        assertFalse(
            shouldPostRunStatusNotification(
                enabled = false,
                canPost = true,
                state = RunPresentationState.SUCCEEDED,
            ),
        )
        assertFalse(
            shouldPostRunStatusNotification(
                enabled = true,
                canPost = false,
                state = RunPresentationState.SUCCEEDED,
            ),
        )
        assertFalse(
            shouldPostRunStatusNotification(
                enabled = true,
                canPost = true,
                state = RunPresentationState.CANCELLED,
            ),
        )
        assertFalse(
            shouldPostRunStatusNotification(
                enabled = true,
                canPost = true,
                state = RunPresentationState.UNCERTAIN,
            ),
        )
        assertFalse(
            shouldPostRunStatusNotification(
                enabled = true,
                canPost = true,
                state = RunPresentationState.RUNNING,
            ),
        )
    }

    @Test
    fun a_run_that_is_still_running_is_never_notifiable() {
        val run = Run(RunId("run-with-sensitive-ids"), SessionId("session-secret"), "running")
        assertFalse(run.toRunPresentationState().isNotifiableTerminal())
    }
}

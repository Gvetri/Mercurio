package org.mercurio.client.feature.entry.presentation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mercurio.client.feature.entry.application.EntryState
import org.mercurio.client.feature.entry.domain.AppLockEnrolment
import org.mercurio.client.feature.entry.domain.AppLockSettingsStore

class EntryStateHolderAppLockTest {
    @Test
    fun a_fresh_install_leaves_the_switch_off_and_the_content_open() {
        val holder = appLockHolder()

        val lock = holder.uiState.value.appLock

        assertFalse(lock.isEnabled)
        assertFalse(lock.isLocked)
        assertTrue(lock.isAvailable)
        assertFalse(lock.isUnlockRequired)
    }

    @Test
    fun a_saved_switch_opens_the_gate_already_locked() {
        val holder = appLockHolder(store = FakeAppLockSettingsStore(enabled = true))

        val lock = holder.uiState.value.appLock

        assertTrue(lock.isEnabled)
        assertTrue(lock.isLocked)
        assertTrue(lock.isUnlockRequired)
    }

    @Test
    fun a_saved_switch_without_an_enrolled_screen_lock_clears_the_preference() {
        val store = FakeAppLockSettingsStore(enabled = true)
        val holder = appLockHolder(store = store, enrolment = FakeAppLockEnrolment(enrolled = false))

        val lock = holder.uiState.value.appLock

        assertFalse(lock.isEnabled)
        assertFalse(lock.isLocked)
        assertFalse(lock.isAvailable)
        assertEquals(listOf(false), store.saves)
    }

    @Test
    fun turning_the_switch_on_saves_it_and_keeps_the_current_visit_open() {
        val store = FakeAppLockSettingsStore()
        val holder = appLockHolder(store = store)

        holder.onEvent(EntryUiEvent.AppLockToggleClicked)
        runBlocking { holder.awaitStateChangesApplied() }

        assertTrue(holder.uiState.value.appLock.isEnabled)
        assertFalse(holder.uiState.value.appLock.isLocked)
        assertEquals(listOf(true), store.saves)
    }

    @Test
    fun the_switch_stays_off_while_the_device_has_no_screen_lock() {
        val store = FakeAppLockSettingsStore()
        val holder = appLockHolder(store = store, enrolment = FakeAppLockEnrolment(enrolled = false))

        holder.onEvent(EntryUiEvent.AppLockToggleClicked)
        runBlocking { holder.awaitStateChangesApplied() }

        assertFalse(holder.uiState.value.appLock.isEnabled)
        assertTrue(store.saves.isEmpty())
    }

    @Test
    fun turning_the_switch_off_saves_it_and_releases_the_gate() {
        val store = FakeAppLockSettingsStore(enabled = true)
        val holder = appLockHolder(store = store)

        holder.onEvent(EntryUiEvent.AppLockToggleClicked)
        runBlocking { holder.awaitStateChangesApplied() }

        assertFalse(holder.uiState.value.appLock.isEnabled)
        assertFalse(holder.uiState.value.appLock.isLocked)
        assertEquals(listOf(false), store.saves)
    }

    @Test
    fun a_gated_app_locks_when_it_leaves_the_foreground() {
        val holder = appLockHolder(store = FakeAppLockSettingsStore(enabled = true))

        holder.onEvent(EntryUiEvent.AppEnteredBackground)
        runBlocking { holder.awaitStateChangesApplied() }

        assertTrue(holder.uiState.value.appLock.isLocked)
    }

    @Test
    fun an_open_gate_never_locks() {
        val holder = appLockHolder()

        holder.onEvent(EntryUiEvent.AppEnteredBackground)
        runBlocking { holder.awaitStateChangesApplied() }

        assertFalse(holder.uiState.value.appLock.isLocked)
    }

    @Test
    fun the_first_foreground_of_a_gated_visit_asks_for_the_device_proof_once() {
        var requests = 0
        val holder =
            appLockHolder(
                store = FakeAppLockSettingsStore(enabled = true),
                onUnlockRequested = { requests += 1 },
            )

        holder.onEvent(EntryUiEvent.AppEnteredForeground)
        runBlocking { holder.awaitStateChangesApplied() }

        assertEquals(1, requests)
        assertTrue(holder.uiState.value.appLock.isLocked)
    }

    @Test
    fun a_second_foreground_while_the_proof_is_pending_does_not_ask_again() {
        var requests = 0
        val holder =
            appLockHolder(
                store = FakeAppLockSettingsStore(enabled = true),
                onUnlockRequested = { requests += 1 },
            )

        holder.onEvent(EntryUiEvent.AppEnteredForeground)
        runBlocking { holder.awaitStateChangesApplied() }
        holder.onEvent(EntryUiEvent.AppEnteredBackground)
        runBlocking { holder.awaitStateChangesApplied() }
        holder.onEvent(EntryUiEvent.AppEnteredForeground)
        runBlocking { holder.awaitStateChangesApplied() }

        assertEquals(1, requests)
    }

    @Test
    fun an_open_gate_asks_for_no_proof_on_foreground() {
        var requests = 0
        val holder = appLockHolder(onUnlockRequested = { requests += 1 })

        holder.onEvent(EntryUiEvent.AppEnteredForeground)
        runBlocking { holder.awaitStateChangesApplied() }

        assertEquals(0, requests)
    }

    @Test
    fun a_dismissed_proof_keeps_the_content_hidden_and_the_unlock_action_asks_again() {
        var requests = 0
        val holder =
            appLockHolder(
                store = FakeAppLockSettingsStore(enabled = true),
                onUnlockRequested = { requests += 1 },
            )

        holder.onEvent(EntryUiEvent.AppEnteredForeground)
        runBlocking { holder.awaitStateChangesApplied() }
        holder.onEvent(EntryUiEvent.AppLockAuthenticationResult(authenticated = false))
        runBlocking { holder.awaitStateChangesApplied() }

        assertTrue(holder.uiState.value.appLock.isUnlockRequired)

        holder.onEvent(EntryUiEvent.AppUnlockRequested)
        runBlocking { holder.awaitStateChangesApplied() }

        assertEquals(2, requests)
        assertTrue(holder.uiState.value.appLock.isUnlockRequired)
    }

    @Test
    fun an_accepted_proof_shows_the_content() {
        val holder = appLockHolder(store = FakeAppLockSettingsStore(enabled = true))

        holder.onEvent(EntryUiEvent.AppEnteredForeground)
        runBlocking { holder.awaitStateChangesApplied() }
        holder.onEvent(EntryUiEvent.AppLockAuthenticationResult(authenticated = true))
        runBlocking { holder.awaitStateChangesApplied() }

        assertTrue(holder.uiState.value.appLock.isEnabled)
        assertFalse(holder.uiState.value.appLock.isLocked)
        assertFalse(holder.uiState.value.appLock.isUnlockRequired)
    }

    @Test
    fun a_foreground_visit_turns_the_gate_off_when_the_screen_lock_is_gone() {
        val enrolment = FakeAppLockEnrolment(enrolled = true)
        val store = FakeAppLockSettingsStore(enabled = true)
        val holder = appLockHolder(store = store, enrolment = enrolment)

        enrolment.enrolled = false
        holder.onEvent(EntryUiEvent.AppEnteredForeground)
        runBlocking { holder.awaitStateChangesApplied() }

        assertFalse(holder.uiState.value.appLock.isEnabled)
        assertFalse(holder.uiState.value.appLock.isUnlockRequired)
        assertEquals(listOf(false), store.saves)
    }

    @Test
    fun a_foreground_visit_notices_a_newly_set_screen_lock() {
        val enrolment = FakeAppLockEnrolment(enrolled = false)
        val holder = appLockHolder(enrolment = enrolment)

        assertFalse(holder.uiState.value.appLock.isAvailable)

        enrolment.enrolled = true
        holder.onEvent(EntryUiEvent.AppEnteredForeground)
        runBlocking { holder.awaitStateChangesApplied() }

        assertTrue(holder.uiState.value.appLock.isAvailable)
    }

    @Test
    fun removing_the_gateway_connection_keeps_the_gate_on() {
        val holder = appLockHolder(store = FakeAppLockSettingsStore(enabled = true))

        holder.onEvent(EntryUiEvent.RemoveGatewayConnectionClicked)
        runBlocking { holder.awaitStateChangesApplied() }
        holder.onEvent(EntryUiEvent.ConfirmRemoveGatewayConnectionClicked)
        runBlocking { holder.awaitStateChangesApplied() }

        val lock = holder.uiState.value.appLock

        assertTrue(lock.isEnabled)
        assertTrue(lock.isLocked)
        assertTrue(lock.isUnlockRequired)
    }

    @Test
    fun the_unlock_action_does_nothing_while_no_gate_is_up() {
        var requests = 0
        val holder = appLockHolder(onUnlockRequested = { requests += 1 })

        holder.onEvent(EntryUiEvent.AppUnlockRequested)
        runBlocking { holder.awaitStateChangesApplied() }

        assertEquals(0, requests)
    }

    private fun appLockHolder(
        store: FakeAppLockSettingsStore = FakeAppLockSettingsStore(),
        enrolment: FakeAppLockEnrolment = FakeAppLockEnrolment(),
        onUnlockRequested: () -> Unit = {},
    ): EntryStateHolder =
        EntryStateHolder(
            initialState = EntryState(isGatewayConnectionConfigured = false),
            dependencies =
                EntryStateHolderDependencies(
                    appLock =
                        AppLockPorts(
                            settingsStore = store,
                            enrolment = enrolment,
                            requestUnlock = onUnlockRequested,
                        ),
                ),
        )
}

private class FakeAppLockSettingsStore(
    var enabled: Boolean = false,
) : AppLockSettingsStore {
    val saves = mutableListOf<Boolean>()

    override fun loadEnabled(): Boolean = enabled

    override fun saveEnabled(enabled: Boolean) {
        saves += enabled
        this.enabled = enabled
    }
}

private class FakeAppLockEnrolment(
    var enrolled: Boolean = true,
) : AppLockEnrolment {
    override fun isEnrolled(): Boolean = enrolled
}

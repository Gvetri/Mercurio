package org.mercurio.client.feature.entry.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryLinkParsingTest {
    @Test
    fun the_two_canonical_actions_parse() {
        assertEquals(EntryLink.Session("abc-123"), parseEntryLink("mercurio://session/abc-123"))
        assertEquals(EntryLink.Compose, parseEntryLink("mercurio://compose"))
    }

    @Test
    fun scheme_and_host_are_case_insensitive_and_a_trailing_slash_is_allowed() {
        assertEquals(EntryLink.Session("abc"), parseEntryLink("MERCURIO://SESSION/abc/"))
        assertEquals(EntryLink.Compose, parseEntryLink("mercurio://compose/"))
    }

    @Test
    fun a_session_link_without_a_usable_id_is_reported_as_unknown() {
        assertEquals(EntryLink.Session(null), parseEntryLink("mercurio://session"))
        assertEquals(EntryLink.Session(null), parseEntryLink("mercurio://session/"))
        assertEquals(EntryLink.Session(null), parseEntryLink("mercurio://session/a/b"))
        assertEquals(EntryLink.Session(null), parseEntryLink("mercurio://session/%20"))
    }

    @Test
    fun foreign_schemes_and_unbuilt_actions_do_not_parse() {
        assertNull(parseEntryLink(null))
        assertNull(parseEntryLink(""))
        assertNull(parseEntryLink("https://session/abc"))
        assertNull(parseEntryLink("mercurio:/session/abc"))
        assertNull(parseEntryLink("mercurio://settings"))
        assertNull(parseEntryLink("mercurio://compose/now"))
    }

    @Test
    fun an_unknown_query_or_fragment_is_ignored() {
        assertEquals(EntryLink.Session("abc"), parseEntryLink("mercurio://session/abc?ref=chat"))
        assertEquals(EntryLink.Compose, parseEntryLink("mercurio://compose#top"))
    }
}

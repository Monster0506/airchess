package com.airchess.ui

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class GameLinksTest {
    @Test
    fun parsesValidDeepLinkCaseInsensitively() {
        assertEquals("ABC234", parseJoinCode("airchess://join/abc234"))
    }

    @Test
    fun rejectsInvalidDeepLinkCodes() {
        assertNull(parseJoinCode("airchess://join/ABC230"))
        assertNull(parseJoinCode("airchess://join/ABC23"))
        assertNull(parseJoinCode("https://example.test/play/?code=ABC234"))
    }

    @Test
    fun derivesWebInvitesWithPorts() {
        assertEquals("https://example.test:8443/play/?code=ABC234", webInviteLink("wss://example.test:8443/ws", "ABC234"))
        assertEquals("http://example.test:8091/play/?code=ABC234", webInviteLink("ws://example.test:8091/ws", "ABC234"))
    }
}

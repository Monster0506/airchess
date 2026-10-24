@file:OptIn(ExperimentalCoroutinesApi::class)

package com.airchess.server

import com.airchess.protocol.ColorChoice
import com.airchess.protocol.Protocol
import com.airchess.protocol.TimeControl
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class RoomRegistryTest {

    private fun TestScope.registry(ttl: Long = 1_000) = RoomRegistry(
        scope = backgroundScope,
        newReferee = { ScriptedReferee() },
        now = { currentTime },
        random = Random(7),
        idleTtlMs = ttl,
    )

    @Test
    fun `codes are unique and use the protocol alphabet`() = runTest {
        val registry = registry()
        val codes = List(500) { registry.create(TimeControl.BLITZ_5_0, ColorChoice.RANDOM).code }
        assertEquals(500, codes.toSet().size)
        assertEquals(500, registry.size)
        assertTrue(codes.all { it.length == Protocol.CODE_LENGTH && it.all { c -> c in Protocol.CODE_ALPHABET } })
    }

    @Test
    fun `lookup ignores case and whitespace`() = runTest {
        val registry = registry()
        val room = registry.create(TimeControl.BLITZ_5_0, ColorChoice.WHITE)
        assertNotNull(registry.find(" ${room.code.lowercase()} "))
        assertNull(registry.find("NOPE22"))
    }

    @Test
    fun `random color picks both sides`() = runTest {
        val registry = registry()
        val sides = List(40) {
            val conn = FakeConnection()
            registry.create(TimeControl.BLITZ_5_0, ColorChoice.RANDOM).join("Ann", conn)!!.side
        }
        assertEquals(2, sides.toSet().size)
    }

    @Test
    fun `sweep drops only rooms idle past the ttl`() = runTest {
        val registry = registry(ttl = 1_000)
        val empty = registry.create(TimeControl.BLITZ_5_0, ColorChoice.WHITE)
        val busy = registry.create(TimeControl.BLITZ_5_0, ColorChoice.WHITE)
        busy.join("Ann", FakeConnection())

        testScheduler.advanceTimeBy(1_001)
        registry.sweep()

        assertNull(registry.find(empty.code))
        assertNotNull(registry.find(busy.code))
    }
}

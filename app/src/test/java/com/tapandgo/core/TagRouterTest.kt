package com.tapandgo.core

import com.tapandgo.core.nfc.TagRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRouterTest {
    private var clock = 0L
    private val router = TagRouter<String> { clock }

    @Test
    fun `top screen gets the tag and a leaving screen cannot remove the new one`() {
        val read = mutableListOf<String>()
        val write = mutableListOf<String>()
        val readOwner = Any()
        val writeOwner = Any()
        router.register(readOwner) { read += it }
        // Navigation Read -> Write: Write registers before Read is disposed.
        router.register(writeOwner) { write += it }
        router.unregister(readOwner)
        assertTrue(router.dispatch("tag1"))
        assertEquals(listOf("tag1"), write)
        assertTrue(read.isEmpty())
    }

    @Test
    fun `no screen listening parks the tag for the next screen within 5 s`() {
        assertFalse(router.dispatch("t"))
        router.park("t")
        assertEquals(1L, router.unhandled.value)
        val got = mutableListOf<String>()
        clock = 4_000
        router.register(Any()) { got += it }
        assertEquals(listOf("t"), got)
    }

    @Test
    fun `stale parked tag is dropped`() {
        router.park("old")
        clock = 6_000
        val got = mutableListOf<String>()
        router.register(Any()) { got += it }
        assertTrue(got.isEmpty())
    }

    @Test
    fun `pause is reference counted per owner`() {
        val a = Any(); val b = Any()
        router.pauseReader(a); router.pauseReader(b)
        router.resumeReader(a)
        assertTrue(router.paused.value)
        router.resumeReader(b)
        assertFalse(router.paused.value)
    }
}

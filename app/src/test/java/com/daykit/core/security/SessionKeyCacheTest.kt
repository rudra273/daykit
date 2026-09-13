package com.daykit.core.security

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SessionKeyCacheTest {
    @Test fun copiesRemainIntactAfterReplacementAndLock() {
        val cache = SessionKeyCache()
        val source = ByteArray(32) { 42 }
        assertTrue(cache.install(source, cache.generation()))
        source.fill(0)
        val copy = cache.copy()!!
        cache.install(ByteArray(32) { 7 }, cache.generation())
        cache.clear()
        assertArrayEquals(ByteArray(32) { 42 }, copy)
        assertNull(cache.copy())
    }

    @Test fun aLockRejectsAnUnlockThatWasAlreadyDerivingItsKey() {
        val cache = SessionKeyCache()
        val generation = cache.generation()
        cache.clear()
        assertFalse(cache.install(ByteArray(32) { 42 }, generation))
        assertFalse(cache.isUnlocked())
    }

    @Test fun concurrentCopiesNeverContainPartiallyWipedKeys() {
        val cache = SessionKeyCache()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val writer = pool.submit {
                start.await()
                repeat(10_000) {
                    cache.install(ByteArray(32) { 42 }, cache.generation())
                    cache.clear()
                }
            }
            val reader = pool.submit {
                start.await()
                repeat(10_000) {
                    cache.copy()?.let { assertArrayEquals(ByteArray(32) { 42 }, it) }
                }
            }
            start.countDown()
            writer.get(10, TimeUnit.SECONDS)
            reader.get(10, TimeUnit.SECONDS)
        } finally { pool.shutdownNow() }
    }
}

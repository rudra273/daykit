package com.daykit.core.security

/** Copying, replacing and wiping key bytes share a monitor; callers only receive owned copies. */
internal class SessionKeyCache {
    private var bytes: ByteArray? = null
    private var generation = 0L

    @Synchronized fun generation(): Long = generation
    @Synchronized fun isUnlocked(): Boolean = bytes != null
    @Synchronized fun copy(): ByteArray? = bytes?.copyOf()

    /** A lock during key derivation must not be undone by a late unlock result. */
    @Synchronized fun install(key: ByteArray, expectedGeneration: Long): Boolean {
        if (generation != expectedGeneration) return false
        bytes?.fill(0)
        bytes = key.copyOf()
        generation++
        return true
    }

    @Synchronized fun clear() {
        generation++
        bytes?.fill(0)
        bytes = null
    }
}

package com.daykit.feature.focus.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusDefaultGroupsTest {
    @Test
    fun `social group includes only installed supported social apps in stable order`() {
        val packages = FocusDefaultGroups.socialPackages(
            listOf(
                "com.example.calendar",
                "com.zhiliaoapp.musically",
                "com.instagram.android",
                "com.instagram.android",
                "com.facebook.orca",
            ),
        )

        assertEquals(
            listOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically",
            ),
            packages,
        )
    }

    @Test
    fun `social group is empty when no supported social apps are installed`() {
        assertEquals(
            emptyList<String>(),
            FocusDefaultGroups.socialPackages(listOf("com.example.calendar")),
        )
    }
}

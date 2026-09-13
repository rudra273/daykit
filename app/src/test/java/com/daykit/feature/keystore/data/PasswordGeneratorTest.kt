package com.daykit.feature.keystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class PasswordGeneratorTest {
    @Test fun generatedPasswordHasRequestedLengthAndEverySelectedType() {
        val password = PasswordGenerator.generate(PasswordGeneratorOptions(length = 32), SecureRandom())
        assertEquals(32, password.length)
        assertTrue(password.any(Char::isUpperCase))
        assertTrue(password.any(Char::isLowerCase))
        assertTrue(password.any(Char::isDigit))
        assertTrue(password.any { !it.isLetterOrDigit() })
    }

    @Test fun disabledTypesAreExcluded() {
        val password = PasswordGenerator.generate(
            PasswordGeneratorOptions(length = 24, uppercase = false, lowercase = true, numbers = false, symbols = false),
            SecureRandom(),
        )
        assertTrue(password.all(Char::isLowerCase))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyCharacterSelectionIsRejected() {
        PasswordGenerator.generate(PasswordGeneratorOptions(uppercase = false, lowercase = false, numbers = false, symbols = false))
    }
}

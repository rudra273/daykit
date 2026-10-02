package com.daykit.feature.keystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardNumbersTest {
    @Test
    fun detectsNetworksByPrefix() {
        assertEquals(CardNetwork.Visa, CardNumbers.networkOf("4111111111111111"))
        assertEquals(CardNetwork.Mastercard, CardNumbers.networkOf("5555555555554444"))
        assertEquals(CardNetwork.Mastercard, CardNumbers.networkOf("2223003122003222"))
        assertEquals(CardNetwork.Amex, CardNumbers.networkOf("378282246310005"))
        assertEquals(CardNetwork.Discover, CardNumbers.networkOf("6011111111111117"))
        assertEquals(CardNetwork.RuPay, CardNumbers.networkOf("6074 1234 5678 9012"))
        assertEquals(CardNetwork.RuPay, CardNumbers.networkOf("8212345678901234"))
        assertEquals(CardNetwork.Jcb, CardNumbers.networkOf("3528000700000000"))
        // 353 / 356 sit inside JCB's range but are RuPay-issued in India.
        assertEquals(CardNetwork.RuPay, CardNumbers.networkOf("3530111333300000"))
        assertEquals(CardNetwork.Diners, CardNumbers.networkOf("30569309025904"))
        assertEquals(CardNetwork.Unknown, CardNumbers.networkOf(""))
        assertEquals(CardNetwork.Unknown, CardNumbers.networkOf("9999"))
    }

    @Test
    fun luhn() {
        assertTrue(CardNumbers.passesLuhn("4111 1111 1111 1111"))
        assertTrue(CardNumbers.passesLuhn("378282246310005"))
        assertFalse(CardNumbers.passesLuhn("4111111111111112"))
        assertFalse(CardNumbers.passesLuhn("4111"))
    }

    @Test
    fun formatsInGroups() {
        assertEquals("4111 1111 1111 1111", CardNumbers.format("4111111111111111"))
        assertEquals("3782 822463 10005", CardNumbers.format("378282246310005"))
        assertEquals("4111 11", CardNumbers.format("411111"))
        assertEquals("4111", CardNumbers.format("4111"))
    }

    @Test
    fun digitsOfStripsAndCaps() {
        assertEquals("4111111111111111", CardNumbers.digitsOf("4111-1111 1111 1111"))
        assertEquals(19, CardNumbers.digitsOf("1".repeat(25)).length)
    }
}

package com.daykit.feature.keystore.data

import java.security.SecureRandom

data class PasswordGeneratorOptions(
    val length: Int = 20,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val numbers: Boolean = true,
    val symbols: Boolean = true,
)

object PasswordGenerator {
    private const val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijkmnopqrstuvwxyz"
    private const val NUMBERS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}"

    fun generate(options: PasswordGeneratorOptions, random: SecureRandom = SecureRandom()): String {
        require(options.length in 8..128) { "Password length must be between 8 and 128" }
        val sets = buildList {
            if (options.uppercase) add(UPPERCASE)
            if (options.lowercase) add(LOWERCASE)
            if (options.numbers) add(NUMBERS)
            if (options.symbols) add(SYMBOLS)
        }
        require(sets.isNotEmpty()) { "Select at least one character type" }
        require(options.length >= sets.size)

        val characters = mutableListOf<Char>()
        sets.forEach { set -> characters += set[random.nextInt(set.length)] }
        val pool = sets.joinToString("")
        repeat(options.length - characters.size) { characters += pool[random.nextInt(pool.length)] }
        for (index in characters.lastIndex downTo 1) {
            val swapWith = random.nextInt(index + 1)
            val value = characters[index]
            characters[index] = characters[swapWith]
            characters[swapWith] = value
        }
        return characters.joinToString("")
    }
}

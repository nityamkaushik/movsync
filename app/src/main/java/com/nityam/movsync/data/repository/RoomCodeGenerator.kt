package com.nityam.movsync.data.repository

import kotlin.random.Random

object RoomCodeGenerator {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    fun generate(length: Int = 6): String {
        return buildString(length) {
            repeat(length) {
                append(ALPHABET[Random.nextInt(ALPHABET.length)])
            }
        }
    }
}

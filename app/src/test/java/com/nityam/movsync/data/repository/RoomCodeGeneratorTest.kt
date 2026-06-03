package com.nityam.movsync.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCodeGeneratorTest {
    @Test
    fun generatedCodeIsSixAlphanumericCharacters() {
        val code = RoomCodeGenerator.generate()

        assertEquals(6, code.length)
        assertTrue(code.all { it.isLetterOrDigit() })
    }

    @Test
    fun generatedCodesHaveVariety() {
        val codes = List(100) { RoomCodeGenerator.generate() }.toSet()

        assertTrue(codes.size > 90)
    }
}

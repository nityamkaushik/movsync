package com.nityam.movsync.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftCorrectorTest {
    private val corrector = DriftCorrector()

    @Test
    fun smallDriftIsAccepted() {
        val action = corrector.evaluate(currentPosition = 1_050L, expectedPosition = 1_000L)

        assertTrue(action is DriftAction.InSync)
    }

    @Test
    fun mediumBehindDriftSpeedsUp() {
        val action = corrector.evaluate(currentPosition = 200L, expectedPosition = 1_000L)

        assertTrue(action is DriftAction.SoftCorrect)
        assertEquals(1.05f, (action as DriftAction.SoftCorrect).speed)
    }

    @Test
    fun mediumAheadDriftSlowsDown() {
        val action = corrector.evaluate(currentPosition = 1_800L, expectedPosition = 1_000L)

        assertTrue(action is DriftAction.SoftCorrect)
        assertEquals(0.95f, (action as DriftAction.SoftCorrect).speed)
    }

    @Test
    fun largeDriftHardSeeks() {
        val action = corrector.evaluate(currentPosition = 3_500L, expectedPosition = 1_000L)

        assertTrue(action is DriftAction.HardSeek)
        assertEquals(1_000L, (action as DriftAction.HardSeek).targetPosition)
    }
}

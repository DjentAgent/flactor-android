package com.psycode.spotiflac.data.service.download

import com.psycode.spotiflac.data.service.download.session.SpeedCalculator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedCalculatorTest {

    @Test
    fun `returns zero until min measurement window is reached`() = runTest {
        val calc = SpeedCalculator(windowSize = 5, minWindowMs = 1_500L)

        assertEquals(0L, calc.addMeasurement(timestamp = 1_000L, totalBytes = 0L))
        assertEquals(0L, calc.addMeasurement(timestamp = 1_700L, totalBytes = 700_000L))
        val speed = calc.addMeasurement(timestamp = 2_600L, totalBytes = 1_600_000L)

        assertTrue(speed > 0L)
    }

    @Test
    fun `caps unrealistic spikes to configured maximum`() = runTest {
        val maxSpeed = 5_000_000L
        val calc = SpeedCalculator(
            windowSize = 3,
            minWindowMs = 100L,
            maxDisplayBytesPerSec = maxSpeed
        )

        calc.addMeasurement(timestamp = 1_000L, totalBytes = 0L)
        val speed = calc.addMeasurement(timestamp = 1_200L, totalBytes = 100_000_000L)

        assertEquals(maxSpeed, speed)
    }
}

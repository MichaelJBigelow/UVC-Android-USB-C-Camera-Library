package com.herohan.uvcdemo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRateMeterTest {

    private var now = 0L
    private val rates = mutableListOf<Double>()
    private val meter = FrameRateMeter(intervalMs = 500, clock = { now }, onRate = { rates += it })

    /** Delivers a frame every [stepMs] milliseconds, [count] times. */
    private fun frames(count: Int, stepMs: Long) {
        repeat(count) {
            meter.onFrame()
            now += stepMs
        }
    }

    @Test
    fun reportsTheRateOnceTheIntervalHasPassed() {
        // 30 fps: the first frame starts the interval, the next 16 span 528 ms.
        frames(count = 17, stepMs = 33)
        assertEquals(1, rates.size)
        assertEquals(16 * 1000.0 / 528, rates[0], 0.001)
    }

    @Test
    fun reportsNothingWithinTheFirstInterval() {
        frames(count = 10, stepMs = 33)
        assertTrue(rates.isEmpty())
    }

    @Test
    fun resetStartsANewInterval() {
        frames(count = 10, stepMs = 33)
        meter.reset()
        now += 10_000
        frames(count = 10, stepMs = 33)
        assertTrue(rates.isEmpty())
    }
}

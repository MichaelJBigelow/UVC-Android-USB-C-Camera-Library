package com.herohan.uvcsample.ui.dialog

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlChangeTest {

    private fun merged(vararg changes: ControlChange): List<ControlChange> =
        mutableListOf<ControlChange>().apply { changes.forEach { addMerging(it) } }

    @Test
    fun repeatedMovesOfOneSliderKeepOnlyTheLatest() {
        assertEquals(
            listOf(ControlChange.SliderValue(0, 30)),
            merged(
                ControlChange.SliderValue(0, 10),
                ControlChange.SliderValue(0, 20),
                ControlChange.SliderValue(0, 30),
            ),
        )
    }

    @Test
    fun movesOfDifferentSlidersAreAllKept() {
        assertEquals(
            listOf(ControlChange.SliderValue(0, 10), ControlChange.SliderValue(1, 20)),
            merged(ControlChange.SliderValue(0, 10), ControlChange.SliderValue(1, 20)),
        )
    }

    @Test
    fun otherChangesKeepTheirOrder() {
        assertEquals(
            listOf(
                ControlChange.SliderValue(0, 10),
                ControlChange.Auto(0, true),
                ControlChange.SliderValue(0, 20),
                ControlChange.ResetAll,
            ),
            merged(
                ControlChange.SliderValue(0, 10),
                ControlChange.Auto(0, true),
                ControlChange.SliderValue(0, 20),
                ControlChange.ResetAll,
            ),
        )
    }
}

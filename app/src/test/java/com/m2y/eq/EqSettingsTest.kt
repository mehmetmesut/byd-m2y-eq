package com.m2y.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqSettingsTest {
    @Test fun cutoffsAreBetweenNeighbourCentresInBothLayouts() {
        for (centers in EqSettings.LAYOUTS.values) {
            val c = EqSettings.cutoffs(centers)
            for (i in 0 until centers.lastIndex) {
                assertTrue(c[i] > centers[i] && c[i] < centers[i + 1])
                assertTrue(c[i] < c[i + 1])
            }
            assertEquals(20000f, c.last(), 0f)
            assertTrue(c.last() < 22050f) // below Nyquist of 44,1 kHz sources
        }
        assertEquals(1414.2f, EqSettings.cutoffs(EqSettings.OCTAVE)[5], 0.1f) // sqrt(1000 * 2000)
    }

    @Test fun progressRoundTrip() {
        val min = EqSettings.BAND_MIN_DB
        val max = EqSettings.BAND_MAX_DB
        assertEquals(48, EqSettings.steps(min, max)) // ±12 dB in 0,5 dB steps
        for (db in listOf(-12f, -2.5f, 0f, 3f, 12f)) {
            assertEquals(db, EqSettings.progressToDb(EqSettings.dbToProgress(db, min, max), min), 0f)
        }
        assertEquals(48, EqSettings.dbToProgress(99f, min, max)) // clamped
    }

    @Test fun mmyPresetMatchesUserTuning() {
        assertEquals(listOf(3f, 2f, 2f, -2f, -3f, 0f, 3f, 2f, 3f, 1f), EqSettings.PRESETS.getValue("M2Y").gains)
    }

    @Test fun briefProfilesAreThirdOctaveAndMatchTheTable() {
        val rap = EqSettings.PRESETS.getValue("Rap")
        assertEquals(30, rap.gains.size)
        val at = { hz: Float -> rap.gains[EqSettings.THIRD_OCTAVE.toList().indexOf(hz)] }
        assertEquals(0f, at(25f), 0f)
        assertEquals(2f, at(50f), 0f)
        assertEquals(-1f, at(250f), 0f)
        assertEquals(1f, at(10000f), 0f)
        assertTrue(EqSettings.PRESETS.getValue(EqSettings.REFERENCE_PRESET).gains.all { it == 0f })
    }

    @Test fun resampleKeepsCurveAtSharedFrequencies() {
        val mmy = EqSettings.PRESETS.getValue("M2Y")
        val fine = mmy.resampled(30)
        assertEquals(30, fine.gains.size)
        // 125, 250, 500, 1k, 2k, 4k, 8k, 16k exist in both layouts
        for (hz in listOf(125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)) {
            val octave = mmy.gains[EqSettings.OCTAVE.toList().indexOf(hz)]
            assertEquals(octave, fine.gains[EqSettings.THIRD_OCTAVE.toList().indexOf(hz)], 0f)
        }
        assertEquals(3f, fine.gains.first(), 0f) // 25 Hz, below the first octave band, holds its value
        val back = fine.resampled(10).gains
        for (i in 2 until back.size) assertEquals(mmy.gains[i], back[i], 0f) // 31/62 Hz are 31.5/63 Hz there
    }

    @Test fun balanceAttenuatesOnlyTheOppositeSide() {
        val s = EqSettings.FLAT.copy(loudnessDb = 2f)
        assertEquals(2f to 2f, s.channelGains())
        assertEquals(2f to -8f, s.copy(balance = -0.5f).channelGains()) // towards left: right is cut 10 dB
        assertEquals(-18f to 2f, s.copy(balance = 1f).channelGains())
        assertEquals(-18f to 2f, s.copy(balance = 5f).channelGains()) // clamped
    }

    @Test fun preampFollowsTheBrief() {
        val p = { name: String -> EqSettings.preampDb(EqSettings.PRESETS.getValue(name)) }
        assertEquals(0f, p(EqSettings.REFERENCE_PRESET), 0f)
        assertEquals(-3.5f, p("Pop"), 0f)
        assertEquals(-3.5f, p("Rock"), 0f)
        assertEquals(-4f, p("Rap"), 0f)     // max boost + 2 dB
        assertEquals(-3f, p("Klasik"), 0f)  
        assertEquals(-6f, EqSettings.preampDb(EqSettings.FLAT.copy(bassDb = 4f)), 0f) // bass shelf counts too
        assertEquals(0f, EqSettings.preampDb(EqSettings(List(10) { -4f })), 0f)       // cuts never raise the gain
    }

    @Test fun everyPresetIsWithinRange() {
        EqSettings.PRESETS.values.forEach { p ->
            assertTrue(p.gains.size in EqSettings.LAYOUTS)
            assertTrue(p.gains.none { kotlin.math.abs(it) > EqSettings.SAFE_DB }) // built-in profiles stay in the safe range
        }
    }

    @Test fun aiProfileIsGentleAndShapedForTheCabin() {
        val ai = EqSettings.PRESETS.getValue("AI")
        assertEquals(30, ai.gains.size)
        assertTrue(ai.gains.all { it in -1.5f..1.5f })                 // stays close to factory tuning
        val at = { hz: Float -> ai.gains[EqSettings.THIRD_OCTAVE.toList().indexOf(hz)] }
        assertEquals(-.5f, at(25f), 0f)                                 // infrasonic trim
        assertEquals(-1.5f, at(315f), 0f)                               // boxy cabin region cut
        assertTrue(at(4000f) <= 0f && at(5000f) <= 0f)                  // no tweeter glare boost
        assertEquals(-3.5f, EqSettings.preampDb(ai), 0f)
    }

    @Test fun lerpBlendsGainsAndKeepsTarget() {
        val a = EqSettings.FLAT
        val b = EqSettings.PRESETS.getValue("M2Y").copy(loudnessDb = 4f, surround = 0.3f)
        val half = a.lerp(b, 0.5f)
        assertEquals(1.5f, half.gains[0], 0f)
        assertEquals(2f, half.loudnessDb, 0f)
        assertEquals(0.3f, half.surround, 0f) // non-continuous fields jump to the target
        assertEquals(b, a.lerp(b, 1f))
    }

    @Test fun engineAlwaysRunsThirtySmoothBands() {
        val m2y = EqSettings.PRESETS.getValue("M2Y")
        val curve = m2y.engineGains()
        assertEquals(30, curve.size)
        // octave centres keep their value; between them the curve moves in ≤ 1,5 dB steps instead of one 4 dB jump
        assertEquals(2f, curve[EqSettings.THIRD_OCTAVE.toList().indexOf(125f)], 0.001f)
        assertEquals(-2f, curve[EqSettings.THIRD_OCTAVE.toList().indexOf(250f)], 0.001f)
        assertTrue(curve.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) <= 1.5f })
        val fine = EqSettings.PRESETS.getValue("Pop")
        assertEquals(fine.gains, fine.engineGains()) // 30-band input is used as is
    }

    @Test fun reviewedProfilesRespectCarRules() {
        val idx = { hz: Float -> EqSettings.THIRD_OCTAVE.toList().indexOf(hz) }
        EqSettings.PRESETS.filterKeys { it != "M2Y" }.values.map { it.engineGains() }.forEach { c ->
            assertTrue(c[idx(25f)] <= 0f)                                // no infrasonic boost
            assertTrue(c[idx(5000f)] <= 0.5f)                            // no windscreen/tweeter glare
            assertTrue(c[idx(250f)] <= 0f && c[idx(315f)] <= 0f)         // no boxy boost
        }
    }

    @Test fun anyAdjustableChangeDiffersFromThePresetButOnOffDoesNot() {
        val preset = EqSettings.PRESETS.getValue("M2Y").copy(bassDb = 3f, surround = .2f)
        assertFalse(preset.differsFrom(preset))
        assertFalse(preset.copy(enabled = false).differsFrom(preset))
        assertTrue(preset.copy(bassDb = 3.5f).differsFrom(preset))
        assertTrue(preset.copy(loudnessDb = .5f).differsFrom(preset))
        assertTrue(preset.copy(surround = .21f).differsFrom(preset))
        assertTrue(preset.copy(balance = .05f).differsFrom(preset))
        assertTrue(preset.copy(bassHz = 85f).differsFrom(preset))
        assertTrue(preset.withBand(0, 3.5f).differsFrom(preset))
    }

    @Test fun withBandClamps() {
        assertEquals(12f, EqSettings.FLAT.withBand(0, 40f).gains[0], 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongBandCountRejected() {
        EqSettings(listOf(0f))
    }
}

package com.m2y.eq

import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pure equalizer model: band layouts, presets and slider mapping. No Android types, so it is unit-tested. */
data class EqSettings(
    val gains: List<Float>,        // dB per band; 10 = octave ("Basit"), 30 = 1/3 octave ("Hassas")
    val bassDb: Float = 0f,        // low-shelf boost below bassHz
    val loudnessDb: Float = 0f,    // limiter post-gain
    val enabled: Boolean = true,
    val bassHz: Float = BASS_DEFAULT_HZ,
    val balance: Float = 0f,       // -1 = left only … +1 = right only
    val surround: Float = 0f,      // Virtualizer strength 0…1; 0 = effect not created
) {
    init {
        require(gains.size in LAYOUTS) { "band count must be one of ${LAYOUTS.keys}, got ${gains.size}" }
    }

    /** Limiter post-gain per channel (left, right): loudness minus the attenuation of the side we pan away from. */
    fun channelGains(): Pair<Float, Float> {
        val b = balance.coerceIn(-1f, 1f)
        return (loudnessDb - BALANCE_MAX_CUT_DB * maxOf(0f, b)) to (loudnessDb - BALANCE_MAX_CUT_DB * maxOf(0f, -b))
    }

    val centers: FloatArray get() = LAYOUTS.getValue(gains.size)

    /** Step [t] (0…1) of a smooth transition to [to]; used for the 20–50 ms profile change ramp. */
    fun lerp(to: EqSettings, t: Float): EqSettings {
        require(to.gains.size == gains.size) { "cannot blend ${gains.size} into ${to.gains.size} bands" }
        fun mix(a: Float, b: Float) = a + (b - a) * t
        return to.copy(
            gains = gains.zip(to.gains, ::mix),
            bassDb = mix(bassDb, to.bassDb),
            loudnessDb = mix(loudnessDb, to.loudnessDb),
            balance = mix(balance, to.balance),
        )
    }

    /** Any adjustable value (bands, bass, loudness, bass frequency, balance, surround) differs from [preset]; on/off is not part of a preset. */
    fun differsFrom(preset: EqSettings): Boolean = copy(enabled = true) != preset.resampled(gains.size).copy(enabled = true)

    fun withBand(index: Int, db: Float) = copy(gains = gains.toMutableList().also { it[index] = clampBand(db) })

    /** Same curve on another layout: gains are interpolated on a log-frequency axis. */
    fun resampled(bands: Int): EqSettings =
        if (bands == gains.size) this else copy(gains = LAYOUTS.getValue(bands).map { curveAt(it) })

    /**
     * The curve the engine runs: always 30 bands. A 10-band setting is spread smoothly (unrounded) over the 1/3-octave
     * grid, so the frequency-domain EQ has no 2–4 dB jumps at the wide octave band edges.
     */
    fun engineGains(): List<Float> =
        if (gains.size == THIRD_OCTAVE.size) gains else THIRD_OCTAVE.map { curveAt(it, round = false) }

    /** Same settings with the engine's 30-band curve; used to blend between any two settings. */
    fun toEngine(): EqSettings = copy(gains = engineGains())

    private fun curveAt(hz: Float, round: Boolean = true): Float {
        val c = centers
        if (hz <= c.first()) return gains.first()
        if (hz >= c.last()) return gains.last()
        val i = c.indexOfLast { it <= hz }
        val t = (ln(hz) - ln(c[i])) / (ln(c[i + 1]) - ln(c[i]))
        val db = gains[i] + t * (gains[i + 1] - gains[i])
        return if (round) (db / STEP_DB).roundToInt() * STEP_DB else db
    }

    companion object {
        val OCTAVE = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        // 30 bands, 25 Hz–20 kHz (20 Hz is below what the boot subwoofer plays usefully)
        val THIRD_OCTAVE = floatArrayOf(
            25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f, 200f, 250f, 315f, 400f, 500f, 630f,
            800f, 1000f, 1250f, 1600f, 2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f,
        )
        val LAYOUTS = mapOf(OCTAVE.size to OCTAVE, THIRD_OCTAVE.size to THIRD_OCTAVE)

        // Safe range is ±6 dB; the user may go further (±12), the UI then shows the value in
        // orange. The automatic preamp keeps the EQ from clipping either way.
        const val SAFE_DB = 6f
        const val BAND_MIN_DB = -12f
        const val BAND_MAX_DB = 12f
        const val BASS_MAX_DB = 12f
        const val LOUDNESS_MAX_DB = 10f

        const val BASS_DEFAULT_HZ = 80f
        const val BASS_MIN_HZ = 40f
        const val BASS_MAX_HZ = 150f
        const val BALANCE_MAX_CUT_DB = 20f

        const val STEP_DB = 0.5f
        const val TOP_CUTOFF_HZ = 20000f

        val FLAT = EqSettings(List(OCTAVE.size) { 0f })

        /** Reference profile: every band 0 dB and no extra processing; the factory Infinity DSP stays as tuned. */
        const val REFERENCE_PRESET = "BYD"

        // Pop/Rock/Rap/Klasik are 1/3-octave curves tuned for this car (Infinity system, SUV cabin):
        // nothing is boosted at 25 Hz (sub limit, cabin gain), 4–5 kHz stays ≤ +0,5 dB (A-pillar tweeters and
        // windscreen glare), 250–315 Hz is never boosted (door-panel boxiness). M2Y (the user's own curve) and Konuşma
        // are octave curves; the engine always runs them as smooth 30-band curves.
        private fun third(vararg from25Hz: Float) = EqSettings(from25Hz.toList())
        val PRESETS: Map<String, EqSettings> = linkedMapOf(
            "M2Y" to EqSettings(listOf(3f, 2f, 2f, -2f, -3f, 0f, 3f, 2f, 3f, 1f)),
            REFERENCE_PRESET to FLAT,
            // "AI": transparent calibration for the SEAL U Infinity system (boot subwoofer, 4 door woofers, centre mid,
            // 4 tweeters) in a closed SUV cabin. Cabin gain already lifts < 60 Hz, so the sub gets only a gentle
            // 40–80 Hz punch and 25 Hz is trimmed to save headroom; 250–400 Hz door-panel/boxy build-up is cut;
            // 2–2,5 kHz adds vocal clarity while 4–5 kHz (A-pillar tweeter + windscreen glare) stays flat;
            // 8–12,5 kHz restores the air that road noise masks. Max ±1,5 dB keeps it close to the factory tuning.
            "AI" to third(-.5f, .5f, 1f, 1.5f, 1.5f, 1.5f, 1f, .5f, 0f, -.5f, -1f, -1.5f, -1f, -.5f, 0f,
                0f, 0f, .5f, .5f, 1f, 1f, .5f, 0f, 0f, .5f, 1f, 1.5f, 1.5f, 1f, 0f), // 25 Hz −0,5: no infrasonic load
            "Pop" to third(0f, 0f, .5f, .5f, 1f, 1f, .5f, .5f, 0f, -.5f, -.5f, -.5f, 0f, 0f, 0f,
                .5f, .5f, .5f, 1f, 1f, 1.5f, 1f, .5f, 0f, 0f, .5f, 1f, 1f, .5f, 0f),
            "Rock" to third(0f, 0f, 0f, .5f, .5f, 1f, 1.5f, 1f, .5f, 0f, -.5f, -.5f, 0f, 0f, 0f,
                .5f, .5f, .5f, 1f, 1.5f, 1.5f, 1f, .5f, .5f, 0f, 0f, .5f, .5f, .5f, 0f), // 4 kHz +1 → +0,5
            "Rap" to third(0f, 1f, 1.5f, 2f, 2f, 1.5f, 1f, .5f, 0f, -.5f, -1f, -.5f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, .5f, .5f, .5f, .5f, 0f, .5f, 1f, 1f, .5f, .5f, 0f),
            "Klasik" to third(0f, 0f, 0f, 0f, 0f, 0f, -.5f, -.5f, -.5f, -.5f, 0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, .5f, .5f, .5f, .5f, .5f, .5f, .5f, 1f, 1f, .5f, 0f), // 4 kHz +1 → +0,5
            // Speech clarity from the centre speaker at 1–2 kHz; 250–315 Hz and 4–5 kHz kept out of the boost (car rules)
            "Konuşma" to EqSettings(listOf(-3f, -2f, -1f, -1f, 1f, 3f, 3f, .5f, 0f, -1f)),
        )

        /**
         * Input gain that cancels the largest boost plus a true-peak margin, so the EQ itself does not clip
         * (e.g. Pop/Rock −3,5 dB); the −1 dB limiter is only a backstop. Cuts never raise it.
         */
        fun preampDb(s: EqSettings): Float {
            val boost = (s.gains + s.bassDb).max()
            return if (boost > 0f) -(boost + PREAMP_MARGIN_DB) else 0f
        }
        const val PREAMP_MARGIN_DB = 2f
        const val DEFAULT_PRESET = "M2Y"

        /** Upper cutoff of each band = geometric mean of neighbouring centres; the last band reaches 20 kHz. */
        fun cutoffs(centers: FloatArray): FloatArray = FloatArray(centers.size) { i ->
            if (i == centers.lastIndex) TOP_CUTOFF_HZ else sqrt(centers[i] * centers[i + 1])
        }

        fun clampBand(db: Float) = db.coerceIn(BAND_MIN_DB, BAND_MAX_DB)

        /** Sliders work in integer steps; range min..max maps to 0..steps. */
        fun dbToProgress(db: Float, min: Float, max: Float) = ((db.coerceIn(min, max) - min) / STEP_DB).roundToInt()
        fun progressToDb(progress: Int, min: Float) = min + progress * STEP_DB
        fun steps(min: Float, max: Float) = ((max - min) / STEP_DB).roundToInt()

        fun label(hz: Float): String = when { // Turkish decimal comma: 31,5 · 1,25k
            hz >= 1000f -> (hz / 1000f).let { if (it % 1f == 0f) "${it.toInt()}k" else "${it}k" }
            else -> if (hz % 1f == 0f) hz.toInt().toString() else hz.toString()
        }.replace('.', ',')
    }
}

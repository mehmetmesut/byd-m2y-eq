package com.m2y.eq

import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.DynamicsProcessing.EqBand
import android.media.audiofx.DynamicsProcessing.Limiter
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Per audio session: one DynamicsProcessing (bass shelf, 30-band EQ, per-channel limiter for loudness and balance),
 * plus a Virtualizer only while surround is switched on.
 * No reverb: PresetReverb on session 0 is an auxiliary effect that ordinary players never attach to, so it is inaudible.
 * Session 0 is the global output mix; player sessions are used only if the global one is refused.
 */
class EqEngine {
    private val effects = mutableMapOf<Int, DynamicsProcessing>()
    private val virtualizers = mutableMapOf<Int, Virtualizer>()

    /** Creates the effects for [session] if needed and applies [s]. Returns false if the platform refused the EQ. */
    fun attach(session: Int, s: EqSettings): Boolean {
        val dp = effects[session] ?: try {
            DynamicsProcessing(Int.MAX_VALUE, session, config()).also { effects[session] = it }
        } catch (e: RuntimeException) {
            Log.e(TAG, "DynamicsProcessing refused for session $session", e)
            return false
        }
        return applySafely(session, dp, s)
    }

    fun detach(session: Int) {
        effects.remove(session)?.release()
        virtualizers.remove(session)?.release()
    }

    fun applyAll(s: EqSettings) {
        effects.toList().forEach { (session, dp) -> applySafely(session, dp, s) }
    }

    /**
     * An effect dies with the audio server (restart, route change) and its next call throws. The broken instance is
     * dropped and false returned, so the next apply builds a fresh one instead of crash-looping the boot-time service.
     */
    private fun applySafely(session: Int, dp: DynamicsProcessing, s: EqSettings): Boolean = try {
        apply(dp, s)
        applyExtras(session, s)
        true
    } catch (e: RuntimeException) {
        Log.e(TAG, "effect lost for session $session, will be recreated", e)
        detach(session)
        false
    }

    fun releaseAll() {
        effects.keys.toList().forEach(::detach)
    }

    private fun apply(dp: DynamicsProcessing, s: EqSettings) {
        dp.setInputGainAllChannelsTo(EqSettings.preampDb(s))
        dp.setPreEqBandAllChannelsTo(0, EqBand(true, s.bassHz, s.bassDb))
        dp.setPreEqBandAllChannelsTo(1, EqBand(true, EqSettings.TOP_CUTOFF_HZ, 0f))
        s.engineGains().forEachIndexed { i, db -> dp.setPostEqBandAllChannelsTo(i, EqBand(true, CUTOFFS[i], db)) }
        val (left, right) = s.channelGains()
        dp.setLimiterByChannelIndex(0, limiter(left))
        dp.setLimiterByChannelIndex(1, limiter(right))
        dp.enabled = s.enabled
    }

    /** The optional effect exists only while used, so they cost nothing when off. A refusal is logged, never fatal. */
    private fun applyExtras(session: Int, s: EqSettings) {
        val wantSurround = s.enabled && s.surround > 0f
        if (!wantSurround) virtualizers.remove(session)?.release()
        else create(session, virtualizers) { Virtualizer(Int.MAX_VALUE, session) }?.let { v ->
            if (!v.strengthSupported) { // most speaker outputs: the effect would do nothing, so it is not kept
                virtualizers.remove(session)?.release()
                return
            }
            v.setStrength((s.surround * MAX_STRENGTH).toInt().toShort())
            v.enabled = true
        }
    }

    private fun <T : AudioEffect> create(session: Int, map: MutableMap<Int, T>, factory: () -> T): T? =
        map[session] ?: try {
            factory().also { map[session] = it }
        } catch (e: RuntimeException) {
            Log.e(TAG, "optional effect refused for session $session", e)
            null
        }

    // Post gain (loudness, balance) is added after the limiter, so the threshold drops by the same amount
    // to keep the output ceiling at LIMITER_THRESHOLD_DB.
    private fun limiter(postGainDb: Float) = Limiter(true, true, 0, LIMITER_ATTACK_MS, LIMITER_RELEASE_MS, LIMITER_RATIO,
        LIMITER_THRESHOLD_DB - maxOf(0f, postGainDb), postGainDb)

    private fun config() = DynamicsProcessing.Config.Builder(
        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
        CHANNELS,
        true, PRE_EQ_BANDS,
        false, 0,
        true, CUTOFFS.size,
        true,
    ).setPreferredFrameDuration(FRAME_MS).build()

    private companion object {
        const val TAG = "M2YEQ"
        // Always the 30-band 1/3-octave grid; 10-band settings arrive already spread over it (EqSettings.engineGains)
        val CUTOFFS = EqSettings.cutoffs(EqSettings.THIRD_OCTAVE)
        const val CHANNELS = 2
        const val PRE_EQ_BANDS = 2 // bass shelf + pass-through up to 20 kHz
        const val FRAME_MS = 10f
        const val LIMITER_ATTACK_MS = 1f
        const val LIMITER_RELEASE_MS = 60f
        const val LIMITER_RATIO = 10f
        const val LIMITER_THRESHOLD_DB = -1f // −1 dB ceiling; it is a sample-peak limiter, so the preamp keeps extra margin
        const val MAX_STRENGTH = 1000 // Virtualizer strength range 0..1000
    }
}

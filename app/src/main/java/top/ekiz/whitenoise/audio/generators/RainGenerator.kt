package top.ekiz.whitenoise.audio.generators

import kotlin.math.PI

class RainGenerator : NoiseGenerator() {

    private var pinkB0L = 0.0
    private var pinkB1L = 0.0
    private var pinkB2L = 0.0
    private var pinkB3L = 0.0
    private var pinkB4L = 0.0
    private var pinkB5L = 0.0
    private var pinkB6L = 0.0

    private var pinkB0R = 0.0
    private var pinkB1R = 0.0
    private var pinkB2R = 0.0
    private var pinkB3R = 0.0
    private var pinkB4R = 0.0
    private var pinkB5R = 0.0
    private var pinkB6R = 0.0

    private var lp1L = 0.0
    private var lp2L = 0.0
    private var lp1R = 0.0
    private var lp2R = 0.0

    private var hp1L = 0.0
    private var hp2L = 0.0
    private var hp1R = 0.0
    private var hp2R = 0.0

    private var gustLfoPhase = 0.0
    private var piOverSr = 0.0
    private var gustLfoInc = 0.0

    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        piOverSr = PI / sr

        gustLfoInc = 0.02 / sr
    }

    override fun reset() {
        pinkB0L = 0.0
        pinkB1L = 0.0
        pinkB2L = 0.0
        pinkB3L = 0.0
        pinkB4L = 0.0
        pinkB5L = 0.0
        pinkB6L = 0.0
        pinkB0R = 0.0
        pinkB1R = 0.0
        pinkB2R = 0.0
        pinkB3R = 0.0
        pinkB4R = 0.0
        pinkB5R = 0.0
        pinkB6R = 0.0

        lp1L = 0.0
        lp2L = 0.0
        lp1R = 0.0
        lp2R = 0.0
        hp1L = 0.0
        hp2L = 0.0
        hp1R = 0.0
        hp2R = 0.0

        gustLfoPhase = 0.0
    }

    private fun smoothWave(phase: Double): Double {
        val tri = if (phase < 0.5) phase * 2.0 else 2.0 - phase * 2.0
        return tri * tri * (3.0 - 2.0 * tri)
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        pinkB0L = 0.99886 * pinkB0L + wL * 0.0555179
        pinkB1L = 0.99332 * pinkB1L + wL * 0.0750759
        pinkB2L = 0.96900 * pinkB2L + wL * 0.1538520
        pinkB3L = 0.86650 * pinkB3L + wL * 0.3104856
        pinkB4L = 0.55000 * pinkB4L + wL * 0.5329522
        pinkB5L = -0.7616 * pinkB5L - wL * 0.0168980
        val pinkOutL =
            pinkB0L + pinkB1L + pinkB2L + pinkB3L + pinkB4L + pinkB5L + pinkB6L + wL * 0.5362
        pinkB6L = wL * 0.115926
        val pinkL = pinkOutL * 0.11

        pinkB0R = 0.99886 * pinkB0R + wR * 0.0555179
        pinkB1R = 0.99332 * pinkB1R + wR * 0.0750759
        pinkB2R = 0.96900 * pinkB2R + wR * 0.1538520
        pinkB3R = 0.86650 * pinkB3R + wR * 0.3104856
        pinkB4R = 0.55000 * pinkB4R + wR * 0.5329522
        pinkB5R = -0.7616 * pinkB5R - wR * 0.0168980
        val pinkOutR =
            pinkB0R + pinkB1R + pinkB2R + pinkB3R + pinkB4R + pinkB5R + pinkB6R + wR * 0.5362
        pinkB6R = wR * 0.115926
        val pinkR = pinkOutR * 0.11

        gustLfoPhase += gustLfoInc
        if (gustLfoPhase >= 1.0) gustLfoPhase -= 1.0
        val gust = smoothWave(gustLfoPhase)

        val lpCutoff = 800.0 + gust * 500.0
        val fLp = 2.0 * lpCutoff * piOverSr

        val hpL_state = pinkL - lp1L - 1.2 * lp2L
        lp2L += fLp * hpL_state
        lp1L += fLp * lp2L
        val rumbleL = lp1L

        val hpR_state = pinkR - lp1R - 1.2 * lp2R
        lp2R += fLp * hpR_state
        lp1R += fLp * lp2R
        val rumbleR = lp1R

        val hpCutoff = 2500.0 - gust * 400.0
        val fHp = 2.0 * hpCutoff * piOverSr

        val hpL_hstate = pinkL - hp1L - 1.2 * hp2L
        hp2L += fHp * hpL_hstate
        hp1L += fHp * hp2L
        val sizzleL = hpL_hstate

        val hpR_hstate = pinkR - hp1R - 1.2 * hp2R
        hp2R += fHp * hpR_hstate
        hp1R += fHp * hp2R
        val sizzleR = hpR_hstate

        val outVolumeL = rumbleL * 0.6 + sizzleL * 0.15
        val outVolumeR = rumbleR * 0.6 + sizzleR * 0.15

        val swell = 0.85 + gust * 0.15

        outL = (outVolumeL * swell).toFloat().coerceIn(-1.0f, 1.0f)
        outR = (outVolumeR * swell).toFloat().coerceIn(-1.0f, 1.0f)
    }
}

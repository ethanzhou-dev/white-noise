package top.ekiz.whitenoise.audio.generators

import kotlin.math.PI
import kotlin.math.abs

class WindGenerator : NoiseGenerator() {
    private var windLfo = 0.0
    private var windSvfLpL = 0.0
    private var windSvfBpL = 0.0
    private var windSvfLpR = 0.0
    private var windSvfBpR = 0.0

    private var piOverSr = 0.0
    private var lfoAlpha = 0.0

    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        piOverSr = PI / sr
        lfoAlpha = 2.0 * PI * 0.15 / sr
    }

    override fun reset() {
        windLfo = 0.0
        windSvfLpL = 0.0
        windSvfBpL = 0.0
        windSvfLpR = 0.0
        windSvfBpR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        windLfo += lfoAlpha * (wL - windLfo)

        val gustAmplified = windLfo * 80.0
        val softGust = gustAmplified / (1.0 + abs(gustAmplified))

        val gust01 = softGust * 0.5 + 0.5

        val baseFc = 80.0 + gust01 * gust01 * 170.0

        val damping = 1.414

        val fL = 2.0 * (baseFc * piOverSr)
        val hpL = wL - windSvfLpL - damping * windSvfBpL
        windSvfBpL += fL * hpL
        windSvfLpL += fL * windSvfBpL

        val hpR = wR - windSvfLpR - damping * windSvfBpR
        windSvfBpR += fL * hpR
        windSvfLpR += fL * windSvfBpR

        outL = (windSvfLpL * 10.0).toFloat()
        outR = (windSvfLpR * 10.0).toFloat()
    }
}

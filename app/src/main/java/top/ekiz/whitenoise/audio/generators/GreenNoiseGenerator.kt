package top.ekiz.whitenoise.audio.generators

class GreenNoiseGenerator : NoiseGenerator() {
    private var greenX1L = 0.0
    private var greenX2L = 0.0
    private var greenY1L = 0.0
    private var greenY2L = 0.0
    private var greenX1R = 0.0
    private var greenX2R = 0.0
    private var greenY1R = 0.0
    private var greenY2R = 0.0

    private var greenB0 = 0.066447
    private var greenB2 = -0.066447
    private var greenA1 = -1.862368
    private var greenA2 = 0.867096

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)

        val w0G = 2.0 * Math.PI * 500.0 / sr
        val alphaG = Math.sin(w0G) / 1.0
        val a0G = 1.0 + alphaG
        greenB0 = alphaG / a0G
        greenB2 = -alphaG / a0G
        greenA1 = -2.0 * Math.cos(w0G) / a0G
        greenA2 = (1.0 - alphaG) / a0G
    }

    override fun reset() {
        greenX1L = 0.0
        greenX2L = 0.0
        greenY1L = 0.0
        greenY2L = 0.0
        greenX1R = 0.0
        greenX2R = 0.0
        greenY1R = 0.0
        greenY2R = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        val outY_L = greenB0 * wL + greenB2 * greenX2L - greenA1 * greenY1L - greenA2 * greenY2L
        greenX2L = greenX1L
        greenX1L = wL
        greenY2L = greenY1L
        greenY1L = outY_L
        outL = (outY_L * 4.0).toFloat()

        val outY_R = greenB0 * wR + greenB2 * greenX2R - greenA1 * greenY1R - greenA2 * greenY2R
        greenX2R = greenX1R
        greenX1R = wR
        greenY2R = greenY1R
        greenY1R = outY_R
        outR = (outY_R * 4.0).toFloat()
    }
}

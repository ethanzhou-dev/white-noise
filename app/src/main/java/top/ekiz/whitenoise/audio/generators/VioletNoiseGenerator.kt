package top.ekiz.whitenoise.audio.generators

class VioletNoiseGenerator : NoiseGenerator() {
    private var lastWhiteL = 0.0
    private var lastWhiteR = 0.0

    override fun reset() {
        lastWhiteL = 0.0
        lastWhiteR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        outL = (wL - lastWhiteL).toFloat()
        outR = (wR - lastWhiteR).toFloat()

        lastWhiteL = wL
        lastWhiteR = wR
    }
}

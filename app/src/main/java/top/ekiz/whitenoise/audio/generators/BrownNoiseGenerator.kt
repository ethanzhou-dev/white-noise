package top.ekiz.whitenoise.audio.generators

class BrownNoiseGenerator : NoiseGenerator() {
    private var lastBrownOutL = 0.0
    private var lastBrownOutR = 0.0

    override fun reset() {
        lastBrownOutL = 0.0
        lastBrownOutR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        lastBrownOutL = (lastBrownOutL + (0.02 * wL)) / 1.02
        outL = (lastBrownOutL * 3.5).toFloat()

        lastBrownOutR = (lastBrownOutR + (0.02 * wR)) / 1.02
        outR = (lastBrownOutR * 3.5).toFloat()
    }
}

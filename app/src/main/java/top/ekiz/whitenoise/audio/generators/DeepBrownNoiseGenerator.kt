package top.ekiz.whitenoise.audio.generators

class DeepBrownNoiseGenerator : NoiseGenerator() {
    private var lastDeepBrownOutL = 0.0
    private var lastDeepBrownOutR = 0.0

    override fun reset() {
        lastDeepBrownOutL = 0.0
        lastDeepBrownOutR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        lastDeepBrownOutL = lastDeepBrownOutL * 0.9901 + wL * 0.0099
        outL = (lastDeepBrownOutL * 5.0).toFloat()

        lastDeepBrownOutR = lastDeepBrownOutR * 0.9901 + wR * 0.0099
        outR = (lastDeepBrownOutR * 5.0).toFloat()
    }
}

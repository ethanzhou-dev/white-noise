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

        lastDeepBrownOutL = (lastDeepBrownOutL + (0.01 * wL)) / 1.01
        outL = (lastDeepBrownOutL * 5.0).toFloat()
        
        lastDeepBrownOutR = (lastDeepBrownOutR + (0.01 * wR)) / 1.01
        outR = (lastDeepBrownOutR * 5.0).toFloat()
    }
}

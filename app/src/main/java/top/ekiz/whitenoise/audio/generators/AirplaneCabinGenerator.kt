package top.ekiz.whitenoise.audio.generators

class AirplaneCabinGenerator : NoiseGenerator() {
    private var lastDeepBrownOutL = 0.0
    private var lastDeepBrownOutR = 0.0
    private var cabinPhase = 0.0

    override fun reset() {
        lastDeepBrownOutL = 0.0
        lastDeepBrownOutR = 0.0
        cabinPhase = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        lastDeepBrownOutL = (lastDeepBrownOutL + (0.01 * wL)) / 1.01
        lastDeepBrownOutR = (lastDeepBrownOutR + (0.01 * wR)) / 1.01
        
        cabinPhase += 2.0 * Math.PI * 160.0 / sampleRate
        if (cabinPhase > 2.0 * Math.PI) cabinPhase -= 2.0 * Math.PI
        val drone = kotlin.math.sin(cabinPhase) * 0.2
        
        outL = (lastDeepBrownOutL * 4.0 + drone).toFloat()
        outR = (lastDeepBrownOutR * 4.0 + drone).toFloat()
    }
}

package top.ekiz.whitenoise.audio.generators

class BlackNoiseGenerator : NoiseGenerator() {
    private var lastBrownOutL = 0.0
    private var lastBlackOutL = 0.0
    
    private var lastBrownOutR = 0.0
    private var lastBlackOutR = 0.0

    override fun reset() {
        lastBrownOutL = 0.0
        lastBlackOutL = 0.0
        lastBrownOutR = 0.0
        lastBlackOutR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        lastBrownOutL = lastBrownOutL * 0.98039 + wL * 0.01961
        lastBlackOutL = lastBlackOutL * 0.9901 + lastBrownOutL * 0.0099
        outL = (lastBlackOutL * 10.0).toFloat()
        
        lastBrownOutR = lastBrownOutR * 0.98039 + wR * 0.01961
        lastBlackOutR = lastBlackOutR * 0.9901 + lastBrownOutR * 0.0099
        outR = (lastBlackOutR * 10.0).toFloat()
    }
}

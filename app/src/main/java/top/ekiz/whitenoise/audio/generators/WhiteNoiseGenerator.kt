package top.ekiz.whitenoise.audio.generators

class WhiteNoiseGenerator : NoiseGenerator() {
    override fun reset() {
        // No state needed
    }

    override fun process(whiteL: Float, whiteR: Float) {
        outL = whiteL
        outR = whiteR
    }
}

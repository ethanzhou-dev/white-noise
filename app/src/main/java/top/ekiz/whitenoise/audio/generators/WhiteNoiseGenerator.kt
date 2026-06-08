package top.ekiz.whitenoise.audio.generators

class WhiteNoiseGenerator : NoiseGenerator() {
    override fun reset() {}

    override fun process(whiteL: Float, whiteR: Float) {
        outL = whiteL
        outR = whiteR
    }
}

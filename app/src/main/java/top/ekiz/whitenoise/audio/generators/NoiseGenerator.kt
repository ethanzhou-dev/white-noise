package top.ekiz.whitenoise.audio.generators

abstract class NoiseGenerator {
    var outL: Float = 0f
    var outR: Float = 0f
    var sampleRate: Double = 44100.0

    open fun updateSampleRate(sr: Double) {
        sampleRate = sr
    }

    abstract fun reset()

    abstract fun process(whiteL: Float, whiteR: Float)
}

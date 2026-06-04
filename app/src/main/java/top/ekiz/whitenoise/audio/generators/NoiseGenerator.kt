package top.ekiz.whitenoise.audio.generators

abstract class NoiseGenerator {
    var outL: Float = 0f
    var outR: Float = 0f
    var sampleRate: Double = 44100.0
    
    open fun updateSampleRate(sr: Double) {
        sampleRate = sr
    }
    
    abstract fun reset()
    
    /**
     * @param whiteL the input random white noise for the left channel (-1.0 to 1.0)
     * @param whiteR the input random white noise for the right channel (-1.0 to 1.0)
     */
    abstract fun process(whiteL: Float, whiteR: Float)
}

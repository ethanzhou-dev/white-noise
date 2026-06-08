package top.ekiz.whitenoise.audio.generators

class VelvetNoiseGenerator : NoiseGenerator() {
    private var velvetCounterL = 0
    private var velvetGridL = 22
    private var velvetPulseL = 0.0
    private var velvetLpfL = 0.0

    private var velvetCounterR = 0
    private var velvetGridR = 22
    private var velvetPulseR = 0.0
    private var velvetLpfR = 0.0

    private var baseGrid = 15
    private var varianceGrid = 15.0

    init {
        updateSampleRate(44100.0)
    }

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)

        baseGrid = 1.coerceAtLeast((0.00034 * sr).toInt())
        varianceGrid = 0.00034 * sr
    }

    override fun reset() {
        velvetCounterL = 0
        velvetPulseL = 0.0
        velvetLpfL = 0.0
        velvetCounterR = 0
        velvetPulseR = 0.0
        velvetLpfR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        velvetCounterL++
        if (velvetCounterL >= velvetGridL) {
            velvetCounterL = 0
            velvetGridL = baseGrid + ((wL + 1.0) * 0.5 * varianceGrid).toInt()
            velvetPulseL = if (wL > 0) 1.0 else -1.0
        } else {
            velvetPulseL = 0.0
        }
        velvetLpfL = velvetLpfL * 0.95238 + velvetPulseL * 0.04762
        outL = (velvetLpfL * 2.5).toFloat()

        velvetCounterR++
        if (velvetCounterR >= velvetGridR) {
            velvetCounterR = 0
            velvetGridR = baseGrid + ((wR + 1.0) * 0.5 * varianceGrid).toInt()
            velvetPulseR = if (wR > 0) 1.0 else -1.0
        } else {
            velvetPulseR = 0.0
        }
        velvetLpfR = velvetLpfR * 0.95238 + velvetPulseR * 0.04762
        outR = (velvetLpfR * 2.5).toFloat()
    }
}

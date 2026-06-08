package top.ekiz.whitenoise.audio.generators

class PinkNoiseGenerator : NoiseGenerator() {
    private var b0L = 0.0
    private var b1L = 0.0
    private var b2L = 0.0
    private var b3L = 0.0
    private var b4L = 0.0
    private var b5L = 0.0
    private var b6L = 0.0

    private var b0R = 0.0
    private var b1R = 0.0
    private var b2R = 0.0
    private var b3R = 0.0
    private var b4R = 0.0
    private var b5R = 0.0
    private var b6R = 0.0

    override fun reset() {
        b0L = 0.0
        b1L = 0.0
        b2L = 0.0
        b3L = 0.0
        b4L = 0.0
        b5L = 0.0
        b6L = 0.0
        b0R = 0.0
        b1R = 0.0
        b2R = 0.0
        b3R = 0.0
        b4R = 0.0
        b5R = 0.0
        b6R = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        b0L = 0.99886 * b0L + wL * 0.0555179
        b1L = 0.99332 * b1L + wL * 0.0750759
        b2L = 0.96900 * b2L + wL * 0.1538520
        b3L = 0.86650 * b3L + wL * 0.3104856
        b4L = 0.55000 * b4L + wL * 0.5329522
        b5L = -0.7616 * b5L - wL * 0.0168980
        val pinkL = b0L + b1L + b2L + b3L + b4L + b5L + b6L + wL * 0.5362
        b6L = wL * 0.115926
        outL = (pinkL * 0.11).toFloat()

        b0R = 0.99886 * b0R + wR * 0.0555179
        b1R = 0.99332 * b1R + wR * 0.0750759
        b2R = 0.96900 * b2R + wR * 0.1538520
        b3R = 0.86650 * b3R + wR * 0.3104856
        b4R = 0.55000 * b4R + wR * 0.5329522
        b5R = -0.7616 * b5R - wR * 0.0168980
        val pinkR = b0R + b1R + b2R + b3R + b4R + b5R + b6R + wR * 0.5362
        b6R = wR * 0.115926
        outR = (pinkR * 0.11).toFloat()
    }
}

package top.ekiz.whitenoise.audio.generators

class BlueNoiseGenerator : NoiseGenerator() {
    private var b0BlueL = 0.0; private var b1BlueL = 0.0; private var b2BlueL = 0.0; private var b3BlueL = 0.0
    private var b4BlueL = 0.0; private var b5BlueL = 0.0; private var b6BlueL = 0.0
    private var lastBluePinkL = 0.0

    private var b0BlueR = 0.0; private var b1BlueR = 0.0; private var b2BlueR = 0.0; private var b3BlueR = 0.0
    private var b4BlueR = 0.0; private var b5BlueR = 0.0; private var b6BlueR = 0.0
    private var lastBluePinkR = 0.0

    override fun reset() {
        b0BlueL = 0.0; b1BlueL = 0.0; b2BlueL = 0.0; b3BlueL = 0.0; b4BlueL = 0.0; b5BlueL = 0.0; b6BlueL = 0.0; lastBluePinkL = 0.0
        b0BlueR = 0.0; b1BlueR = 0.0; b2BlueR = 0.0; b3BlueR = 0.0; b4BlueR = 0.0; b5BlueR = 0.0; b6BlueR = 0.0; lastBluePinkR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        b0BlueL = 0.99886 * b0BlueL + wL * 0.0555179
        b1BlueL = 0.99332 * b1BlueL + wL * 0.0750759
        b2BlueL = 0.96900 * b2BlueL + wL * 0.1538520
        b3BlueL = 0.86650 * b3BlueL + wL * 0.3104856
        b4BlueL = 0.55000 * b4BlueL + wL * 0.5329522
        b5BlueL = -0.7616 * b5BlueL - wL * 0.0168980
        val pinkL = b0BlueL + b1BlueL + b2BlueL + b3BlueL + b4BlueL + b5BlueL + b6BlueL + wL * 0.5362
        b6BlueL = wL * 0.115926
        outL = ((pinkL - lastBluePinkL) * 2.0).toFloat()
        lastBluePinkL = pinkL
        
        b0BlueR = 0.99886 * b0BlueR + wR * 0.0555179
        b1BlueR = 0.99332 * b1BlueR + wR * 0.0750759
        b2BlueR = 0.96900 * b2BlueR + wR * 0.1538520
        b3BlueR = 0.86650 * b3BlueR + wR * 0.3104856
        b4BlueR = 0.55000 * b4BlueR + wR * 0.5329522
        b5BlueR = -0.7616 * b5BlueR - wR * 0.0168980
        val pinkR = b0BlueR + b1BlueR + b2BlueR + b3BlueR + b4BlueR + b5BlueR + b6BlueR + wR * 0.5362
        b6BlueR = wR * 0.115926
        outR = ((pinkR - lastBluePinkR) * 2.0).toFloat()
        lastBluePinkR = pinkR
    }
}

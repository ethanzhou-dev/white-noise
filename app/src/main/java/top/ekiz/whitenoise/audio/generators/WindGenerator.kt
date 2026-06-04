package top.ekiz.whitenoise.audio.generators

class WindGenerator : NoiseGenerator() {
    private var windLfo = 0.0
    private var windSvfLpL = 0.0; private var windSvfBpL = 0.0
    private var windSvfLpR = 0.0; private var windSvfBpR = 0.0

    override fun reset() {
        windLfo = 0.0; windSvfLpL = 0.0; windSvfBpL = 0.0; windSvfLpR = 0.0; windSvfBpR = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        val alpha = 2.0 * Math.PI * 0.15 / sampleRate
        windLfo += alpha * (wL - windLfo)
        
        val gustAmplified = windLfo * 80.0
        val softGust = gustAmplified / (1.0 + kotlin.math.abs(gustAmplified))
        
        val gust01 = softGust * 0.5 + 0.5
        
        val baseFc = 80.0 + gust01 * gust01 * 170.0
        val fcL = baseFc
        val fcR = baseFc
        
        val damping = 1.414 
        val pi_over_sr = Math.PI / sampleRate
        
        val fL = 2.0 * (fcL * pi_over_sr)
        val hpL = wL - windSvfLpL - damping * windSvfBpL
        windSvfBpL += fL * hpL
        windSvfLpL += fL * windSvfBpL
        
        val fR = 2.0 * (fcR * pi_over_sr)
        val hpR = wR - windSvfLpR - damping * windSvfBpR
        windSvfBpR += fR * hpR
        windSvfLpR += fR * windSvfBpR
        
        outL = (windSvfLpL * 10.0).toFloat()
        outR = (windSvfLpR * 10.0).toFloat()
    }
}

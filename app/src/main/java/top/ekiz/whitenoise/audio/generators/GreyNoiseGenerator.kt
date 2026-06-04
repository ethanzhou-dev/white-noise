package top.ekiz.whitenoise.audio.generators

class GreyNoiseGenerator : NoiseGenerator() {
    private var greyLx1L = 0.0; private var greyLx2L = 0.0; private var greyLy1L = 0.0; private var greyLy2L = 0.0
    private var greyHx1L = 0.0; private var greyHx2L = 0.0; private var greyHy1L = 0.0; private var greyHy2L = 0.0
    
    private var greyLx1R = 0.0; private var greyLx2R = 0.0; private var greyLy1R = 0.0; private var greyLy2R = 0.0
    private var greyHx1R = 0.0; private var greyHx2R = 0.0; private var greyHy1R = 0.0; private var greyHy2R = 0.0

    private var greyL_b0 = 1.02268; private var greyL_b1 = -1.96607; private var greyL_b2 = 0.94636; private var greyL_a1 = -1.96728; private var greyL_a2 = 0.96781
    private var greyH_b0 = 1.78054; private var greyH_b1 = -1.33301; private var greyH_b2 = 0.48501; private var greyH_a1 = -0.25003; private var greyH_a2 = 0.18257

    override fun updateSampleRate(sr: Double) {
        super.updateSampleRate(sr)
        // Grey Low Shelf (Fc=250Hz, Gain=15dB)
        val aL = Math.pow(10.0, 15.0 / 40.0)
        val w0L = 2.0 * Math.PI * 250.0 / sr
        val alphaL = Math.sin(w0L) / Math.sqrt(2.0)
        val cosW0L = Math.cos(w0L)
        val a0L = (aL + 1.0) + (aL - 1.0) * cosW0L + 2.0 * Math.sqrt(aL) * alphaL
        greyL_b0 = (aL * ((aL + 1.0) - (aL - 1.0) * cosW0L + 2.0 * Math.sqrt(aL) * alphaL)) / a0L
        greyL_b1 = (2.0 * aL * ((aL - 1.0) - (aL + 1.0) * cosW0L)) / a0L
        greyL_b2 = (aL * ((aL + 1.0) - (aL - 1.0) * cosW0L - 2.0 * Math.sqrt(aL) * alphaL)) / a0L
        greyL_a1 = (-2.0 * ((aL - 1.0) + (aL + 1.0) * cosW0L)) / a0L
        greyL_a2 = ((aL + 1.0) + (aL - 1.0) * cosW0L - 2.0 * Math.sqrt(aL) * alphaL) / a0L

        // Grey High Shelf (Fc=8000Hz, Gain=8dB)
        val aH = Math.pow(10.0, 8.0 / 40.0)
        val w0H = 2.0 * Math.PI * 8000.0 / sr
        val alphaH = Math.sin(w0H) / Math.sqrt(2.0)
        val cosW0H = Math.cos(w0H)
        val a0H = (aH + 1.0) - (aH - 1.0) * cosW0H + 2.0 * Math.sqrt(aH) * alphaH
        greyH_b0 = (aH * ((aH + 1.0) + (aH - 1.0) * cosW0H + 2.0 * Math.sqrt(aH) * alphaH)) / a0H
        greyH_b1 = (-2.0 * aH * ((aH - 1.0) + (aH + 1.0) * cosW0H)) / a0H
        greyH_b2 = (aH * ((aH + 1.0) + (aH - 1.0) * cosW0H - 2.0 * Math.sqrt(aH) * alphaH)) / a0H
        greyH_a1 = (2.0 * ((aH - 1.0) - (aH + 1.0) * cosW0H)) / a0H
        greyH_a2 = ((aH + 1.0) - (aH - 1.0) * cosW0H - 2.0 * Math.sqrt(aH) * alphaH) / a0H
    }

    override fun reset() {
        greyLx1L = 0.0; greyLx2L = 0.0; greyLy1L = 0.0; greyLy2L = 0.0
        greyLx1R = 0.0; greyLx2R = 0.0; greyLy1R = 0.0; greyLy2R = 0.0
        greyHx1L = 0.0; greyHx2L = 0.0; greyHy1L = 0.0; greyHy2L = 0.0
        greyHx1R = 0.0; greyHx2R = 0.0; greyHy1R = 0.0; greyHy2R = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        val wL = whiteL.toDouble()
        val wR = whiteR.toDouble()

        // Inverse A-Weighting Approximation via Biquad Shelving Filters
        // 1. Low Shelf
        val midL = greyL_b0 * wL + greyL_b1 * greyLx1L + greyL_b2 * greyLx2L - greyL_a1 * greyLy1L - greyL_a2 * greyLy2L
        greyLx2L = greyLx1L; greyLx1L = wL
        greyLy2L = greyLy1L; greyLy1L = midL
        
        // 2. High Shelf
        val outY_L = greyH_b0 * midL + greyH_b1 * greyHx1L + greyH_b2 * greyHx2L - greyH_a1 * greyHy1L - greyH_a2 * greyHy2L
        greyHx2L = greyHx1L; greyHx1L = midL
        greyHy2L = greyHy1L; greyHy1L = outY_L
        
        outL = (outY_L * 0.12).toFloat() // Attenuate to avoid clipping
        
        val midR = greyL_b0 * wR + greyL_b1 * greyLx1R + greyL_b2 * greyLx2R - greyL_a1 * greyLy1R - greyL_a2 * greyLy2R
        greyLx2R = greyLx1R; greyLx1R = wR
        greyLy2R = greyLy1R; greyLy1R = midR
        
        val outY_R = greyH_b0 * midR + greyH_b1 * greyHx1R + greyH_b2 * greyHx2R - greyH_a1 * greyHy1R - greyH_a2 * greyHy2R
        greyHx2R = greyHx1R; greyHx1R = midR
        greyHy2R = greyHy1R; greyHy1R = outY_R
        
        outR = (outY_R * 0.12).toFloat()
    }
}

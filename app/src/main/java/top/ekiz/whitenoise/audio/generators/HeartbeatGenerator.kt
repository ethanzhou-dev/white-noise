package top.ekiz.whitenoise.audio.generators

class HeartbeatGenerator : NoiseGenerator() {
    private var heartPhase = 0.0
    private var heartTonePhase = 0.0

    override fun reset() {
        heartPhase = 0.0
        heartTonePhase = 0.0
    }

    override fun process(whiteL: Float, whiteR: Float) {
        heartPhase += 1.0 / sampleRate
        if (heartPhase > 1.0) heartPhase -= 1.0
        
        var env = 0.0
        var freq = 50.0
        if (heartPhase < 0.15) { // Lub (150ms)
            env = kotlin.math.sin(heartPhase / 0.15 * Math.PI)
            freq = 60.0 - (heartPhase / 0.15) * 20.0
        } else if (heartPhase > 0.3 && heartPhase < 0.4) { // Dub (100ms)
            env = kotlin.math.sin((heartPhase - 0.3) / 0.1 * Math.PI) * 0.8
            freq = 55.0 - ((heartPhase - 0.3) / 0.1) * 15.0
        }
        
        heartTonePhase += 2.0 * Math.PI * freq / sampleRate
        if (heartTonePhase > 2.0 * Math.PI) heartTonePhase -= 2.0 * Math.PI
        
        val tone = (kotlin.math.sin(heartTonePhase) * env * 0.85).toFloat()
        
        outL = tone
        outR = tone
    }
}

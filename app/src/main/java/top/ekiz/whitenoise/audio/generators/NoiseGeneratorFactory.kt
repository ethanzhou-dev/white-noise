package top.ekiz.whitenoise.audio.generators

import top.ekiz.whitenoise.domain.NoiseType

object NoiseGeneratorFactory {
    fun create(type: NoiseType): NoiseGenerator {
        return when (type) {
            NoiseType.WHITE -> WhiteNoiseGenerator()
            NoiseType.PINK -> PinkNoiseGenerator()
            NoiseType.BROWN -> BrownNoiseGenerator()
            NoiseType.DEEP_BROWN -> DeepBrownNoiseGenerator()
            NoiseType.BLUE -> BlueNoiseGenerator()
            NoiseType.VIOLET -> VioletNoiseGenerator()
            NoiseType.GREY -> GreyNoiseGenerator()
            NoiseType.GREEN -> GreenNoiseGenerator()
            NoiseType.BLACK -> BlackNoiseGenerator()
            NoiseType.VELVET -> VelvetNoiseGenerator()
            NoiseType.OCEAN_WAVES -> OceanWavesGenerator()
            NoiseType.BINAURAL_BEATS -> BinauralBeatsGenerator()
            NoiseType.WIND -> WindGenerator()
            NoiseType.AIRPLANE_CABIN -> AirplaneCabinGenerator()
            NoiseType.HEARTBEAT -> HeartbeatGenerator()
            NoiseType.ISOCHRONIC_TONES -> IsochronicTonesGenerator()
            NoiseType.SOLFEGGIO_FREQUENCIES -> SolfeggioFrequenciesGenerator()
        }
    }
}

package com.dsq.rebackground.paint.material

object DryPaperResponse {
    fun coverageFactor(
        paperHeight: Float,
        heightAmplitude: Float,
        affinity: Float,
        pressure: Float,
        thresholdLow: Float = 0.32f,
        thresholdHigh: Float = 0.68f,
    ): Float {
        require(paperHeight.isFinite() && paperHeight in 0f..1f)
        require(heightAmplitude.isFinite() && heightAmplitude in 0f..1f)
        require(affinity.isFinite() && affinity in 0f..1f)
        require(pressure.isFinite() && pressure in 0f..1f)
        require(thresholdLow in 0f..1f && thresholdHigh in 0f..1f && thresholdLow < thresholdHigh)
        val contact = 0.5f + (paperHeight - 0.5f) * heightAmplitude + (pressure - 0.5f) * 0.16f
        val tooth = 1f +
            (smoothstep(thresholdLow, thresholdHigh, contact) - 1f) * heightAmplitude
        return (1f + (tooth - 1f) * affinity).coerceIn(0f, 1f)
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

package com.dsq.rebackground.paint.material

import kotlin.math.exp

data class DryBrushLoadConfig(
    val initialLoad: Float = 1f,
    val depletionRatePerDocumentUnit: Float = 0f,
    val rechargeRatePerDocumentUnit: Float = 0f,
    val speedDepletionInfluence: Float = 0f,
) {
    init {
        require(initialLoad.isFinite() && initialLoad in 0f..1f)
        require(depletionRatePerDocumentUnit.isFinite() && depletionRatePerDocumentUnit >= 0f)
        require(rechargeRatePerDocumentUnit.isFinite() && rechargeRatePerDocumentUnit >= 0f)
        require(speedDepletionInfluence.isFinite() && speedDepletionInfluence in 0f..1f)
    }
}

object DryBrushLoad {
    fun advance(
        currentLoad: Float,
        distanceDocumentUnits: Float,
        normalizedSpeed: Float,
        config: DryBrushLoadConfig,
    ): Float {
        require(currentLoad.isFinite() && currentLoad in 0f..1f)
        require(distanceDocumentUnits.isFinite() && distanceDocumentUnits >= 0f)
        require(normalizedSpeed.isFinite() && normalizedSpeed in 0f..1f)
        if (distanceDocumentUnits == 0f) return currentLoad
        val recharged = (currentLoad + config.rechargeRatePerDocumentUnit * distanceDocumentUnits)
            .coerceIn(0f, 1f)
        val rate = config.depletionRatePerDocumentUnit *
            (1f + normalizedSpeed * config.speedDepletionInfluence)
        return (recharged * exp((-rate * distanceDocumentUnits).toDouble()).toFloat()).coerceIn(0f, 1f)
    }
}

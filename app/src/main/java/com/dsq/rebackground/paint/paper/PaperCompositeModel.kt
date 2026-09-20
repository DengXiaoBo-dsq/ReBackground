package com.dsq.rebackground.paint.paper

import com.dsq.rebackground.paint.pigment.PigmentColor

/** Combines paper base colour, pigment and sampled paper grain into a normalized colour. */
class PaperCompositeModel {
    fun composite(paperBase: PigmentColor, pigment: PigmentColor, grain: Float): PigmentColor {
        require(grain.isFinite() && grain in 0f..1f)
        val pigmentWeight = .35f + grain * .65f
        return PigmentColor(
            (paperBase.red * (1f - pigmentWeight) + pigment.red * pigmentWeight).coerceIn(0f, 1f),
            (paperBase.green * (1f - pigmentWeight) + pigment.green * pigmentWeight).coerceIn(0f, 1f),
            (paperBase.blue * (1f - pigmentWeight) + pigment.blue * pigmentWeight).coerceIn(0f, 1f)
        )
    }
}

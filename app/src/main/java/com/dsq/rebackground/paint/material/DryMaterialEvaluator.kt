package com.dsq.rebackground.paint.material

import com.dsq.rebackground.paint.bristle.DryBristleField
import com.dsq.rebackground.paint.brush.BrushMaterial
import com.dsq.rebackground.paint.paper.PaperHeightField
import com.dsq.rebackground.paint.paper.PaperMaterial

/** CPU reference for the same four-stage dry-material model used by the stamp shader. */
object DryMaterialEvaluator {
    fun evaluate(
        brushCoverage: Float,
        load: Float,
        documentX: Float,
        documentY: Float,
        localNormal: Float,
        arcLengthDocumentUnits: Float,
        pressure: Float,
        brush: BrushMaterial,
        paper: PaperMaterial,
        paperField: PaperHeightField,
    ): DryDeposit {
        val height = paperField.sample(documentX, documentY, paper.grainScale)
        val paperFactor = DryPaperResponse.coverageFactor(
            height,
            paper.heightAmplitude,
            brush.paperGrainAffinity,
            pressure,
        )
        val bristleFactor = DryBristleField.coverage(
            localNormal,
            arcLengthDocumentUnits,
            brush.bristleDensity,
            brush.bristleSeed,
        )
        return DryDeposit(brushCoverage, load, paperFactor, bristleFactor)
    }
}

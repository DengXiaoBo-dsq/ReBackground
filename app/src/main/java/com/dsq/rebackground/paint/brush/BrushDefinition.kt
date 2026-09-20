package com.dsq.rebackground.paint.brush

/** Immutable, serializable brush recipe. Runtime motion state and paint fields are deliberately absent. */
data class BrushDefinition(
    val id: String,
    val displayName: String,
    val baseDiameterDocumentUnits: Float,
    val tip: BrushTip = BrushTip.Round,
    val material: BrushMaterial = BrushMaterial(),
    val dynamics: BrushDynamics = BrushDynamics(),
    val opacity: Float = 1f,
    val flow: Float = 1f
) {
    init {
        require(id.isNotBlank())
        require(displayName.isNotBlank())
        require(baseDiameterDocumentUnits.isFinite() && baseDiameterDocumentUnits > 0f)
        require(opacity.isFinite() && opacity in 0f..1f)
        require(flow.isFinite() && flow in 0f..1f)
    }
}

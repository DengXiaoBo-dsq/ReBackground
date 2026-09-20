package com.dsq.rebackground.paint.pigment

/**
 * A semantic paint deposit expressed in document coordinates.
 * It is generated from brush geometry but does not know how it will be rendered.
 */
data class PigmentDeposit(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val color: PigmentColor,
    val amount: Float = 1f,
    val wetness: Float = 0f
) {
    init {
        require(centerX.isFinite() && centerY.isFinite()) { "Deposit centre must be finite" }
        require(radius.isFinite() && radius > 0f) { "Deposit radius must be positive and finite" }
        require(amount.isFinite() && amount in 0f..1f) { "Deposit amount must be normalized" }
        require(wetness.isFinite() && wetness in 0f..1f) { "Deposit wetness must be normalized" }
    }
}

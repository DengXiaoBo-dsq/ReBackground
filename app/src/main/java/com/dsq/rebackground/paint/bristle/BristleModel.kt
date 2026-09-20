package com.dsq.rebackground.paint.bristle

/** Static bristle-brush recipe. Runtime tip positions live in BristleField. */
data class BristleModel(
    val count: Int = 64,
    val length: Float = 1f,
    val stiffness: Float = 0.85f,
    val spread: Float = 0.25f,
    val damping: Float = 0.9f
) {
    init {
        require(count > 0)
        require(length.isFinite() && length > 0f)
        require(stiffness.isFinite() && stiffness in 0f..1f)
        require(spread.isFinite() && spread > 0f)
        require(damping.isFinite() && damping in 0f..1f)
    }
}

/** Normalized instantaneous input used by BristleSimulator. */
data class BristleInput(
    val pressure: Float = 1f,
    val tiltX: Float = 0f,
    val tiltY: Float = 0f,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val wetness: Float = 0f
) {
    init {
        require(pressure.isFinite() && pressure in 0f..1f)
        require(wetness.isFinite() && wetness in 0f..1f)
        require(tiltX.isFinite() && tiltY.isFinite() && velocityX.isFinite() && velocityY.isFinite())
    }
}

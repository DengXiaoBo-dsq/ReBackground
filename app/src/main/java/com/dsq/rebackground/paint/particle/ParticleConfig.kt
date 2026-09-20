package com.dsq.rebackground.paint.particle

/** Emission and integration parameters for the P5 micro-detail particle system. */
data class ParticleConfig(
    val droplets: Int = 6,
    val splatters: Int = 4,
    val dryFragments: Int = 2,
    val baseSpeed: Float = 24f,
    val gravity: Float = 90f,
    val damping: Float = 2.5f,
    val maximumLife: Float = 2f
) {
    init {
        require(droplets >= 0 && splatters >= 0 && dryFragments >= 0)
        require(baseSpeed.isFinite() && baseSpeed >= 0f)
        require(gravity.isFinite() && gravity >= 0f)
        require(damping.isFinite() && damping >= 0f)
        require(maximumLife.isFinite() && maximumLife > 0f)
    }
}

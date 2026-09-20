package com.dsq.rebackground.paint.particle

import com.dsq.rebackground.paint.pigment.PigmentDeposit
import kotlin.random.Random

/** P5 particle facade. It owns only micro-detail state and is intentionally independent of GL/Bitmap. */
class ParticleSystem(
    capacity: Int = 512,
    private val config: ParticleConfig = ParticleConfig(),
    random: Random = Random.Default
) {
    val pool = ParticlePool(capacity)
    private val emitter = ParticleEmitter(config, random)

    val activeCount: Int get() = pool.activeCount

    fun emit(deposit: PigmentDeposit) {
        emitter.emit(pool, deposit)
    }

    fun step(dt: Float) {
        require(dt.isFinite() && dt >= 0f)
        if (dt == 0f) return
        val velocityFactor = (1f - config.damping * dt).coerceIn(0f, 1f)
        for (index in 0 until pool.capacity) {
            if (!pool.alive[index]) continue

            pool.vy[index] += config.gravity * dt
            pool.vx[index] *= velocityFactor
            pool.vy[index] *= velocityFactor
            pool.x[index] += pool.vx[index] * dt
            pool.y[index] += pool.vy[index] * dt
            pool.life[index] += dt
            if (pool.life[index] >= pool.maxLife[index]) pool.kill(index)
        }
    }

    fun clear() {
        pool.clear()
    }
}

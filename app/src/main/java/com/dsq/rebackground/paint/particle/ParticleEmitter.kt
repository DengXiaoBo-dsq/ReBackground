package com.dsq.rebackground.paint.particle

import com.dsq.rebackground.paint.pigment.PigmentDeposit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Converts semantic pigment deposits into small droplet/splatter/fragment particles. */
class ParticleEmitter(
    private val config: ParticleConfig,
    private val random: Random = Random.Default
) {
    fun emit(pool: ParticlePool, deposit: PigmentDeposit) {
        repeat(config.droplets) { spawn(pool, ParticleKind.DROPLET, deposit, .9f, 1f) }
        repeat(config.splatters) { spawn(pool, ParticleKind.SPLATTER, deposit, 1.4f, .7f) }
        repeat(config.dryFragments) { spawn(pool, ParticleKind.DRY_FRAGMENT, deposit, .55f, .35f) }
    }

    private fun spawn(
        pool: ParticlePool,
        kind: ParticleKind,
        deposit: PigmentDeposit,
        speedMultiplier: Float,
        lifeMultiplier: Float
    ) {
        val index = pool.spawn()
        if (index < 0) return

        val angle = random.nextFloat() * 2f * PI.toFloat()
        val speed = config.baseSpeed * speedMultiplier * (.55f + random.nextFloat() * .9f)
        pool.kind[index] = kind
        pool.x[index] = deposit.centerX + cos(angle) * deposit.radius * random.nextFloat()
        pool.y[index] = deposit.centerY + sin(angle) * deposit.radius * random.nextFloat()
        pool.vx[index] = cos(angle) * speed
        pool.vy[index] = sin(angle) * speed
        pool.life[index] = 0f
        pool.maxLife[index] = config.maximumLife * lifeMultiplier * (.6f + random.nextFloat() * .8f)
        pool.size[index] = deposit.radius * (.05f + random.nextFloat() * .12f)
        pool.red[index] = deposit.color.red
        pool.green[index] = deposit.color.green
        pool.blue[index] = deposit.color.blue
    }
}

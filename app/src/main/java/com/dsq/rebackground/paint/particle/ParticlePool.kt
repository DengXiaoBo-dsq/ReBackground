package com.dsq.rebackground.paint.particle

/** Fixed-capacity SoA particle pool. */
class ParticlePool(val capacity: Int) {
    init {
        require(capacity > 0)
    }

    internal val alive = BooleanArray(capacity)
    internal val kind = arrayOfNulls<ParticleKind>(capacity)
    internal val x = FloatArray(capacity)
    internal val y = FloatArray(capacity)
    internal val vx = FloatArray(capacity)
    internal val vy = FloatArray(capacity)
    internal val life = FloatArray(capacity)
    internal val maxLife = FloatArray(capacity)
    internal val size = FloatArray(capacity)
    internal val red = FloatArray(capacity)
    internal val green = FloatArray(capacity)
    internal val blue = FloatArray(capacity)

    val activeCount: Int get() = alive.count { it }

    fun spawn(): Int {
        for (index in 0 until capacity) {
            if (!alive[index]) {
                alive[index] = true
                return index
            }
        }
        return -1
    }

    fun kill(index: Int) {
        require(index in 0 until capacity)
        alive[index] = false
        kind[index] = null
    }

    fun clear() {
        alive.fill(false)
        kind.fill(null)
    }
}

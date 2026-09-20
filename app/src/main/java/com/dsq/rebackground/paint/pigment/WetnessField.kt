package com.dsq.rebackground.paint.pigment

/** CPU-owned water/wetness scalar field. */
class WetnessField(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Wetness field dimensions must be positive" }
    }

    private val size = width * height
    internal val values = FloatArray(size)

    fun clear() {
        values.fill(0f)
    }

    fun get(x: Int, y: Int): Float {
        val index = index(x, y)
        return values[index]
    }

    fun add(x: Int, y: Int, amount: Float) {
        require(amount.isFinite() && amount >= 0f) { "Wetness amount must be finite and non-negative" }
        val index = index(x, y)
        values[index] = (values[index] + amount).coerceIn(0f, 1f)
    }

    fun scale(factor: Float) {
        require(factor.isFinite() && factor >= 0f) { "Wetness scale factor must be finite and non-negative" }
        for (index in 0 until size) {
            values[index] = (values[index] * factor).coerceIn(0f, 1f)
        }
    }

    internal fun setValues(newValues: FloatArray) {
        require(newValues.size == size) { "Wetness array size must match the field" }
        for (index in 0 until size) {
            values[index] = newValues[index].coerceIn(0f, 1f)
        }
    }

    private fun index(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height) { "Wetness sample out of bounds" }
        return y * width + x
    }
}

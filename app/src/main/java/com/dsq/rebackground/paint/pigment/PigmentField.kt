package com.dsq.rebackground.paint.pigment

/** CPU-owned pigment field. It is intentionally independent of textures, Bitmap and Canvas. */
class PigmentField(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Pigment field dimensions must be positive" }
    }

    private val size = width * height
    internal val red = FloatArray(size)
    internal val green = FloatArray(size)
    internal val blue = FloatArray(size)

    fun clear() {
        red.fill(0f)
        green.fill(0f)
        blue.fill(0f)
    }

    fun colorAt(x: Int, y: Int): PigmentColor {
        val index = index(x, y)
        return PigmentColor(red[index], green[index], blue[index])
    }

    fun add(x: Int, y: Int, red: Float, green: Float, blue: Float, amount: Float) {
        require(amount.isFinite() && amount >= 0f) { "Pigment amount must be finite and non-negative" }
        val index = index(x, y)
        this.red[index] = (this.red[index] + red * amount).coerceIn(0f, 1f)
        this.green[index] = (this.green[index] + green * amount).coerceIn(0f, 1f)
        this.blue[index] = (this.blue[index] + blue * amount).coerceIn(0f, 1f)
    }

    fun scale(factor: Float) {
        require(factor.isFinite() && factor >= 0f) { "Pigment scale factor must be finite and non-negative" }
        for (index in 0 until size) {
            red[index] = (red[index] * factor).coerceIn(0f, 1f)
            green[index] = (green[index] * factor).coerceIn(0f, 1f)
            blue[index] = (blue[index] * factor).coerceIn(0f, 1f)
        }
    }

    internal fun setChannels(red: FloatArray, green: FloatArray, blue: FloatArray) {
        require(red.size == size && green.size == size && blue.size == size) {
            "Pigment channel sizes must match the field"
        }
        for (index in 0 until size) {
            this.red[index] = red[index].coerceIn(0f, 1f)
            this.green[index] = green[index].coerceIn(0f, 1f)
            this.blue[index] = blue[index].coerceIn(0f, 1f)
        }
    }

    private fun index(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height) { "Pigment sample out of bounds" }
        return y * width + x
    }
}

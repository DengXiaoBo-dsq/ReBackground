package com.dsq.rebackground.paint.paper

import android.graphics.Bitmap
import android.graphics.Color

/** CPU generator for the repeatable, neutral paper texture sampled by Layer A. */
object PaperTextureGenerator {
    const val SIZE = 512

    fun createBitmap(paper: PaperDefinition, size: Int = SIZE): Bitmap {
        require(size > 0)
        val field = PaperHeightField(paper.seed)
        val pixels = IntArray(size * size)
        val material = paper.material
        for (y in 0 until size) for (x in 0 until size) {
            // The texture spans a stable paper-space tile; the secondary octave is
            // phase-shifted to expose fibre detail without introducing colour.
            val px = x.toFloat() * 0.75f
            val py = y.toFloat() * 0.75f
            val coarse = field.sample(px, py, material.grainScale)
            val fibre = field.sample(px * 2.9f + 71f, py * 2.9f + 29f, material.grainScale * .45f)
            val valley = (1f - coarse).coerceIn(0f, 1f)
            val fibreValley = (1f - fibre).coerceIn(0f, 1f)
            val relief = material.heightAmplitude * (.58f * valley + .42f * fibreValley * material.fiberDensity)
            // Layer A is evaluated at normal viewing distance, so its neutral
            // reflectance needs enough contrast to survive display quantisation.
            val darkness = (.16f + .78f * material.roughness) * relief
            val value = (1f - darkness).coerceIn(.28f, 1f)
            val gray = (value * 255f).toInt().coerceIn(0, 255)
            pixels[y * size + x] = Color.rgb(gray, gray, gray)
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}

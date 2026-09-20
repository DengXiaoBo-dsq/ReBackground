package com.dsq.rebackground.paint.rendering

/** Pixel allocation requested by a renderer. Paint geometry remains in document space. */
data class RenderTarget(val width: Int, val height: Int, val format: Format = Format.RGBA8) {
enum class Format { RGBA8, RGBA16F }

    init { require(width > 0 && height > 0) { "Render targets require positive dimensions" } }
}

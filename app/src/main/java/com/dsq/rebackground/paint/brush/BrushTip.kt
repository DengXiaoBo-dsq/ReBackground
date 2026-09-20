package com.dsq.rebackground.paint.brush

/** Geometry source for a brush stamp. It contains no colour, pigment, or wetness state. */
sealed interface BrushTip {
    data object Round : BrushTip

    data class Ellipse(override val aspectRatio: Float) : BrushTip {
        init { require(aspectRatio.isFinite() && aspectRatio > 0f) }
    }

    /** A future renderer resolves this stable key to a texture; the model does not load Bitmaps. */
    data class Texture(val resourceKey: String, override val aspectRatio: Float = 1f) : BrushTip {
        init {
            require(resourceKey.isNotBlank())
            require(aspectRatio.isFinite() && aspectRatio > 0f)
        }
    }

    val aspectRatio: Float
        get() = when (this) {
            Round -> 1f
            is Ellipse -> aspectRatio
            is Texture -> aspectRatio
        }
}

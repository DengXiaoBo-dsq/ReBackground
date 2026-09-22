package com.dsq.rebackground.paint.brush

import kotlin.math.floor

/** Deterministic normalized response curve. Inputs and outputs are always clamped to [0, 1]. */
sealed interface DynamicsCurve {
    fun evaluate(input: Float): Float

    data object Linear : DynamicsCurve {
        override fun evaluate(input: Float): Float = input.normalized()
    }

    /** Cubic Bezier timing curve with monotonic x control points. */
    data class Bezier(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
    ) : DynamicsCurve {
        init {
            require(x1.isFinite() && x1 in 0f..1f)
            require(x2.isFinite() && x2 in 0f..1f)
            require(x1 <= x2) { "Bezier x control points must be monotonic" }
            require(y1.isFinite() && y1 in 0f..1f)
            require(y2.isFinite() && y2 in 0f..1f)
        }

        override fun evaluate(input: Float): Float {
            val x = input.normalized()
            if (x == 0f || x == 1f) return x
            var low = 0.0
            var high = 1.0
            repeat(24) {
                val t = (low + high) * 0.5
                if (cubic(t, x1.toDouble(), x2.toDouble()) < x) low = t else high = t
            }
            return cubic((low + high) * 0.5, y1.toDouble(), y2.toDouble()).toFloat().normalized()
        }

        private fun cubic(t: Double, a: Double, b: Double): Double {
            val oneMinus = 1.0 - t
            return 3.0 * oneMinus * oneMinus * t * a + 3.0 * oneMinus * t * t * b + t * t * t
        }
    }

    /** More response at low input. */
    data object Soft : DynamicsCurve {
        override fun evaluate(input: Float): Float {
            val x = input.normalized()
            return 1f - (1f - x) * (1f - x)
        }
    }

    /** More control at low input. */
    data object Hard : DynamicsCurve {
        override fun evaluate(input: Float): Float = input.normalized().let { it * it }
    }

    data object Inverse : DynamicsCurve {
        override fun evaluate(input: Float): Float = 1f - input.normalized()
    }

    data class Stepped(val steps: Int) : DynamicsCurve {
        init { require(steps >= 2) }
        override fun evaluate(input: Float): Float {
            val x = input.normalized()
            return floor(x * steps).coerceAtMost((steps - 1).toFloat()) / (steps - 1).toFloat()
        }
    }
}

private fun Float.normalized(): Float = if (isFinite()) coerceIn(0f, 1f) else 0f

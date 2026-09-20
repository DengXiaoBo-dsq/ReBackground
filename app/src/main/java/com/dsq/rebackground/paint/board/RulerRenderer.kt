package com.dsq.rebackground.paint.board

import android.graphics.Canvas
import android.graphics.Paint

/**
 * 刻度尺渲染器。绘制四周的刻度尺。
 * 每 10 个像素一个小刻度，每 5 个小刻度一个中刻度，每 10 个小刻度一个大刻度。
 */
class RulerRenderer {

    companion object {
        const val TICK_SPACING_PX = 10f
        const val MID_TICK_EVERY = 5
        const val LARGE_TICK_EVERY = 10
    }

    /**
     * 绘制四周刻度尺。
     */
    fun drawAllSides(
        canvas: Canvas,
        width: Int,
        height: Int,
        thickness: Float,
        bgPaint: Paint,
        linePaint: Paint
    ) {
        // 四边背景
        canvas.drawRect(0f, 0f, width.toFloat(), thickness, bgPaint)
        canvas.drawRect(0f, height - thickness, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawRect(0f, 0f, thickness, height.toFloat(), bgPaint)
        canvas.drawRect(width - thickness, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 四边刻度
        drawHorizontalTicks(canvas, width, height, thickness, top = true, linePaint)
        drawHorizontalTicks(canvas, width, height, thickness, top = false, linePaint)
        drawVerticalTicks(canvas, width, height, thickness, left = true, linePaint)
        drawVerticalTicks(canvas, width, height, thickness, left = false, linePaint)
    }

    private fun drawHorizontalTicks(
        canvas: Canvas,
        width: Int,
        height: Int,
        thickness: Float,
        top: Boolean,
        paint: Paint
    ) {
        val smallLen = thickness * 0.25f
        val midLen = thickness * 0.45f
        val largeLen = thickness * 0.75f

        var x = 0f
        var index = 0
        while (x <= width) {
            val len = when {
                index % LARGE_TICK_EVERY == 0 -> largeLen
                index % MID_TICK_EVERY == 0 -> midLen
                else -> smallLen
            }
            if (top) {
                canvas.drawLine(x, thickness, x, thickness - len, paint)
            } else {
                canvas.drawLine(x, height - thickness, x, height - thickness + len, paint)
            }
            x += TICK_SPACING_PX
            index++
        }
    }

    private fun drawVerticalTicks(
        canvas: Canvas,
        width: Int,
        height: Int,
        thickness: Float,
        left: Boolean,
        paint: Paint
    ) {
        val smallLen = thickness * 0.25f
        val midLen = thickness * 0.45f
        val largeLen = thickness * 0.75f

        var y = 0f
        var index = 0
        while (y <= height) {
            val len = when {
                index % LARGE_TICK_EVERY == 0 -> largeLen
                index % MID_TICK_EVERY == 0 -> midLen
                else -> smallLen
            }
            if (left) {
                canvas.drawLine(thickness, y, thickness - len, y, paint)
            } else {
                canvas.drawLine(width - thickness, y, width - thickness + len, y, paint)
            }
            y += TICK_SPACING_PX
            index++
        }
    }
}
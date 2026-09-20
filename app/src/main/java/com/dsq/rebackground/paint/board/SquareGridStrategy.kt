package com.dsq.rebackground.paint.board

import android.graphics.Canvas
import android.graphics.Paint

/**
 * 方形网格实现。每 gridSizePx 像素画一条横线和竖线。
 */
class SquareGridStrategy : GridStrategy {
    override fun drawGrid(canvas: Canvas, width: Int, height: Int, gridSizePx: Float, paint: Paint) {
        if (gridSizePx <= 0f) return

        // 竖线
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            x += gridSizePx
        }

        // 横线
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += gridSizePx
        }
    }
}
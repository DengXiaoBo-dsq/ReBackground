
package com.dsq.rebackground.paint.board

import android.graphics.Canvas
import android.graphics.Paint

/**
 * 网格绘制策略接口。
 * 未来可扩展：菱形网格、圆形网格等。
 */
interface GridStrategy {
    fun drawGrid(canvas: Canvas, width: Int, height: Int, gridSizePx: Float, paint: Paint)
}
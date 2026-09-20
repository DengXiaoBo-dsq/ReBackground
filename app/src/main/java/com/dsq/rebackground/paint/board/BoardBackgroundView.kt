package com.dsq.rebackground.paint.board

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View

class BoardBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var gridSizePx: Float = 40f
        set(value) { field = value; invalidate() }

    var rulerThicknessPx: Float = 60f
        set(value) { field = value; invalidate() }

    var showGrid: Boolean = true
        set(value) { field = value; invalidate() }

    var showRuler: Boolean = true
        set(value) { field = value; invalidate() }

    private var gridStrategy: GridStrategy = SquareGridStrategy()

    // [MOD 2026-09-19 PR-4 v2] 高对比度配色，确保可见
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#0F1419")   // 深蓝黑
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#4A5568")   // 中灰蓝，对比明显
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    private val rulerBgPaint = Paint().apply {
        color = Color.parseColor("#000000")   // 纯黑
        style = Paint.Style.FILL
    }

    private val rulerLinePaint = Paint().apply {
        color = Color.parseColor("#FFFFFF")   // 纯白刻度线
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = false
    }

    private val rulerRenderer = RulerRenderer()

    // [MOD 2026-09-19 PR-4 v2] 诊断计数器
    private var drawCount = 0

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height

        // [MOD 2026-09-19 PR-4 v2] 每次重绘打一次日志（前 5 次）
        if (drawCount < 5) {
            Log.d("BOARD", "onDraw w=$w h=$h gridSize=$gridSizePx rulerThickness=$rulerThicknessPx")
            drawCount++
        }

        if (w <= 0 || h <= 0) return

        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        if (showGrid) {
            gridStrategy.drawGrid(canvas, w, h, gridSizePx, gridPaint)
        }

        if (showRuler) {
            rulerRenderer.drawAllSides(canvas, w, h, rulerThicknessPx, rulerBgPaint, rulerLinePaint)
        }
    }

    fun setGridStrategy(strategy: GridStrategy) {
        gridStrategy = strategy
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d("BOARD", "onSizeChanged $oldw x $oldh → $w x $h")
    }
}
package com.dsq.rebackground.paint.material

import com.dsq.rebackground.paint.brush.BrushDefinition
import com.dsq.rebackground.paint.stroke.Stroke

/**
 * 湿画材质引擎。
 *
 * G0: 只定义接口。
 * G6: 接入 WaterSolver / PigmentAdvection / Diffusion / Absorption / Drying。
 *
 * 架构边界：
 * - Brush 只负责生成 MaterialDeposit。
 * - Wet 不直接修改 BrushSource。
 * - Wet 需要可测试的不变量（质量守恒 / 非负 / NaN=0 / Inf=0）。
 *
 * 参数类型已确认：
 * - Stroke 位于 com.dsq.rebackground.paint.stroke.Stroke
 * - 构造为 Stroke(points: List<StrokePoint>)
 */
interface WetMaterialEngine {
    fun beginStroke(stroke: Stroke, definition: BrushDefinition)
    fun deposit(deposit: MaterialDeposit)
    fun step(dt: Float)
    fun endStroke(strokeId: Long)
    fun resolve()
}
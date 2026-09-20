package com.dsq.rebackground.paint.material

import com.dsq.rebackground.paint.brush.BrushDefinition
import com.dsq.rebackground.paint.stroke.Stroke

/**
 * 干画材质引擎。
 *
 * G0: 空骨架。与 WetMaterialEngine 签名同构，便于上层统一调用。
 * G5: 接入 BrushLoad / BristleField / PaperTooth。
 *
 * 约束：
 * - 现在不引用 G5 才有的类型（BrushLoad / BristleField / PaperTooth）。
 * - 不写任何实现体。
 */
interface DryMaterialEngine {
    fun beginStroke(stroke: Stroke, definition: BrushDefinition)
    fun deposit(deposit: MaterialDeposit)
    fun step(dt: Float)
    fun endStroke(strokeId: Long)
    fun resolve()
}
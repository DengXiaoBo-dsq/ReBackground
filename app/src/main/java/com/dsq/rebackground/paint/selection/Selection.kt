package com.dsq.rebackground.paint.selection

import com.dsq.rebackground.paint.math.Vec2

/**
 * 选区（PR-2 预留接口，暂不实现）
 *
 * 设计意图：
 * - 选区是文档/图层级的概念，不是笔刷属性
 * - 未来用于：自由变换、局部绘制、局部滤镜
 * - 现在只定义数据结构和空操作，不影响现有绘制流程
 */
interface Selection {
    val id: String
    val isEmpty: Boolean
    /** 判断文档坐标点是否在选区内 */
    fun contains(x: Float, y: Float): Boolean
    /** 选区变换（平移/缩放/旋转） */
    fun transform(matrix: FloatArray): Selection
}

object NoSelection : Selection {
    override val id: String = "no-selection"
    override val isEmpty: Boolean = true
    override fun contains(x: Float, y: Float): Boolean = true  // 空选区 = 全选
    override fun transform(matrix: FloatArray): Selection = this
}

class RectangleSelection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) : Selection {
    override val id: String = "rect-${left}-${top}-${right}-${bottom}"
    override val isEmpty: Boolean = false
    override fun contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom
    override fun transform(matrix: FloatArray): Selection = this  // TODO
}
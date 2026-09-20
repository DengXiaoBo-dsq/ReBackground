package com.dsq.rebackground.paint.material

enum class SimulationState { ACTIVE, QUIESCENT, FROZEN }

/**
 * Wet Simulation 的时间调度器。
 *
 * G0: 只定义接口。
 * G6: 实现 event-driven + fixed-step + quiet-state convergence。
 *
 * 语义：
 * - ACTIVE:     用户正在绘画
 * - QUIESCENT:  用户抬笔，继续收敛直到阈值
 * - FROZEN:     湿状态低于阈值，停止模拟，保留 BoundPigment
 * - Rewet 触发 FROZEN → ACTIVE
 *
 * 约束：
 * - 时间推进必须使用 fixedDt，不用原始 frameDelta。
 * - 必须有 maxSubstepsPerFrame 限制，防止卡顿后补帧爆炸。
 */
interface SimulationScheduler {
    fun notifyInput()
    fun update(dt: Float): SimulationState
}
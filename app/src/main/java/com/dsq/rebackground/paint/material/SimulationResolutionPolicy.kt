package com.dsq.rebackground.paint.material

import com.dsq.rebackground.paint.performance.PerformanceTier

enum class SimulationResolution { FULL, HALF, QUARTER, ADAPTIVE }

/**
 * 决定 Wet Simulation 的工作分辨率。
 *
 * G0: 只定义接口。
 * G6: 由真实 benchmark 决定具体策略，不在此处拍板。
 *
 * 约束：
 * - 使用 canvasWidth/Height + activeWidth/Height + PerformanceTier。
 * - 暂不引入 Size / Rect；未来若引入，可替换参数签名，语义不变。
 * - 复用 PerformanceTier（LOW / MEDIUM / HIGH）。
 *   若未来 QualityTier 与 PerformanceTier 语义分离，再独立枚举。
 */
interface SimulationResolutionPolicy {
    fun choose(
        canvasWidth: Int,
        canvasHeight: Int,
        activeWidth: Int,
        activeHeight: Int,
        tier: PerformanceTier
    ): SimulationResolution
}
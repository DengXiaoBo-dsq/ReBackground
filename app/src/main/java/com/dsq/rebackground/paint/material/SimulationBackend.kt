package com.dsq.rebackground.paint.material

import com.dsq.rebackground.paint.core.PaintCapabilities

/**
 * 模拟后端抽象。
 *
 * G0: 只定义接口。
 * G6: GLES30FragmentBackend 实现（Ping-Pong FBO + Semi-Lagrangian）。
 * G8+: GLES31ComputeBackend 可选。
 *
 * 约束：
 * - 上层 MaterialEngine 不应感知 OpenGL / FBO / GLSL。
 * - 坐标约定：simulation space，由 Backend 内部映射到 document space。
 * - 不引入 Size / Rect，使用基本类型，避免 G6 前出现多余依赖。
 */
interface SimulationBackend {
    fun initialize(capabilities: PaintCapabilities)
    fun inject(deposit: MaterialDeposit)
    fun step(dt: Float)
    fun resolve()
    fun collectMetrics(): SimulationMetrics
}
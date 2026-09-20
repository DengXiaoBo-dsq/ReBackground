# DECISIONS

## D-01 GL 版本
- 决定：代码按 ES 3.0 写，设备实际运行 ES 3.2
- 理由：只用 GLES30.* API，向后兼容；G8 若上 Compute Shader 再做 capability 检测
- 未来触发条件：G8 引入 GLES31/32 API

## D-02 DryMaterialEngine 骨架
- 决定：G0 加入空骨架
- 理由：与 WetMaterialEngine 对称；一次性建好，避免 G5 前改包结构
- 约束：只写 interface 签名 + 注释，不引用 G5 类型
- 未来触发条件：G5

## D-03 P1 问题 G0 处理
- 决定：只记录，不修复
- 理由：G0 是基线冻结阶段，任何修复都会改变基线
- 归属：纹理方向 → G3；铅笔浓度 → G2
- 未来触发条件：对应 Gate 开始

## D-04 T-LINE-001
- 决定：记录为 P3，不主动排查
- 理由：已加双保险（ACTION_DOWN 清空 + MAX_STROKE_JUMP=200f）
- 若复现：独立 PR，不混入任何 Gate
- 未来触发条件：复现

## D-05 Mixbox 许可
- 决定：G0 只记录，不动代码
- 现状：CC BY-NC 4.0；当前项目非商业（免费 App）
- 未来触发条件：商业化决策时重新审查或替换

## D-06 Gate 路线
- 决定：Gate 路线取代 PR-0~PR-27
- 理由：以正确性门控制，不以 PR 数量或时间控制
- 旧 PR 处理：作为 Gate 内工作项，不再作为进度指标
- 未来触发条件：无

## D-07 Stroke 类型依赖
- 决定：WetMaterialEngine.beginStroke 使用 stroke: Stroke
- 已确认：Stroke 位于 com.dsq.rebackground.paint.stroke.Stroke
- 未来触发条件：无

## D-08 BrushGenerator 成为 Metrics 载体
- 决定：BrushGenerator 新增 _strokeStampCount / strokeStampCount / beginStroke()
- 理由：按 G0 修正，stampCount 由 BrushGenerator 负责累加
- 影响：replayDocument 前必须调用 generator.beginStroke()，避免 metrics 污染
- 未来触发条件：无

## D-09 类型依赖最小化
- 决定：SimulationResolutionPolicy 使用 Int 参数而非 Size / Rect
- 决定：复用 PerformanceTier，不新增 QualityTier 枚举
- 理由：项目当前无 Size / Rect / QualityTier，避免引入多余类型
- 未来触发条件：G6 时若语义分离再独立

## D-10 MaterialDeposit 加入 G0
- 决定：新增 paint/material/MaterialDeposit.kt
- 理由：deposit() / inject() 需要统一参数类型
- 未来触发条件：无
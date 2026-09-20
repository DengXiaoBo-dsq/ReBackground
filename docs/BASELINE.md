# BASELINE

## 环境
- Git commit: （待填）
- Build version: （待填）
- Device: Huawei / Mali-G77
- Android version: （待填）
- GL runtime: OpenGL ES 3.2 v1.r34p0-...
- GL API: 代码按 ES 3.0（GLES30.*），setEGLContextClientVersion(3)
- Canvas: 1080 × 1865
- Brush library count: （待填）

## T1-T7
（每条 JSON，含 mae / maxError / coverage / nanCount / infCount）
- T1 单笔红 (255, 0, 0)
- T2 单笔绿 (176, 229, 93)
- T3 红蓝紫 (113, 1, 105)
- T4 越描越深
- T5 无印记
- T6 不转弯
- T7 生命周期

## T-LINE-001
- 状态：观察中
- 已知：历史偶发 3 次
- 双保险：ACTION_DOWN 清空 generatorStates + MAX_STROKE_JUMP = 200f
- unexpectedSegmentCount 目标：0
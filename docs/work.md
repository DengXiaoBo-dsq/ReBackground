# ReBackground Professional Paint Engine

## 工程实现、调试、量化验收与 AI 执行技术规范

**文档版本：** v3.0
**目标：** Android 专业级 GPU 数字绘画内核
**GPU API：** OpenGL ES 3.0
**核心语言：** Kotlin
**目标绘画模型：** Brush Kernel + Dry Material + Wet Material + Pigment + Paper
**设计目标：** 专业级移动端数字绘画体验
**执行对象：** AI 编程助手

---

# 0. 本文档的最高优先级

本文档不是普通的开发建议。

它是：

> **ReBackground 绘画引擎的工程实施协议。**

任何实施助手必须遵守：

```text
设计文档
   ↓
本技术规范
   ↓
代码
   ↓
自动化验证
   ↓
日志
   ↓
量化验收
```

而不是：

```text
助手想到一个办法
        ↓
修改代码
        ↓
肉眼看起来差不多
        ↓
继续下一步
```

后者禁止。

---

# 1. 为什么必须采用这种工程模式

顶级绘画软件的复杂性并不主要来自“代码量”。

真正困难的是多个连续系统同时工作：

```text
输入
 ↓
Stroke
 ↓
Brush Sampling
 ↓
Brush Dynamics
 ↓
Material
 ↓
Paper
 ↓
Pigment
 ↓
Simulation
 ↓
Color
 ↓
Composite
 ↓
Display
```

其中任意一个环节发生微小错误，都可能导致最终：

```text
纹理不对
方向不对
笔触断裂
颜色不对
水彩不自然
重放不一致
性能下降
```

因此：

> **必须先建立可测量的中间状态，再建立最终视觉效果。**

---

# 2. 外部成熟软件给我们的工程启示

这份设计并不是凭空创造“笔刷系统”。

目前公开资料已经能看到成熟绘画软件在概念上的共同点。

Procreate 将 Brush 拆成 Shape、Grain、Stroke Path、Dynamics、Wet Mix 等部分，并明确说明笔刷是通过沿路径重复放置 shape，同时让 grain 在其中工作。它还区分 Grain 的 Moving / Texturized 行为，以及 Wet Mix 中的 Dilution、Charge 等概念。

Krita 的笔刷系统同样把 Brush Tip、Opacity、Flow、Texture、Sensors 等作为不同机制，并提供独立的纹理行为。

因此我们的工程结构也必须遵循：

```text
Brush Source
    ≠
Stroke
    ≠
Dynamics
    ≠
Material
    ≠
Pigment
    ≠
Paper
```

---

# 3. 第一条最高原则：弱助手不能“自由发挥”

之后交给其他助手的任务，必须采用：

```text
任务契约
+
允许修改文件
+
禁止修改文件
+
输入输出契约
+
量化验收
+
日志要求
```

例如不能说：

> “把 PNG 笔刷做得更真实。”

应该说：

```text
任务：
实现 ImageBrushSource。

允许修改：
BrushSource.kt
ImageBrushSource.kt
BrushKernel.kt
brush_sample.frag

禁止修改：
PaintEngineController.kt
Mixbox
pigment_add.frag
present.vert
HistoryManager

输入：
512×512 RGBA PNG。

输出：
BrushSample。

验收：
1. 1:1 stamp RGB MAE <= 1.5/255
2. Alpha MAE <= 1/255
3. SSIM >= 0.995
4. shader NaN count = 0
5. GPU pass <= 0.5ms
6. 原 T1-T7 全部不回归。

失败：
不得进入下一 PR。
```

这样能力较弱的助手仍然可以工作。

---

# 4. 第二条最高原则：所有模块都有不变量

每一个核心模块必须写出：

```text
Input
Invariant
Output
```

例如：

## Brush Kernel

```text
Input:
StrokeFrame + BrushDefinition

Invariant:
UV 不越界到错误 source
scale > 0
coverage ∈ [0,1]
opacity ∈ [0,1]
flow ∈ [0,1]
所有 float 为 finite

Output:
BrushSample
```

---

# 5. 第三条最高原则：代码不允许跳级

工程状态必须严格：

```text
PASS
 ↓
下一阶段
```

不能：

```text
FAIL
 ↓
“应该没问题”
 ↓
继续开发水彩
```

尤其：

```text
Brush WYSIWYG FAIL
```

时：

> 禁止开发 Wet Simulation。

因为否则以后无法判断：

```text
最终水彩不好看
```

到底来自：

```text
Brush
Water
Pigment
Paper
```

---

# 6. 第四条最高原则：保留旧管线作为基准

目前项目已经有：

```text
Mixbox
RGBA16F
FBO
Stroke
BrushGenerator
GLPaintRenderer
```

这些不应该一次性删除。

建立：

```text
RenderBackendLegacy
RenderBackendV2
```

或者通过：

```text
BrushEngineMode
```

控制：

```text
LEGACY
V2
SHADOW
```

---

# 7. Shadow Render 是整个重构过程中最重要的安全机制

新 Brush Kernel 加入后：

```text
Input Stroke
       │
       ├───────────────┐
       ▼               ▼
Legacy Pipeline     New Pipeline
       │               │
       ▼               ▼
Old FBO             New FBO
       │               │
       └──────┬────────┘
              ▼
          Diff Pass
```

Diff Pass 输出：

```text
MAE
RMSE
MaxError
SSIM
CoverageError
```

只有新系统稳定以后才能关闭 Legacy。

---

# 8. 当前项目从哪里切入

根据目前项目说明：

```text
Phase 1 颜料流动
Phase 2 Mixbox
```

已经基本完成。

但是：

```text
Phase 3 水痕
Phase 4 飞白
```

尚未开始。

因此**现在绝对不应该直接进入 Phase 3。**

正确入口是：

```text
Current Project
        ↓
PR-0 基线冻结
        ↓
PR-1 可观测性
        ↓
PR-2 Stroke Frame
        ↓
PR-3 Brush Source
        ↓
PR-4 WYSIWYG
        ↓
PR-5 Shape / Grain
        ↓
PR-6 Texture Phase
        ↓
PR-7 Dry Material
        ↓
PR-8 Paper
        ↓
PR-9 Pigment
        ↓
PR-10 Wet Engine
        ↓
PR-11 Drying
        ↓
PR-12 Advanced Watercolor
```

---

# 9. PR-0：建立当前版本基线

这是开始重构之前唯一必须做的事情。

禁止添加新视觉功能。

只记录现在的状态。

---

## 9.1 建立 Baseline ID

例如：

```text
PAINT_BASELINE_2026_09_20
```

保存：

```text
Git commit
Build version
Device model
Android version
GPU renderer
Canvas size
GL version
Brush parameters
```

日志：

```json
{
  "event": "baseline",
  "commit": "xxxxxxxx",
  "device": "Huawei ...",
  "glVersion": "OpenGL ES 3.0",
  "canvasWidth": 1080,
  "canvasHeight": 1865
}
```

Android 官方明确支持 OpenGL ES 3.0，并建议根据设备实际能力创建并检查对应 EGL/OpenGL ES context。

---

# 10. 当前项目必须先重测原 T1-T7

现有：

```text
T1 红
T2 绿
T3 红蓝紫
T4 Flow
T5 无印记
T6 不转弯
T7 生命周期
```

全部重新记录。

不能只记录：

```text
PASS
```

必须：

```json
{
  "test": "T3_RED_BLUE_MIX",
  "pass": true,
  "mae": 0.0041,
  "maxError": 0.019,
  "coverage": 1.000,
  "nanCount": 0
}
```

---

# 11. 第二个基线：长直线问题

文档中已有：

> 偶发“画布内长直线”。

这个问题现在暂时没有复现。

因此建立：

```text
T-LINE-001
```

自动随机生成：

```text
1000
5000
10000
```

组 Stroke。

监控：

```text
unexpectedSegmentCount
unexpectedSegmentLength
offStrokeCoverage
```

目标：

```text
unexpectedSegmentCount = 0
```

如果发现：

```text
>0
```

必须标记：

```text
P0 REGRESSION
```

不能继续 Brush 重构。

---

# 12. PR-1：Observable Paint Engine

第一项实际代码工作不是画笔。

而是：

> **让引擎自己告诉我们发生了什么。**

---

# 13. Logging 系统

统一：

```kotlin
PaintMetrics
```

例如：

```kotlin
data class PaintMetrics(
    val frameId: Long,
    val strokeId: Long,

    val inputSamples: Int,
    val generatedStamps: Int,

    val activeTiles: Int,

    val brushMs: Float,
    val simulationMs: Float,
    val compositeMs: Float,
    val frameMs: Float,

    val gpuMemoryMb: Float,

    val nanCount: Int,
    val infCount: Int,

    val maxVelocity: Float,
    val wetMass: Double,
    val pigmentMass: Double
)
```

---

# 14. 日志分级

```text
ERROR
WARN
INFO
METRIC
TRACE
```

生产模式：

```text
ERROR
WARN
METRIC
```

开发模式：

```text
INFO
METRIC
```

深度调试：

```text
TRACE
```

---

# 15. 每一帧至少记录

```json
{
  "event": "frame_metric",
  "frameId": 1123,
  "frameMs": 12.7,
  "gpuMs": 9.3,
  "brushMs": 1.1,
  "simulationMs": 2.6,
  "compositeMs": 1.4,
  "activeTiles": 16,
  "stampCount": 8,
  "nanCount": 0,
  "infCount": 0
}
```

---

# 16. 为什么必须记录 NaN / Infinity

Shader 中一旦出现：

```text
NaN
Inf
```

可能产生：

```text
随机颜色
随机传播
FBO 污染
整个画面扩散
```

而肉眼看到的可能只是：

> “这里偶尔有一个奇怪点。”

因此：

```text
nanCount != 0
```

直接：

```text
FAIL
```

---

# 17. GPU Debug Probe

建立：

```text
DebugProbe
```

可以读取：

```text
min
max
mean
sum
nonZero
nan
inf
```

针对：

```text
BrushCoverage
Wetness
Velocity
Pigment
FixedPigment
```

---

# 18. 不能每帧读取整个 GPU FBO

因为：

```text
glReadPixels(full canvas)
```

会造成 GPU/CPU synchronization。

调试时采用：

```text
Region Probe
```

例如：

```text
64×64
128×128
```

或者：

```text
bounding box
```

生产关闭。

---

# 19. PR-2：Stroke Framework

当前：

```text
PointerSample
 ↓
BrushGenerator
```

必须升级为：

```text
PointerSample
 ↓
StrokeResampler
 ↓
StrokePoint
 ↓
StrokeFrame
 ↓
BrushKernel
```

---

# 20. StrokeFrame 定义

```kotlin
data class StrokeFrame(
    val tangent: Vec2,
    val normal: Vec2,

    val angle: Float,
    val unwrappedAngle: Float,

    val arcLength: Float,
    val speed: Float,
    val curvature: Float
)
```

---

# 21. Tangent 算法

推荐第一版本：

```text
三点 centered difference
```

例如：

```text
T = normalize(P[i+1] - P[i-1])
```

起点：

```text
T = normalize(P1 - P0)
```

终点：

```text
T = normalize(Pn - Pn-1)
```

---

# 22. 低速情况下不能直接使用当前差分

如果：

```text
|P[i+1] - P[i]|
≈ 0
```

则方向噪声巨大。

使用：

```text
lastStableTangent
```

而不是：

```text
normalize(vec2(0,0))
```

---

# 23. Angle Unwrap

伪代码：

```text
delta = wrapToPi(currentAngle - previousAngle)

unwrappedAngle =
    previousUnwrappedAngle + delta
```

其中：

```text
wrapToPi(x)
=
while x > π:
    x -= 2π

while x < -π:
    x += 2π
```

---

# 24. StrokeFrame 验收

标准测试：

```text
水平
垂直
45°
圆
S
90° corner
180° U-turn
```

量化：

```text
stable path tangent error <= 3°
```

定义：

```text
error =
acos(dot(computedTangent, referenceTangent))
```

---

# 25. Angle Jump 指标

不能简单检查：

```text
currentAngle - previousAngle
```

必须使用 unwrap 后的：

```text
angleDelta
```

要求：

```text
正常连续路径：
P99(|angleDelta|) <= 15°
```

对于故意急转弯：

```text
允许更大。
```

因此测试需要区分：

```text
smooth path
corner path
```

---

# 26. PR-3：Brush Source

建立：

```text
BrushSource
```

接口：

```kotlin
interface BrushSource {
    val width: Int
    val height: Int

    fun sampleMode(): BrushSampleMode
}
```

实现：

```text
ImageBrushSource
MaskBrushSource
```

---

# 27. BrushSample

统一输出：

```kotlin
data class BrushSample(
    val colorR: Float,
    val colorG: Float,
    val colorB: Float,

    val coverage: Float,

    val height: Float = 0f,
    val flowModifier: Float = 1f
)
```

注意：

```text
coverage
```

不是：

```text
opacity
```

也不是：

```text
flow
```

---

# 28. Image Brush 采样规则

```text
PNG RGBA
 ↓
Sample
 ↓
Source RGBA
 ↓
Coverage = A
RGB = source RGB
```

默认情况下：

```text
userColor
```

不能覆盖：

```text
ImageBrush RGB
```

否则会违背 WYSIWYG。

---

# 29. Mask Brush

```text
PNG A
 ↓
Coverage

userColor
 ↓
RGB
```

公式：

```text
sample.rgb = brushColor
sample.coverage = textureAlpha
```

---

# 30. Brush Sampling Shader 原型

下面是**伪代码级** GLSL，不是最终文件：

```glsl
#version 300 es
precision highp float;

uniform sampler2D uBrushTexture;

uniform float uRotation;
uniform float uScale;
uniform vec2  uPivot;

uniform vec4 uTint;

uniform int uSourceMode;
// 0 = Image
// 1 = Mask

in vec2 vLocalUV;

layout(location = 0) out vec4 outSample;

mat2 rotation(float a)
{
    float c = cos(a);
    float s = sin(a);

    return mat2(
        c, -s,
        s,  c
    );
}

void main()
{
    vec2 p = vLocalUV - uPivot;

    // 注意：
    // 最终正负号必须通过方向标定测试确定。
    // 不能让助手凭感觉改成 R(theta) 或 R(-theta)。
    p = rotation(-uRotation) * p;

    p /= max(uScale, 0.0001);

    vec2 uv = p + uPivot;

    if (uv.x < 0.0 || uv.x > 1.0 ||
        uv.y < 0.0 || uv.y > 1.0)
    {
        outSample = vec4(0.0);
        return;
    }

    vec4 src = texture(uBrushTexture, uv);

    if (uSourceMode == 0)
    {
        // Image Brush
        outSample = vec4(src.rgb, src.a);
    }
    else
    {
        // Mask Brush
        float coverage = src.a;

        // 如果设计规定使用 luminance，
        // 必须明确写成 luminance(src.rgb)。
        outSample = vec4(
            uTint.rgb,
            coverage
        );
    }
}
```

---

# 31. 为什么这里不能直接写成：

```glsl
src.rgb * src.a
```

因为：

```text
source RGB
```

是否已经是：

```text
premultiplied alpha
```

必须由 Texture Pipeline 明确规定。

推荐全系统统一：

```text
Brush Source Storage
=
straight alpha

GPU Working Color
=
premultiplied alpha
```

或者反过来。

但是：

> **全项目只能选择一种，不能每个 shader 自己决定。**

---

# 32. Brush Alpha 验收

建立标准 PNG：

```text
BrushTest_A.png
```

包含：

```text
0
0.25
0.5
0.75
1
```

五级 Alpha。

GPU 输出与 CPU reference sampler 比较。

指标：

```text
Alpha MAE <= 1 / 255
Alpha MaxError <= 2 / 255
```

---

# 33. RGB WYSIWYG 验收

标准：

```text
512×512
RGBA PNG
```

在：

```text
scale=1
rotation=0
pressure=1
opacity=1
flow=1
```

下进行单 stamp。

推荐：

```text
RGB MAE <= 1.5 / 255
RGB RMSE <= 3 / 255
SSIM >= 0.995
```

这些是**本项目内部工程目标，不是行业官方标准**。

如果设备/驱动造成 1 LSB 级差异，可以通过 reference sampler + GPU 实测校准。

---

# 34. 为什么需要 CPU Reference Renderer

因为：

```text
GPU 错了
```

时，不能拿：

```text
GPU 输出
```

自己和自己比较。

必须有：

```text
Reference
```

例如 Kotlin/JVM 或离线 Python：

```text
PNG
 ↓
Reference UV transform
 ↓
Reference Bilinear Sample
 ↓
Reference Alpha
```

然后：

```text
Reference
      vs
GPU
```

比较。

---

# 35. 图像比较工具

最基本：

```text
MAE
RMSE
Max Error
```

进一步：

```text
PSNR
SSIM
ΔE00
```

其中：

```text
MAE
```

用于整体误差；

```text
MaxError
```

发现异常像素；

```text
SSIM
```

检测结构变化；

```text
ΔE00
```

用于颜色差异。

---

# 36. PR-4：Aspect Ratio Fidelity

PNG：

```text
1024×256
```

Brush Size：

```text
100 px
```

如果尺寸定义为：

```text
height = 100
```

则：

```text
width = 400
```

必须保持：

```text
aspect = sourceWidth / sourceHeight
```

---

# 37. Aspect Ratio 验收

使用：

```text
1024×256
256×1024
1000×333
333×1000
```

测量实际非透明 bounding box：

```text
measuredAspect
```

要求：

```text
abs(measuredAspect - sourceAspect)
/
sourceAspect
<= 0.005
```

即：

```text
误差 ≤ 0.5%
```

---

# 38. PR-5：Shape / Grain

正式建立：

```text
ShapeSource
GrainSource
```

Brush：

```text
Shape
+
Grain
```

这与成熟绘画软件公开的结构思路一致。Procreate 明确将 Shape 作为容器、Grain 作为纹理，并分别处理 Shape 的 stamp 行为与 Grain 的运动行为。

---

# 39. Shape 的职责

Shape：

```text
覆盖区域
边界
Stamp
尺寸
旋转
Scatter
Tilt
```

---

# 40. Grain 的职责

Grain：

```text
内部纹理
Continuous
Moving
Texturized
Scale
Depth
Phase
```

---

# 41. Grain Continuous Mapping

定义：

```text
grainU =
arcLength / grainScale + phase
```

而：

```text
grainV
```

来自：

```text
localNormalDistance
```

于是：

```text
             tangent →
  ┌─────────────────────────┐
  │ A B C D E F G H I J K  │
  └─────────────────────────┘
```

连续沿 Stroke。

---

# 42. Texture Phase 验收

标准：

```text
1000 px straight line
```

记录：

```text
phaseAt0
phaseAt100
phaseAt500
phaseAt1000
```

使用：

```text
expectedPhase =
initialPhase + distance / textureScale
```

要求：

```text
phaseError <= 0.25 texel
```

整个 1000 px 期间：

```text
phase drift <= 0.5 texel
```

---

# 43. Per-Dab Rotation 与 Rake

必须区分：

```text
Dab Rotation
```

和：

```text
Grain Rake
```

不能把：

```text
所有纹理
```

都简单设置为：

```text
rotation = tangentAngle
```

至少支持：

```text
NONE
FIXED
FOLLOW_STROKE
RAKE
CONTINUOUS
```

---

# 44. 转弯测试

路径：

```text
────────┐
        │
        │
        │
```

记录每个 stamp：

```text
angle
arcLength
texturePhase
```

输出：

```csv
index,distance,angle,phase
0,0,0.0,0.0
1,12.5,0.4,0.125
2,25.0,1.1,0.250
...
```

如果：

```text
angle
```

突然：

```text
179 → -179
```

说明失败。

---

# 45. PR-6：Spacing

Procreate 的公开说明也明确指出 spacing 本质上是在路径上重复放置 brush shape； spacing 太大就出现 stamp 间的空隙。

我们的数学定义：

```text
spacingDistance =
brushEffectiveDiameter × spacingRatio
```

建议：

```text
default = 0.15 ~ 0.25
```

---

# 46. Spacing 量化测试

对于：

```text
brushDiameter = D
```

相邻 Stamp：

```text
distance <= 0.35D
```

作为高速连续笔测试目标。

最终更应该检测：

```text
stroke centerline
```

上的 coverage：

```text
minimumCoverageAlongPath >= 0.95
```

而不是只检查 Stamp 数量。

---

# 47. PR-7：Brush Dynamics

统一：

```text
Input
 ↓
Sensor
 ↓
Response Curve
 ↓
Parameter
```

例如：

```text
pressure
 ↓
Bezier Curve
 ↓
Size
```

---

# 48. 不允许：

```kotlin
size = baseSize * pressure
```

作为唯一通用方案。

因为不同笔刷需要：

```text
linear
soft
hard
inverse
stepped
```

所以：

```kotlin
interface DynamicsCurve {
    fun evaluate(x: Float): Float
}
```

---

# 49. Pressure 验收

输入：

```text
0.0
0.1
...
1.0
```

对于 Linear Curve：

```text
output
```

应该：

```text
monotonic
```

用线性回归：

```text
R² >= 0.99
```

并要求：

```text
monotonicityViolations = 0
```

非线性曲线不使用 R² 作为主要标准，而比较：

```text
referenceCurve
vs
GPU/Runtime curve
```

---

# 50. Speed Dynamics

速度：

```text
v = distance / deltaTime
```

必须做：

```text
clamp
+
smoothing
```

不能直接：

```text
speed = distance / deltaTime
```

因为输入时间戳和采样间隔会抖动。

---

# 51. Tilt Dynamics

统一：

```text
tiltX
tiltY
```

转换：

```text
tiltMagnitude
azimuth
```

公式：

```text
magnitude =
sqrt(tiltX² + tiltY²)
```

方向：

```text
atan2(tiltY, tiltX)
```

建立：

```text
TiltSensor
```

不让 BrushGenerator 自己解释。

---

# 52. PR-8：Dry Material Engine

现在正式进入干画。

架构：

```text
BrushKernel
      ↓
DryMaterialEngine
      ↓
DryDeposit
```

---

# 53. Dry Material 不做成一个 Shader

至少拆成：

```text
DryBrushLoad
BristleField
PaperResponse
DryDeposit
```

---

# 54. Dry Brush Load

状态：

```text
load ∈ [0,1]
```

初始：

```text
load = charge
```

随着弧长：

```text
load -= depletionRate × distance
```

最低：

```text
max(load, 0)
```

---

# 55. Dry Brush Load 伪代码

```text
load = initialCharge

for each sample:
    load -=
        distance *
        depletionRate *
        speedResponse

    load = clamp(load, 0, 1)

    deposit =
        textureCoverage *
        pressureResponse *
        load
```

---

# 56. Dry Brush 连续重复测试

画：

```text
1000 px
```

记录：

```text
depositMass(s)
```

要求总体：

```text
monotonic non-increasing
```

对于没有 recharge 的 Dry Brush：

```text
load[i+1] <= load[i]
```

违反：

```text
FAIL
```

---

# 57. Dry Bristle Field

第一代不要模拟几百根真实粒子。

使用：

```text
Bristle Field
```

数据：

```text
offset
direction
length
width
load
jitter
```

Shader 通过：

```text
Bristle Texture / Procedural pattern
```

得到：

```text
coverage
```

---

# 58. Dry Bristle 的核心不是“毛”

而是：

> **Coverage 被大量细长、方向一致、载荷不同的子结构共同决定。**

这会决定：

```text
飞白
拖痕
分叉
断裂
```

---

# 59. Paper Height Field

建立：

```text
H(x,y)
```

表示纸面相对高度。

例如：

```text
凸起 → H 高
凹陷 → H 低
```

---

# 60. Dry Paper Response

可以先使用：

```text
paperFactor =
smoothstep(
    thresholdLow,
    thresholdHigh,
    H
)
```

然后：

```text
coverageFinal =
coverageBrush *
paperFactor
```

但要注意：

> 第一版只是视觉模型。

以后可以升级成：

```text
contact + pressure + bristle
```

共同决定 Coverage。

---

# 61. Dry Paper 验收

同一个 Brush：

```text
Paper A
Paper B
```

必须产生统计上明显不同的 Coverage。

指标：

```text
coverageVariance(A)
vs
coverageVariance(B)
```

要求：

```text
ratio >= 1.20
```

对于专门设计的高低 Tooth 测试纸。

这不是现实纸张的物理标准，而是验证：

> Paper 参数确实进入了笔刷计算。

---

# 62. PR-9：Pigment State

现在进入真正的颜料层。

不要继续把所有信息塞进：

```text
RGBA
```

至少逻辑上区分：

```text
Wetness
MobilePigment
FixedPigment
Velocity
```

---

# 63. Mobile Pigment

表示：

> 还能随着水运动的颜料。

---

# 64. Fixed Pigment

表示：

> 已经沉积到纸上的颜料。

这类“水中移动颜料”和“纸面沉积颜料”的分离，是经典数字绘画/水彩模型的重要思想。Xu 等人的通用颜料模型就是从 pigment-water solution 与 brush/paper interaction 建模。

---

# 65. Pigment Data

逻辑状态：

```text
W = Water
M = Mobile Pigment
F = Fixed Pigment
U,V = velocity
A = absorbency
H = paper height
```

---

# 66. Mixbox

当前 Mixbox 继续保留。

Mixbox 官方提供：

```text
RGB → latent
latent → RGB
```

同时支持 multi-color latent mixing；其 Shader 使用 LUT + GLSL。

因此可以采用：

```text
Brush Pigment
      ↓
Mixbox latent
      ↓
mobile/fixed accumulation
      ↓
latent → display RGB
```

---

# 67. 重要许可提醒

当前官方 Mixbox 仓库注明：

```text
CC BY-NC 4.0
```

并说明商业许可需要联系作者。

因此：

> 如果 ReBackground 将来进入商业发行，Mixbox 许可证必须单独重新审查。

这是工程发布前的硬性检查项。

---

# 68. PR-10：Wet Material Engine

湿画不能：

```text
Dry Brush
+
Blur
```

这是禁止方案。

必须：

```text
Water
+
Pigment
+
Velocity
+
Diffusion
+
Absorption
+
Drying
```

经典水彩研究已经把水、颜料、纸张和流动作为交互系统；Curtis 等人的 Computer-Generated Watercolor 工作也明确研究了 edge darkening 等由水分迁移导致的现象。

---

# 69. Wet Simulation 状态

第一代：

```text
WaterField          RG16F
VelocityField       RG16F
MobilePigment       RGBA16F
FixedPigment        RGBA16F
Wetness             R16F / RG16F
Paper               RGBA8 / RG16F
```

不一定所有设备都完整使用全部状态。

---

# 70. 为什么采用 RG16F / RGBA16F

你们当前方案已经选择 RGBA16F。

这个方向可以继续。

但是必须：

```text
startup
 ↓
query format
 ↓
create FBO
 ↓
glCheckFramebufferStatus
```

绝对不能认为：

> “Shader 能编译 = FBO 就一定可用。”

---

# 71. OpenGL ES Capability Probe

启动时输出：

```json
{
  "glVersion": "...",
  "glslVersion": "...",
  "maxTextureSize": 4096,
  "maxCombinedTextureUnits": 16,
  "rgba16fRenderable": true,
  "rg16fRenderable": true
}
```

Android 官方说明 OpenGL ES 3.0 在 Android 4.3/API 18 以上可用，但具体设备仍取决于厂商实现，因此运行时 capability probing 必须存在。

---

# 72. Wet Solver 第一版本

为了兼容当前 ES 3.0：

```text
Fragment Shader
+
Ping-Pong FBO
+
Semi-Lagrangian Advection
```

不要一开始强依赖 Compute Shader。

因为 OpenGL ES 3.0 与 3.1 的 API 能力不同，ES 3.1 才提供更现代的 Compute Shader 路线。Android 官方也将 GLES31 单独列为 3.1 API。

---

# 73. Semi-Lagrangian 基础算法

给定当前 UV：

```text
backUV =
uv - velocity * dt
```

然后：

```text
newField(uv) =
oldField(backUV)
```

---

# 74. Fragment Shader 伪代码

```glsl
#version 300 es
precision highp float;

uniform sampler2D uOldField;
uniform sampler2D uVelocity;

uniform vec2 uInvResolution;
uniform float uDt;
uniform float uVelocityScale;

in vec2 vUV;

layout(location = 0) out vec4 outField;

void main()
{
    vec2 velocity =
        texture(uVelocity, vUV).xy;

    vec2 backUV =
        vUV -
        velocity *
        uDt *
        uVelocityScale *
        uInvResolution;

    backUV = clamp(
        backUV,
        vec2(0.0),
        vec2(1.0)
    );

    vec4 value =
        texture(uOldField, backUV);

    outField = value;
}
```

---

# 75. 但是这个版本不是最终版

Semi-Lagrangian 的优点：

```text
稳定
```

缺点：

```text
数值耗散
```

所以第二代可以考虑：

```text
BFECC
MacCormack
Conservative Advection
```

但原则是：

> 第一版先保证稳定，再提升精度。

---

# 76. Wet Solver 第二层：Diffusion

基本：

```text
Pnew =
Padvected +
D * laplacian(P)
```

二维 Laplacian：

```text
L =
Pleft
+ Pright
+ Ptop
+ Pbottom
- 4Pcenter
```

---

# 77. Shader 伪代码

```glsl
vec4 c = texture(uField, uv);
vec4 l = texture(uField, uv + vec2(-dx, 0.0));
vec4 r = texture(uField, uv + vec2( dx, 0.0));
vec4 t = texture(uField, uv + vec2(0.0,  dx));
vec4 b = texture(uField, uv + vec2(0.0, -dx));

vec4 laplacian =
    l + r + t + b - 4.0 * c;

vec4 result =
    c + uDiffusion * uDt * laplacian;
```

---

# 78. Diffusion 稳定性检查

对于纯扩散测试：

```text
总质量
```

必须近似保持。

定义：

```text
Mass =
Σ(field × pixelArea)
```

第一代目标：

```text
relativeMassError <= 2%
```

高级实现：

```text
<= 0.5%
```

在固定分辨率、封闭边界、无吸收条件测试下。

---

# 79. Wet Mass Monitoring

每次测试输出：

```json
{
  "initialMass": 1.000000,
  "currentMass": 0.987531,
  "relativeError": 0.012469
}
```

如果：

```text
relativeError > threshold
```

则：

```text
FAIL
```

---

# 80. PR-11：Paper Absorption

纸张吸收：

```text
A(x,y)
```

定义：

```text
absorptionRate
```

每个模拟步：

```text
waterLost =
water * absorptionRate * dt
```

然后：

```text
water -= waterLost
```

同时部分：

```text
mobilePigment
 →
fixedPigment
```

---

# 81. 吸收模型伪代码

```text
absorb =
    W
    * paperAbsorption
    * pigmentAbsorption
    * dt

W -= absorb

M -= absorb * pigmentRetention

F += absorb * pigmentRetention
```

必须保证：

```text
W >= 0
M >= 0
F >= 0
```

---

# 82. Non-Negative Invariant

所有物理场：

```text
water >= 0
pigment >= 0
wetness >= 0
```

任何：

```text
NaN
Inf
negative beyond epsilon
```

都记录：

```text
simulationInvariantViolation
```

---

# 83. PR-12：Wet-on-Dry

初始：

```text
paperWetness = 0
```

Injection：

```text
water += brushWater
pigment += brushPigment
```

随后：

```text
absorption
+
advection
+
diffusion
```

---

# 84. Wet-on-Wet

画第二笔前：

```text
existing wetness > threshold
```

第二笔：

```text
water
+
pigment
```

进入原有：

```text
velocity field
+
mobile pigment
```

---

# 85. Wetness Mask

定义：

```text
wetness =
clamp(water / waterCapacity, 0, 1)
```

用途：

```text
edgeDarkening
pigmentMobility
diffusion
paperAbsorption
```

---

# 86. Wetness 验收

无持续输入时：

```text
wetness(t+1) <= wetness(t)
```

允许极小数值噪声：

```text
wetnessIncrease <= 1e-4
```

否则：

```text
FAIL
```

---

# 87. PR-13：Edge Darkening

不要：

```text
edge = sobel(color)
color *= edge
```

作为核心物理模型。

可以用于视觉辅助，但核心应该来自：

```text
wetness
+
boundary distance
+
outward transport
+
pigment deposition
```

经典水彩模型对 edge darkening 的解释就是蒸发导致边缘附近流动，将颜料带向边界。

---

# 88. Edge Distance Field

建立：

```text
distanceToWetBoundary
```

或者使用：

```text
wetness gradient
```

简单版：

```text
edgeStrength =
length(gradient(wetness))
```

高级版：

```text
edgeStrength =
distanceFieldBasedResponse
```

---

# 89. Edge Deposition

伪代码：

```text
edgePressure =
edgeFunction(wetnessGradient)

outwardFlow =
edgePressure * wetness

mobilePigment
    →
edgeTransport

fixedPigment += depositedAmount
```

---

# 90. Edge Darkening 验收

标准样本：

```text
100×100 wet square
```

中心区域：

```text
inner 60×60
```

边缘环：

```text
10 px
```

定义：

```text
edgeMass
coreMass
```

目标：

```text
edgeMass / coreMass
>= 1.10
```

在标准 Drying Profile 结束时。

更高级的目标：

```text
1.10 ~ 1.60
```

防止变成黑色描边。

---

# 91. PR-14：Drying

状态：

```text
WET
DAMP
TACKY
DRY
```

可以用连续参数：

```text
dryness ∈ [0,1]
```

而不是离散跳变。

---

# 92. Drying 模型

第一代：

```text
dryness += evaporationRate * dt
```

高级：

```text
evaporationRate =
baseRate
*
temperatureFactor
*
airflowFactor
*
paperFactor
*
surfaceAreaFactor
```

但第一版不要加入太多现实参数。

优先：

```text
stable
controllable
artist-friendly
```

---

# 93. Drying 验收

固定笔迹：

```text
t=0
t=1
t=2
t=5
t=10
```

记录：

```text
wetMass
mobilePigment
fixedPigment
edgeMass
```

要求：

```text
wetMass ↓
mobilePigment ↓
fixedPigment ↑
```

最终：

```text
mobilePigment → near zero
```

---

# 94. PR-15：Backrun

Backrun 是高级水彩特征。

测试：

```text
先画湿色块
 ↓
边缘部分干燥
 ↓
再注入大量水
```

检测：

```text
localWetness
+
gradient
+
existingPigmentBoundary
```

触发：

```text
secondaryFlow
```

---

# 95. Backrun 的第一版

不要模拟复杂表面张力。

可以：

```text
waterInjection
*
existingEdgePigment
*
dryness
```

得到：

```text
backrunImpulse
```

再进入：

```text
VelocityField
```

---

# 96. Backrun 自动验收

标准：

```text
100×100 pigment region
```

重新加水：

```text
center = 0.8 water
```

观察：

```text
pigmentBoundaryArea
```

要求：

```text
Area_after > Area_before
```

并：

```text
expansionRatio
```

落在预设区间。

第一版可以要求：

```text
1.05 <= expansionRatio <= 1.50
```

避免：

```text
没有效果
```

或者：

```text
全画布爆炸
```

---

# 97. PR-16：Granulation

第一阶段不要使用：

```text
random noise
```

直接贴到最终颜色。

应该：

```text
paperHeight
+
pigmentParticlePreference
```

---

# 98. Granulation 伪代码

```text
particleAffinity =
    function(paperHeight)

granulation =
    pigmentMass *
    particleAffinity
```

最终视觉：

```text
pigment
    ↓
paper pores
    ↓
particle accumulation
```

---

# 99. Granulation 验收

使用相同：

```text
Brush
Pigment
Water
```

只改变：

```text
Paper A
Paper B
```

比较：

```text
localVariance
spatialFrequency
```

要求：

```text
Paper A != Paper B
```

且差异可通过参数解释。

---

# 100. PR-17：Color Pipeline

整个项目必须统一：

```text
Source Color
Working Color
Pigment Color
Display Color
```

---

# 101. 推荐的颜色路径

```text
PNG sRGB
   ↓
Decode
   ↓
Linear Working RGB
   ↓
Pigment / Mixbox
   ↓
Linear Composite
   ↓
sRGB Output
```

不能出现：

```text
某个 Shader 用 sRGB
下一个 Shader 假设 linear
```

Mixbox 官方代码本身同时提供 sRGB/linear float 路径，因此项目必须明确选择并统一。

---

# 102. Color Conversion Test

建立：

```text
0
0.003
0.018
0.25
0.5
0.75
1.0
```

做：

```text
sRGB
→
Linear
→
sRGB
```

要求：

```text
MAE <= 1 LSB
```

---

# 103. PR-18：RenderGraph

现在 GLPaintRenderer 中如果所有事情都在一个巨大函数里：

```text
drawBrush
flush
diffuse
composite
present
```

最终会越来越难维护。

必须形成：

```text
RenderPass
```

---

# 104. 推荐 Pass

```text
BrushDepositPass

WaterInjectionPass

VelocityPass

PigmentAdvectionPass

PigmentDiffusionPass

AbsorptionPass

DryingPass

PigmentResolvePass

PaperCompositePass

FinalCompositePass

PresentPass
```

---

# 105. 每个 Pass 必须有：

```text
Input FBO
Output FBO
Uniforms
State
Metrics
DebugName
```

例如：

```kotlin
interface RenderPass {
    val name: String

    fun execute(
        context: RenderContext,
        frame: FrameState
    )

    fun collectMetrics(
        metrics: PaintMetrics
    )
}
```

---

# 106. 禁止 Pass 自己偷偷改变全局状态

禁止：

```text
Pass A
glEnable(...)
glBlendFunc(...)

Pass B
不知道 A 做过什么
```

必须：

```text
GLStateCache
```

统一管理。

---

# 107. PR-19：Active Tile System

这是性能达到高级水平的关键。

当前：

```text
1080×1865
≈ 2.0M pixels
```

如果每帧所有 Wet Pass 全画布执行：

```text
Water
Velocity
Advection
Diffusion
Absorption
Drying
```

带来的带宽压力非常大。

因此：

```text
Canvas
 ↓
Active Tile
```

---

# 108. Tile 建议

第一版：

```text
64×64
```

高级：

```text
64×64 / 128×128
```

根据 GPU 调整。

---

# 109. Tile 生命周期

```text
INACTIVE
 ↓
DIRTY
 ↓
ACTIVE
 ↓
EVAPORATING
 ↓
QUIET
 ↓
FROZEN
```

只有：

```text
ACTIVE
```

才进行完整 Water Simulation。

---

# 110. Dirty Region

Brush Injection：

```text
boundingBox
```

扩大：

```text
simulationPadding
```

例如：

```text
brushBounds
+
3 × diffusionRadius
```

对应 Tile：

```text
markActive(tile)
```

---

# 111. Active Tile 验收

日志：

```json
{
  "canvasPixels": 2014200,
  "activeTiles": 18,
  "activePixelRatio": 0.036
}
```

对于局部水彩：

```text
activePixelRatio
```

应该明显小于：

```text
1.0
```

---

# 112. 性能指标

不以“感觉流畅”为标准。

至少记录：

```text
FPS
Frame Time
GPU Time
CPU Time
Brush Time
Simulation Time
Composite Time
Memory
Active Tiles
Stamp Count
```

---

# 113. 目标性能

以下是：

> **本项目内部工程目标，而不是行业认证标准。**

参考设备：

```text
1080×1865
Mali-G77
```

---

## Dry

目标：

```text
60 FPS
P95 frame time <= 12 ms
P99 <= 16.67 ms
```

---

## Wet 局部模拟

目标：

```text
60 FPS
```

当：

```text
active area <= 25%
```

---

## Wet 全画面极端测试

目标：

```text
>= 30 FPS
```

不能出现：

```text
<15 FPS
```

持续数秒。

---

# 114. 输入延迟

需要记录：

```text
MotionEvent timestamp
```

到：

```text
first visible stamp timestamp
```

定义：

```text
inputToPixelLatency
```

内部目标：

```text
P95 <= 35 ms
P99 <= 50 ms
```

---

# 115. GPU Memory Budget

例如：

```text
RenderTargets
+
Textures
+
Buffers
+
Simulation
```

总工作集建议设：

```text
<= 160 MB
```

作为 1080×1865 High Profile 的初始工程上限。

如果超过：

```text
QualityController
```

自动降低：

```text
wet simulation resolution
```

或者：

```text
active tile count
```

---

# 116. 为什么不把所有状态全部保持 Full Resolution

因为：

```text
Visual Canvas
```

和：

```text
Physical Simulation
```

没有必要完全一样的分辨率。

建议：

```text
Display
=
Full Resolution

Dry Brush
=
Full Resolution

Wet Simulation
=
Adaptive 1/2 ~ 1x

Frozen Pigment
=
Full Resolution Resolve
```

---

# 117. PR-20：Replay / Undo

Wet simulation 是时间依赖系统。

所以：

```text
Stroke List
```

还不够。

需要：

```text
Checkpoint
+
Stroke Log
```

---

# 118. Replay 数据

必须保存：

```text
brushId
brushRevision
sourceHash
materialRevision

color
opacity
flow

size
rotation

pressure
tilt
speed

timestamp
points

randomSeed

simulationVersion
```

---

# 119. Deterministic Random

禁止：

```text
Math.random()
```

必须：

```text
seed =
hash(strokeId,
     pointIndex,
     bristleIndex)
```

然后：

```text
random(seed)
```

这样：

```text
同一个 Stroke
=
同一个结果
```

---

# 120. Replay 验收

同一设备：

```text
Original
vs
Replay
```

要求：

```text
RGB MAE <= 1.0 / 255
MaxError <= 3 / 255
SSIM >= 0.999
```

对于涉及 GPU 浮点差异的 Simulation，可以适当放宽。

关键是：

```text
same renderer
same device
same quality tier
```

必须高度一致。

---

# 121. 跨设备 Replay

不能要求：

```text
Pixel Exact
```

因为不同 GPU 可能存在：

```text
floating point differences
```

应比较：

```text
SSIM
ΔE00
structural metrics
```

内部目标：

```text
SSIM >= 0.995
mean ΔE00 <= 1.0
```

这同样是工程目标，不是行业标准。

---

# 122. Quantitative Acceptance Matrix

最终建立统一表：

| 模块             | 指标                |                目标 |
| -------------- | ----------------- | ----------------: |
| PNG            | RGB MAE           |          ≤1.5/255 |
| PNG            | Alpha MAE         |            ≤1/255 |
| PNG            | SSIM              |            ≥0.995 |
| Aspect         | 比例误差              |             ≤0.5% |
| Phase          | 漂移                | ≤0.5 texel/1000px |
| Angle          | 稳定路径误差            |               ≤3° |
| Angle          | P99 jump          |              ≤15° |
| Pressure       | Linear R²         |             ≥0.99 |
| Coverage       | centerline min    |             ≥0.95 |
| NaN            | Shader count      |                 0 |
| Inf            | Shader count      |                 0 |
| Water          | Mass drift        |               ≤2% |
| Advanced Water | Mass drift        |             ≤0.5% |
| Wetness        | 无输入增长             |             ≤1e-4 |
| Edge           | edge/core mass    |             ≥1.10 |
| Replay         | Same device SSIM  |            ≥0.999 |
| Replay         | Cross device SSIM |            ≥0.995 |
| Dry FPS        | P95 frame         |             ≤12ms |
| Dry FPS        | P99               |          ≤16.67ms |
| Input          | P95 latency       |             ≤35ms |
| Input          | P99 latency       |             ≤50ms |
| Memory         | High Profile      |            ≤160MB |

注意：

> 上表中的数字是 **ReBackground 的工程验收目标**，不是声称这些数字是 Procreate/Krita 的官方内部标准。成熟软件的内部实现和阈值并未公开，我们只能借鉴其公开能力和建立自己的可测量标准。

---

# 123. “顶级效果”仍然需要人工视觉检查吗？

需要。

但人工检查变成：

```text
最后一层
```

而不是：

```text
唯一一层
```

最终验收：

```text
Quantitative
+
Visual
```

---

# 124. Visual Review 也必须标准化

不能：

> “我觉得水彩很好看。”

而应该准备固定样片：

```text
V01 直线
V02 圆
V03 急转弯
V04 压力
V05 速度
V06 干刷
V07 湿纸
V08 湿碰湿
V09 Edge Darkening
V10 Backrun
V11 Granulation
V12 多色 Mixbox
```

每一版都使用：

```text
完全相同输入
```

截图。

---

# 125. A/B Comparison

每次优化：

```text
Version A
Version B
```

不能：

```text
A 今天画
B 明天手动画
```

必须：

```text
same Stroke Recording
same Brush
same Paper
same Parameters
same Canvas
```

然后 Replay。

---

# 126. Debug Visualization Mode

必须增加调试显示：

```text
DEBUG_NONE

DEBUG_BRUSH_UV

DEBUG_COVERAGE

DEBUG_STROKE_TANGENT

DEBUG_STROKE_ANGLE

DEBUG_TEXTURE_PHASE

DEBUG_WETNESS

DEBUG_VELOCITY

DEBUG_MOBILE_PIGMENT

DEBUG_FIXED_PIGMENT

DEBUG_PAPER_HEIGHT

DEBUG_ACTIVE_TILES

DEBUG_ERROR_HEATMAP
```

---

# 127. UV Debug

画：

```text
U → Red
V → Green
```

例如：

```glsl
outColor = vec4(uv.x, uv.y, 0.0, 1.0);
```

这样一眼发现：

```text
UV 镜像
UV 翻转
UV 跳变
UV 缩放
```

---

# 128. Tangent Debug

输出：

```text
R = tangent.x
G = tangent.y
```

水平：

```text
(1,0)
```

垂直：

```text
(0,1)
```

这样不用肉眼看纹理即可判断：

> StrokeFrame 算对没有。

---

# 129. Wetness Debug

```text
black = 0
white = 1
```

必须看到：

```text
湿区
```

是否真的随着：

```text
Drying
```

逐渐消失。

---

# 130. Velocity Debug

使用：

```text
R = velocity.x
G = velocity.y
```

如果出现：

```text
整块区域异常纯红/纯绿
```

表示 velocity 爆掉。

---

# 131. Error Heatmap

建立：

```text
difference =
abs(reference - actual)
```

然后：

```text
black = 0 error
red = high error
```

这会比直接看两张 PNG 高效很多。

---

# 132. Brush WYSIWYG 的黄金测试

制作：

```text
brush_test_master.png
```

内容故意复杂：

```text
透明边缘
半透明
红色
蓝色
绿色
黑色
白色
渐变
尖角
细线
非正方形
```

单 stamp。

必须通过：

```text
MAE
RMSE
SSIM
Alpha
Aspect
```

五重测试。

---

# 133. Brush Rotation Golden Test

角度：

```text
0
15
30
45
60
90
120
180
270
```

每个输出：

```text
rotation_XXX.png
```

与 CPU reference 比较。

---

# 134. Brush Stroke Golden Test

路径：

```text
horizontal
vertical
circle
spiral
S
corner
zigzag
```

全部固定。

---

# 135. Dry Golden Set

固定：

```text
paperA
paperB
paperC
```

Brush：

```text
drybrush01
pencil01
charcoal01
```

每个输出：

```text
dry_<brush>_<paper>.png
```

---

# 136. Wet Golden Set

至少：

```text
wet_on_dry
wet_on_wet
wash
edge_darkening
backrun
granulation
two_color_mix
three_color_mix
```

每个都有：

```text
t=0
t=1
t=2
t=5
t=10
```

---

# 137. 为什么一定要保存时间序列

因为水彩是动态系统。

最终图片：

```text
t=10
```

看起来正常。

但是：

```text
t=0
t=1
t=2
```

可能已经爆炸。

因此：

> **只测试最终截图是不够的。**

---

# 138. Simulation Stability Test

设置：

```text
极大水量
极大速度
极高 diffusion
极低 absorbency
```

进行：

```text
1000 frames
```

要求：

```text
NaN = 0
Inf = 0
```

并：

```text
maxVelocity < configuredSafetyLimit
```

---

# 139. Stress Test

测试：

```text
连续高速画
多笔同时湿
大面积水彩
快速撤销重做
旋转缩放
后台前台切换
Surface 重建
```

监控：

```text
FBO leak
Texture leak
memory growth
context restore
```

---

# 140. OpenGL Resource Leak Test

每次：

```text
onSurfaceCreated
```

记录：

```text
texturesCreated
framebuffersCreated
programsCreated
buffersCreated
```

每次：

```text
onSurfaceDestroyed
```

记录释放。

重复：

```text
50 次
```

要求：

```text
steady-state resource count
```

不能持续增长。

---

# 141. Context Loss Recovery

Android 的 `GLSurfaceView` 生命周期要求把一次性 GL 初始化放在对应的 surface 生命周期中，因此 GL 资源不能只依赖 Activity 生命周期。

必须测试：

```text
旋转
后台
恢复
Surface 重建
```

之后：

```text
Brush texture
Mixbox LUT
FBO
Shader
```

都必须重新加载。

---

# 142. Shader 错误必须结构化记录

不能：

```text
Shader compile failed
```

然后结束。

必须记录：

```json
{
  "shader": "brush_sample.frag",
  "stage": "fragment",
  "compileSuccess": false,
  "log": "...",
  "sourceHash": "...",
  "version": 3
}
```

---

# 143. 每个 Shader 必须有 Source Hash

例如：

```text
brush_sample.frag
SHA256 = ...
```

这样当用户拿日志回来时，可以确认：

> 当时运行的到底是哪一版 Shader。

---

# 144. 每个 Brush 也必须有 Definition Hash

例如：

```text
brushId = watercolor_01
revision = 7
sourceHash = ...
definitionHash = ...
```

---

# 145. 每一笔 Stroke 都记录

```json
{
  "strokeId": 73,
  "brushId": "watercolor_01",
  "brushRevision": 7,
  "material": "wet",
  "randomSeed": 937512,
  "pointCount": 342,
  "stampCount": 181,
  "length": 1245.3,
  "durationMs": 832
}
```

---

# 146. 这样以后发生问题时可以回答：

不是：

> “这支笔昨天还正常。”

而是：

```text
Brush:
watercolor_01 revision 7

Stroke:
73

Points:
342

Stamps:
181

Phase drift:
0.14 texel

Wet mass:
0.9983

NaN:
0

GPU:
Mali-G77

Shader:
SHA256=...
```

然后才有真正意义上的 Debug。

---

# 147. AI 助手的每个 PR 必须遵守这个模板

以后给弱助手任务时，直接采用：

```text
# PR-N

## 目标
一句话描述。

## 允许修改
文件列表。

## 禁止修改
文件列表。

## 当前输入协议
字段、单位、坐标。

## 当前输出协议
字段、单位、范围。

## 数学算法
明确公式。

## Shader
必须实现什么。

## 日志
必须增加哪些字段。

## Debug Mode
必须能够观察什么。

## 自动验收
具体数字。

## 回归测试
T1...
T2...
T3...

## 完成条件
全部通过。

## 禁止事项
不得：
1. 改 Mixbox。
2. 改坐标系。
3. 改生命周期。
4. 顺便重构其他代码。
```

---

# 148. 弱助手不能同时改多个逻辑层

错误：

```text
一个 PR：
Stroke
Brush
Mixbox
Paper
Wetness
```

正确：

```text
PR-2 Stroke
```

完成。

然后：

```text
PR-3 BrushSource
```

完成。

然后：

```text
PR-4 WYSIWYG
```

完成。

---

# 149. 更重要：每个 PR 最多允许一个“逻辑原因”

例如：

> 修复纹理旋转。

只允许修改：

```text
BrushSampling
```

不能顺便：

```text
改 spacing
改 opacity
改 Mixbox
改坐标
```

否则无法归因。

---

# 150. 每一个 PR 必须产生 Before / After

保存：

```text
before.png
after.png
diff.png
metrics.json
log.txt
```

---

# 151. PR 完成报告

助手必须输出：

```text
PR:
PR-5

修改文件:
...

未修改文件:
...

算法:
...

测试:
T-BRUSH-001 PASS
T-BRUSH-002 PASS
...

指标:
RGB MAE = 0.0031
Alpha MAE = 0.0012
SSIM = 0.9988

回归:
T1 PASS
T2 PASS
...
T7 PASS

结论:
PASS
```

不能只说：

> “已经完成。”

---

# 152. 什么时候允许进入下一阶段？

必须：

```text
ALL REQUIRED METRICS PASS
```

而不是：

```text
大部分 PASS
```

---

# 153. P0 / P1 / P2 缺陷等级

## P0

```text
崩溃
NaN
Inf
数据丢失
坐标错误
长线
FBO corruption
Replay 不可恢复
```

禁止继续。

---

## P1

```text
明显纹理错误
明显水彩不稳定
性能严重下降
Color Pipeline 错误
```

禁止相关阶段继续。

---

## P2

```text
小型视觉偏差
调参问题
UI
```

可以延期。

---

# 154. 最重要的“禁止回退”清单

你们原来的：

```text
ACTION_UP 生命周期
L1-L3
Mixbox t
viewToDocument
present.vert
越界丢弃
multiTouchLocked
Flow / Opacity
```

继续保留。

在此基础上增加：

```text
Brush Source ≠ Mask
Coverage ≠ Flow
Opacity ≠ Coverage
StrokeFrame 是唯一方向来源
Texture Phase 不得 reset
Random 必须 deterministic
Wet simulation 不允许直接修改 Brush Source
Paper 不允许污染 Brush Definition
```

---

# 155. 未来新的“架构真相”

整个项目最终只允许一个方向链：

```text
Pointer
 ↓
Stroke
 ↓
StrokeFrame
 ↓
BrushKernel
 ↓
MaterialDeposit
 ↓
MaterialEngine
 ↓
Pigment
 ↓
Paper
 ↓
Composite
```

反方向禁止。

例如：

```text
Paper
→ 修改 Brush Definition
```

禁止。

```text
Pigment
→ 修改 Brush Source
```

禁止。

---

# 156. Brush Kernel 最终接口

建议：

```kotlin
interface BrushKernel {

    fun beginStroke(
        definition: BrushDefinition,
        initialPoint: StrokePoint
    ): BrushRuntimeState

    fun sample(
        frame: StrokeFrame,
        point: StrokePoint,
        state: BrushRuntimeState
    ): BrushSample

    fun endStroke(
        state: BrushRuntimeState
    )
}
```

---

# 157. Material Engine 最终接口

```kotlin
interface MaterialEngine {

    fun beginStroke(
        stroke: Stroke,
        definition: BrushDefinition
    )

    fun deposit(
        deposit: MaterialDeposit
    )

    fun step(
        dt: Float
    )

    fun endStroke(
        strokeId: Long
    )
}
```

---

# 158. Water Solver

```kotlin
interface WaterSolver {

    fun inject(
        region: Region,
        waterAmount: Float
    )

    fun step(
        dt: Float
    )

    fun collectMetrics(): WaterMetrics
}
```

这样：

```text
WetMaterialEngine
```

不需要知道：

```text
OpenGL
FBO
GLSL
```

---

# 159. GPU Backend

最终：

```text
MaterialEngine
       ↓
RenderGraph
       ↓
OpenGL ES Backend
```

而不是：

```text
MaterialEngine
       ↓
GLES30.glBindTexture()
```

---

# 160. 为什么这样设计

以后如果：

```text
OpenGL ES 3.0
```

成为性能瓶颈，可以增加：

```text
OpenGLES31Backend
```

甚至：

```text
VulkanBackend
```

而：

```text
BrushKernel
Material
Pigment
Paper
Stroke
```

全部不需要重写。

---

# 161. OpenGL ES 3.1 后端

第二阶段可以加入：

```text
Compute Shader
```

用于：

```text
Water
Advection
Diffusion
Tile Simulation
```

但：

```text
Brush Sampling
```

仍然可以保留 Fragment Shader。

因此：

```text
RendererBackend
├── GLES30
└── GLES31
```

---

# 162. 第一代与第二代不要混在一起

第一代目标：

```text
正确
稳定
可测试
```

第二代：

```text
更快
更精确
更高级
```

不能为了追求高级而让：

```text
第一代
```

失去可控性。

---

# 163. 最重要的算法升级路线

## Brush

```text
Direct PNG
↓
Shape/Grain
↓
Continuous Phase
↓
Rake
↓
Pressure/Speed/Tilt
↓
Bristle
```

---

## Dry

```text
Coverage
↓
Paper
↓
Load
↓
Bristle
↓
Fiber
↓
Pigment Deposit
```

---

## Wet

```text
Water
↓
Velocity
↓
Advection
↓
Diffusion
↓
Absorption
↓
Pigment Transport
↓
Deposition
↓
Drying
↓
Edge Darkening
↓
Bloom/Backrun
↓
Granulation
```

---

# 164. 最重要的调试顺序

任何视觉问题必须按照：

```text
1. Input
2. Coordinates
3. StrokeFrame
4. Brush UV
5. Brush Sample
6. Deposit
7. Material
8. Pigment
9. Paper
10. Composite
```

顺序排查。

不能直接改最后的：

```text
Composite Shader
```

---

# 165. 例如用户说：

> “纹理转弯的时候不自然。”

弱助手不能马上改：

```text
frag
```

而必须检查：

```text
Stroke angle log
```

如果：

```text
angle:
0
3
6
9
12
15
```

正确。

然后检查：

```text
phase
```

如果：

```text
0
0.1
0.2
0.3
```

正确。

再检查：

```text
UV
```

如果 UV 跳了：

```text
0.1
0.2
0.8
0.3
```

问题在 Sampling。

---

# 166. 例如用户说：

> “水彩边缘太黑。”

不能马上：

```glsl
edgeBoost *= 0.5
```

必须输出：

```text
water
mobilePigment
fixedPigment
edgeMass
coreMass
```

如果：

```text
edgeMass/coreMass = 4.8
```

才能判断：

> 颜料迁移过强。

如果：

```text
edgeMass/coreMass = 1.1
```

但是显示仍然很黑：

> 问题很可能在 Color/Composite，而不是物理模型。

---

# 167. 例如用户说：

> “水彩扩散不自然。”

必须先看：

```text
velocity magnitude
```

再看：

```text
water field
```

再看：

```text
pigment advection
```

再看：

```text
paper absorption
```

最后才调整：

```text
visual composite
```

---

# 168. 三个核心调试探针

必须建立：

```text
BrushProbe
MaterialProbe
SimulationProbe
```

---

# 169. BrushProbe

输出：

```text
coverageMean
coverageMin
coverageMax

uvMin
uvMax

phase
angle
spacing

sourceRGB
sampleRGB
```

---

# 170. MaterialProbe

输出：

```text
depositMass
load
water
pigment
pressure
speed
```

---

# 171. SimulationProbe

输出：

```text
waterMass
mobilePigmentMass
fixedPigmentMass

velocityMean
velocityMax

wetArea
activeTiles

edgeMass
coreMass
```

---

# 172. 最终 Debug Overlay

屏幕角落显示：

```text
FPS: 59.8
GPU: 10.2ms
SIM: 2.8ms

Brush:
stamps=31
phaseErr=0.08
angleErr=1.2°

Wet:
tiles=14
water=0.183
pigment=0.091
velocity=0.24

Errors:
NaN=0
Inf=0
```

这样用户截图一次就能给助手非常有价值的信息。

---

# 173. “视觉顶级”不能只定义为更多算法

这是整个项目必须警惕的问题。

：

```text
算法越多
≠
效果越好
```

例如：

```text
Noise
+
Blur
+
Edge Darkening
+
Bump
+
Color LUT
```

堆在一起：

可能看起来“复杂”，

但不一定像真实绘画。

因此最终评分应该：

```text
Fidelity
Continuity
Control
Material Coherence
Physical Plausibility
Performance
```

---

# 174. 最终 Brush 等级

## Level A

```text
PNG 真实
```

---

## Level B

```text
PNG
+
Shape
+
Grain
+
Phase
+
Rake
```

---

## Level C

```text
B
+
Pressure
+
Speed
+
Tilt
```

---

## Level D

```text
C
+
Paper
+
Bristle
+
Drying
+
Load
```

---

## Level E

```text
D
+
Water
+
Pigment
+
Diffusion
+
Absorption
+
Drying
+
Backrun
+
Granulation
```

这就是最终目标。

---

# 175. 最终“顶级”并不是一个算法

真正的顶级系统是：

```text
一致性
+
可预测
+
高保真
+
可控制
+
可重放
+
稳定
+
实时
```

因此：

```text
顶级 Brush
```

不是：

```text
最好看的某一笔
```

而是：

> **同一笔在不同时间、不同压力、不同速度、不同缩放、不同设备条件下，都能按照 Brush Definition 的预期稳定地产生结果。**

---

# 176. 最终项目完成定义

当以下全部成立：

```text
[✓] Brush Source 正确
[✓] PNG WYSIWYG
[✓] Shape/Grain
[✓] Texture Phase
[✓] Stroke Frame
[✓] Pressure
[✓] Speed
[✓] Tilt
[✓] Dry Load
[✓] Bristle
[✓] Paper Tooth
[✓] Paper Absorption
[✓] Pigment
[✓] Mixbox
[✓] Water
[✓] Advection
[✓] Diffusion
[✓] Absorption
[✓] Drying
[✓] Edge Darkening
[✓] Backrun
[✓] Granulation
[✓] Replay
[✓] Undo
[✓] Active Tile
[✓] Quality Tier
[✓] Quantitative Tests
[✓] Performance Tests
```

并满足：

```text
No P0
No NaN
No memory leak
No replay corruption
No baseline regression
```

才能宣布：

# Professional Paint Engine v1.0

---

# 177. 实际施工顺序

最终严格按照下面执行：

```text
PR-0
冻结当前版本
        ↓
PR-1
Observable / Metrics
        ↓
PR-2
StrokeFrame
        ↓
PR-3
BrushSource
        ↓
PR-4
PNG WYSIWYG
        ↓
PR-5
Aspect / Alpha / Color
        ↓
PR-6
Shape + Grain
        ↓
PR-7
Texture Phase
        ↓
PR-8
Rake / Rotation
        ↓
PR-9
Dynamics
        ↓
PR-10
Dry Brush Load
        ↓
PR-11
Bristle
        ↓
PR-12
Paper Height
        ↓
PR-13
Pigment Deposit
        ↓
PR-14
Wetness
        ↓
PR-15
Water Solver
        ↓
PR-16
Pigment Advection
        ↓
PR-17
Diffusion
        ↓
PR-18
Absorption
        ↓
PR-19
Drying
        ↓
PR-20
Edge Darkening
        ↓
PR-21
Backrun
        ↓
PR-22
Granulation
        ↓
PR-23
Adaptive Tile
        ↓
PR-24
Replay / Checkpoint
        ↓
PR-25
Performance
        ↓
PR-26
Golden Tests
        ↓
PR-27
Production Hardening
```

---

# 178. 现在项目下一步最正确的动作

根据现有项目状态：

```text
Phase 1 ✓
Phase 2 ✓
Phase 3 未开始
Phase 4 未开始
```

所以现在：

## 不进入 Phase 3。

也不要马上写：

```text
pigment_dry.frag
paper.frag
BristleSimulator
```

正确动作：

```text
当前代码
 ↓
建立 baseline
 ↓
恢复/确认 T1-T7
 ↓
建立 Metrics
 ↓
建立 DebugProbe
 ↓
建立 CPU Reference
 ↓
建立 Golden Image
 ↓
建立 Shadow Rendering
 ↓
开始 StrokeFrame
```

---

# 179. 第一轮只允许完成四件事

如果现在正式开始施工，我建议第一个工作包只做：

```text
1. PaintMetrics
2. DebugProbe
3. Golden Test 基础
4. StrokeFrame
```

不要碰：

```text
Mixbox
Pigment
Water
Paper
```

---

# 180. 第一轮完成后的正确状态

系统应该能够告诉我们：

```text
Stroke:
points = 321
resampled = 294
length = 1352.4

Angle:
meanError = 0.93°
maxError = 4.21°

Spacing:
mean = 0.19D
max = 0.24D

FPS:
59.8

NaN:
0

Inf:
0
```

做到这一点以后，再开始：

```text
BrushKernel
```

---

# 181. 第二轮只做 PNG Fidelity

只做：

```text
ImageBrushSource
BrushTextureSampler
BrushKernel
```

得到：

```text
PNG
→
Brush Sample
```

然后：

```text
Reference
vs
GPU
```

---

# 182. 第二轮完成条件

必须：

```text
MAE <= 1.5/255
SSIM >= 0.995
Alpha MAE <= 1/255
Aspect error <= 0.5%
```

否则：

```text
不进入 Grain
```

---

# 183. 第三轮才进入 Shape / Grain

这样你以后看到：

```text
纹理不好
```

可以明确知道：

```text
BrushSource
还是
Grain
```

---

# 184. 第四轮才进入 Dry Material

这时：

```text
Brush
```

已经稳定。

所以：

```text
干刷效果
```

的问题可以定位到：

```text
Paper
Bristle
Load
```

而不再怀疑：

```text
Brush Sampling
```

---

# 185. 第五轮才进入 Wet

此时：

```text
Brush
+
Dry
+
Paper
+
Pigment
```

都是稳定的。

于是：

```text
Water
```

出现问题，就能明确定位。

---

# 186. 这就是整个路线最重要的思想

不是：

```text
先把所有东西做出来
然后调
```

而是：

```text
构建基础
 ↓
建立测量
 ↓
锁死基础
 ↓
增加一层
 ↓
测量
 ↓
锁死
 ↓
继续
```

这叫：

> **可验证递进式 Engine Development。**

---

# 187. 给弱助手的最终执行规则

以后任何助手在修改代码前必须回答：

```text
1. 我正在修改哪个层？
2. 输入是什么？
3. 输出是什么？
4. 哪些文件允许修改？
5. 哪些文件禁止修改？
6. 数学不变量是什么？
7. 如何观察中间结果？
8. 如何自动测试？
9. 哪些日志必须出现？
10. 什么数字代表 PASS？
11. 什么数字代表 FAIL？
12. 原有 T1-T7 怎么回归？
```

有任何一个问题无法回答：

> **不要修改核心代码。**

---

# 188. 最终架构全景

最终整个 ReBackground：

```text
                         ┌──────────────────────┐
                         │     PaintActivity    │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │   PointerInput       │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │   StrokeResampler    │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │     StrokeFrame      │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │     Brush Kernel     │
                         └──────────┬───────────┘
                                    │
                  ┌─────────────────┼─────────────────┐
                  │                 │                 │
                  ▼                 ▼                 ▼
             Shape/PNG          Grain             Dynamics
                  │                 │                 │
                  └─────────────────┼─────────────────┘
                                    │
                                    ▼
                            Material Deposit
                                    │
                       ┌────────────┴────────────┐
                       │                         │
                       ▼                         ▼
                 Dry Material              Wet Material
                       │                         │
                 ┌─────┼─────┐             ┌─────┼─────┐
                 │     │     │             │     │     │
              Bristle Load Paper         Water Velocity Pigment
                 │     │     │             │     │     │
                 └─────┴─────┘             └─────┴─────┘
                       │                         │
                       └────────────┬────────────┘
                                    ▼
                              Pigment Engine
                                    │
                                  Mixbox
                                    │
                            Optical Composite
                                    │
                              Paper Response
                                    │
                                  Present
```

同时旁边一直存在：

```text
                ┌─────────────────────────┐
                │     Observability       │
                ├─────────────────────────┤
                │ Metrics                 │
                │ Debug Probe             │
                │ Golden Tests             │
                │ Replay                  │
                │ Performance Monitor      │
                │ Error Heatmap            │
                └─────────────────────────┘
```

---

# 189. 最终结论

现在你们已经不应该再把这个项目看成：

> “给现有画笔加纹理、加水彩、加飞白。”

而应该正式把它定义为：

> **一个由 Brush Kernel、Stroke Geometry、Material Engine、Pigment Engine、Paper Engine 和 GPU RenderGraph 组成的专业数字绘画内核。**

而对弱助手最重要的不是让它“理解所有理论”。

真正有效的方法是：

```text
把复杂理论
        ↓
拆成明确接口
        ↓
拆成单一 PR
        ↓
定义不变量
        ↓
定义 Debug Probe
        ↓
定义 Golden Test
        ↓
定义数字阈值
        ↓
通过后才准进入下一阶段
```

这样，能力较弱的助手不需要拥有和设计者一样的整体认知，也能够在一个**严格受控的工程框架**里面正确实施。

最重要的是：

> **以后任何“效果不好”的问题，都必须能够回答“哪一个中间变量首先偏离了预期”。**

如果做不到这一点，说明系统仍然处于“靠经验调 Shader”的阶段；如果能够做到，才真正进入专业绘画引擎的工程阶段。

而你们目前最正确的切入点就是：

```text
冻结现有 Phase 2
        ↓
建立 PR-0 基线
        ↓
建立 Metrics / Probe / Golden Test
        ↓
建立 StrokeFrame
        ↓
建立 Brush Kernel
        ↓
完成 PNG WYSIWYG
```

**在 PNG WYSIWYG、Stroke Frame、统一采样和量化验收体系彻底建立以前，不建议继续推进现在计划书里的“阶段 3 水痕”和“阶段 4 飞白”。**这不是放慢项目，而是在避免后面用大量水彩 Shader 去掩盖一个尚未稳定的 Brush Foundation。

长期目标则非常清晰：

```text
Level 1  可画
      ↓
Level 2  专业 Brush Engine
      ↓
Level 3  专业 Dry Media
      ↓
Level 4  高级 Wet Media
      ↓
Level 5  Professional-class Paint Engine
```

这套实施规范的核心价值，就是把从 **Level 2 → Level 5** 的过程从“艺术调参项目”变成一个可以逐层验证、可以回归、可以量化、可以交给不同助手持续实施的工程项目。

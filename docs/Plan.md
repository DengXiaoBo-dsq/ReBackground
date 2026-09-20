# ReBackground Professional Paint Engine

## 顶级数字绘画内核总体设计与分步实施计划

**目标平台：** Android
**当前渲染后端：** OpenGL ES 3.0
**目标：** 专业级移动端 Brush Engine + 干画材质 + 湿画材质 + 颜料/纸张模拟
**核心原则：** Brush Fidelity First，Material Simulation Second，Performance Always

---

# 一、项目最终目标重新定义

ReBackground 的最终目标不应该是：

> “实现一个可以画画的 OpenGL Canvas。”

而应该是：

> **建立一个能够解释真实画笔、笔刷纹理、压力、速度、倾角、颜料、水分和纸张之间关系的 GPU 绘画内核。**

最终架构需要能够自然地支持：

```text
铅笔
钢笔
马克笔
炭笔
粉笔
毛笔
干刷
纹理笔
印章笔
水彩
湿水彩
湿边水彩
水墨
泼墨
油画/厚涂（后续）
```

而不是为每一种笔刷写一个特殊 Shader。

---

# 二、真正衡量“顶级”的标准

不能简单用：

```text
有没有水彩
有没有纹理
有没有 Mixbox
```

来判断。

应该建立七个维度。

| 维度             | 专业级标准                      |
| -------------- | -------------------------- |
| Brush Fidelity | PNG/Brush Source 与实际笔触高度一致 |
| Stroke Quality | 高速、低速、转弯、抬笔都连续             |
| Dynamics       | 压力、速度、倾角自然响应               |
| Dry Material   | 干性介质有真实纸纹、纤维、颗粒、干湿浓淡       |
| Wet Material   | 水、颜料、流动、扩散、吸收、干燥产生自然变化     |
| Color          | 多颜料混合不是简单 RGB 插值           |
| Performance    | 绘画过程中不能因为复杂模拟明显破坏交互        |

其中最重要的是：

```text
Brush Fidelity
+
Stroke Quality
+
Material Behavior
```

这三个层次必须分开。

---

# 三、顶级引擎应该采用的总架构

最终架构建议：

```text
                 ┌─────────────────────┐
                 │     PaintActivity   │
                 └──────────┬──────────┘
                            │
                            ▼
                 ┌─────────────────────┐
                 │ PointerInputSystem  │
                 └──────────┬──────────┘
                            │
                            ▼
                 ┌─────────────────────┐
                 │ Stroke Builder      │
                 │ Resampler           │
                 │ Stroke Frame        │
                 └──────────┬──────────┘
                            │
                            ▼
                 ┌─────────────────────┐
                 │    Brush Kernel     │
                 └──────────┬──────────┘
                            │
               ┌────────────┼─────────────┐
               │            │             │
               ▼            ▼             ▼
        Brush Source    Dynamics      Material
        Shape/Grain     Pressure      Dry/Wet
        PNG/Mask       Speed/Tilt
               │            │             │
               └────────────┼─────────────┘
                            │
                            ▼
                   Brush Deposit Model
                            │
              ┌─────────────┴─────────────┐
              │                           │
              ▼                           ▼
       Dry Material Engine        Wet Material Engine
              │                           │
       ┌──────┼──────┐             ┌──────┼──────┐
       │      │      │             │      │      │
       ▼      ▼      ▼             ▼      ▼      ▼
     Paper  Fiber  Pigment       Water  Pigment  Flow
     Grain  Bristle Deposit      Film   Transport
       │      │      │             │      │      │
       └──────┴──────┘             └──────┴──────┘
              │                           │
              └─────────────┬─────────────┘
                            ▼
                       Pigment Field
                            │
                            ▼
                    Color / Mix Engine
                            │
                            ▼
                     Optical Composite
                            │
                            ▼
                         Present
```

这个结构是整个项目以后几年扩展的基础。

---

# 四、第一大阶段：建立 Brush Kernel

## 目标

彻底解决：

> “PNG 到底是不是一个真正的 Brush Source？”

当前系统应该从：

```text
BrushStamp
```

升级到：

```text
BrushKernel
```

---

## 1. BrushSource

建立：

```kotlin
interface BrushSource
```

实现：

```text
ImageBrushSource
MaskBrushSource
ShapeBrushSource
GrainBrushSource
CompositeBrushSource
```

---

## ImageBrushSource

这是整个项目最重要的模式。

PNG：

```text
RGBA
```

直接定义：

```text
Visual Source
```

也就是说：

```text
PNG
 ↓
采样
 ↓
Brush Sample
```

而不是：

```text
PNG
 ↓
转成 Alpha
 ↓
重新套颜色
 ↓
重新生成圆形笔尖
```

---

# 五、Brush Source 必须区分三种语义

## A. Image Brush

```text
RGB = 视觉颜色
A   = 覆盖率
```

用于：

```text
特殊纹理
印章
彩色笔刷
真实扫描笔触
```

这是你说的：

> 所见即所得。

---

## B. Mask Brush

```text
A / Luminance
       ↓
Coverage
       ↓
用户指定颜色
```

用于：

```text
铅笔
墨水
普通毛刷
喷枪
```

---

## C. Material Brush

高级模式：

```text
Base Color
Alpha
Height
Roughness
Flow
Direction
```

但这些必须是**显式 Material Maps**。

不能把普通 PNG 的 RGB 自动解释成法线或粗糙度。

---

# 六、第二大阶段：Shape 与 Grain 分离

这是你们必须进行的架构升级。

专业笔刷应该：

```text
Shape
+
Grain
+
Dynamics
+
Material
```

而不是：

```text
一个 PNG
```

Procreate 当前公开的 Brush Studio 就明确将 Shape、Grain、Stroke Path、Rendering、Dynamics 和 Wet Mix 分离；Krita 也把笔尖、纹理、间距、旋转、散布、颜色来源等作为独立笔刷行为。

---

# 七、第三大阶段：Stroke Geometry

这一阶段解决：

> “笔迹本身是不是专业级？”

不能再：

```text
MotionEvent
 ↓
直接 stamp
```

而必须：

```text
MotionEvent
 ↓
Raw Pointer Samples
 ↓
Resample
 ↓
StrokePath
 ↓
StrokeFrame
 ↓
Brush Kernel
```

---

# 八、Stroke Resampling

输入：

```text
x
y
pressure
tilt
timestamp
```

重新按弧长采样。

核心：

```text
distance(Pi, Pi-1)
```

达到：

```text
spacing × brushDiameter
```

才产生新的 Brush Sample。

这样可以避免：

```text
高速 = 空洞
低速 = 堆积
```

---

# 九、Stroke Frame

每一个 Stroke Point 应增加：

```kotlin
data class StrokeFrame(
    val tangentX: Float,
    val tangentY: Float,
    val normalX: Float,
    val normalY: Float,

    val angle: Float,
    val unwrappedAngle: Float,

    val distance: Float,
    val speed: Float,
    val curvature: Float
)
```

真正的笔刷旋转基于：

```text
Tangent
```

而不是简单：

```text
atan2(dy, dx)
```

---

# 十、方向计算

推荐：

```text
3-point tangent
+
look-ahead
+
angle unwrap
+
unit-vector smoothing
```

而不是直接：

```kotlin
angle = atan2(dy, dx)
```

这样：

```text
179°
-179°
```

不会造成：

```text
358°
```

的错误旋转。

---

# 十一、Texture Phase

这个功能是实现高级纹理笔的关键。

不要：

```text
每个 Stamp
    textureUV = 0
```

而应该：

```text
Stroke Distance
       ↓
Texture Phase
```

例如：

```text
ABCDEFGH
```

沿笔迹：

```text
A B C D E F G H I J K L ...
```

自然连续。

因此需要：

```kotlin
texturePhase
distanceAlongStroke
```

---

# 十二、纹理应该存在两个模式

## Stamp Mode

每个 Dab 独立：

```text
Shape
Texture
Rotation
```

适合：

```text
印章
颗粒
星星
叶片
特殊笔尖
```

---

## Continuous Mode

纹理沿 Stroke 连续：

```text
Stroke Distance
        ↓
Continuous Texture
```

适合：

```text
木纹
干刷
铅笔纹
毛笔纹
油画刷痕
粉笔
```

---

## Hybrid Mode

最终默认高级模式：

```text
Shape = Per-Dab
Grain = Continuous
```

这是非常值得采用的设计。

---

# 十三、第四大阶段：真正的 PNG WYSIWYG

这一阶段必须单独验收。

## 1. 1:1 Stamp

条件：

```text
Opacity = 1
Flow = 1
Pressure = 1
Rotation = 0
Scale = 1
```

结果应该：

```text
Canvas ≈ PNG
```

---

## 2. Alpha

必须使用：

```text
Premultiplied Alpha
```

避免：

```text
透明区域黑边
透明区域白边
旋转脏边
```

---

## 3. 色彩

建立明确管线：

```text
PNG sRGB
 ↓
Brush Sampling
 ↓
必要时 Linear
 ↓
Pigment / Blend
 ↓
Linear Composite
 ↓
Display sRGB
```

Mixbox 官方同时提供 sRGB 与 linear 浮点接口，因此你们应该明确统一整个引擎的颜色空间规则，而不是不同 Pass 各自决定。

---

# 十四、纹理采样必须成为独立模块

建立：

```text
BrushTextureSampler
```

负责：

```text
Rotation
Scale
Aspect
Pivot
Filtering
Wrap
Mip
Alpha
Phase
```

这样：

```text
Brush Cursor
Brush Preview
Brush Library Thumbnail
Actual Stroke
```

全部调用同一个采样逻辑。

这是实现真正：

> 所见即所得

的关键。

---

# 十五、第五大阶段：Dry Material Engine

这是“干模式”。

这里我不建议把它实现成：

```text
PNG + Alpha
```

而是：

```text
Brush
+
Paper
+
Dry Deposit
```

---

# 十六、Dry 模式的核心物理模型

定义：

```text
B = Brush Load
C = Coverage
P = Pressure
S = Speed
T = Paper Tooth
F = Fiber Field
```

最终沉积：

```text
Deposit =
BrushSource
× Coverage
× PressureResponse
× Load
× PaperResponse
```

---

# 十七、Brush Load

这是干刷高级效果的核心。

每一笔开始：

```text
Load = 1
```

随着 Stroke 距离：

```text
Load ↓
```

例如：

```text
████████████████
███████████
████████
████
██
```

于是自然形成：

```text
满载
 ↓
逐渐变干
 ↓
飞白
```

类似的“笔刷上颜料会随着拖动逐渐减少、抬笔重新获得 Charge”也是成熟数字绘画系统中的明确设计；Procreate 的 Wet Mix 文档就把 Charge、Dilution、Attack、Drag 等作为独立行为。

---

# 十八、Paper Tooth

纸张不能只是：

```text
一个 Noise
```

应该至少包含：

```text
Paper Height
Paper Absorbency
Paper Roughness
Paper Fiber Orientation
Paper Pore
```

---

# 十九、纸张高度场

```text
H(x,y)
```

作用：

```text
笔刷经过凸起
↓
不能完全覆盖
↓
产生飞白
```

而凹陷区域：

```text
更容易留下颜料
```

于是：

```text
普通数字圆笔
```

变成：

```text
真实纸面上的干刷
```

---

# 二十、Bristle 模型

第一版不要做复杂 CPU 粒子。

推荐：

```text
Virtual Bristles
```

例如：

```text
32
64
128
```

根：

```text
Brush Center
```

方向：

```text
Stroke Tangent
```

每根 Bristle：

```text
Offset
Length
Width
Load
Jitter
```

GPU 根据 Bristle Field 生成 Coverage。

---

# 二十一、Dry Brush 最终效果

第一阶段：

```text
纸纹
+
Load depletion
```

第二阶段：

```text
Bristle
+
Paper Tooth
```

第三阶段：

```text
Pressure
+
Speed
+
Tilt
```

第四阶段：

```text
Color Deposit
+
Pigment Accumulation
```

最终达到：

```text
慢画：
████████████

快速：
███ ██  █  █

转弯：
纤维方向随笔迹改变

大压力：
████████

轻压力：
██ ██  ██
```

---

# 二十二、第六大阶段：Wet Material Engine

真正的湿画和干笔必须彻底分开。

不要：

```text
Dry Brush
+
Blur
=
Watercolor
```

这只能得到：

> 模糊的数字水彩。

真正的湿画至少需要：

```text
Water
+
Mobile Pigment
+
Fixed Pigment
+
Velocity
+
Paper
+
Drying
```

数字水彩研究中已经长期采用水层、移动颜料层和固定颜料层等思路；现代实时系统也通常把水流、颜料平流/扩散、沉积和光学渲染分开处理。

---

# 二十三、Wet Engine 的核心状态

建议：

```text
WaterField W
MobilePigment M
FixedPigment F
Velocity U/V
Wetness D
PaperAbsorption A
PaperHeight H
```

---

# 二十四、为什么必须有 Mobile Pigment 和 Fixed Pigment

如果只有：

```text
Pigment
```

无法表现：

```text
湿颜料
      ↓
流动
      ↓
逐渐沉积
      ↓
固定
```

所以：

```text
Mobile Pigment
```

负责：

```text
流
扩散
混色
```

而：

```text
Fixed Pigment
```

负责：

```text
已经进入纸面的颜料
```

这也是经典数字水彩模型的重要思想。

---

# 二十五、Wet Engine 的每帧流程

建议：

```text
1. Brush Injection
        ↓
2. Water Update
        ↓
3. Velocity Update
        ↓
4. Pigment Advection
        ↓
5. Pigment Diffusion
        ↓
6. Paper Absorption
        ↓
7. Deposition
        ↓
8. Evaporation
        ↓
9. Edge Formation
        ↓
10. Optical Composite
```

---

# 二十六、Water Injection

不同 Brush：

```text
Dry Watercolor
Water = 0.2

Normal Watercolor
Water = 0.6

Wet Wash
Water = 1.0
```

而不是所有水彩都使用同一个水量。

---

# 二十七、Wet-on-Dry

表现：

```text
干纸
+
湿笔
```

过程：

```text
Brush
 ↓
Water
 ↓
Paper absorbs
 ↓
Pigment slows
 ↓
形成笔迹边缘
```

效果：

```text
中心浓
边缘软
```

---

# 二十八、Wet-on-Wet

表现：

```text
湿纸
+
湿笔
```

过程：

```text
新水
 ↓
加入旧水
 ↓
Velocity
 ↓
Pigment Advection
 ↓
Diffusion
```

结果：

```text
████████████
  ╲      ╱
   ╲____╱
```

颜色自己产生自然扩散。

---

# 二十九、Water Flow 算法选择

你们当前是 OpenGL ES 3.0。

OpenGL ES 3.1 才正式提供 Compute Shader，因此不能直接把依赖 Compute Shader 的现代 GPU 流体架构照搬到当前 ES 3.0 后端。

因此我建议：

## 第一代

使用：

```text
Fragment Shader
+
Ping-Pong FBO
+
Semi-Lagrangian Advection
```

优点：

```text
兼容 ES3.0
实现简单
稳定
容易调参数
```

---

## 第二代

在支持 ES 3.1 的设备上提供：

```text
Compute Shader Backend
```

但不要让上层引擎知道。

架构：

```text
WaterSolver
├── GL30FragmentBackend
└── GL31ComputeBackend
```

这样未来可以迁移。

---

# 三十、不要第一版就使用完整 Navier-Stokes

这是非常重要的工程判断。

手机绘画需要的是：

> 艺术可控性

不是：

> 流体力学博士论文。

第一阶段采用：

```text
Shallow-water / simplified velocity
+
Semi-Lagrangian
+
Diffusion
+
Capillary spread
```

比完整 CFD 更适合产品。

---

# 三十一、Velocity Field

建议：

```text
Velocity = Gravity
         + BrushImpulse
         + MoistureGradient
         + SurfaceTension
         + PaperResistance
```

其中：

```text
BrushImpulse
```

让用户拖动颜料。

```text
MoistureGradient
```

让水从湿到干产生流动。

```text
PaperResistance
```

让不同纸张产生不同阻力。

---

# 三十二、Diffusion

基本：

```text
∂P/∂t =
-Div(PV)
+
D∇²P
```

即：

```text
Advection
+
Diffusion
```

其中：

```text
D = Pigment Diffusion
```

而不是简单 Blur。

---

# 三十三、Paper Absorption

必须独立。

建立：

```text
AbsorptionRate(x,y)
```

然后：

```text
dWater/dt = -kWater × Absorption
dPigment/dt = -kPigment × Absorption
```

水：

```text
吸收较快
```

颜料：

```text
吸收较慢
```

这种“溶剂被吸收、较大颜料更多留在纸面”的关系在真实水性绘画模型中是核心现象。

---

# 三十四、Edge Darkening

不能简单：

```text
StrokeEdge
×
Black
```

而应该：

```text
湿度梯度
+
蒸发
+
毛细迁移
+
颜料沉积
```

即：

```text
湿中心
       ↓
水向外围迁移
       ↓
边界浓度增加
       ↓
深色边缘
```

也就是所谓：

```text
Coffee-ring / Edge pooling
```

这是水彩真实感非常关键的一项。

---

# 三十五、Backrun / Bloom

这是最终高级水彩必须有的。

当：

```text
干边
+
突然加入大量水
```

出现：

```text
边缘回冲
```

模型可以：

```text
newWater
+
localWetnessGradient
+
pigmentBoundary
```

触发：

```text
secondary flow
```

然后形成：

```text
花瓣状
云状
边缘扩散
```

研究型数字水彩系统也会针对 backrun、edge darkening、granulation 等现象建模。

---

# 三十六、Granulation

不能：

```text
Noise
×
Color
```

伪造。

应该：

```text
Paper Height
+
Pigment Particle Deposition
```

让颗粒倾向：

```text
纸张凹陷
```

于是：

```text
颜料颗粒
██████
 ██  ██
   ██
```

自然形成粒状纹理。

---

# 三十七、Drying Model

建立：

```text
DryingModel
```

状态：

```text
Wet
 ↓
Damp
 ↓
Tacky
 ↓
Dry
```

定义：

```text
wetness(t)
```

然后：

```text
Evaporation
Absorption
Deposition
Diffusion
```

随时间改变。

这样用户：

```text
画完立即看
```

与：

```text
等 1 秒
等 3 秒
等 10 秒
```

会得到不同结果。

---

# 三十八、第七大阶段：颜色与 Pigment Engine 升级

你们现在使用 Mixbox，这个方向应该保留。

Mixbox 官方本身就是基于 Kubelka–Munk 思路的颜料混合方案，并提供多颜色 latent mixing 接口，而不只是简单 RGB `lerp`。

但是：

## 当前设计还存在一个重要隐患

如果长期：

```text
RGBA16F
RGB = 最终颜色
A   = Flow
```

那么：

```text
红
+
蓝
+
黄
```

混合之后只剩：

```text
最终 RGB
```

会逐渐丢失颜料组成历史。

---

# 三十九、建议未来的 Pigment State

高级模式使用：

```text
PigmentMass
+
Mixbox Latent
```

Mixbox 本身支持多色 latent 混合，因此可以把颜料组成保存在 latent 空间，而不是每一次操作后立即坍缩成 RGB。

但移动设备上不能盲目全画布堆大量 FP16 通道。

所以：

```text
Wet Active Region
      ↓
High Precision Pigment Latent

Dry / Frozen Region
      ↓
Compressed / Resolved Pigment
```

即：

> **只让正在发生物理变化的区域保持高精度。**

---

# 四十、湿画 GPU 数据建议

例如 Active Wet Tile：

```text
Wetness        RG16F
Velocity       RG16F
PigmentLatent0 RGBA16F
PigmentLatent1 RG16F
FixedPigment   RGBA16F
Paper          RGBA8 / RG16F
```

而不是整个 1080×1865 永远完整跑。

---

# 四十一、Active Tile System

这是整个项目性能能否达到专业级的关键。

不要：

```text
每一帧
全画布水彩模拟
```

而是：

```text
Stroke
 ↓
Wet Region
 ↓
Bounding Box
 ↓
Tile Expansion
 ↓
Active Tiles
 ↓
Simulation
```

例如：

```text
整张画布：

████████████████████████

只有这里湿：

      ███████
      ███████
      ███████
```

那么只模拟：

```text
███████
```

而不是整个 Canvas。

---

# 四十二、Wet Tile 生命周期

```text
INACTIVE
   ↓
INJECTED
   ↓
ACTIVE
   ↓
EVAPORATING
   ↓
DAMP
   ↓
DRY
   ↓
FROZEN
```

Frozen 后：

```text
不再进行完整模拟
```

这样性能会比“每帧全画布模拟”高很多。

---

# 四十三、第八大阶段：统一 Brush / Dry / Wet 接口

建立：

```kotlin
interface MaterialEngine
```

实现：

```text
DryMaterialEngine
WetMaterialEngine
```

Brush Kernel 不知道具体是哪一种。

流程：

```text
Brush Kernel
      ↓
MaterialDeposit
      ↓
MaterialEngine
```

---

# 四十四、MaterialDeposit

建议数据：

```kotlin
data class MaterialDeposit(
    val position: Vec2,
    val radius: Float,
    val rotation: Float,

    val coverage: Float,
    val opacity: Float,
    val flow: Float,

    val water: Float,
    val pigment: PigmentSample,

    val pressure: Float,
    val speed: Float,

    val texturePhase: Float
)
```

这会成为 Brush Kernel 与物理系统之间的统一协议。

---

# 四十五、第九大阶段：Pressure / Speed / Tilt Dynamics

最终 BrushDynamics 必须采用：

```text
Input Sensor
        ↓
Response Curve
        ↓
Parameter Modifier
```

而不是：

```text
pressure * size
```

---

# 四十六、Pressure

可以控制：

```text
Size
Opacity
Flow
BristleSpread
PigmentLoad
WaterLoad
```

每一个独立。

例如水彩：

```text
Pressure → Size
Pressure → Water
Pressure → Pigment
```

---

# 四十七、Speed

可以影响：

```text
Coverage
Opacity
Dryness
Spacing
Texture Contrast
```

例如：

```text
slow
 ↓
满载

fast
 ↓
飞白
```

---

# 四十八、Tilt

可以控制：

```text
Brush Aspect
Rotation
Bristle Direction
Contact Area
```

例如毛笔：

```text
垂直
 ↓
细

倾斜
 ↓
宽
```

---

# 四十九、第十大阶段：Brush Cursor

Cursor 必须使用：

```text
BrushKernel
```

而不是重新画一个 Android Drawable。

统一：

```text
Brush Preview
Brush Cursor
Brush Thumbnail
Real Stamp
```

共享：

```text
Sampling
Transform
Dynamics
```

最终做到：

> Cursor 显示什么，第一笔真正出现的就是什么。

---

# 五十、第十一阶段：Rendering Pipeline

最终建议：

```text
Input
 ↓
Stroke
 ↓
Brush Kernel
 ↓
Deposit
 ↓
Dry/Wet Engine
 ↓
Pigment
 ↓
Optical Layer
 ↓
Paper Composite
 ↓
Color Management
 ↓
Present
```

而不是现在：

```text
BrushStamp
 ↓
stroke FBO
 ↓
pigment FBO
```

让每一个 Pass 都有清晰语义。

---

# 五十一、RenderGraph

建议正式建立：

```text
RenderGraph
```

Pass：

```text
BrushDepositPass
WetInjectionPass
WaterVelocityPass
PigmentAdvectionPass
PigmentDiffusionPass
AbsorptionPass
DryingPass
PigmentResolvePass
PaperPass
CompositePass
PresentPass
```

这样以后可以：

```text
启用水彩
→ 自动加入 Wet Pass

干笔
→ 跳过 Wet Pass

纯 PNG
→ 直接 Brush Pass
```

---

# 五十二、第十二大阶段：纸张系统正式独立

PaperMaterial：

```kotlin
data class PaperMaterial(
    val grainTexture: Texture,
    val heightTexture: Texture,
    val absorbencyTexture: Texture,
    val roughnessTexture: Texture,
    val fiberDirectionTexture: Texture,
    val absorptionRate: Float,
    val toothStrength: Float
)
```

这样：

```text
冷压纸
热压纸
粗纹纸
宣纸
素描纸
油画布
```

都只是不同 PaperDefinition。

---

# 五十三、第十三大阶段：Undo/Redo 必须与物理模拟兼容

普通：

```text
Stroke List
```

可以。

但是 Wet Simulation 是时间相关的。

所以：

```text
Checkpoint
+
Stroke Log
```

必须结合。

推荐：

```text
Stroke 0
Stroke 1
Checkpoint
Stroke 2
Stroke 3
Checkpoint
...
```

撤销：

```text
找到最近 Checkpoint
↓
恢复模拟状态
↓
Replay 后续 Stroke
```

---

# 五十四、草稿文件必须记录 Brush Revision

不能只：

```text
brushId
```

而需要：

```text
brushId
brushRevision
brushSourceHash
materialRevision
randomSeed
simulationVersion
```

否则：

```text
今天打开
```

和：

```text
半年后重新打开
```

可能得到不同结果。

---

# 五十五、Random Seed 必须保存

任何：

```text
Scatter
Jitter
Bristle Randomness
Particle Randomness
```

全部必须：

```text
Deterministic Random
```

即：

```text
StrokeSeed
+
PointIndex
+
BristleIndex
```

决定随机数。

不要：

```text
Math.random()
```

---

# 五十六、第十四阶段：性能架构

目标不是：

> Shader 越多越高级。

而是：

> **只对需要模拟的像素进行模拟。**

---

# 五十七、性能分层

建立：

```text
QualityTier
```

例如：

### LOW

```text
Dry Full Resolution
Wet 1/4 Resolution
1-pass diffusion
```

### MEDIUM

```text
Wet 1/2 Resolution
2-pass diffusion
Basic paper
```

### HIGH

```text
Wet 1/2~1x
Advection
Diffusion
Absorption
Drying
Granulation
```

### ULTRA

```text
高精度 Active Tile
完整 Paper Response
高级 Bristle
高级 Pigment
```

---

# 五十八、为什么湿画模拟最好不要一开始全分辨率

你们现在设备是约：

```text
1080 × 1865
```

约：

```text
2M pixels
```

如果大量 FP16 状态都全画布双缓冲，会迅速产生巨大的 GPU memory bandwidth 压力。

Android 文档确认 RGBA16F 对应每通道 16-bit 浮点的 RGBA 格式；这类格式非常适合高动态范围的中间状态，但不意味着应该让所有 simulation state 永久全分辨率存在。

因此：

```text
视觉 Canvas
=
Full Resolution

Physics Canvas
=
Adaptive Resolution
```

应该成为基本原则。

---

# 五十九、第十五阶段：必须建立真正的测试体系

这是现在项目里最应该增加的部分之一。

建立：

```text
BrushGoldenTests
MaterialGoldenTests
WetSimulationTests
ColorTests
TransformTests
ReplayTests
PerformanceTests
```

---

# 六十、Brush Golden Test

例如：

```text
PNG:
512×512
```

要求：

```text
Scale = 1
Rotation = 0
Opacity = 1
Flow = 1
```

最终：

```text
MAE ≤ 1 LSB
```

或使用更严格的像素匹配规则。

这是：

# WYSIWYG 最高优先级测试

---

# 六十一、Rotation Test

测试：

```text
0°
15°
30°
45°
90°
135°
180°
270°
```

同时：

```text
水平
垂直
圆
S 型
急转
```

检查：

```text
方向连续性
纹理连续性
无突然翻转
```

---

# 六十二、Phase Test

连续画：

```text
1000 px
```

要求：

```text
Texture Phase Drift
```

在允许误差内。

否则：

```text
纹理会周期性错位
```

---

# 六十三、Dry Brush Test

测试：

```text
Pressure
Speed
Paper
Load
```

检查：

```text
飞白
纹理
纤维
沉积
```

---

# 六十四、Wet Test

测试：

```text
Wet-on-Dry
Wet-on-Wet
Water Load
Pigment Load
Drying
Rewet
Backrun
Granulation
```

这些应该成为固定的视觉回归样本。

---

# 六十五、Wet Engine 最重要的视觉验收

最终必须能够产生至少下面这些现象：

```text
1. 湿边
2. 边缘浓积
3. 中心稀释
4. 颜料扩散
5. 颜色互相渗透
6. 局部回流
7. 不同水量产生不同结果
8. 纸张不同产生不同结果
9. 放置后逐渐干燥
10. 干后再次加水能部分重新激活
```

第 10 项很高级，但也是未来可以做到的方向。

---

# 六十六、最终效果应该达到什么等级

我建议把目标划成五级。

## Level 1——Digital Brush

具备：

```text
PNG Brush
Pressure
Spacing
Rotation
Opacity
Flow
```

现在项目已经接近这里。

---

## Level 2——Professional Brush Engine

具备：

```text
Brush Fidelity
Shape
Grain
Continuous Texture
Texture Phase
Stroke Frame
Pressure
Speed
Tilt
Deterministic Replay
```

这是你们接下来第一重大目标。

---

## Level 3——Advanced Dry Media

具备：

```text
Bristle
Paper Tooth
Fiber
Brush Load
Dryness
Pigment Deposit
```

达到：

```text
铅笔
炭笔
粉笔
干刷
毛刷
```

已经可以成为真正专业的数字绘画笔刷系统。

---

## Level 4——Advanced Wet Media

具备：

```text
Water
Mobile Pigment
Fixed Pigment
Advection
Diffusion
Absorption
Evaporation
Edge Darkening
Bloom
Backrun
Granulation
```

达到：

```text
水彩
水墨
湿画
Wet-on-Wet
Wet-on-Dry
```

这个阶段才是真正意义上的“物理型数字水彩”。

现有研究中，水、移动颜料、固定颜料、扩散、吸收以及纸面沉积正是数字水彩达到自然效果的核心组成部分。

---

## Level 5——Professional-Class Paint Engine

最终：

```text
Brush Fidelity
+
Advanced Dry
+
Advanced Wet
+
Pigment Mixing
+
Paper Interaction
+
Deterministic Replay
+
Adaptive GPU Simulation
+
Color Management
+
Layer System
+
Non-destructive Document
```

这才是我认为你应该把“顶级绘画工具”定义为的目标。

它不等于：

> “100% 模拟现实。”

而是：

> **在用户交互、笔刷控制、视觉结果和材质表现上，达到专业绘画软件可以接受的水平，同时保持移动设备上的实时性。**

---

# 六十七、完整实施顺序

这里是我最建议你们真正执行的路线。

```text
PHASE 0
基础审计
      ↓
PHASE 1
Stroke Framework
      ↓
PHASE 2
Brush Kernel
      ↓
PHASE 3
PNG WYSIWYG
      ↓
PHASE 4
Shape + Grain
      ↓
PHASE 5
Texture Phase / Rake
      ↓
PHASE 6
Dry Material Engine
      ↓
PHASE 7
Paper Engine
      ↓
PHASE 8
Pigment Engine
      ↓
PHASE 9
Wet Material Engine
      ↓
PHASE 10
Water Simulation
      ↓
PHASE 11
Drying / Bloom / Granulation
      ↓
PHASE 12
Color / Optical Pipeline
      ↓
PHASE 13
Adaptive Tile GPU
      ↓
PHASE 14
History / Replay
      ↓
PHASE 15
Golden Tests
      ↓
PHASE 16
Quality Tier
      ↓
PHASE 17
Production Optimization
```

---

# 六十八、每个阶段的难度

| 阶段               |    难度 | 主要风险          |
| ---------------- | ----: | ------------- |
| 0 基础审计           |    ★★ | 对现有代码理解不足     |
| 1 Stroke         |   ★★★ | 输入/坐标/采样      |
| 2 Brush Kernel   |  ★★★★ | 架构            |
| 3 WYSIWYG        |  ★★★★ | 采样/Alpha/颜色   |
| 4 Shape/Grain    |   ★★★ | 连续纹理          |
| 5 Phase/Rake     |  ★★★★ | 转弯和方向连续       |
| 6 Dry Engine     |  ★★★★ | Bristle/Paper |
| 7 Paper          |  ★★★★ | 高度/吸收/纹理      |
| 8 Pigment        | ★★★★★ | 状态表示/内存       |
| 9 Wet Engine     | ★★★★★ | 材质状态          |
| 10 Water         | ★★★★★ | GPU 模拟        |
| 11 Drying/Bloom  | ★★★★★ | 稳定性           |
| 12 Color         |  ★★★★ | 色彩空间          |
| 13 Adaptive Tile | ★★★★★ | GPU 性能        |
| 14 History       |  ★★★★ | 时态模拟          |
| 15 Golden Tests  |   ★★★ | 工具链           |
| 16 Quality Tier  |   ★★★ | 参数体系          |
| 17 Optimization  | ★★★★★ | 兼容性           |

真正困难的不是：

```text
画一个 PNG
```

而是：

```text
让 Brush
+
Stroke
+
Paper
+
Pigment
+
Water
+
GPU
```

在同一个确定性的数学体系里面工作。

---

# 六十九、现有项目应该怎样调整，而不是推倒重来

现在这些应该保留：

```text
PaintActivity
PaintEngineController
PointerInputProcessor
StrokeResampler
StrokeInterpolator
Stroke
StrokePoint
BrushGenerator
GLPaintRenderer
RenderTarget
FBO
Mixbox
present.vert
present.frag
```

但职责需要重新整理。

---

# 七十、新目录建议

建议逐步形成：

```text
paint/
│
├── core/
│
├── input/
│
├── stroke/
│   ├── Stroke.kt
│   ├── StrokePoint.kt
│   ├── StrokeFrame.kt
│   ├── StrokePath.kt
│   ├── StrokeResampler.kt
│   └── StrokeTangentSolver.kt
│
├── brush/
│   ├── BrushDefinition.kt
│   ├── BrushSource.kt
│   ├── ImageBrushSource.kt
│   ├── MaskBrushSource.kt
│   ├── BrushShape.kt
│   ├── BrushGrain.kt
│   ├── BrushDynamics.kt
│   ├── BrushKernel.kt
│   ├── BrushSample.kt
│   ├── BrushTextureSampler.kt
│   ├── BrushTextureState.kt
│   ├── BrushRuntimeState.kt
│   └── BrushRepository.kt
│
├── material/
│   ├── MaterialEngine.kt
│   │
│   ├── dry/
│   │   ├── DryMaterialEngine.kt
│   │   ├── DryBrushLoad.kt
│   │   ├── BristleModel.kt
│   │   └── DryDepositModel.kt
│   │
│   └── wet/
│       ├── WetMaterialEngine.kt
│       ├── WaterField.kt
│       ├── MobilePigmentField.kt
│       ├── FixedPigmentField.kt
│       ├── WaterSolver.kt
│       ├── PigmentAdvection.kt
│       ├── PigmentDiffusion.kt
│       ├── AbsorptionModel.kt
│       ├── DryingModel.kt
│       └── BloomModel.kt
│
├── pigment/
│   ├── PigmentColor.kt
│   ├── PigmentState.kt
│   ├── PigmentLatent.kt
│   ├── PigmentMixer.kt
│   └── MixboxAdapter.kt
│
├── paper/
│   ├── PaperDefinition.kt
│   ├── PaperMaterial.kt
│   ├── PaperHeightField.kt
│   ├── PaperAbsorbency.kt
│   ├── PaperGrain.kt
│   └── FiberField.kt
│
├── rendering/
│   ├── RenderGraph.kt
│   ├── RenderPass.kt
│   ├── RenderTarget.kt
│   └── gl/
│
├── simulation/
│   ├── ActiveRegion.kt
│   ├── TileManager.kt
│   ├── SimulationScheduler.kt
│   └── QualityController.kt
│
├── history/
│
└── test/
    ├── BrushGoldenTest.kt
    ├── StrokeGoldenTest.kt
    ├── WetGoldenTest.kt
    ├── ColorGoldenTest.kt
    └── ReplayGoldenTest.kt
```

---

# 七十一、最重要的工程原则

以后开发时必须坚持以下原则。

## 原则 1

```text
Brush Source ≠ Material Simulation
```

PNG 是 Brush Source。

水彩是 Material Simulation。

---

## 原则 2

```text
Stroke ≠ Stamp
```

Stroke 是连续曲线。

Stamp 只是采样结果。

---

## 原则 3

```text
Texture ≠ Alpha
```

纹理可以有视觉信息。

Alpha 只是其中一个维度。

---

## 原则 4

```text
Opacity ≠ Flow ≠ Coverage
```

三者必须保持独立语义。

---

## 原则 5

```text
Physics ≠ Blur
```

水彩绝对不能靠 Blur 假装。

---

## 原则 6

```text
Noise ≠ Material
```

纸纹和颗粒不能简单靠 Noise 美化。

---

## 原则 7

```text
Random ≠ Non-deterministic
```

所有随机必须可重放。

---

## 原则 8

```text
Visual Resolution ≠ Simulation Resolution
```

这会成为移动 GPU 性能的核心。

---

# 七十二、最终开发的正确顺序

我尤其建议你不要再按照旧文档：

```text
阶段 3 水痕
↓
阶段 4 飞白
```

直接往前推进。

更合理的是：

```text
第一阶段
把 Brush 做到真正专业

第二阶段
把 Dry Material 做到专业

第三阶段
把 Paper 做到专业

第四阶段
把 Pigment Mixing 做到稳定

第五阶段
再开始 Wet Simulation

第六阶段
最后加入 Drying/Bloom/Granulation
```

因为：

> **如果 Brush 基础层没有稳定，越早加入水彩物理，越难判断究竟是 Brush 错了，还是 Fluid Solver 错了。**

---

# 七十三、我建议最终把整个系统看成“三层世界”

这是整个设计最核心的一点。

## 第一层：Artist World

用户看到：

```text
Brush
Stroke
Pressure
Speed
Color
Paper
```

---

## 第二层：Material World

系统内部：

```text
Coverage
Pigment
Water
Bristle
Fiber
Wetness
```

---

## 第三层：GPU World

最终：

```text
Texture
FBO
Ping-Pong
Shader
Tile
RenderPass
```

三个世界不能互相污染。

即：

```text
Artist World
      ↓
Material World
      ↓
GPU World
```

而不能：

```text
Activity
↓
Shader uniform
↓
直接决定物理行为
```

---

# 七十四、这个项目真正的“北极星”

我建议以后项目的所有技术讨论最终都回到一句话：

> **用户选择一个 Brush，然后用手写笔画下去；系统应该让用户感觉自己真的拿着那支笔、蘸着那种材料、在那张纸上作画。**

于是：

```text
PNG
```

只是“笔尖长什么样”。

```text
Stroke
```

决定“笔怎么运动”。

```text
Dynamics
```

决定“手怎么控制”。

```text
Material
```

决定“颜料怎么表现”。

```text
Paper
```

决定“颜料落在什么东西上”。

```text
Pigment
```

决定“颜色怎么混合”。

```text
Water
```

决定“湿画怎么流动”。

```text
Drying
```

决定“它最后怎么留下来”。

这才是一套完整的数字绘画内核。

---

# 七十五、最终目标效果

按照这条路线做完后，你应该能够看到这样的结果：

### 干笔

```text
轻压
→ 细、淡、有纸纹

重压
→ 粗、浓、覆盖强

快速
→ 飞白

慢速
→ 颜料充分沉积

倾斜
→ 接触面变宽

粗纸
→ 颗粒明显

细纸
→ 线条连续
```

### 水彩

```text
少水
→ 浓、边缘清晰

多水
→ 淡、扩散

湿纸
→ 自由扩散

干纸
→ 明显笔触边界

停留
→ 局部积色

干燥
→ 边缘浓积

重新加水
→ 重新激活局部颜料

不同纸张
→ 不同扩散和颗粒
```

### PNG 笔刷

```text
选择 PNG
↓
Cursor 显示 PNG
↓
点击
↓
出现真实 PNG
↓
拖动
↓
PNG 沿笔迹自然运动
↓
转弯
↓
方向连续
↓
高速
↓
无空洞
↓
缩放
↓
仍保持正确视觉比例
```

这就是你现在真正应该追求的结果。

---

# 七十六、最终技术路线一句话总结

整个项目最后应该形成：

```text
                     ReBackground Paint Engine

                  ┌────────────────────────┐
                  │      Brush Kernel      │
                  └───────────┬────────────┘
                              │
            ┌─────────────────┼──────────────────┐
            │                 │                  │
            ▼                 ▼                  ▼
        Image/Mask          Dynamics          Stroke
        Shape/Grain         Pressure           Frame
            │                 │                  │
            └─────────────────┼──────────────────┘
                              ▼
                       Material Deposit
                              │
                 ┌────────────┴────────────┐
                 │                         │
                 ▼                         ▼
          Dry Material                 Wet Material
                 │                         │
         Bristle / Paper          Water / Pigment
         Fiber / Load             Flow / Diffusion
         Coverage                 Absorption
                 │                         │
                 └────────────┬────────────┘
                              ▼
                         Pigment Engine
                              │
                         Mixbox/KM
                              │
                         Paper Optical
                              │
                          Composite
                              │
                            Display
```

**这就是我认为最适合你这个项目的终极架构方向。**

其中最关键的战略变化只有一个：

> **不要再把“画笔效果”“水彩效果”“纹理效果”分别看成三个功能。应该把它们统一成：Brush Kernel + Material Engine + Paper Engine。**

这样以后你们才能不断增加新的画材，而不用一次又一次推翻绘画内核。

另外，Mixbox 当前官方版本确实可以通过 GLSL LUT 使用，并支持 latent 多色混合；但官方明确其 CC BY-NC 4.0 条款限制非商业用途，所以如果未来产品授权性质改变，应重新检查许可。

**下一步最适合做的不是直接进入“阶段 1 写代码”，而是对你现有真实源码做一次 Brush Kernel 架构审计：把 `BrushDefinition / BrushTip / BrushMaterial / BrushDynamics / BrushGenerator / BrushStamp / ColoredBrushStamp / GLPaintRenderer / brush_stamp.vert / brush_stamp.frag / PaintEngineController / StrokeResampler` 全部对照这份总体设计逐项映射。** 这样可以确定哪些直接保留、哪些拆分、哪些重构、哪些暂时不要碰，再形成真正可以执行的 **PR-0 → PR-20 改造清单**，避免在已经正确的 Mixbox、坐标变换、生命周期和 FBO 基础上发生无意义回退。

# G5 纸张参数完整接入

## 判定

PASS。L1 数值门禁与 L2 真机渲染门禁全部通过。

## 最终预设

| paperId | heightAmplitude | roughness | absorption | fiberDensity | grainScale | seed |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `smooth` | 0.10 | 0.10 | 0.10 | 0.10 | 1.30 | 101 |
| `medium` | 0.40 | 0.50 | 0.50 | 0.50 | 1.00 | 211 |
| `rough-watercolor` | 0.80 | 0.90 | 0.80 | 0.90 | 0.72 | 307 |

`GLPaintRenderer.setPaper` 将三项新增参数提交为 `uPaperRoughness`、`uPaperAbsorption`、`uPaperFiberDensity`；`brush_stamp.frag` 分别用于接触起伏、干颜料孔隙吸收和稳定微纤维孔隙。旧 `setDryPaper` 保持这三项为零，因此既有 G5 fixture 行为不变。

## L2 真机渲染验收

固定 fixture：26px 铅笔、500px 水平直线、paper affinity=1、真实 stroke FBO 读回。

| 项目 | 实测 | 阈值 | 判定 |
| --- | ---: | ---: | --- |
| R1 参数完整接入 | Logcat 三纸均记录 roughness / absorption / fiberDensity | 3 / 3 | PASS |
| R2 粗糙/光滑覆盖标准差比 | 11.0264 | >= 1.5 | PASS |
| R3 粗糙与光滑 SSIM | 0.02545 | < 0.95 | PASS |
| R4 覆盖方差（光/中/粗） | 0.000366 / 0.010819 / 0.044504 | 单调，粗 >= 1.5x 光 | PASS |
| R5 GL error | 三纸均 0 | 0 | PASS |

R1 Logcat：

- `smooth`: `0.1 / 0.1 / 0.1`
- `medium`: `0.5 / 0.5 / 0.5`
- `rough-watercolor`: `0.9 / 0.8 / 0.9`

真机证据：`evidence/2026-09-23/G5-PAPER/R/uncommitted/`。

## L1 与基础回归

- G5 A–F：全部 PASS；纸张差异、干刷断裂和笔毛方向均无退化，GL error=0。
- T1–T7：7 / 7 PASS；NaN=0、Inf=0、GL error=0。

## 用户目视验证

1. 新建画布，选择“粗糙水彩纸”。
2. 选择铅笔，快速画一条线，放大到 400%。可见稳定的孔隙飞白与纤维断续。
3. 新建相同尺寸画布，选择“光滑纸”，以相同压力和速度重画。飞白显著减少、覆盖更均匀。

## 遗留

`absorption` 仅在 G5 干媒介中表示纸孔对干颜料的接受量；它没有提前接入 G6 湿媒介模拟。

# G2-A Raw Sampling 状态

## 判定

PASS

## 改动

- 新增 `BrushTextureSamplingMode.SOURCE_RGBA`，PNG 笔刷可选择保留源 RGBA；既有笔刷默认继续使用 `ALPHA_MASK`，行为不变。
- `brush_stamp.frag` 的 SOURCE_RGBA 分支直接采样源纹理，不再丢弃 RGB，也不做 alpha 均值归一化。
- `GLPaintRenderer` 按 stamp 传递采样模式；增加仅供验收使用的 RGBA16F/HALF_FLOAT 精确读回。
- Debug 构建新增 `G2RawSamplingActivity`：生成确定性 PNG、执行 1:1 texel-aligned stamp、计算误差并输出完整证据。

## 测试结果

- 条件：rotation=0、scale=1、opacity=1、flow=1；16×16 PNG 对齐到 16×16 stamp。
- 真机：HUAWEI NBLUT21218023155。
- RGB MAE：`0.0000818694`，阈值 `≤ 0.0039215686`。
- Alpha MAE：`0.00000574449`，阈值 `≤ 0.0039215686`。
- MaxError：`0.000243183`，阈值 `≤ 0.0078431373`。
- GL error：`0`。
- `:app:assembleDebug`：PASS。
- `:app:testDebugUnitTest`：PASS。
- 既有真机 T1-T7：`7/7 PASS`。
- 完成 G2-B/C/D 后再次执行 Identity：指标保持不变，PASS。
- 证据：`evidence/2026-09-22/G2/A/uncommitted/`（input/reference/actual/diff/metrics/summary/log）。

## 迭代记录

1. 首次构建发现 Debug Activity 导入路径错误，按编译器错误修正。
2. 真机输出全透明；定位为 RGBA16F 使用 GL_FLOAT 读回不兼容。
3. RGBA8 读回同样被设备拒绝。
4. 改用附件原生 `GL_HALF_FLOAT` 并精确解码，达到全部阈值。

## 遗留

- G2-B 旋转采样、G2-C 透明边缘、G2-D 宽高比尚未开始。

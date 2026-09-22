# G2-C Alpha / Edge 状态

## 判定

PASS

## 改动

- Brush Texture Sampler 改为 alpha-aware 双线性采样：四邻域先转关联色（RGB×A）插值，再安全还原直通 RGB。
- 完全透明样本统一输出透明黑，避免无定义 RGB；低 Alpha 有效色不受透明黑/白 texel 污染。
- 验收覆盖透明黑边、透明白边、半透明色边，并在 45° 旋转下检查插值边缘。

## 测试结果

- 3/3 fixtures PASS，GL error 全部为 0。
- 最差 halo RGB error：`0.001855493`，阈值 `≤ 0.007843137`。
- Alpha MAE：`0.00000563227`，阈值 `≤ 0.003921569`。
- 每组检查低 Alpha 边缘像素 408 个。
- unexpected dark cluster：`0`。
- unexpected bright cluster：`0`。
- 证据：`evidence/2026-09-22/G2/C/uncommitted/`。

## 遗留

- 无 G2-C 遗留；继续 G2-D。

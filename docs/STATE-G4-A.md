# G4-A Response Curves 状态

## 判定

PASS

## 改动

- 新增统一 `DynamicsCurve`：Linear、Bezier、Soft、Hard、Inverse、Stepped。
- 所有曲线输入输出归一化到 `[0,1]`；Bezier 使用确定性反解。
- Dynamics 流程统一为 Sensor → Response Curve → Parameter Mapping。

## 测试结果

- Linear 101 点：R² `0.999999999999998`，MAE `9.81e-9`，MaxError `2.86e-8`。
- Linear 单调违规 `0`。
- Bezier/Soft/Hard/Inverse 最差 MAE `1.98e-8`，最差 MaxError `8.29e-8`。
- 阈值：R² `≥0.99`、MAE `≤0.01`、MaxError `≤0.03`。
- 证据：`evidence/2026-09-22/G4/A/uncommitted/`。

## 遗留

- 无 G4-A 遗留。

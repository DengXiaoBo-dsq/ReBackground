# G4 Dynamics 总状态

## 判定

PASS

## 子任务

- G4-A Response Curves：PASS；Linear R² `0.999999999999998`，单调违规 `0`。
- G4-B Pressure Endpoints：PASS；size/opacity/flow 最大相对误差 `1.49e-8`。
- G4-C Speed Dynamics：PASS；三组理论速度误差 `0`，单调违规 `0`。
- G4-D Tilt Dynamics：PASS；平均方向误差 `0.00000192°`，P99 `0.00000665°`。

## 实现结果

- 建立 renderer-independent 的 Sensor → Curve → Parameter Mapping → Smoothing 管线。
- Pressure、Speed、Tilt 不再由 `BrushGenerator` 分散解释。
- size、opacity、flow、aspect、rotation 均支持独立响应与确定性重放。
- 动态 opacity/flow 与几何参数按 stamp 输出，并在插值 stamp 间连续过渡。
- Speed 使用帧率无关的时间常数平滑，同时保留 raw speed 供诊断与兼容。

## 最终回归

- `:app:testDebugUnitTest`：PASS，共 `63` 项。
- `:app:assembleDebug`：PASS。
- 最终 APK 上 G4-A 至 G4-D：全部 PASS。
- 最终 APK 上 G3-A 至 G3-E：全部 PASS。
- 最终 APK 上 G2-A 至 G2-D：全部 PASS。
- 最终 APK 上 G0 能力探测：PASS；RGBA16F/RG16F FBO 均 COMPLETE。
- 最终 APK 上 T1–T7：`7/7 PASS`。
- 真机验收期间临时停用的第三方前台应用已恢复启用。

## 证据

- `evidence/2026-09-22/G4/A/uncommitted/`
- `evidence/2026-09-22/G4/B/uncommitted/`
- `evidence/2026-09-22/G4/C/uncommitted/`
- `evidence/2026-09-22/G4/D/uncommitted/`

## 遗留

- G4 无阻塞遗留，可以进入 G5。

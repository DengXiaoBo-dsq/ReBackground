# G2 Brush Fidelity 总状态

## 判定

PASS

## 子任务

- G2-A Raw Sampling：PASS；RGB MAE `0.0000818694`，Alpha MAE `0.00000574449`。
- G2-B Transform Sampling：PASS；8 个角度全部通过，最差 RGB MAE `0.0000122491`，最差 SSIM `0.9999999516`。
- G2-C Alpha / Edge：PASS；最差 halo RGB error `0.001855493`，黑/白异常簇均为 `0`。
- G2-D Aspect Ratio：PASS；4 个规定比例全部通过，最差误差 `0.1001%`。

## 最终回归

- `:app:assembleDebug`：PASS。
- `:app:testDebugUnitTest`：PASS。
- 最终 APK 上 G2-A：PASS。
- 最终 APK 上 T1-T7：`7/7 PASS`，NaN/Inf/GL error 均为 0。
- 真机验收期间临时停用的第三方前台应用已恢复启用。

## 证据

- `evidence/2026-09-22/G2/A/uncommitted/`
- `evidence/2026-09-22/G2/B/uncommitted/`
- `evidence/2026-09-22/G2/C/uncommitted/`
- `evidence/2026-09-22/G2/D/uncommitted/`

## 遗留

- G2 无阻塞遗留，可以进入 G3。

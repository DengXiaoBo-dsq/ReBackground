# G4-D Tilt Dynamics 状态

## 判定

PASS

## 改动

- 新增独立 `TiltSensor`，统一输出 magnitude 与 azimuth。
- Tilt 曲线可独立控制 brush aspect 与 rotation influence。
- 有路径方向时使用最短角插值，无有效路径方向时直接使用 Tilt azimuth。

## 测试结果

- 0–355°、每 5° 一点：平均方向误差 `0.00000192°`。
- P99 方向误差 `0.00000665°`，阈值 `≤3°`。
- 最大 aspect 相对误差 `0%`。
- 证据：`evidence/2026-09-22/G4/D/uncommitted/`。

## 遗留

- 无 G4-D 遗留。

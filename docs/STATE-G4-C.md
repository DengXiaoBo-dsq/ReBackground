# G4-C Speed Dynamics 状态

## 判定

PASS

## 改动

- 新增独立 `SpeedSensor`，统一执行 distance/time、clamp、归一化与时间常数平滑。
- raw speed 与 filtered speed 分离，兼容记录契约并让参数映射使用稳定速度。
- 重复或合并时间戳保持上一速度，避免错误的瞬时归零尖峰。
- Speed 可独立控制 size、opacity、flow，连续插值保持动态参数平滑。

## 测试结果

- `10px/10ms`、`10px/20ms`、`10px/40ms` 实测分别为 `1000/500/250 px/s`。
- 最大速度误差 `0`；单调违规 `0`。
- 35ms 时间常数平滑结果有限且在合法范围；0ms 时间差保持 `321 px/s`。
- 证据：`evidence/2026-09-22/G4/C/uncommitted/`。

## 遗留

- 无 G4-C 遗留。

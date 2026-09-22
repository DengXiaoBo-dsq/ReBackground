# G3-D Rake / StrokeFrame 状态

## 判定

PASS

## 改动

- Rake Grain 方向由每个 stamp 的 StrokeFrame 驱动。
- 增加水平、垂直、45°及十二点圆形切线轨迹真机验收。

## 测试结果

- 四类轨迹平均方向误差：`0.2345°`，阈值 `≤3°`。
- P99 方向误差：`0.4579°`，阈值 `≤8°`。
- 圆形轨迹各采样点均跟随局部切线；GL error 为 `0`。
- 证据：`evidence/2026-09-22/G3/D/uncommitted/`。

## 遗留

- 无 G3-D 遗留。

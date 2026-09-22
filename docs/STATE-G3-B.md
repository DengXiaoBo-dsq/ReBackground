# G3-B Grain Phase 状态

## 判定

PASS

## 改动

- Grain 相位统一为 `initialPhase + arcLength / grainScale`，中间计算使用 Double 精度。
- 插值 stamp 从区间起点相位按弧长连续推进，不在事件或分段边界重置。
- 增加 1000px 与 100/200/500/750/1000px 检查点验收。

## 测试结果

- 1000px 最大漂移：`0.00001815 texel`，阈值 `≤0.5 texel`。
- P99 漂移：`0.00001320 texel`，阈值 `≤0.25 texel`。
- 最大步进残差：`0.00002392 texel`，无相位重置或大于 1 texel 跳变。
- 证据：`evidence/2026-09-22/G3/B/uncommitted/`。

## 遗留

- 无 G3-B 遗留。

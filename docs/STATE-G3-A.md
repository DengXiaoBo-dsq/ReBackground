# G3-A Shape / Grain Independence 状态

## 判定

PASS

## 改动

- 新增彼此独立的 `ShapeSource` 与 `GrainSource` 数据语义。
- Shape 与 Grain 使用独立纹理单元；Grain 只调制 Shape 内部覆盖率，不改变 Shape 支持域。
- 增加 Shape A/B × Grain A/B 四组合真机验收。

## 测试结果

- Shape A + Grain A/B：BoundingBox 相对误差 `0%`，CoverageArea 差 `0%`。
- Shape B + Grain A/B：BoundingBox 相对误差 `0%`，CoverageArea 差 `0%`。
- Grain scale、phase、orientation 在 Shape 切换时保持不变。
- GL error 全部为 `0`；阈值分别为 `≤1%`、`≤2%`。
- 证据：`evidence/2026-09-22/G3/A/uncommitted/`。

## 遗留

- 无 G3-A 遗留。

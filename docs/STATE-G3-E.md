# G3-E Continuous Stroke 状态

## 判定

PASS

## 改动

- stamp 间距使用笔刷 `spacingRatio`，插值数量使用向上取整避免欠采样。
- 插值 stamp 保留 Shape/Grain 全部字段，并按弧长连续插值 Grain phase。
- 增加 1000px 连续笔触中心走廊验收。

## 测试结果

- 1000px 笔触共 `210` 个 stamps。
- 走廊像素 `1001`，空洞像素 `0`，gapRate `0%`，阈值 `≤0.5%`。
- GL error 为 `0`。
- 证据：`evidence/2026-09-22/G3/E/uncommitted/`。

## 遗留

- 无 G3-E 遗留。

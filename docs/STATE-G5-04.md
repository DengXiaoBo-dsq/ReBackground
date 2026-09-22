# G5-04 Paper Height Correlation 状态

## 判定

PASS

## 改动

- 纸高经压力接触阈值转换为纸面保留/损失，再受笔刷纸纹亲和度控制。

## 测试结果

- 真机 `corr(PaperHeight, Coverage)` 为 `0.944795`（绝对值阈值 `≥0.5`）。
- GL error `0`。
- 证据：`Android/data/com.dsq.rebackground/files/evidence/2026-09-23/G5/D/uncommitted/`。

## 遗留

- 无。

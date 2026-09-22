# G5-02 Deposit Budget 状态

## 判定

PASS

## 改动

- 建立由笔刷覆盖度、载量、纸张响应和纤维响应相乘的确定性沉积预算。

## 测试结果

- 真机 `corr(load, depositMass)` 为 `0.9999999999999969`（阈值 `≥0.8`）。
- 沉积质量随载量单调下降；满载 `0.401062`，空载 `0`。
- 证据：`Android/data/com.dsq.rebackground/files/evidence/2026-09-23/G5/B/uncommitted/`。

## 遗留

- 无。

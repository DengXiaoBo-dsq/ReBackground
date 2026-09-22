# G5-05 Dry Bristle Gaps 状态

## 判定

PASS

## 改动

- 新增确定性平行纤维束、纵向脱落和密度非线性分离；在 GPU 侧增加纤维边缘抗锯齿。

## 测试结果

- 专用 1000px 干刷路径 gap rate `8.49%`（阈值 `5%–35%`）。
- 平均 gap `10.625px`，最大 gap `22px`，GL error `0`。
- 证据：`Android/data/com.dsq.rebackground/files/evidence/2026-09-23/G5/E/uncommitted/`。

## 遗留

- 无。

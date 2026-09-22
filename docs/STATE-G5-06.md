# G5-06 Bristle Orientation 状态

## 判定

PASS

## 改动

- 纤维场以笔刷局部坐标生成，并由 stamp 旋转统一带入文档空间。
- 方向夹具按每根纤维至少约四个 framebuffer 像素采样，排除像素格而非纤维造成的度量偏差。

## 测试结果

- 真机 0°–165° 多角度方向误差：平均 `3.533°`（阈值 `≤5°`），P99 `5.892°`（阈值 `≤12°`）。
- 全部案例 GL error `0`。
- 证据：`Android/data/com.dsq.rebackground/files/evidence/2026-09-23/G5/F/uncommitted/`。

## 遗留

- 无。

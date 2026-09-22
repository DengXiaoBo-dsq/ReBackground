# G2-D Aspect Ratio 状态

## 判定

PASS

## 改动

- SOURCE_RGBA stamp 从已上传纹理尺寸自动取得 `sourceWidth / sourceHeight`。
- 尺寸语义统一为：`diameterDocumentUnits` 表示高度，宽度为 `height × sourceAspect`。
- Mask 模式仍使用笔刷定义的 aspectRatio，不改变既有笔刷数据语义。

## 测试结果

- `1024×256`：实测 `960×240`，比例误差 `0%`。
- `256×1024`：实测 `60×240`，比例误差 `0%`。
- `1000×333`：实测 `720×240`，比例误差 `0.1000%`。
- `333×1000`：实测 `80×240`，比例误差 `0.1001%`。
- 4/4 PASS；最差误差 `0.1001%`，阈值 `≤ 0.5%`；GL error 全部为 0。
- 证据：`evidence/2026-09-22/G2/D/uncommitted/`。

## 遗留

- 无 G2-D 遗留。

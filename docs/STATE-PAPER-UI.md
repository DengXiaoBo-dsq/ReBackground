# Paper UI 状态

## 判定

PASS

## 预设

| paperId | 名称 | roughness | absorption | fiberDensity | grainScale | heightAmplitude | seed |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `smooth` | 光滑纸 | 0.08 | 0.24 | 0.18 | 1.35 | 0.04 | 101 |
| `medium` | 中粗纸 | 0.48 | 0.35 | 0.52 | 1.00 | 0.55 | 211 |
| `rough-watercolor` | 粗糙水彩纸 | 0.88 | 0.62 | 0.86 | 0.72 | 0.94 | 307 |

## 接口

`CreateImageSettingsActivity` 将选择映射为 `Intent.putExtra("paperId", id)`；`PaintActivity` 在创建引擎后调用 `PaintEngineController.setPaper(id)`；控制器解析预设并调用 `PaintGLSurfaceView.setPaper(definition)`；最终由 `GLPaintRenderer.setPaper(definition)` 设置既有 G5 shader 的纸高幅度、纹理尺度与种子。

未知 ID 回退为 `medium`。未传入 ID 的旧入口同样默认 `medium`，因此兼容既有调用方。

## 遗留

- 暂不支持用户导入纸张、缩略图或编辑；这些不属于 G5 验证入口范围。

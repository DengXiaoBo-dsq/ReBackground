# 技术决策

## 纸张是画布属性

日期：2026-09-23

纸张由稳定 `paperId` 表示，并在创建画布时确定；它不是笔刷纹理，也不随单笔切换。

理由：纸张高度场属于整个文档的连续坐标系统。将它作为画布属性可保证同一画布上的干刷载量、飞白和后续 G6 水彩吸收响应一致，同时让文档只需持久化一个可版本化的 ID。

当前预设由 `PaperPresets` 管理。UI 传递 `paperId`，`PaintActivity → PaintEngineController → PaintGLSurfaceView → GLPaintRenderer` 解析后，把 `heightAmplitude`、`grainScale` 和 `seed` 传入既有 brush shader uniform。未识别的 ID 回退至中粗纸。

创建画布界面的旧“纹理”Spinner 仅写入未被渲染链路消费的 `textureIndex`，因此以纸张选择替换，避免两个看似相近但只有一个实际生效的入口。

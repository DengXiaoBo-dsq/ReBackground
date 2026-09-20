# KNOWN-ISSUES

## P1-01 纹理方向跟随
- 现象：纹理不能沿笔迹方向自然展示
- 状态：PR-2.8 已改 BrushGenerator，待验证
- 归属：G3（Shape / Grain / Phase / Rake）
- G0 处理策略：只记录，不修复
- 修复时禁止影响：T1~T7 基线

## P1-02 铅笔浓度归一化
- 现象：铅笔绘制后浓度约 0.4，期望 1.0
- 状态：未修
- 归属：G2（Brush Fidelity / PNG WYSIWYG）
- G0 处理策略：只记录，不修复
- 修复方案：brush_stamp.frag 引入 uTextureAlphaMean 归一化

## P2-01 大像素边缘锯齿（荷叶边）
- 现象：大直径笔刷边缘呈锯齿
- 状态：stamp 模型固有问题，SDF 已写但关闭
- 归属：G2 后评估
- G0 处理策略：只记录，不修复

## P2-02 小像素断续
- 现象：小直径笔刷沿笔迹断续
- 状态：同 P2-01
- 归属：G2 后评估
- G0 处理策略：只记录，不修复

## P3-01 偶发长直线
- 现象：画布内出现跨笔长直线，偶发（3 次）
- 状态：已加双保险（ACTION_DOWN 清空 generatorStates + MAX_STROKE_JUMP=200f），观察中
- 归属：独立 PR，不混入任何 Gate
- G0 处理策略：只记录，不主动排查
- 若复现：立即截图 + 完整 Logcat（tag: STROKE / STAMP / PaintEngineController），不尝试修复
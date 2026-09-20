precision mediump float;
varying vec2 vUnitPosition;
uniform vec4 uColor;
uniform sampler2D uBrushTexture;
uniform float uUseTexture;
uniform float uFlow;

// ============================================================
// [MOD PR-2.5] 过渡带改为"绝对像素"
// ============================================================
uniform float uEdgeSoftnessPx;   // 过渡带宽度（文档像素）
uniform float uDiameterPx;       // 当前 stamp 直径（文档像素）

// [MOD PR-2.7] 纹理 alpha 均值（用于归一化）
uniform float uTextureAlphaMean;

// ============================================================
// [MOD PR-2.8] 笔迹方向角度（弧度）
//   用于纹理 UV 反向旋转，让纹理跟随笔迹方向
//   来源：BrushGenerator 里计算的笔迹方向（rotationRadians）
//   对程序化圆无影响（圆对称）
// ============================================================
uniform highp float uRotationRadians;

void main() {
    // 过渡带转 unit 空间（相对半径）
    float aaUnit = (2.0 * uEdgeSoftnessPx) / max(uDiameterPx, 0.5);
    aaUnit = min(aaUnit, 1.0);
    float alpha;

    if (uUseTexture < 0.5) {
        // ============================================================
        // 程序化圆：动态过渡带（圆对称，不需要旋转）
        // ============================================================
        float dist = length(vUnitPosition);
        alpha = 1.0 - smoothstep(1.0 - aaUnit, 1.0, dist);

        if (alpha <= 0.0) discard;
    } else {
        // ============================================================
        // [MOD PR-2.8] 纹理笔刷：反向旋转 UV，让纹理跟随笔迹方向
        //
        // 原理：
        //   - 几何体（quad）不旋转
        //   - 在 frag 里把采样坐标反向旋转
        //   - 效果：纹理图案跟着笔迹方向转，不出现方块/突出角
        //
        // 数学：
        //   vUnitPosition 在 [-1, 1] 空间，中心在 (0,0)
        //   旋转 -θ 得到采样坐标
        //   映射到 [0, 1] 后采样纹理
        //
        // 与 Krita 的等价：
        //   Krita 旋转 dab 位图（CPU）
        //   我们旋转 UV 采样坐标（GPU）
        //   数学等价
        // ============================================================
        float c = cos(-uRotationRadians);
        float s = sin(-uRotationRadians);
        vec2 rotatedPos = vec2(
                vUnitPosition.x * c - vUnitPosition.y * s,
                vUnitPosition.x * s + vUnitPosition.y * c
        );
        vec2 texCoord = rotatedPos * 0.5 + 0.5;

        float rawAlpha = texture2D(uBrushTexture, texCoord).a;

        // 归一化：纹理 = 质感调制
        float meanAlpha = max(uTextureAlphaMean, 0.05);
        alpha = rawAlpha / meanAlpha;
        alpha = min(alpha, 1.0);

        if (alpha <= 0.001) discard;
    }

    // flow 在 stamp 阶段乘（绘制时与抬手后浓度一致）
    alpha *= uColor.a * uFlow;
    gl_FragColor = vec4(uColor.rgb * alpha, alpha);
}
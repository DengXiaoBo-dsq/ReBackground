precision highp float;
varying vec2 vUnitPosition;
uniform vec4 uColor;
uniform sampler2D uBrushTexture;
uniform float uUseTexture;
uniform float uTextureSamplingMode;
uniform vec2 uTextureSize;
uniform float uFlow;
uniform sampler2D uGrainTexture;
uniform float uUseGrain;
uniform vec2 uGrainTextureSize;
uniform vec2 uHalfExtentDocument;
uniform float uGrainScaleDocumentUnitsPerTexel;
uniform float uGrainPhaseTexels;
uniform float uGrainRotationRadians;
uniform float uGrainDepth;
uniform float uDryLoad;
uniform float uDryArcLength;
uniform float uBristleDensity;
uniform float uPaperAffinity;
uniform float uPaperHeightAmplitude;
uniform float uPaperGrainScale;
uniform float uDrySeed;
uniform float uDryPressure;
uniform vec2 uCenterDocument;

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

vec4 sampleBrushTexture(vec2 uv) {
    vec2 size = max(uTextureSize, vec2(1.0));
    vec2 pixel = uv * size - vec2(0.5);
    vec2 base = floor(pixel);
    vec2 fraction = fract(pixel);
    vec2 maxIndex = size - vec2(1.0);

    vec2 p00 = (clamp(base, vec2(0.0), maxIndex) + vec2(0.5)) / size;
    vec2 p10 = (clamp(base + vec2(1.0, 0.0), vec2(0.0), maxIndex) + vec2(0.5)) / size;
    vec2 p01 = (clamp(base + vec2(0.0, 1.0), vec2(0.0), maxIndex) + vec2(0.5)) / size;
    vec2 p11 = (clamp(base + vec2(1.0), vec2(0.0), maxIndex) + vec2(0.5)) / size;

    vec4 s00 = texture2D(uBrushTexture, p00);
    vec4 s10 = texture2D(uBrushTexture, p10);
    vec4 s01 = texture2D(uBrushTexture, p01);
    vec4 s11 = texture2D(uBrushTexture, p11);
    s00.rgb *= s00.a;
    s10.rgb *= s10.a;
    s01.rgb *= s01.a;
    s11.rgb *= s11.a;

    vec4 associated = mix(
        mix(s00, s10, fraction.x),
        mix(s01, s11, fraction.x),
        fraction.y
    );
    if (associated.a <= 0.000001) return vec4(0.0);
    return vec4(associated.rgb / associated.a, associated.a);
}

vec2 wrappedTexelCenter(vec2 index, vec2 size) {
    vec2 wrapped = mod(mod(index, size) + size, size);
    return (wrapped + vec2(0.5)) / size;
}

float sampleGrain() {
    if (uUseGrain < 0.5 || uGrainDepth <= 0.0) return 1.0;

    float c = cos(-uGrainRotationRadians);
    float s = sin(-uGrainRotationRadians);
    vec2 localDocument = vUnitPosition * uHalfExtentDocument;
    vec2 oriented = vec2(
        localDocument.x * c - localDocument.y * s,
        localDocument.x * s + localDocument.y * c
    );
    vec2 grainTexel = vec2(uGrainPhaseTexels, 0.0) +
        oriented / max(uGrainScaleDocumentUnitsPerTexel, 0.000001);

    vec2 size = max(uGrainTextureSize, vec2(1.0));
    vec2 base = floor(grainTexel);
    vec2 fraction = fract(grainTexel);
    vec4 s00 = texture2D(uGrainTexture, wrappedTexelCenter(base, size));
    vec4 s10 = texture2D(uGrainTexture, wrappedTexelCenter(base + vec2(1.0, 0.0), size));
    vec4 s01 = texture2D(uGrainTexture, wrappedTexelCenter(base + vec2(0.0, 1.0), size));
    vec4 s11 = texture2D(uGrainTexture, wrappedTexelCenter(base + vec2(1.0), size));
    vec4 grainSample = mix(mix(s00, s10, fraction.x), mix(s01, s11, fraction.x), fraction.y);
    float luminance = dot(grainSample.rgb, vec3(0.2126, 0.7152, 0.0722)) * grainSample.a;
    // Preserve the Shape support while allowing strong internal texture.
    float texturedCoverage = 0.25 + 0.75 * luminance;
    return mix(1.0, texturedCoverage, uGrainDepth);
}

float dryHash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7)) + uDrySeed * 74.7) * 43758.5453);
}

float dryValueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(dryHash(i), dryHash(i + vec2(1.0, 0.0)), u.x),
               mix(dryHash(i + vec2(0.0, 1.0)), dryHash(i + vec2(1.0)), u.x), u.y);
}

float dryPaperHash(vec2 p) {
    float phase = p.x * 12.9898 + p.y * 78.233 + uDrySeed * 37.719;
    return 0.5 + 0.5 * sin(phase);
}

float dryPaperValueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(dryPaperHash(i), dryPaperHash(i + vec2(1.0, 0.0)), u.x),
               mix(dryPaperHash(i + vec2(0.0, 1.0)), dryPaperHash(i + vec2(1.0)), u.x), u.y);
}

float dryPaperHeight(vec2 documentPosition) {
    vec2 p = documentPosition / max(18.0 * uPaperGrainScale, 0.001);
    float value = dryPaperValueNoise(p) * 0.5714286;
    p *= 2.07;
    value += dryPaperValueNoise(p) * 0.2857143;
    p *= 2.07;
    value += dryPaperValueNoise(p) * 0.1428571;
    return clamp(value, 0.0, 1.0);
}

float sampleDryMaterial() {
    if (uDryLoad >= 0.999999 && uBristleDensity <= 0.0 && uPaperAffinity <= 0.0) return 1.0;
    vec2 localDocument = vUnitPosition * uHalfExtentDocument;
    float c = cos(uRotationRadians);
    float s = sin(uRotationRadians);
    vec2 documentPosition = uCenterDocument + vec2(
        localDocument.x * c - localDocument.y * s,
        localDocument.x * s + localDocument.y * c
    );

    float height = dryPaperHeight(documentPosition);
    float contact = 0.5 + (height - 0.5) * uPaperHeightAmplitude + (uDryPressure - 0.5) * 0.16;
    float paperTooth = mix(
        1.0,
        smoothstep(0.32, 0.68, contact),
        clamp(uPaperHeightAmplitude, 0.0, 1.0)
    );
    float paperFactor = mix(1.0, paperTooth, uPaperAffinity);

    float bands = 8.0 + uBristleDensity * 40.0;
    float coordinate = (clamp(vUnitPosition.y, -1.0, 1.0) * 0.5 + 0.5) * bands + 0.5;
    float band = floor(coordinate);
    float within = fract(coordinate);
    float bristleLoad = dryHash(vec2(band + uDrySeed * 0.23, 7.0));
    float halfWidth = 0.16 + bristleLoad * 0.22;
    float fiberAa = max(0.10, min(0.35, bands / max(uDiameterPx, 1.0) * 0.75));
    float fiber = 1.0 - smoothstep(halfWidth, halfWidth + fiberAa, abs(within - 0.5));
    float longitudinal = dryValueNoise(vec2((uDryArcLength + localDocument.x) * 0.055, band * 0.37));
    float breakup = smoothstep(0.24, 0.60, longitudinal + bristleLoad * 0.22);
    float pattern = fiber * (0.08 + 0.92 * breakup) * (0.55 + 0.45 * bristleLoad);
    float separation = uBristleDensity * (2.0 - uBristleDensity);
    float bristleFactor = mix(1.0, pattern, separation);
    return clamp(uDryLoad * paperFactor * bristleFactor, 0.0, 1.0);
}

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
        // vUnitPosition already is brush-local space. The vertex shader rotates
        // the complete stamp into document space, so rotating UVs again would
        // double the requested angle and clip the source.
        vec2 texCoord = vUnitPosition * 0.5 + 0.5;

        // Image brushes need alpha-aware interpolation to prevent transparent
        // texels from bleeding black/white. Mask brushes keep the single-tap
        // hardware path because only coverage is consumed.
        vec4 rawSample = uTextureSamplingMode > 0.5
                ? sampleBrushTexture(texCoord)
                : texture2D(uBrushTexture, texCoord);
        if (uTextureSamplingMode > 0.5) {
            gl_FragColor = vec4(rawSample.rgb, rawSample.a * uColor.a * uFlow * sampleGrain() * sampleDryMaterial());
            return;
        }
        float rawAlpha = rawSample.a;

        // 归一化：纹理 = 质感调制
        float meanAlpha = max(uTextureAlphaMean, 0.05);
        alpha = rawAlpha / meanAlpha;
        alpha = min(alpha, 1.0);

        if (alpha <= 0.001) discard;
    }

    // flow 在 stamp 阶段乘（绘制时与抬手后浓度一致）
    alpha *= uColor.a * uFlow * sampleGrain() * sampleDryMaterial();
    gl_FragColor = vec4(uColor.rgb * alpha, alpha);
}

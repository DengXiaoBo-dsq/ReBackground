// ============================================================
// [MOD PR-2.4] SDF 胶囊片段着色器
//
// 算法：
//   1. 计算当前像素到线段 [P0, P1] 的最短距离
//   2. 在投影处插值半径 r(t)
//   3. 用 smoothstep 得到覆盖率 alpha
//
// 输出语义与 brush_stamp.frag 保持一致：
//   非预乘 (rgb * alpha, alpha)
//   alpha 乘 uColor.a * uFlow
// ============================================================

precision highp float;

varying vec2 vDocumentPosition;
varying vec2 vP0;
varying vec2 vP1;
varying float vR0;
varying float vR1;

uniform vec4 uColor;
uniform float uAA;      // 过渡带宽度（文档单位）
uniform float uFlow;

void main() {
    // 1. 到线段的最短距离
    vec2 pa = vDocumentPosition - vP0;
    vec2 ba = vP1 - vP0;
    float baLenSq = dot(ba, ba);
    float h = (baLenSq < 1e-6) ? 0.0 : clamp(dot(pa, ba) / baLenSq, 0.0, 1.0);

    // 2. 插值半径
    float r = mix(vR0, vR1, h);

    // 3. 距离
    float d = length(pa - ba * h);

    // 4. SDF 覆盖率
    float alpha = 1.0 - smoothstep(r - uAA, r + uAA, d);
    if (alpha <= 0.0) discard;

    // 5. 与 brush_stamp.frag 输出语义一致
    alpha *= uColor.a * uFlow;
    gl_FragColor = vec4(uColor.rgb * alpha, alpha);
}
precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uStroke;
uniform sampler2D uOldPigment;
uniform float uStrokeOpacity;
// ============================================================
// [MOD PR-2.6] flow：本笔的颜料沉积率
//   缺失此声明 → GLSL 编译时 uFlow 未定义 → Kotlin 传不进去
//   → uFlow 默认值 = 0 → strokeCoverage = 0 → 笔迹被丢弃
// ============================================================
uniform float uFlow;

void main() {
    vec4 oldPig = texture2D(uOldPigment, vTexCoord);
    vec4 stroke = texture2D(uStroke, vTexCoord);
    // [MOD PR-2.6-fix] flow 已在 brush_stamp 阶段乘过，此处不再乘
    float strokeCoverage = min(stroke.a, uStrokeOpacity);

    if (strokeCoverage < 1e-4) {
        gl_FragColor = oldPig;
        return;
    }

    vec3 strokeColor = stroke.rgb / max(stroke.a, 1e-4);

    float oldCoverage = oldPig.a;

    float newCoverage = strokeCoverage + oldCoverage * (1.0 - strokeCoverage);

    if (oldCoverage < 1e-4) {
        gl_FragColor = vec4(strokeColor, newCoverage);
        return;
    }

    float totalInk = strokeCoverage + oldCoverage;
    float t = strokeCoverage / totalInk;

    vec3 mixedColor = mixbox_lerp(oldPig.rgb, strokeColor, t);

    gl_FragColor = vec4(mixedColor, newCoverage);
}
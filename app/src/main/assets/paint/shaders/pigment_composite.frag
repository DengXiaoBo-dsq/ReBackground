precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uCanvasTexture;
uniform sampler2D uPigmentTexture;
uniform sampler2D uStrokeTexture;
uniform float uStrokeOpacity;   // [MOD 2026-09-11] 覆盖度上限


void main() {
    vec3 canvas = texture2D(uCanvasTexture, vTexCoord).rgb;
    vec4 pig = texture2D(uPigmentTexture, vTexCoord);
    vec4 stroke = texture2D(uStrokeTexture, vTexCoord);

    vec3 displayColor;
    float displayCoverage;

    float strokeCoverage = min(stroke.a, uStrokeOpacity);

    if (strokeCoverage > 1e-4) {
        vec3 strokeColor = stroke.rgb / stroke.a;
        // 覆盖度用 over（决定最终像素可见性）
        float newCoverage = strokeCoverage + pig.a * (1.0 - strokeCoverage);

        if (pig.a < 1e-4) {
            displayColor = strokeColor;
        } else {
            // ============================================================
            // [MOD 2026-09-11] 修复 t 的计算（同 pigment_add.frag）
            // t = 新颜料 / (新颜料 + 旧颜料)
            // ============================================================
            float totalInk = strokeCoverage + pig.a;
            float t = strokeCoverage / totalInk;
            // ============================================================
            displayColor = mixbox_lerp(pig.rgb, strokeColor, t);
        }
        displayCoverage = newCoverage;
    } else {
        displayColor = pig.rgb;
        displayCoverage = pig.a;
    }

    vec3 result = mix(canvas, displayColor, displayCoverage);
    gl_FragColor = vec4(result, 1.0);
}

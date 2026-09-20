precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uColorTexture;
uniform sampler2D uPaperTexture;
uniform float uPaperStrength;

void main() {
    vec3 color = texture2D(uColorTexture, vTexCoord).rgb;
    float paper = texture2D(uPaperTexture, vTexCoord).r;
    vec3 result = color * (1.0 - uPaperStrength * 0.35 + paper * uPaperStrength * 0.35);
    gl_FragColor = vec4(result, 1.0);
}

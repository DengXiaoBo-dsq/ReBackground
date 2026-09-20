precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uPigmentTexture;
uniform sampler2D uVelocityTexture;
uniform vec2 uTexelSize;
uniform float uDt;
uniform float uDiffusion;

void main() {
    vec2 velocity = texture2D(uVelocityTexture, vTexCoord).xy * uDt;
    vec2 previousUV = vTexCoord - velocity * uTexelSize;
    vec4 center = texture2D(uPigmentTexture, vTexCoord);
    vec4 left = texture2D(uPigmentTexture, vTexCoord + vec2(-uTexelSize.x, 0.0));
    vec4 right = texture2D(uPigmentTexture, vTexCoord + vec2(uTexelSize.x, 0.0));
    vec4 up = texture2D(uPigmentTexture, vTexCoord + vec2(0.0, -uTexelSize.y));
    vec4 down = texture2D(uPigmentTexture, vTexCoord + vec2(0.0, uTexelSize.y));
    vec4 laplacian = left + right + up + down - 4.0 * center;
    vec4 diffused = center + laplacian * uDiffusion;
    gl_FragColor = diffused;
}

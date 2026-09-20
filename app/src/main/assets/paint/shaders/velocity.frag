precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uWetnessTexture;
uniform vec2 uTexelSize;
uniform float uAdvectionStrength;
uniform float uVelocityScale;

void main() {
    float center = texture2D(uWetnessTexture, vTexCoord).a;
    float right = texture2D(uWetnessTexture, vTexCoord + vec2(uTexelSize.x, 0.0)).a;
    float left = texture2D(uWetnessTexture, vTexCoord - vec2(uTexelSize.x, 0.0)).a;
    float up = texture2D(uWetnessTexture, vTexCoord + vec2(0.0, uTexelSize.y)).a;
    float down = texture2D(uWetnessTexture, vTexCoord - vec2(0.0, uTexelSize.y)).a;

    vec2 gradient = vec2(right - left, up - down);
    vec2 velocity = -gradient * uAdvectionStrength * center;
    gl_FragColor = vec4(velocity * uVelocityScale, 0.0, 1.0);
}

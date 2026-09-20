attribute vec2 aUnitPosition;
varying vec2 vTexCoord;

void main() {
    vTexCoord = aUnitPosition * 0.5 + 0.5;
    gl_Position = vec4(aUnitPosition, 0.0, 1.0);
}

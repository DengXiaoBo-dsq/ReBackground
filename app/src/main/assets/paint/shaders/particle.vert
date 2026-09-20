attribute vec2 aPosition;
attribute vec4 aColor;
attribute float aSize;
varying vec4 vColor;

void main() {
    vColor = aColor;
    gl_PointSize = aSize;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}

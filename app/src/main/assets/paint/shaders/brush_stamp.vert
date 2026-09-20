attribute vec2 aUnitPosition;
uniform vec2 uCenterDocument;
uniform vec2 uHalfExtentDocument;
uniform vec2 uDocumentSize;
uniform float uRotationRadians;
varying vec2 vUnitPosition;

void main() {
    float s = sin(uRotationRadians);
    float c = cos(uRotationRadians);
    vec2 localPosition = aUnitPosition * uHalfExtentDocument;
    vec2 rotatedPosition = vec2(
        localPosition.x * c - localPosition.y * s,
        localPosition.x * s + localPosition.y * c
    );
    vec2 documentPosition = uCenterDocument + rotatedPosition;
    vec2 clipPosition = vec2(
        documentPosition.x / uDocumentSize.x * 2.0 - 1.0,
        1.0 - documentPosition.y / uDocumentSize.y * 2.0
    );
    vUnitPosition = aUnitPosition;
    gl_Position = vec4(clipPosition, 0.0, 1.0);
}

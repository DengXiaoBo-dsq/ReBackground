// ============================================================
// [MOD PR-2.4] SDF 胶囊顶点着色器
//
// 用途：小像素（< 4px 直径）笔迹走 SDF 路径，避免 stamp 采样不足导致的锯齿
//
// 几何：每个胶囊 6 顶点（2 三角形），每个顶点带完整的 P0/P1/r0/r1
//      这样 N 个胶囊可以一次 draw call
// ============================================================

attribute vec2 aDocumentPosition;   // 顶点在文档坐标的位置
attribute vec2 aP0;                 // 胶囊起点
attribute vec2 aP1;                 // 胶囊终点
attribute float aR0;                // 起点半径
attribute float aR1;                // 终点半径

uniform vec2 uDocumentSize;         // (docW, docH)

varying vec2 vDocumentPosition;
varying vec2 vP0;
varying vec2 vP1;
varying float vR0;
varying float vR1;

void main() {
    vDocumentPosition = aDocumentPosition;
    vP0 = aP0;
    vP1 = aP1;
    vR0 = aR0;
    vR1 = aR1;

    // 文档坐标 → clip 空间
    vec2 clipPos = vec2(
            aDocumentPosition.x / uDocumentSize.x * 2.0 - 1.0,
            1.0 - aDocumentPosition.y / uDocumentSize.y * 2.0
    );
    gl_Position = vec4(clipPos, 0.0, 1.0);
}
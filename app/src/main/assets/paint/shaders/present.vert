attribute vec2 aUnitPosition;
varying vec2 vTexCoord;

uniform vec2 uViewOffset;
uniform vec2 uViewScale;
uniform float uViewRotation;
uniform vec2 uDocumentAspect;

// [MOD PR-2.3] 画布适配：让 document 按比例 fit 到屏幕
uniform vec2 uDocumentSize;   // document 像素尺寸 (docW, docH)
uniform vec2 uScreenSize;     // 屏幕像素尺寸 (screenW, screenH)
uniform float uFitScale;      // min(screenW/docW, screenH/docH)

void main() {
    // ============================================================
    // [MOD PR-2.3] 屏幕 NDC → 画布虚拟 NDC
    //
    // 画布虚拟 NDC：画布中心 (0,0)，画布边缘 ±1
    // 屏幕上画布占据 NDC 的 ±canvasHalfNdc 区域
    //   canvasHalfNdc = docSize * fitScale / screenSize
    // ============================================================
    vec2 canvasHalfNdc = vec2(
            uDocumentSize.x * uFitScale / uScreenSize.x,
            uDocumentSize.y * uFitScale / uScreenSize.y
    );
    vec2 pos = aUnitPosition / canvasHalfNdc;

    // ============================================================
    // 用户视图变换（保留原逻辑）
    // ============================================================
    pos.x *= uDocumentAspect.x;

    float s = sin(uViewRotation);
    float c = cos(uViewRotation);
    vec2 p = vec2(pos.x * c - pos.y * s,
            pos.x * s + pos.y * c);

    p.x /= uDocumentAspect.x;

    p = p / uViewScale - uViewOffset;
    vTexCoord = p * 0.5 + 0.5;

    // ============================================================
    // 全屏 quad：让 frag shader 能绘制画布外的底板
    // ============================================================
    gl_Position = vec4(aUnitPosition, 0.0, 1.0);
}
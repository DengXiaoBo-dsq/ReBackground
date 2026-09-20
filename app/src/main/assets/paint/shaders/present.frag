precision highp float;
varying vec2 vTexCoord;
uniform sampler2D uTexture;
uniform vec2 uScreenSize;

const vec3 BOARD_BG     = vec3(0.059, 0.078, 0.098);
const vec3 GRID_COLOR   = vec3(0.290, 0.333, 0.408);
const vec3 RULER_BG     = vec3(0.0, 0.0, 0.0);
const vec3 RULER_LINE   = vec3(1.0, 1.0, 1.0);
const float GRID_SIZE         = 40.0;
const float RULER_THICKNESS   = 60.0;
const float TICK_SPACING      = 10.0;
const float LINE_WIDTH        = 1.5;

void main() {
    // 画布内：正常采样
    if (vTexCoord.x >= 0.0 && vTexCoord.x <= 1.0 &&
        vTexCoord.y >= 0.0 && vTexCoord.y <= 1.0) {
        gl_FragColor = texture2D(uTexture, vTexCoord);
        return;
    }

    // 画布外：绘制板子
    vec2 p = gl_FragCoord.xy;
    vec2 s = uScreenSize;

    bool inLeft   = p.x < RULER_THICKNESS;
    bool inRight  = p.x > s.x - RULER_THICKNESS;
    bool inBottom = p.y < RULER_THICKNESS;
    bool inTop    = p.y > s.y - RULER_THICKNESS;
    bool inRuler  = inLeft || inRight || inBottom || inTop;

    vec3 color;

    if (inRuler) {
        color = RULER_BG;

        float pos;
        float distFromEdge;

        if (inLeft) {
            pos = p.y;
            distFromEdge = p.x;
        } else if (inRight) {
            pos = p.y;
            distFromEdge = s.x - p.x;
        } else if (inBottom) {
            pos = p.x;
            distFromEdge = p.y;
        } else {
            pos = p.x;
            distFromEdge = s.y - p.y;
        }

        float tickMod = mod(pos, TICK_SPACING);
        if (tickMod < LINE_WIDTH) {
            float index = floor(pos / TICK_SPACING);
            float len = 15.0;
            if (mod(index, 10.0) < 0.5)      len = 45.0;
            else if (mod(index, 5.0) < 0.5)  len = 30.0;

            if (distFromEdge < len) {
                color = RULER_LINE;
            }
        }
    } else {
        color = BOARD_BG;
        float gx = mod(p.x, GRID_SIZE);
        float gy = mod(p.y, GRID_SIZE);
        if (gx < LINE_WIDTH || gy < LINE_WIDTH) {
            color = GRID_COLOR;
        }
    }

    gl_FragColor = vec4(color, 1.0);
}
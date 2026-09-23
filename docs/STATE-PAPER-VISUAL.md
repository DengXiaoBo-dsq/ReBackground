# Paper Visual System — Layer A + Layer B

## Status

PASS — 2026-09-23.  Paper is now a canvas-level visual surface and a
brush-level material response.  This change does not alter WetCoreSimulator,
Mixbox, presentation shaders, view-to-document conversion, or FBO dimensions
and formats.

## Implementation

### Layer A — visual paper surface

`PaperTextureGenerator` creates one repeatable 512 x 512 neutral paper texture
from the selected `PaperDefinition`.  `GLPaintRenderer.setPaper()` queues that
texture for GL-thread upload and `pigment_composite.frag` multiplies the final
display colour by its reflectance.  Therefore the paper is visible on both an
empty canvas and beneath a painted stroke.

| Paper | visualStrength | heightAmplitude | roughness | absorption | fiberDensity |
|---|---:|---:|---:|---:|---:|
| Smooth | 0.05 | 0.10 | 0.10 | 0.10 | 0.10 |
| Medium tooth | 0.20 | 0.40 | 0.50 | 0.50 | 0.50 |
| Rough watercolour | 0.40 | 0.80 | 0.90 | 0.80 | 0.90 |

### Layer B — stroke/paper interaction

`BrushMaterial.paperResponseStrength` is copied to each `BrushStamp` and sent
as `uPaperResponseStrength`.  It scales the existing dry-material paper
modulation while preserving the brush's geometry and opacity/flow behaviour.

| Brush | paperResponseStrength |
|---|---:|
| Electronic pen | 0.10 |
| Pencil | 1.00 |
| Marker | 0.40 |
| Airbrush | 0.30 |
| G61 / G61-1 | 0.60 |
| User brush default | 0.50 |

## Evidence

All PNGs and the machine-readable report are in
`evidence/paper-visual-final/`.  They were captured from the final composite
FBO at document resolution by the debug-only `PaperDiagnosticActivity`.

### L1 — regression

| Check | Result |
|---|---|
| G5 strict gates A–F | PASS |
| T1–T7 device suite | PASS (7 / 7; `nan=0`, `inf=0`, GL errors 0) |
| Debug unit tests | PASS (no XML failures or errors) |
| Debug APK build | PASS |

### L2 — device image metrics

| Gate | Requirement | Measured | Result |
|---|---|---:|---|
| R6 empty smooth vs rough | SSIM < 0.95 | 0.519070 | PASS |
| R7 electronic pen | SSIM < 0.98 | 0.966905 | PASS |
| R7 pencil | SSIM < 0.98 | 0.821030 | PASS |
| R7 marker | SSIM < 0.98 | 0.939592 | PASS |
| R7 airbrush | SSIM < 0.98 | 0.950746 | PASS |
| R7 G61 | SSIM < 0.98 | 0.910626 | PASS |

The fixed B fixture (pencil, 200 px line) has smooth-vs-rough SSIM 0.605449.
The C extreme diagnostic is isolated to the debug diagnostic definition and
has SSIM 0.447209; no production paper preset uses those temporary values.
Full MAE/max-error values are retained in `result.json`.

### L3 — visual evidence

`A1_smooth_nodraw.png` is visually flat, while `A2_rough_nodraw.png` visibly
shows the rough paper fibres without zooming.  The paired B/C images and the
five `R7_*` smooth/rough pairs show paper beneath and within the stroke.  This
passes the visual-layer requirement; pencil has the strongest tooth response,
and the electronic pen intentionally relies mostly on Layer A.

## Manual recheck

1. In **Create canvas**, select **Rough watercolour paper**; with no mark,
   verify the canvas fibres are visible.
2. Draw a short pencil line.  It should show clear broken/toothed deposition.
3. Create a **Smooth paper** canvas and repeat.  The blank surface and pencil
   deposition should be materially smoother.
4. Repeat on electronic pen, marker, airbrush, G61, and a user PNG brush.
   Each has the same canvas paper surface; their stroke response follows the
   strength table above.


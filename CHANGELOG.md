# Development changelog

These entries describe repository implementation milestones, not an assertion
that corresponding Git tags or binary releases have been published.

## 0.7.0 — 2026-11-18

- Introduced the standalone native Android editor, light/dark appearance, compact
  icon bars, modal tools, persistent drafts, undo/redo and copy export.
- Added source-size opening through the shared library, warm Open-with routing,
  always-available viewport pinch/pan, exact crop/scale and pixel selection tools.
- Added compact indexed PNG/IFF palette editing and IFF palette-cycling export.
- Added visual layer editing, PSD/XCF raster subsets and extension-preserving ORA
  media containers, including opaque assets and retained non-PNG originals.
- Added `.icanim` ZIP/XML timelines containing an ORA document per frame, library
  identity/metadata/thumbnail/playback/writing, and APNG/indexed FLI/FLC import.
- Added independent frame-layer editing, timing/order/loop controls, timeline
  Play/Pause/current-frame highlights and scrolling that follows playback while
  preserving paused manual selection offsets.
- Added initially checked Apply-to-all-frames controls and atomic undoable bulk
  geometry, color/palette/cycling and selection-clear operations.
- Removed app-specific editing quotas and repaired obsolete history tests that
  allocated an Int.MAX_VALUE-sized list. Representation and actual memory limits
  remain; encoding and editing subsets are documented separately.

See [validation](docs/validation.md) for exactly which builds and checks ran and
which handset/format acceptance work remains. See [library saving profiles](libraries/libinfinicanvas/docs/saving.md)
for writer-specific losses and unsupported source encoders.

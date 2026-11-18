# InfiniCanvas application

## Version 0.7.0

InfiniCanvas is an independent Android image editor with raster editing,
indexed-palette editing, IFF palette cycling, media-layer workflows, and independent layered animation projects.
It owns document state, its interface, history, drafts, and export policy.
Reusable codecs and authoring APIs are in the pinned libinfinicanvas;
libinfiniselect provides SAF and optional Infinifolder integration.

## Editing workflows

Open through **Open**, Android **Open with**, **Edit**, or **Share**. VIEW/EDIT
accept intent data, streams, or ClipData; Share prefers its stream. The receiver
retains the granted access URI, optional source identity, filename, and MIME hint.
A new file reaches the existing editor task even if a picker was on top; abandoned
open/import picker results are ignored. Requests wait for recovery/current work,
and survive Activity recreation. An incoming file with a different identity is
not mistaken for the launch that was handled before recreation. Unexported drafts
get a confirmation naming the incoming file; cancel keeps the current draft.
A failed open leaves the previous committed draft intact and shows an error dialog.
Names/provider MIME metadata are resolved on the worker, with bounded header
identification for opaque URI names. **Edit** uses the same source-preserving copy
workflow; it does not overwrite the sender's file or return an edited URI. The original URI is never opened for
writing. New creates a transparent/white canvas. Pinch zooms, dragging pans,
and Fit/double tap restores the fitted viewport. Pinch and viewport navigation
remain active during playback or worker operations while edit gestures are locked.
Tool panels pass outside touches to the exposed canvas and stay open while zooming.

The open picker derives extensions from the linked Canvas decoder registry. SAF
allows all MIME types so mislabelled legacy images are selectable. The actual
library decode decides support. Editable palette/layer/frame import is attempted
first; if it fails, the complete viewer and generic image pipelines still run.
Thus unsupported PSD/XCF layer groups, Krita ZIPs or unsupported editable
precision can open as a rendered image when the library supports them. Extended
ORA uses the media-preserving container path.
A notice explains the unavailable structure and remains in Document information.
Failures leave the previous committed draft intact.

**Scale image** opens from its toolbar icon or Transform. Enter exact width and
height in pixels, optionally lock the original aspect ratio, or use a percentage.
Smooth resampling can be disabled for nearest-neighbor pixel art (the default for
indexed sources). Whole-image scaling resizes the canvas, every layer/frame's
pixels and positions together. Selected-layer scaling changes only that bitmap;
its origin and the canvas stay fixed. Dimensions must be positive and fit the Android bitmap representation. No editor
resolution quota or automatic import reduction applies. Allocation, codec, integer
overflow and storage failures keep the committed draft.

**Crop** offers handles, aspect presets, a live pixel size/origin, 1:1 viewing,
and an exact X/Y/width/height dialog. Coordinates start at zero; 1×1 crops are
supported. A layered image defaults to cropping the selected layer and updating
its offset. Animation cropping defaults to the frame canvas, with Apply to all
frames controlling its targets. The dialog also offers a whole-canvas crop, shifting the stack origin
and clipping the composition while retaining off-canvas layer pixels. Whole-image
crop/scale apply consistently to animation frame canvases. Zoom in for a pixel
grid; use two fingers to pan/zoom while cropping or selecting. More → View at 1:1
maps one image pixel to one physical screen pixel.

**Selection tools** temporarily replace the bottom rail with tool choice,
settings, exact bounds, invert, and clear icons. Rectangle, ellipse, freehand
lasso, contiguous magic wand and whole-layer similar-color selection operate on
the selected layer (or a plain image's working pixels). Settings offer Replace,
Add, Subtract, Intersect, color tolerance 0–255, Select all, Deselect, background
detection and Crop to selection bounds. Shape selections have hard pixel edges.
The highlight shows selected coverage with a contrasting bounding outline.

Background detection estimates the dominant border color and selects matching
pixels connected to an edge using four-way connectivity and per-channel color/
alpha tolerance. It works best with uniform backgrounds; it is not semantic
object segmentation. Inspect the highlight, adjust tolerance, and use Add/
Subtract or Invert before **Clear selection to transparent**. Clearing changes
alpha in the targeted image/layer, or in each target animation frame according
to Apply to all frames, and is undoable. A colored layer underneath may still
show through; PNG/ORA retain transparency. Raster edits disable indexed export.
Selections operate on native working dimensions, subject to bitmap/memory feasibility. Selection restricts clearing; other adjustment/transform tools
operate on their full layer/image. Uncommitted selections survive configuration
changes but are transient across process death and reset after a committed edit,
undo/redo, opening a file or changing layers. Committed clears retain their masks
as private draft assets and replay after recovery.

**Transform** rotates/flips; **Adjust** provides brightness, contrast and
saturation with Apply/Cancel. Undo, redo and reset cover committed geometry,
clear, color, palette, cycle, layer and frame changes. History has no fixed edit-count quota; a new edit after undo replaces its future branch.

**Palette** opens the original color table for indexed PNG and supported
ILBM/PBM IFF images in a compact dialog. The grid has 16 columns (up to 16 rows)
or 32 columns in wide windows/landscape (up to 8 rows). Color boxes show only a
centered index, with no visible hex labels. Cells expose accessible index/color
names and highlight the selected entry. An index-entry action provides an exact
alternative to tapping a small box. Tap a color to open a saturation/value field
and hue strip, with exact hex entry and PNG opacity inside the picker. Duplicate
palette entries remain distinct. Rotate a selected index range left/right to
move palette values without rewriting any pixel indices. PNG PLTE/tRNS and IFF
CMAP are retained as actual indexed source data, not reconstructed from RGB.
Other indexed formats (including GIF/BMP/TIFF and indexed XCF's colormap identity)
are not yet exposed through this palette tool. HAM/EHB expose base registers;
scanline-palette IFF variants are rejected by the authoring path. The dialog explains
when no editable table is retained, including older raster-only drafts that need
the source reopened. Reversed, unused IFF cycling records no longer invalidate
the color table; their original chunks remain intact.

**Cycles** edits supported IFF documents. Edit/add first/last index, speed in palette steps
per second, direction, and enabled state. The main toolbar exposes a **Play/Pause** icon when animation is available.
Restart is in More options, the cycling dialog, and a long press on Play/Pause. IFF/bitmap playback pauses at its current phase
and resumes; Restart begins at zero. Native GIF/WebP use Android start/stop behavior.
Editing resets playback; backgrounding pauses it. Transform/crop/color/selection previews
disable playback until the tool is left. Preview is bounded to a 512 px
frame and existing Canvas animation-layout limits. Existing CRNG/CCRT ranges are
read and deduplicated; explicitly changed ranges save as CRNG with format-rate
quantization. DRNG chunks remain unchanged and can play through the existing
renderer, but virtual DRNG cells cannot be edited. Palette-only edits keep
original cycling chunks. IFF BODY and unrelated chunks remain intact on indexed
export; masks/transparent indices continue to work when cycling is disabled.

**Layers** opens a dialog containing a topmost-first stack with checkerboard-backed thumbnails,
visibility controls, opacity/blend summaries, and a highlighted selection. Tap a
thumbnail or visible canvas content to select a layer. Close the dialog and drag the selected outline
to position it; the outline previews its destination and the composition updates
on release. Pinch still zooms. Select a layer, change visibility/name,
opacity, x/y offset, or a supported blend mode; reorder, duplicate, delete, add a
blank layer, or import a media file as its own layer. Encoded media is retained
for ORA export; unavailable renderers are identified in the layer list. At least one layer remains.
For a layered document, selection/adjust and selected-layer crop previews display
the selected layer by itself; whole-canvas crop shows the composition. Transform
rotates/flips the selected layer. Cropping a layer adjusts its offset.
Layer geometry does not resize the document canvas. Plain one-layer raster edits
retain the original canvas-resizing crop/rotate/resize behavior until a layer
operation creates a stack.

**Animation** opens a compact horizontal timeline. Selecting a frame keeps the
timeline at its current horizontal scroll position. The panel includes a fixed
Play/Pause icon; playback highlights the displayed frame and scrolls just enough
to keep its tile visible, including when playback loops. Paused manual frame
selection retains the current scroll position. Play starts at the selected frame, pause keeps
the current frame selected, and a completed finite sequence restarts from the
beginning on the next Play. APNG (including indexed APNG)
and indexed 8-bit FLI/FLC import as complete displayed frames with millisecond
frame timing. Select a frame to edit duration, move, duplicate, delete or import a
still/ORA frame; loop count is editable. **Edit layers in this frame** opens the
selected frame's actual layer editor. Frames are independent ORA documents, not
image layers. Crop, resize, transform, color/palette/cycling, and selection-clear
controls expose **Apply to all frames**, initially checked. Checked operations
apply to each complete frame; unchecking targets only the selected frame (or its
selected layer while editing frame layers). All-frame image adjustments affect
all renderable layers. Palette operations require compatible editable palettes
in the target frames; opaque media remain retained.

All-frame canvas crop/scale changes the shared animation dimensions. Single-frame
crop/scale changes its contents at the existing canvas origin and preserves the
shared canvas: uncovered space is transparent and overflow is clipped. The scope
choice survives Activity recreation. Each applied operation records its targets
in undo/recovery history; changing the checkbox later does not change past edits.
Failures retain the previous committed draft. At least one frame remains.

Save **ICANIM** to retain the editable animation: a ZIP renamed `.icanim`, containing
`animation.xml` and an ORA file for each frame. Durations, loop count, layer graphs,
media and retained original imports survive reopening. Other library consumers can
identify, thumbnail and play the format. APNG/GIF export writes composed frames;
ORA export from an animation saves only the selected frame's layers. FLI/FLC write
support is not implemented; save edits as ICANIM or export APNG/GIF. GIF/WebP/MNG
remain playback/poster imports. Complete editor frame/layer pixels remain in memory;
actual allocation, backend representation and format fields determine feasibility.

ORA is treated as an extensible media container. Each referenced media file is a
separate layer; extra unreferenced files are hidden layers. PNG, IFF and other
library-renderable images/animations get previews. Files without a renderer are
listed as retained files, preserving their encoded contents for export. Group/XML
extensions and extra entries survive ORA saving. Embedded IFF palette/cycle edits
preserve original indices and cycling chunks. Pixel edits use PNG working copies,
retaining non-PNG originals. Cross-group reparenting and audio/video editing are
not implemented. This profile extends the standard PNG-only ORA format.

## Supported authoring imports

| Format | Editable subset | Limits |
| --- | --- | --- |
| Indexed PNG | Original indexed pixel stream, palette entries, entry alpha | Palette changes preserve IDAT/frame data. Other indexed formats are not inferred from RGB. |
| IFF ILBM/PBM | CMAP editing, palette shifts, CRNG/CCRT range editing, existing cycling preview | DRNG cells and scanline palettes are not editable; original masking stays intact. |
| APNG / FLI / FLC | Complete RGBA frame snapshots, durations, order, duplication/deletion, per-frame transforms | Independent frame editing; millisecond timing; ICANIM retains ORA frame documents. |
| OpenRaster | Separate encoded media layers, groups, extension XML, opaque files | Five preview blend modes; unknown blend properties retained. Opaque media cannot receive pixel edits. |
| ICANIM | Timeline with layered ORA frames, timing and loops | Version 1; static frame composites during outer playback. |
| Photoshop PSD v1 | 8-bit RGB/grayscale layers; raw/RLE/ZIP/ZIP-prediction channels | Supports the same five blend modes. Groups, masks, clipping, adjustments, text/smart objects/effects and unsupported additional records are rejected. PSB is not editable. |
| GIMP XCF v0–23 | Supported 8-bit RGB/grayscale/indexed raster layers; raw/RLE/zlib; 32/64-bit offsets | Normal layers; groups, masks, effects, unsupported precision and blend/composite spaces are rejected. Indexed layer pixels expand to RGBA. |

Layer imports request source dimensions. No fixed layer count, canvas-side,
aggregate-pixel or encoded-input quota is applied by these authoring adapters.
Android raster allocation, integer/addressable-buffer limits and genuine format
fields still apply. Scaling uses one document factor for layer positions and pixels. This is not full Photoshop/GIMP fidelity or a
color-managed document engine. Unsupported authoring content is reported; the app can use the library’s rendered
image fallback while identifying the unavailable structure.

Ordinary PNG/JPEG/WebP and other Canvas-supported raster/vector inputs remain
available as still working images, with the platform/codec's actual restrictions.
SVG becomes raster pixels. Broader format support is conditional, not a universal
verified format matrix.

## Export and preservation

**Export** queries the shared writer API, offers a source-format target when
available, and otherwise identifies that the source has no writer. It offers:

- **Indexed PNG/IFF** when only palette/cycle edits have been applied. Saves the
  original encoded pixel data and edited table; IFF includes cycling information.
  These exports keep the source pixel dimensions, even when the display is sampled.
- **ICANIM** to retain the whole animation timeline and each layered ORA frame.
- **OpenRaster** to retain media/layer graphs, extension XML, encoded originals,
  palette/cycle edits and a merged preview. From an animation, it saves only the
  selected frame; use ICANIM for all frames.
- **PNG** as a flat still copy of the displayed working pixels and alpha. Frame
  sequences export the selected still; native GIF/WebP export their poster.
- **APNG** for frame sequences, preserving visible frame order, timing, loop count,
  and working RGBA pixels. Export uses full SOURCE frames with NONE disposal.
- **PSD** as RGB8 raster layers with supported blend modes and a merged RGBA image.
- **XCF** as version-0 RGBA8 normal raster layers; other blends fail explicitly.
- **JPEG/WebP/BMP/TGA/QOI/farbfeld/Netpbm/PAM/TIFF** as still rasters.
  JPEG/Netpbm without alpha use white matting; monochrome/grayscale targets convert
  colors. TIFF is uncompressed RGBA8. WebP is lossless on Android 11+.
- **GIF** as a still or visible frame sequence using a fixed RGB332 palette, binary
  alpha and centisecond timing. Animation/GIF input authoring is still limited to
  APNG, indexed FLI/FLC and ICANIM timelines; native GIF import remains playback/poster.
- **IFF** after raster changes as RGB24 ILBM with a binary mask. Original palette
  identity/cycles require the indexed preservation route.
- **SVG/SVGZ** containing embedded edited PNG pixels, not retained vector objects.

Every export explains structural/alpha/precision losses before destination choice.
No encoder is inferred from a decoder: formats with no writer (for example Krita,
RAW and some legacy formats) open when decodable and need another export target.
PSD/XCF writers preserve their supported raster subsets. Extended ORA/ICANIM writers preserve embedded media and extension data as described above. Working raster exports do not preserve palette identity, IFF cycles, source metadata,
masks/groups, original color profiles/bit depth, or undo history. The export
dialog explains these boundaries. Raster/layer edits disable indexed export so
they cannot be silently omitted. A flat copy does not mark a layered/indexed/animated draft
as fully exported. APNG copies omit frame layer structure; ICANIM is the complete
animation export used to mark that recipe exported. The source URI is never used as a write destination.

One versioned private draft stores its original working PNG or retained indexed/
layered/animated input bytes, replayable operations, undo cursor, imported layer assets,
and last complete exported recipe. Atomic metadata replacement commits edits
before retiring prior base assets. Failed operations/exports and picker cancellation
keep the draft. Version-1/2 drafts remain readable; current metadata uses version 3. This is app-private
recovery, separate from the portable ICANIM project format; clearing app data/uninstall deletes it.

## Interface and branding

The resting workspace has a 48 dp document bar and a 48 dp icon toolbar;
the image fills the space between them with narrow fit margins. Large font settings
can grow the document bar. The document bar contains Open, filename/dimensions,
Undo, Redo, Export and More. More contains New canvas, Fit, Restart, Reset,
Appearance and document information. The lower toolbar opens Selection, Scale, Palette, Layers,
Transform, Crop, Adjust, Cycles and Frames; it scrolls horizontally if needed.
Palette/cycling icons appear for supported indexed sources; a palette explanation
remains available in More for other images. Long press icons for names; all toolbar
icons have 48 dp touch targets and
accessibility labels. Play/Pause is fixed at the toolbar's leading edge when usable.

Tool controls open in bounded dialogs above the image rather than permanently
reducing the viewport. Palette dialogs expand horizontally for a dense grid;
other dialogs stay narrower. Layer/frame lists retain visual thumbnails and
compact icon actions. Properties, durations, resizing and cycle ranges open their
own dialogs. Adjust provides a live preview with an undimmed canvas behind it.
Crop and selection use the canvas and temporarily replace the lower toolbar
with compact context actions. Exact crop/selection and scale inputs use dialogs. Closing an adjustment dialog discards its preview.
Closing Layers leaves canvas selection/drag mode active; Back exits that mode.
Closed layer/frame panels stay closed through Activity recreation.

Messages appear briefly over the canvas and full details remain available by
tapping the document title or More → Document information. Palette changes and
same-size composite updates preserve the viewport instead of repeatedly fitting.
Light/dark mode follows the device by default, with persistent overrides. Icons,
color-box indices, dialogs, text, canvas and checkerboards follow the theme.

The launcher uses the existing InfiniCanvas easel and shared infinity emblem,
generated with InfiniFolder's `scripts/build-app-icons.py`. Rendered resources are
owned by this checkout and need no companion repository during an app build.

## Remaining direction

Painting, group creation/reparenting, masks/effects, tiled/disk-backed editing, broader indexed
formats, DRNG cell authoring, full PSD/XCF round trips, richer levels/histograms,
semantic background removal, native GIF/WebP frame import authoring and additional encoders
remain future work.
No new interapplication protocol is introduced beyond standard Android grants and
the existing libinfiniselect contract. AI colorization/upscaling are not required.

See [library animation project format](../libraries/libinfinicanvas/docs/animation-projects.md) and [extended ORA contract](../libraries/libinfinicanvas/docs/openraster-container.md) for exact preservation and subset limits.

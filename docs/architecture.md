# Architecture

The independent root Android application uses only pinned source dependencies in
`libraries/`, app-owned code/resources, and its documented SDK/toolchain. Existing
`com.infinifolder.preview` packages belong to libinfinicanvas, not another app.

## Document and rendering

`EditorController` owns one serialized worker for open/decode, edit replay,
persistence, encoding, and IFF animation rendering. State publication is on the
main thread; expensive operations disable controls. Incoming intents/picker
results wait for recovery. The controller survives Activity configuration changes.

`DocumentRenderer` starts from retained base pixels, a library `LayerDocument`,
or an encoded library `PaletteDocument`/`AnimationDocument`. Palette edits modify its original table,
not an RGB-derived colormap, so duplicate entries keep identity. Raster/layer
operations preserve the original indexed source in the draft but require a raster
export. Palette edits modify the base table before retained raster filters replay.
`ImageRenderer` delegates geometry to Canvas and uses Android color matrices for
brightness/contrast/saturation. All preview/export alpha behavior shares those APIs.

`EditHistory` is immutable with a cursor and no fixed edit-count quota.
Undo/Redo check cursor boundaries before changing them, avoiding integer overflow
at a collection’s largest representable cursor. `EditCodec` writes
raster, palette, cycling, layer properties/order/duplication/deletion/new/import,
frame mode/durations, and nested selected-layer edits. Redo history is retained until a new edit branches.
Selected-layer geometry/color is explicitly wrapped in `Edit.OnLayer`. Plain raster
operations resize the working canvas; per-layer operations retain the canvas.
Layer composition and ORA encoding are library APIs.

`CanvasActivity` owns tool choice, layout, theme, previews, and confirmations.
Selection/adjust and selected-layer crop previews show the selected bitmap alone,
so display coordinates and applied edits refer to the same image. Whole-canvas
crop and other tools show the composition. `CanvasPreview` owns viewport and normalized crop gestures.
`PixelSelections` in the shared library owns bounded coverage masks, shapes,
four-connected/color/background selection, combination, inversion, bounds and
alpha clearing. The controller computes masks on its worker and publishes an
ALPHA_8 preview overlay. Selection mode stops playback and detaches native
animation drawables so gesture coordinates match the working bitmap. The selected
layer is shown in isolation; masks use its local pixel coordinates. Selection
state is transient and cleared after document publication or layer changes.

Integer `PixelCrop` edits preserve exact coordinates instead of replaying
normalized floats. `CropImage` changes the document canvas and stack origin;
`ScaleImage` rescales every layer and offset using one pair of document factors,
checking representability and integer-coordinate overflow before allocation. Layer `Resize`
uses the additive shared `ImageTransform.Scale` with explicit filtering. Old
resize recipes retain smooth defaults. The viewport supports native pixel scale,
nearest display at high zoom, and a visible pixel grid; two-finger navigation
continues while crop/selection gestures are active.

`EditorThumbnail` draws untinted source pixels and palette colors over a theme
checkerboard. `PaletteGrid` measures square index-only cells into 16/32 columns,
with native per-cell focus/click accessibility. `HsvColorPicker` owns the visual
hue/SV gestures; host hex input and PNG alpha control provide exact alternatives.
Hue is retained during achromatic gestures rather than reconstructed on every drag. Layer selection maps from viewport coordinates to document pixels;
a drag previews an offset outline and commits one undoable property operation.
For composition selection, visible layers are hit-tested topmost first using alpha.

`TimelineRenderer` owns an `AnimationProject` timeline of independent ORA frame
documents. Its compatibility preview adapter supplies frame composites through the
old renderer result shape; it never exports those frames as an image layer stack.
`Edit.FrameEdit` scopes ordinary layer edits to one frame; timing, order, duplication,
loop count and deletion are timeline edits. Opening frame layers presents that
frame's actual media/layer graph through the existing visual layer controls.
APNG and indexed FLI/FLC import use library AnimationDocuments; original encoded
inputs are retained in project attachments. `.icanim` uses the shared ZIP/XML + ORA
project decoder and writer. The frame strip and frame layer panel are separate.
`FrameOperation` records either all frames or one frame, with an optional layer
for single-frame layer editing. `FrameOperations` replays geometry, color/palette
and mask operations through existing document APIs while retaining each ORA graph.
All-frame canvas geometry updates the project canvas; single-frame geometry clips
or pads content on the existing canvas. Bulk operations commit as one undo step
only after every target succeeds. Existing FrameEdit/OnLayer recipes and public
render/controller overloads remain readable/callable.

The Activity owns the initially checked Apply-to-all-frames choice and saves it
through recreation. Playback publishes a separate frame ID alongside its cached
bitmap. The frame panel updates only the previous/current tile borders and its
Play/Pause icon during playback without rebuilding the strip. It scrolls only
when needed to reveal the active tile, including after layout and when loops wrap;
instant scrolling keeps short frames visible. Paused manual selection retains the
scroll offset. Pausing selects the displayed frame. Finite completion resets the next Play
to the start; selecting a frame starts the next Play at that frame's time offset.

IFF playback uses a sampled renderer; bitmap animations publish immutable frames
at up to roughly 30 fps. Editable frame playback reuses cached immutable snapshots
at a nominal 60 Hz instead of allocating full canvases on each tick; finite loop
counts finish on the final frame. Frame canvases are checked for representable bitmap dimensions; actual allocation can fail. Generation tokens reject stale worker results. Pause keeps
the displayed bitmap and elapsed offset; resume continues that offset, Restart
resets it. APNG editing previews use the same timeline model used for encoding.
GIF/WebP native drawable callbacks run on the preview View and are stopped when
hidden/backgrounded; their resume semantics belong to Android. Other formats may
expose the existing BitmapAnimation API. Source animation data is retained only
while its pixels are unedited; edit replay disables playback-only source state.

## Recovery and export

`files/draft/session.json` version 3 points to a generated `base-<uuid>.png` for a
raster draft or `base-<uuid>.bin` containing the original encoded indexed/layered/animated
input. It includes kind, original display name/authoritative location, operations,
cursor, selected layer, last fully exported operation list, and pending export kind.
Version-1/2 drafts remain readable. Animation kind, original extension, frame-mode
operations, and durations are replayed from retained source bytes. Media imports
retain original `asset-<uuid>.bin` files; legacy imported-PNG recipes remain readable.
Selected frame, frame-layer mode and opaque-media state are reconstructed from
the retained source and recipe. ICANIM sources use draft kind `project`. Clear operations save their
coverage as a lossless PNG asset with dimensions in the recipe; replay restores
the mask alpha and clears the same pixels. These assets remain for undo/redo and
are retired when the document is replaced. A replacement base is synced
before AtomicFile commits the session; previous assets are deleted afterward.
This schema is private recovery state, not an interchange/project contract.

Opening resolves granted access and authoritative identity separately through
libinfiniselect. `IncomingImage` normalizes VIEW/EDIT/SEND data, stream and ClipData
handles plus source/name/MIME metadata. Manifest `singleTask` routes later opens to
`onNewIntent`, clearing picker activities above the one private editor document.
A new explicit request cancels stale open/import picker bookkeeping. URI grants are
retained on the Activity; persistable grants are taken only when offered.

The complete queued request and last handled launch identity survive recreation.
A posted request drain waits for worker recovery and active exports, avoiding
reentrant open calls inside UI rebuilding. Dirty-draft confirmation keeps its
request until accepted/canceled. Superseding requests cannot open an older dialog's
file. Metadata queries and bounded signature identification run on the worker.
Open failures publish a consumed error state for the current Activity to present,
including after recreation, while the prior committed draft remains intact.
Temporary read handles are not substituted for supplied stable source identities.
Authoring imports use library parsers without an app-specific input-byte quota.
Native-resolution decode uses additive `decodeEditorFrame`/`decodeEditorImage`
entry points; default thumbnail/viewer sampling policies remain separate. Optional
authoring adapters cannot prevent the generic library decode from opening an image.
A successful fallback stores its limitation notice in the draft. Raster canvases
use software drawing to avoid GPU texture-size rejection of large source bitmaps.
Editing remains in-memory; it is not a tiled/full-disk image engine. No app storage backend or
another app's private data is imported.

`ImageSaving` discovers `ImageWriter` services registered for ENCODE. Formats,
extensions, MIME types, alpha/layer/animation support and subset losses belong to
the library. Native raster, document and animation write methods borrow their
streams; host file publication remains in the app. Third-party writers can supply
their own document/animation methods. Existing PNG processor APIs remain intact.

Export freezes the current applied recipe. PNG is a flattened still; ORA retains
working media layers and extensions; ICANIM retains independent ORA frame documents; indexed PNG/IFF patches original encoded data;
APNG/GIF save frame sequences with timing and loops. Export passes full working
pixels, never the sampled playback display. The source format is preferred when
a writer exists; target-specific limitations are shown before publication.
An indexed export is allowed only when no raster/layer operations would be omitted.
The generated cache file is published via `InfiniSelect.createFileDocument` and
saved through `InfiniSelect.save`, with SAF fallback. It remains until the result
returns. Pending picker/file state survives recreation. A flat PNG does not clear
the dirty state of a layered/indexed/animated document whose source structure was omitted.
No code opens the source URI for writing.

## Dependency reproducibility

Reusable APIs live in the libinfinicanvas repository. The app pins a committed
library revision in both its Git submodule entry and `dependencies.lock.json`;
no app-carried source patch is currently needed. `build.sh` initializes source
submodules, prepares any explicitly declared future patches, checks exact pins,
and invokes the app build. A clean checkout needs no sibling application or
unpublished dependency commit. See [maintenance](maintenance.md) for publication
order, build-only commands and the native harness prerequisites.

## Layout and branding

All UI colors use paired day/night resources and native theme parents. The device
setting is default; an Activity configuration context implements persistent overrides.
System bars/cutouts use WindowInsets. The image fills a weighted viewport between
compact document and tool bars, normally 48 dp each. Larger font settings can grow
the document bar. No permanent inspector or status row consumes canvas space.
Messages use a transient overlay; full information lives in a dialog.

`EditorIcon` draws code-native vector artwork with 48 dp hit targets, selected and
disabled states, accessibility labels, and platform tooltips. Document controls
share busy/selection gating. Viewport gestures remain enabled independently of
edit gestures during worker operations and playback. Tool-dialog windows pass
outside touches through to the exposed viewport. Palette/cycling icons are shown for relevant sources.
The bottom tool rail scrolls when needed; animated
sources expose a fixed Play/Pause action, with Restart in overflow/long press.

Palette, layers, frames, transform, cycling and adjustments use bounded native
dialogs. Palette width adapts for 16/32-column density. Dialog content refreshes
from committed history while keeping the current dialog and its scroll position.
The animation timeline retains its horizontal offset across frame selection and panel
rebuilds, restoring after layout and clamping when frames are removed. The offset
also survives closing/reopening the panel and Activity recreation.
Layer/frame panels can close while their viewport mode stays active; saved UI
state preserves that closed state on recreation. Adjustment close discards only
the pending preview; applying is explicit. Cropping and selection temporarily replace the tool
rail with context icons. Exact pixel inputs and scale controls use scrollable
dialogs. Crop preview coordinates follow the chosen layer/canvas scope. Same-sized committed palette/composite changes preserve zoom.

Launcher resources are generated suite assets; `artwork/` retains source
SVGs and provenance. Generation is a development step, not a build dependency.

## Extended containers in 0.7.0

ORA opening uses OpenRasterDocuments, preserving each encoded media layer, group and extension metadata. Opaque files appear as retained layers; pixel edits are rejected. Palette/cycle changes target embedded IFF assets. Rasterized non-PNG sources retain their originals alongside PNG working copies. Imported media use original .bin draft assets; old imported-PNG recipes remain supported. Animation projects use draft kind `project` and retained `.icanim` bytes. `FrameEdit` recipes replay nested frame-layer edits without flattening the frame graph. See the shared [ORA](../libraries/libinfinicanvas/docs/openraster-container.md) and [animation project](../libraries/libinfinicanvas/docs/animation-projects.md) contracts.

# Validation

## Documentation and source publication — 2026-11-18

Published libinfinicanvas authoring/container/writer APIs and their documentation
at `7bfbde3ee86c4d947a658600851cc32e8c0faeaf`. The app now pins that exact source
revision; the previous app-carried patch has been removed. Both pins and submodule
entries are committed together. Historical entries below describe the earlier
patch workflow and checks at those development milestones.

After the pin migration, dependency preparation and all three exact revision
checks pass. Assembly-only `assembleDebug` completed successfully in 10s
(76 tasks up to date); log: `build/publication-build.log`. Local Markdown links
and staged whitespace were checked. No SDK paths, signing keys, APKs or build/cache
outputs are included in the source commit. The local APK was refreshed from the
assembled output. No tests were added or run for this documentation/publication
step; earlier JVM/native evidence remains recorded below, and handset acceptance
for the later timeline and bulk-frame controls is still outstanding.

## Timeline follows playback

On 2026-11-18, `assembleDebug` completed successfully in 14s. Log:
`build/timeline-follow-build.log`. Playback reveals the current highlighted tile
with the smallest required horizontal scroll, follows loop wraps and the final
frame, and handles the initial panel layout. Offsets clamp to the scroll range;
narrow viewports align wide tiles consistently. Paused manual selection keeps
its prior offset. The packaged APK was copied to the root download. No tests
were added or run; handset playback/scroll acceptance remains outstanding.

## Frame playback controls and operation scope

On 2026-11-18, assembled with `assembleDebug` after adding the timeline Play/Pause
control, playback frame IDs/highlight updates, and initially checked Apply to all
frames controls. Scope is serialized in the edit recipe and processed atomically;
older controller call signatures and recipes remain available. No new tests were
added or run for this change. Final build: BUILD SUCCESSFUL in 6s; the root APK
was refreshed. Log: `build/frame-controls-build.log`. Handset
acceptance remains outstanding for playback highlights, scroll retention, finite
loops, all-frame versus single-frame crop/scale/color/clear, and undo/recovery.

## Timeline scroll retention

On 2026-11-18, assembled with `assembleDebug`: BUILD SUCCESSFUL in 10s.
Log: `build/timeline-scroll-build.log`. The timeline restores its horizontal
position after layout when frame selection rebuilds the panel; the offset is
also saved across panel reopening and Activity recreation, and clamps to the
remaining content after deletion. App whitespace check passes; the root APK
was refreshed. No tests were added or run for this UI change. Handset interaction
acceptance remains outstanding.

## Version 0.7.0: extensible ORA and layered animation projects

Built on 2026-11-18 with the complete standalone entry point:

```sh
ANDROID_HOME="$HOME/android-sdk" ./build.sh --max-workers=1 --console=plain \
  -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 \
  :libinfinicanvas:canvas-android:testDebugUnitTest
python3 scripts/verify-authoring-runtime.py
```

Results: BUILD SUCCESSFUL; 11 app JVM tests and 109 library JVM tests pass with
zero failures/errors/skips. Native Android checks run real Bitmap/Canvas/DOM/ZIP
implementations against the actual APK: 15 existing authoring groups and 12 new
container/animation groups pass. Logs: `build/container-final-build.log` and
`build/container-runtime-final.log`. No Activity UI/handset gesture acceptance is
claimed from these checks.

Coverage includes old Java layer constructors and render overloads; reflection of
ImageLayer/LayerDocument constructors and copy methods; legacy decode/encode/writer
signatures; existing PSD/XCF/indexed PNG/IFF behavior; mixed ORA opaque/media bytes,
extension XML, groups and opacity; embedded IFF palette/cycling save; retained
originals after raster edits, including newly imported media; explicit media
deletion; APNG timing/loops; indexed FLI/FLC palette delta frames; ICANIM ZIP/XML/ORA
round trips with layered frames and attachments; independent nested frame-layer
edits and JSON recovery; byte identity, capability discovery/probing, bounded
metadata extraction, source/MIME/extension and extensionless viewer paths;
registered writer execution; finite playback and caller stream ownership.

The native checks caught an Android DOM Document.cloneNode incompatibility and
optional native codec linkage errors on opaque media. The writer now imports XML
children into a new document; absent optional image codecs preserve opaque source
assets. Probes keep executable decoder/thumbnail/metadata operations after project
byte identification. JVM tests cover malformed timeline versions/durations and
external entity rejection without decoding frame pixels.

All three dependency pins, the complete library patch digest/applied state,
forward application of the patch to its pinned base, and app/library whitespace
checks pass. The root APK is identical to the packaged output: versionCode 8,
versionName 0.7.0. The library source changes ship in the declared patch; no sibling
app build or unpublished library commit is required.

Remaining acceptance: real mobile light/dark layouts, timeline and frame-layer
navigation, pinch behind dialogs, SAF/Open-with for ICANIM/ORA and large actual
media sets. In-memory editor allocations may fail. FLI/FLC encoding, native
GIF/WebP frame extraction and outer-timeline advancement of embedded independent
animations/cycles remain unimplemented. Shared viewer frames are decoded on demand;
editor frames retain source-size layer graphs and complete composites.

## History/geometry test correction after 0.6.0

On 2026-11-18, reran the complete standalone build after correcting tests left
behind by removal of the editor quotas:

```sh
ANDROID_HOME="$HOME/android-sdk" ./build.sh --max-workers=1 --console=plain \
  -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

Result: **BUILD SUCCESSFUL** in 25s. All **11 app JVM tests pass**, with zero
failures, errors or skips. Log: `build/history-test-fix.log`; XML/HTML reports
are under `build/test-results/testDebugUnitTest` and
`build/reports/tests/testDebugUnitTest`. Dependency pins/patch digest and
app/library whitespace checks pass. The build script rebuilt and copied the APK.

The old history test allocated a list using LIMIT, which had become Int.MAX_VALUE,
and the old geometry test still expected rejection above 200 edits/2048 pixels.
Removed the misleading sentinel and redundant JSON length check. Regression
fixtures use 512 edits, accept larger representable dimensions across geometry
and mask recipes, reject invalid cursors/nonpositive or unrepresentable dimensions
and crop overflow, and verify Redo at Int.MAX_VALUE using a virtual list with no
large allocation. Undo/Redo check their boundary before changing the cursor.

These results cover app history and recipe validation. New format writers, native
image operations and handset gestures still need the acceptance checks below.

## Version 0.6.0 opening, navigation, native pixels and writers

Assembled on 2026-11-18 with `assembleDebug` only: **BUILD SUCCESSFUL** in 9s.
Log: `build/format-saving-build.log`. Dependency pins/patch digest and app/library
whitespace checks pass. The final APK was copied to the repository root.
APK versionCode 7 / versionName 0.6.0. No tests were added or run for this change.
Prior version checks below describe their historical code and do not establish
new writer/large-image behavior.

Implemented optional authoring imports followed by library viewer/still fallback,
dynamic decoder extensions with unfiltered SAF MIME selection, explicit fallback
notices retained in document information, and native-size editor decode. Removed
editor 2048-side, 64-layer/frame, 32 Mi input/aggregate and 200-edit quotas. Bitmap
representation, allocation and genuine format bounds remain. The backend is
in-memory, not tiled; legacy codec subsets/restrictions still apply.

Viewport gestures are enabled independently from edit locks during playback and
worker operations. Tool dialogs pass outside touches to the exposed canvas.
Software drawing avoids large bitmap GPU texture-size failures.

Shared ImageSaving/ImageWriter APIs advertise real ENCODE targets and preserve
caller ownership. ORA document saving and indexed IFF saving/cycling are primary
paths. The app’s indexed export calls writePalette, retaining source pixels and
unchanged cycling data or writing edited CRNG ranges. Other raster/document/frame
writers are implemented but have not been exercised end to end in this change.

Still requires device/external application acceptance:

- Reopen the user’s failing ORA and standard/nested/non-PNG/embedded-IFF projects;
  check editable versus rendered-image notices and playback/recovery. The exact
  failing user file/error was not supplied during implementation.
- ORA export/reopen: layer pixels, positions, opacity, visibility, names, blend
  modes, source byte identity, failed saves, and fallback structure losses.
- IFF palette/cycle edit/export/reopen: BODY identity, CMAP, unchanged versus
  changed CRNG/CCRT, DRNG preservation, timing/direction/enabled state and alpha.
- Source dimensions above 2048; large layers, selections, crop, scale, undo,
  export and recovery; allocation/storage failure keeps the previous draft.
- Pinch during playback, selection, crop, busy operations and outside tool
  dialogs in portrait/landscape, large fonts, and both themes.
- Every new writer with independent decoders: PSD/XCF raster layers and Unicode
  names, GIF palette/transparency/disposal/timing/finite loops, TIFF channel alpha,
  BMP masks, TGA origin, QOI/farbfeld, Netpbm conversions and embedded SVG/SVGZ.


## Version 0.5.0 pixel editing and selections

Assembled on 2026-11-17 with `assembleDebug`: **BUILD SUCCESSFUL** in 22s.
Log: `build/selection-tools-build.log`; APK versionCode 6 / versionName 0.5.0.
No tests were added or run for this change. Pin/patch-digest verification and
app/library whitespace checks pass. The APK was copied to the repository root.

Implemented exact integer crop, whole-image/layer scaling with filtering control,
1:1/pixel-grid viewing, rectangle/ellipse/lasso/color/background selections,
coverage combination/inversion and clearing selected alpha. Clear masks are
lossless draft assets referenced by replayable edits. Existing resize recipes
keep smooth defaults and shared Resize options remain compatible; explicit
filtering uses the additive Scale operation. Source-level exhaustive ImageTransform
consumers must include its new variant on rebuild.

Compilation does not establish handset behavior. Device acceptance remains:

- Exact 1×1 and edge crops; layer versus canvas scope and offsets; repeated crop/undo.
- Nonuniform and aspect-locked scaling; nearest-neighbor pixel identity versus
  smoothing; selected layer versus whole document/frame sequence.
- Shape gestures, color tolerance, four-connected wand/background results,
  replace/add/subtract/intersect, invert, exact bounds, highlight alignment and
  clear/undo/redo, including transparent pixels and other layers showing below.
- Clear-mask recovery after process death, draft replacement, edited indexed
  raster export, PNG alpha, ORA layer alpha, and APNG frame crop/scale/clear.
- Pinch/two-finger pan, 1:1 viewing and high-zoom grid; small portrait/landscape,
  keyboard/large-font dialog bounds, accessibility and light/dark themes.
- Oversized source layers, operation budgets/failure preservation, and native
  animation poster alignment while selecting.

Background detection uses dominant border color with tolerance and connected
regions; it is not semantic segmentation. Selection is bounded to 2048 px per
side. Uncommitted selection masks are transient; committed clear assets persist.

## Version 0.4.1 incoming-file handling

Assembled on 2026-11-17 using `assembleDebug`: **BUILD SUCCESSFUL**.
Log: `build/open-with-build.log`; APK versionCode 5 / versionName 0.4.1.
No tests were added or run for this change. Dependency-pin and whitespace checks
pass. The app remains independently built against its pinned libraries.

Inspected the sender's existing public handoff: InfiniFolder passes VIEW (or EDIT)
with a grantable working-copy URI and ClipData, or a granted provider URI with
separate authoritative-source metadata. The receiver now accepts these forms,
uses one editor task, preserves queued metadata across recovery/recreation,
ignores superseded open-picker results, and displays explicit open errors.
Filename/MIME lookup now runs off the UI thread and decoder dispatch can use a
bounded signature when access URLs have opaque names.

The user's failing warm Open-with path has not been exercised end to end on a
handset. Confirm cold/warm/recreated starts, opening above an existing picker,
VIEW/EDIT/SEND and ClipData-only URIs, dirty-draft cancel/accept, permission-denied
and unsupported-file dialogs, and preservation of the previous image on failure.
The EDIT route uses copy export; in-place external editing is not implemented.

## Historical version 0.4.0 compact interface

Assembled on 2026-11-17 using the SDK/Gradle command below, with
`assembleDebug` only: **BUILD SUCCESSFUL**. Log: `build/compact-ui-build.log`.
The APK reports versionCode 4 / versionName 0.4.0. No tests were added or run
for this interface change.

The persistent inspector, text button rows, separate transport row, and permanent
status row were replaced with two compact bars around the canvas. Tool dialogs,
16/32-column indexed grids, HSV color picking, icon labels/states, crop action
mode, viewport preservation, and closed-panel recreation state are implemented.
Shared codecs and the pinned authoring patch are unchanged by this UI work.

Physical handset acceptance remains outstanding: confirm palette cell selection
at 256 colors, keyboard/color-picker interaction, dialog bounds in portrait/
landscape and split screen, large fonts, TalkBack/focus navigation, light/dark
contrast, tool close/apply/cancel, and layer/animation workflows. Compile success
is not a claim that these touch/layout paths have been exercised on a handset.

## Historical version 0.3.0 build

Assembled on 2026-11-17 with the local Termux Android SDK 36 toolchain:

```sh
ANDROID_HOME="$HOME/android-sdk" ./gradlew \
  -Dandroid.home="$HOME/android-sdk" \
  -Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2 \
  assembleDebug --max-workers=1 --console=plain
```

Result: **BUILD SUCCESSFUL**. Current log: `build/visual-editor-build.log`.
This build includes the visual layer stack/outline gestures, untinted and expanded
palette views, reversed-unused-IFF-range handling, global playback controls,
animation source recovery, and APNG frame authoring/export. Tests were not rerun
for this change. The older results below describe version 0.2.0 only.

Handset touch/layout, actual user IFF files, native GIF/WebP pause/resume, and APNG
import/edit/export/reopen acceptance remain unverified. Keep these distinct from
compile success. Check small portrait/landscape and light/dark themes, source byte
identity, frame disposal/transparency/timing/loop counts, edit/undo/recovery, and
export behavior on a device before claiming those paths are validated.

## Historical version 0.2.0 evidence

Checked on 2026-11-17 using the local Termux/Android toolchain.

## Build, JVM checks, and lint

```sh
./build.sh --max-workers=1 --console=plain lintDebug \
  :libinfinicanvas:canvas-android:testDebugUnitTest
```

Result: **BUILD SUCCESSFUL**, with 6 app JVM tests and 102 library JVM tests,
zero failures/errors/skips. Android lint has no errors. Remaining warnings cover
the deliberately pinned AGP/target SDK, unused generated icon foreground art,
and English text constructed in the UI. Gradle/upstream deprecation and prebuilt
native-symbol stripping notices do not prevent assembly.

App JVM checks exercise undo/redo and history branching/bounds plus crop,
dimension, cursor, and adjustment validation. The library's existing decoder,
processing, ownership, and malformed-input tests remain green after the shared
static-IFF transparency fix and authoring API additions.

The patch SHA-256 and all three dependency pins pass verification. The final
library patch was applied and reversed successfully in an independent temporary
copy of its exact pinned base, without accessing any sibling app. Both app and
library `git diff --check` pass.

## Native authoring checks

```sh
python3 scripts/verify-authoring-runtime.py
```

Result: **all 15 native check groups passed**. This helper compiles a Java harness,
loads the actual packaged app classes with Android's `app_process64`, and exercises
real Android Bitmap/Canvas and the library processors. It does not bootstrap an
Activity or replace Android graphics with test stubs. The runner uses this app's
APK, SDK, and declared dependency outputs only. It requires a local Android runtime;
use an appropriate device/instrumentation runner on a non-Android development host.

Fixtures are generated independently from the format layouts by
`scripts/generate-authoring-fixtures.py`. Groups cover:

- Indexed PNG duplicate palette slots, unchanged IDAT pixels, palette alpha.
- IFF forward/reverse cycling, saved CMAP/ranges, unchanged BODY and unknown chunks.
- Disabled cycling with transparent indices, including the decoder regression fix.
- Unchanged DRNG data and range order; separate overlapping CRNG ranges.
- Separate ORA layers and alpha/offset composition.
- PSD raw, RLE, ZIP, and ZIP-prediction layer channels and Unicode names.
- XCF raw, RLE, and zlib tiles with 32-bit and 64-bit offsets.
- ORA layer export/reopen and source-over alpha pixels.
- Layer visibility, duplication, reordering, deletion, selected-layer color edits,
  and unchanged original layer state/pixels.
- JSON edit recipe recovery, indexed-palette rotation, rejection of indexed export
  after raster edits, and truncated-project rejection.

Logs: `build/authoring-validation.log`, `build/authoring-native.log`, and
`build/authoring-runtime/result.log`. JVM/lint reports are under each build directory.
The native checks establish these fixture subsets, not complete real-world
Photoshop/GIMP interoperability or full codec round-trip coverage.

## Artifacts and outstanding acceptance

The APK is `build/outputs/apk/debug/InfiniCanvas-debug.apk`, copied to
`InfiniCanvas-debug.apk` in the repository root. The launcher/header use the existing
suite icon; the generated launcher PNG was inspected visually. Artwork provenance
and the generator command are in `artwork/README.md`.

No Activity/widget screenshots or handset touch/picker acceptance were completed.
Still check compact portrait/landscape, tablets/split-screen, large fonts, device
light/dark changes and overrides, palette/range dialogs, long layer stacks,
pinned Apply/Cancel, selected-layer gesture mapping, system insets, and live
SAF/optional Infinifolder providers. Exercise process death/recovery with palette,
cycle, and layer edits and outstanding pickers, storage failures, revoked grants,
export collisions/cancellation, and source-file byte identity on real providers.
Groups/masks/effects, full-resolution processing, other indexed formats, editable
DRNG cells, full PSD/XCF fidelity and PSD/XCF writing remain unsupported.

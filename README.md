# InfiniCanvas

InfiniCanvas is a standalone Android image editor in the InfiniForge family.
It opens images through libinfinicanvas, retains a recoverable draft, and exports
edited copies without overwriting the input file. Current app version: **0.7.0**.

## Editing

- Native working pixels, exact pixel crop/scale, smooth or nearest resampling,
  rotate/flip, brightness, contrast and saturation.
- Rectangle, ellipse, lasso, magic wand, similar-color and border-background
  selection; combine/invert selections and clear selected pixels to transparent.
- Indexed PNG/IFF palette editing with a compact 16/32-column index grid and
  visual color picker. IFF palette rotation/cycling saves original pixel indices,
  BODY data and retained cycling chunks; DRNG cell editing is not implemented.
- Visual PSD/XCF raster layers and extended ORA media layers: thumbnails, names,
  visibility, offsets, opacity, supported blend modes, duplicate/reorder/delete,
  blank layers and imported media. ORA preserves groups, extension XML and encoded
  files; media without an image renderer remain retained layers.
- Independent animation frames with their own ORA layer documents. APNG and
  indexed 8-bit FLI/FLC import, editable timing/order/loops and nested frame layers.
  The frame panel has Play/Pause, a current-frame highlight, and scrolling that
  follows playback. Paused manual selection retains the scroll position.
- **Apply to all frames** is initially checked for image operations. Turn it off
  to edit one frame or its selected layer. Bulk edits commit as one undo step.
- Undo/redo, private draft recovery, Android Open with/Edit/Share, and SAF opening
  and export. Infinifolder integration is optional.

The mobile interface keeps the image between compact icon bars; tool controls
open in dialogs. Pinch/pan remains available during playback, worker operations
and on exposed canvas behind tool panels. Device light/dark mode is honored by
default, with a persistent appearance override and accessible icon labels.

## Saving and project formats

`.icanim` is an ordinary ZIP with `animation.xml` and an ORA document per frame.
It retains the timeline, timing/loops, frame layers and encoded media. Frames are
separate from image layers. APNG/GIF export writes composed animation frames;
ORA export from an animation saves only its selected frame.

The library supplies PNG/JPEG/WebP, BMP/TGA/QOI/farbfeld, Netpbm/PAM, TIFF, IFF,
SVG/SVGZ raster wrappers, PSD/XCF/ORA, GIF/APNG and ICANIM writers. Target-specific
losses are explained before export. Decode support does not imply write support.
FLI/FLC encoding and native GIF/WebP frame extraction remain future work.

Editing uses in-memory Android bitmaps, not tiled/disk-backed processing. There
are no fixed app resolution, layer, frame or history quotas; available memory,
integer/bitmap representation, codec subsets and format fields still apply.
PSD/XCF groups/masks/effects and full fidelity are incomplete. Not every indexed
format has an editable palette. Opaque ORA assets are preserved, not rendered or
edited as audio/video. See [workflows and preservation limits](docs/APPLICATION.md).

## Standalone build

Requirements: Git, Python 3, JDK 17 or 21, Android SDK platform 36 and build tools
35.0.0. The checked-in Gradle 9.7.1 wrapper has a pinned distribution SHA-256;
AGP 8.9.2 and Kotlin 2.2.20 are declared in the build. Runtime minimum: API 26.
Codecs include upstream native AARs; this app needs no app-specific NDK build.

```sh
git clone --recurse-submodules https://github.com/infiniforge/infinicanvas.git
cd infinicanvas
export ANDROID_HOME=/path/to/android-sdk
./build.sh --max-workers=1 --console=plain
```

Alternatively set `sdk.dir` in untracked `local.properties`. The build script also
recognizes common SDK directories. On Termux, install `aapt2` with `pkg install
aapt2`; the script selects it automatically. Initial builds need the declared
Gradle/Maven artifacts locally cached or downloadable.

`build.sh` initializes submodules, checks dependency pins, assembles the debug APK,
runs app JVM tests, and copies the APK to `InfiniCanvas-debug.apk`. The assembled
output is `build/outputs/apk/debug/InfiniCanvas-debug.apk`. APKs, SDK settings,
caches and build logs are ignored by Git; pushing source does not publish a binary.

For an incremental assembly without tests:

```sh
./gradlew assembleDebug --max-workers=1 --console=plain
```

On Termux, also pass `-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)`.
The app builds only from its own checkout and exact source submodules in
[dependencies.lock.json](dependencies.lock.json); no sibling app build is needed.
See [maintenance and publishing](docs/maintenance.md) for changing those pins.

## Documentation and license

- [Documentation index](docs/README.md)
- [Architecture, recovery and export](docs/architecture.md)
- [Animation project format](libraries/libinfinicanvas/docs/animation-projects.md)
- [Extended ORA media contract](libraries/libinfinicanvas/docs/openraster-container.md)
- [Validation evidence and remaining device checks](docs/validation.md)
- [Development changelog](CHANGELOG.md)

Original app code is [MIT licensed](LICENSE.md). The app reuses the existing suite
artwork; linked libraries retain their own licenses and notices. See
[attribution](docs/third-party.md) and [artwork provenance](artwork/README.md).

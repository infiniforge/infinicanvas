# Dependencies and attribution

InfiniCanvas original source is MIT licensed; see `LICENSE.md`.

The source dependencies are Git submodules with exact revisions recorded in
`dependencies.lock.json`. libinfinicanvas supplies image decoders, transforms,
and format-aware raster/document/animation encoding and authoring APIs;
libinfinistream supplies its common processing contracts;
libinfiniselect supplies the optional Infinifolder picker contract and SAF
fallback. These libraries retain their original MIT notices and upstream
third-party provenance. Their bundled resources/notices remain packaged through
their Android library modules; no third-party code or artwork is copied into the
app source.

libinfinicanvas declares AndroidSVG 1.4, JxlCoder 2.6.1, and jp2-android 1.0.5,
including native JPEG XL/OpenJPEG components. Those projects retain their
upstream licenses and transitive notices; consult the pinned library's
`SOURCE_PROVENANCE.md`, build metadata, and packaged notices before distribution.
InfiniCanvas uses the suite's existing easel/infinity launcher artwork, generated
by InfiniFolder's icon script; source SVG provenance is in `artwork/README.md`. There are no
network downloads or AI-generated assets in the application runtime.

The authoring/container/writer implementations are original project code
published in libinfinicanvas and pinned by source commit in the app. Their
provenance is recorded in the library; publication does not change its license
or introduce an application dependency. ICANIM is an InfiniForge extension,
not an OpenRaster-standard animation format.

The GIF89a writer follows the published format specification. The Graphics
Interchange Format and GIF service mark belong to CompuServe Incorporated.

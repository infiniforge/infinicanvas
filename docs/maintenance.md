# Dependencies, builds and publishing

## Reproducibility

The app owns its Android build, source, resources, permissions and lifecycle.
`libraries/` contains Git submodules for libinfinicanvas, libinfinistream and
libinfiniselect. Their URLs and exact commits appear in both `.gitmodules`/the Git
submodule entries and `dependencies.lock.json`. Library module coordinates are
resolved through these source composite builds, not a sibling app or a mutable
remote library release.

Run `python3 scripts/check-dependency-pins.py` to compare checkout revisions with
the lock. `build.sh` initializes submodules and invokes the dependency preparation
and pin checks before Gradle. `prepare-dependencies.py` supports optional declared
source patches, verifying their digest and refusing overlapping edits; the current
lock uses published source commits with no additional patch.

The initial editor work used an app-carried libinfinicanvas authoring patch. Its
implementation is now committed to the library, and the app pins that revision.
The obsolete patch is not reapplied. Historical validation entries still describe
checks that used the old patch workflow.

## Updating a dependency

1. Work in the owning library and keep host UI/state in this app.
2. Read its API/compatibility docs and describe supported behavior and losses.
3. Commit and push the library revision to its own repository.
4. Update this app's submodule entry and matching dependency-lock commit together.
5. Check pins and assemble from this app checkout. Run tests only when requested;
   keep build/test evidence distinct from unverified handset acceptance.
6. Commit and push the app only after referenced library commits are available.

Do not change sibling applications' private source, pins or build outputs as part
of publishing InfiniCanvas. Existing applications obtain new shared capabilities
when they explicitly update their own library dependency.

## Build and validation entry points

See the [README](../README.md#standalone-build) for prerequisites and assembly.
`build.sh` also runs app JVM tests by default. For an assembly-only iteration use
`./gradlew assembleDebug` with the documented SDK/aapt2 settings.

When verification is requested, the library suite can be invoked from this
composite build as `:libinfinicanvas:canvas-android:testDebugUnitTest`. The optional
`scripts/verify-authoring-runtime.py` uses generated fixtures and the actual APK
for native Android Bitmap/Canvas/DOM/ZIP checks. It needs javac, Android SDK 36,
build tools **36.0.0** (d8), and an Android `app_process64` runtime; it is not a
portable desktop JVM or Activity UI test. Compile app classes for the harness via
`bundleDebugClassesToCompileJar` if using an assembly-only build first.

Build/test reports and logs are local ignored artifacts. Record exact commands,
counts and remaining acceptance in [validation.md](validation.md); do not infer
live UI, external format round trips or successful tests from compilation alone.

## Repository publication

Commit library changes first, then the app source/docs and dependency pointers.
Use a normal push; if upstream moved, integrate its changes without dropping
remote work. Do not force-push or rewrite published history for a routine update.
Generated `docs/ECOSYSTEM.md` belongs to the ecosystem manifest and is not edited
locally. Its planned-app description may lag this independently published app.

The debug APK is copied to the repository root for local installation and is
ignored by Git. A source push does not create a GitHub release or upload the APK.
Signing/release artifacts are separate work when requested; never commit local
SDK paths, signing keys, tokens, caches or build directories.

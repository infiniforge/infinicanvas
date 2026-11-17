# InfiniCanvas
InfiniCanvas is a planned Android image viewing, correction, editing, and animation application.
This repository is currently a placeholder; see the [application scope and implementation
status](docs/APPLICATION.md).

**Status: planned placeholder.** This checkout currently contains project guidance
only. It has no Android application source, Gradle build, app-level `build.sh`, or
APK, so it cannot be built or installed yet.

The planned app is an image editor. Once implementation begins, it should own its
editing UI, user settings, and document lifecycle in an independent Android
project. Android SAF is the baseline for opening and exporting files. The planned
storage integration is through `libinfiniselect`, with Infinifolder's provider
remaining optional. Reusable image processing may use `libinfinicanvas` through
its public API.

Do not treat the image-library repository as an implemented editor application.

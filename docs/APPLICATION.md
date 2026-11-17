# InfiniCanvas application

## Purpose

InfiniCanvas is the planned InfiniForge Android image-viewing, editing, correction, and animation application. It is intended to support practical image work while preserving source data and making corrections reversible where feasible. The application is separate from the existing libinfinicanvas library, which provides reusable image decoding and processing APIs.

## Primary use cases

- View, inspect, rotate, crop, and geometrically transform images.
- Correct color, brightness, contrast, saturation, levels, histograms, and alpha/transparency.
- Edit indexed-color images and palettes, including palette cycling and frame-based/classic animation workflows.
- Remove backgrounds and work with layers where the selected processor supports those operations.
- Preserve an original image while saving edits as reversible operations, layers, or an application project.
- Open and export images through Android SAF, local providers, and optional InfiniForge storage integration.

## Current implementation status

This repository is a planned placeholder. It contains no Android application source, Gradle build, app build script, installable APK, application API, or declared library dependency. The application functionality in this document is planned product direction, not a description of working editor features. It must not be confused with libinfinicanvas, which already owns reusable image codec, frame, thumbnail, and animation functionality.

## Intended responsibilities and shared libraries

The application should own the Android image-editor experience: document lifecycle, user interface, edit history, layer/project state, safe source preservation, and export workflow. Reusable image decoding and processing belong in libinfinicanvas; Android resource selection and grant handling may use libinfiniselect, shared viewer UI may use libinfiniui, and provider-neutral resource contracts may use libinfinicore. These are candidate integrations only; no dependency is declared by this placeholder.

InfiniCanvas should not implement storage backends or archive/filesystem-image navigation. When its public APIs support a needed format or operation, use those libraries instead of copying their implementations into the app.

## Planned and exploratory functionality

The intended editing set includes crop/rotation/transform, color correction and histogram/automatic RGB-level adjustments, brightness/contrast/saturation, transparency/alpha operations, background detection/removal, layers, indexed-color tools, palette cycling, frame-based animation, and optional drawing/painting. The preservation goal is to retain source bytes when practical and represent edits as reversible operations or layers.

OpenRaster compatibility is an architectural direction, with possible extensions for animated palette changes, layer-relative positions, bump maps, reflectivity, and animated brightness/saturation. These are exploratory format/design ideas; no extension schema is established. AI colorization and artificial resolution enhancement are not required goals.

## Interoperability

The [generated ecosystem map](ECOSYSTEM.md) provides the wider application/library map. A future app can exchange images with Infinifolder through Android intents, content URIs, and URI grants, and may accept an optional resource-resolution integration through public library APIs. Infinifolder remains the file manager and storage navigator. Integration with InfiniMedia for visual presentation/export is a potential workflow, not an implemented contract. The app must remain independently usable through local/SAF paths.

## Documentation audit

- Consistent: the placeholder README and agent guidance distinguish the editor application from the existing libinfinicanvas processing library.
- Incomplete: repository docs did not contain a full user-facing editing scope or preservation goals; this document records the requested direction.
- Unverified: every editor capability is planned because no application source exists; library decoding does not demonstrate editor behavior.

## Limitations and outstanding work

- No editor UI, document model, file support matrix, project format, or standalone build exists here.
- Library codec support does not imply an application editing workflow or successful round-trip editing.
- Source preservation, edit-stack serialization, OpenRaster extensions, background removal, and animation/palette workflows require explicit design and tests.
- Identify supported formats and preservation guarantees from the actual selected library APIs before exposing editing operations.
- The app has no current public API or interapplication protocol.

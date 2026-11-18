# InfiniCanvas agent guidance

InfiniCanvas is an independent Infiniforge Android image editor. The implementation supports source-size pixel selections, exact crop/scale, library-wide opening and format-aware raster/layer saving, indexed PNG/IFF palettes, IFF cycling, independent layered animation projects and APNG/FLI/FLC frame authoring, and copy export; document future features separately from implemented behavior.

## Independence and integration

- Maintain the standalone Android application build in this repository. It must build from its own checkout with only its declared and pinned library dependencies and documented toolchain.
- Keep its Gradle build independent from every other application. Do not import sibling app source or depend on sibling build outputs.
- Use reusable image APIs through linked libraries such as libinfinicanvas when suitable. Keep editor workflow, UI, and app-owned state in this application.
- Use Android SAF and granted content URIs for input and export. Infinifolder may improve file access when installed, but must remain optional.

## Project focus

- The intended role is image editing. Define supported formats, editing operations, and preservation behavior as implementation grows; do not overstate them.
- Document the app's standalone build and keep library revisions explicit.

## Ecosystem workflow for agents

1. Read `docs/ECOSYSTEM.md` first to understand the component map and ownership boundaries.
2. Read `docs/APPLICATION.md` for this app's intended role and current status. Before changing a public interface, read the repository's architecture and API documentation listed below and inspect compatibility notes.
3. Check the owning shared library repository before implementing behavior that may already exist elsewhere. Do not conclude a capability is unavailable just because its implementation is outside the current repository.
4. Keep application responsibilities, shared library APIs, and other applications' private data/source separate.
5. Before a cross-repository change, check the exact dependency pin and the relevant interoperability contract. Dependency versions and protocol versions are separate; do not infer protocol incompatibility from a library-version mismatch.
6. Update the local documentation index and relevant API/behavior documentation when public behavior changes. `docs/ECOSYSTEM.md` is generated from the central manifest and must not be edited locally.

## Documentation index

- `README.md` — project purpose, prerequisites, and standalone build instructions.
- `AGENTS.md` — repository-specific development rules and this ecosystem workflow.
- `docs/README.md` — local documentation index.
- `docs/APPLICATION.md` — application purpose, responsibilities, implementation status, planned work, and known limits.
- `docs/architecture.md` — editor state, recovery, shared API usage, layout, and theme behavior.
- `docs/validation.md` — build/test evidence and outstanding device acceptance.
- `docs/third-party.md` — dependencies and licensing.
- `docs/ECOSYSTEM.md` — generated ecosystem-wide inventory, boundaries, dependencies, versions, and protocols.

- `CHANGELOG.md` — implementation milestones and feature history.
- `docs/maintenance.md` — source pins, build/validation commands and publication order.

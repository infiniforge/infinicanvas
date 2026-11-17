# InfiniCanvas agent guidance

InfiniCanvas is a planned Infiniforge image editor. This directory is a project placeholder; do not describe features as implemented until they exist in this app.

## Independence and integration

- Establish a standalone Android application build in this repository. It must build from its own checkout with only its declared and pinned library dependencies and documented toolchain.
- Keep its Gradle build independent from every other application. Do not import sibling app source or depend on sibling build outputs.
- Use reusable image APIs through linked libraries such as libinfinicanvas when suitable. Keep editor workflow, UI, and app-owned state in this application.
- Use Android SAF and granted content URIs for input and export. Infinifolder may improve file access when installed, but must remain optional.

## Project focus

- The intended role is image editing. Define supported formats, editing operations, and preservation behavior as implementation grows; do not overstate them.
- Document the app's standalone build and keep library revisions explicit.

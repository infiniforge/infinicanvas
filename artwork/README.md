# InfiniCanvas branding

These are the existing InfiniForge suite assets, not newly designed replacements:
`canvas.svg` from InfiniFolder's `icons.zip` and its shared `infini.svg` emblem.
The composed icon depicts an easel/canvas with the infinity emblem. Artwork and
renderer came from the local InfiniFolder checkout at commit
`f8f2b1b8a857db127b6570bcc120ae0f86003816`.

The Android launcher density resources and header image were generated using:

```sh
python3 ../infinifolder/scripts/build-app-icons.py --only infinicanvas \
  --output "$PWD/build/branding" --resources-dir "$PWD/src/main/res"
```

The generated Android resources are checked into this app; its standalone build
does not invoke that script or read a sibling checkout. Source artwork retains
the suite's project licensing/attribution. No AI image generation is involved.

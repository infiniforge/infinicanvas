#!/usr/bin/env sh
set -eu
cd -- "$(dirname -- "$0")"
git submodule update --init --recursive
python3 scripts/prepare-dependencies.py
python3 scripts/check-dependency-pins.py
CANVAS_SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$CANVAS_SDK" ] && [ -f local.properties ]; then
    CANVAS_SDK=$(sed -n 's/^[[:space:]]*sdk.dir[[:space:]]*[:=][[:space:]]*//p' local.properties | head -n 1)
fi
if [ -z "$CANVAS_SDK" ]; then
    for CANVAS_CANDIDATE in "$HOME/android-sdk" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
        if [ -d "$CANVAS_CANDIDATE/platforms" ]; then CANVAS_SDK=$CANVAS_CANDIDATE; break; fi
    done
fi
if [ -n "$CANVAS_SDK" ]; then set -- "-Dandroid.home=$CANVAS_SDK" "$@"; fi
if [ -n "${TERMUX_VERSION:-}" ]; then
    CANVAS_AAPT2=$(command -v aapt2 || true)
    if [ -z "$CANVAS_AAPT2" ]; then echo "Install aapt2 with: pkg install aapt2" >&2; exit 1; fi
    set -- "-Pandroid.aapt2FromMavenOverride=$CANVAS_AAPT2" "$@"
fi
./gradlew assembleDebug testDebugUnitTest "$@"
cp build/outputs/apk/debug/*.apk .

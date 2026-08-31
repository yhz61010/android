#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
MAVEN_REPO="$(mktemp -d)"
trap 'rm -rf "$MAVEN_REPO"' EXIT

VERSION="$(sed -n 's/^leo-version = "\([^"]*\)"$/\1/p' "$PROJECT_DIR/gradle/libs.versions.toml")"
test -n "$VERSION"
AUDIO="adpcm-ima-qt-codec"
VIDEO="h264-hevc-decoder"
COMBINED="adpcm-ima-qt-codec-h264-hevc-decoder"

"$PROJECT_DIR/gradlew" \
    -p "$PROJECT_DIR" \
    :adpcm-ima-qt-codec:publishReleasePublicationToMavenLocal \
    :h264-hevc-decoder:publishReleasePublicationToMavenLocal \
    :adpcm-ima-qt-codec-h264-hevc-decoder:publishReleasePublicationToMavenLocal \
    -Dmaven.repo.local="$MAVEN_REPO" \
    --rerun-tasks

for module in "$AUDIO" "$VIDEO" "$COMBINED"; do
    module_file="$MAVEN_REPO/com/leovp/android/$module/$VERSION/$module-$VERSION.module"
    test -f "$module_file"
    rg -q '"name": "ffmpeg-native-runtime"' "$module_file"
    "$PROJECT_DIR/gradlew" \
        -p "$SCRIPT_DIR" \
        -Dmaven.repo.local="$MAVEN_REPO" \
        :app:assembleDebug \
        -PffmpegVersion="$VERSION" \
        -PffmpegModules="$module" \
        --rerun-tasks
done

for modules in "$AUDIO,$VIDEO" "$AUDIO,$COMBINED" "$VIDEO,$COMBINED"; do
    log_file="$MAVEN_REPO/conflict-${modules//,/-}.log"
    if "$PROJECT_DIR/gradlew" \
        -p "$SCRIPT_DIR" \
        -Dmaven.repo.local="$MAVEN_REPO" \
        :app:assembleDebug \
        -PffmpegVersion="$VERSION" \
        -PffmpegModules="$modules" \
        --rerun-tasks >"$log_file" 2>&1; then
        echo "Expected capability conflict for $modules" >&2
        exit 1
    fi
    rg -qi 'capabilit.*ffmpeg-native-runtime|ffmpeg-native-runtime.*capabilit' "$log_file"
done

echo "FFmpeg runtime capability verification passed."

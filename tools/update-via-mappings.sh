#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TARGET="$ROOT/src/main/resources/vendored/viaversion-mappings"
URL=https://github.com/ViaVersion/ViaVersion.git

if ! git -C "$TARGET" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    rm -rf "$TARGET"
    git clone --depth 1 --filter=blob:none --sparse "$URL" "$TARGET"
fi

# Keep only the compiled mapping NBT/JSON data from ViaVersion.
git -C "$TARGET" sparse-checkout init --no-cone
git -C "$TARGET" sparse-checkout set \
    '/common/src/main/resources/assets/viaversion/data/'

git -C "$TARGET" checkout --quiet

# Copy into the actual resources location if sparse checkout placed files elsewhere.
# When cloning directly into TARGET, the data lives at TARGET/common/...
SRC="$TARGET/common/src/main/resources/assets/viaversion/data"
if [ -d "$SRC" ]; then
    mkdir -p "$ROOT/src/main/resources/vendored/viaversion-mappings"
    # Clear old files (except .git)
    find "$ROOT/src/main/resources/vendored/viaversion-mappings" -maxdepth 1 -type f -delete
    cp "$SRC"/*.nbt "$ROOT/src/main/resources/vendored/viaversion-mappings"/ 2>/dev/null || true
    cp "$SRC"/*.json "$ROOT/src/main/resources/vendored/viaversion-mappings"/ 2>/dev/null || true
    rm -rf "$TARGET"
    mkdir -p "$TARGET"
    cp "$ROOT/src/main/resources/vendored/viaversion-mappings"/* "$TARGET"/ 2>/dev/null || true
    printf 'viaversion-mappings %s (copied from %s)\n' "$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)" "$SRC"
else
    printf 'viaversion-mappings %s\n' "$(git -C "$TARGET" rev-parse HEAD)"
fi

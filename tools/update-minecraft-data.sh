#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TARGET="$ROOT/src/main/resources/vendored/minecraft-data"
URL=https://github.com/PrismarineJS/minecraft-data.git

if ! git -C "$TARGET" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    rm -rf "$TARGET"
    git clone --depth 1 --filter=blob:none --sparse "$URL" "$TARGET"
fi

# Keep only Java-edition registry/configuration snapshots in the worktree. The Git object
# database may retain other objects, but Gradle never packages it.
git -C "$TARGET" sparse-checkout init --no-cone
git -C "$TARGET" sparse-checkout set \
    '/data/dataPaths.json' \
    '/data/pc/common/protocolVersions.json' \
    '/data/pc/common/versions.json' \
    '/data/pc/*/version.json' \
    '/data/pc/*/loginPacket.json'

git -C "$TARGET" checkout --quiet
printf 'minecraft-data %s\n' "$(git -C "$TARGET" rev-parse HEAD)"
